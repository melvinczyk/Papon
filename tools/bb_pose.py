#!/usr/bin/env python3
"""Quick pose check on the project already open in Blockbench (bb_preview.py loads it): reloads the
animation file from disk (Blockbench reads it itself; never send big files through MCP), poses one
animation at the given times and shoots each from the front and the side into one sheet.

  BB_KEEP=1 python tools/bb_preview.py anim /tmp/x.png papon_boss guard 0   # once, to open the model
  python tools/bb_pose.py <out.png> <anim file> <anim name> <t> [<t> ...]
"""
import json
import os
import sys

from PIL import Image, ImageDraw

import bb_mcp
from bb_preview import ASSETS, ev, ensure_view, path_of, shot

VIEWS = [('front', [0, 22, -62]), ('side', [62, 22, -8]), ('3/4', [44, 28, -44])]


def main(out, anim_file, anim, times):
    apath = path_of(ASSETS, 'animations', 'entity', anim_file + '.animation.json')
    ev('Animation.all.slice().forEach(a => a.remove(false)); '
       'Animator.loadFile({path: %s, content: require("fs").readFileSync(%s, "utf8")}); Animation.all.length'
       % (json.dumps(apath), json.dumps(apath)))
    vid = ensure_view('pose_view', 300, 340)
    full = 'animation.%s.%s' % (anim_file, anim)
    tiles = []
    for t in times:
        ev('var a = Animation.all.find(a => a.name == %s); a.select(); Timeline.setTime(%f); Animator.preview(); true'
           % (json.dumps(full), t))
        for label, pos in VIEWS:
            tiles.append(('%s %.2f %s' % (anim, t, label), shot(vid, pos, [0, 16, 0])))
    tw, th = tiles[0][1].size
    cols = len(VIEWS)
    im = Image.new('RGBA', (tw * cols, th * ((len(tiles) + cols - 1) // cols)), (24, 24, 28, 255))
    d = ImageDraw.Draw(im)
    for i, (label, t) in enumerate(tiles):
        x, y = (i % cols) * tw, (i // cols) * th
        im.alpha_composite(t, (x, y))
        d.text((x + 6, y + 4), label, fill=(255, 210, 90, 255))
    im.save(out)
    print(out)


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2], sys.argv[3], [float(t) for t in sys.argv[4:]])
