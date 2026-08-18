"""Watches for ZTF3 joining; auto re-gives the test kit once; then streams diagnostics."""
import re
import socket
import struct
import sys
import time

HOST, PORT, PASSWORD = '127.0.0.1', 25575, 'fdtest2026'
GIVEN = False


def rcon(cmd):
    def pack(pid, ptype, body):
        data = struct.pack('<ii', pid, ptype) + body.encode('utf-8') + b'\x00\x00'
        return struct.pack('<i', len(data)) + data

    sock = socket.create_connection((HOST, PORT), timeout=10)
    sock.sendall(pack(1, 3, PASSWORD))
    raw = b''
    while len(raw) < 4:
        raw += sock.recv(4 - len(raw))
    (length,) = struct.unpack('<i', raw)
    data = b''
    while len(data) < length:
        data += sock.recv(length - len(data))
    out = ''
    sock.sendall(pack(2, 2, cmd))
    raw = b''
    while len(raw) < 4:
        raw += sock.recv(4 - len(raw))
    (length,) = struct.unpack('<i', raw)
    data = b''
    while len(data) < length:
        data += sock.recv(length - len(data))
    out = data[8:-2].decode('utf-8', 'replace')
    sock.close()
    return out


KIT_CE = [
    ('farmersdelight:flint_knife', 1), ('farmersdelight:iron_knife', 1),
    ('farmersdelight:cutting_board', 4), ('farmersdelight:cooking_pot', 2),
    ('farmersdelight:stove', 4), ('farmersdelight:skillet', 1),
    ('farmersdelight:basket', 2), ('farmersdelight:oak_cabinet', 2),
    ('farmersdelight:canvas_sign', 4),
    ('farmersdelight:cabbage_seeds', 8), ('farmersdelight:tomato_seeds', 8),
    ('farmersdelight:onion', 8), ('farmersdelight:rice', 8),
    ('farmersdelight:rich_soil', 16), ('farmersdelight:organic_compost', 8),
    ('farmersdelight:rope', 16),
    ('farmersdelight:beef_stew', 4), ('farmersdelight:cooked_rice', 4), ('farmersdelight:ham', 8),
]
KIT_VANILLA = [
    ('minecraft:bowl', 32), ('minecraft:beef', 32), ('minecraft:cooked_beef', 16),
    ('minecraft:bone_meal', 16), ('minecraft:flint_and_steel', 1),
    ('minecraft:carrot', 16), ('minecraft:potato', 16),
]

print('watcher running: waiting for ZTF3...', flush=True)
while True:
    try:
        players = rcon('list')
        if 'ZTF3' in players:
            if not GIVEN:
                print('ZTF3 online - re-giving test kit', flush=True)
                for item, n in KIT_CE:
                    rcon(f'ce item give ZTF3 {item} {n}')
                for item, n in KIT_VANILLA:
                    rcon(f'give ZTF3 {item} {n}')
                rcon('op ZTF3')
                GIVEN = True
                print('test kit re-given', flush=True)
        time.sleep(10)
    except KeyboardInterrupt:
        break
    except Exception as e:
        print(f'watcher error: {e}', flush=True)
        time.sleep(10)
