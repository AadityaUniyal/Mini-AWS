package com.minicloud.api.compute;

import com.minicloud.api.audit.AuditService;
import com.minicloud.api.auth.SecurityUtils;
import com.minicloud.api.auth.UserPrincipal;
import com.minicloud.api.compute.event.*;
import com.minicloud.api.compute.runtime.*;
import com.minicloud.api.domain.*;
import com.minicloud.api.dto.ExecRequest;
import com.minicloud.api.dto.ExecResponse;
import com.minicloud.api.dto.InstanceResponse;
import com.minicloud.api.iam.PolicyEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final SubnetRepository subnetRepository;
    private final RuntimeFactory runtimeFactory;
    private final NetworkingAdvisor networkingAdvisor;
    private final AuditService auditService;
    private final UserRepository userRepository;
    private final PolicyEvaluator policyEvaluator;
    private final ApplicationEventPublisher eventPublisher;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Transactional
    public InstanceResponse launchInstance(UUID userId, String accountId, String name, String typeStr, UUID subnetId, UUID securityGroupId, String command) {
        checkPermission(userId, "ec2:RunInstances", "arn:aws:ec2:*:*:instance/*");
        
        InstanceType type = InstanceType.T2_MICRO;
        if (typeStr != null) {
            try {
                type = InstanceType.valueOf(typeStr.toUpperCase().replace(".", "_"));
            } catch (Exception e) {
                type = InstanceType.T2_MICRO;
            }
        }

        if (command == null || command.isBlank()) {
            command = "sleep 3600";
        }

        if (subnetId == null) {
            List<Subnet> subnets = subnetRepository.findByAccountId(accountId);
            if (!subnets.isEmpty()) {
                subnetId = subnets.get(0).getId();
            }
        }

        if (securityGroupId == null) {
            List<SecurityGroup> sgs = securityGroupRepository.findByAccountId(accountId);
            if (!sgs.isEmpty()) {
                securityGroupId = sgs.get(0).getId();
            } else {
                SecurityGroup sg = SecurityGroup.builder()
                        .name("default")
                        .description("Default security group")
                        .userId(userId)
                        .accountId(accountId)
                        .build();
                SecurityGroup savedSg = securityGroupRepository.save(sg);
                securityGroupId = savedSg.getId();
            }
        }

        UUID instanceId = UUID.randomUUID();

        Instance instance = Instance.builder()
                .id(instanceId)
                .userId(userId)
                .accountId(accountId)
                .name(name)
                .type(type)
                .state(InstanceState.PENDING)
                .privateIp(networkingAdvisor.assignPrivateIp())
                .publicIp(networkingAdvisor.assignPublicIp())
                .subnetId(subnetId)
                .securityGroupId(securityGroupId)
                .command(command)
                .cpuCores(1)
                .ramMb(1024)
                .diskGb(10)
                .createdAt(LocalDateTime.now())
                .build();
        
        Instance saved = instanceRepository.save(instance);

        // Launch in compute runtime
        try {
            ComputeRuntime runtime = runtimeFactory.getComputeRuntime();
            LaunchSpec spec = LaunchSpec.builder()
                    .instanceId(saved.getId())
                    .instanceName(saved.getName())
                    .accountId(accountId)
                    .userId(userId)
                    .instanceType(type.name())
                    .cpuCores(1)
                    .ramMb(1024)
                    .diskGb(10)
                    .command(command)
                    .build();

            RuntimeHandle handle = runtime.launch(spec);
            saved.setPid(handle.getPid());
            saved.setContainerId(handle.getContainerId());
            saved.setState(InstanceState.RUNNING);
            saved.setLaunchedAt(LocalDateTime.now());
            saved = instanceRepository.save(saved);

            eventPublisher.publishEvent(new InstanceStartedEvent(this, saved));
        } catch (Exception e) {
            log.error("Failed to launch instance {} on compute runtime: {}", instanceId, e.getMessage());
            saved.setState(InstanceState.FAILED);
            saved = instanceRepository.save(saved);
            eventPublisher.publishEvent(new InstanceFailedEvent(this, saved, e.getMessage()));
            throw new RuntimeException("Compute runtime launch failed: " + e.getMessage(), e);
        }
        
        String username = userRepository.findById(userId).map(User::getUsername).orElse(userId.toString());
        auditService.recordSuccess(username, "EC2", "RunInstances", saved.getName());
        
        return toResponse(saved);
    }

    @Transactional
    public void terminateInstance(UUID instanceId) {
        Instance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new RuntimeException("Instance not found: " + instanceId));
        
        SecurityUtils.validateAccountOwnership(instance.getAccountId());
        checkPermission(instance.getUserId(), "ec2:TerminateInstances", "arn:aws:ec2:*:*:instance/" + instanceId);

        InstanceStateMachine.validateTransition(instance.getState(), InstanceState.TERMINATED);

        ComputeRuntime runtime = runtimeFactory.getComputeRuntime();
        RuntimeHandle handle = RuntimeHandle.builder()
                .instanceId(instance.getId())
                .containerId(instance.getContainerId())
                .pid(instance.getPid())
                .build();
        runtime.terminate(handle);

        instance.setState(InstanceState.TERMINATED);
        instance.setPid(null);
        instanceRepository.save(instance);

        eventPublisher.publishEvent(new InstanceTerminatedEvent(this, instance));
        
        String username = userRepository.findById(instance.getUserId()).map(User::getUsername).orElse(instance.getUserId().toString());
        auditService.recordSuccess(username, "EC2", "TerminateInstances", instance.getName());
    }

    @Transactional
    public InstanceResponse startInstance(UUID instanceId) {
        Instance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new RuntimeException("Instance not found: " + instanceId));
        
        SecurityUtils.validateAccountOwnership(instance.getAccountId());
        checkPermission(instance.getUserId(), "ec2:StartInstances", "arn:aws:ec2:*:*:instance/" + instanceId);
        
        InstanceStateMachine.validateTransition(instance.getState(), InstanceState.RUNNING);
        
        ComputeRuntime runtime = runtimeFactory.getComputeRuntime();
        LaunchSpec spec = LaunchSpec.builder()
                .instanceId(instance.getId())
                .instanceName(instance.getName())
                .accountId(instance.getAccountId())
                .userId(instance.getUserId())
                .instanceType(instance.getType().name())
                .command(instance.getCommand())
                .build();

        RuntimeHandle handle = runtime.launch(spec);
        instance.setState(InstanceState.RUNNING);
        instance.setLaunchedAt(LocalDateTime.now());
        instance.setPid(handle.getPid());
        instance.setContainerId(handle.getContainerId());
        Instance saved = instanceRepository.save(instance);

        eventPublisher.publishEvent(new InstanceStartedEvent(this, saved));
        
        String username = userRepository.findById(instance.getUserId()).map(User::getUsername).orElse(instance.getUserId().toString());
        auditService.recordSuccess(username, "EC2", "StartInstances", instance.getName());
        
        return toResponse(saved);
    }

    @Transactional
    public InstanceResponse stopInstance(UUID instanceId) {
        Instance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new RuntimeException("Instance not found: " + instanceId));

        SecurityUtils.validateAccountOwnership(instance.getAccountId());
        checkPermission(instance.getUserId(), "ec2:StopInstances", "arn:aws:ec2:*:*:instance/" + instanceId);

        InstanceStateMachine.validateTransition(instance.getState(), InstanceState.STOPPED);

        ComputeRuntime runtime = runtimeFactory.getComputeRuntime();
        RuntimeHandle handle = RuntimeHandle.builder()
                .instanceId(instance.getId())
                .containerId(instance.getContainerId())
                .pid(instance.getPid())
                .build();
        runtime.stop(handle);

        instance.setState(InstanceState.STOPPED);
        instance.setPid(null);
        Instance saved = instanceRepository.save(instance);

        eventPublisher.publishEvent(new InstanceStoppedEvent(this, saved));
        
        String username = userRepository.findById(instance.getUserId()).map(User::getUsername).orElse(instance.getUserId().toString());
        auditService.recordSuccess(username, "EC2", "StopInstances", instance.getName());
        
        return toResponse(saved);
    }

    public ExecResponse execCommand(UUID instanceId, ExecRequest request) {
        Instance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new RuntimeException("Instance not found: " + instanceId));

        SecurityUtils.validateAccountOwnership(instance.getAccountId());
        if (instance.getState() != InstanceState.RUNNING) {
            throw new IllegalStateException("Cannot execute command on non-running instance (state=" + instance.getState() + ")");
        }

        ComputeRuntime runtime = runtimeFactory.getComputeRuntime();
        RuntimeHandle handle = RuntimeHandle.builder()
                .instanceId(instance.getId())
                .containerId(instance.getContainerId())
                .pid(instance.getPid())
                .build();

        CommandSpec spec = CommandSpec.builder()
                .command(request.getCommand())
                .timeoutSeconds(request.getTimeoutSeconds() > 0 ? request.getTimeoutSeconds() : 30)
                .maxOutputBytes(request.getMaxOutputBytes() > 0 ? request.getMaxOutputBytes() : 65536)
                .build();

        CommandResult result = runtime.exec(handle, spec);

        String username = SecurityUtils.getAuthenticatedUsername();
        auditService.recordSuccess(username, "EC2", "ExecCommand", instance.getName() + " -> " + request.getCommand());

        return ExecResponse.builder()
                .instanceId(instanceId.toString())
                .exitCode(result.getExitCode())
                .stdout(result.getStdout())
                .stderr(result.getStderr())
                .durationMs(result.getDurationMs())
                .timedOut(result.isTimedOut())
                .build();
    }

    public InstanceResponse getInstance(UUID instanceId) {
        Instance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new RuntimeException("Instance not found: " + instanceId));
        SecurityUtils.validateAccountOwnership(instance.getAccountId());
        return toResponse(instance);
    }

    public List<InstanceResponse> getInstancesForAccount(String accountId) {
        SecurityUtils.validateAccountOwnership(accountId);
        return instanceRepository.findByAccountId(accountId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<InstanceResponse> getActiveInstances() {
        UserPrincipal principal = SecurityUtils.getAuthenticatedPrincipal();
        if (principal.getAccountId() != null) {
            return instanceRepository.findByAccountIdAndStateNot(principal.getAccountId(), InstanceState.TERMINATED).stream()
                    .map(this::toResponse)
                    .collect(Collectors.toList());
        }
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
        if (userId == null) return;
        userRepository.findById(userId).ifPresent(user -> {
            if (!policyEvaluator.isAuthorized(user, action, resource)) {
                auditService.recordFailure(user.getUsername(), "EC2", action, resource, "Access Denied");
                throw new AccessDeniedException("Access Denied: " + action + " on " + resource);
            }
        });
    }
}
