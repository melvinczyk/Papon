"""Animations for the mesh celebrities (EDP445, Badlands Chugs), written into customer.animation.json
by gen_npc_anims.py.

Their rigs (mh_human.py) have more than the shared customer rig: elbows (forearm_r/_l), a jaw and
eyelids. Every pose where a hand must reach something (taco to mouth, jug to lips, phone in front of
his eyes, hand on belly) is solved on the real mesh by pose_solver.py when this runs, so the poses
follow the models when they are regenerated. The jaw also flaps on its own while they talk and the
eyes blink (CrowdModel), so these animations only key the jaw for eating, drinking and shouting.

Each has his own walk, order, receive, walk_taco and hold_taco (Customer.own lists them), an idle,
and the reaction; reaction lengths are Customer.<WHO>.reactTicks / 20 and the spoken lines' ticks
are in Customer too:
  EDP445          react 8.0 s; "leave" at 1.5 s, "chant" at 5.9 s
  Badlands Chugs  react 7.0 s; "watch" at 0.0 s, "leave" (the burp) at 4.4 s
"""
import numpy as np

import pose_solver as PS
from gen_anims import EPS

REST_L, REST_R = [0, 0, -3], [0, 0, 3]
ZERO = [0, 0, 0]


CRISP = 'easeInOutCubic'


def crisp(anims):
    """Pose-to-pose timing for every rotation and position key that would otherwise move at a
    constant speed (linear) or drift (sine): a cubic ease in and out, so each move starts with
    intent, passes quickly through the middle and settles cleanly on its pose. Catmull-rom keys
    (walk cycles, idle sways, chewing) and the accents with their own easing (back, bounce, quad)
    keep theirs; scale keys (prop pops) stay as they are."""
    for a in anims:
        for chans in a.bones.values():
            for chan in ('rotation', 'position'):
                keys = sorted(chans.get(chan, {}).items(), key=lambda kv: float(kv[0]))
                for i, (_, kf) in enumerate(keys):
                    if kf.get('lerp_mode') == 'catmullrom':
                        continue
                    if kf.get('easing') in (None, 'easeInOutSine', 'linear'):
                        kf['easing'] = CRISP
                    # a big swing launched with easeOutBack starts at ~5x its average speed and
                    # pops: wind it up and settle it instead (anticipation and follow-through)
                    if kf.get('easing') == 'easeOutBack' and chan == 'rotation' and i > 0 and \
                            np.abs(np.subtract(kf['vector'], keys[i - 1][1]['vector'])).max() > 50:
                        kf['easing'] = 'easeInOutBack'
                        kf['easingArgs'] = [1.1]
    return anims


def osc(t0, t1, step, a, b, ease=None):
    keys, t, i = [], t0, 0
    while t <= t1 + 1e-6:
        k = (round(t, 4), a if i % 2 == 0 else b)
        keys.append(k + ((ease,) if ease else ()))
        t += step
        i += 1
    return keys


# ---------------------------------------------------------------- solved poses
def poses_edp():
    R = PS.Rig('edp445')
    eyes = PS.head_point(R, 'eyes', head=(16, 0, 0))
    mouth = PS.head_point(R, 'mouth')
    p = {}
    p['phone'] = PS.solve_reach('edp445', 'l', 'phone', eyes, offset=(0.8, -2.2, -6.5),
                                face=((0, 0, 1), (0, 0.35, 1), 6), free_prop=True)
    p['taco_mouth'] = PS.solve_reach('edp445', 'r', 'taco', mouth, offset=(0, -0.3, -1.4))
    p['taco_show'] = PS.solve_reach('edp445', 'r', 'taco', mouth, offset=(-1.0, -1.5, -7.5))
    p['belly_r'] = PS.solve_reach('edp445', 'r', 'taco', R.locators['belly'], point='pivot', offset=(-2.6, 1.2, 0.6))
    p['forehead_r'] = PS.solve_reach('edp445', 'r', 'taco', PS.head_point(R, 'forehead'), point='pivot',
                                     offset=(0.4, 0.2, -0.9))
    p['taco_up'] = PS.solve_reach('edp445', 'r', 'taco', mouth, offset=(-2.5, 7.5, -4.0))
    return p


def poses_chugs():
    R = PS.Rig('badlands_chugs')
    p = {}
    p['jug_mouth'] = PS.solve_reach('badlands_chugs', 'l', 'jug', PS.head_point(R, 'mouth', head=(-28, 0, 0)), cube=1,
                                    offset=(0, 0.2, -0.5), face=((0, 1, 0), (0, -0.5, 1), 8), free_prop=True)
    p['jug_deep'] = PS.solve_reach('badlands_chugs', 'l', 'jug', PS.head_point(R, 'mouth', head=(-42, 0, 0)), cube=1,
                                   offset=(0, 0.2, -0.4), face=((0, 1, 0), (0, -0.9, 0.5), 8), free_prop=True)
    p['jug_show'] = PS.solve_reach('badlands_chugs', 'l', 'jug', R.locators['chest'], offset=(4.5, 0.0, -7.0),
                                   face=((0, 1, 0), (0, 1, 0), 8), free_prop=True)
    p['taco_mouth'] = PS.solve_reach('badlands_chugs', 'r', 'taco', PS.head_point(R, 'mouth'), offset=(0, -0.3, -1.4))
    p['wipe'] = PS.solve_reach('badlands_chugs', 'r', 'taco', PS.head_point(R, 'mouth'), point='pivot',
                               offset=(-1.8, -0.4, -1.4))
    for side, sx in (('r', -1), ('l', 1)):
        sh = R.pivot('arm_' + side)
        # double biceps: upper arm out level with the shoulder, forearm straight up
        upper = np.linalg.norm(R.pivot('forearm_' + side) - sh)
        lower = np.linalg.norm(R.pivot('taco' if side == 'r' else 'jug') - R.pivot('forearm_' + side))
        el = sh + [sx * upper * 0.97, 0.3, -upper * 0.2]
        p['flex_' + side] = PS.solve_reach('badlands_chugs', side, 'taco' if side == 'r' else 'jug', el + [0, lower * 0.95, -0.4],
                                           point='pivot', elbow=(el, 3), natural=0,
                                           bounds=((-120, 60), (-120, 120), (0, 150), (-150, 0)))
    return p


# ---------------------------------------------------------------- EDP445
def edp_anims(H):
    P = poses_edp()
    phone_arm, phone_fore, phone_tilt = P['phone']['arm'], P['phone']['forearm'], P['phone']['prop']
    belly_arm, belly_fore = P['belly_r']['arm'], P['belly_r']['forearm']
    show = lambda a: a.scale('phone', [(0, [1, 1, 1])], 'lin')
    out = []

    # walk: short heavy steps, legs apart, a big side-to-side waddle, arms held out around the belly
    a = H('walk_edp445', 1.2, True)
    a.rot('leg_r', [(0, [20, 0, 5]), (0.6, [-18, 0, 5]), (1.2, [20, 0, 5])])
    a.rot('leg_l', [(0, [-18, 0, -5]), (0.6, [20, 0, -5]), (1.2, [-18, 0, -5])])
    a.rot('shin_r', [(0, [8, 0, 0]), (0.3, [36, 0, 0]), (0.6, [4, 0, 0]), (0.9, [10, 0, 0]), (1.2, [8, 0, 0])])
    a.rot('shin_l', [(0, [4, 0, 0]), (0.3, [10, 0, 0]), (0.6, [8, 0, 0]), (0.9, [36, 0, 0]), (1.2, [4, 0, 0])])
    a.rot('torso', [(0, [3, 6, -6]), (0.3, [4, 0, 0]), (0.6, [3, -6, 6]), (0.9, [4, 0, 0]), (1.2, [3, 6, -6])])
    a.pos('root', [(0, [0.5, 0, 0]), (0.3, [0, 0.5, 0]), (0.6, [-0.5, 0, 0]), (0.9, [0, 0.5, 0]), (1.2, [0.5, 0, 0])])
    a.rot('arm_r', [(0, [12, 0, 16]), (0.6, [-14, 0, 16]), (1.2, [12, 0, 16])])
    a.rot('arm_l', [(0, [-14, 0, -16]), (0.6, [12, 0, -16]), (1.2, [-14, 0, -16])])
    a.rot('forearm_r', [(0, [-18, 0, 0]), (0.6, [-26, 0, 0]), (1.2, [-18, 0, 0])])
    a.rot('forearm_l', [(0, [-26, 0, 0]), (0.6, [-18, 0, 0]), (1.2, [-26, 0, 0])])
    a.rot('head', [(0, [-3, -3, 5]), (0.6, [-3, 3, -5]), (1.2, [-3, -3, 5])])
    a.rot('jaw', [(0, [4, 0, 0])], 'lin')          # mouth a little open: he's breathing hard
    show(a)
    out.append(a)

    # idle: thumbing through his phone, right hand on his belly, breathing heavy; every so often
    # looks up at how long this line is, sighs, wipes his forehead, back to the phone
    a = H('idle_edp445', 8.0, True)
    show(a)
    up, look = phone_arm, [phone_arm[0] + 30, phone_arm[1], phone_arm[2]]
    a.rot('arm_l', [(0, up), (2.8, up), (3.2, look, 'easeInOutSine'), (5.8, look), (6.2, up, 'easeInOutSine'), (8.0, up)])
    a.rot('forearm_l', [(0, phone_fore), (0.6, [phone_fore[0] - 4, 0, 0]), (1.2, phone_fore), (1.8, [phone_fore[0] - 4, 0, 0]),
                        (2.4, phone_fore), (2.8, phone_fore), (3.2, [phone_fore[0] + 25, 0, 0], 'easeInOutSine'),
                        (5.8, [phone_fore[0] + 25, 0, 0]), (6.2, phone_fore, 'easeInOutSine'), (7.0, [phone_fore[0] - 4, 0, 0]),
                        (8.0, phone_fore)])
    a.rot('phone', [(0, phone_tilt), (8.0, phone_tilt)], 'lin')
    a.rot('head', [(0, [16, 0, 0]), (2.8, [16, 0, 0]), (3.2, [-4, 0, 0], 'easeInOutSine'), (3.6, [-4, -24, 0]),
                   (4.0, [-4, -24, 0]), (4.4, [-2, 22, 0], 'easeInOutSine'), (4.8, [-2, 22, 0]), (5.1, [-8, 0, 0]),
                   (5.8, [-6, 0, 0]), (6.2, [16, 0, 0], 'easeInOutSine'), (8.0, [16, 0, 0])])
    a.rot('arm_r', [(0, belly_arm), (4.9, belly_arm), (5.25, P['forehead_r']['arm'], 'easeInOutSine'),
                    (5.45, [P['forehead_r']['arm'][0], P['forehead_r']['arm'][1] - 12, P['forehead_r']['arm'][2]]),
                    (5.65, P['forehead_r']['arm']), (6.0, belly_arm, 'easeInOutSine'), (8.0, belly_arm)])
    a.rot('forearm_r', [(0, belly_fore), (4.9, belly_fore), (5.25, P['forehead_r']['forearm'], 'easeInOutSine'),
                        (5.65, P['forehead_r']['forearm']), (6.0, belly_fore, 'easeInOutSine'), (8.0, belly_fore)])
    a.pos('root', [(0, ZERO), (2.0, [0.3, 0, 0]), (4.0, ZERO), (6.0, [-0.3, 0, 0]), (8.0, ZERO)])
    a.rot('torso', [(0, ZERO), (2.0, [0, 0, -2]), (4.0, ZERO), (6.0, [0, 0, 2]), (8.0, ZERO)])
    a.rot('jaw', [(0, [3, 0, 0]), (4.2, [3, 0, 0]), (4.4, [10, 0, 0]), (4.9, [2, 0, 0]), (8.0, [3, 0, 0])])
    a.sound(0.6, 'phone').sound(1.8, 'phone').sound(2.4, 'phone').sound(4.4, 'hmm').sound(7.0, 'phone')
    out.append(a)

    # order: leans on the counter, "twelve" on his fingers, a big nod, points at the menu
    a = H('order_edp445', 3.0, True)
    show(a)
    a.rot('torso', [(0, [6, 0, 0]), (0.4, [9, -4, 0]), (1.6, [9, 4, 0]), (3.0, [6, 0, 0])])
    a.rot('arm_r', [(0, [-60, -10, 8]), (0.3, [-150, -6, 12], 'easeOutBack'), (0.8, [-150, -6, 12]),
                    (1.1, [-75, -20, 6], 'easeInOutSine'), (1.35, [-85, -20, 6]), (1.6, [-75, -20, 6]),
                    (1.85, [-85, -20, 6]), (2.1, [-75, -20, 6]), (2.6, [-60, -10, 8]), (3.0, [-60, -10, 8])])
    a.rot('forearm_r', [(0, [-40, 0, 0]), (0.3, [-10, 0, 0]), (0.8, [-10, 0, 0]), (1.1, [-60, 0, 0]),
                        (1.35, [-80, 0, 0]), (1.6, [-60, 0, 0]), (1.85, [-80, 0, 0]), (2.1, [-60, 0, 0]),
                        (3.0, [-40, 0, 0])])
    a.rot('arm_l', [(0, [-8, 0, -12]), (3.0, [-8, 0, -12])])
    a.rot('forearm_l', [(0, [-30, 0, 0]), (3.0, [-30, 0, 0])])
    a.rot('head', [(0, [-10, 0, 0]), (0.4, [-18, 4, 0]), (0.8, [-18, -4, 0]), (1.1, [-6, 0, 0]), (1.35, [4, 0, 0]),
                   (1.6, [-6, 0, 0]), (1.85, [4, 0, 0]), (2.1, [-6, 0, 0]), (2.4, [10, 0, 0], 'easeOutBack'),
                   (2.7, [-8, 0, 0]), (3.0, [-10, 0, 0])])
    a.sound(1.1, 'tap').sound(1.6, 'tap')
    out.append(a)

    # receive: both hands up, gets it, pulls it to his chest, a happy little shimmy
    a = H('receive_edp445', 1.5, 'hold')
    show(a)
    a.rot('arm_r', [(0, [-40, 0, 8]), (0.45, [-155, -14, 6], 'easeOutBack'), (0.75, [-155, -14, 6]),
                    (1.05, P['taco_show']['arm'], 'easeInOutSine'), (1.5, P['taco_show']['arm'])], 'lin')
    a.rot('forearm_r', [(0, [-30, 0, 0]), (0.45, [-10, 0, 0]), (0.75, [-10, 0, 0]), (1.05, P['taco_show']['forearm']),
                        (1.5, P['taco_show']['forearm'])], 'lin')
    a.rot('arm_l', [(0, [-8, 0, -12]), (0.45, [-120, 14, -10], 'easeOutBack'), (0.8, [-120, 14, -10]),
                    (1.1, [-8, 0, -12], 'easeInOutSine'), (1.5, [-8, 0, -12])])
    a.vis('taco', False, [(0.75, True)])
    a.scale('taco', [(0, ZERO), (0.75 - EPS, ZERO), (0.75, [1.3, 1.3, 1.3]), (0.95, [1, 1, 1], 'easeOutBack'),
                     (1.5, [1, 1, 1])], 'lin')
    a.rot('head', [(0, [-14, 0, 0]), (0.5, [-24, 0, 0]), (0.9, [-6, 0, 0]), (1.2, [8, 0, 0]), (1.5, [4, 0, 0])])
    a.rot('torso', [(0, ZERO), (1.05, ZERO), (1.2, [0, 8, 0]), (1.35, [0, -8, 0]), (1.5, ZERO)])
    a.rot('jaw', [(0, [3, 0, 0]), (0.75, [16, 0, 0], 'easeOutBack'), (1.0, [4, 0, 0]), (1.5, [4, 0, 0])])
    a.sound(0.75, 'pop')
    out.append(a)

    # carrying it back down the line, holding it up in front of him, admiring
    a = edp_anims_walk_taco(H, P)
    show(a)
    out.append(a)

    a = H('hold_taco_edp445', 3.0, True)
    show(a)
    sniff = P['taco_mouth']
    a.rot('arm_r', [(0, P['taco_show']['arm']), (1.0, P['taco_show']['arm']), (1.4, sniff['arm'], 'easeInOutSine'),
                    (1.9, sniff['arm']), (2.3, P['taco_show']['arm'], 'easeInOutSine'), (3.0, P['taco_show']['arm'])])
    a.rot('forearm_r', [(0, P['taco_show']['forearm']), (1.0, P['taco_show']['forearm']), (1.4, sniff['forearm']),
                        (1.9, sniff['forearm']), (2.3, P['taco_show']['forearm']), (3.0, P['taco_show']['forearm'])])
    a.rot('head', [(0, [10, 0, 0]), (1.0, [10, 0, 0]), (1.5, [4, 0, 0]), (1.9, [-6, 0, 0]), (2.2, [8, 0, 0]),
                   (2.5, [2, 0, 0]), (2.8, [8, 0, 0]), (3.0, [10, 0, 0])])
    a.rot('arm_l', [(0, [-8, 0, -12]), (3.0, [-8, 0, -12])])
    a.rot('forearm_l', [(0, [-30, 0, 0]), (3.0, [-30, 0, 0])])
    a.scale('taco', [(0, [1, 1, 1])], 'lin')
    a.sound(1.6, 'hmm')
    out.append(a)

    out.append(edp_react(H, P))
    return crisp(out)


def edp_anims_walk_taco(H, P):
    a = H('walk_taco_edp445', 1.2, True)
    a.rot('leg_r', [(0, [20, 0, 5]), (0.6, [-18, 0, 5]), (1.2, [20, 0, 5])])
    a.rot('leg_l', [(0, [-18, 0, -5]), (0.6, [20, 0, -5]), (1.2, [-18, 0, -5])])
    a.rot('shin_r', [(0, [8, 0, 0]), (0.3, [36, 0, 0]), (0.6, [4, 0, 0]), (0.9, [10, 0, 0]), (1.2, [8, 0, 0])])
    a.rot('shin_l', [(0, [4, 0, 0]), (0.3, [10, 0, 0]), (0.6, [8, 0, 0]), (0.9, [36, 0, 0]), (1.2, [4, 0, 0])])
    a.rot('torso', [(0, [3, 6, -6]), (0.3, [4, 0, 0]), (0.6, [3, -6, 6]), (0.9, [4, 0, 0]), (1.2, [3, 6, -6])])
    a.pos('root', [(0, [0.5, 0, 0]), (0.3, [0, 0.5, 0]), (0.6, [-0.5, 0, 0]), (0.9, [0, 0.5, 0]), (1.2, [0.5, 0, 0])])
    show = P['taco_show']
    a.rot('arm_r', [(0, show['arm']), (0.3, [show['arm'][0] - 4, show['arm'][1], show['arm'][2]]), (0.6, show['arm']),
                    (0.9, [show['arm'][0] - 4, show['arm'][1], show['arm'][2]]), (1.2, show['arm'])])
    a.rot('forearm_r', [(0, show['forearm']), (1.2, show['forearm'])])
    a.rot('arm_l', [(0, [-14, 0, -16]), (0.6, [12, 0, -16]), (1.2, [-14, 0, -16])])
    a.rot('forearm_l', [(0, [-26, 0, 0]), (0.6, [-18, 0, 0]), (1.2, [-26, 0, 0])])
    a.rot('head', [(0, [12, -3, 4]), (0.6, [12, 3, -4]), (1.2, [12, -3, 4])])
    a.scale('taco', [(0, [1, 1, 1])], 'lin')
    return a


# letters of E-A-G-L-E-S as arm shapes: (arm_r, forearm_r, arm_l, forearm_l)
LETTERS = {
    'E': ([0, 0, 90], [0, 0, 0], [-90, 0, -20], [-60, 0, 0]),
    'A': ([0, -20, 150], [0, 0, 0], [0, 20, -150], [0, 0, 0]),
    'G': ([-70, -30, 40], [-110, 0, 0], [0, 0, -50], [0, 0, 0]),
    'L': ([0, 0, 172], [0, 0, 0], [0, 0, -90], [0, 0, 0]),
    'S': ([0, 0, 130], [-70, 0, 0], [0, 0, -40], [-70, 0, 0]),
}


def edp_react(H, P):
    """8.0 s. Bites, chews, the heat hits him; "Yo these tacos is heat bro" at you with the taco held
    out; then spells E-A-G-L-E-S with his arms and pumps his fists for the Birds."""
    a = H('react_edp445', 8.0, 'hold')
    tm, ts, tu = P['taco_mouth'], P['taco_show'], P['taco_up']
    arm_r = [(0, ts['arm']), (0.3, tm['arm'], 'easeInOutSine'), (0.75, tm['arm']), (1.0, ts['arm'], 'easeInOutSine'),
             (1.4, ts['arm']), (1.55, [-62, 0, 10], 'easeOutBack'), (2.3, [-62, 0, 10]), (2.45, [-72, -10, 10]),
             (2.6, [-55, 0, 10]), (3.55, [-55, 0, 10]), (3.7, [-72, -10, 10]), (3.85, [-55, 0, 10]),
             (4.6, tu['arm'], 'easeInOutSine'), (5.5, tu['arm'])]
    fore_r = [(0, ts['forearm']), (0.3, tm['forearm']), (0.75, tm['forearm']), (1.0, ts['forearm']), (1.4, ts['forearm']),
              (1.55, [-12, 0, 0]), (3.85, [-12, 0, 0]), (4.6, tu['forearm']), (5.5, tu['forearm'])]
    arm_l = [(0, [-8, 0, -12]), (1.4, [-8, 0, -12]), (1.6, [-40, 20, -10]), (2.3, [-40, 20, -10]),
             (2.45, [-75, 25, -10], 'easeOutQuad'), (2.6, [-30, 20, -10]), (3.55, [-30, 20, -10]),
             (3.7, [-75, 25, -10], 'easeOutQuad'), (3.85, [-30, 20, -10]), (4.6, [-20, 0, -60], 'easeInOutSine'),
             (5.5, [-20, 0, -60])]
    fore_l = [(0, [-30, 0, 0]), (1.4, [-30, 0, 0]), (1.6, [-80, 0, 0]), (2.3, [-80, 0, 0]), (2.45, [-30, 0, 0]),
              (2.6, [-80, 0, 0]), (3.55, [-80, 0, 0]), (3.7, [-30, 0, 0]), (3.85, [-80, 0, 0]), (5.5, [-10, 0, 0])]
    head = [(0, [6, 0, 0]), (0.3, [0, 0, 0]), (0.55, [4, 0, 0]), (0.7, [0, 0, 0]), (0.9, [6, 0, 0]), (1.1, [2, 0, 0]),
            (1.3, [6, 0, 0]), (1.45, [-18, 0, 0], 'easeOutBack'), (1.65, [-12, 0, 0]),
            (2.0, [-4, -10, 0]), (2.45, [6, 0, 0], 'easeOutQuad'), (2.9, [-4, 10, 0]), (3.3, [-4, 10, 0]),
            (3.7, [8, 0, 0], 'easeOutQuad'), (4.2, [-6, 0, 0]), (4.6, [-20, 0, 0], 'easeInOutSine'), (5.5, [-16, 0, 0])]
    torso = [(0, ZERO), (1.45, [-8, 0, 0], 'easeOutBack'), (1.8, [4, 0, 0]), (2.45, [10, 0, 0]), (2.9, [4, 0, 0]),
             (3.7, [10, 0, 0]), (4.2, ZERO), (4.6, [-10, 0, 0], 'easeInOutSine'), (5.5, [-8, 0, 0])]
    jaw = [(0, [2, 0, 0]), (0.3, [16, 0, 0], 'easeOutQuad'), (0.5, [0, 0, 0], 'easeInQuad'),
           (0.65, [8, 0, 0]), (0.8, [0, 0, 0]), (0.95, [8, 0, 0]), (1.1, [0, 0, 0]), (1.25, [8, 0, 0]),
           (1.4, [0, 0, 0]), (1.5, [18, 0, 0], 'easeOutBack'), (1.7, [4, 0, 0]), (5.5, [4, 0, 0])]
    root = [(0, ZERO), (2.45, ZERO), (2.55, [0, -0.6, 0]), (2.7, ZERO), (3.7, ZERO), (3.8, [0, -0.6, 0]), (3.95, ZERO),
            (4.6, [0, 0, 1.5], 'easeInOutSine'), (5.5, [0, 0, 1.5])]
    taco = [(0, [1, 1, 1]), (0.5 - EPS, [1, 1, 1]), (0.5, [0.72, 0.72, 0.72], 'easeOutQuad'), (5.5, [0.72, 0.72, 0.72]),
            (5.75, [0, 0, 0], 'easeInBack'), (8.0, [0, 0, 0])]
    a.sound(0.5, 'bite').sound(0.95, 'hmm').sound(1.45, 'gasp').sound(2.45, 'hype').sound(3.7, 'hype')

    # E-A-G-L-E-S from 5.9 s, one letter every 0.24 s, then "EAGLES!" with fist pumps and hops
    t = 5.75
    for i, ch in enumerate('EAGLES'):
        ar, fr, al, fl = LETTERS[ch]
        t = round(5.9 + 0.24 * i, 3)
        arm_r.append((t, ar, 'easeOutCubic'))
        fore_r.append((t, fr))
        arm_l.append((t, al, 'easeOutCubic'))
        fore_l.append((t, fl))
        root.append((t, [0, 0.8 if i % 2 == 0 else 0, 1.5]))
        if i in (0, 5):
            a.sound(t, 'hype')
    for i in range(3):
        t = round(7.42 + 0.15 * i, 3)
        up = i % 2 == 0
        arm_r.append((t, [-172, 0, 14] if up else [-60, 0, 14]))
        arm_l.append((t, [-60, 0, -14] if up else [-172, 0, -14]))
        fore_r.append((t, [-30, 0, 0] if not up else [0, 0, 0]))
        fore_l.append((t, [0, 0, 0] if not up else [-30, 0, 0]))
        root.append((t, [0, 1.6 if up else 0, 1.5]))
        torso.append((t, [-6, 8 if up else -8, 0]))
        jaw.append((t, [22 if up else 10, 0, 0]))
    arm_r += [(8.0, [-20, 0, 8], 'easeInOutSine')]
    arm_l += [(8.0, [-8, 0, -12], 'easeInOutSine')]
    fore_r += [(8.0, [-30, 0, 0])]
    fore_l += [(8.0, [-30, 0, 0])]
    root += [(8.0, [0, 0, 1.5])]
    torso += [(8.0, ZERO)]
    jaw += [(8.0, [4, 0, 0])]
    head += [(5.9, [-10, 0, 0]), (7.42, [-16, 0, 0]), (7.7, [-6, 0, 0]), (8.0, [0, 0, 0])]
    a.rot('arm_r', arm_r, 'lin')
    a.rot('forearm_r', fore_r, 'lin')
    a.rot('arm_l', arm_l, 'lin')
    a.rot('forearm_l', fore_l, 'lin')
    a.rot('head', head)
    a.rot('torso', torso)
    a.rot('jaw', jaw, 'lin')
    a.pos('root', root)
    a.scale('taco', taco, 'lin')
    # phone goes in his pocket while he talks with his hands
    a.scale('phone', [(0, [1, 1, 1]), (1.45, [1, 1, 1]), (1.5, [0, 0, 0], 'easeInBack'), (8.0, [0, 0, 0])], 'lin')
    return a


# ---------------------------------------------------------------- Badlands Chugs
JUG_REST_ARM, JUG_REST_FORE = [-14, 6, -8], [-20, 0, 0]


def chugs_anims(H):
    P = poses_chugs()
    out = []
    jm, jd, js = P['jug_mouth'], P['jug_deep'], P['jug_show']

    # walk: a big man's confident stroll, chest out, the jug swinging
    a = H('walk_badlands_chugs', 1.1, True)
    a.rot('leg_r', [(0, [24, 0, 3]), (0.55, [-22, 0, 3]), (1.1, [24, 0, 3])])
    a.rot('leg_l', [(0, [-22, 0, -3]), (0.55, [24, 0, -3]), (1.1, [-22, 0, -3])])
    a.rot('shin_r', [(0, [8, 0, 0]), (0.275, [40, 0, 0]), (0.55, [4, 0, 0]), (0.825, [10, 0, 0]), (1.1, [8, 0, 0])])
    a.rot('shin_l', [(0, [4, 0, 0]), (0.275, [10, 0, 0]), (0.55, [8, 0, 0]), (0.825, [40, 0, 0]), (1.1, [4, 0, 0])])
    a.rot('torso', [(0, [-3, 6, -3]), (0.55, [-3, -6, 3]), (1.1, [-3, 6, -3])])
    a.pos('root', [(0, ZERO), (0.275, [0, 0.7, 0]), (0.55, ZERO), (0.825, [0, 0.7, 0]), (1.1, ZERO)])
    a.rot('arm_r', [(0, [-22, 0, 8]), (0.55, [20, 0, 8]), (1.1, [-22, 0, 8])])
    a.rot('forearm_r', [(0, [-24, 0, 0]), (0.55, [-8, 0, 0]), (1.1, [-24, 0, 0])])
    a.rot('arm_l', [(0, [16, 6, -10]), (0.55, [-20, 6, -10]), (1.1, [16, 6, -10])])
    a.rot('forearm_l', [(0, [-14, 0, 0]), (0.55, [-28, 0, 0]), (1.1, [-14, 0, 0])])
    a.rot('jug', [(0, [-10, 0, 0]), (0.55, [12, 0, 0]), (1.1, [-10, 0, 0])])
    a.rot('head', [(0, [-2, -4, 0]), (0.55, [-2, 4, 0]), (1.1, [-2, -4, 0])])
    out.append(a)

    # idle: a casual pull from the jug, throat working, wipes his mouth, "ahh", looks around
    a = H('idle_badlands_chugs', 6.0, True)
    a.rot('arm_l', [(0, JUG_REST_ARM), (1.0, JUG_REST_ARM), (1.45, jm['arm'], 'easeInOutSine'), (2.6, jm['arm']),
                    (3.0, JUG_REST_ARM, 'easeInOutSine'), (6.0, JUG_REST_ARM)], 'lin')
    a.rot('forearm_l', [(0, JUG_REST_FORE), (1.0, JUG_REST_FORE), (1.45, jm['forearm'], 'easeInOutSine'),
                        (2.6, jm['forearm']), (3.0, JUG_REST_FORE, 'easeInOutSine'), (6.0, JUG_REST_FORE)], 'lin')
    a.rot('jug', [(0, ZERO), (1.0, ZERO), (1.45, jm['prop'], 'easeInOutSine'), (2.6, jm['prop']),
                  (3.0, ZERO, 'easeInOutSine'), (6.0, ZERO)], 'lin')
    head = [(0, ZERO), (1.1, ZERO), (1.5, [-28, 0, 0], 'easeInOutSine')]
    head += [(round(1.7 + 0.25 * i, 3), [-26 if i % 2 else -30, 0, 0]) for i in range(4)]
    head += [(2.65, [-28, 0, 0]), (3.0, [4, 0, 0], 'easeInOutSine'), (3.6, [-6, 0, 0]), (4.2, [-2, -26, 0]),
             (4.8, [-2, 18, 0]), (5.4, ZERO), (6.0, ZERO)]
    a.rot('head', head)
    a.rot('jaw', [(0, ZERO), (1.4, ZERO), (1.55, [9, 0, 0]), (2.6, [9, 0, 0]), (2.75, ZERO), (3.5, ZERO),
                  (3.65, [14, 0, 0]), (3.95, [3, 0, 0]), (6.0, ZERO)])
    wipe = P['wipe']
    a.rot('arm_r', [(0, REST_R), (2.9, REST_R), (3.15, wipe['arm'], 'easeOutQuad'),
                    (3.35, [wipe['arm'][0], wipe['arm'][1] + 25, wipe['arm'][2]]), (3.6, REST_R, 'easeInOutSine'),
                    (6.0, REST_R)], 'lin')
    a.rot('forearm_r', [(0, [-6, 0, 0]), (2.9, [-6, 0, 0]), (3.15, wipe['forearm'], 'easeOutQuad'), (3.35, wipe['forearm']),
                        (3.6, [-6, 0, 0], 'easeInOutSine'), (6.0, [-6, 0, 0])], 'lin')
    a.sound(1.8, 'glug').sound(2.4, 'glug').sound(3.65, 'hmm')
    out.append(a)

    # order: shows the jug to the taquero, taps it, two fingers up ("make it two")
    a = H('order_badlands_chugs', 3.0, True)
    a.rot('arm_l', [(0, JUG_REST_ARM), (0.4, js['arm'], 'easeOutBack'), (1.6, js['arm']), (2.0, JUG_REST_ARM, 'easeInOutSine'),
                    (3.0, JUG_REST_ARM)], 'lin')
    a.rot('forearm_l', [(0, JUG_REST_FORE), (0.4, js['forearm']), (1.6, js['forearm']), (2.0, JUG_REST_FORE),
                        (3.0, JUG_REST_FORE)], 'lin')
    a.rot('jug', [(0, ZERO), (0.4, js['prop']), (1.6, js['prop']), (2.0, ZERO), (3.0, ZERO)], 'lin')
    a.rot('arm_r', [(0, REST_R), (0.7, [-55, -40, 4], 'easeInOutSine'), (0.9, [-60, -42, 4]), (1.05, [-55, -40, 4]),
                    (1.2, [-60, -42, 4]), (1.6, [-130, -6, 10], 'easeOutBack'), (2.4, [-130, -6, 10]),
                    (2.8, REST_R, 'easeInOutSine'), (3.0, REST_R)], 'lin')
    a.rot('forearm_r', [(0, [-6, 0, 0]), (0.7, [-70, 0, 0]), (1.2, [-70, 0, 0]), (1.6, [-30, 0, 0]),
                        (2.0, [-50, 0, 0]), (2.4, [-30, 0, 0]), (3.0, [-6, 0, 0])])
    a.rot('head', [(0, [-12, 0, 0]), (0.4, [-14, -6, 0]), (1.2, [-6, 0, 0]), (1.7, [-16, 0, 0]), (2.1, [-10, 4, 0]),
                   (2.5, [-16, 0, 0]), (3.0, [-12, 0, 0])])
    a.rot('torso', [(0, ZERO), (0.4, [-3, -6, 0]), (1.6, [0, 4, 0]), (3.0, ZERO)])
    a.sound(0.9, 'tap').sound(1.2, 'tap')
    out.append(a)

    # receive: reaches up with his free hand, takes it, nods his approval
    a = H('receive_badlands_chugs', 1.5, 'hold')
    a.rot('arm_r', [(0, REST_R), (0.45, [-150, -14, 6], 'easeOutBack'), (0.75, [-150, -14, 6]),
                    (1.1, [-50, -14, 4], 'easeInOutSine'), (1.5, [-50, -14, 4])], 'lin')
    a.rot('forearm_r', [(0, [-6, 0, 0]), (0.45, [-15, 0, 0]), (0.75, [-15, 0, 0]), (1.1, [-50, 0, 0]),
                        (1.5, [-50, 0, 0])], 'lin')
    a.rot('arm_l', [(0, JUG_REST_ARM), (1.5, JUG_REST_ARM)])
    a.rot('forearm_l', [(0, JUG_REST_FORE), (1.5, JUG_REST_FORE)])
    a.vis('taco', False, [(0.75, True)])
    a.scale('taco', [(0, ZERO), (0.75 - EPS, ZERO), (0.75, [1.3, 1.3, 1.3]), (0.95, [1, 1, 1], 'easeOutBack'),
                     (1.5, [1, 1, 1])], 'lin')
    a.rot('head', [(0, [-14, 0, 0]), (0.5, [-22, 0, 0]), (0.9, [-6, 0, 0]), (1.1, [8, 0, 0]), (1.3, [-2, 0, 0]),
                   (1.5, [4, 0, 0])])
    a.sound(0.75, 'pop')
    out.append(a)

    a = H('walk_taco_badlands_chugs', 1.1, True)
    for b, ch in out[0].bones.items():          # his walk, with the taco held up in his right hand
        if b not in ('arm_r', 'forearm_r'):
            a.bones[b] = {c: dict(k) for c, k in ch.items()}
    a.sounds = dict(out[0].sounds)
    a.rot('arm_r', [(0, [-50, -14, 4]), (0.275, [-54, -14, 4]), (0.55, [-50, -14, 4]), (0.825, [-54, -14, 4]),
                    (1.1, [-50, -14, 4])])
    a.rot('forearm_r', [(0, [-50, 0, 0]), (1.1, [-50, 0, 0])])
    a.scale('taco', [(0, [1, 1, 1])], 'lin')
    out.append(a)

    a = H('hold_taco_badlands_chugs', 2.0, True)
    a.rot('arm_r', [(0, [-50, -14, 4]), (1.0, [-54, -14, 4]), (2.0, [-50, -14, 4])])
    a.rot('forearm_r', [(0, [-50, 0, 0]), (2.0, [-50, 0, 0])])
    a.rot('arm_l', [(0, JUG_REST_ARM), (2.0, JUG_REST_ARM)])
    a.rot('forearm_l', [(0, JUG_REST_FORE), (2.0, JUG_REST_FORE)])
    a.rot('head', [(0, [8, 0, 0]), (1.0, [10, 4, 0]), (2.0, [8, 0, 0])])
    a.scale('taco', [(0, [1, 1, 1])], 'lin')
    out.append(a)

    out.append(chugs_react(H, P))
    return crisp(out)


def chugs_react(H, P):
    """7.0 s. "Hold up. Watch this." Chugs the whole gallon, throat working, head going back as it
    empties; drops it, the burp (4.4 s), a double biceps flex, and the taco in one bite."""
    a = H('react_badlands_chugs', 7.0, 'hold')
    jm, jd, tm, fr, fl = P['jug_mouth'], P['jug_deep'], P['taco_mouth'], P['flex_r'], P['flex_l']
    hold_arm, hold_fore = [-50, -14, 4], [-50, 0, 0]
    # right hand: index finger up ("hold up"), wags it, then holds the taco aside; flex; taco to mouth
    a.rot('arm_r', [(0, hold_arm), (0.25, [-120, -10, 14], 'easeOutBack'), (0.8, [-120, -10, 14]),
                    (1.0, [-30, 0, 30], 'easeInOutSine'), (3.8, [-30, 0, 30]), (4.7, [-30, 0, 30]),
                    (4.9, fr['arm'], 'easeOutBack'), (5.6, fr['arm']), (5.9, tm['arm'], 'easeInOutSine'),
                    (6.4, tm['arm']), (6.7, hold_arm, 'easeInOutSine'), (7.0, hold_arm)], 'lin')
    a.rot('forearm_r', [(0, hold_fore), (0.25, [-60, 0, 0]), (0.4, [-50, 0, 0]), (0.55, [-60, 0, 0]),
                        (0.7, [-50, 0, 0]), (0.8, [-60, 0, 0]), (1.0, [-20, 0, 0]), (4.7, [-20, 0, 0]),
                        (4.9, fr['forearm'], 'easeOutBack'), (5.1, [fr['forearm'][0] - 15, 0, 0]),
                        (5.3, fr['forearm']), (5.45, [fr['forearm'][0] - 15, 0, 0]), (5.6, fr['forearm']),
                        (5.9, tm['forearm']), (6.4, tm['forearm']), (6.7, hold_fore), (7.0, hold_fore)], 'lin')
    # left hand: the jug up to his lips, held there as he chugs, tipping further as it empties
    a.rot('arm_l', [(0, JUG_REST_ARM), (0.9, JUG_REST_ARM), (1.3, jm['arm'], 'easeInOutSine'), (2.5, jm['arm']),
                    (3.6, jd['arm']), (3.85, [-40, 10, -10], 'easeInQuad'), (4.0, [-10, 6, -10]),
                    (4.7, [-10, 6, -10]), (4.9, fl['arm'], 'easeOutBack'), (5.6, fl['arm']),
                    (5.9, JUG_REST_ARM, 'easeInOutSine'), (7.0, JUG_REST_ARM)], 'lin')
    a.rot('forearm_l', [(0, JUG_REST_FORE), (0.9, JUG_REST_FORE), (1.3, jm['forearm'], 'easeInOutSine'),
                        (2.5, jm['forearm']), (3.6, jd['forearm']), (3.85, [-20, 0, 0]), (4.7, [-20, 0, 0]),
                        (4.9, fl['forearm'], 'easeOutBack'), (5.1, [fl['forearm'][0] - 15, 0, 0]),
                        (5.3, fl['forearm']), (5.45, [fl['forearm'][0] - 15, 0, 0]), (5.6, fl['forearm']),
                        (5.9, JUG_REST_FORE), (7.0, JUG_REST_FORE)], 'lin')
    a.rot('jug', [(0, ZERO), (0.9, ZERO), (1.3, jm['prop'], 'easeInOutSine'), (2.5, jm['prop']), (3.6, jd['prop']),
                  (3.85, [30, 0, 0], 'easeInQuad'), (4.0, ZERO), (7.0, ZERO)], 'lin')
    head = [(0, [-6, 0, 0]), (0.25, [-2, 0, 0]), (0.8, [-4, 0, 0]), (1.0, [-12, 0, 0]), (1.35, [-28, 0, 0], 'easeInOutSine')]
    jaw = [(0, ZERO), (1.2, ZERO), (1.35, [10, 0, 0])]
    t = 1.5
    while t < 3.6:   # glug, glug: throat and head working, the jug's angle going up
        k = (t - 1.5) / 2.1
        head.append((round(t, 3), [-28 - 14 * k + 2, 0, 0]))
        head.append((round(t + 0.15, 3), [-28 - 14 * k - 2, 0, 0]))
        if round((t - 1.5) / 0.3) % 2 == 0:
            a.sound(round(t, 3), 'glug')
        t += 0.3
    head += [(3.6, [-42, 0, 0]), (3.85, [6, 0, 0], 'easeOutQuad'), (4.0, ZERO), (4.25, [-14, 0, 0], 'easeInQuad'),
             (4.4, [14, 0, 0], 'easeOutBack'), (4.75, [-4, 0, 0]), (4.9, [-8, 0, 0]), (5.6, [-8, 0, 0]),
             (5.9, ZERO), (6.4, [4, 0, 0]), (6.55, ZERO), (6.7, [4, 0, 0]), (7.0, ZERO)]
    jaw += [(3.6, [10, 0, 0]), (3.85, ZERO), (4.25, ZERO), (4.4, [30, 0, 0], 'easeOutBack'), (4.9, [6, 0, 0]),
            (5.6, [4, 0, 0]), (6.0, [18, 0, 0]), (6.2, [0, 0, 0], 'easeInQuad'), (6.4, [7, 0, 0]),
            (6.55, ZERO), (6.7, [7, 0, 0]), (6.85, ZERO), (7.0, ZERO)]
    a.rot('head', head)
    a.rot('jaw', jaw, 'lin')
    a.rot('torso', [(0, ZERO), (1.3, [-6, 0, 0]), (3.6, [-12, 0, 0]), (3.85, [4, 0, 0]), (4.25, [-4, 0, 0]),
                    (4.4, [14, 0, 0], 'easeOutBack'), (4.8, ZERO), (5.6, ZERO), (7.0, ZERO)])
    a.pos('root', [(0, ZERO), (3.8, ZERO), (3.88, [0, -0.6, 0]), (4.0, ZERO), (7.0, ZERO)])
    a.scale('taco', [(0, [1, 1, 1]), (6.2 - EPS, [1, 1, 1]), (6.2, [0, 0, 0], 'easeInBack'), (7.0, [0, 0, 0])], 'lin')
    a.sound(3.85, 'plop').sound(4.4, 'burp').sound(4.9, 'flex').sound(5.3, 'flex').sound(6.2, 'bite').sound(6.75, 'gulp')
    return a
