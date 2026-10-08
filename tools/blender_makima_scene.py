"""Blender side of tools/makima_scene.py (her posed body, the outfit cut from it, lights, camera). Not meant to be run by hand."""
import json
import math
import sys

import bmesh
import bpy
import numpy as np
from mathutils import Vector


def conv(P):
    """makima_view's frame (y up, her front toward -z, x as the game draws it) -> Blender's (z up, her front toward +y)."""
    P = np.asarray(P, float)
    return np.stack([P[..., 0], -P[..., 2], P[..., 1]], -1)


def nodes_of(owner):
    try:
        owner.use_nodes = True
    except (AttributeError, TypeError):
        pass
    return owner.node_tree


def material(name, color=None, rough=0.6, **inputs):
    m = bpy.data.materials.new(name)
    nt = nodes_of(m)
    b = nt.nodes['Principled BSDF']
    if color is not None:
        b.inputs['Base Color'].default_value = (*color, 1)
    b.inputs['Roughness'].default_value = rough
    for k, v in inputs.items():
        b.inputs[k.replace('_', ' ')].default_value = v
    return m, nt, b


def textured(name, img, rough, **inputs):
    m, nt, b = material(name, None, rough, **inputs)
    t = nt.nodes.new('ShaderNodeTexImage')
    t.image = img
    nt.links.new(t.outputs['Color'], b.inputs['Base Color'])
    return m, nt, b, t


def body_materials(img):
    skin, nt, b, t = textured('skin', img, 0.5, Subsurface_Weight=0.08)
    b.inputs['Subsurface Radius'].default_value = (0.9, 0.35, 0.2)
    b.inputs['Subsurface Scale'].default_value = 0.01
    shirt, nt, b, t = textured('shirt', img, 0.72, Sheen_Weight=0.25)
    hair, nt, b, t = textured('hair', img, 0.42, Specular_IOR_Level=0.6)
    eyes, nt, b, t = textured('eyes', img, 0.15)
    nt.links.new(t.outputs['Color'], b.inputs['Emission Color'])
    b.inputs['Emission Strength'].default_value = 0.35
    # blue jeans: the trousers' own folds (their texture's brightness) over denim, a fine twill bump
    jeans, nt, b, t = textured('jeans', img, 0.82, Sheen_Weight=0.3)
    bw = nt.nodes.new('ShaderNodeRGBToBW')
    nt.links.new(t.outputs['Color'], bw.inputs['Color'])
    mr = nt.nodes.new('ShaderNodeMapRange')
    mr.inputs['From Min'].default_value = 0.0
    mr.inputs['From Max'].default_value = 0.32
    mr.inputs['To Min'].default_value = 0.3
    mr.inputs['To Max'].default_value = 1.0
    nt.links.new(bw.outputs['Val'], mr.inputs['Value'])
    mul = nt.nodes.new('ShaderNodeVectorMath')
    mul.operation = 'MULTIPLY'
    mul.inputs[1].default_value = (0.022, 0.06, 0.2)
    nt.links.new(mr.outputs['Result'], mul.inputs[0])
    nt.links.new(mul.outputs['Vector'], b.inputs['Base Color'])
    return {'skin': skin, 'shirt': shirt, 'hair': hair, 'eyes': eyes, 'trouser': jeans}


def patent(name, base=(0.008, 0.008, 0.01), rough=0.14):
    m, nt, b = material(name, base, rough, Coat_Weight=1.0, Coat_Roughness=0.03, Specular_IOR_Level=0.7)
    return m


def build_body(d, mats):
    X = conv(d['positions'])
    N = conv(d['normals'])
    T = np.array(d['tris'])
    # her triangles as the game winds them, turned outward for Blender
    fn = np.cross(X[T[:, 1]] - X[T[:, 0]], X[T[:, 2]] - X[T[:, 0]])
    if (np.einsum('ij,ij->i', fn, N[T].sum(1)) < 0).mean() > 0.5:
        T = T[:, ::-1]
    me = bpy.data.meshes.new('makima')
    me.from_pydata(X.tolist(), [], T.tolist())
    uv = np.array(d['uvs'], float).reshape(-1, 2)
    layer = me.uv_layers.new()
    layer.data.foreach_set('uv', uv[T.flatten()].flatten())
    order = ['skin', 'shirt', 'hair', 'trouser', 'eyes']
    for k in order:
        me.materials.append(mats[k])
    me.polygons.foreach_set('material_index', [order.index(l) for l in d['labels']])
    me.polygons.foreach_set('use_smooth', [True] * len(T))
    me.normals_split_custom_set_from_vertices(N.tolist())
    me.update()
    ob = bpy.data.objects.new('Makima', me)
    bpy.context.scene.collection.objects.link(ob)
    return ob, X, N, T


def shell(name, X, N, T, ids, offset, thick, mat, smooth=0, planes=()):
    """Her surface over the triangles `ids`, pushed `offset` out along her normals and given `thick` of thickness inward."""
    tri = T[ids]
    used, inv = np.unique(tri, return_inverse=True)
    P = X[used] + N[used] * offset
    me = bpy.data.meshes.new(name)
    me.from_pydata(P.tolist(), [], inv.reshape(-1, 3).tolist())
    bm = bmesh.new()
    bm.from_mesh(me)
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-4)
    for co, no in planes:      # a clean edge: everything beyond the plane cut away
        bmesh.ops.bisect_plane(bm, geom=bm.verts[:] + bm.edges[:] + bm.faces[:], plane_co=conv(co).tolist(),
                               plane_no=conv(no).tolist(), clear_outer=True)
    bm.to_mesh(me)
    bm.free()
    me.polygons.foreach_set('use_smooth', [True] * len(me.polygons))
    me.materials.append(mat)
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    if smooth:
        cs = ob.modifiers.new('smooth', 'CORRECTIVE_SMOOTH')
        cs.factor, cs.iterations, cs.use_only_smooth = 0.5, smooth, True
    so = ob.modifiers.new('thick', 'SOLIDIFY')
    so.thickness, so.offset, so.use_even_offset = thick, -1, True
    return ob


def laces(rows, offset, lace_mat, metal):
    """The corset's lacing: criss-crossed cord between eyelets either side of her spine."""
    pts = [[conv(p) + conv(n) * offset for p, n in row] for row in rows]
    for start in (0, 1):
        cu = bpy.data.curves.new('lace', 'CURVE')
        cu.dimensions = '3D'
        cu.bevel_depth = 0.0028
        cu.bevel_resolution = 2
        sp = cu.splines.new('POLY')
        seq = [pts[i][(i + start) % 2] for i in range(len(pts))]
        sp.points.add(len(seq) - 1)
        for p, q in zip(sp.points, seq):
            p.co = (*q, 1)
        ob = bpy.data.objects.new('lace', cu)
        ob.data.materials.append(lace_mat)
        bpy.context.scene.collection.objects.link(ob)
    for (a, na), (b, nb) in rows:
        for p, n in ((a, na), (b, nb)):
            bpy.ops.mesh.primitive_torus_add(major_radius=0.0065, minor_radius=0.0018, location=tuple(conv(p) + conv(n) * offset))
            o = bpy.context.active_object
            o.rotation_mode = 'QUATERNION'
            o.rotation_quaternion = Vector((0, 0, 1)).rotation_difference(Vector(conv(n)))
            o.data.materials.append(metal)


def backdrop(sc):
    """A dusky alley: dark towers either side, a warm glow low behind her, haze."""
    world = bpy.data.worlds.new('w')
    nt = nodes_of(world)
    nt.nodes['Background'].inputs['Color'].default_value = (0.012, 0.011, 0.014, 1)
    vol = nt.nodes.new('ShaderNodeVolumePrincipled')
    vol.inputs['Density'].default_value = 0.0035
    vol.inputs['Color'].default_value = (1.0, 0.85, 0.65, 1)
    nt.links.new(vol.outputs['Volume'], nt.nodes['World Output'].inputs['Volume'])
    sc.world = world
    # the glow: a wall far behind her, spherical gradient warm at its heart
    bpy.ops.mesh.primitive_plane_add(size=40, location=(0, 18, 6), rotation=(math.radians(90), 0, 0))
    wall = bpy.context.active_object
    m, nt, b = material('glow')
    em = nt.nodes.new('ShaderNodeEmission')
    tc = nt.nodes.new('ShaderNodeTexCoord')
    mp = nt.nodes.new('ShaderNodeMapping')
    mp.inputs['Location'].default_value = (0, -0.1, 0)
    mp.inputs['Scale'].default_value = (0.07, 0.11, 0.07)
    gr = nt.nodes.new('ShaderNodeTexGradient')
    gr.gradient_type = 'SPHERICAL'
    ramp = nt.nodes.new('ShaderNodeValToRGB')
    el = ramp.color_ramp.elements
    el[0].color = (0.01, 0.008, 0.008, 1)
    el[1].position, el[1].color = 0.85, (1.0, 0.86, 0.6, 1)
    mid = el.new(0.45)
    mid.color = (0.55, 0.26, 0.08, 1)
    nt.links.new(tc.outputs['Object'], mp.inputs['Vector'])
    nt.links.new(mp.outputs['Vector'], gr.inputs['Vector'])
    nt.links.new(gr.outputs['Fac'], ramp.inputs['Fac'])
    nt.links.new(ramp.outputs['Color'], em.inputs['Color'])
    em.inputs['Strength'].default_value = 1.6
    nt.links.new(em.outputs['Emission'], nt.nodes['Material Output'].inputs['Surface'])
    wall.data.materials.append(m)
    # the ground and the towers
    bpy.ops.mesh.primitive_plane_add(size=60, location=(0, 0, 0))
    floor = bpy.context.active_object
    floor.data.materials.append(material('ground', (0.03, 0.028, 0.026), 0.35)[0])
    dark = material('tower', (0.012, 0.012, 0.014), 0.85)[0]
    lit = material('window', (0.02, 0.02, 0.02), 0.5)[0]
    nt = lit.node_tree
    nt.nodes['Principled BSDF'].inputs['Emission Color'].default_value = (1.0, 0.7, 0.4, 1)
    nt.nodes['Principled BSDF'].inputs['Emission Strength'].default_value = 0.5
    rng = np.random.default_rng(7)
    for side in (-1, 1):
        for k, (y, w, h) in enumerate([(-0.5, 2.2, 14), (3.5, 3.0, 22), (8, 3.5, 30), (13, 4, 26)]):
            x = side * (2.6 + w / 2 + 0.6 * k)
            bpy.ops.mesh.primitive_cube_add(size=1, location=(x, y, h / 2))
            t = bpy.context.active_object
            t.scale = (w, 3.2, h)
            t.data.materials.append(dark)
            for _ in range(10 + 4 * k):     # a few lit windows on the face toward the alley
                wz = rng.uniform(2, h - 1)
                wy = y + rng.uniform(-1.3, 1.3)
                bpy.ops.mesh.primitive_cube_add(size=1, location=(x - side * (w / 2 + 0.01), wy, wz))
                p = bpy.context.active_object
                p.scale = (0.04, 0.26, 0.36)
                p.data.materials.append(lit if rng.random() < 0.55 else dark)


def light(sc, name, kind, loc, aim, power, color, size=1.0, spot=None):
    L = bpy.data.lights.new(name, kind)
    L.energy = power
    L.color = color
    if kind == 'AREA':
        L.size = size
    if spot:
        L.spot_size = math.radians(spot)
        L.spot_blend = 0.6
    o = bpy.data.objects.new(name, L)
    o.location = loc
    o.rotation_mode = 'QUATERNION'
    o.rotation_quaternion = Vector((0, 0, -1)).rotation_difference(Vector(aim) - Vector(loc))
    sc.collection.objects.link(o)
    return o


def main():
    d = json.load(open(sys.argv[sys.argv.index('--') + 1]))
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    engines = [e.identifier for e in bpy.types.RenderSettings.bl_rna.properties['engine'].enum_items]
    sc.render.engine = 'BLENDER_EEVEE_NEXT' if 'BLENDER_EEVEE_NEXT' in engines else 'BLENDER_EEVEE'
    if d.get('engine') == 'cycles':      # cleaner: no shading stripes on her smooth-shaded mesh, true reflections in the patent
        sc.render.engine = 'CYCLES'
        sc.cycles.samples = d['samples']
        sc.cycles.use_denoising = True
        prefs = bpy.context.preferences.addons['cycles'].preferences
        for kind in ('OPTIX', 'CUDA', 'HIP', 'ONEAPI', 'METAL'):
            try:
                prefs.compute_device_type = kind
            except TypeError:
                continue
            prefs.get_devices()
            gpus = [dv for dv in prefs.devices if dv.type == kind]
            if gpus:
                for dv in gpus:
                    dv.use = True
                sc.cycles.device = 'GPU'
                print('cycles on', kind, [dv.name for dv in gpus])
                break
    ee = sc.eevee
    for k, v in (('taa_render_samples', d['samples']), ('use_raytracing', True), ('use_shadows', True),
                 ('volumetric_tile_size', '4'), ('volumetric_end', 40.0)):
        try:
            setattr(ee, k, v)
        except (AttributeError, TypeError):
            pass
    sc.view_settings.exposure = -0.25
    try:
        sc.view_settings.view_transform = 'AgX'
        sc.view_settings.look = 'AgX - Punchy'
    except TypeError:
        pass

    img = bpy.data.images.load(d['texture'])
    ob, X, N, T = build_body(d, body_materials(img))
    g = d['garments']
    corset = patent('corset')
    cut = d['cuts']
    shell('Corset', X, N, T, g['corset'], 0.0075, 0.004, corset, planes=cut['corset'])
    boots = patent('boots')
    for s in 'lr':
        shell('Boot_' + s, X, N, T, g['boot_' + s], 0.009, 0.004, boots, planes=cut['boot_' + s])
    shell('Gloves', X, N, T, g['glove'], 0.0022, 0.0015, patent('gloves', (0.01, 0.01, 0.012), 0.22))
    laces(d['laces'], 0.0115, material('lace', (0.01, 0.01, 0.01), 0.45, Sheen_Weight=0.6)[0],
          material('eyelet', (0.6, 0.6, 0.62), 0.25, Metallic=1.0)[0])

    backdrop(sc)
    m = d['marks']
    waist = conv(m['torso'])
    # the glow behind her, rims either side, a cool soft fill from the camera's side so her back still reads
    light(sc, 'back', 'AREA', (0.2, 3.5, 2.8), waist + [0, 0, 0.3], 900, (1.0, 0.72, 0.42), 2.5)
    light(sc, 'rim_l', 'SPOT', (-1.6, 1.6, 2.2), waist + [0, 0, 0.4], 450, (1.0, 0.78, 0.5), spot=40)
    light(sc, 'rim_r', 'SPOT', (1.7, 1.4, 2.6), conv(m['head']), 380, (1.0, 0.8, 0.55), spot=35)
    light(sc, 'fill', 'AREA', (0.6, -2.6, 0.6), waist, 45, (0.72, 0.8, 1.0), 2.0)
    light(sc, 'face', 'SPOT', (1.2, 0.2, 2.3), conv(m['eyes']), 60, (1.0, 0.85, 0.7), spot=20)

    # the camera low behind her and a little to her left, looking up past her hips
    cam = bpy.data.objects.new('camera', bpy.data.cameras.new('camera'))
    cam.data.lens = 22
    cam.location = (-0.18, -1.0, 0.42)
    target = bpy.data.objects.new('aim', None)
    target.location = waist + [0.06, 0, 0.32]
    sc.collection.objects.link(target)
    tr = cam.constraints.new('TRACK_TO')
    tr.target, tr.track_axis, tr.up_axis = target, 'TRACK_NEGATIVE_Z', 'UP_Y'
    sc.collection.objects.link(cam)
    sc.camera = cam

    sc.render.resolution_x, sc.render.resolution_y = d['size']
    sc.render.filepath = d['out']
    try:
        bpy.ops.file.pack_all()
    except RuntimeError:
        pass
    bpy.ops.wm.save_as_mainfile(filepath=d['blend'])
    bpy.ops.render.render(write_still=True)
    print('wrote', d['out'], d['blend'])


main()
