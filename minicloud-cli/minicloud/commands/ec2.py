"""EC2 compute commands for MiniCloud CLI."""

import typer
from typing import Optional
from minicloud.client import MiniCloudClient
from minicloud.formatters import console, render_table, format_state, print_success, print_error, print_json

app = typer.Typer(help="Manage virtual EC2 compute instances.")


@app.command("list")
def list_instances(
    json_out: bool = typer.Option(False, "--json", help="Output in JSON format"),
):
    """List all EC2 compute instances."""
    client = MiniCloudClient()
    status, res = client.get("/api/v1/compute/instances")
    if status != 200:
        err = res.get("message") or res.get("error") or "Failed to fetch instances"
        if json_out:
            print_json({"error": err, "code": status})
        else:
            print_error(f"Error fetching instances: {err}")
        raise typer.Exit(code=1)

    instances = res.get("data", res) if isinstance(res, dict) else res
    if not isinstance(instances, list):
        instances = []

    if json_out:
        print_json(instances)
        return

    if not instances:
        console.print("[dim]No EC2 instances found.[/dim]")
        return

    rows = []
    for inst in instances:
        rows.append([
            inst.get("id") or inst.get("instanceId"),
            inst.get("name") or "(unnamed)",
            inst.get("instanceType"),
            format_state(inst.get("state")),
            inst.get("privateIp") or "-",
            inst.get("publicIp") or "-",
        ])

    render_table(
        title="EC2 Compute Instances",
        columns=["Instance ID", "Name", "Type", "State", "Private IP", "Public IP"],
        rows=rows,
    )


@app.command("launch")
def launch_instance(
    instance_type: str = typer.Option("T2_MICRO", "--type", "-t", help="Instance type (e.g. T2_MICRO, T2_SMALL, T2_MEDIUM, M5_XLARGE)"),
    ami: str = typer.Option("ami-alpine-3.18", "--ami", "-a", help="AMI identifier or image name"),
    name: Optional[str] = typer.Option(None, "--name", "-n", help="Optional name tag for the instance"),
    subnet_id: Optional[str] = typer.Option(None, "--subnet", help="Subnet ID"),
    security_group_id: Optional[str] = typer.Option(None, "--sg", help="Security Group ID"),
    command: Optional[str] = typer.Option(None, "--command", "-c", help="Startup shell command/workload"),
    json_out: bool = typer.Option(False, "--json", help="Output in JSON format"),
):
    """Launch a new EC2 virtual instance."""
    client = MiniCloudClient()
    payload = {
        "instanceType": instance_type,
        "ami": ami,
        "name": name or f"instance-{instance_type.lower()}",
        "subnetId": subnet_id,
        "securityGroupId": security_group_id,
        "command": command,
    }
    status, res = client.post("/api/v1/compute/instances", json_data=payload)
    if status in (200, 201):
        data = res.get("data", res)
        if json_out:
            print_json(data)
        else:
            inst_id = data.get("id") or data.get("instanceId")
            print_success(f"Launched instance [bold cyan]{inst_id}[/bold cyan] ({instance_type}) - State: {format_state(data.get('state'))}")
    else:
        err = res.get("message") or res.get("error") or "Failed to launch instance"
        if json_out:
            print_json({"error": err, "code": status})
        else:
            print_error(f"Failed to launch instance: {err}")
        raise typer.Exit(code=1)


@app.command("stop")
def stop_instance(
    instance_id: str = typer.Argument(..., help="Instance ID to stop"),
    json_out: bool = typer.Option(False, "--json", help="Output in JSON format"),
):
    """Stop a running EC2 instance."""
    client = MiniCloudClient()
    status, res = client.post(f"/api/v1/compute/instances/{instance_id}/stop")
    if status == 200:
        data = res.get("data", res)
        if json_out:
            print_json(data)
        else:
            print_success(f"Instance [bold cyan]{instance_id}[/bold cyan] stopped.")
    else:
        err = res.get("message") or res.get("error") or "Failed to stop instance"
        if json_out:
            print_json({"error": err, "code": status})
        else:
            print_error(f"Failed to stop instance: {err}")
        raise typer.Exit(code=1)


@app.command("start")
def start_instance(
    instance_id: str = typer.Argument(..., help="Instance ID to start"),
    json_out: bool = typer.Option(False, "--json", help="Output in JSON format"),
):
    """Start a stopped EC2 instance."""
    client = MiniCloudClient()
    status, res = client.post(f"/api/v1/compute/instances/{instance_id}/start")
    if status == 200:
        data = res.get("data", res)
        if json_out:
            print_json(data)
        else:
            print_success(f"Instance [bold cyan]{instance_id}[/bold cyan] started.")
    else:
        err = res.get("message") or res.get("error") or "Failed to start instance"
        if json_out:
            print_json({"error": err, "code": status})
        else:
            print_error(f"Failed to start instance: {err}")
        raise typer.Exit(code=1)


@app.command("terminate")
def terminate_instance(
    instance_id: str = typer.Argument(..., help="Instance ID to terminate"),
    json_out: bool = typer.Option(False, "--json", help="Output in JSON format"),
):
    """Terminate an EC2 instance permanently."""
    client = MiniCloudClient()
    status, res = client.delete(f"/api/v1/compute/instances/{instance_id}")
    if status in (200, 204):
        if json_out:
            print_json({"status": "terminated", "instanceId": instance_id})
        else:
            print_success(f"Instance [bold cyan]{instance_id}[/bold cyan] terminated.")
    else:
        err = res.get("message") or res.get("error") or "Failed to terminate instance"
        if json_out:
            print_json({"error": err, "code": status})
        else:
            print_error(f"Failed to terminate instance: {err}")
        raise typer.Exit(code=1)
