# -*- coding: utf-8 -*-
"""Fake Minecraft integrated server used to test McLanPin.

It opens a TCP port, announces it on 224.0.2.60:4445 exactly like vanilla
"Open to LAN" does, answers Minecraft server-list pings, and echoes PING.
"""
import argparse
import json
import socket
import sys
import threading
import time


def read_varint(sock):
    num = 0
    shift = 0
    while True:
        b = sock.recv(1)
        if not b:
            raise EOFError
        b = b[0]
        num |= (b & 0x7f) << shift
        if not (b & 0x80):
            return num
        shift += 7


def read_exact(sock, n):
    buf = b""
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            raise EOFError
        buf += chunk
    return buf


LEGACY_ONLY = False


def handle(conn, port):
    conn.settimeout(5.0)
    try:
        first = conn.recv(1, socket.MSG_PEEK)
        if not first:
            return
        if first == b"\xfe":
            # 1.4~1.6 老式 ping: FE 01 -> FF + 长度 + UTF-16BE "§1\0协议\0版本\0MOTD"
            conn.recv(2)
            payload = "\u00a71\u000078\u00001.5.2\u0000legacy-lan".encode("utf-16-be")
            n = len(payload) // 2
            conn.sendall(b"\xff" + n.to_bytes(2, "big") + payload)
            return
        if first == b"P" or first == b"B":
            data = b""
            while not data.endswith(b"\n"):
                c = conn.recv(1)
                if not c:
                    return
                data += c
            line = data.decode("ascii", "replace").strip()
            if line.startswith("BLAST "):
                n = int(line.split()[1])
                conn.sendall(b"Z" * n + b"\nOK\n")
            else:
                conn.sendall(("PONG %d\n" % port).encode())
            return
        if LEGACY_ONLY:
            return  # 只回老式 ping，现代握手一律不答，逼工具走 1.4~1.6 分支
        ln = read_varint(conn)
        read_exact(conn, ln)
        ln = read_varint(conn)
        read_exact(conn, ln)
        payload = json.dumps({
            "version": {"name": "1.12.2", "protocol": 340},
            "players": {"max": 8, "online": 0},
            "description": {"text": "mock-lan"},
        }).encode()
        body = b"\x00" + bytes([len(payload)]) + payload
        conn.sendall(bytes([len(body)]) + body)
    except Exception:
        pass
    finally:
        try:
            conn.close()
        except Exception:
            pass


def announce(port, motd, stop, interval):
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, 1)
        s.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_LOOP, 1)
        msg = ("[MOTD]%s[/MOTD][AD]%d[/AD]" % (motd, port)).encode("utf-8")
        while not stop.is_set():
            try:
                s.sendto(msg, ("224.0.2.60", 4445))
            except OSError:
                pass
            stop.wait(interval)
    finally:
        s.close()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=0)
    ap.add_argument("--motd", default="mock")
    ap.add_argument("--no-announce", action="store_true")
    ap.add_argument("--interval", type=float, default=0.4)
    ap.add_argument("--legacy-only", action="store_true")
    a = ap.parse_args()

    global LEGACY_ONLY
    LEGACY_ONLY = a.legacy_only

    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("127.0.0.1", a.port))
    srv.listen(16)
    port = srv.getsockname()[1]

    stop = threading.Event()
    if not a.no_announce:
        threading.Thread(target=announce, args=(port, a.motd, stop, a.interval),
                         daemon=True).start()

    sys.stdout.write("READY %d\n" % port)
    sys.stdout.flush()

    while True:
        try:
            conn, _ = srv.accept()
        except OSError:
            break
        threading.Thread(target=handle, args=(conn, port), daemon=True).start()


if __name__ == "__main__":
    main()
