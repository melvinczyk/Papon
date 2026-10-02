#!/usr/bin/env python3
"""Minimal Blockbench MCP client, for sessions where the server is not registered as a tool.

Blockbench (with the MCP plugin) must be open; it serves streamable-HTTP MCP at
http://localhost:3000/bb-mcp. Usage:

  python3 tools/bb_mcp.py list                       # tool names + descriptions
  python3 tools/bb_mcp.py schema <tool> [<tool>...]  # full input schemas
  python3 tools/bb_mcp.py <tool> '<json args>'       # call a tool

Images returned by tools (screenshots) are saved under $BB_SHOTS (default /tmp/bb_shots) and
the path is printed. Set SHOT=<name> to choose the file name. The MCP session id is cached in
$BB_SID (default /tmp/bb_mcp.sid) and re-negotiated if Blockbench restarted.
"""
import base64
import json
import os
import sys
import time
import urllib.request

URL = os.environ.get('BB_MCP_URL', 'http://localhost:3000/bb-mcp')
SID_FILE = os.environ.get('BB_SID', '/tmp/bb_mcp.sid')
SHOTS = os.environ.get('BB_SHOTS', '/tmp/bb_shots')


def _post(body, sid=None):
    headers = {'Content-Type': 'application/json', 'Accept': 'application/json, text/event-stream'}
    if sid:
        headers['mcp-session-id'] = sid
    req = urllib.request.Request(URL, json.dumps(body).encode(), headers)
    with urllib.request.urlopen(req, timeout=120) as r:
        text = r.read().decode()
        new_sid = r.headers.get('mcp-session-id')
    if text.startswith(('event', 'data')):
        text = [l[5:] for l in text.splitlines() if l.startswith('data:')][-1]
    return new_sid, (json.loads(text) if text.strip() else None)


def _session(fresh=False):
    if not fresh and os.path.exists(SID_FILE):
        return open(SID_FILE).read()
    sid, _ = _post({'jsonrpc': '2.0', 'id': 1, 'method': 'initialize', 'params': {
        'protocolVersion': '2025-03-26', 'capabilities': {}, 'clientInfo': {'name': 'papon-tools', 'version': '1'}}})
    _post({'jsonrpc': '2.0', 'method': 'notifications/initialized'}, sid)
    with open(SID_FILE, 'w') as f:
        f.write(sid)
    return sid


def call(method, params=None):
    body = {'jsonrpc': '2.0', 'id': 2, 'method': method, 'params': params or {}}
    try:
        return _post(body, _session())[1]
    except Exception:
        return _post(body, _session(fresh=True))[1]


def tool(name, args=None):
    r = call('tools/call', {'name': name, 'arguments': args or {}})
    if 'error' in r:
        return 'ERROR ' + json.dumps(r['error'])
    out = []
    for c in r['result'].get('content', []):
        if c.get('type') == 'image':
            os.makedirs(SHOTS, exist_ok=True)
            p = os.path.join(SHOTS, os.environ.get('SHOT', 'shot_%d' % int(time.time() * 1000)) + '.png')
            with open(p, 'wb') as f:
                f.write(base64.b64decode(c['data']))
            out.append('IMAGE ' + p)
        else:
            out.append(c.get('text', '<%s>' % c.get('type')))
    return ('[isError] ' if r['result'].get('isError') else '') + '\n'.join(out)


if __name__ == '__main__':
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    if sys.argv[1] == 'list':
        for t in call('tools/list')['result']['tools']:
            print(t['name'], '-', t.get('description', '')[:150].replace('\n', ' '))
    elif sys.argv[1] == 'schema':
        for t in call('tools/list')['result']['tools']:
            if t['name'] in sys.argv[2:]:
                print(json.dumps(t, indent=1))
    else:
        print(tool(sys.argv[1], json.loads(sys.argv[2]) if len(sys.argv) > 2 else {}))
