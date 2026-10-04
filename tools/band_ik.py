#!/usr/bin/env python3
"""Hand poses for the mariachi band, solved on the rig so the instruments line up.

The band's arms are one bone each (gen_vendor.rig_hands): a rotation at the shoulder and a stretch
(`arm_*_mesh` scaled along Y, `hand_*` slid down by (k - 1) * 6.5 px), so "the hand there" is a
small IK problem: arm rotation (X, Y, Z) and stretch k so the hand, or a point on what the hand
holds, lands on a target, with the prop pointing the right way when it matters (the bow across the
strings). Rotations are in the animation files' convention, applied as GeckoLib does
(pose_solver.rot: R = Rz(-Z) Ry(Y) Rx(-X) in geo coordinates).

  bow_poses()      the bowing arm for bow positions 0 (frog on the string) .. 1 (tip on the string):
                   the hand travels along a line through the bridge and the bow stays square across
                   the strings (the bow bone's own rotation is solved with the arm)
  violin_left()    the left hand on the violin's neck, low and high
  trumpet_poses()  both hands on the trumpet (right hand's fingers on the valve caps, left hand
                   cradling the valve block) for the horn up at the lips, raised for the high notes,
                   and lowered to rest
  requinto_poses() the singer's strumming hand over the soundhole and his fretting hand low and high
                   on the neck
  coro_poses()     the backup singers' hand mic up under the mouth, and down at rest

gen_band_anims.py keys these into the play loops and writes them to assets/papon/song/band_poses.json
for client/MariachiModel, which blends between them in time with the music.
"""
import json
import os

import numpy as np
from scipy.optimize import least_squares

from pose_solver import rot

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GEO = os.path.join(ROOT, 'src/main/resources/assets/papon/geo/entity/mariachi.geo.json')
ARM = 6.5           # px from the shoulder pivot to the hand at k = 1


class Rig:
    def __init__(self):
        geo = json.load(open(GEO))['minecraft:geometry'][0]
        self.bones = {b['name']: b for b in geo['bones']}

    def pivot(self, b):
        return np.array(self.bones[b]['pivot'], float)

    def rest(self, b):
        return np.array(self.bones[b].get('rotation', [0, 0, 0]), float)

    def cube_point(self, b, i, frac):
        c = self.bones[b]['cubes'][i]
        return np.array(c['origin'], float) + np.array(c['size'], float) * np.asarray(frac, float)

    def to_world(self, bone, p, pose):
        """A point given in rest (geo) coordinates of `bone` to world, through every parent.
        pose: {bone: (rotation [x, y, z] added to the rest rotation, position offset)}"""
        b = bone
        p = np.asarray(p, float)
        while b:
            r, off = pose.get(b, ((0, 0, 0), (0, 0, 0)))
            total = self.rest(b) + np.asarray(r, float)
            if np.any(total):
                piv = self.pivot(b)
                p = piv + rot(*total) @ (p - piv)
            p = p + np.asarray(off, float)
            b = self.bones[b].get('parent')
        return p

    def dir_to_world(self, bone, v, pose):
        a = self.to_world(bone, np.zeros(3), pose)
        return self.to_world(bone, np.asarray(v, float), pose) - a


RIG = None


def rig():
    global RIG
    if RIG is None:
        RIG = Rig()
    return RIG


def arm_pose(side, X, Y, Z, k, extra=None):
    pose = {'arm_' + side: ((X, Y, Z), (0, 0, 0)), 'hand_' + side: ((0, 0, 0), (0, -(k - 1) * ARM, 0))}
    if extra:
        pose.update(extra)
    return pose


def solve(fun, x0, lo, hi):
    r = least_squares(fun, x0, bounds=(lo, hi), xtol=1e-10, ftol=1e-10, max_nfev=4000)
    return r.x, float(np.sqrt(np.mean(r.fun ** 2)))


def reach(side, point_bone, point, target, x0, prop_rot=None, natural=0.02):
    """Arm (X, Y, Z, k) so `point` (rest coords, on point_bone in the hand) lands on `target`."""
    R = rig()

    def f(x):
        extra = {point_bone: (prop_rot, (0, 0, 0))} if prop_rot is not None and point_bone not in ('hand_r', 'hand_l') else None
        p = R.to_world(point_bone, point, arm_pose(side, *x, extra=extra))
        return np.concatenate([p - target, natural * np.array([x[1], x[2], 0, 0])])
    x, err = solve(f, x0, [-200, -120, -120, 0.8], [60, 120, 120, 3.2])
    return x, err


def orient(scroll, top):
    """Rotation (animation convention, degrees) taking local -Z to `scroll` and local +Y to `top`
    (made square to it first). Returns (rotation, scroll unit, top unit)."""
    sw = np.asarray(scroll, float)
    sw = sw / np.linalg.norm(sw)
    nw = np.asarray(top, float)
    nw = nw - sw * (nw @ sw)
    nw = nw / np.linalg.norm(nw)

    def f(x):
        R = rot(*x)
        return np.concatenate([R @ [0, 0, -1] - sw, R @ [0, 1, 0] - nw])
    best = None
    for x0 in ([0, 0, 0], [20, 30, -30], [-20, -30, 30], [0, 90, 0], [0, -90, 0]):
        x, err = solve(f, np.array(x0, float), [-180] * 3, [180] * 3)
        if best is None or err < best[1]:
            best = (x, err)
    return [round(float(v), 2) for v in best[0]], sw, nw


def fmt(x, prop=None):
    out = {'rot': [round(float(v), 1) for v in x[:3]], 'k': round(float(x[3]), 3)}
    if prop is not None:
        out['prop'] = [round(float(v), 1) for v in prop]
    return out


# ===================================================================== the violin

def violin_frame():
    """The violin's string axis (scroll-wards), its top's normal, the contact point between the
    bridge and the fingerboard, and the bow's direction across the strings (towards his left)."""
    R = rig()
    pose = {}
    s = R.dir_to_world('violin', [0, 0, -1], pose)
    n = R.dir_to_world('violin', [0, 1, 0], pose)
    s, n = s / np.linalg.norm(s), n / np.linalg.norm(n)
    c = R.to_world('violin', R.pivot('violin') + np.array([0, 1.15, -0.5]), pose)
    u = np.cross(n, s)
    if u[0] < 0:
        u = -u
    return s, n, c, u / np.linalg.norm(u)


BOW_STICK = ('bow', 0)      # the stick cube: hair runs along its -Z


def bow_poses(ps=(0, 0.25, 0.5, 0.75, 1.0)):
    R = rig()
    s, n, c, u = violin_frame()
    stick = R.bones['bow']['cubes'][1]           # the hair
    hair_at = lambda p: np.array([stick['origin'][0] + 0.1, stick['origin'][1], stick['origin'][2] + stick['size'][2] - 1.5 - p * 13.5])
    out = []
    x = np.array([-70, -40, 0, 1.6, 0, 0, 0], float)
    for p in ps:
        prev = x.copy()

        def f(v):
            pose = arm_pose('r', *v[:4], extra={'bow': ((v[4], v[5], v[6]), (0, 0, 0))})
            hp = R.to_world('bow', hair_at(p), pose)
            d = R.dir_to_world('bow', [0, 0, -1], pose)
            d = d / np.linalg.norm(d)
            hair = R.dir_to_world('bow', [0, -1, 0], pose)
            hair = hair / np.linalg.norm(hair)
            # the hair just on top of the strings, the bow square across them, the hair (under the
            # stick) facing down onto them; and close to the last pose, so the blend never spins
            return np.concatenate([(hp - (c + n * 0.3)), 6 * (d - u), 2 * (hair + n), 0.004 * (v - prev),
                                   [0.001 * v[1], 0.001 * v[2]]])
        x, err = solve(f, x, [-200, -120, -120, 0.8, -180, -180, -180], [60, 120, 120, 3.6, 180, 180, 180])
        out.append({'p': p, **fmt(x[:4], x[4:7]), 'err': round(err, 3)})
    return out


def violin_left():
    """The left hand under the neck: low (by the body) and high (by the scroll)."""
    R = rig()
    s, n, c, u = violin_frame()
    out = {}
    x0 = np.array([-90, 10, -20, 1.4])
    for name, z in (('low', -4.5), ('high', -8.5)):
        t = R.to_world('violin', R.pivot('violin') + np.array([0, -1.2, z]), {})
        x, err = reach('l', 'hand_l', R.pivot('hand_l'), t, x0)
        out[name] = {**fmt(x), 'err': round(err, 3)}
        x0 = x
    return out


# ===================================================================== the trumpet

def trumpet_poses():
    """Both hands on the horn: the right hand's fist resting on the valve caps (fingers on them),
    the left hand under the valve block. For the horn at the lips (play), lifted for the high
    notes (lift) and lowered to rest (down): the trumpet bone's X in each."""
    R = rig()
    out = {}
    for name, tx in (('play', -4.0), ('lift', -16.0), ('down', 52.0)):
        pose = {'trumpet': ((tx, 0, 0), (0, 0, 0))}
        valves = R.to_world('trumpet', R.pivot('trumpet') + np.array([-0.35, 3.4, -4.6]), pose)
        up = R.dir_to_world('trumpet', [0, 1, 0], pose)
        right_t = valves + up * 2.6 + np.array([-0.6, 0, 0])
        block = R.to_world('trumpet', R.pivot('trumpet') + np.array([1.0, 1.5, -4.5]), pose)
        left_t = block - up * 1.6 + np.array([2.6, 0, 0])
        xr, er = reach('r', 'hand_r', R.pivot('hand_r'), right_t, np.array([-100, -40, 0, 2.0]))
        xl, el = reach('l', 'hand_l', R.pivot('hand_l'), left_t, np.array([-90, 40, 0, 2.0]))
        out[name] = {'trumpet': tx, 'r': {**fmt(xr), 'err': round(er, 3)}, 'l': {**fmt(xl), 'err': round(el, 3)}}
    return out


# ===================================================================== the requinto

def requinto_poses():
    R = rig()
    piv = R.pivot('requinto')
    # the strumming fist just in front of the strings over the soundhole
    strum_t = R.to_world('requinto', piv + np.array([0, 0.5, -3.8]), {})
    xs, es = reach('r', 'hand_r', R.pivot('hand_r'), strum_t, np.array([-50, -40, 0, 2.0]))
    out = {'strum': {**fmt(xs), 'err': round(es, 3)}}
    x0 = np.array([-60, 30, 0, 2.0])
    for name, y in (('low', 5.0), ('high', 10.5)):
        t = R.to_world('requinto', piv + np.array([0, y, -3.0]), {})
        x, err = reach('l', 'hand_l', R.pivot('hand_l'), t, x0)
        out[name] = {**fmt(x), 'err': round(err, 3)}
        x0 = x
    return out


# ===================================================================== the coro

def coro_poses():
    """The hand mic's grille just under the mouth (up), and held at the chest (down)."""
    R = rig()
    grille = R.cube_point('hand_mic', 1, [0.5, 0.5, 0.5])
    out = {}
    for name, t, x0 in (('up', np.array([1.2, 7.2, -11.5]), [-150, 20, 0, 1.6]), ('down', np.array([4.0, 4.5, -11.0]), [-80, 20, 0, 1.3])):
        x, err = reach('l', 'hand_mic', grille, t, np.array(x0, float))
        out[name] = {**fmt(x), 'err': round(err, 3)}
    return out


def all_poses():
    return {'bow': bow_poses(), 'violin_l': violin_left(), 'trumpet': trumpet_poses(), 'requinto': requinto_poses(),
            'coro': coro_poses()}


if __name__ == '__main__':
    print(json.dumps(all_poses(), indent=1))
