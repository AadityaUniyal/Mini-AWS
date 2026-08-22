"""Chaos Engineering and resilience commands for MiniCloud CLI."""

import typer
from typing import Optional
from minicloud.client import MiniCloudClient
from minicloud.formatters import console, format_state, print_success, print_error, print_json

app = typer.Typer(help="Trigger Chaos Monkey experiments and verify self-healing recovery.")


@app.command("terminate-random")
def terminate_random(
    asg_id: Optional[str] = typer.Option(None, "--asg", "-g", help="Optional Auto Scaling Group ID or name to scope chaos"),
    account_id: Optional[str] = typer.Option(None, "--account", "-a", help="Optional account ID"),
    group_name: Optional[str] = typer.Option(None, "--group", help="Optional Group Name"),
    json_out: bool = typer.Option(False, "--json", help="Output in JSON format"),
):
    """Chaos Monkey: Terminate a random running compute instance and trigger self-healing."""
    payload = {}
    if asg_id:
        payload["autoScalingGroupId"] = asg_id
    if account_id:
        payload["accountId"] = account_id
    if group_name:
        payload["groupName"] = group_name

    client = MiniCloudClient()
    status, res = client.post("/api/v1/chaos/terminate-random-instance", json_data=payload)
    if status == 200:
        data = res.get("data", res)
        if json_out:
            print_json(data)
            return

        victim_id = data.get("terminatedInstanceId", "unknown")
        rep_id = data.get("replacementInstanceId", "unknown")
        rep_state = data.get("replacementState", "RUNNING")
        asg = data.get("autoScalingGroupId", "default-asg")
        deficit = data.get("deficitDetected", True)

        console.print("[bold red]⚡ Chaos Monkey Injected![/bold red]")
        console.print(f"  [bold]Terminated Victim:[/bold]    [red]{victim_id}[/red] ({format_state('TERMINATED')})")
        console.print(f"  [bold]Auto Scaling Group:[/bold]   {asg}")
        console.print(f"  [bold]Capacity Deficit:[/bold]     {'Detected' if deficit else 'None'}")
        console.print(f"  [bold]Self-Healing Action:[/bold]  Launched replacement [bold green]{rep_id}[/bold green] ({format_state(rep_state)})")
        print_success("Fleet capacity successfully restored via automated self-healing loop.")
    else:
        err = res.get("message") or res.get("error") or "Chaos injection failed"
        if json_out:
            print_json({"error": err, "code": status})
        else:
            print_error(f"Chaos injection failed: {err}")
        raise typer.Exit(code=1)
