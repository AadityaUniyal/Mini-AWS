"""Real-time watch commands for tasks and metrics."""

import asyncio
import json
from typing import Optional
import typer
from minicloud.client import MiniCloudClient
from minicloud.config import load_config
from minicloud.formatters import console, format_state, print_error, print_json

app = typer.Typer(help="Stream live events, task logs, and telemetry metrics in real time.")


async def _stream_ws(ws_url: str, json_out: bool):
    try:
        import websockets
        async with websockets.connect(ws_url) as ws:
            console.print(f"[bold green]Connected to live WebSocket stream:[/bold green] {ws_url}")
            console.print("[dim]Press Ctrl+C to exit.[/dim]\n")
            while True:
                msg = await ws.recv()
                try:
                    data = json.loads(msg)
                    if json_out:
                        print_json(data)
                    else:
                        task_id = data.get("taskId") or data.get("id") or "event"
                        task_type = data.get("taskType") or data.get("type") or "TASK"
                        st = data.get("status") or data.get("state")
                        detail = data.get("message") or data.get("output") or data.get("detail") or str(data)
                        console.print(f"[{task_type}] [bold cyan]{task_id}[/bold cyan] ({format_state(st)}): {detail}")
                except Exception:
                    console.print(msg)
    except KeyboardInterrupt:
        console.print("\n[yellow]Watch stopped by user.[/yellow]")
    except Exception as e:
        print_error(f"WebSocket connection error: {e}")


@app.command("tasks")
def watch_tasks(
    ws_endpoint: Optional[str] = typer.Option(None, "--ws-url", help="Custom WebSocket endpoint URL"),
    json_out: bool = typer.Option(False, "--json", help="Output in JSON format"),
):
    """Stream live serverless executions, S3 triggers, and chaos self-healing events."""
    cfg = load_config()
    base = cfg["endpoint"].replace("http://", "ws://").replace("https://", "wss://")
    target_url = ws_endpoint or f"{base}/ws-events/tasks"
    try:
        asyncio.run(_stream_ws(target_url, json_out))
    except KeyboardInterrupt:
        console.print("\n[yellow]Exited.[/yellow]")


@app.command("metrics")
def watch_metrics(
    json_out: bool = typer.Option(False, "--json", help="Output in JSON format"),
):
    """Inspect live host and instance telemetry."""
    client = MiniCloudClient()
    status, res = client.get("/api/v1/monitoring/metrics")
    if status == 200:
        data = res.get("data", res)
        if json_out:
            print_json(data)
        else:
            console.print("[bold]System & Compute Telemetry:[/bold]")
            if isinstance(data, dict):
                for k, v in data.items():
                    console.print(f"  [bold]{k}:[/bold] {v}")
            else:
                console.print(str(data))
    else:
        err = res.get("message") or res.get("error") or "Failed to fetch metrics"
        if json_out:
            print_json({"error": err, "code": status})
        else:
            print_error(f"Metrics fetch failed: {err}")
        raise typer.Exit(code=1)
