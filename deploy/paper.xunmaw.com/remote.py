#!/usr/bin/env python3
"""SSH/SCP helper for paper.xunmaw.com deployment. Password via DEPLOY_SSH_PASS env var."""
from __future__ import annotations

import os
import sys
from pathlib import Path

import paramiko


def safe_print(text: str) -> None:
    data = text.encode("utf-8", errors="replace")
    try:
        sys.stdout.buffer.write(data)
        sys.stdout.buffer.flush()
    except Exception:
        print(data.decode("utf-8", errors="replace"), end="")


def connect() -> paramiko.SSHClient:
    password = os.environ.get("DEPLOY_SSH_PASS")
    if not password:
        print("Set DEPLOY_SSH_PASS environment variable", file=sys.stderr)
        sys.exit(1)
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(
        hostname=os.environ.get("DEPLOY_SSH_HOST", "159.75.166.190"),
        username=os.environ.get("DEPLOY_SSH_USER", "root"),
        password=password,
        timeout=30,
    )
    return client


def run(client: paramiko.SSHClient, cmd: str) -> int:
    print(f"$ {cmd}")
    _, stdout, stderr = client.exec_command(cmd, get_pty=True)
    for line in stdout:
        safe_print(line)
    err = stderr.read().decode()
    if err.strip():
        print(err, file=sys.stderr)
    return stdout.channel.recv_exit_status()


def upload_dir(local: Path, remote: str) -> None:
    password = os.environ["DEPLOY_SSH_PASS"]
    host = os.environ.get("DEPLOY_SSH_HOST", "159.75.166.190")
    user = os.environ.get("DEPLOY_SSH_USER", "root")
    transport = paramiko.Transport((host, 22))
    transport.connect(username=user, password=password)
    sftp = paramiko.SFTPClient.from_transport(transport)
    assert sftp is not None

    def ensure_remote_dir(path: str) -> None:
        parts = [p for p in path.split("/") if p]
        cur = ""
        for part in parts:
            cur += f"/{part}"
            try:
                sftp.stat(cur)
            except OSError:
                sftp.mkdir(cur)

    def put(local_path: Path, remote_path: str) -> None:
        if local_path.is_dir():
            ensure_remote_dir(remote_path)
            for child in local_path.iterdir():
                put(child, f"{remote_path.rstrip('/')}/{child.name}")
        else:
            ensure_remote_dir(str(Path(remote_path).parent).replace("\\", "/"))
            print(f"  upload {local_path.name} -> {remote_path}")
            sftp.put(str(local_path), remote_path)

    ensure_remote_dir(remote)
    for item in local.iterdir():
        put(item, f"{remote.rstrip('/')}/{item.name}")
    sftp.close()
    transport.close()


def upload_file(local: Path, remote: str) -> None:
    password = os.environ["DEPLOY_SSH_PASS"]
    host = os.environ.get("DEPLOY_SSH_HOST", "159.75.166.190")
    user = os.environ.get("DEPLOY_SSH_USER", "root")
    transport = paramiko.Transport((host, 22))
    transport.connect(username=user, password=password)
    sftp = paramiko.SFTPClient.from_transport(transport)
    assert sftp is not None
    parent = str(Path(remote).parent).replace("\\", "/")
    if parent and parent != ".":
        parts = [p for p in parent.split("/") if p]
        cur = ""
        for part in parts:
            cur += f"/{part}"
            try:
                sftp.stat(cur)
            except OSError:
                sftp.mkdir(cur)
    print(f"  upload {local.name} -> {remote}")
    sftp.put(str(local), remote)
    sftp.close()
    transport.close()


def main() -> None:
    if len(sys.argv) < 2:
        print("Usage: remote.py run <cmd> | upload <local_dir> <remote_dir> | put <local_file> <remote_file>")
        sys.exit(1)

    action = sys.argv[1]
    if action == "run":
        client = connect()
        code = run(client, " ".join(sys.argv[2:]))
        client.close()
        sys.exit(code)
    if action == "upload":
        upload_dir(Path(sys.argv[2]), sys.argv[3])
        return
    if action == "put":
        upload_file(Path(sys.argv[2]), sys.argv[3])
        return
    print(f"Unknown action: {action}", file=sys.stderr)
    sys.exit(1)


if __name__ == "__main__":
    main()
