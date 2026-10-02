#!/usr/bin/env python3
"""Renders several animations of one model at chosen times into one contact sheet, loading the
model into Blockbench once (MCP plugin must be live).

  python tools/bb_frames.py <out.png> <model> <anim>@<t>,<t>,... [<anim>@<t>,... ...]

e.g.  python tools/bb_frames.py /tmp/edp.png edp445 react_edp445@0.4,1.2,1.9,3.0 order@0.5
Rows are animations, columns are times. Camera: front three-quarter.
"""
import json
import sys

from PIL import Image, ImageDraw

import bb_preview as bp


def main():
    out, name, specs = sys.argv[1], sys.argv[2], sys.argv[3:]
    bp.open_model(name)
    vid = bp.ensure_view(w=300, h=380)
    top = bp.height_of(name)
    mid = top / 2
    dist = max(1.0, top / 34)
    prefix = 'animation.%s.' % bp.ANIM_FILES[name]
    rows = []
    for spec in specs:
        anim, times = spec.split('@')
        tiles = []
        for t in (float(x) for x in times.split(',')):
            bp.ev('var a = Animation.all.find(a => a.name == %s); if (!a) throw "no anim %s"; a.select(); '
                  'Timeline.setTime(%f); Animator.preview(); true' % (json.dumps(prefix + anim), anim, t))
            img = bp.shot(vid, [34 * dist, mid + 10 * dist, -58 * dist], [0, mid, 0])
            tiles.append(('%s %.2f' % (anim, t), img))
        rows.append(tiles)
    tw, th = rows[0][0][1].size
    cols = max(len(r) for r in rows)
    sheet = Image.new('RGBA', (tw * cols, th * len(rows)), (24, 24, 28, 255))
    d = ImageDraw.Draw(sheet)
    for j, r in enumerate(rows):
        for i, (label, img) in enumerate(r):
            sheet.alpha_composite(img, (i * tw, j * th))
            d.text((i * tw + 4, j * th + 3), label, fill=(255, 210, 90, 255))
    sheet.save(out)
    print(out)


if __name__ == '__main__':
    main()
