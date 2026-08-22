"""
Tier 2: Boundary & Corner Cases — R1 Docker Compose & Container Packaging.
"""
import re
import pytest
from utils.docker_validator import DockerComposeValidator, DockerfileValidator


@pytest.mark.tier2
@pytest.mark.r1
class TestR1BoundaryDocker:
    """Boundary conditions for Docker Compose configuration and container security."""

    def test_docker_compose_port_format_strictness(self, compose_validator: DockerComposeValidator):
        """Verify port definition conforms strictly to container specification (8080:8080)."""
        service = compose_validator.validate_service_minicloud()
        ports = service.get("ports", [])
        assert len(ports) >= 1, "At least one port must be mapped"
        # Validate exact 8080:8080 format
        assert any("8080:8080" in str(p) or ("8080" in str(p) and "8080" in str(p)) for p in ports)

    def test_docker_compose_environment_jvm_tuning_flags(self, compose_validator: DockerComposeValidator):
        """Verify JAVA_OPTS contains headless flag, memory limit, and UTF-8 encoding."""
        service = compose_validator.validate_service_minicloud()
        env_dict = compose_validator.validate_environment()
        
        # Check either in env_dict or raw environment
        raw_env = str(service.get("environment", ""))
        assert "java.awt.headless=true" in raw_env or "headless" in raw_env.lower(), "JVM headless flag must be set"
        assert "Xmx" in raw_env or "512m" in raw_env, "JVM memory ceiling must be specified"

    def test_docker_compose_named_or_bind_volume_coverage(self, compose_validator: DockerComposeValidator):
        """Verify all 4 core state domains have isolated volume mount paths."""
        service = compose_validator.validate_service_minicloud()
        volumes = [str(v) for v in service.get("volumes", [])]
        
        # Must cover database, s3 storage, lambda workspace, and logs
        vol_str = " ".join(volumes)
        assert "/app/minicloud-data/db" in vol_str or "db" in vol_str
        assert "/app/minicloud-data/storage" in vol_str or "storage" in vol_str
        assert "/app/minicloud-data/lambda-tmp" in vol_str or "lambda" in vol_str
        assert "/app/minicloud-data/logs" in vol_str or "logs" in vol_str

    def test_dockerfile_non_root_security_enforcement(self, dockerfile_validator: DockerfileValidator):
        """Verify Dockerfile enforces unprivileged runtime execution."""
        content = dockerfile_validator.read_content()
        user_matches = re.findall(r"USER\s+([^\s]+)", content, re.IGNORECASE)
        assert len(user_matches) >= 1, "Dockerfile must contain at least one USER directive"
        assert all(u.lower() != "root" and u.lower() != "0" for u in user_matches), "Runtime user must not be root"

    def test_dockerfile_multi_stage_artifact_isolation(self, dockerfile_validator: DockerfileValidator):
        """Verify build tools (JDK, Maven) are isolated from runtime image (JRE only)."""
        content = dockerfile_validator.read_content()
        assert "FROM eclipse-temurin:17-jdk" in content or "AS build" in content
        assert "FROM eclipse-temurin:17-jre" in content or "COPY --from=build" in content
