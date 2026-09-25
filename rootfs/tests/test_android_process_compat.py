"""Android /proc 子进程枚举的离线回归。"""

import importlib.util
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import psutil


OVERLAY = Path(__file__).resolve().parents[1] / "overlays" / "android_process_compat.py"
spec = importlib.util.spec_from_file_location("android_process_compat", OVERLAY)
compat = importlib.util.module_from_spec(spec)
spec.loader.exec_module(compat)


def add_process(root: Path, pid: int, ppid: int, ticks: int, state: str = "S") -> None:
    proc = root / str(pid)
    proc.mkdir()
    fields = [state, str(ppid), *(["0"] * 17), str(ticks)]
    (proc / "stat").write_text(f"{pid} (worker name) {' '.join(fields)}\n", encoding="ascii")


class ProcChildrenTest(unittest.TestCase):
    def test_same_owner_descendants_and_pid_reuse(self):
        work = Path(__file__).resolve().parents[2] / ".tmp"
        work.mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=work) as directory:
            root = Path(directory)
            add_process(root, 100, 1, 1000)
            add_process(root, 101, 100, 1001)
            add_process(root, 102, 101, 1002)
            add_process(root, 103, 100, 999)  # PID 重用，早于父进程
            add_process(root, 104, 100, 1003, state="Z")
            self.assertEqual(compat.proc_children(100, proc_root=root), [101])
            self.assertEqual(compat.proc_children(100, recursive=True, proc_root=root), [101, 102])

    def test_permission_failure_uses_proc_fallback(self):
        original = psutil.Process.children
        try:
            with patch.object(psutil.Process, "children", side_effect=psutil.AccessDenied(pid=os.getpid())):
                compat.install()
                with patch.object(compat, "proc_children", return_value=[os.getpid()]) as fallback:
                    result = psutil.Process().children(recursive=True)
                    self.assertEqual([child.pid for child in result], [os.getpid()])
                    fallback.assert_called_once_with(os.getpid(), True)
        finally:
            psutil.Process.children = original


if __name__ == "__main__":
    unittest.main()
