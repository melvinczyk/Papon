#!/usr/bin/env python3
"""RCON client for the dev server: python3 tools/rcon.py "papon locate" "say hi" (password papon, port 25575)."""
import socket,struct,sys
def pkt(i,t,b): b=b.encode()+b'\x00\x00'; return struct.pack('<iii',len(b)+8,i,t)+b
def recv(s):
    n=struct.unpack('<i',s.recv(4))[0]; d=b''
    while len(d)<n: d+=s.recv(n-len(d))
    return d[8:-2].decode(errors='replace')
s=socket.create_connection(('127.0.0.1',25575)); s.sendall(pkt(1,3,'papon')); recv(s)
for c in sys.argv[1:]:
    s.sendall(pkt(2,2,c)); print('>',c,'\n ',recv(s))
