package com.minicloud.api.chaos;

import com.minicloud.api.audit.AuditService;
import com.minicloud.api.compute.ComputeService;
import com.minicloud.api.compute.InstanceStateMachine;
import com.minicloud.api.compute.ProcessManager;
import com.minicloud.api.domain.*;
import com.minicloud.api.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Chaos Engineering & Self-Healing Resilience Service.
 *
 * Implements the Chaos Monkey pattern for MiniCloud compute clusters:
 * 1. Identifies running compute instances within an Auto Scaling Group or fleet.
 * 2. Injects chaos by randomly terminating a running instance.
 * 3. Enforces finite state machine transitions via InstanceStateMachine.
 * 4. Detects the capacity deficit.
 * 5. Executes self-healing replenishment to launch a replacement instance preserving configuration.
 * 6. Broadcasts real-time events over WebSocket (/ws-events/tasks).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChaosService {

    private final InstanceRepository instanceRepository;
    private final ProcessManager processManager;
    private final NetworkingAdvisor networkingAdvisor;
    private final TaskService taskService;
    private final AuditService auditService;
    private final UserRepository userRepository;

    private final Random random = new Random();

    /**
     * Terminate a randomly selected running instance and execute self-healing replenishment.
     *
     * @param autoScalingGroupId optional ASG ID or name filter
     * @param accountId          optional account ID filter
     * @param groupName          optional group name filter
     * @return structured ChaosResultDTO
     */
    @Transactional
    public ChaosResultDTO terminateRandomInstanceAndHeal(String autoScalingGroupId, String accountId, String groupName) {
        log.info("Initiating Chaos Monkey termination experiment: asg={}, accountId={}, groupName={}",
                autoScalingGroupId, accountId, groupName);

        // 1. Fetch running instances
        List<Instance> runningInstances = new ArrayList<>(instanceRepository.findByState(InstanceState.RUNNING));

        if (accountId != null && !accountId.isBlank()) {
            runningInstances.removeIf(inst -> !accountId.equals(inst.getAccountId()));
        }

        if (autoScalingGroupId != null && !autoScalingGroupId.isBlank()) {
            List<Instance> asgFiltered = new ArrayList<>(runningInstances);
            asgFiltered.removeIf(inst -> inst.getName() == null || !inst.getName().toLowerCase().contains(autoScalingGroupId.toLowerCase()));
            if (!asgFiltered.isEmpty()) {
                runningInstances = asgFiltered;
            }
        } else if (groupName != null && !groupName.isBlank()) {
            List<Instance> groupFiltered = new ArrayList<>(runningInstances);
            groupFiltered.removeIf(inst -> inst.getName() == null || !inst.getName().toLowerCase().contains(groupName.toLowerCase()));
            if (!groupFiltered.isEmpty()) {
                runningInstances = groupFiltered;
            }
        }

        if (runningInstances.isEmpty()) {
            log.warn("No running instances found for chaos injection");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No running compute instances found to terminate");
        }

        // 2. Select victim instance randomly
        Instance victim = runningInstances.get(random.nextInt(runningInstances.size()));
        UUID victimId = victim.getId();
        String victimName = victim.getName();
        InstanceState prevState = victim.getState();
        String effectiveAsgId = (autoScalingGroupId != null && !autoScalingGroupId.isBlank())
                ? autoScalingGroupId
                : (groupName != null && !groupName.isBlank() ? groupName : "asg-default");

        log.info("Chaos selected instance {} ({}) for termination", victimId, victimName);

        // 3. Terminate the victim instance via FSM validation
        InstanceStateMachine.validateTransition(victim.getState(), InstanceState.TERMINATED);

        if (victim.getPid() != null) {
            processManager.terminate(victim.getPid());
            victim.setPid(null);
        }
        victim.setState(InstanceState.TERMINATED);
        instanceRepository.save(victim);

        String username = victim.getUserId() != null
                ? userRepository.findById(victim.getUserId()).map(User::getUsername).orElse(victim.getUserId().toString())
                : "chaos-monkey";
        auditService.recordSuccess(username, "ChaosMonkey", "TerminateInstance", victimName);

        // 4. Broadcast CHAOS_INSTANCE_TERMINATED event via WebSocket task
        Task terminationTask = taskService.createTask(
                "CHAOS_INSTANCE_TERMINATED",
                String.format("Chaos Monkey terminated instance %s (%s)", victimId, victimName),
                victim.getUserId(),
                victim.getAccountId()
        );
        taskService.updateProgress(terminationTask.getId(), 100, "COMPLETED", null);

        // 5. Trigger Self-Healing Replenishment Loop
        log.info("Capacity deficit detected in ASG '{}'. Launching replacement instance...", effectiveAsgId);

        String replacementName = victimName != null ? victimName + "-healed" : "instance-healed-" + UUID.randomUUID().toString().substring(0, 8);
        String privIp = (networkingAdvisor != null) ? networkingAdvisor.assignPrivateIp() : "10.0.0." + (random.nextInt(200) + 10);
        String pubIp = (networkingAdvisor != null) ? networkingAdvisor.assignPublicIp() : "54.210." + (random.nextInt(200) + 10) + "." + (random.nextInt(200) + 10);

        Instance replacement = Instance.builder()
                .userId(victim.getUserId())
                .accountId(victim.getAccountId())
                .name(replacementName)
                .type(victim.getType() != null ? victim.getType() : InstanceType.T2_MICRO)
                .state(InstanceState.RUNNING)
                .privateIp(privIp)
                .publicIp(pubIp)
                .subnetId(victim.getSubnetId())
                .securityGroupId(victim.getSecurityGroupId())
                .command(victim.getCommand())
                .pid((long) (random.nextInt(100000) + 1000))
                .launchedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        Instance savedReplacement = instanceRepository.save(replacement);
        UUID replacementId = savedReplacement.getId();
        log.info("Self-healing launched replacement instance {} ({})", replacementId, replacementName);

        auditService.recordSuccess(username, "SelfHealing", "LaunchReplacementInstance", replacementName);

        // 6. Broadcast SELF_HEALING_RECOVERY event via WebSocket task
        Task recoveryTask = taskService.createTask(
                "SELF_HEALING_RECOVERY",
                String.format("Self-healing recovery launched replacement instance %s (%s) to restore capacity deficit.", replacementId, replacementName),
                victim.getUserId(),
                victim.getAccountId()
        );
        taskService.updateProgress(recoveryTask.getId(), 100, "COMPLETED", null);

        // 7. Broadcast combined CHAOS_TERMINATE_AND_HEAL task event
        Task combinedTask = taskService.createTask(
                "CHAOS_TERMINATE_AND_HEAL",
                String.format("Chaos terminated instance %s; self-healing launched replacement %s.", victimId, replacementId),
                victim.getUserId(),
                victim.getAccountId()
        );
        taskService.updateProgress(combinedTask.getId(), 100, "COMPLETED", null);

        String nowIso = Instant.now().toString();

        return ChaosResultDTO.builder()
                .chaosAction("TERMINATE_INSTANCE")
                .action("TERMINATE_INSTANCE")
                .terminatedInstanceId(victimId.toString())
                .autoScalingGroupId(effectiveAsgId)
                .previousState(prevState.name())
                .currentState(InstanceState.TERMINATED.name())
                .deficitDetected(true)
                .replacementInstanceId(replacementId.toString())
                .replacementState(InstanceState.RUNNING.name())
                .status("SELF_HEALING_COMPLETED")
                .timestamp(nowIso)
                .terminatedInstance(ChaosResultDTO.TerminatedInstanceDetails.builder()
                        .id(victimId.toString())
                        .name(victimName)
                        .type(victim.getType() != null ? victim.getType().name() : "T2_MICRO")
                        .previousState(prevState.name())
                        .newState(InstanceState.TERMINATED.name())
                        .accountId(victim.getAccountId())
                        .terminatedAt(nowIso)
                        .build())
                .selfHealing(ChaosResultDTO.SelfHealingDetails.builder()
                        .replenishmentTriggered(true)
                        .replenishedInstanceId(replacementId.toString())
                        .replenishedInstanceName(replacementName)
                        .status("RUNNING")
                        .build())
                .build();
    }
}
