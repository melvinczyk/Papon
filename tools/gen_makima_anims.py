#!/usr/bin/env python3
"""Makima's animations (entity/MakimaEntity, rig from gen_makima_mesh.py): animations/entity/makima.animation.json.

  python tools/gen_makima_anims.py
  python tools/makima_view.py out.png missionary doggy views=side,34 t=0     # posed in software, the rider standing in

Her bind pose is the model's: upper arms out level, forearms crossed in front of her chest. Each position (2026-10-07) is
a held body pose (MakimaEntity.Position, the same names) fitted to its rider (RIDER: where their hips go and how they lean,
the same numbers as MakimaEntity.Fit): her root is moved so the point of her hips the rider meets (crotch, seat or lap
locator) lands on theirs, so her size and theirs line up in every position. Her legs and arms are solved by two-bone IK on
her real limb lengths onto targets: knees and hands planted on the ground, legs wrapped round you, hands on your chest, round
your neck, in her hair. Her braid and tie hang toward the ground (never into her body or the floor), her feet set flat or
pointed. Every loop swings between two such arm poses and sways her head and hips, so she is never still; the thrust itself
is code (client/MakimaRenderer, in step with the rider's stroke), so the motion never drifts out of step with whoever is with
her. Every animation keys every bone (GeckoLib leaves unkeyed bones as they were).

Arms (2026-10-07, the user: "less plastic and more natural"): the collarbone takes part of every arm's swing (SHRUG: more
raising or reaching than lowering), and the fingers (three bones each, tools/makima_hands.py) hold a grip per pose (GRIPS:
relaxed, a soft hold, gripping, flat on the ground, her own hand sign), the splay closed in by it, never the stiff spread
of the model's pose; the renderer skins with dual quaternions, so a bent elbow keeps its volume.

Rotations in degrees, GeckoLib's convention: rot X < 0 swings a hanging limb forward, so it tips an upright one (root,
torso, chest, head) back. Targets in blocks of her frame: F(at, up, side): `at` along her facing as she stood (toward
whoever came at her), `side` to her left.
"""
import json
import math
import os

import numpy as np

import anim_preview as AP

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')
OUT = os.path.join(ASSETS, 'animations/entity/makima.animation.json')
SINE = 'easeInOutSine'
GEO = json.load(open(os.path.join(ASSETS, 'geo/entity/makima_mesh.geo.json')))['minecraft:geometry'][0]
PIV = {b['name']: np.array(b['pivot'], float) for b in GEO['bones']}
PARENT = {b['name']: b.get('parent') for b in GEO['bones']}
LOC = {k: np.array(v, float) for b in GEO['bones'] for k, v in b.get('locators', {}).items()}
LOC_BONE = {k: b['name'] for b in GEO['bones'] for k in b.get('locators', {})}
BONES = [b['name'] for b in GEO['bones']]
HANDS = json.load(open(os.path.join(ASSETS, 'meshes/makima.json')))['hands']
FINGERS = ['thumb', 'index', 'middle', 'ring', 'pinky']
SIDES = (('l', 1), ('r', -1))
DOWN = np.array([0, -1.0, 0])


def F(at, up, side=0.0):
    """Blocks of her frame -> rig pixels (front -Z, her left +X)."""
    return np.array([side * 16, up * 16, -at * 16], float)


# ---------------------------------------------------------------- the riders (MakimaEntity.Fit: keep in step)
# hip: (at, up) of the rider's hip joint (Steve's legs' pivot, 0.75 above the feet standing); tilt: degrees forward about the
# hips (negative: leaning back, -90 on their back); legs: 'stand' straight down, 'kneel' angled back to the knees on the
# ground, 'lie' along the body. The rider faces back along her facing (toward -at) in every position.
RIDER = {
    'missionary': dict(hip=(0.16, 0.25), tilt=55, legs='kneel'),
    'doggy': dict(hip=(0.2, 0.58), tilt=-6, legs='kneel'),
    'bent': dict(hip=(0.2, 0.75), tilt=-4, legs='stand'),
    'cowgirl': dict(hip=(0.0, 0.125), tilt=-90, legs='lie'),
    'reverse': dict(hip=(0.0, 0.125), tilt=-90, legs='lie'),
    'carry': dict(hip=(0.2, 0.75), tilt=-8, legs='stand'),
}
STEVE_HALF = 0.125      # half the depth of Steve's body


def rider_front(name):
    """The front of the rider's hips (blocks: at, up): where she meets them."""
    r = RIDER[name]
    t = math.radians(r['tilt'])
    # the rider faces -at; tipped forward by t their front turns down
    return np.array([r['hip'][0] - STEVE_HALF * math.cos(t), r['hip'][1] - STEVE_HALF * math.sin(t)])


def rider_preview(name):
    """For tools/makima_view.py: the rider's figure (feet, yaw, tip, legs angle) from RIDER."""
    r = RIDER[name]
    return dict(hip=r['hip'], tilt=r['tilt'], legs=r['legs'])


RIDER_PREVIEW = {k: rider_preview(k) for k in RIDER}


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


# ---------------------------------------------------------------- poses
def body(rot):
    """A pose: {bone: [rotation, position]} (positions in rig pixels as the animation keys them)."""
    return {b: [tuple(rot.get(b, (0, 0, 0))), (0.0, 0.0, 0.0)] for b in BONES}


def copy(pose):
    return {b: list(v) for b, v in pose.items()}


def world(pose):
    """Bone world matrices (rig pixels) for a pose, as anim_preview poses the rig."""
    anim = {'bones': {b: {'rotation': {'0': {'vector': list(r)}}, 'position': {'0': {'vector': list(p)}}}
                      for b, (r, p) in pose.items()}}
    M, _ = AP.pose(GEO, anim, 0)
    return M


def apply(M, bone, p):
    return (M[bone] @ np.append(p, 1))[:3]


def at(pose, name):
    """Where a locator (or a bone's pivot) is in her frame, rig pixels."""
    M = world(pose)
    if name in LOC:
        return apply(M, LOC_BONE[name], LOC[name])
    return apply(M, name, PIV[name])


def place(pose, loc, target):
    """Moves her root so that locator `loc` lands on target (rig pixels)."""
    pose = copy(pose)
    pose['root'][1] = (0.0, 0.0, 0.0)
    d = np.asarray(target, float) - at(pose, loc)
    pose['root'][1] = (round(-d[0], 3), round(d[1], 3), round(d[2], 3))      # GeckoLib flips position X
    return pose


def chain(pose, upper, lower, end, T, pole):
    """Two-bone IK: the `end` locator (on `lower`) onto T, the middle joint bent toward `pole` (directions in her frame, rig
    pixels); returns the pose with upper's and lower's rotations solved in their parents' frames."""
    pose = copy(pose)
    pose[upper][0] = (0, 0, 0)
    pose[lower][0] = (0, 0, 0)
    M = world(pose)
    T, pole = np.asarray(T, float), np.asarray(pole, float)
    par = PARENT[upper]
    S = apply(M, par, PIV[upper])
    Rc = M[par][:3, :3]
    u0, f0 = PIV[lower] - PIV[upper], LOC[end] - PIV[lower]
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
    pose[upper][0] = euler(Ra)
    pose[lower][0] = euler(Rf)
    return pose


def aim(pose, bone, rest_vec, want):
    """Turns `bone` (in its parent's frame) so its rest direction rest_vec points along `want` (her frame)."""
    pose = copy(pose)
    pose[bone][0] = (0, 0, 0)
    M = world(pose)
    Rp = M[PARENT[bone]][:3, :3]
    pose[bone][0] = euler(minrot(rest_vec, Rp.T @ np.asarray(want, float)))
    return pose


SHRUG = (0.12, 0.3, 20)      # of the upper arm's swing the collarbone takes: lowering, raising or reaching; at most (deg)


def arms(pose, targets, shrug=SHRUG):
    """Each side's wrist onto targets[side] = (wrist target, elbow pole), rig pixels of her frame; the collarbone lifted or
    drawn forward with the arm (shrug: SHRUG unless given), then the arm solved again from where the shoulder went."""
    SHRUG = shrug
    for s, (T, pole) in targets.items():
        solved = chain(pose, 'arm_' + s, 'forearm_' + s, 'wrist_' + s, T, pole)
        rest = copy(pose)
        rest['arm_' + s][0] = (0, 0, 0)
        u0 = at(rest, 'forearm_' + s) - at(rest, 'arm_' + s)
        u1 = at(solved, 'forearm_' + s) - at(solved, 'arm_' + s)
        u0, u1 = u0 / np.linalg.norm(u0), u1 / np.linalg.norm(u1)
        k = SHRUG[0] if u1[1] < u0[1] and np.linalg.norm((u1 - u0)[[0, 2]]) < 0.6 else SHRUG[1]
        ang = math.degrees(math.acos(np.clip(u0 @ u1, -1, 1))) * k
        if ang > 0.5:
            axis = np.cross(u0, u1)
            axis /= np.linalg.norm(axis)
            t = math.radians(min(ang, SHRUG[2]))
            K = np.array([[0, -axis[2], axis[1]], [axis[2], 0, -axis[0]], [-axis[1], axis[0], 0]])
            R = np.eye(3) + math.sin(t) * K + (1 - math.cos(t)) * K @ K
            clav = at(pose, 'arm_' + s) - at(pose, 'shoulder_' + s)
            pose = aim(pose, 'shoulder_' + s, PIV['arm_' + s] - PIV['shoulder_' + s], R @ clav)
        pose = chain(pose, 'arm_' + s, 'forearm_' + s, 'wrist_' + s, T, pole)
    return pose


def axis_rot(axis, deg):
    a = np.asarray(axis, float)
    a = a / np.linalg.norm(a)
    t = math.radians(deg)
    K = np.array([[0, -a[2], a[1]], [a[2], 0, -a[0]], [-a[1], a[0], 0]])
    return np.eye(3) + math.sin(t) * K + (1 - math.cos(t)) * K @ K


# degrees at each finger's three joints (the thumb's: base, middle, tip), and how much of the model's splay is closed
GRIPS = {
    'relaxed': ({'thumb': (6, 14, 12), 'index': (16, 24, 12), 'middle': (20, 30, 16), 'ring': (24, 34, 18),
                 'pinky': (28, 38, 20)}, 0.65),
    'soft': ({'thumb': (10, 18, 14), 'index': (24, 32, 18), 'middle': (28, 36, 20), 'ring': (32, 40, 22),
              'pinky': (36, 44, 24)}, 0.8),
    'grip': ({'thumb': (24, 30, 26), 'index': (48, 62, 38), 'middle': (54, 66, 40), 'ring': (58, 68, 42),
              'pinky': (62, 70, 44)}, 0.95),
    'flat': ({'thumb': (2, 4, 4), 'index': (4, 6, 4), 'middle': (4, 6, 4), 'ring': (5, 7, 5), 'pinky': (6, 8, 5)}, 0.35),
    'sign': ({'thumb': (2, 4, 3), 'index': (3, 4, 3), 'middle': (5, 6, 4), 'ring': (7, 9, 5), 'pinky': (9, 12, 6)}, 0.15),
    'point': ({'thumb': (-6, 0, 0), 'index': (0, 0, 0), 'middle': (0, 2, 0), 'ring': (70, 85, 50),
               'pinky': (72, 88, 52)}, 0.9),
}


def hands(pose, grip, side=None):
    """The fingers of one hand (or both) in a grip (GRIPS name, or (angles, splay) itself)."""
    angles, splay = GRIPS[grip] if isinstance(grip, str) else grip
    pose = copy(pose)
    for s in ([side] if side else 'lr'):
        for f in FINGERS:
            ax = HANDS[s][f]
            for k, deg in enumerate(angles[f]):
                R = axis_rot(ax['curl'], deg)
                if k == 0 and ax['angle'] > 0.5:
                    R = axis_rot(ax['spread'], ax['angle'] * splay) @ R
                pose['f_%s%d_%s' % (f, k + 1, s)][0] = euler(R)
    return pose


def orient(pose, bone, R):
    """Turns `bone` so its world rotation (her frame, from the rest pose) is R."""
    pose = copy(pose)
    pose[bone][0] = (0, 0, 0)
    pose[bone][0] = euler(world(pose)[PARENT[bone]][:3, :3].T @ R)
    return pose


def frame(a, b):
    """An orthonormal frame (columns) from a direction a and a second one b (made square to a)."""
    a = a / np.linalg.norm(a)
    b = b - (b @ a) * a
    b /= np.linalg.norm(b)
    return np.stack([a, b, np.cross(a, b)], 1)


def palm(s):
    """Her hand at rest (rig pixels): the fingers' direction and the palm's normal (the way the fingers curl)."""
    d = LOC['finger_' + s] - PIV['hand_' + s]
    d /= np.linalg.norm(d)
    v = axis_rot(HANDS[s]['middle']['curl'], 20) @ d - d
    return d, v - (v @ d) * d


def twist_angle(R, u):
    """How far R turns about the axis u (degrees, swing-twist)."""
    w = math.sqrt(max(0.0, 1 + R[0, 0] + R[1, 1] + R[2, 2])) / 2
    v = np.array([R[2, 1] - R[1, 2], R[0, 2] - R[2, 0], R[1, 0] - R[0, 1]]) / (4 * max(w, 1e-6))
    u = u / np.linalg.norm(u)
    return math.degrees(2 * math.atan2(v @ u, w))


def hand_to(pose, s, fingers, palm_dir, roll=0.55):
    """Her hand turned so its fingers run along `fingers` and its palm faces `palm_dir` (her frame), the forearm rolling
    about itself for `roll` of the hand's twist (a wrist never wrings round on its own)."""
    d0, v0 = palm(s)
    return hand_rot(pose, s, frame(np.asarray(fingers, float), np.asarray(palm_dir, float)) @ frame(d0, v0).T, roll)


def hand_rot(pose, s, Rh, roll=0.55):
    """Her hand's world rotation set to Rh, the forearm rolled about itself for `roll` of the twist."""
    M = world(pose)
    u = LOC['wrist_' + s] - PIV['forearm_' + s]
    rel = M['forearm_' + s][:3, :3].T @ Rh
    tw = max(-100.0, min(100.0, twist_angle(rel, u)))
    pose = copy(pose)
    R = M['arm_' + s][:3, :3].T @ M['forearm_' + s][:3, :3] @ axis_rot(u, tw * roll)
    pose['forearm_' + s][0] = euler(R)
    return orient(pose, 'hand_' + s, Rh)


def legs(pose, targets, toes=None):
    """Each side's ankle onto targets[side] = (ankle target, knee pole); the foot pointed along toes[side] (her frame)."""
    for s, (T, pole) in targets.items():
        pose = chain(pose, 'leg_' + s, 'shin_' + s, 'ankle_' + s, T, pole)
        want = (toes or {}).get(s)
        if want is not None:
            pose = aim(pose, 'foot_' + s, LOC['toe_' + s] - PIV['foot_' + s], want)
    return pose


def normal_of(pose, bone, rest_normal):
    M = world(pose)
    n = M[bone][:3, :3] @ np.asarray(rest_normal, float)
    return n / np.linalg.norm(n)


def hang(pose, links, out_normal_bone, out_normal, floor=0.6, stiff=(0.4, 0.15, 0.05), fall=None, rest_out=False):
    """A chain of bones (braid, tie) hung toward the ground: each link's direction its rest direction blended toward `fall`
    (down, unless given: face down the braid falls off to one side of her neck) with `stiff` of the rest kept per link, held
    off her body (never into the side `out_normal` faces, on `out_normal_bone`; None: no check; rest_out: never further in
    than it lies at rest, the tie over her bust) and off the floor (the floor at `floor` pixels: along it instead)."""
    g = DOWN if fall is None else np.asarray(fall, float) / np.linalg.norm(fall)
    for k, (bone, tip) in enumerate(links):
        M = world(pose)
        rest = (LOC[tip] if tip in LOC else PIV[tip]) - PIV[bone]
        cur = M[PARENT[bone]][:3, :3] @ rest
        cur /= np.linalg.norm(cur)
        st = stiff[min(k, len(stiff) - 1)]
        want = st * cur + (1 - st) * g
        if out_normal is not None:
            n = normal_of(pose, out_normal_bone, out_normal)
            least = max(0.2, cur @ n) if rest_out else 0.2
            if want @ n < least:
                want = want - (want @ n - least) * n
        want /= np.linalg.norm(want)
        start = apply(M, PARENT[bone], PIV[bone])
        end = start + want * np.linalg.norm(rest)
        if end[1] < floor:
            want = want.copy()
            want[1] = max(want[1], (floor - start[1]) / np.linalg.norm(rest))
            want /= np.linalg.norm(want)
        pose = aim(pose, bone, rest, want)
    return pose


KNEE_UP, ANKLE_UP = 1.0, 1.0     # pixels: the knee joint above the ground kneeling, the ankle with the shin laid flat


def kneel(p, toward, spread=0.0, ahead=0.0):
    """Both knees on the ground (KNEE_UP) under her hip joints, `ahead` blocks toward `at` * toward and `spread` out to the
    sides, at exactly the thigh's reach; the shins laid back along the ground (the other way), feet pointed."""
    for s, sg in SIDES:
        a = np.linalg.norm(PIV['shin_' + s] - PIV['leg_' + s])
        b = np.linalg.norm(LOC['ankle_' + s] - PIV['shin_' + s])
        H = at(p, 'leg_' + s)
        side = at(p, 'leg_' + s) - at(p, 'torso')
        out = np.array([side[0], 0, side[2]])
        out = out / (np.linalg.norm(out) or 1) * spread * 16
        drop = H[1] - KNEE_UP
        reach = math.sqrt(max(a * a - drop * drop, 0))
        horiz = out + F(toward * ahead, 0, 0)
        hn = np.linalg.norm(horiz)
        horiz = horiz / hn * min(hn, reach) if hn > 1e-6 else F(toward, 0, 0) / 16 * 0
        rest = math.sqrt(max(reach * reach - (horiz @ horiz), 0))
        K = np.array([H[0], KNEE_UP, H[2]]) + horiz + F(toward, 0, 0) / 16 * rest
        A = K + F(-toward, 0, 0) / 16 * math.sqrt(max(b * b - (KNEE_UP - ANKLE_UP) ** 2, 0))
        A[1] = ANKLE_UP
        p = chain(p, 'leg_' + s, 'shin_' + s, 'ankle_' + s, A + (K - A) * 1e-4, K - (H + A) / 2)
        p = aim(p, 'foot_' + s, LOC['toe_' + s] - PIV['foot_' + s], F(-toward, -0.15, 0))
    return p


def dress(pose, braid_fall=None):
    """The braid and the tie hung for whatever pose she is in (braid_fall: face down, which way it falls off her neck)."""
    pose = hang(pose, [('braid', 'braid2'), ('braid2', 'braid3'), ('braid3', 'braid_tip')], 'chest',
                None if braid_fall is not None else (0, 0, 1), fall=braid_fall)
    pose = hang(pose, [('tie', 'tie_tip')], 'chest', (0, 0, -1), stiff=(0.15,), rest_out=True)
    return pose


# ---------------------------------------------------------------- the positions
FALL = F(0, -1, 0.6)        # face down, the braid falls off over her left shoulder


def plant(p, side, toward):
    """Her hand laid flat: the fingers along the ground toward `at` * toward."""
    return aim(p, 'hand_' + side, LOC['finger_' + side] - PIV['hand_' + side], F(toward, -0.3, 0))


def missionary():
    """On her back, the rider kneeling between her knees leaning over her on straight arms: her head away from them (her
    root tipped back), hips lifted to theirs, legs up and wrapped round their hips, ankles crossed behind them. A: hands on
    their chest; B: flung up over her head onto the ground."""
    p = body({'root': (-90, 0, 0), 'torso': (-8, 0, 0), 'spine': (4, 0, 0), 'chest': (6, 0, 0), 'neck': (-10, 0, 0),
              'head': (-14, 14, 0)})
    p = place(p, 'crotch', F(*rider_front('missionary')))
    p['root'][1] = (p['root'][1][0], p['root'][1][1], p['root'][1][2])
    r = RIDER['missionary']
    back = r['hip'][0] + 0.2
    p = legs(p, {s: (F(back, 0.5, 0.07 * sg), F(-0.2, 1, sg)) for s, sg in SIDES},
             toes={s: F(0.2, 0.6, -0.3 * sg) for s, sg in SIDES})
    chest = at(p, 'chest')

    def sh(s):
        return at(p, 'arm_' + s)
    a = arms(p, {s: (sh(s) + F(0.22, 0.24, -0.05 * sg), F(-0.3, 0.1, sg)) for s, sg in SIDES})
    b = arms(p, {s: (sh(s) + F(-0.25, -0.02, 0.12 * sg), F(0, 1, sg * 0.4)) for s, sg in SIDES})
    return dress(hands(a, 'soft')), dress(hands(b, 'relaxed')), chest


def side(p, s):
    """Which way (along F's `side`) her `s` side lies in this pose: +1 her left as she stood, -1 when her root is turned
    round (the old poses added her own left to a turned body: her legs crossed bent over, her elbows bent inward on all
    fours)."""
    return 1.0 if at(p, 'leg_' + s)[0] > at(p, 'torso')[0] else -1.0


GLUTE_OUT = {'stand': (0, 0, 0), 'doggy': (0.05, -0.1, 0.4), 'bent': (0.05, -0.1, 0.4)}     # pixels the glutes are pushed out


def glutes(p, push):
    """Her glutes pushed out of her hips (rig pixels, the pelvis's frame: x out to each side, y up her back, z behind her):
    bent over, they fill out round instead of being drawn flat over the bent hip."""
    p = copy(p)
    x, y, z = push
    p['glute_l'][1] = (-x, y, z)        # GeckoLib flips position X: out to her left
    p['glute_r'][1] = (x, y, z)
    return p


def doggy():
    """On her knees and hands, her back arched so her seat is up to the rider kneeling behind her, looking back over her
    shoulder; her root turned away from them. A: up on straight arms, hands flat under her shoulders; B: down on her
    forearms, chest low, seat high."""
    def body_at(torso, spine, chest, neck):
        p = body({'root': (0, 180, 0), 'torso': (torso, 0, 0), 'spine': (spine, 0, 0), 'chest': (chest, 0, 0),
                  'neck': (neck, -18, 0), 'head': (-26, -24, -6)})
        p = place(p, 'seat', F(*(rider_front('doggy') + [-0.02, 0.0])))
        return kneel(p, toward=-1, spread=0.09)

    def ground(p, s, ahead, up=0.05):
        q = at(p, 'arm_' + s)
        return np.array([q[0], up * 16, q[2]]) + F(-ahead, 0, 0.02 * side(p, s))
    a = body_at(98, -10, -4, -46)
    a = arms(a, {s: (ground(a, s, 0.03), F(0.6, 0, 0.5 * side(a, s))) for s in 'lr'})
    b = body_at(100, 20, 16, -56)
    b = arms(b, {s: (ground(b, s, ARM[s] / 16 * 0.5, 0.06), F(0.3, -1, 0.3 * side(b, s))) for s in 'lr'})
    for s in 'lr':
        a, b = plant(a, s, -1), plant(b, s, -1)
    a, b = glutes(a, GLUTE_OUT['doggy']), glutes(b, GLUTE_OUT['doggy'])
    return dress(hands(a, 'flat'), FALL), dress(hands(b, 'flat'), FALL), at(a, 'chest')


def bent():
    """Standing bent over at the hips, her back arched so her seat is pushed up and out to the rider, feet planted wide with
    the toes turned out and the knees bent over them (so her hips come down to theirs), looking back at them over her
    shoulder; root turned away. A: hands on her knees; B: one on a knee, the other on her own seat, pulling it aside."""
    p = body({'root': (0, 180, 0), 'torso': (70, 0, 0), 'spine': (-4, 0, 0), 'chest': (8, 0, 0), 'neck': (-34, -22, 0),
              'head': (-24, -24, -4)})
    p = place(p, 'seat', F(*(rider_front('bent') + [-0.02, 0.0])))
    sd = {s: side(p, s) for s in 'lr'}
    p = legs(p, {s: (at(p, 'leg_' + s) * [1, 0, 1] + F(0.1, 0.1, 0.1 * sd[s]), F(-1, 0.1, 0.4 * sd[s])) for s in 'lr'},
             toes={s: F(-1, -0.05, 0.4 * sd[s]) for s in 'lr'})

    def knee(q, s):
        """Her hand on the inside of her thigh just above the knee, fingers round it, the elbow out."""
        T = at(q, 'shin_' + s) + F(0.02, 0.09, -0.05 * sd[s])
        return T, F(0.3, 0.2, sd[s])

    def on_knee(q, s):
        return hand_to(q, s, F(-0.35, -1, 0.25 * sd[s]), F(0.5, -0.2, sd[s] * 0.6))
    a = arms(p, {s: knee(p, s) for s in 'lr'})
    for s in 'lr':
        a = on_knee(a, s)
    # B: her right hand back on her right buttock
    g = at(p, 'glute_r') + F(0.1, 0.02, 0.02 * sd['r'])
    b = arms(p, {'l': knee(p, 'l'), 'r': (g + F(-0.02, 0.06, 0), F(0.4, 1, sd['r'] * 0.8))})
    b = on_knee(b, 'l')
    b = hand_to(b, 'r', F(0.25, -1, -0.5 * sd['r']), F(-1, 0.1, 0))
    a, b = glutes(a, GLUTE_OUT['bent']), glutes(b, GLUTE_OUT['bent'])
    return dress(hands(a, 'grip'), FALL), dress(hands(hands(b, 'grip', 'l'), 'soft', 'r'), FALL), at(p, 'chest')


def cowgirl(reverse=False):
    """Astride the rider on their back, on her knees with her shins along the ground, sat on their hips; facing their head
    (reverse: their feet, her root turned round). A: hands down on their chest (reverse: on their knees); B: up in her
    hair."""
    turn = 180 if reverse else 0
    p = body({'root': (0, turn, 0), 'torso': (10 if reverse else 4, 0, 0), 'spine': (8 if reverse else 2, 0, 0),
              'chest': (4, 0, 0), 'neck': (6, 0, 0), 'head': (8 if reverse else 10, 0, 0)})
    p = place(p, 'crotch', F(*(rider_front('cowgirl') + [0.0, 0.03])))
    dirf = -1 if reverse else 1      # her forward along `at`

    p = kneel(p, toward=dirf, spread=0.27, ahead=0.12)
    if reverse:
        a = arms(p, {s: (at(p, 'arm_' + s) + F(-0.22, -0.4, 0.04 * sg * -1), F(0, 0.2, -sg)) for s, sg in SIDES})
    else:
        a = arms(p, {s: (at(p, 'arm_' + s) + F(0.22, -0.4, -0.04 * sg), F(0, 0.2, sg)) for s, sg in SIDES})
    head = at(p, 'head')
    turnx = -1 if reverse else 1
    b = arms(p, {s: (head + F(-0.06 * dirf, 0.1, 0.08 * sg * turnx), F(0.2 * dirf, 0.6, sg * turnx)) for s, sg in SIDES})
    return dress(hands(a, 'grip' if reverse else 'soft')), dress(hands(b, 'grip')), at(p, 'chest')


def carry():
    """Held up against the standing rider, face to face: her hips on theirs, legs wrapped round their waist with the ankles
    crossed behind their back, arms round their neck (A) or one hand in their hair (B), leaning back in their arms."""
    p = body({'torso': (-8, 0, 0), 'spine': (-4, 0, 0), 'chest': (4, 0, 0), 'neck': (8, 0, 0), 'head': (2, 8, 4)})
    p = place(p, 'lap', F(*(rider_front('carry') + [-0.03, 0.02])))
    hip = RIDER['carry']['hip']
    p = legs(p, {s: (F(hip[0] + 0.24, hip[1] + 0.06, 0.06 * sg), F(-0.2, 0.3, sg)) for s, sg in SIDES},
             toes={s: F(0.3, -0.5, -0.6 * sg) for s, sg in SIDES})
    neck = F(hip[0] + 0.04, hip[1] + 0.7, 0)
    a = arms(p, {s: (neck + F(0.06, 0.02, 0.07 * sg), F(-0.2, -0.6, sg)) for s, sg in SIDES})
    b = arms(p, {'l': (neck + F(0.06, 0.02, 0.07), F(-0.2, -0.6, 1)), 'r': (neck + F(0.0, 0.2, -0.04), F(-0.4, 0.4, -1))})
    return dress(hands(a, 'grip')), dress(hands(b, 'grip')), at(p, 'chest')


# Her stance (2026-10-07, the overhaul: "graceful posture", "calm, confident, intimidating presence"): her weight on her
# right leg, the hips rolled up on that side and the shoulders a touch against them, the left knee eased forward and in;
# her hands loosely clasped in front of her (the left over the right), her head level (client/MakimaFace tilts it: her
# look; a tilt here doubled it). STANCE: the
# trunk's turns; HOLD: where the wrists go, blocks (ahead of the hips, up from them, aside).
STANCE = {'torso': (0, -5, -3.5), 'spine': (1, 2, 2), 'chest': (-1.5, 2, 1.5), 'neck': (1, 0, 0), 'head': (0, -3, 0)}
HOLD = {'l': (0.175, -0.1, 0.03), 'r': (0.155, -0.115, -0.045)}


def stand():
    p = body(STANCE)
    hip = {s: at(body({}), 'leg_' + s) for s in 'lr'}
    p = legs(p, {'r': (hip['r'] * [1, 0, 1] + F(0.0, 0.1, 0.012), F(1, 0, 0.1)),
                 'l': (hip['l'] * [1, 0, 1] + F(0.07, 0.1, -0.035), F(1, 0, -0.35))},
             {'r': F(1, -0.15, -0.25), 'l': F(1, -0.25, 0.1)})
    base = at(p, 'torso')
    p = arms(p, {s: (base + F(*HOLD[s]), F(-0.4, -0.2, 1.0 * sg)) for s, sg in SIDES}, (0.0, 0.08, 6))     # shoulders down, back
    # the hands across each other, palms in toward her, fingers falling toward the other side
    p = hand_to(p, 'r', F(0.1, -0.7, 0.75), F(-1, 0, 0.1))
    p = hand_to(p, 'l', F(0.05, -0.75, -0.7), F(-1, 0, -0.1))
    return dress(hands(p, 'relaxed'))


ARM = {s: (np.linalg.norm(PIV['forearm_' + s] - PIV['arm_' + s]) + np.linalg.norm(LOC['wrist_' + s] - PIV['forearm_' + s]))
       for s in 'lr'}      # shoulder to wrist, rig pixels


def hanging(p, s, sg, ahead=0.0, out=0.02, bend=0.93):
    """A wrist target for an arm hanging at her side (`bend` of its full reach below the shoulder, `ahead` and `out` blocks),
    the elbow pointing back."""
    return at(p, 'arm_' + s) + F(ahead, -ARM[s] * bend / 16, out * sg), F(-1, 0, 0.25 * sg)


# ---------------------------------------------------------------- animations
def keyset(pose_at, length, times):
    """Every bone keyed at `times` (seconds) from pose_at(t) (a pose), eased."""
    bones = {}
    poses = [(t, pose_at(t)) for t in times]
    for bone in BONES:
        rot = {str(round(t, 3)): {'vector': [round(float(v), 2) for v in p[bone][0]], 'easing': SINE} for t, p in poses}
        pos = {str(round(t, 3)): {'vector': [round(float(v), 3) for v in p[bone][1]], 'easing': SINE} for t, p in poses}
        if len({tuple(k['vector']) for k in rot.values()}) == 1:
            rot = {'0.0': {'vector': rot[str(round(times[0], 3))]['vector']}}
        if len({tuple(k['vector']) for k in pos.values()}) == 1:
            pos = {'0.0': {'vector': pos[str(round(times[0], 3))]['vector']}}
        bones[bone] = {'rotation': rot, 'position': pos}
    return {'loop': True, 'animation_length': length, 'bones': bones}


def blend(a, b, k, sway=None, s=0.0):
    """Pose a toward b by k (rotations), plus `sway` {bone: (dx, dy, dz)} times s."""
    out = copy(a)
    for bone in BONES:
        ra, rb = np.array(a[bone][0], float), np.array(b[bone][0], float)
        r = ra + (rb - ra) * k + np.array((sway or {}).get(bone, (0, 0, 0)), float) * s
        out[bone][0] = tuple(r)
    return out


def loop(a, b, length, sway=None):
    """Swung from pose a (at 0 and the end) to pose b (half way), `sway` added at the quarters (head rolling, hips circling)."""
    def pose_at(t):
        ph = t / length
        k = 0.5 - 0.5 * math.cos(ph * 2 * math.pi)
        return blend(a, b, k, sway, math.sin(ph * 2 * math.pi))
    return keyset(pose_at, length, [length * i / 4 for i in range(5)])


def idle():
    base = stand()
    life = {'chest': (-1.2, 0, 0), 'head': (1.5, 3, -1.5), 'torso': (0, 0, 1.0), 'spine': (0, 0, -0.6), 'braid': (2, 0, 1)}

    def pose_at(t):
        out = copy(base)
        k = math.sin(t / 4 * 2 * math.pi)
        for bone, d in life.items():
            out[bone][0] = tuple(np.array(base[bone][0]) + np.array(d) * k)
        return out
    return keyset(pose_at, 4.0, [0, 1, 2, 3, 4])


def walk():
    """Following you (MakimaEntity follow mode): her arms down at her sides, a stride of STRIDE blocks a step, two steps a
    second loop of 1 s (the renderer scales its speed to how fast she moves), the hips swaying, the head steady on you."""
    stride, lift = 0.36, 0.09
    base = body({'neck': (-4, 0, 0)})
    hipw = {s: at(base, 'leg_' + s) for s in 'lr'}

    def pose_at(t):
        ph = t * 2 * math.pi
        p = body({'torso': (2, 6 * math.sin(ph), 3 * math.sin(ph)), 'spine': (2, -4 * math.sin(ph), -2 * math.sin(ph)),
                  'chest': (0, -3 * math.sin(ph), 0), 'neck': (-4, 0, 0), 'head': (2, 3 * math.sin(ph), 0)})
        p['root'][1] = (0.0, round(-0.35 * math.cos(2 * ph) - 0.35, 3), 0.0)
        tg = {}
        toes = {}
        for s, sg in SIDES:
            q = ph + (0 if s == 'l' else math.pi)
            fwd = stride / 2 * math.cos(q)
            up = max(0.0, math.sin(q)) * lift
            tg[s] = (hipw[s] * [1, 0, 1] + F(fwd, 0.1 + up, 0.0), F(1, 0, 0))
            toes[s] = F(1, -0.15 - up * 2, 0)
        p = legs(p, tg, toes)
        # the arms hang loose, swinging against the legs, the elbows a little bent, the palms toward her thighs
        sw = {s: math.cos(ph + (math.pi if s == 'l' else 0)) for s in 'lr'}
        p = arms(p, {s: hanging(p, s, sg, 0.03 + 0.11 * sw[s], 0.035, 0.94 - 0.03 * max(0.0, sw[s])) for s, sg in SIDES})
        for s, sg in SIDES:
            p = hand_to(p, s, F(0.12 + 0.12 * sw[s], -1, 0.02 * sg), F(0.15, 0, -sg))
        return dress(hands(p, 'relaxed'))
    return keyset(pose_at, 1.0, [i / 8 for i in range(9)])


def main():
    m_a, m_b, _ = missionary()
    d_a, d_b, _ = doggy()
    b_a, b_b, _ = bent()
    c_a, c_b, _ = cowgirl()
    r_a, r_b, _ = cowgirl(True)
    k_a, k_b, _ = carry()
    anims = {
        'idle': idle(),
        'walk': walk(),
        'missionary': loop(m_a, m_b, 6.0, {'head': (0, 10, 0), 'torso': (0, 0, 3)}),
        'doggy': loop(d_a, d_b, 5.0, {'head': (6, 12, 0), 'torso': (0, 6, 0)}),
        'bent': loop(b_a, b_b, 5.0, {'head': (0, 12, 0), 'torso': (0, 5, 0)}),
        'cowgirl': loop(c_a, c_b, 6.0, {'head': (-6, 0, 8), 'torso': (0, 0, 5), 'chest': (0, 0, -3)}),
        'reverse': loop(r_a, r_b, 6.0, {'head': (-4, 10, 0), 'torso': (0, 0, 5), 'chest': (0, 0, -3)}),
        'carry': loop(k_a, k_b, 6.0, {'head': (0, 10, 4), 'torso': (0, 0, 2)}),
    }
    out = {'format_version': '1.8.0', 'animations': {'animation.makima.' + k: v for k, v in anims.items()}}
    with open(OUT, 'w') as f:
        json.dump(out, f, separators=(',', ':'))
    print(OUT)
    for name in RIDER:
        print('fit %-10s rider hip at %.3f up %.3f tilt %d' % (name, *RIDER[name]['hip'], RIDER[name]['tilt']))


if __name__ == '__main__':
    main()
