package com.minicloud.api.compute;

import com.minicloud.api.exception.ResourceNotFoundException;
import com.minicloud.api.iam.User;
import com.minicloud.api.iam.UserRepository;
import com.minicloud.core.dto.InstanceRequest;
import com.minicloud.core.dto.InstanceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
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
    private final UserRepository userRepository;
    private final ProcessManager processManager;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public InstanceResponse launchInstance(InstanceRequest request, String username) throws IOException {
        User user = getUser(username);

        InstanceType type;
        try {
            type = InstanceType.valueOf(request.getType().toUpperCase());
        } catch (Exception e) {
            type = InstanceType.MICRO;
        }

        String command = request.getCommand();
        if (command == null || command.isBlank()) {
            String os = System.getProperty("os.name").toLowerCase();
            command = os.contains("win") ? "ping -t localhost" : "sleep 3600";
        }

        // Create instance record as PENDING
        Instance instance = Instance.builder()
                .name(request.getName())
                .type(type)
                .state(InstanceState.PENDING)
                .userId(user.getId())
                .command(command)
                .launchedAt(LocalDateTime.now())
                .build();
        instanceRepository.save(instance);

        // Final instance variable for use in thread
        final Instance finalInstance = instance;
        final String finalCommand = command;

        // Launch in background
        new Thread(() -> {
            try {
                // BRIEF SLEEP to simulate cloud provisioning delay
                Thread.sleep(2000);
                int pid = processManager.launchProcess(finalCommand);
                finalInstance.setPid(pid);
                finalInstance.setState(InstanceState.RUNNING);
                instanceRepository.save(finalInstance);
                log.info("Instance '{}' transitioned to RUNNING with PID={}", finalInstance.getName(), pid);
            } catch (Exception e) {
                log.error("Failed to launch background process for instance {}: {}", finalInstance.getId(), e.getMessage());
                finalInstance.setState(InstanceState.STOPPED);
                instanceRepository.save(finalInstance);
            }
        }).start();

        return toResponse(instance);
    }

    public List<InstanceResponse> listInstances(String username) {
        User user = getUser(username);
        return instanceRepository.findAllByUserId(user.getId()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public InstanceResponse getInstance(UUID id, String username) {
        User user = getUser(username);
        Instance instance = instanceRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Instance not found: " + id));
        return toResponse(instance);
    }

    public InstanceResponse stopInstance(UUID id, String username) {
        User user = getUser(username);
        Instance instance = instanceRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Instance not found: " + id));

        if (instance.getState() != InstanceState.RUNNING) {
            throw new IllegalArgumentException("Instance is not running: " + instance.getState());
        }

        if (instance.getPid() != null) {
            processManager.stopProcess(instance.getPid());
        }

        instance.setState(InstanceState.STOPPED);
        instanceRepository.save(instance);
        return toResponse(instance);
    }

    public InstanceResponse startInstance(UUID id, String username) throws IOException {
        User user = getUser(username);
        Instance instance = instanceRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Instance not found: " + id));

        if (instance.getState() != InstanceState.STOPPED) {
            throw new IllegalArgumentException("Instance must be STOPPED to start: " + instance.getState());
        }

        instance.setState(InstanceState.PENDING);
        instanceRepository.save(instance);

        final Instance finalInstance = instance;
        new Thread(() -> {
            try {
                Thread.sleep(1500);
                int pid = processManager.launchProcess(finalInstance.getCommand());
                finalInstance.setPid(pid);
                finalInstance.setState(InstanceState.RUNNING);
                finalInstance.setLaunchedAt(LocalDateTime.now());
                instanceRepository.save(finalInstance);
            } catch (Exception e) {
                log.error("Failed to start instance in background: {}", e.getMessage());
                finalInstance.setState(InstanceState.STOPPED);
                instanceRepository.save(finalInstance);
            }
        }).start();

        return toResponse(instance);
    }

    public InstanceResponse terminateInstance(UUID id, String username) {
        User user = getUser(username);
        Instance instance = instanceRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Instance not found: " + id));

        if (instance.getPid() != null) {
            processManager.terminateProcess(instance.getPid());
        }

        instance.setState(InstanceState.TERMINATED);
        instance.setPid(null);
        instanceRepository.save(instance);
        return toResponse(instance);
    }

    public List<String> getConsoleOutput(UUID id, String username) {
        User user = getUser(username);
        Instance instance = instanceRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Instance not found: " + id));

        if (instance.getPid() == null) {
            return List.of("Instance has no associated process (PID is null).");
        }

        return processManager.getConsoleOutput(instance.getPid());
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    private InstanceResponse toResponse(Instance instance) {
        long uptime = 0;
        if (instance.getLaunchedAt() != null && instance.getState() == InstanceState.RUNNING) {
            uptime = ChronoUnit.SECONDS.between(instance.getLaunchedAt(), LocalDateTime.now());
        }
        return InstanceResponse.builder()
                .id(instance.getId().toString())
                .name(instance.getName())
                .type(instance.getType() != null ? instance.getType().name() : "MICRO")
                .state(instance.getState() != null ? instance.getState().name() : "UNKNOWN")
                .pid(instance.getPid())
                .command(instance.getCommand())
                .uptimeSeconds(uptime)
                .launchedAt(instance.getLaunchedAt() != null ? instance.getLaunchedAt().format(FMT) : "")
                .updatedAt(instance.getUpdatedAt() != null ? instance.getUpdatedAt().format(FMT) : "")
                .build();
    }
}
