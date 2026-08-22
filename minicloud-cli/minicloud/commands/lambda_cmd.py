"""Lambda serverless commands for MiniCloud CLI."""

import json
import typer
from pathlib import Path
from typing import Optional, List
from minicloud.client import MiniCloudClient
from minicloud.formatters import console, render_table, format_state, print_success, print_error, print_json

app = typer.Typer(help="Manage serverless Lambda functions and S3 event triggers.")
trigger_app = typer.Typer(help="Manage S3-to-Lambda event trigger subscriptions.")
app.add_typer(trigger_app, name="trigger")


@app.command("list")
def list_functions(
    json_out: bool = typer.Option(False, "--json", help="Output in JSON format"),
):
    """List all registered Lambda functions."""
    client = MiniCloudClient()
    status, res = client.get("/api/v1/lambda/functions")
    if status != 200:
        err = res.get("message") or res.get("error") or "Failed to list functions"
        if json_out:
            print_json({"error": err, "code": status})
        else:
            print_error(f"Failed to list Lambda functions: {err}")
        raise typer.Exit(code=1)

    functions = res.get("data", res) if isinstance(res, dict) else res
    if not isinstance(functions, list):
        functions = []

    if json_out:
        print_json(functions)
        return

    if not functions:
        console.print("[dim]No Lambda functions found.[/dim]")
        return

    rows = []
    for fn in functions:
        name = fn.get("functionName") or fn.get("name")
        runtime = fn.get("runtime") or "-"
        handler = fn.get("handler") or "-"
        timeout = fn.get("timeoutSeconds") or fn.get("timeout") or 30
        memory = fn.get("memoryMb") or fn.get("memorySize") or 128
        rows.append([name, runtime, handler, f"{timeout}s", f"{memory} MB"])

    render_table(
        title="Lambda Serverless Functions",
        columns=["Function Name", "Runtime", "Handler", "Timeout", "Memory"],
        rows=rows,
    )


@app.command("create")
def create_function(
    name: str = typer.Option(..., "--name", "-n", help="Unique function name"),
    runtime: str = typer.Option("PYTHON3", "--runtime", "-r", help="Runtime environment (e.g. PYTHON3, NODEJS, JAVA17, BINARY)"),
    file_path: Optional[str] = typer.Option(None, "--file", "-f", help="Path to function script file"),
    code: Optional[str] = typer.Option(None, "--code", "-c", help="Inline function source code"),
    handler: str = typer.Option("handler", "--handler", "-h", help="Entry point handler name"),
    timeout: int = typer.Option(30, "--timeout", "-t", help="Timeout in seconds"),
    memory: int = typer.Option(128, "--memory", "-m", help="Memory allocation in MB"),
    json_out: bool = typer.Option(False, "--json", help="Output in JSON format"),
):
    """Create or register a serverless Lambda function."""
    function_code = code
    if file_path:
        p = Path(file_path)
        if not p.exists():
            print_error(f"File '{file_path}' not found.")
            raise typer.Exit(code=1)
        with open(p, "r", encoding="utf-8") as f:
            function_code = f.read()

    if not function_code:
        # Default starter code for python
        function_code = (
            "import json\n"
            "def handler(event, context):\n"
            "    print(f'Processing event: {event}')\n"
            "    return {'statusCode': 200, 'body': json.dumps({'message': 'Success'})}\n"
        )

    payload = {
        "functionName": name,
        "runtime": runtime.upper(),
        "code": function_code,
        "handler": handler,
        "timeoutSeconds": timeout,
        "memoryMb": memory,
    }

    client = MiniCloudClient()
    status, res = client.post("/api/v1/lambda/functions", json_data=payload)
    if status in (200, 201):
        data = res.get("data", res)
        if json_out:
            print_json(data)
        else:
            print_success(f"Lambda function [bold cyan]{name}[/bold cyan] ({runtime}) created successfully.")
    else:
        err = res.get("message") or res.get("error") or "Failed to create function"
        if json_out:
            print_json({"error": err, "code": status})
        else:
            print_error(f"Failed to create Lambda function: {err}")
        raise typer.Exit(code=1)


@app.command("invoke")
def invoke_function(
    name: str = typer.Argument(..., help="Function name to invoke"),
    payload: str = typer.Option("{}", "--payload", "-p", help="JSON payload string to pass to function"),
    json_out: bool = typer.Option(False, "--json", help="Output in JSON format"),
):
    """Invoke a Lambda function synchronously or with a payload."""
    try:
        payload_obj = json.loads(payload)
    except Exception:
        payload_obj = {"input": payload}

    client = MiniCloudClient()
    status, res = client.post(f"/api/v1/lambda/functions/{name}/invoke", json_data=payload_obj)
    if status == 200:
        data = res.get("data", res)
        if json_out:
            print_json(data)
        else:
            print_success(f"Function [bold cyan]{name}[/bold cyan] executed.")
            out = data.get("result") or data.get("output") or data.get("response") or data
            console.print(f"[bold]Result:[/bold] {out}")
            if data.get("logs"):
                console.print(f"[dim]Logs:[/dim]\n{data.get('logs')}")
    else:
        err = res.get("message") or res.get("error") or "Invocation failed"
        if json_out:
            print_json({"error": err, "code": status})
        else:
            print_error(f"Invocation failed: {err}")
        raise typer.Exit(code=1)


@app.command("logs")
def get_logs(
    name: str = typer.Argument(..., help="Function name"),
    json_out: bool = typer.Option(False, "--json", help="Output in JSON format"),
):
    """Fetch execution logs for a Lambda function."""
    client = MiniCloudClient()
    status, res = client.get(f"/api/v1/lambda/functions/{name}/logs")
    if status == 200:
        data = res.get("data", res)
        if json_out:
            print_json(data)
        else:
            console.print(f"[bold]Execution Logs for {name}:[/bold]")
            if isinstance(data, list):
                for item in data:
                    console.print(f"  {item}")
            else:
                console.print(str(data))
    else:
        err = res.get("message") or res.get("error") or "Failed to fetch logs"
        if json_out:
            print_json({"error": err, "code": status})
        else:
            print_error(f"Failed to fetch logs: {err}")
        raise typer.Exit(code=1)


@app.command("delete")
def delete_function(
    name: str = typer.Argument(..., help="Function name to delete"),
    json_out: bool = typer.Option(False, "--json", help="Output in JSON format"),
):
    """Delete a Lambda function."""
    client = MiniCloudClient()
    status, res = client.delete(f"/api/v1/lambda/functions/{name}")
    if status in (200, 204):
        if json_out:
            print_json({"status": "deleted", "functionName": name})
        else:
            print_success(f"Lambda function [bold cyan]{name}[/bold cyan] deleted.")
    else:
        err = res.get("message") or res.get("error") or "Failed to delete function"
        if json_out:
            print_json({"error": err, "code": status})
        else:
            print_error(f"Failed to delete function: {err}")
        raise typer.Exit(code=1)


# Trigger subcommands
@trigger_app.command("add")
def add_trigger(
    bucket: str = typer.Option(..., "--bucket", "-b", help="Source S3 bucket name"),
    function: str = typer.Option(..., "--function", "-f", help="Target Lambda function name"),
    events: str = typer.Option("s3:ObjectCreated:*", "--events", "-e", help="Comma-separated event patterns"),
    json_out: bool = typer.Option(False, "--json", help="Output in JSON format"),
):
    """Register an S3-to-Lambda event trigger."""
    event_list = [ev.strip() for ev in events.split(",") if ev.strip()]
    payload = {
        "bucketName": bucket.replace("s3://", "").strip("/"),
        "functionName": function,
        "events": event_list,
        "enabled": True,
    }
    client = MiniCloudClient()
    # Try storage/triggers first, fallback to lambda/triggers
    status, res = client.post("/api/v1/storage/triggers", json_data=payload)
    if status not in (200, 201):
        status, res = client.post("/api/v1/lambda/triggers", json_data=payload)

    if status in (200, 201):
        data = res.get("data", res)
        if json_out:
            print_json(data)
        else:
            trig_id = data.get("id", "created")
            print_success(f"Created trigger [bold cyan]{trig_id}[/bold cyan]: s3://{bucket} -> {function} ({events})")
    else:
        err = res.get("message") or res.get("error") or "Failed to create trigger"
        if json_out:
            print_json({"error": err, "code": status})
        else:
            print_error(f"Failed to create trigger: {err}")
        raise typer.Exit(code=1)


@trigger_app.command("list")
def list_triggers(
    bucket: Optional[str] = typer.Option(None, "--bucket", "-b", help="Optional bucket name to filter triggers"),
    json_out: bool = typer.Option(False, "--json", help="Output in JSON format"),
):
    """List S3-to-Lambda event triggers."""
    client = MiniCloudClient()
    params = {}
    if bucket:
        params["bucketName"] = bucket.replace("s3://", "").strip("/")
    status, res = client.get("/api/v1/storage/triggers", params=params)
    if status != 200:
        status, res = client.get("/api/v1/lambda/triggers", params=params)

    if status != 200:
        err = res.get("message") or res.get("error") or "Failed to list triggers"
        if json_out:
            print_json({"error": err, "code": status})
        else:
            print_error(f"Failed to list triggers: {err}")
        raise typer.Exit(code=1)

    triggers = res.get("data", res) if isinstance(res, dict) else res
    if not isinstance(triggers, list):
        triggers = []

    if json_out:
        print_json(triggers)
        return

    if not triggers:
        console.print("[dim]No S3 event triggers configured.[/dim]")
        return

    rows = []
    for t in triggers:
        tid = t.get("id", "-")
        bname = t.get("bucketName", "-")
        fname = t.get("functionName", "-")
        evs = ",".join(t.get("events", [])) if isinstance(t.get("events"), list) else str(t.get("events", "-"))
        en = format_state("ENABLED" if t.get("enabled", True) else "DISABLED")
        rows.append([tid, bname, fname, evs, en])

    render_table(
        title="S3-to-Lambda Event Triggers",
        columns=["Trigger ID", "Bucket", "Target Function", "Events", "Status"],
        rows=rows,
    )


@trigger_app.command("delete")
def delete_trigger(
    trigger_id: str = typer.Argument(..., help="Trigger UUID to delete"),
    json_out: bool = typer.Option(False, "--json", help="Output in JSON format"),
):
    """Delete an S3-to-Lambda event trigger."""
    client = MiniCloudClient()
    status, res = client.delete(f"/api/v1/storage/triggers/{trigger_id}")
    if status not in (200, 204):
        status, res = client.delete(f"/api/v1/lambda/triggers/{trigger_id}")

    if status in (200, 204):
        if json_out:
            print_json({"status": "deleted", "triggerId": trigger_id})
        else:
            print_success(f"Trigger [bold cyan]{trigger_id}[/bold cyan] deleted.")
    else:
        err = res.get("message") or res.get("error") or "Failed to delete trigger"
        if json_out:
            print_json({"error": err, "code": status})
        else:
            print_error(f"Failed to delete trigger: {err}")
        raise typer.Exit(code=1)
