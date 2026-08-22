"""Config commands for MiniCloud CLI."""

import typer
from minicloud.config import load_config, save_config, clear_auth
from minicloud.formatters import console, print_success, print_json

app = typer.Typer(help="Manage CLI configuration and target endpoint.")


@app.command("set-endpoint")
def set_endpoint(
    endpoint: str = typer.Argument(..., help="Backend API base URL (e.g. http://localhost:8080)"),
    token: str = typer.Option(None, "--token", "-t", help="Optional authentication JWT token"),
    json_out: bool = typer.Option(False, "--json", help="Output in JSON format"),
):
    """Set the target MiniCloud API endpoint and optional token."""
    cfg = save_config(endpoint=endpoint, token=token)
    if json_out:
        print_json(cfg)
    else:
        print_success(f"Endpoint set to [cyan]{cfg['endpoint']}[/cyan]")
        if cfg.get("token"):
            console.print("  Token updated.")


@app.command("view")
def view(
    json_out: bool = typer.Option(False, "--json", help="Output in JSON format"),
):
    """View active CLI configuration."""
    cfg = load_config()
    if json_out:
        print_json(cfg)
    else:
        console.print("[bold]MiniCloud CLI Configuration:[/bold]")
        console.print(f"  [bold]Endpoint:[/bold] {cfg['endpoint']}")
        console.print(f"  [bold]User:[/bold]     {cfg.get('username') or '(unauthenticated)'}")
        console.print(f"  [bold]Token:[/bold]    {'*' * 16 if cfg.get('token') else '(none)'}")
        console.print(f"  [bold]Account:[/bold]  {cfg.get('account_id') or '(default)'}")


@app.command("clear-token")
def clear_token(
    json_out: bool = typer.Option(False, "--json", help="Output in JSON format"),
):
    """Clear stored authentication credentials."""
    cfg = clear_auth()
    if json_out:
        print_json(cfg)
    else:
        print_success("Stored authentication token cleared.")
