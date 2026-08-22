"""Billing and cost optimization commands for MiniCloud CLI."""

import typer
from typing import Optional
from minicloud.client import MiniCloudClient
from minicloud.formatters import console, render_table, print_error, print_json

app = typer.Typer(help="Inspect cost accruals and Rightsizing Advisor recommendations.")


@app.command("summary")
def get_summary(
    account_id: Optional[str] = typer.Option(None, "--account", "-a", help="Optional account ID"),
    json_out: bool = typer.Option(False, "--json", help="Output in JSON format"),
):
    """Get real-time accumulated billing summary."""
    client = MiniCloudClient()
    params = {}
    if account_id:
        params["accountId"] = account_id
    status, res = client.get("/api/v1/billing/summary", params=params)
    if status != 200:
        err = res.get("message") or res.get("error") or "Failed to fetch billing summary"
        if json_out:
            print_json({"error": err, "code": status})
        else:
            print_error(f"Failed to fetch billing summary: {err}")
        raise typer.Exit(code=1)

    data = res.get("data", res)
    if json_out:
        print_json(data)
        return

    console.print("[bold]MiniCloud Billing Summary[/bold]")
    if isinstance(data, dict):
        total_cost = data.get("totalAccruedCost") or data.get("totalCost") or 0.0
        currency = data.get("currency", "USD")
        console.print(f"  [bold]Total Accrued Cost:[/bold] [bold green]${total_cost:.4f} {currency}[/bold green]")
        if data.get("breakdown"):
            console.print("  [bold]Breakdown by Service:[/bold]")
            for k, v in data["breakdown"].items():
                console.print(f"    - {k}: ${v:.4f}")
    else:
        console.print(str(data))


@app.command("estimate")
def get_estimate(
    account_id: Optional[str] = typer.Option(None, "--account", "-a", help="Optional account ID"),
    json_out: bool = typer.Option(False, "--json", help="Output in JSON format"),
):
    """Get projected 30-day monthly cost estimate."""
    client = MiniCloudClient()
    params = {}
    if account_id:
        params["accountId"] = account_id
    status, res = client.get("/api/v1/billing/estimate", params=params)
    if status != 200:
        err = res.get("message") or res.get("error") or "Failed to fetch estimate"
        if json_out:
            print_json({"error": err, "code": status})
        else:
            print_error(f"Failed to fetch estimate: {err}")
        raise typer.Exit(code=1)

    data = res.get("data", res)
    if json_out:
        print_json(data)
        return

    console.print("[bold]MiniCloud Monthly Cost Estimate[/bold]")
    if isinstance(data, dict):
        monthly = data.get("estimatedMonthlyCost") or data.get("estimate") or 0.0
        console.print(f"  [bold]Projected Monthly Cost:[/bold] [bold cyan]${monthly:.2f} USD[/bold cyan]")
    else:
        console.print(str(data))


@app.command("recommendations")
def get_recommendations(
    account_id: Optional[str] = typer.Option(None, "--account", "-a", help="Optional account ID"),
    json_out: bool = typer.Option(False, "--json", help="Output in JSON format"),
):
    """View Telemetry-driven Compute Rightsizing and Cost Optimization recommendations."""
    client = MiniCloudClient()
    params = {}
    if account_id:
        params["accountId"] = account_id
    status, res = client.get("/api/v1/billing/recommendations", params=params)
    if status != 200:
        err = res.get("message") or res.get("error") or "Failed to fetch recommendations"
        if json_out:
            print_json({"error": err, "code": status})
        else:
            print_error(f"Failed to fetch recommendations: {err}")
        raise typer.Exit(code=1)

    data = res.get("data", res)
    if json_out:
        print_json(data)
        return

    if not isinstance(data, dict):
        console.print(str(data))
        return

    total_savings = data.get("totalEstimatedMonthlySavings", 0.0)
    count = data.get("recommendationsCount", 0)
    recs = data.get("recommendations", [])

    console.print("[bold]Compute Optimizer & Rightsizing Advisor[/bold]")
    console.print(f"  [bold]Potential Monthly Savings:[/bold] [bold green]${total_savings:.2f} USD[/bold green]")
    console.print(f"  [bold]Underutilized Instances:[/bold] {count}")

    if not recs:
        console.print("  [dim]All running compute instances are well-utilized (no downsizing recommended).[/dim]")
        return

    rows = []
    for r in recs:
        inst_id = r.get("instanceId", "-")
        inst_name = r.get("instanceName", "-")
        cur_type = r.get("currentInstanceType", "-")
        rec_type = r.get("recommendedInstanceType", "-")
        avg_cpu = f"{r.get('averageCpuUtilization', 0.0):.1f}%"
        cur_cost = f"${r.get('currentHourlyCost', 0.0):.4f}/hr"
        rec_cost = f"${r.get('recommendedHourlyCost', 0.0):.4f}/hr"
        mo_save = f"${r.get('estimatedMonthlySavings', 0.0):.2f}/mo"

        rows.append([
            inst_id,
            inst_name,
            f"{cur_type} -> [bold green]{rec_type}[/bold green]",
            avg_cpu,
            cur_cost,
            rec_cost,
            f"[bold green]{mo_save}[/bold green]",
        ])

    render_table(
        title="Rightsizing Recommendations (<10% CPU Utilization)",
        columns=["Instance ID", "Name", "Type Migration", "Avg CPU", "Current Rate", "Optimized Rate", "Est. Monthly Savings"],
        rows=rows,
    )
