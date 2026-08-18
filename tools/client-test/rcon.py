"""Minimal Minecraft RCON client (offline protocol implementation)."""
import socket
import struct
import sys


def pack(pid, ptype, body):
    data = struct.pack('<ii', pid, ptype) + body.encode('utf-8') + b'\x00\x00'
    return struct.pack('<i', len(data)) + data


def unpack(sock):
    raw = b''
    while len(raw) < 4:
        raw += sock.recv(4 - len(raw))
    (length,) = struct.unpack('<i', raw)
    data = b''
    while len(data) < length:
        data += sock.recv(length - len(data))
    pid, ptype = struct.unpack('<ii', data[:8])
    return pid, ptype, data[8:-2].decode('utf-8', 'replace')


def main(host, port, password, commands):
    sock = socket.create_connection((host, port), timeout=10)
    sock.sendall(pack(1, 3, password))
    pid, ptype, body = unpack(sock)
    if ptype == 2 and pid == 1:
        for cmd in commands:
            sock.sendall(pack(2, 2, cmd))
            _, _, out = unpack(sock)
            print(f'> {cmd}\n{out}')
    else:
        print('AUTH FAILED', pid, ptype, body)
    sock.close()


if __name__ == '__main__':
    cmds = sys.argv[3:]
    main(sys.argv[1], int(sys.argv[2]), 'fdtest2026', cmds)
