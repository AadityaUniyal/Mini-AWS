import com.minicloud.api.iam.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PolicyRepository policyRepository;
    private final PasswordEncoder passwordEncoder;

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
                .statements(List.of(
                        PolicyStatement.builder()
                                .effect(PolicyStatement.Effect.ALLOW)
                                .action("*")
                                .resource("*")
                                .build()
                ))
                .build();

        // 2. S3ReadOnlyAccess
        Policy s3ReadOnly = Policy.builder()
                .name("S3ReadOnlyAccess")
                .description("Provides read-only access to MiniS3 buckets and objects.")
                .managed(true)
                .statements(List.of(
                        PolicyStatement.builder()
                                .effect(PolicyStatement.Effect.ALLOW)
                                .action("s3:List*")
                                .resource("*")
                                .build(),
                        PolicyStatement.builder()
                                .effect(PolicyStatement.Effect.ALLOW)
                                .action("s3:Get*")
                                .resource("*")
                                .build()
                ))
                .build();

        // 3. EC2FullAccess
        Policy ec2Full = Policy.builder()
                .name("EC2FullAccess")
                .description("Provides full access to MiniEC2 instances and security groups.")
                .managed(true)
                .statements(List.of(
                        PolicyStatement.builder()
                                .effect(PolicyStatement.Effect.ALLOW)
                                .action("ec2:*")
                                .resource("*")
                                .build()
                ))
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
                    .passwordHash(passwordEncoder.encode("admin123"))
                    .role(UserRole.ADMIN)
                    .policies(Set.of(adminPolicy))
                    .build();

            userRepository.save(admin);
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("  MiniCloud initialized — default admin created");
            log.info("  Username : admin");
            log.info("  Password : admin123");
            log.info("  Swagger  : http://localhost:8080/swagger-ui.html");
            log.info("  H2 DB    : http://localhost:8080/h2-console");
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        } else {
            log.info("MiniCloud API started — {} users in system", userRepository.count());
        }
    }
}
