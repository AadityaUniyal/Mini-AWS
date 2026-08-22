"""
Tier 1: Feature R1 — One-Command Setup (Docker Compose & Dockerfile) Verification.
Source: ORIGINAL_REQUEST §1, PROJECT.md §Feature Inventory R1.
"""
import pytest
from utils.docker_validator import DockerComposeValidator, DockerfileValidator


@pytest.mark.tier1
@pytest.mark.r1
class TestFeatureR1DockerCompose:
    """Validates Docker Compose and Dockerfile specifications for single-command deployment."""

    def test_docker_compose_file_exists_and_valid_yaml(self, compose_validator: DockerComposeValidator):
        """Verify that root docker-compose.yml exists and is valid YAML."""
        assert compose_validator.exists(), "docker-compose.yml should exist at repository root"
        data = compose_validator.parse()
        assert isinstance(data, dict), "docker-compose.yml must parse into a dictionary"
        assert "services" in data, "docker-compose.yml must contain 'services' root key"

    def test_docker_compose_service_minicloud_defined(self, compose_validator: DockerComposeValidator):
        """Verify that 'minicloud' service is defined with build context."""
        service = compose_validator.validate_service_minicloud()
        assert "build" in service, "minicloud service must specify a 'build' section"
        build_cfg = service["build"]
        assert "context" in build_cfg, "build section must specify context"
        assert "dockerfile" in build_cfg or "Dockerfile" in str(build_cfg), "build section must reference Dockerfile"

    def test_docker_compose_port_mapping_8080(self, compose_validator: DockerComposeValidator):
        """Verify that port 8080 is mapped to container port 8080."""
        is_mapped = compose_validator.validate_port_mappings(required_port="8080:8080")
        assert is_mapped, "Port 8080:8080 must be mapped in docker-compose.yml"

    def test_docker_compose_volume_persistence_mounts(self, compose_validator: DockerComposeValidator):
        """Verify that persistent data directories are mounted (db, storage, lambda-tmp, logs)."""
        required = ["db", "storage", "lambda-tmp", "logs"]
        matched = compose_validator.validate_volumes(required_mounts=required)
        for req in required:
            assert req in matched, f"Required persistent volume mount '{req}' missing from docker-compose.yml"

    def test_docker_compose_env_and_healthcheck(self, compose_validator: DockerComposeValidator):
        """Verify that headless WEB mode environment and healthcheck probe are configured."""
        envs = compose_validator.validate_environment()
        assert envs.get("MINICLOUD_MODE") == "WEB", "MINICLOUD_MODE must be set to 'WEB'"
        hc = compose_validator.validate_healthcheck()
        assert hc is not None, "Healthcheck configuration must be present"

    def test_dockerfile_multistage_and_security(self, dockerfile_validator: DockerfileValidator):
        """Verify that Dockerfile uses multi-stage builds and runs as a non-root user."""
        assert dockerfile_validator.exists(), "Dockerfile must exist at Mini-AWS/minicloud-api/Dockerfile"
        assert dockerfile_validator.validate_multistage(), "Dockerfile must use multi-stage build (JDK build -> JRE runtime)"
        assert dockerfile_validator.validate_non_root_user(), "Dockerfile must create and switch to a non-root user (e.g. minicloud)"
        assert dockerfile_validator.validate_exposed_port(8080), "Dockerfile must expose port 8080"
        assert dockerfile_validator.validate_headless_env(), "Dockerfile must set headless mode flags"
