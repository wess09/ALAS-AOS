#!/usr/bin/env python3
"""让 Android App 成为 WebUI 进程树的生命周期属主。"""

import os
import signal
import stat
import subprocess
import sys
import threading


def main():
    root = os.path.dirname(os.path.abspath(__file__))
    os.chdir(root)
    child = subprocess.Popen(
        [os.path.join(root, '.venv/bin/python'), 'gui.py', '--host', '127.0.0.1', '--port', '25548'],
        cwd=root, stdin=subprocess.DEVNULL, start_new_session=True,
    )
    closing = threading.Event()

    def stop(*_):
        if closing.is_set():
            return
        closing.set()
        try:
            os.killpg(child.pid, signal.SIGTERM)
        except ProcessLookupError:
            return
        try:
            child.wait(timeout=5)
        except subprocess.TimeoutExpired:
            os.killpg(child.pid, signal.SIGKILL)
            child.wait()

    def watch_stdin():
        try:
            sys.stdin.buffer.read()
        finally:
            stop()

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    try:
        if stat.S_ISFIFO(os.fstat(sys.stdin.fileno()).st_mode):
            threading.Thread(target=watch_stdin, name='android-parent-watch', daemon=True).start()
    except (OSError, ValueError):
        pass
    code = child.wait()
    stop()
    return code


if __name__ == '__main__':
    sys.exit(main())
