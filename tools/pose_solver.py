"""Solves arm poses on the mesh characters' rigs, so held props reach the right spot.

The meshes (mh_human.py) have elbows, and their props are cubes on the forearms, so "taco to his
mouth" or "phone in front of his eyes" is a small IK problem: pick arm rotation (X, Y, Z) and elbow
bend (X) so a point on the prop lands on a target. Rotations follow GeckoLib's conventions, the same
ones the animations use (rot X < 0 swings a limb forward; arm_r Z > 0 / arm_l Z < 0 raise it out).

Targets come from the locators mh_human.py writes into each geo (mouth, eyes, forehead on the head;
chest and belly on the torso), so regenerating a mesh moves the solved poses with it.
"""
import json
import math
import os

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GEO = os.path.join(ROOT, 'src/main/resources/assets/papon/geo/entity/%s_mesh.geo.json')


def rot(X, Y, Z):
    a, b, c = (math.radians(v) for v in (-X, Y, -Z))
    rx = np.array([[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]])
    ry = np.array([[math.cos(b), 0, math.sin(b)], [0, 1, 0], [-math.sin(b), 0, math.cos(b)]])
    rz = np.array([[math.cos(c), -math.sin(c), 0], [math.sin(c), math.cos(c), 0], [0, 0, 1]])
    return rz @ ry @ rx


class Rig:
    def __init__(self, name):
        geo = json.load(open(GEO % name))['minecraft:geometry'][0]
        self.bones = {b['name']: b for b in geo['bones']}
        self.locators = {k: np.array(v, float) for b in geo['bones'] for k, v in b.get('locators', {}).items()}

    def pivot(self, bone):
        return np.array(self.bones[bone]['pivot'], float)

    def prop_point(self, prop, cube=0):
        """Centre of one of a prop's cubes, at rest."""
        c = self.bones[prop]['cubes'][cube]
        return np.array(c['origin'], float) + np.array(c['size'], float) / 2

    def chain_apply(self, p, chain, pose):
        """Apply rotations of `chain` (deepest bone first) to point p."""
        for bone in chain:
            r = pose.get(bone)
            if r is None:
                continue
            piv = self.pivot(bone)
            p = piv + rot(*r) @ (p - piv)
        return p


def head_point(rig, name, head=(0, 0, 0), torso=(0, 0, 0)):
    return rig.chain_apply(rig.locators[name], ['head', 'torso'], {'head': head, 'torso': torso})


def solve_reach(name, side, prop, target, cube=0, torso=(0, 0, 0), offset=(0, 0, 0), point=None, face=None,
                elbow=None, free_prop=False, seed=0, natural=0.0006,
                bounds=((-175, 10), (-70, 90), (-60, 120), (-140, 0))):
    """Arm (X, Y, Z), forearm X (and, with free_prop, the prop's own X) so a point on `prop` reaches
    `target` + `offset` (rig pixels, front is -Z).

    side: 'r' or 'l' (arm_l mirrors: the same motion has -Y and -Z). point: 'pivot' for the hand
    itself, else the centre of the prop's cube `cube`. face: (prop-local axis, world direction,
    weight) to orient the prop, e.g. a jug's cap toward his mouth or a phone screen toward his eyes.
    elbow: (target, weight) to place the elbow too. Returns dict(arm, forearm, prop, err)."""
    rig = Rig(name)
    arm, fore = 'arm_' + side, 'forearm_' + side
    sign = 1 if side == 'r' else -1
    p0 = rig.pivot(prop) if point == 'pivot' else rig.prop_point(prop, cube)
    tgt = np.asarray(target, float) + np.asarray(offset, float)
    shoulder = rig.pivot(arm)
    bounds = list(bounds) + ([(-150, 150)] if free_prop else [])

    def place(x):
        X, Y, Z, F = x[:4]
        P = x[4] if free_prop else 0
        pose = {prop: (P, 0, 0), fore: (F, 0, 0), arm: (X, sign * Y, sign * Z), 'torso': torso}
        p = rig.chain_apply(p0, [prop, fore, arm, 'torso'], pose)
        e = rig.chain_apply(rig.pivot(fore), [arm, 'torso'], pose)
        return p, e, pose

    def cost(x):
        p, e, pose = place(x)
        c = np.sum((p - tgt) ** 2)
        out = (e[0] - shoulder[0]) * (-1 if side == 'r' else 1)
        c += 0.6 * max(0.0, 0.5 - out) ** 2          # elbow out to his side, never through his body
        # a natural reach: swing forward and bend the elbow rather than hike the shoulder up and out,
        # and keep the elbow under the shoulder (unless the pose asks for the elbow somewhere)
        X, Y, Z = x[0], x[1], x[2]
        c += natural * (Y * Y + max(0.0, Z - 15) ** 2)
        if elbow is None:
            c += 0.3 * max(0.0, e[1] - (shoulder[1] - 1.0)) ** 2
        if face is not None:
            axis, direction, w = face
            M = np.eye(3)
            for b in (prop, fore, arm, 'torso'):
                M = rot(*pose[b]) @ M
            v = M @ np.asarray(axis, float)
            d = np.asarray(direction, float)
            c += w * (1 - v @ d / (np.linalg.norm(v) * np.linalg.norm(d)))
        if elbow is not None:
            et, w = elbow
            c += w * np.sum((e - np.asarray(et, float)) ** 2)
        return c

    rng = np.random.default_rng(seed)
    lo, hi = np.array([b[0] for b in bounds], float), np.array([b[1] for b in bounds], float)
    n = len(bounds)
    best_c, best = None, None
    for x in rng.uniform(lo, hi, (8000, n)):
        c = cost(x)
        if best_c is None or c < best_c:
            best_c, best = c, x
    step = (hi - lo) / 8
    for _ in range(4000):
        y = np.clip(best + rng.normal(0, 1, n) * step, lo, hi)
        c = cost(y)
        if c < best_c:
            best_c, best = c, y
        else:
            step *= 0.998
    p, _, _ = place(best)
    X, Y, Z, F = (round(float(v)) for v in best[:4])
    return dict(arm=[X, sign * Y, sign * Z], forearm=[F, 0, 0], prop=[round(float(best[4])) if free_prop else 0, 0, 0],
                err=float(np.linalg.norm(p - tgt)))
