"""Android proot 下为 psutil 的子进程枚举提供 /proc 身份后备。"""

import os
from pathlib import Path

import psutil


def _read_stat(path: Path) -> tuple[str, int, int]:
    raw = path.read_text(encoding="ascii")
    closing = raw.rfind(")")
    if closing < 0:
        raise ValueError("invalid /proc stat")
    fields = raw[closing + 1 :].split()
    if len(fields) <= 19:
        raise ValueError("short /proc stat")
    return fields[0], int(fields[1]), int(fields[19])


def proc_children(pid: int, recursive: bool = False, proc_root: Path = Path("/proc")) -> list[int]:
    """只枚举同 UID、创建时间晚于父进程的后代，避免 PID 复用误认。"""
    root = proc_root / str(pid)
    owner = root.stat().st_uid
    _, _, root_ticks = _read_stat(root / "stat")
    children_by_parent: dict[int, list[tuple[int, int]]] = {}
    for entry in proc_root.iterdir():
        if not entry.name.isdigit() or entry.name == str(pid):
            continue
        try:
            if entry.stat().st_uid != owner:
                continue
            state, ppid, ticks = _read_stat(entry / "stat")
        except (OSError, ValueError):
            # /proc 会随进程退出而变化；单个条目消失不影响其他后代。
            continue
        if state == "Z":
            continue
        children_by_parent.setdefault(ppid, []).append((int(entry.name), ticks))

    result: list[int] = []
    seen = {pid}
    pending = [(pid, root_ticks)]
    while pending:
        parent_pid, parent_ticks = pending.pop()
        for child_pid, child_ticks in children_by_parent.get(parent_pid, []):
            if child_pid in seen or child_ticks < parent_ticks:
                continue
            seen.add(child_pid)
            result.append(child_pid)
            if recursive:
                pending.append((child_pid, child_ticks))
    return result


def install() -> None:
    """只在 Android 的 psutil 权限失败时走兼容枚举。"""
    original = psutil.Process.children

    def children(self, recursive=False):
        try:
            return original(self, recursive=recursive)
        except (psutil.AccessDenied, PermissionError):
            try:
                return [psutil.Process(pid) for pid in proc_children(self.pid, recursive)]
            except (OSError, ValueError) as exc:
                raise psutil.AccessDenied(self.pid, msg=f"Android /proc children unavailable: {exc}") from exc

    psutil.Process.children = children
