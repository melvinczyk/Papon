"""Blender side of tools/makima_preview.py. Not meant to be run by hand."""
import json
import math
import sys

import bpy
import numpy as np


def box(name, lo, hi, mat):
    lo, hi = np.array(lo), np.array(hi)
    v = [[x, y, z] for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])]
    me = bpy.data.meshes.new(name)
    me.from_pydata(v, [], [[0, 1, 3, 2], [4, 6, 7, 5], [0, 4, 5, 1], [2, 3, 7, 6], [0, 2, 6, 4], [1, 5, 7, 3]])
    ob = bpy.data.objects.new(name, me)
    ob.data.materials.append(mat)
    return ob


def main():
    d = json.load(open(sys.argv[sys.argv.index('--') + 1]))
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.engine = 'BLENDER_EEVEE_NEXT' if 'BLENDER_EEVEE_NEXT' in [e.identifier for e in
                                                                       bpy.types.RenderSettings.bl_rna.properties['engine'].enum_items] else 'BLENDER_EEVEE'
    mat = bpy.data.materials.new('skin')
    mat.use_nodes = True
    tex = mat.node_tree.nodes.new('ShaderNodeTexImage')
    tex.image = bpy.data.images.load(d['texture'])
    bsdf = mat.node_tree.nodes['Principled BSDF']
    mat.node_tree.links.new(tex.outputs['Color'], bsdf.inputs['Base Color'])
    bsdf.inputs['Roughness'].default_value = 0.8
    grid = bpy.data.materials.new('grid')
    grid.use_nodes = True
    nt = grid.node_tree
    chk = nt.nodes.new('ShaderNodeTexChecker')
    chk.inputs['Scale'].default_value = 8.0
    chk.inputs['Color1'].default_value = (0.42, 0.5, 0.36, 1)
    chk.inputs['Color2'].default_value = (0.36, 0.44, 0.3, 1)
    nt.links.new(chk.outputs['Color'], nt.nodes['Principled BSDF'].inputs['Base Color'])
    steve = bpy.data.materials.new('steve')
    steve.use_nodes = True
    steve.node_tree.nodes['Principled BSDF'].inputs['Base Color'].default_value = (0.2, 0.45, 0.75, 1)
    uv = np.array(d['uvs'], float).reshape(-1, 2)
    faces = d['faces']
    yaw = {'front': 0, '34': -35, 'side': -90, 'back': 180, 'top': 0, 'side2': 90, '34b': 145}[d['view']]
    n = len(d['frames'])
    for k, X in enumerate(d['frames']):
        P = np.array(X) / 16.0
        P = P[:, [0, 2, 1]] * [-1, -1, 1]       # rig -> Blender (her front toward +Y), mirrored as GeckoLib bakes it
        me = bpy.data.meshes.new('m')
        me.from_pydata(P.tolist(), [], [f[::-1] for f in faces])
        layer = me.uv_layers.new()
        layer.data.foreach_set('uv', uv[[v for f in faces for v in f[::-1]]].flatten())
        for p in me.polygons:
            p.use_smooth = True
        ob = bpy.data.objects.new('m', me)
        ob.data.materials.append(mat)
        objs = [ob]
        bpy.ops.mesh.primitive_plane_add(size=6)
        plane = bpy.context.active_object
        plane.data.materials.append(grid)
        if d.get('rider'):
            x, y, z, ryaw, pitch = d['rider']
            parts = [('head', (-0.25, -0.25, 1.3), (0.25, 0.25, 1.8)), ('body', (-0.25, -0.125, 0.75), (0.25, 0.125, 1.5)),
                     ('legs', (-0.25, -0.125, 0.0), (0.25, 0.125, 0.75))]
            for name, lo, hi in parts:
                b = box(name, lo, hi, steve)
                b.rotation_mode = 'ZXY'
                # rider: x, y up, z in her rig axes (front -Z = Blender +Y): Blender (-x, -z, y)
                b.location = (-x, -z, y)
                b.rotation_euler = (math.radians(-pitch), 0, math.radians(-ryaw))
                objs.append(b)
        for o in objs:
            sc.collection.objects.link(o)
        lo, hi = P.min(0), P.max(0)
        c = (lo + hi) / 2
        span = max(hi - lo) * 1.35 + 0.4
        a = math.radians(yaw)
        if d['view'] == 'top':
            loc, rot = (c[0], c[1], 6), (0, 0, math.pi)
        else:
            dist = 8
            loc = (c[0] + math.sin(a) * dist, c[1] + math.cos(a) * dist, c[2] + 2.0)
            look = np.array(c) - np.array(loc)
            rot = (math.atan2(math.hypot(look[0], look[1]), -look[2]), 0, math.atan2(look[0], -look[1]) + math.pi)
        cam = bpy.data.objects.new('cam', bpy.data.cameras.new('cam'))
        cam.data.type = 'ORTHO'
        cam.data.ortho_scale = span
        cam.location = loc
        cam.rotation_euler = rot
        sc.collection.objects.link(cam)
        sc.camera = cam
        world = bpy.data.worlds.new('w')
        world.use_nodes = True
        world.node_tree.nodes['Background'].inputs['Color'].default_value = (0.75, 0.78, 0.82, 1)
        sc.world = world
        sun = bpy.data.objects.new('sun', bpy.data.lights.new('sun', 'SUN'))
        sun.data.energy = 2.5
        sun.rotation_euler = (math.radians(40), 0, math.radians(160))
        sc.collection.objects.link(sun)
        sc.render.resolution_x = 640
        sc.render.resolution_y = 480
        sc.render.filepath = d['out'].replace('.png', '_%d.png' % k)
        bpy.ops.render.render(write_still=True)
        for o in list(sc.collection.objects):
            bpy.data.objects.remove(o)
    print('wrote', d['out'])


main()
