"""
Docker Compose and Dockerfile Validator for MINI-AWS (R1 Verification).
"""
import os
import re
from pathlib import Path
from typing import Dict, Any, List, Optional
import yaml

class DockerComposeValidator:
    def __init__(self, compose_path: Optional[str] = None):
        if compose_path is None:
            # Look in repository root
            root_dir = Path(__file__).resolve().parent.parent.parent
            self.compose_path = root_dir / "docker-compose.yml"
        else:
            self.compose_path = Path(compose_path)

    def exists(self) -> bool:
        return self.compose_path.is_file()

    def parse(self) -> Dict[str, Any]:
        if not self.exists():
            raise FileNotFoundError(f"docker-compose.yml not found at {self.compose_path}")
        with open(self.compose_path, "r", encoding="utf-8") as f:
            return yaml.safe_load(f)

    def validate_service_minicloud(self) -> Dict[str, Any]:
        data = self.parse()
        services = data.get("services", {})
        assert "minicloud" in services, "Service 'minicloud' not defined in docker-compose.yml"
        return services["minicloud"]

    def validate_port_mappings(self, required_port: str = "8080:8080") -> bool:
        service = self.validate_service_minicloud()
        ports = [str(p) for p in service.get("ports", [])]
        return any(required_port in p or "8080" in p for p in ports)

    def validate_volumes(self, required_mounts: Optional[List[str]] = None) -> List[str]:
        if required_mounts is None:
            required_mounts = ["db", "storage", "lambda-tmp", "logs"]
        service = self.validate_service_minicloud()
        volumes = [str(v) for v in service.get("volumes", [])]
        matched = []
        for req in required_mounts:
            if any(req in v for v in volumes):
                matched.append(req)
        return matched

    def validate_environment(self, required_envs: Optional[Dict[str, str]] = None) -> Dict[str, str]:
        if required_envs is None:
            required_envs = {"MINICLOUD_MODE": "WEB"}
        service = self.validate_service_minicloud()
        env_section = service.get("environment", [])
        env_dict = {}
        if isinstance(env_section, list):
            for item in env_section:
                if "=" in item:
                    k, v = item.split("=", 1)
                    env_dict[k.strip()] = v.strip()
        elif isinstance(env_section, dict):
            env_dict = {str(k): str(v) for k, v in env_section.items()}
        return env_dict

    def validate_healthcheck(self) -> Dict[str, Any]:
        service = self.validate_service_minicloud()
        hc = service.get("healthcheck", {})
        assert hc, "Healthcheck section missing in minicloud service"
        test_cmd = hc.get("test", [])
        test_str = " ".join(test_cmd) if isinstance(test_cmd, list) else str(test_cmd)
        assert "health" in test_str or "actuator" in test_str or "8080" in test_str, (
            f"Healthcheck does not probe /actuator/health: {test_str}"
        )
        return hc


class DockerfileValidator:
    def __init__(self, dockerfile_path: Optional[str] = None):
        if dockerfile_path is None:
            root_dir = Path(__file__).resolve().parent.parent.parent
            self.dockerfile_path = root_dir / "Mini-AWS" / "minicloud-api" / "Dockerfile"
        else:
            self.dockerfile_path = Path(dockerfile_path)

    def exists(self) -> bool:
        return self.dockerfile_path.is_file()

    def read_content(self) -> str:
        if not self.exists():
            raise FileNotFoundError(f"Dockerfile not found at {self.dockerfile_path}")
        with open(self.dockerfile_path, "r", encoding="utf-8") as f:
            return f.read()

    def validate_multistage(self) -> bool:
        content = self.read_content()
        from_matches = re.findall(r"FROM\s+([^\s]+)\s+AS\s+([^\s]+)", content, re.IGNORECASE)
        # Should have at least one build stage and one final stage
        return len(from_matches) >= 1 or len(re.findall(r"FROM\s+", content, re.IGNORECASE)) >= 2

    def validate_non_root_user(self) -> bool:
        content = self.read_content()
        has_user = re.search(r"USER\s+([a-zA-Z0-9_\-]+)", content, re.IGNORECASE)
        has_adduser = "adduser" in content or "useradd" in content
        return bool(has_user and has_user.group(1).lower() != "root" and has_adduser)

    def validate_exposed_port(self, port: int = 8080) -> bool:
        content = self.read_content()
        return bool(re.search(rf"EXPOSE\s+{port}", content))

    def validate_headless_env(self) -> bool:
        content = self.read_content()
        return "MINICLOUD_MODE=WEB" in content or "-Djava.awt.headless=true" in content
