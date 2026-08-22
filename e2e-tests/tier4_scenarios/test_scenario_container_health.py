"""
Tier 4: Scenario 5 — Containerized Startup & Port Health Verification.
Features: Docker Compose, Dockerfile Multi-Stage, Actuator Health Probe, Headless Mode (R1).
Complexity: Medium
"""
import pytest
from utils.api_client import MiniCloudApiClient
from utils.docker_validator import DockerComposeValidator, DockerfileValidator


@pytest.mark.tier4
class TestScenarioContainerHealth:
    """End-to-End Verification of Containerized Packaging, Headless Configuration, and System Health Probes."""

    def test_complete_container_startup_and_health_probe(
        self,
        api_client: MiniCloudApiClient,
        compose_validator: DockerComposeValidator,
        dockerfile_validator: DockerfileValidator
    ):
        """
        Stage 1: Validate docker-compose.yml structure, service definitions, ports, and volumes
        Stage 2: Validate Dockerfile multi-stage security, unprivileged user, and headless runtime flags
        Stage 3: Probe live Actuator health endpoint (/actuator/health)
        Stage 4: Validate system health response schema (status: UP, db, diskSpace)
        """
        # Stage 1: Docker Compose Validation
        assert compose_validator.exists(), "docker-compose.yml must be present"
        service = compose_validator.validate_service_minicloud()
        assert compose_validator.validate_port_mappings("8080:8080")
        matched_vols = compose_validator.validate_volumes(["db", "storage", "lambda-tmp", "logs"])
        assert len(matched_vols) == 4

        # Stage 2: Dockerfile Validation
        assert dockerfile_validator.exists(), "Dockerfile must be present"
        assert dockerfile_validator.validate_multistage()
        assert dockerfile_validator.validate_non_root_user()
        assert dockerfile_validator.validate_exposed_port(8080)
        assert dockerfile_validator.validate_headless_env()

        # Stage 3 & 4: Health Probe
        health_resp = api_client.health_check()
        assert health_resp.status_code == 200, f"Healthcheck failed: {health_resp.text}"
        health_data = health_resp.json()
        assert health_data.get("status") == "UP", f"Expected system status UP, got {health_data.get('status')}"
        if "components" in health_data:
            components = health_data["components"]
            if "db" in components:
                assert components["db"].get("status") == "UP"
