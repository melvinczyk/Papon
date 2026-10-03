#!/usr/bin/env python3
"""Loads generated models into Blockbench (MCP plugin must be live), renders them, and saves
editable .bbmodel projects.

  python tools/bb_preview.py sheet <out.png> <model> [<model> ...]
        renders each model from four angles into one contact sheet
  python tools/bb_preview.py anim <out.png> <model> <anim> <t> [<t> ...]
        renders one animation at several times (front three-quarter view)
  python tools/bb_preview.py save <model> [<model> ...]
        writes tools/blockbench/<model>.bbmodel with geometry, texture and its animations

<model> is a geo name under assets/papon/geo/entity (e.g. edp445). Animations come from
ANIM_FILES below. Every call opens a fresh Blockbench project per model and closes it after.
"""
import base64
import io
import json
import os
import sys

from PIL import Image, ImageDraw

import bb_mcp

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'papon')
BB_DIR = os.path.join(ROOT, 'tools', 'blockbench')

# geo name -> (texture name, animation file) ; texture defaults to the geo name
ANIM_FILES = {
    'wandering_papon': 'wandering_papon',
    'taco_vendor': 'papon_npc',
    'customer_papon': 'papon_npc',
    'don_pollo': 'customer',
    'reviewbrah': 'customer',
    'gerbin': 'gerbin',
    'papon_boss': 'papon_boss',
    'caravan': 'caravan',
}

VIEWS = [
    ('front', [0, 20, -70]),
    ('three-quarter', [45, 26, -55]),
    ('side', [70, 20, 0]),
    ('back', [-30, 24, 62]),
]


def path_of(*parts):
    return os.path.join(*parts).replace('\\', '/')


def ev(js):
    out = bb_mcp.tool('risky_eval', {'code': js})
    if out.startswith('[isError]') or out.startswith('ERROR'):
        raise RuntimeError(out[:800])
    return out


def open_model(name):
    ev('ModelProject.all.slice().forEach(p => { try { p.close(true); } catch (e) {} }); true')
    print(bb_mcp.tool('create_project', {'name': name, 'format': 'geckolib_model'}).splitlines()[0])
    geo = path_of(ASSETS, 'geo', 'entity', name + '.geo.json')
    bb_mcp.tool('from_geo_json', {'geojson': geo})
    png = path_of(ASSETS, 'textures', 'entity', name + '.png')
    w, h = Image.open(png).size
    bb_mcp.tool('create_texture', {'name': name + '.png', 'data': png, 'width': w, 'height': h})
    ev('Texture.all.forEach(t => t.use_as_default = true); Canvas.updateAll(); true')
    anim = ANIM_FILES.get(name)
    if anim:
        apath = path_of(ASSETS, 'animations', 'entity', anim + '.animation.json')
        if os.path.exists(apath):
            content = open(apath).read()
            ev('Animator.loadFile({path: %s, content: %s}); Animation.all.length' % (json.dumps(apath), json.dumps(content)))


def shot(view, pos, target):
    os.environ['SHOT'] = 'tmp_view'
    out = bb_mcp.tool('set_camera_angle', {'view': view, 'position': pos, 'target': target,
                                           'projection': 'perspective'})
    for line in out.splitlines():
        if line.startswith('IMAGE '):
            return Image.open(line[6:]).convert('RGBA')
    raise RuntimeError('no image: ' + out[:300])


def ensure_view(vid='papon_view', w=420, h=520):
    try:
        bb_mcp.tool('delete_offscreen_view', {'id': vid})
    except Exception:
        pass
    bb_mcp.tool('create_offscreen_view', {'id': vid, 'width': w, 'height': h, 'antialias': False,
                                          'copy_view': 'none'})
    return vid


def height_of(name):
    geo = json.load(open(path_of(ASSETS, 'geo', 'entity', name + '.geo.json')))
    top = 0
    for b in geo['minecraft:geometry'][0]['bones']:
        for c in b.get('cubes', []):
            top = max(top, c['origin'][1] + c['size'][1])
    return top


def reach_of(name):
    """How far the model reaches out from its centre line (long things like the van need the camera back)."""
    geo = json.load(open(path_of(ASSETS, 'geo', 'entity', name + '.geo.json')))
    r = 0
    for b in geo['minecraft:geometry'][0]['bones']:
        for c in b.get('cubes', []):
            for k in (0, 2):
                r = max(r, abs(c['origin'][k]), abs(c['origin'][k] + c['size'][k]))
    return r


def sheet(out, names):
    tiles = []
    for name in names:
        open_model(name)
        vid = ensure_view()
        mid = height_of(name) / 2
        dist = max(1.0, height_of(name) / 34, reach_of(name) / 20)
        for label, pos in VIEWS:
            p = [pos[0] * dist, mid + (pos[1] - 18) * dist, pos[2] * dist]
            tiles.append((name + ' ' + label, shot(vid, p, [0, mid, 0])))
    cols = len(VIEWS)
    tw, th = tiles[0][1].size
    im = Image.new('RGBA', (tw * cols, th * ((len(tiles) + cols - 1) // cols)), (24, 24, 28, 255))
    d = ImageDraw.Draw(im)
    for i, (label, t) in enumerate(tiles):
        x, y = (i % cols) * tw, (i // cols) * th
        im.alpha_composite(t, (x, y))
        d.text((x + 6, y + 4), label, fill=(255, 210, 90, 255))
    im.save(out)
    print(out)


def anim(out, name, anim_name, times, prefix=None):
    open_model(name)
    vid = ensure_view(w=360, h=460)
    mid = height_of(name) / 2
    full = (prefix or 'animation.%s.' % ANIM_FILES[name]) + anim_name
    tiles = []
    for t in times:
        ev('var a = Animation.all.find(a => a.name == %s); a.select(); Timeline.setTime(%f); '
           'Animator.preview(); true' % (json.dumps(full), t))
        tiles.append(('%s %.2fs' % (anim_name, t), shot(vid, [40, mid + 12, -60], [0, mid, 0])))
    tw, th = tiles[0][1].size
    cols = min(len(tiles), 6)
    im = Image.new('RGBA', (tw * cols, th * ((len(tiles) + cols - 1) // cols)), (24, 24, 28, 255))
    d = ImageDraw.Draw(im)
    for i, (label, t) in enumerate(tiles):
        x, y = (i % cols) * tw, (i // cols) * th
        im.alpha_composite(t, (x, y))
        d.text((x + 6, y + 4), label, fill=(255, 210, 90, 255))
    im.save(out)
    print(out)


def save(names):
    os.makedirs(BB_DIR, exist_ok=True)
    for name in names:
        open_model(name)
        raw = ev('JSON.stringify(Codecs.project.compile({raw: true}))')
        data = json.loads(raw)
        if isinstance(data, str):
            data = json.loads(data)
        path = os.path.join(BB_DIR, name + '.bbmodel')
        with open(path, 'w', newline='\n') as f:
            json.dump(data, f, separators=(',', ':'))
        print(path)


if __name__ == '__main__':
    cmd = sys.argv[1]
    if cmd == 'sheet':
        sheet(sys.argv[2], sys.argv[3:])
    elif cmd == 'anim':
        anim(sys.argv[2], sys.argv[3], sys.argv[4], [float(t) for t in sys.argv[5:]])
    elif cmd == 'save':
        save(sys.argv[2:])
    else:
        sys.exit(__doc__)
