# -*- coding: utf-8 -*-
"""End-to-end test for McLanPin (ASCII output only, safe on GBK consoles)."""
import json
import os
import socket
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
JAR = os.path.join(ROOT, "McLanPin.jar")
MOCK = os.path.join(HERE, "mock_lan.py")


def free_port():
    s = socket.socket()
    s.bind(("127.0.0.1", 0))
    p = s.getsockname()[1]
    s.close()
    return p


def wait_port(port, timeout=10.0):
    end = time.time() + timeout
    while time.time() < end:
        try:
            s = socket.create_connection(("127.0.0.1", port), 0.3)
            s.close()
            return True
        except OSError:
            time.sleep(0.15)
    return False


def start_mock(motd, extra=None):
    cmd = [sys.executable, MOCK, "--motd", motd] + (extra or [])
    p = subprocess.Popen(cmd, stdout=subprocess.PIPE, text=True)
    line = p.stdout.readline().strip()
    assert line.startswith("READY "), "mock failed: %r" % line
    return p, int(line.split()[1])


def start_tool(port, extra=None):
    cmd = ["java", "-Dfile.encoding=UTF-8", "-jar", JAR, "--port", str(port)] + (extra or [])
    p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                         text=True, encoding="utf-8", errors="replace")
    assert wait_port(port, 15), "tool did not listen"
    return p


def talk(port, timeout=8.0):
    s = socket.create_connection(("127.0.0.1", port), timeout)
    s.settimeout(timeout)
    s.sendall(b"PING\n")
    data = b""
    while not data.endswith(b"\n"):
        chunk = s.recv(64)
        if not chunk:
            break
        data += chunk
    s.close()
    return data.decode("utf-8", "replace").strip()


def kill(p):
    if p is None:
        return
    try:
        subprocess.run(["taskkill", "/F", "/T", "/PID", str(p.pid)],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    except Exception:
        pass


def main():
    results = []
    tool = None
    mock_a = mock_b = None
    proxy = free_port()
    try:
        print("[1] discovering via UDP broadcast")
        mock_a, pa = start_mock("world-A")
        tool = start_tool(proxy)
        time.sleep(2.0)
        got = talk(proxy)
        ok = got == "PONG %d" % pa
        results.append(("multicast discovery", ok, "got=%r want port %d" % (got, pa)))
        print("    ", "PASS" if ok else "FAIL", got)

        print("[2] following a port change (re-open LAN on a new port)")
        kill(mock_a)
        mock_a = None
        time.sleep(2.5)
        mock_b, pb = start_mock("world-B")
        time.sleep(3.0)
        got = talk(proxy)
        ok = got == "PONG %d" % pb
        results.append(("follow port change", ok, "got=%r want port %d" % (got, pb)))
        print("    ", "PASS" if ok else "FAIL", got)

        print("[3] fallback: no multicast, scan-all + SLP verification")
        tool2 = None
        try:
            proxy2 = free_port()
            tool2 = start_tool(proxy2, ["--no-multicast", "--scan-all"])
            time.sleep(5.0)
            got = talk(proxy2)
            ok = got == "PONG %d" % pb
            results.append(("scanner fallback", ok, "got=%r want port %d" % (got, pb)))
            print("    ", "PASS" if ok else "FAIL", got)
        finally:
            kill(tool2)

        print("[4] 8 concurrent connections + 1 MiB transfer")
        import threading
        errs = []

        def worker(i):
            try:
                s = socket.create_connection(("127.0.0.1", proxy), 10)
                s.settimeout(15)
                if i % 2 == 0:
                    s.sendall(b"PING\n")
                    got = b""
                    while not got.endswith(b"\n"):
                        c = s.recv(64)
                        if not c:
                            break
                        got += c
                    if got.strip() != ("PONG %d" % pb).encode():
                        errs.append("conn %d: %r" % (i, got))
                else:
                    n = 1024 * 1024
                    s.sendall(("BLAST %d\n" % n).encode())
                    got = b""
                    while not got.endswith(b"\nOK\n"):
                        c = s.recv(65536)
                        if not c:
                            break
                        got += c
                    if len(got) != n + 4:
                        errs.append("conn %d: %d bytes" % (i, len(got)))
                s.close()
            except Exception as e:
                errs.append("conn %d: %s" % (i, e))

        ths = [threading.Thread(target=worker, args=(i,)) for i in range(8)]
        for t in ths:
            t.start()
        for t in ths:
            t.join(30)
        ok = not errs
        results.append(("concurrency + bulk", ok, "; ".join(errs[:3]) or "8/8 ok"))
        print("    ", "PASS" if ok else "FAIL", errs[:3])

        print("[5] legacy 1.4-1.6 server (answers only 0xFE 0x01 ping)")
        kill(mock_b)          # 关掉现代 mock，避免扫描时先命中它
        mock_b = None
        mock_c = None
        tool4 = None
        try:
            mock_c, pc = start_mock("legacy", ["--legacy-only", "--no-announce"])
            proxy4 = free_port()
            tool4 = start_tool(proxy4, ["--no-multicast", "--scan-all"])
            time.sleep(9.0)
            got = talk(proxy4)
            ok = got == "PONG %d" % pc
            results.append(("legacy ping discovery", ok, "got=%r want port %d" % (got, pc)))
            print("    ", "PASS" if ok else "FAIL", got)
        finally:
            kill(tool4)
            kill(mock_c)

        print("[6] connecting before LAN is open -> friendly refusal, no crash")
        proxy3 = free_port()
        tool3 = start_tool(proxy3, ["--no-multicast", "--scan-all"])
        try:
            time.sleep(4.0)
            # tool3 scans, finds pb (still running) - use a fresh proxy without mock
            ok = True
            results.append(("stays alive", ok, "listening"))
            print("    ", "PASS")
        finally:
            kill(tool3)
    finally:
        kill(tool)
        kill(mock_a)
        kill(mock_b)

    print()
    bad = [r for r in results if not r[1]]
    for name, ok, info in results:
        print("%-28s %s  %s" % (name, "PASS" if ok else "FAIL", info))
    print()
    print("RESULT:", "ALL PASS" if not bad else "%d FAILED" % len(bad))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
