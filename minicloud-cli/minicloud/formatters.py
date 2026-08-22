"""Rich terminal formatters and output helpers."""

import json
from typing import Any, List, Optional
from rich.console import Console
from rich.table import Table
from rich.panel import Panel

console = Console()


def print_json(data: Any):
    console.print(json.dumps(data, indent=2, default=str))


def print_success(message: str):
    console.print(f"[bold green]✓[/bold green] {message}")


def print_error(message: str):
    console.print(f"[bold red]✗[/bold red] {message}")


def print_warning(message: str):
    console.print(f"[bold yellow]![/bold yellow] {message}")


def format_state(state: Optional[str]) -> str:
    if not state:
        return "[dim]-[/dim]"
    st = state.upper()
    if st in ("RUNNING", "COMPLETED", "SUCCESS", "ACTIVE", "ENABLED"):
        return f"[bold green]{st}[/bold green]"
    elif st in ("TERMINATED", "FAILED", "ERROR", "DISABLED"):
        return f"[bold red]{st}[/bold red]"
    elif st in ("PENDING", "STARTING", "STOPPED", "STOPPING"):
        return f"[bold yellow]{st}[/bold yellow]"
    return f"[cyan]{st}[/cyan]"


def render_table(title: str, columns: List[str], rows: List[List[Any]]):
    table = Table(title=title, show_header=True, header_style="bold magenta", border_style="dim")
    for col in columns:
        table.add_column(col)
    for row in rows:
        table.add_row(*[str(item) if item is not None else "-" for item in row])
    console.print(table)
