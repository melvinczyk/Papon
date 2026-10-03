"""Renders a mesh celebrity (assets/papon/meshes/<name>.json + its texture) in Blender, in rest pose.

  Blender -b --python tools/blender_preview.py -- <name> [out.png] [--face]

Front, three-quarter and side views side by side (or close-ups of the face with --face), lit like a
bright overcast day so the texture reads as it will in game. Used to check the face and body after
regenerating (tools/gen_*_mesh.py) without starting Minecraft.
"""
import json
import math
import os
import sys

import bpy
import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')


def load(name):
    m = json.load(open(os.path.join(ASSETS, 'meshes/%s.json' % name)))
    P = np.array(m['positions'], float).reshape(-1, 3) / 16.0      # rig pixels -> blocks (metres)
    # rig (y up, front -Z) -> Blender (z up, front +Y), mirrored in X as GeckoLib bakes it
    P = P[:, [0, 2, 1]] * [-1, -1, 1]
    uv = np.array(m['uvs'], float).reshape(-1, 2)
    idx = np.array(m['indices']).reshape(-1, m.get('corners', 3))[:, ::-1]     # the mirror flips winding
    polys = [list(dict.fromkeys(f)) for f in idx.tolist()]       # quads with a doubled corner are triangles
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(P.tolist(), [], polys)
    layer = mesh.uv_layers.new()
    layer.data.foreach_set('uv', uv[[v for f in polys for v in f]].flatten())
    for p in mesh.polygons:
        p.use_smooth = True
    ob = bpy.data.objects.new(name, mesh)
    bpy.context.scene.collection.objects.link(ob)
    tex = m['texture'].split(':')[1]
    img = bpy.data.images.load(os.path.join(ASSETS, tex))
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nt = mat.node_tree
    bsdf = nt.nodes['Principled BSDF']
    t = nt.nodes.new('ShaderNodeTexImage')
    t.image = img
    t.interpolation = 'Closest'
    nt.links.new(t.outputs['Color'], bsdf.inputs['Base Color'])
    bsdf.inputs['Roughness'].default_value = 0.8
    ob.data.materials.append(mat)
    return ob, P


def main():
    argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else ['badlands_chugs']
    face = '--face' in argv
    argv = [a for a in argv if not a.startswith('--')]
    name = argv[0]
    out = argv[1] if len(argv) > 1 else os.path.join(ROOT, 'tools/blockbench/%s_blender%s.png' % (name, '_face' if face else ''))
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.engine = 'BLENDER_EEVEE' if 'BLENDER_EEVEE' in [e.identifier for e in
                                                               bpy.types.RenderSettings.bl_rna.properties['engine'].enum_items] else 'BLENDER_EEVEE_NEXT'
    ob, P = load(name)
    h = P[:, 2].max()
    world = bpy.data.worlds.new('w')
    world.use_nodes = True
    world.node_tree.nodes['Background'].inputs['Color'].default_value = (0.75, 0.78, 0.82, 1)
    world.node_tree.nodes['Background'].inputs['Strength'].default_value = 0.9
    sc.world = world
    sun = bpy.data.objects.new('sun', bpy.data.lights.new('sun', 'SUN'))
    sun.data.energy = 2.5
    sun.rotation_euler = (math.radians(-40), 0, math.radians(20))
    sc.collection.objects.link(sun)
    cam = bpy.data.objects.new('cam', bpy.data.cameras.new('cam'))
    sc.collection.objects.link(cam)
    sc.camera = cam
    cam.data.type = 'ORTHO'
    sc.render.resolution_x, sc.render.resolution_y = (600, 600) if face else (500, 900)
    sc.render.film_transparent = False
    at = next((a for a in sys.argv if a.startswith('--at=')), None)
    if at:     # --at=x,y,z,size in rig pixels (as the geo's pivots)
        x, y, z, size = (float(v) for v in at[5:].split(','))
        target = np.array([-x, z, y]) / 16.0
        cam.data.ortho_scale = size / 16.0
        sc.render.resolution_x = sc.render.resolution_y = 600
    elif face:
        head = P[P[:, 2] > h - 0.42]
        target = head.mean(0) + [0, 0, 0.02]
        cam.data.ortho_scale = 0.5
    else:
        target = np.array([0, 0, h / 2])
        cam.data.ortho_scale = h * 1.1
    shots = []
    for i, yaw in enumerate((0, 35, 90)):
        a = math.radians(yaw)
        d = 6.0
        cam.location = (target[0] + d * math.sin(a), target[1] + d * math.cos(a), target[2])
        cam.rotation_euler = (math.radians(90), 0, math.pi - a)
        path = out.replace('.png', '_%d.png' % i)
        sc.render.filepath = path
        bpy.ops.render.render(write_still=True)
        shots.append(path)
    # stitch side by side
    imgs = [bpy.data.images.load(p) for p in shots]
    w, hh = imgs[0].size
    sheet = np.zeros((hh, w * len(imgs), 4), np.float32)
    for k, im in enumerate(imgs):
        sheet[:, k * w:(k + 1) * w] = np.array(im.pixels[:], np.float32).reshape(hh, w, 4)
    res = bpy.data.images.new('sheet', w * len(imgs), hh)
    res.pixels = sheet.flatten()
    res.filepath_raw = out
    res.file_format = 'PNG'
    res.save()
    for p in shots:
        os.remove(p)
    print('wrote', out)


main()
