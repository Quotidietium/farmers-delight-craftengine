"""One-command live client regression for the FarmersDelight Papo plugin.

Starts the smoke server (if not running), waits for boot, then streams the log
while you perform the CLIENT_TEST.md steps in a real client. Every expected
marker that appears in the log is ticked off; at the end (Ctrl+C or `finish`)
a PASS/FAIL/MISSING report is written to smoke/regression-report.txt.

Usage:  python tools/client-test/regression.py [--host 127.0.0.1 --port 25575]
"""
import re
import socket
import struct
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SMOKE = ROOT / "smoke"
LOG = SMOKE / "server.log"
RCON_HOST, RCON_PORT, RCON_PASS = "127.0.0.1", 25575, "fdtest2026"

CHECKS = [
    # (marker regex in log, description, step label)
    (r"\[PATH-A\] CE furniture event: farmersdelight:(\w+)", "PATH-A furniture interaction", "B. interactions"),
    (r"\[PATH-B\] Bukkit entity interact fallback", "PATH-B fallback interaction", "B. interactions"),
    (r"\[GUI\] opened for .*", "pot GUI opened", "B. interactions"),
    (r"\[GUI\] pot layout swapped", "pot layout swap", "B. interactions"),
    (r"\[ENCHANT-TABLE OFFER\] Backstabbing", "ENCHANT-TABLE Backstabbing offer (deviation 7 closed)", "C. enchanting table"),
]
STEPS = """
================ 真人客户端回归步骤（对照 note/port/CLIENT_TEST.md） ================
A. 准备：进入客户端（离线模式 127.0.0.1），接受资源包
   控制台已自动执行：给予刀具/背包物品
B. 交互：
   1. 右键砧板放物+刀切       2. 右键烹饪锅开 GUI（观察进度条/配方书）
   3. 锅投料（小麦x3 等）加热源烹饪  4. 右键盛宴取一份（手持碗）
   5. 右键绳子放置/卷绳        6. 南瓜种子种在沃土耕地（观察结瓜）
C. 附魔台（重点，闭合偏差⑦）：
   1. 放置附魔台（周围书架）+手持 farmersdelight:iron_knife
   2. 观察候选是否出现「背刺 Backstabbing」
   3. 附魔后背对生物攻击：暴击音+伤害提升
====================================================================================
"""


def pack(pid, ptype, body):
    data = struct.pack("<ii", pid, ptype) + body.encode() + b"\x00\x00"
    return struct.pack("<i", len(data)) + data


def unpack(sock):
    raw = b""
    while len(raw) < 4:
        raw += sock.recv(4 - len(raw))
    (length,) = struct.unpack("<i", raw)
    data = b""
    while len(data) < length:
        data += sock.recv(length - len(data))
    return data[8:-2].decode("utf-8", "replace")


def rcon(commands):
    sock = socket.create_connection((RCON_HOST, RCON_PORT), timeout=10)
    sock.sendall(pack(1, 3, RCON_PASS))
    unpack(sock)
    for cmd in commands:
        sock.sendall(pack(2, 2, cmd))
        unpack(sock)
    sock.close()


def main():
    print(STEPS)
    marker_log = SMOKE / "regression-marks.log"
    marker_log.write_text("", encoding="utf-8")

    # optional: boot the server if it is not running
    try:
        rcon(["list"])
        print("server already running")
    except OSError:
        print("starting smoke server ...")
        subprocess.Popen(
            ["F:/Java/21/bin/java.exe", "-Xmx2G", "-jar", "server.jar", "nogui"],
            cwd=SMOKE, stdout=open(SMOKE / "server.log", "w"), stderr=subprocess.STDOUT)
        time.sleep(90)

    # hand the tester their kit
    try:
        rcon([
            "ce item give @s farmersdelight:iron_knife 4",
            "ce item give @s farmersdelight:cooking_pot 1",
            "ce item give @s farmersdelight:cutting_board 1",
            "ce item give @s farmersdelight:roast_chicken_block 1",
            "ce item give @s farmersdelight:rope 16",
            "give @s pumpkin_seeds 16",
            "give @s bowl 16",
            "give @s wheat 32",
        ])
        print("kit deployed via RCON")
    except OSError as e:
        print("RCON unavailable (", e, ") - continue watching log only")

    print("watching log ... perform the steps above, then Ctrl+C here to finish\n")
    seen = set()
    try:
        pos = 0
        while True:
            try:
                text = LOG.read_bytes().decode("gbk", errors="replace")
            except OSError:
                text = ""
            if len(text) >= pos:
                for line in text[pos:].splitlines():
                    for pattern, desc, _step in CHECKS:
                        if re.search(pattern, line) and (pattern, desc) not in seen:
                            seen.add((pattern, desc))
                            print(f"  [SEEN] {desc}: {line.strip()[:120]}")
                            with open(marker_log, "a", encoding="utf-8") as fh:
                                fh.write(f"SEEN {desc} :: {line.strip()}\n")
                pos = len(text)
            else:
                pos = 0  # log rotated
            time.sleep(1)
    except KeyboardInterrupt:
        pass

    report = ["# Client regression report", ""]
    for pattern, desc, step in CHECKS:
        status = "SEEN" if any(p == pattern for p, _ in seen) else "not observed"
        report.append(f"[{status:>12}] {step}: {desc}")
    report.append("")
    report.append("Manual items (tick by hand):")
    report.append("  [ ] enchanting table offers Backstabbing for farmersdelight:iron_knife (deviation 7 client check)")
    report.append("  [ ] feast serving took 1 from the platter (visual servings decrement)")
    report.append("  [ ] rope placement/reel felt correct")
    out = SMOKE / "regression-report.txt"
    out.write_text("\n".join(report) + "\n", encoding="utf-8")
    print(f"\nreport -> {out}")


if __name__ == "__main__":
    main()
