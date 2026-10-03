"""Blender side of tools/anim_preview.py: one copy of the posed mesh per frame, side by side, rendered
in one shot with the frame times underneath. Not meant to be run by hand."""
import json
import math
import sys

import bpy
import numpy as np


def main():
    d = json.load(open(sys.argv[sys.argv.index('--') + 1]))
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.engine = 'BLENDER_EEVEE' if 'BLENDER_EEVEE' in [e.identifier for e in
                                                               bpy.types.RenderSettings.bl_rna.properties['engine'].enum_items] else 'BLENDER_EEVEE_NEXT'
    img = bpy.data.images.load(d['texture'])
    mat = bpy.data.materials.new('skin')
    mat.use_nodes = True
    tex = mat.node_tree.nodes.new('ShaderNodeTexImage')
    tex.image = img
    bsdf = mat.node_tree.nodes['Principled BSDF']
    mat.node_tree.links.new(tex.outputs['Color'], bsdf.inputs['Base Color'])
    bsdf.inputs['Roughness'].default_value = 0.8
    prop_mat = bpy.data.materials.new('prop')
    prop_mat.diffuse_color = (0.9, 0.6, 0.2, 1)
    prop_mat.use_nodes = True
    prop_mat.node_tree.nodes['Principled BSDF'].inputs['Base Color'].default_value = (0.9, 0.55, 0.15, 1)
    uv = np.array(d['uvs'], float).reshape(-1, 2)
    faces = d['faces']
    yaw = {'front': 0, '34': -35, 'side': -90}[d['view']]
    spacing = 1.7
    n = len(d['frames'])
    for k, X in enumerate(d['frames']):
        P = np.array(X) / 16.0
        P = P[:, [0, 2, 1]] * [-1, -1, 1]       # rig -> Blender, mirrored as GeckoLib bakes it
        a = math.radians(yaw)
        R = np.array([[math.cos(a), -math.sin(a), 0], [math.sin(a), math.cos(a), 0], [0, 0, 1]])
        P = P @ R.T + [-k * spacing, 0, 0]
        me = bpy.data.meshes.new('f%d' % k)
        me.from_pydata(P.tolist(), [], [f[::-1] for f in faces])
        layer = me.uv_layers.new()
        layer.data.foreach_set('uv', uv[[v for f in faces for v in f[::-1]]].flatten())
        for p in me.polygons:
            p.use_smooth = True
        ob = bpy.data.objects.new('f%d' % k, me)
        ob.data.materials.append(mat)
        sc.collection.objects.link(ob)
        for v, f in d['props'][k]:
            Q = np.array(v) / 16.0
            Q = Q[:, [0, 2, 1]] * [-1, -1, 1] @ R.T + [-k * spacing, 0, 0]
            pm = bpy.data.meshes.new('p')
            pm.from_pydata(Q.tolist(), [], [x[::-1] for x in f])
            po = bpy.data.objects.new('p', pm)
            po.data.materials.append(prop_mat)
            sc.collection.objects.link(po)
        txt = bpy.data.curves.new('t%d' % k, 'FONT')
        txt.body = '%.2fs' % d['times'][k]
        txt.size = 0.25
        to = bpy.data.objects.new('t%d' % k, txt)
        to.location = (-k * spacing + 0.3, 1.5, 0.05)
        to.rotation_euler = (math.radians(90), 0, math.pi)
        sc.collection.objects.link(to)
    world = bpy.data.worlds.new('w')
    world.use_nodes = True
    world.node_tree.nodes['Background'].inputs['Color'].default_value = (0.75, 0.78, 0.82, 1)
    sc.world = world
    sun = bpy.data.objects.new('sun', bpy.data.lights.new('sun', 'SUN'))
    sun.data.energy = 2.5
    sun.rotation_euler = (math.radians(40), 0, math.radians(160))
    sc.collection.objects.link(sun)
    cam = bpy.data.objects.new('cam', bpy.data.cameras.new('cam'))
    cam.data.type = 'ORTHO'
    width = spacing * n
    cam.data.ortho_scale = width
    cam.location = (-(width / 2 - spacing / 2), 20, 1.25)        # in front of him (he faces +Y)
    cam.rotation_euler = (math.radians(90), 0, math.pi)
    sc.collection.objects.link(cam)
    sc.camera = cam
    sc.render.resolution_x = 240 * n
    sc.render.resolution_y = int(240 * n * 2.7 / width)
    sc.render.filepath = d['out']
    bpy.ops.render.render(write_still=True)
    print('wrote', d['out'])


main()
