"""Minimal Docker Engine API client over unix socket."""
from __future__ import annotations

import http.client
import json
import socket
from typing import Any


class DockerUnixConnection(http.client.HTTPConnection):
    def __init__(self, sock_path: str = "/var/run/docker.sock"):
        super().__init__("localhost")
        self.sock_path = sock_path

    def connect(self) -> None:
        sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        sock.connect(self.sock_path)
        self.sock = sock


def docker_request(
    method: str,
    path: str,
    body: dict | None = None,
    *,
    timeout: int = 120,
) -> tuple[int, Any]:
    conn = DockerUnixConnection()
    conn.timeout = timeout
    payload = json.dumps(body).encode("utf-8") if body is not None else None
    headers = {"Content-Type": "application/json"} if payload else {}
    conn.request(method, path, body=payload, headers=headers)
    resp = conn.getresponse()
    raw = resp.read()
    conn.close()
    data: Any
    if raw:
        try:
            data = json.loads(raw.decode("utf-8"))
        except Exception:
            data = raw.decode("utf-8", errors="ignore")
    else:
        data = None
    return resp.status, data


def restart_container(name: str) -> str:
    status, data = docker_request("POST", f"/containers/{name}/restart?t=20")
    if status not in (204, 200):
        raise RuntimeError(f"restart failed ({status}): {data}")
    return "restarted"


def container_logs(name: str, tail: int = 80) -> str:
    conn = DockerUnixConnection()
    conn.timeout = 30
    conn.request(
        "GET",
        f"/containers/{name}/logs?stdout=1&stderr=1&tail={tail}&timestamps=0",
    )
    resp = conn.getresponse()
    raw = resp.read()
    status = resp.status
    conn.close()
    if status != 200:
        raise RuntimeError(f"logs failed ({status}): {raw[:500]!r}")
    return _demux_logs(raw)


def _demux_logs(raw: bytes) -> str:
    # If multiplexed, frames start with stream type + size
    out = bytearray()
    i = 0
    while i + 8 <= len(raw):
        # heuristic: if looks like header
        size = int.from_bytes(raw[i + 4 : i + 8], "big")
        if size > 0 and i + 8 + size <= len(raw) and raw[i] in (0, 1, 2):
            out.extend(raw[i + 8 : i + 8 + size])
            i += 8 + size
        else:
            out.extend(raw[i:])
            break
    else:
        if i < len(raw):
            out.extend(raw[i:])
    if not out:
        return raw.decode("utf-8", errors="ignore")
    return out.decode("utf-8", errors="ignore")
