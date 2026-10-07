#!/usr/bin/env python3
"""Makima's animations (entity/MakimaEntity, rig from gen_makima_mesh.py): animations/entity/makima.animation.json.

  python3.11 tools/gen_makima_anims.py
  python3.11 tools/makima_preview.py out.png missionary doggy view=side t=0     # stills, any time in the loop

Her bind pose is the model's: standing, both hands crossed in front of her chest. Each position is a held body pose
(MakimaEntity.Position, the same names) whose arms are solved (two-bone IK on her real arm lengths) onto targets: hands
planted on the ground, on her knees, round your neck, on your chest, in her hair. Every loop swings between two such arm
poses and sways her head and hips, so she is never still; the thrust itself is code (client/MakimaRenderer: her body
jolted, her back arching, her head thrown back and the braid swinging, in step with PillowClient.thrust), so the motion
never drifts out of step with whoever is with her. Every animation keys every bone (GeckoLib leaves unkeyed bones as
they were).

Rotations in degrees, GeckoLib's convention: rot X < 0 swings a hanging limb forward, so it tips an upright one (root,
torso, chest, head) back; leg_l/arm_l Z < 0 out to her left. Root positions are given in metres of her model (PX rig
pixels each); targets in metres of the entity (her front, as she stood, toward -Z).
"""
import json
import math
import os

import numpy as np

import anim_preview as AP
from gen_makima_mesh import PX

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')
OUT = os.path.join(ASSETS, 'animations/entity/makima.animation.json')
BONES = ['root', 'torso', 'chest', 'head', 'braid', 'arm_l', 'arm_r', 'forearm_l', 'forearm_r', 'leg_l', 'leg_r',
         'shin_l', 'shin_r']
SINE = 'easeInOutSine'
GEO = json.load(open(os.path.join(ASSETS, 'geo/entity/makima_mesh.geo.json')))['minecraft:geometry'][0]
PIV = {b['name']: np.array(b['pivot'], float) for b in GEO['bones']}
LOC = {k: np.array(v, float) for b in GEO['bones'] for k, v in b.get('locators', {}).items()}
SIDES = (('l', 1), ('r', -1))


# ---------------------------------------------------------------- rotations
def minrot(a, b):
    """The smallest rotation taking direction a onto direction b."""
    a, b = a / np.linalg.norm(a), b / np.linalg.norm(b)
    v, c = np.cross(a, b), float(a @ b)
    if c < -0.9999:
        axis = np.cross(a, [1, 0, 0] if abs(a[0]) < 0.9 else [0, 1, 0])
        axis /= np.linalg.norm(axis)
        return 2 * np.outer(axis, axis) - np.eye(3)
    K = np.array([[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]])
    return np.eye(3) + K + K @ K / (1 + c)


def euler(R):
    """PS.rot(X, Y, Z) == R (pose_solver's convention, the one anim_preview poses with): back to the animation's degrees."""
    b = math.asin(max(-1.0, min(1.0, -R[2, 0])))
    a = math.atan2(R[2, 1], R[2, 2])
    c = math.atan2(R[1, 0], R[0, 0])
    return (round(-math.degrees(a), 2), round(math.degrees(b), 2), round(-math.degrees(c), 2))


def m(*v):
    """Metres of her model -> rig pixels."""
    return np.array(v, float) * PX


# ---------------------------------------------------------------- poses
def body(rot, root_m=(0, 0, 0)):
    """A pose: {bone: [rotation, position]}, the root moved by root_m metres."""
    out = {b: [tuple(rot.get(b, (0, 0, 0))), (0, 0, 0)] for b in BONES}
    out['root'][1] = tuple(float(v) for v in np.round(m(*root_m), 3))
    return out


def world(pose):
    """Bone world matrices (rig pixels) for a pose, as anim_preview poses the rig."""
    anim = {'bones': {b: {'rotation': {'0': {'vector': list(r)}}, 'position': {'0': {'vector': list(p)}}}
                      for b, (r, p) in pose.items()}}
    M, _ = AP.pose(GEO, anim, 0)
    return M


def apply(M, bone, p):
    return (M[bone] @ np.append(p, 1))[:3]


def joint(pose, bone, p=None):
    """Where a bone's pivot (or point p of it) is, entity space, rig pixels."""
    return apply(world(pose), bone, PIV[bone] if p is None else p)


def shoulder(pose, side):
    return joint(pose, 'chest', PIV['arm_' + side])


def out(pose, side):
    """+1 or -1: which way (entity x) is out to that side of her (turned round, her left is at -x)."""
    return float(np.sign(shoulder(pose, side)[0]))


def arms(pose, targets):
    """Two-bone IK: each side's wrist onto targets[side] = (wrist target, elbow pole direction), entity space, rig pixels;
    returns a copy of the pose with arm_* and forearm_* rotations solved."""
    pose = {b: list(v) for b, v in pose.items()}
    for side, (T, pole) in targets.items():
        pose['arm_' + side][0] = (0, 0, 0)
        pose['forearm_' + side][0] = (0, 0, 0)
        M = world(pose)
        T, pole = np.asarray(T, float), np.asarray(pole, float)
        sh, el, wr = PIV['arm_' + side], PIV['forearm_' + side], LOC['wrist_' + side]
        S = apply(M, 'chest', sh)
        Rc = M['chest'][:3, :3]
        u0, f0 = el - sh, wr - el
        a, b = np.linalg.norm(u0), np.linalg.norm(f0)
        d = T - S
        dh = d / np.linalg.norm(d)
        dist = min(np.linalg.norm(d), a + b - 1e-3)
        x = (a * a - b * b + dist * dist) / (2 * dist)
        h = math.sqrt(max(a * a - x * x, 0))
        n = pole - (pole @ dh) * dh
        n /= np.linalg.norm(n)
        E = S + x * dh + h * n
        Ra = minrot(u0, Rc.T @ (E - S))
        Rf = minrot(f0, Ra.T @ Rc.T @ (S + dist * dh - E))
        pose['arm_' + side][0] = euler(Ra)
        pose['forearm_' + side][0] = euler(Rf)
    return pose


# ---------------------------------------------------------------- the positions
# on her back, you planked over her: laid back about her heels, knees up and wide, head back on the ground
MISSIONARY = body({
    'root': (-90, 0, 0), 'head': (-18, 0, 0), 'braid': (0, 0, 70),
    'leg_l': (-55, 0, -40), 'leg_r': (-55, 0, 40), 'shin_l': (125, 0, 0), 'shin_r': (125, 0, 0),
}, (0, 0.113, 0))


def missionary_arms():
    # A: up round your neck (you are over her chest); B: flung up over her head onto the ground
    a = {s: (shoulder(MISSIONARY, s) + m(-0.04 * sg, 0.3, 0.1), m(sg, 0.1, -0.5)) for s, sg in SIDES}
    b = {s: (shoulder(MISSIONARY, s) + m(0.1 * sg, 0.0, 0.28), m(sg, 0.6, 0)) for s, sg in SIDES}
    return arms(MISSIONARY, a), arms(MISSIONARY, b)


# on her knees, face down and hips up, you kneeling behind her: turned away from you (root Y 180), thighs straight down
# to the knees (the torso's tilt taken back off the legs), shins back along the ground, chest low, looking back at you
DOGGY = body({
    'root': (0, 180, 0), 'torso': (62, 0, 0), 'chest': (50, 0, 0), 'head': (-50, -30, 0),
    'leg_l': (-58, 0, -8), 'leg_r': (-58, 0, 8), 'shin_l': (90, 0, 0), 'shin_r': (90, 0, 0),
}, (0, -0.44, 0))


def doggy_arms():
    # A: hands flat on the ground ahead of her shoulders; B: one hand down, the other reaching back for you
    def ground(s, ahead):
        sh, sg = shoulder(DOGGY, s), out(DOGGY, s)
        return (np.array([sh[0] + m(0.05 * sg)[0], m(0, 0.04, 0)[1], sh[2] + m(0, 0, ahead)[2]]), m(sg, 0.1, -0.7))
    hip, sr = joint(DOGGY, 'torso'), out(DOGGY, 'r')
    a = {s: ground(s, 0.12) for s in 'lr'}
    b = {'l': ground('l', 0.16), 'r': (hip + m(0.15 * sr, 0.1, -0.03), m(sr, 0.6, 0.3))}
    return arms(DOGGY, a), arms(DOGGY, b)


# standing, bent over at the hips, feet apart, knees bent, hands on her knees, looking back at you over her shoulder
BENT = body({
    'root': (0, 180, 0), 'torso': (78, 0, 0), 'chest': (10, 0, 0), 'head': (-50, -30, 0),
    'leg_l': (-128, 0, -10), 'leg_r': (-128, 0, 10), 'shin_l': (90, 0, 0), 'shin_r': (90, 0, 0),
}, (0, -0.23, 0))


def bent_arms():
    # A: both hands on her knees; B: one hand on a knee, the other reaching back to grab your hip
    def knee(s):
        sg = out(BENT, s)
        return (joint(BENT, 'shin_' + s) + m(0.02 * sg, 0.08, 0.04), m(sg, 0.3, 0))
    hip, sr = joint(BENT, 'torso'), out(BENT, 'r')
    a = {s: knee(s) for s in 'lr'}
    b = {'l': knee('l'), 'r': (hip + m(0.16 * sr, 0.08, -0.12), m(sr, 0.8, 0.2))}
    return arms(BENT, a), arms(BENT, b)


# astride you on her knees (you on your back, your head toward her front), sat upright on your hips
COWGIRL = body({
    'torso': (6, 0, 0), 'chest': (4, 0, 0), 'head': (16, 0, 0),
    'leg_l': (-38, 0, -24), 'leg_r': (-38, 0, 24), 'shin_l': (128, 0, 0), 'shin_r': (128, 0, 0),
}, (0, -0.59, 0))


def cowgirl_arms():
    # A: hands down on your chest, ahead of her; B: hands up behind her head, in her hair
    a = {s: (shoulder(COWGIRL, s) + m(0.03 * sg, -0.34, -0.2), m(sg, 0, 0.6)) for s, sg in SIDES}
    head = joint(COWGIRL, 'head')
    b = {s: (head + m(0.08 * sg, 0.12, 0.06), m(sg, 0.3, 0.2)) for s, sg in SIDES}
    return arms(COWGIRL, a), arms(COWGIRL, b)


# ---------------------------------------------------------------- animations
def loop(a, b, length, sway=None):
    """Every bone swung between pose a (at 0 and the end) and pose b (half way), eased; `sway` adds {bone: (dx, dy, dz)}
    at the quarter points, opposite ways (head rolling, hips circling)."""
    bones = {}
    for bone in BONES:
        ra, rb = np.array(a[bone][0], float), np.array(b[bone][0], float)
        sw = np.array((sway or {}).get(bone, (0, 0, 0)), float)
        keys = [(0, ra), (length * 0.25, (ra + rb) / 2 + sw), (length * 0.5, rb), (length * 0.75, (ra + rb) / 2 - sw), (length, ra)]
        rot = {str(round(t, 3)): {'vector': [round(float(v), 2) for v in r], 'easing': SINE} for t, r in keys}
        if not np.any(ra != rb) and not np.any(sw):
            rot = {'0.0': {'vector': [round(float(v), 2) for v in ra]}}
        bones[bone] = {'rotation': rot, 'position': {'0.0': {'vector': [float(v) for v in a[bone][1]]}}}
    return {'loop': True, 'animation_length': length, 'bones': bones}


def idle():
    bones = {b: {'rotation': {'0.0': {'vector': [0, 0, 0]}}, 'position': {'0.0': {'vector': [0, 0, 0]}}} for b in BONES}
    life = {'chest': [(0, (0, 0, 0)), (2, (-1.4, 0, 0)), (4, (0, 0, 0))],
            'head': [(0, (0, 0, 3)), (2, (2, 4, 1)), (4, (0, 0, 3))],
            'torso': [(0, (0, 0, 0)), (2, (0, 0, 1.5)), (4, (0, 0, 0))],
            'braid': [(0, (0, 0, 0)), (2, (2, 0, 1)), (4, (0, 0, 0))]}
    for b, keys in life.items():
        bones[b]['rotation'] = {str(float(t)): {'vector': list(v), 'easing': SINE} for t, v in keys}
    return {'loop': True, 'animation_length': 4.0, 'bones': bones}


def main():
    anims = {
        'idle': idle(),
        'missionary': loop(*missionary_arms(), 6.0, {'head': (0, 10, 0), 'torso': (0, 0, 3)}),
        'doggy': loop(*doggy_arms(), 5.0, {'head': (6, 12, 0), 'torso': (0, 6, 0)}),
        'bent': loop(*bent_arms(), 5.0, {'head': (0, 12, 0), 'torso': (0, 5, 0)}),
        'cowgirl': loop(*cowgirl_arms(), 6.0, {'head': (-6, 0, 8), 'torso': (0, 0, 5), 'chest': (0, 0, -3)}),
    }
    out = {'format_version': '1.8.0', 'animations': {'animation.makima.' + k: v for k, v in anims.items()}}
    with open(OUT, 'w') as f:
        json.dump(out, f, separators=(',', ':'))
    print(OUT)


if __name__ == '__main__':
    main()
