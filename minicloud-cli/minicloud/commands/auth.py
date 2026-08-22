"""Auth commands for MiniCloud CLI."""

import typer
from minicloud.client import MiniCloudClient
from minicloud.config import load_config, save_config, clear_auth
from minicloud.formatters import console, print_success, print_error, print_json

app = typer.Typer(help="Manage user authentication and identity.")


@app.command("login")
def login(
    username: str = typer.Option(..., "--username", "-u", prompt=True, help="MiniCloud username"),
    password: str = typer.Option(..., "--password", "-p", prompt=True, hide_input=True, help="MiniCloud password"),
    json_out: bool = typer.Option(False, "--json", help="Output in JSON format"),
):
    """Authenticate and store JWT credentials."""
    client = MiniCloudClient()
    status, res = client.post("/api/v1/auth/login", json_data={"username": username, "password": password})
    if status == 200:
        data = res.get("data", {})
        token = data.get("token") or res.get("token")
        account_id = data.get("accountId") or data.get("userId")
        save_config(token=token, username=username, account_id=account_id)
        if json_out:
            print_json({"status": "success", "username": username, "token": token, "accountId": account_id})
        else:
            print_success(f"Logged in successfully as [bold cyan]{username}[/bold cyan]")
    else:
        err_msg = res.get("message") or res.get("error") or "Authentication failed"
        if json_out:
            print_json({"status": "error", "message": err_msg, "code": status})
        else:
            print_error(f"Login failed: {err_msg} (HTTP {status})")
        raise typer.Exit(code=1)


@app.command("register")
def register(
    username: str = typer.Option(..., "--username", "-u", prompt=True, help="New username"),
    password: str = typer.Option(..., "--password", "-p", prompt=True, hide_input=True, help="New password"),
    email: str = typer.Option(..., "--email", "-e", prompt=True, help="User email address"),
    json_out: bool = typer.Option(False, "--json", help="Output in JSON format"),
):
    """Register a new user account."""
    client = MiniCloudClient()
    status, res = client.post("/api/v1/auth/register", json_data={"username": username, "password": password, "email": email})
    if status in (200, 201):
        if json_out:
            print_json(res)
        else:
            print_success(f"User [bold cyan]{username}[/bold cyan] registered successfully. You can now login.")
    else:
        err_msg = res.get("message") or res.get("error") or "Registration failed"
        if json_out:
            print_json({"status": "error", "message": err_msg, "code": status})
        else:
            print_error(f"Registration failed: {err_msg} (HTTP {status})")
        raise typer.Exit(code=1)


@app.command("status")
def status(
    json_out: bool = typer.Option(False, "--json", help="Output in JSON format"),
):
    """Check current authentication status against the active endpoint."""
    cfg = load_config()
    token = cfg.get("token")
    if not token:
        if json_out:
            print_json({"authenticated": False, "endpoint": cfg["endpoint"]})
        else:
            console.print("[yellow]Not currently authenticated.[/yellow]")
            console.print(f"Target endpoint: {cfg['endpoint']}")
        return

    client = MiniCloudClient()
    status_code, res = client.get("/api/v1/auth/me")
    if status_code == 200:
        data = res.get("data", res)
        if json_out:
            print_json({"authenticated": True, "user": data, "endpoint": cfg["endpoint"]})
        else:
            print_success(f"Authenticated as [bold cyan]{cfg.get('username') or 'User'}[/bold cyan]")
            console.print(f"  [bold]Endpoint:[/bold] {cfg['endpoint']}")
    else:
        # Fallback local status
        if json_out:
            print_json({"authenticated": True, "localUser": cfg.get("username"), "endpoint": cfg["endpoint"]})
        else:
            console.print(f"[green]Authenticated (local token present)[/green] for [bold cyan]{cfg.get('username')}[/bold cyan]")
            console.print(f"  [bold]Endpoint:[/bold] {cfg['endpoint']}")
