package com.minicloud.api.compute;

import com.minicloud.api.domain.Instance;
import com.minicloud.api.domain.InstanceState;
import com.minicloud.api.domain.InstanceType;
import com.minicloud.api.domain.SecurityGroup;
import com.minicloud.api.domain.InstanceRepository;
import com.minicloud.api.domain.SecurityGroupRepository;
import com.minicloud.api.domain.NetworkingAdvisor;
import com.minicloud.api.dto.InstanceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComputeService {

    private final InstanceRepository instanceRepository;
    private final SecurityGroupRepository securityGroupRepository;
    private final ProcessManager processManager;
    private final NetworkingAdvisor networkingAdvisor;
    private final com.minicloud.api.audit.AuditService auditService;
    private final com.minicloud.api.domain.UserRepository userRepository;
    private final com.minicloud.api.iam.PolicyEvaluator policyEvaluator;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public InstanceResponse launchInstance(UUID userId, String accountId, String name, String typeStr, UUID subnetId, UUID securityGroupId, String command) {
        checkPermission(userId, "ec2:RunInstances", "arn:aws:ec2:*:*:instance/*");
        
        InstanceType type;
        try {
            type = InstanceType.valueOf(typeStr.toUpperCase());
        } catch (Exception e) {
            type = InstanceType.T2_MICRO;
        }

        if (command == null || command.isBlank()) {
            command = "sleep 3600";
        }

        Instance instance = Instance.builder()
                .userId(userId)
                .accountId(accountId)
                .name(name)
                .type(type)
                .state(InstanceState.RUNNING)
                .privateIp(networkingAdvisor.assignPrivateIp())
                .publicIp(networkingAdvisor.assignPublicIp())
                .subnetId(subnetId)
                .securityGroupId(securityGroupId)
                .pid((long) (Math.random() * 100000)) // Simulation
                .build();
        
        Instance saved = instanceRepository.save(instance);
        
        String username = userRepository.findById(userId).map(u -> u.getUsername()).orElse(userId.toString());
        auditService.recordSuccess(username, "EC2", "RunInstances", saved.getName());
        
        return toResponse(saved);
    }

    public void terminateInstance(UUID instanceId) {
        Instance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new RuntimeException("Instance not found"));
        
        checkPermission(instance.getUserId(), "ec2:TerminateInstances", "arn:aws:ec2:*:*:instance/" + instanceId);

        if (instance.getPid() != null) {
                processManager.terminate(instance.getPid());
            }
            instance.setState(InstanceState.TERMINATED);
            instance.setPid(null);
            instanceRepository.save(instance);
            
            String username = userRepository.findById(instance.getUserId()).map(u -> u.getUsername()).orElse(instance.getUserId().toString());
            auditService.recordSuccess(username, "EC2", "TerminateInstances", instance.getName());
    }


    public InstanceResponse startInstance(UUID instanceId) {
        Instance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new RuntimeException("Instance not found"));
        
        checkPermission(instance.getUserId(), "ec2:StartInstances", "arn:aws:ec2:*:*:instance/" + instanceId);
        
        if (instance.getState() != InstanceState.STOPPED) {
            throw new RuntimeException("Instance is not stopped");
        }
        
        instance.setState(InstanceState.RUNNING);
        instance.setLaunchedAt(LocalDateTime.now());
        instance.setPid((long) (Math.random() * 100000));
        Instance saved = instanceRepository.save(instance);
        
        String username = userRepository.findById(instance.getUserId()).map(u -> u.getUsername()).orElse(instance.getUserId().toString());
        auditService.recordSuccess(username, "EC2", "StartInstances", instance.getName());
        
        return toResponse(saved);
    }

    public InstanceResponse stopInstance(UUID instanceId) {
        Instance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new RuntimeException("Instance not found"));

        checkPermission(instance.getUserId(), "ec2:StopInstances", "arn:aws:ec2:*:*:instance/" + instanceId);

        if (instance.getState() != InstanceState.RUNNING) {
            throw new RuntimeException("Instance is not running");
        }

        if (instance.getPid() != null) {
            processManager.terminate(instance.getPid());
            instance.setPid(null);
        }

        instance.setState(InstanceState.STOPPED);
        Instance saved = instanceRepository.save(instance);
        
        String username = userRepository.findById(instance.getUserId()).map(u -> u.getUsername()).orElse(instance.getUserId().toString());
        auditService.recordSuccess(username, "EC2", "StopInstances", instance.getName());
        
        return toResponse(saved);
    }

    public InstanceResponse getInstance(UUID instanceId) {
        return instanceRepository.findById(instanceId)
                .map(this::toResponse)
                .orElseThrow(() -> new RuntimeException("Instance not found"));
    }

    public List<InstanceResponse> getInstancesForAccount(String accountId) {
        return instanceRepository.findByAccountId(accountId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<InstanceResponse> getActiveInstances() {
        return instanceRepository.findByStateNot(InstanceState.TERMINATED).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private InstanceResponse toResponse(Instance instance) {
        long uptime = 0;
        if (instance.getLaunchedAt() != null && instance.getState() == InstanceState.RUNNING) {
            uptime = ChronoUnit.SECONDS.between(instance.getLaunchedAt(), LocalDateTime.now());
        }

        String sgName = null;
        if (instance.getSecurityGroupId() != null) {
            sgName = securityGroupRepository.findById(instance.getSecurityGroupId())
                    .map(SecurityGroup::getName)
                    .orElse("Unknown");
        }

        return InstanceResponse.builder()
                .id(instance.getId().toString())
                .name(instance.getName())
                .type(instance.getType().name())
                .state(instance.getState().name())
                .accountId(instance.getAccountId())
                .subnetId(instance.getSubnetId() != null ? instance.getSubnetId().toString() : null)
                .privateIp(instance.getPrivateIp())
                .publicIp(instance.getPublicIp())
                .pid(instance.getPid() != null ? instance.getPid().intValue() : null)
                .uptimeSeconds(uptime)
                .launchedAt(instance.getLaunchedAt() != null ? instance.getLaunchedAt().format(FMT) : "")
                .securityGroupId(instance.getSecurityGroupId() != null ? instance.getSecurityGroupId().toString() : null)
                .securityGroupName(sgName)
                .build();
    }

    private void checkPermission(UUID userId, String action, String resource) {
        com.minicloud.api.domain.User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        
        if (!policyEvaluator.isAuthorized(user, action, resource)) {
            auditService.recordFailure(user.getUsername(), "EC2", action, resource, "Access Denied");
            throw new RuntimeException("Access Denied: " + action + " on " + resource);
        }
    }
}

