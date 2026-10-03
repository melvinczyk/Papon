"""Blender pass that conforms a MakeHuman head to a face reconstructed from a photo.

  Blender -b --python tools/blender_face_fit.py -- <in.npz> <out.npz>

Run by mh_human.py (Character.conform); not meant to be run by hand. Input: the body (`V`, `quads`),
a per-vertex `weight` (the front of the face, 0 round the eyes and mouth), and the reconstructed
face (`F` points already in MakeHuman space, `tris` from MediaPipe's canonical face).

A Shrinkwrap (projecting along the face's forward axis, both ways) lays the weighted vertices onto
the scanned surface, so his face has the scan's exact depth and contours; a Corrective Smooth over
the same region then restores MakeHuman's own fine detail (eyelid folds, nostrils, lip edges) on the
new shape, which the 898-triangle scan is too coarse to carry.
"""
import sys

import bpy
import numpy as np


def mesh_object(name, verts, faces):
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts.tolist(), [], faces.tolist())
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    return ob


def main():
    src, dst = sys.argv[sys.argv.index('--') + 1:][:2]
    d = np.load(src)
    V, quads, weight, F, tris = d['V'], d['quads'], d['weight'], d['F'], d['tris']
    bpy.ops.wm.read_factory_settings(use_empty=True)
    head = mesh_object('head', V, quads)
    scan = mesh_object('scan', F, tris)
    g = head.vertex_groups.new(name='fit')
    for i in np.nonzero(weight > 1e-4)[0]:
        g.add([int(i)], float(weight[i]), 'REPLACE')

    sw = head.modifiers.new('wrap', 'SHRINKWRAP')
    sw.target = scan
    sw.wrap_method = 'PROJECT'
    sw.use_project_z = True
    sw.use_negative_direction = True
    sw.use_positive_direction = True
    sw.vertex_group = 'fit'
    cs = head.modifiers.new('detail', 'CORRECTIVE_SMOOTH')
    cs.rest_source = 'ORCO'
    cs.smooth_type = 'LENGTH_WEIGHTED'
    cs.iterations = 12
    cs.factor = 0.5
    cs.vertex_group = 'fit'
    bpy.context.view_layer.objects.active = head
    for m in ('wrap', 'detail'):
        bpy.ops.object.modifier_apply(modifier=m)

    out = np.zeros(len(head.data.vertices) * 3)
    head.data.vertices.foreach_get('co', out)
    out = out.reshape(-1, 3)
    np.savez(dst, V=out)
    moved = np.linalg.norm(out - V, axis=1)
    print('conformed: %d vertices moved, at most %.3f dm' % ((moved > 1e-4).sum(), moved.max()))


main()
