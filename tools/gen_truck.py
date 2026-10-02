#!/usr/bin/env python3
"""Generates the Taco Truck structure template.

Writes src/main/resources/data/papon/structures/taco_truck.nbt (a vanilla structure file,
DataVersion 3465 = 1.20.1). The serving window faces +Z (south). Cells TacoTruck relies on:
  WINDOW  (4, 0, 7)  the ordering spot, ground level in front of the counter
  VENDOR  (4, 1, 4)  the floor cell the Papon working the window stands on
  GRIDDLE (3, 1, 1)  the campfire he cooks on
The counter is a bottom slab at y1 (its top 1.5 blocks up, a customer's head height) with the
window open above it, so the 1.5x Papon behind it is in full view.

This is a placeholder build. To replace it, build the truck in game with the window facing
south, save it with a structure block as papon:taco_truck, and drop the .nbt over this one.
"""
import gzip
import os
import struct

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'src/main/resources/data/papon/structures/taco_truck.nbt')
SIZE = (11, 8, 8)

# ---------------------------------------------------------------- minimal NBT writer
END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTES, STRING, LIST, COMPOUND = range(11)


class Byte(int):
    pass


def _name(s):
    b = s.encode('utf-8')
    return struct.pack('>H', len(b)) + b


def _tag_type(v):
    if isinstance(v, Byte):
        return BYTE
    if isinstance(v, bool):
        return BYTE
    if isinstance(v, int):
        return INT
    if isinstance(v, str):
        return STRING
    if isinstance(v, list):
        return LIST
    if isinstance(v, dict):
        return COMPOUND
    raise TypeError(v)


def _payload(v):
    t = _tag_type(v)
    if t == BYTE:
        return struct.pack('>b', int(v))
    if t == INT:
        return struct.pack('>i', v)
    if t == STRING:
        return _name(v)
    if t == LIST:
        et = _tag_type(v[0]) if v else COMPOUND
        return struct.pack('>bi', et, len(v)) + b''.join(_payload(e) for e in v)
    if t == COMPOUND:
        out = b''
        for k, e in v.items():
            out += struct.pack('>b', _tag_type(e)) + _name(k) + _payload(e)
        return out + struct.pack('>b', END)


def write_nbt(path, root):
    data = struct.pack('>b', COMPOUND) + _name('') + _payload(root)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as raw:
        with gzip.GzipFile(fileobj=raw, mode='wb', mtime=0) as f:   # mtime=0: reproducible bytes
            f.write(data)


# ---------------------------------------------------------------- the build
blocks = {}


def put(x, y, z, state, nbt=None):
    assert 0 <= x < SIZE[0] and 0 <= y < SIZE[1] and 0 <= z < SIZE[2], (x, y, z)
    blocks[(x, y, z)] = (state, nbt)


def box(x0, y0, z0, x1, y1, z1, state):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                put(x, y, z, state)


def shell(x0, y0, z0, x1, y1, z1, state):
    """Hollow walls (no floor or ceiling)."""
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) or z in (z0, z1):
                    put(x, y, z, state)


def build():
    # ---- y0: skirt, wheels and the floor the vendor stands on
    box(0, 0, 0, 10, 0, 6, 'minecraft:red_concrete')
    box(1, 0, 1, 6, 0, 5, 'minecraft:polished_andesite')
    for x in (1, 2, 8, 9):
        for z in (0, 6):
            put(x, 0, z, 'minecraft:black_concrete')

    # ---- cargo body x0..7: red stripe, white walls, roof at y4
    shell(0, 1, 0, 7, 1, 6, 'minecraft:red_concrete')
    shell(0, 2, 0, 7, 3, 6, 'minecraft:white_concrete')
    box(0, 4, 0, 7, 4, 6, 'minecraft:white_concrete')
    # serving window on the south (+z) wall: a counter at the customers' head height,
    # open above it, so the Papon behind it is in full view
    for x in range(2, 7):
        put(x, 1, 6, 'minecraft:smooth_quartz_slab[type=bottom]')
        put(x, 2, 6, 'minecraft:air')
        put(x, 3, 6, 'minecraft:air')
    # striped awning over the ordering spot, and a billboard with the sign on the roof edge
    for x in range(1, 8):
        put(x, 4, 7, 'minecraft:red_wool' if x % 2 else 'minecraft:white_wool')
    for x in range(2, 7):
        put(x, 5, 6, 'minecraft:white_concrete')
    sign = {
        'id': 'minecraft:sign',
        'is_waxed': Byte(1),
        'front_text': {
            'has_glowing_text': Byte(1),
            'color': 'yellow',
            'messages': ['{"text":""}', '{"text":"TACOS","bold":true}', '{"text":"~ Papon\'s ~"}', '{"text":""}'],
        },
        'back_text': {
            'has_glowing_text': Byte(0),
            'color': 'black',
            'messages': ['{"text":""}', '{"text":""}', '{"text":""}', '{"text":""}'],
        },
    }
    put(4, 5, 7, 'minecraft:dark_oak_wall_sign[facing=south]', sign)
    # tail lights
    put(0, 2, 1, 'minecraft:red_stained_glass')
    put(0, 2, 5, 'minecraft:red_stained_glass')
    # kitchen along the back wall; the campfire is the griddle (TacoTruck.GRIDDLE), venting
    # through bars in the roof
    put(2, 1, 1, 'minecraft:smoker[facing=south,lit=true]')
    put(3, 1, 1, 'minecraft:campfire[lit=true,signal_fire=false,facing=south]')
    put(3, 4, 1, 'minecraft:iron_bars')
    put(4, 1, 1, 'minecraft:smoker[facing=south,lit=true]')
    put(5, 1, 1, 'minecraft:barrel[facing=up]')
    put(6, 1, 1, 'minecraft:water_cauldron[level=3]')
    put(1, 1, 4, 'minecraft:barrel[facing=east]')
    put(6, 1, 4, 'minecraft:composter')
    put(4, 3, 3, 'minecraft:lantern[hanging=true]')

    # ---- cab x8..10
    shell(8, 1, 0, 10, 1, 6, 'minecraft:red_concrete')
    for z in range(1, 6):
        put(10, 2, z, 'minecraft:light_blue_stained_glass')
    for x in (8, 9):
        put(x, 2, 0, 'minecraft:light_blue_stained_glass')
        put(x, 2, 6, 'minecraft:light_blue_stained_glass')
    put(10, 2, 0, 'minecraft:white_concrete')
    put(10, 2, 6, 'minecraft:white_concrete')
    box(8, 3, 0, 10, 3, 6, 'minecraft:white_concrete')
    put(10, 1, 1, 'minecraft:sea_lantern')
    put(10, 1, 5, 'minecraft:sea_lantern')
    for z in (2, 3, 4):
        put(10, 1, z, 'minecraft:gray_concrete')
    box(8, 0, 1, 9, 0, 5, 'minecraft:gray_concrete')
    put(9, 1, 3, 'minecraft:dark_oak_stairs[facing=east,half=bottom]')

    # ---- the giant taco on the roof
    for x in range(1, 7):
        for z in (2, 3, 4):
            put(x, 5, z, 'minecraft:yellow_terracotta')
        put(x, 6, 1, 'minecraft:yellow_terracotta')
        put(x, 6, 5, 'minecraft:yellow_terracotta')
        put(x, 6, 2, 'minecraft:lime_wool')
        put(x, 6, 3, 'minecraft:brown_wool')
        put(x, 6, 4, 'minecraft:red_wool')
        put(x, 7, 1, 'minecraft:yellow_terracotta')
        put(x, 7, 5, 'minecraft:yellow_terracotta')
        put(x, 7, 2, 'minecraft:lime_carpet')
        put(x, 7, 4, 'minecraft:lime_carpet')
        if x % 2:
            put(x, 7, 3, 'minecraft:red_carpet')


def parse_state(s):
    if '[' not in s:
        return {'Name': s}
    name, props = s[:-1].split('[', 1)
    return {'Name': name, 'Properties': dict(p.split('=') for p in props.split(','))}


def main():
    build()
    palette, index, out = [], {}, []
    for (x, y, z), (state, nbt) in sorted(blocks.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
        if state not in index:
            index[state] = len(palette)
            palette.append(parse_state(state))
        b = {'pos': [x, y, z], 'state': index[state]}
        if nbt:
            b['nbt'] = nbt
        out.append(b)
    root = {
        'DataVersion': 3465,
        'size': list(SIZE),
        'palette': palette,
        'blocks': out,
        'entities': [],
    }
    write_nbt(OUT, root)
    print(OUT, len(out), 'blocks')


if __name__ == '__main__':
    main()
