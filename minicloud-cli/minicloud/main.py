"""MiniCloud CLI — Unified Command Line Interface for MINI-AWS (MiniCloud)."""

import typer
from rich.console import Console

from minicloud import __version__
from minicloud.commands import auth, config, ec2, s3, lambda_cmd, billing, chaos, watch

app = typer.Typer(
    name="minicloud",
    help="MINI-AWS (MiniCloud) CLI — Powerful command-line tool for local cloud emulation.",
    add_completion=False,
)

console = Console()

# Register subcommands
app.add_typer(config.app, name="config", help="Manage CLI configuration and target endpoint.")
app.add_typer(auth.app, name="auth", help="Manage authentication and user identity.")
app.add_typer(ec2.app, name="ec2", help="Manage EC2 compute instances.")
app.add_typer(s3.app, name="s3", help="Manage S3 object storage buckets and files.")
app.add_typer(lambda_cmd.app, name="lambda", help="Manage serverless Lambda functions and event triggers.")
app.add_typer(billing.app, name="billing", help="Inspect cost telemetry and Rightsizing recommendations.")
app.add_typer(chaos.app, name="chaos", help="Trigger Chaos Monkey experiments and auto-healing resilience.")
app.add_typer(watch.app, name="watch", help="Stream live execution logs and real-time telemetry.")


@app.callback(invoke_without_command=True)
def main(
    ctx: typer.Context,
    version: bool = typer.Option(False, "--version", "-v", help="Show MiniCloud CLI version"),
):
    if version:
        console.print(f"[bold cyan]MiniCloud CLI[/bold cyan] version [bold green]{__version__}[/bold green]")
        raise typer.Exit()
    if ctx.invoked_subcommand is None:
        console.print("[bold cyan]MiniCloud CLI[/bold cyan] — Type [bold green]minicloud --help[/bold green] for available commands.")


if __name__ == "__main__":
    app()
