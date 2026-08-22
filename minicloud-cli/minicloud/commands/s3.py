"""S3 storage commands for MiniCloud CLI."""

import os
import typer
from pathlib import Path
from typing import Optional
from minicloud.client import MiniCloudClient
from minicloud.formatters import console, render_table, print_success, print_error, print_json

app = typer.Typer(help="Manage S3 object storage buckets and files.")


@app.command("mb")
def make_bucket(
    bucket_name: str = typer.Argument(..., help="Bucket name to create"),
    json_out: bool = typer.Option(False, "--json", help="Output in JSON format"),
):
    """Create a new S3 storage bucket."""
    client = MiniCloudClient()
    status, res = client.post("/api/v1/storage/buckets", json_data={"bucketName": bucket_name})
    if status in (200, 201):
        data = res.get("data", res)
        if json_out:
            print_json(data)
        else:
            print_success(f"Bucket [bold cyan]s3://{bucket_name}[/bold cyan] created.")
    else:
        err = res.get("message") or res.get("error") or "Failed to create bucket"
        if json_out:
            print_json({"error": err, "code": status})
        else:
            print_error(f"Failed to create bucket: {err}")
        raise typer.Exit(code=1)


@app.command("ls")
def list_buckets_or_objects(
    bucket_name: Optional[str] = typer.Argument(None, help="Optional bucket name to list objects inside"),
    json_out: bool = typer.Option(False, "--json", help="Output in JSON format"),
):
    """List S3 buckets or objects within a bucket."""
    client = MiniCloudClient()
    if not bucket_name:
        # List all buckets
        status, res = client.get("/api/v1/storage/buckets")
        if status != 200:
            err = res.get("message") or res.get("error") or "Failed to list buckets"
            if json_out:
                print_json({"error": err, "code": status})
            else:
                print_error(f"Failed to list buckets: {err}")
            raise typer.Exit(code=1)

        buckets = res.get("data", res) if isinstance(res, dict) else res
        if not isinstance(buckets, list):
            buckets = []

        if json_out:
            print_json(buckets)
            return

        if not buckets:
            console.print("[dim]No S3 buckets found.[/dim]")
            return

        rows = []
        for b in buckets:
            name = b.get("name") or b.get("bucketName")
            created = b.get("createdAt") or b.get("creationDate") or "-"
            count = b.get("objectCount", "-")
            rows.append([name, str(count), str(created)])

        render_table(title="S3 Storage Buckets", columns=["Bucket Name", "Objects", "Created At"], rows=rows)
    else:
        # List objects in bucket
        clean_name = bucket_name.replace("s3://", "").strip("/")
        status, res = client.get(f"/api/v1/storage/buckets/{clean_name}/objects")
        if status != 200:
            err = res.get("message") or res.get("error") or f"Failed to list objects in {clean_name}"
            if json_out:
                print_json({"error": err, "code": status})
            else:
                print_error(f"Failed to list objects in s3://{clean_name}: {err}")
            raise typer.Exit(code=1)

        objects = res.get("data", res) if isinstance(res, dict) else res
        if not isinstance(objects, list):
            objects = []

        if json_out:
            print_json(objects)
            return

        if not objects:
            console.print(f"[dim]No objects found in s3://{clean_name}[/dim]")
            return

        rows = []
        for obj in objects:
            key = obj.get("key") or obj.get("objectKey") or obj.get("name")
            size = obj.get("size") or obj.get("contentLength") or 0
            size_str = f"{size} B" if size < 1024 else f"{size / 1024:.1f} KB"
            etag = obj.get("eTag") or obj.get("etag") or "-"
            last_mod = obj.get("lastModified") or obj.get("createdAt") or "-"
            rows.append([key, size_str, etag, str(last_mod)])

        render_table(title=f"Objects in s3://{clean_name}", columns=["Object Key", "Size", "ETag", "Last Modified"], rows=rows)


@app.command("upload")
def upload_object(
    bucket_name: str = typer.Argument(..., help="Target bucket name"),
    file_path: str = typer.Argument(..., help="Path to local file to upload"),
    key: Optional[str] = typer.Option(None, "--key", "-k", help="Destination object key (defaults to filename)"),
    json_out: bool = typer.Option(False, "--json", help="Output in JSON format"),
):
    """Upload a local file to an S3 bucket."""
    clean_bucket = bucket_name.replace("s3://", "").strip("/")
    p = Path(file_path)
    if not p.exists() or not p.is_file():
        print_error(f"Local file '{file_path}' does not exist.")
        raise typer.Exit(code=1)

    dest_key = key or p.name
    client = MiniCloudClient()
    with open(p, "rb") as f:
        files = {"file": (dest_key, f, "application/octet-stream")}
        status, res = client.post(
            f"/api/v1/storage/buckets/{clean_bucket}/upload",
            files=files,
            params={"key": dest_key},
        )

    if status in (200, 201):
        data = res.get("data", res)
        if json_out:
            print_json(data)
        else:
            print_success(f"Uploaded [bold]{p.name}[/bold] -> [bold cyan]s3://{clean_bucket}/{dest_key}[/bold cyan]")
    else:
        err = res.get("message") or res.get("error") or "Upload failed"
        if json_out:
            print_json({"error": err, "code": status})
        else:
            print_error(f"Failed to upload object: {err}")
        raise typer.Exit(code=1)


@app.command("download")
def download_object(
    bucket_name: str = typer.Argument(..., help="Source bucket name"),
    key: str = typer.Argument(..., help="Object key in bucket"),
    dest_path: Optional[str] = typer.Option(None, "--out", "-o", help="Local destination file path"),
    json_out: bool = typer.Option(False, "--json", help="Output in JSON format"),
):
    """Download an object from an S3 bucket."""
    clean_bucket = bucket_name.replace("s3://", "").strip("/")
    client = MiniCloudClient()
    status, res = client.get(f"/api/v1/storage/buckets/{clean_bucket}/objects/{key}")
    if status == 200:
        out_file = dest_path or Path(key).name
        if isinstance(res, dict) and "raw" in res:
            content = res["raw"].encode("utf-8")
        else:
            content = json.dumps(res).encode("utf-8")
        with open(out_file, "wb") as f:
            f.write(content)
        if json_out:
            print_json({"bucket": clean_bucket, "key": key, "savedTo": str(out_file)})
        else:
            print_success(f"Downloaded [bold cyan]s3://{clean_bucket}/{key}[/bold cyan] to [bold]{out_file}[/bold]")
    else:
        err = res.get("message") or res.get("error") or "Download failed"
        if json_out:
            print_json({"error": err, "code": status})
        else:
            print_error(f"Failed to download object: {err}")
        raise typer.Exit(code=1)


@app.command("rb")
def remove_bucket(
    bucket_name: str = typer.Argument(..., help="Bucket name to delete"),
    force: bool = typer.Option(False, "--force", "-f", help="Force deletion"),
    json_out: bool = typer.Option(False, "--json", help="Output in JSON format"),
):
    """Delete an S3 bucket."""
    clean_bucket = bucket_name.replace("s3://", "").strip("/")
    client = MiniCloudClient()
    status, res = client.delete(f"/api/v1/storage/buckets/{clean_bucket}")
    if status in (200, 204):
        if json_out:
            print_json({"status": "deleted", "bucketName": clean_bucket})
        else:
            print_success(f"Bucket [bold cyan]s3://{clean_bucket}[/bold cyan] deleted.")
    else:
        err = res.get("message") or res.get("error") or "Failed to delete bucket"
        if json_out:
            print_json({"error": err, "code": status})
        else:
            print_error(f"Failed to delete bucket: {err}")
        raise typer.Exit(code=1)
