package com.minicloud.api.config;

import com.minicloud.api.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(10)
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PolicyRepository policyRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.minicloud.api.route.VpcService vpcService;

    @Override
    public void run(String... args) throws Exception {
        seedPolicies();
        seedUsers();
    }

    private void seedPolicies() {
        if (policyRepository.count() > 0) return;

        log.info("Seeding default IAM policies...");

        // 1. AdministratorAccess
        Policy adminPolicy = Policy.builder()
                .name("AdministratorAccess")
                .description("Provides full access to MiniCloud services and resources.")
                .managed(true)
                .document("{\"Version\": \"2012-10-17\", \"Statement\": [{\"Effect\": \"Allow\", \"Action\": \"*\", \"Resource\": \"*\"}]}")
                .build();

        // 2. S3ReadOnlyAccess
        Policy s3ReadOnly = Policy.builder()
                .name("S3ReadOnlyAccess")
                .description("Provides read-only access to MiniS3 buckets and objects.")
                .managed(true)
                .document("{\"Version\": \"2012-10-17\", \"Statement\": [{\"Effect\": \"Allow\", \"Action\": [\"s3:List*\", \"s3:Get*\"], \"Resource\": \"*\"}]}")
                .build();

        // 3. EC2FullAccess
        Policy ec2Full = Policy.builder()
                .name("EC2FullAccess")
                .description("Provides full access to MiniEC2 instances and security groups.")
                .managed(true)
                .document("{\"Version\": \"2012-10-17\", \"Statement\": [{\"Effect\": \"Allow\", \"Action\": \"ec2:*\", \"Resource\": \"*\"}]}")
                .build();

        policyRepository.saveAll(List.of(adminPolicy, s3ReadOnly, ec2Full));
    }

    private void seedUsers() {
        // Seed default admin user if no users exist
        if (userRepository.count() == 0) {
            Policy adminPolicy = policyRepository.findByName("AdministratorAccess")
                    .orElseThrow(() -> new RuntimeException("AdministratorAccess policy not found"));

            User admin = User.builder()
                    .username("admin")
                    .email("admin@minicloud.io")
                    .accountId("123456789012")
                    .passwordHash(passwordEncoder.encode("admin123"))
                    .role(UserRole.ADMIN)
                    .rootUser(true)
                    .enabled(true)
                    .policies(Set.of(adminPolicy))
                    .build();

            userRepository.save(admin);

            // Provision default networking for the admin account
            vpcService.createDefaultVpc("123456789012");

            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("  MiniCloud initialized — default admin created");
            log.info("  Account ID: 123456789012");
            log.info("  Email     : admin@minicloud.io");
            log.info("  Username  : admin");
            log.info("  Password  : admin123");
            log.info("  Swagger  : http://localhost:8080/swagger-ui.html");
            log.info("  H2 DB    : http://localhost:8080/h2-console");
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        } else {
            log.info("MiniCloud API started — {} users in system", userRepository.count());
        }
    }
}
