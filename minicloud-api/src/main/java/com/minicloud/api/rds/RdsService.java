package com.minicloud.api.rds;

import com.minicloud.api.audit.AuditService;
import com.minicloud.api.auth.SecurityUtils;
import com.minicloud.api.domain.RdsInstance;
import com.minicloud.api.domain.RdsRepository;
import com.minicloud.api.domain.UserRepository;
import com.minicloud.api.dto.RdsResponse;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.h2.tools.Server;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RdsService {

    private final RdsRepository rdsRepository;
    private final AuditService auditService;
    private final UserRepository userRepository;

    private final Map<UUID, Server> activeH2Servers = new ConcurrentHashMap<>();

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

    public List<RdsResponse> listInstancesForAccount(String accountId) {
        return rdsRepository.findByAccountId(accountId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public RdsResponse launchInstance(UUID userId, String accountId, String name, String dbName, String masterUsername, String masterPassword, UUID securityGroupId) {
        if (rdsRepository.findByName(name).isPresent()) {
            throw new IllegalArgumentException("RDS instance with name '" + name + "' already exists.");
        }
        int nextPort = findAvailablePort();
        
        RdsInstance instance = RdsInstance.builder()
                .userId(userId)
                .accountId(accountId)
                .name(name)
                .dbName(dbName)
                .masterUsername(masterUsername)
                .masterPassword(masterPassword)
                .port(nextPort)
                .securityGroupId(securityGroupId)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();
        
        RdsInstance saved = rdsRepository.save(instance);

        // Start in background
        new Thread(() -> {
            try {
                startH2Server(saved);
                saved.setStatus("RUNNING");
                rdsRepository.save(saved);

                Thread.sleep(500);
                initializeDatabase(saved);
            } catch (Exception e) {
                log.error("Failed to launch RDS instance {}: {}", saved.getId(), e.getMessage());
                saved.setStatus("FAILED");
                rdsRepository.save(saved);
            }
        }).start();

        String username = SecurityUtils.getAuthenticatedUsername();
        auditService.recordSuccess(username, "RDS", "CreateDBInstance", saved.getName());
        
        return toResponse(saved);
    }

    public RdsResponse stopInstance(UUID id) {
        RdsInstance instance = rdsRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("RDS Instance not found: " + id));

        SecurityUtils.validateAccountOwnership(instance.getAccountId());

        if (!"RUNNING".equals(instance.getStatus())) {
            throw new RuntimeException("RDS Instance is not running");
        }

        stopH2Server(instance.getId());

        instance.setStatus("STOPPED");
        RdsInstance saved = rdsRepository.save(instance);
        
        String username = SecurityUtils.getAuthenticatedUsername();
        auditService.recordSuccess(username, "RDS", "StopDBInstance", saved.getName());
        
        return toResponse(saved);
    }

    public RdsResponse startInstance(UUID id) {
        RdsInstance instance = rdsRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("RDS Instance not found: " + id));

        SecurityUtils.validateAccountOwnership(instance.getAccountId());

        if (!"STOPPED".equals(instance.getStatus()) && !"FAILED".equals(instance.getStatus())) {
            throw new RuntimeException("RDS Instance must be STOPPED to start");
        }

        instance.setStatus("PENDING");
        rdsRepository.save(instance);

        new Thread(() -> {
            try {
                startH2Server(instance);
                instance.setStatus("RUNNING");
                rdsRepository.save(instance);
            } catch (Exception e) {
                log.error("Failed to start RDS instance {}: {}", instance.getId(), e.getMessage());
                instance.setStatus("FAILED");
                rdsRepository.save(instance);
            }
        }).start();

        String username = SecurityUtils.getAuthenticatedUsername();
        auditService.recordSuccess(username, "RDS", "StartDBInstance", instance.getName());
        
        return toResponse(instance);
    }

    public void terminateInstance(UUID id) {
        RdsInstance instance = rdsRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("RDS Instance not found: " + id));
        
        SecurityUtils.validateAccountOwnership(instance.getAccountId());

        stopH2Server(instance.getId());
        
        String username = SecurityUtils.getAuthenticatedUsername();
        auditService.recordSuccess(username, "RDS", "DeleteDBInstance", instance.getName());
        
        rdsRepository.delete(instance);
    }

    private synchronized int findAvailablePort() {
        for (int port = 9000; port < 9200; port++) {
            if (rdsRepository.findByPort(port).isEmpty()) {
                try (ServerSocket socket = new ServerSocket(port)) {
                    return port;
                } catch (Exception ignored) {
                    // Port is in use externally, try next
                }
            }
        }
        throw new RuntimeException("No available ports for new RDS instances");
    }

    private void startH2Server(RdsInstance inst) throws Exception {
        String owner = inst.getAccountId() != null ? inst.getAccountId() : (inst.getUserId() != null ? inst.getUserId().toString() : "default");
        Path dir = Path.of("./minicloud-data/rds", owner).toAbsolutePath().normalize();
        Files.createDirectories(dir);

        Server server = Server.createTcpServer(
                "-tcp",
                "-tcpPort", String.valueOf(inst.getPort()),
                "-tcpAllowOthers",
                "-ifNotExists",
                "-baseDir", dir.toString()
        ).start();

        activeH2Servers.put(inst.getId(), server);
        log.info("Started in-process H2 RDS instance [{}] on port {}", inst.getName(), inst.getPort());
    }

    private void stopH2Server(UUID id) {
        Server server = activeH2Servers.remove(id);
        if (server != null && server.isRunning(false)) {
            server.stop();
            log.info("Stopped in-process H2 RDS instance [{}]", id);
        }
    }

    private void initializeDatabase(RdsInstance inst) {
        String url = String.format("jdbc:h2:tcp://localhost:%d/%s", inst.getPort(), inst.getDbName());
        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            try (Statement stmt = conn.createStatement()) {
                String safeUser = inst.getMasterUsername().replaceAll("[^a-zA-Z0-9_]", "");
                String safePass = inst.getMasterPassword().replace("'", "''");
                stmt.execute("CREATE USER IF NOT EXISTS \"" + safeUser + "\" PASSWORD '" + safePass + "' ADMIN");
            }
        } catch (Exception e) {
            log.warn("RDS DB initialization notice: {}", e.getMessage());
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
        log.info("RDS Service cleanup: shutting down all in-process H2 instances...");
        activeH2Servers.forEach((id, server) -> {
            try {
                if (server.isRunning(false)) {
                    server.stop();
                }
            } catch (Exception ignored) {}
        });
        activeH2Servers.clear();
    }
}
