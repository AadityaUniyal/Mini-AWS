package com.minicloud.api.rds;

import com.minicloud.api.dto.RdsResponse;
import com.minicloud.api.domain.RdsInstance;
import com.minicloud.api.domain.RdsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.minicloud.api.compute.ProcessManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class RdsService {

    private final RdsRepository rdsRepository;
    private final ProcessManager processManager;
    private final com.minicloud.api.audit.AuditService auditService;
    private final com.minicloud.api.domain.UserRepository userRepository;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public List<RdsResponse> listAll() {
        return rdsRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<RdsResponse> listInstances(UUID userId) {
        return rdsRepository.findByUserId(userId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public RdsResponse launchInstance(UUID userId, String name, String dbName, String masterUsername, String masterPassword, UUID securityGroupId) {
        // Check for duplicate name
        if (rdsRepository.findByName(name).isPresent()) {
            throw new IllegalArgumentException("RDS instance with name '" + name + "' already exists.");
        }
        int nextPort = findAvailablePort();
        
        RdsInstance instance = RdsInstance.builder()
                .userId(userId)
                .name(name)
                .dbName(dbName)
                .masterUsername(masterUsername)
                .masterPassword(masterPassword)
                .port(nextPort)
                .securityGroupId(securityGroupId)
                .status("PENDING")
                .build();
        
        rdsRepository.save(instance);

        final RdsInstance finalInstance = instance;
        new Thread(() -> {
            try {
                Thread.sleep(1500); 
                int pid = startH2Process(finalInstance);
                finalInstance.setPid((long) pid);
                finalInstance.setStatus("RUNNING");
                rdsRepository.save(finalInstance);

                Thread.sleep(1000); // Give H2 time to start TCP server
                initializeDatabase(finalInstance);
            } catch (Exception e) {
                log.error("Failed to launch RDS background process for {}: {}", finalInstance.getId(), e.getMessage());
                finalInstance.setStatus("FAILED");
                rdsRepository.save(finalInstance);
            }
        }).start();

        RdsResponse response = toResponse(instance);
        
        String username = userId != null
                ? userRepository.findById(userId).map(u -> u.getUsername()).orElse(userId.toString())
                : "system";
        auditService.recordSuccess(username, "RDS", "CreateDBInstance", instance.getName());
        
        return response;
    }

    public RdsResponse stopInstance(UUID id) {
        RdsInstance instance = rdsRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("RDS Instance not found: " + id));

        if (!"RUNNING".equals(instance.getStatus())) {
            throw new RuntimeException("RDS Instance is not running");
        }

        if (instance.getPid() != null) {
            processManager.terminateProcess(instance.getPid());
            instance.setPid(null);
        }

        instance.setStatus("STOPPED");
        RdsInstance saved = rdsRepository.save(instance);
        
        String username = userRepository.findById(saved.getUserId()).map(u -> u.getUsername()).orElse(saved.getUserId().toString());
        auditService.recordSuccess(username, "RDS", "StopDBInstance", saved.getName());
        
        return toResponse(saved);
    }

    public RdsResponse startInstance(UUID id) {
        RdsInstance instance = rdsRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("RDS Instance not found: " + id));

        if (!"STOPPED".equals(instance.getStatus())) {
            throw new RuntimeException("RDS Instance must be STOPPED to start");
        }

        instance.setStatus("PENDING");
        rdsRepository.save(instance);

        final RdsInstance finalInstance = instance;
        new Thread(() -> {
            try {
                Thread.sleep(1500);
                int pid = startH2Process(finalInstance);
                finalInstance.setPid((long) pid);
                finalInstance.setStatus("RUNNING");
                rdsRepository.save(finalInstance);
            } catch (Exception e) {
                log.error("Failed to start RDS instance background process: {}", e.getMessage());
                finalInstance.setStatus("FAILED");
                rdsRepository.save(finalInstance);
            }
        }).start();

        RdsResponse response = toResponse(instance);
        
        String username = userRepository.findById(instance.getUserId()).map(u -> u.getUsername()).orElse(instance.getUserId().toString());
        auditService.recordSuccess(username, "RDS", "StartDBInstance", instance.getName());
        
        return response;
    }

    public void terminateInstance(UUID id) {
        RdsInstance instance = rdsRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("RDS Instance not found"));
        
        if (instance.getPid() != null) {
            processManager.terminateProcess(instance.getPid());
        }
        
        String username = userRepository.findById(instance.getUserId()).map(u -> u.getUsername()).orElse(instance.getUserId().toString());
        auditService.recordSuccess(username, "RDS", "DeleteDBInstance", instance.getName());
        
        rdsRepository.delete(instance);
    }

    private int findAvailablePort() {
        for (int p = 9000; p < 9100; p++) {
            if (rdsRepository.findByPort(p).isEmpty()) return p;
        }
        throw new RuntimeException("No available ports for new RDS instances");
    }

    private int startH2Process(RdsInstance inst) throws Exception {
        String cp = System.getProperty("java.class.path");
        String command = String.format("java -cp \"%s\" org.h2.tools.Server -tcp -tcpPort %d -tcpAllowOthers -ifNotExists -baseDir ./minicloud-data/rds/%s",
                cp, inst.getPort(), inst.getUserId());
        return processManager.launchProcess(inst.getId().toString(), command);
    }

    private void initializeDatabase(RdsInstance inst) {
        String url = String.format("jdbc:h2:tcp://localhost:%d/%s", inst.getPort(), inst.getDbName());
        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(String.format("CREATE USER IF NOT EXISTS %s PASSWORD '%s' ADMIN", 
                        inst.getMasterUsername(), inst.getMasterPassword()));
            }
        } catch (Exception e) {
            log.error("Failed to initialize RDS database: {}", e.getMessage());
        }
    }

    private RdsResponse toResponse(RdsInstance inst) {
        return RdsResponse.builder()
                .id(inst.getId().toString())
                .name(inst.getName())
                .status(inst.getStatus())
                .port(inst.getPort())
                .endpoint("localhost:" + inst.getPort())
                .databaseName(inst.getDbName())
                .masterUsername(inst.getMasterUsername())
                .securityGroupId(inst.getSecurityGroupId() != null ? inst.getSecurityGroupId().toString() : null)
                .createdAt(inst.getCreatedAt() != null ? inst.getCreatedAt().format(FMT) : "")
                .build();
    }

    @PreDestroy
    public void cleanup() {
        log.info("RDS Service cleanup...");
    }
}
