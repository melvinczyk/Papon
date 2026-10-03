#!/usr/bin/env python3
"""Generates the animations of the Taco Truck's back room and its boss fight.

Writes, under src/main/resources/assets/papon/animations/entity/:
  gerbin.animation.json       Gerbin the dishwasher (gen_gerbin.py rig)
  papon_boss.animation.json   Chef Papon as the boss (gen_vendor.py papon_boss, the wkcr rig)

Conventions are the rest of the mod's (gen_anims.py): rot X < 0 swings a limb forward and tilts a
torso back; arm_r Z > 0 / arm_l Z < 0 raise an arm outward; arm_r Y < 0 / arm_l Y > 0 swing it
inward. Props hide by scale 0 and are pinned to 0 in every animation that does not key them.
Papon's arms are stubs, so reaches stretch them (scale Y about the shoulder).

Lengths of one-shots are state durations in Java (GerbinState, BossState); keep them in step:
  gerbin: chop loops 6.0 s (GerbinState.CHOP runs 3 loops), wash loops 5.0 s (3 loops),
          look 3.0 s, shocked 2.0 s
  boss:   leap_out 1.5, chug 5.0, roar 2.0, swipe 1.4 (hits 0.5 / 1.0), swipe_overhead 1.3 (hit 1.0),
          leap (AI-held; launch 0.5, dive 1.45-1.55), leap_land 1.5, charge_windup 0.8, charge (loop),
          stagger 2.0, uppercut 1.1 (hit 0.6), air_spike (AI-held; spike 0.45), slam 2.5 (unused),
          breath 3.0 (fire 0.8-2.6), throw 2.0 (release 1.1), drink_handle 2.5, enter_car 0.8,
          exit_stumble 4.0, phase 3.0 (roar 1.2), death 5.0,
          dash_combo 2.5 (hits 0.4 / 0.9 / 1.4, spin 1.9), van_kick 1.2 (kick 0.55),
          grab_lunge (AI-held; lunge 0.45), grab_combo 4.5 (torch 0.8-2.2, salt 2.5-3.4, throw 3.95),
          p2_kneel 4.0, p2_talk 3.5, p2_season 5.0 (shaker 0.6-3.3, golden at 4.0), p2_scream 3.5 (scream 0.75), p2_arrive 2.5, fly (loop),
          p2_barrage 2.0 (slam 1.85), p2_meteor 3.5, van_throw 2.6 (throw 1.8), p2_dive (loop)
  boss "form" controller (phase two only): p2_sprout 3.5 (with p2_scream), p2_form_idle, p2_form_fly,
          p2_form_attack (loops), p2_form_lift 1.0
"""
import json
import os

from gen_anims import guard_brows, EPS
from gen_npc_anims import Pinned, osc

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, 'src/main/resources/assets/papon/animations/entity')
SHOWN, HIDDEN = [1, 1, 1], [0, 0, 0]


ARM_REACH = 6.5     # px from the shoulder pivot to the fist (gen_vendor HAND_R/L)


def stretch(a, arm, keys):
    """Papon's arms are stubs: a reach stretches one (scale Y about the shoulder). On the boss the
    stretch goes to the arm's cube (`arm_*_mesh`) and the fist (`hand_*`, which carries the props)
    slides down the arm to its new end, so nothing he holds is warped (gen_vendor.rig_hands)."""
    if isinstance(a, (B, F)) and arm in ('arm_r', 'arm_l'):
        a.scale(arm + '_mesh', [(t, [1, k, 1]) + tuple(rest) for t, k, *rest in keys], 'lin')
        a.pos('hand_' + arm[-1], [(t, [0, -(k - 1) * ARM_REACH, 0]) + tuple(rest) for t, k, *rest in keys], 'lin')
        return
    a.scale(arm, [(t, [1, k, 1]) + tuple(rest) for t, k, *rest in keys], 'lin')


# ===================================================================== Gerbin

class G(Pinned):
    pins = ['knife', 'tomato', 'brush', 'lettuce_held', 'chop_station', 'sink_station']


CHOP_UP = [-98, -14, 0]       # knife arm out over the board, tip lifted
CHOP_DN = [-87, -14, 0]       # ...rocked down onto the board
HOLD_L = [-80, 18, 0]         # left glove steadying the lettuce
WASH_R = [-70, -8, 0]         # both hands down in the sink
WASH_L = [-70, 8, 0]


def g_idle():
    a = G('idle', 4.0, True)
    a.pos('torso', [(0, [0, 0, 0]), (2.0, [0, 0.25, 0]), (4.0, [0, 0, 0])])
    a.rot('tail', [(0, [0, 0, 0]), (1.0, [8, 6, 0]), (2.0, [0, 0, 0]), (3.0, [8, -6, 0]), (4.0, [0, 0, 0])])
    a.rot('head', [(0, [0, 0, 0]), (1.2, [0, 0, 0]), (1.5, [0, 25, 0], 'easeInOutSine'), (2.2, [0, 25, 0]),
                   (2.5, [0, 0, 0], 'easeInOutSine'), (4.0, [0, 0, 0])], 'lin')
    a.rot('ear_r', [(0, [0, 0, 0]), (2.9, [0, 0, 0]), (3.0, [0, 0, -20]), (3.1, [0, 0, 0]), (4.0, [0, 0, 0])])
    a.rot('arm_r', [(0, [0, 0, 2]), (2.0, [-3, 0, 4]), (4.0, [0, 0, 2])])
    a.rot('arm_l', [(0, [0, 0, -2]), (2.0, [-3, 0, -4]), (4.0, [0, 0, -2])])
    return a


def g_walk():
    a = G('walk', 0.8, True)
    a.rot('leg_r', [(0, [28, 0, 0]), (0.4, [-28, 0, 0]), (0.8, [28, 0, 0])])
    a.rot('leg_l', [(0, [-28, 0, 0]), (0.4, [28, 0, 0]), (0.8, [-28, 0, 0])])
    a.rot('arm_r', [(0, [-24, 0, 4]), (0.4, [22, 0, 4]), (0.8, [-24, 0, 4])])
    a.rot('arm_l', [(0, [22, 0, -4]), (0.4, [-24, 0, -4]), (0.8, [22, 0, -4])])
    a.pos('root', [(0, [0, 0, 0]), (0.2, [0, 0.8, 0]), (0.4, [0, 0, 0]), (0.6, [0, 0.8, 0]), (0.8, [0, 0, 0])])
    a.rot('root', [(0, [0, 0, -3]), (0.4, [0, 0, 3]), (0.8, [0, 0, -3])])
    a.rot('tail', [(0, [10, 14, 0]), (0.4, [10, -14, 0]), (0.8, [10, 14, 0])])
    a.rot('apron_flap', [(0, [-6, 0, 0]), (0.2, [4, 0, 0]), (0.4, [-6, 0, 0]), (0.6, [4, 0, 0]), (0.8, [-6, 0, 0])])
    return a


def g_chop():
    """6.0 s loop. Grabs a head from the crate, rock-chops it to shreds, sweeps them into the bowl,
    wipes his brow."""
    a = G('chop', 6.0, True)
    a.scale('chop_station', [(0, SHOWN)], 'lin')
    a.scale('knife', [(0, SHOWN)], 'lin')
    # left arm: crate (front-left) -> onto the board -> steadies it -> wipes his brow
    a.rot('arm_l', [(0, HOLD_L), (0.3, [-100, -28, 0], 'easeInOutSine'), (0.45, [-104, -30, 0]),
                    (0.75, HOLD_L, 'easeInOutSine'), (4.0, HOLD_L), (4.3, [-20, 0, -6], 'easeInOutSine'),
                    (5.1, [-20, 0, -6]), (5.35, [-150, 40, 0], 'easeInOutSine'), (5.55, [-150, 14, 0]),
                    (5.75, [-150, 40, 0]), (6.0, HOLD_L, 'easeInOutSine')], 'lin')
    a.vis('lettuce_held', False, [(0.42, True), (0.75, False)])
    # right arm: rock-chop 16 times, then the sweep
    keys = [(0, CHOP_UP), (0.8, CHOP_UP)]
    t = 0.8
    for i in range(16):
        keys.append((round(t + 0.1, 2), CHOP_DN, 'easeInQuad'))
        keys.append((round(t + 0.2, 2), CHOP_UP, 'easeOutQuad'))
        t += 0.2
    keys += [(4.2, [-86, -40, 0], 'easeInOutSine'), (4.7, [-86, 24, 0], 'easeInOutSine'), (5.0, CHOP_UP, 'easeInOutSine'),
             (6.0, CHOP_UP)]
    a.rot('arm_r', keys, 'lin')
    a.rot('knife', [(0, [0, 0, 0]), (4.0, [0, 0, 0]), (4.2, [0, 0, 80], 'easeInOutSine'), (4.9, [0, 0, 80]),
                    (5.1, [0, 0, 0]), (6.0, [0, 0, 0])], 'lin')
    # the lettuce: appears on the board, shrinks under the knife, the shreds pile up and get swept off
    a.scale('chop_lettuce', [(0, HIDDEN), (0.75 - EPS, HIDDEN), (0.75, SHOWN), (0.8, SHOWN), (4.0, [0.3, 0.6, 0.25]),
                             (4.15, HIDDEN), (6.0, HIDDEN)], 'lin')
    a.scale('chop_shreds', [(0, HIDDEN), (0.9, HIDDEN), (4.0, [1.2, 1.6, 1.2]), (4.6, [1, 1.2, 1]),
                            (4.85, HIDDEN, 'easeInQuad'), (6.0, HIDDEN)], 'lin')
    a.pos('chop_shreds', [(0, [0, 0, 0]), (4.2, [0, 0, 0]), (4.85, [-9, 0, 1], 'easeInQuad'), (4.9, [0, 0, 0]),
                          (6.0, [0, 0, 0])], 'lin')
    a.rot('torso', [(0, [10, 0, 0]), (5.1, [10, 0, 0]), (5.4, [-6, 0, 0]), (5.8, [-6, 0, 0]), (6.0, [10, 0, 0])])
    a.rot('head', [(0, [18, 0, 0]), (5.1, [18, 0, 0]), (5.4, [-10, 0, 0]), (5.8, [-10, 0, 0]), (6.0, [18, 0, 0])])
    a.rot('tail', osc(0.8, 4.0, 0.4, [8, 10, 0], [8, -10, 0]) + [(5.5, [-10, 0, 0]), (6.0, [8, 10, 0])])
    a.rot('ear_l', [(0, [0, 0, 0]), (5.4, [0, 0, 0]), (5.5, [0, 0, 18]), (5.6, [0, 0, 0]), (6.0, [0, 0, 0])])
    for i in range(16):
        a.sound(round(0.9 + 0.2 * i, 2), 'chop')
    a.sound(0.45, 'leaf').sound(4.4, 'swish').sound(5.5, 'sigh')
    return a


def g_wash():
    """5.0 s loop. Takes a tomato from the basket, scrubs it in the sink, holds it up to check it
    shines, tosses it into the bowl, shakes off his gloves."""
    a = G('wash', 5.0, True)
    a.scale('sink_station', [(0, SHOWN)], 'lin')
    a.scale('brush', [(0, SHOWN)], 'lin')
    reach = [-96, 30, 0]          # right arm out to the basket on his right
    a.rot('arm_r', [(0, [-40, 0, 6]), (0.35, reach, 'easeInOutSine'), (0.5, reach), (0.9, WASH_R, 'easeInOutSine')]
          + [(round(1.0 + 0.15 * i, 2), [WASH_R[0], WASH_R[1], 10 if i % 2 else -10]) for i in range(15)]
          + [(3.2, [-150, -20, 0], 'easeInOutSine'), (3.75, [-150, -20, 0]), (4.05, [-120, -30, 0], 'easeInQuad'),
             (4.2, [-60, -36, 0], 'easeOutQuad'), (4.5, [-40, 0, 6]), (4.6, [-40, 0, 30]), (4.7, [-40, 0, 6]),
             (4.8, [-40, 0, 30]), (5.0, [-40, 0, 6])], 'lin')
    a.rot('arm_l', [(0, [-40, 0, -6]), (0.6, [-40, 0, -6]), (0.9, WASH_L, 'easeInOutSine')]
          + [(round(1.0 + 0.1 * i, 2), [WASH_L[0] + (6 if i % 2 else -6), WASH_L[1] + (10 if i % 2 else -10), 0]) for i in range(22)]
          + [(3.4, [-40, 0, -6], 'easeInOutSine'), (4.5, [-40, 0, -6]), (4.6, [-40, 0, -30]), (4.7, [-40, 0, -6]),
             (4.8, [-40, 0, -30]), (5.0, [-40, 0, -6])], 'lin')
    a.vis('tomato', False, [(0.45, True), (4.25, False)])
    # the toss: the tomato leaves his hand on an arc to the bowl front-left
    a.pos('tomato', [(0, [0, 0, 0]), (4.05, [0, 0, 0]), (4.25, [0, 6, 2], 'easeOutQuad')], 'lin')
    a.rot('torso', [(0, [6, 0, 0]), (0.9, [22, 0, 0], 'easeInOutSine'), (3.0, [22, 0, 0]), (3.3, [0, 0, 0]), (5.0, [6, 0, 0])])
    a.rot('head', [(0, [0, 0, 0]), (0.9, [14, 0, 0]), (3.0, [14, 0, 0]), (3.3, [-16, 0, 8]), (3.75, [-16, 0, -8]),
                   (4.0, [0, 20, 0]), (4.4, [0, 20, 0]), (5.0, [0, 0, 0])])
    a.rot('tail', osc(0, 5.0, 0.5, [6, 8, 0], [6, -8, 0]))
    a.pos('root', [(0, [0, 0, 0]), (3.75, [0, 0, 0]), (3.85, [0, 0.8, 0]), (3.95, [0, 0, 0]), (5.0, [0, 0, 0])])
    a.sound(0.5, 'pick').sound(1.0, 'splash').sound(1.6, 'scrub').sound(2.2, 'scrub').sound(2.8, 'scrub')
    a.sound(3.6, 'squeak').sound(4.6, 'plop').sound(4.6, 'drip')
    return a


def g_look():
    """3.0 s. Peeks out at the customers and gives them a little wave with his yellow glove."""
    a = G('look', 3.0, 'hold')
    a.rot('head', [(0, [0, 0, 0]), (0.4, [0, -70, 0], 'easeInOutSine'), (2.4, [0, -70, 0]), (2.8, [0, 0, 0], 'easeInOutSine'),
                   (3.0, [0, 0, 0])], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.4, [0, -25, 0], 'easeInOutSine'), (2.4, [0, -25, 0]), (2.8, [0, 0, 0]), (3.0, [0, 0, 0])])
    a.rot('arm_l', [(0, [0, 0, -2]), (0.6, [0, 0, -2]), (0.9, [-10, 0, -150], 'easeOutBack')]
          + [(round(1.0 + 0.15 * i, 2), [-10, 0, -150 + (20 if i % 2 else -20)]) for i in range(8)]
          + [(2.4, [-10, 0, -150]), (2.8, [0, 0, -2], 'easeInOutSine'), (3.0, [0, 0, -2])], 'lin')
    for ear, s in (('ear_r', 1), ('ear_l', -1)):
        a.rot(ear, [(0, [0, 0, 0]), (0.5, [0, 0, -14 * s], 'easeOutBack'), (2.4, [0, 0, -14 * s]), (2.8, [0, 0, 0])])
    a.rot('tail', osc(0.6, 2.4, 0.15, [-10, 20, 0], [-10, -20, 0]) + [(3.0, [0, 0, 0])])
    a.sound(0.9, 'hey')
    return a


def g_shocked():
    """2.0 s. The coupon is a fraud. Gerbin jumps out of his skin and drops everything."""
    a = G('shocked', 2.0, 'hold')
    a.pos('root', [(0, [0, 0, 0]), (0.1, [0, -1, 0]), (0.3, [0, 5, 0], 'easeOutQuad'), (0.55, [0, 0, 0], 'easeInQuad'),
                   (2.0, [0, 0, 0])], 'lin')
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-40, 0, 6 * s]), (0.3, [-170, 0, 30 * s], 'easeOutBack'), (1.2, [-170, 0, 30 * s]),
                    (1.6, [-40, 0, 20 * s], 'easeInOutSine'), (2.0, [-40, 0, 20 * s])], 'lin')
    for ear, s in (('ear_r', 1), ('ear_l', -1)):
        a.rot(ear, [(0, [0, 0, 0]), (0.25, [0, 0, -40 * s], 'easeOutBack'), (2.0, [0, 0, -30 * s])], 'lin')
    a.rot('tail', [(0, [0, 0, 0]), (0.25, [-60, 0, 0], 'easeOutBack'), (2.0, [-50, 0, 0])], 'lin')
    a.rot('head', [(0, [0, 0, 0]), (0.3, [-20, 0, 0]), (1.2, [-10, -40, 0]), (2.0, [-10, -40, 0])])
    a.sound(0.1, 'yelp')
    return a


def g_cower():
    """1.2 s loop. Crouched behind the counter with his arms over his horns, shaking."""
    a = G('cower', 1.2, True)
    a.pos('root', [(round(0.06 * i, 2), [0.25 if i % 2 else -0.25, -3, 0]) for i in range(21)], 'lin')
    for leg in ('leg_r', 'leg_l'):
        a.rot(leg, [(0, [-50, 0, 0])], 'lin')
    a.rot('torso', [(0, [30, 0, 0])], 'lin')
    a.pos('torso', [(0, [0, 0, -1.5])], 'lin')
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-160, -40 * s, 0]), (0.6, [-156, -40 * s, 4 * s]), (1.2, [-160, -40 * s, 0])])
    for ear, s in (('ear_r', 1), ('ear_l', -1)):
        a.rot(ear, [(0, [0, 0, 30 * s])], 'lin')
    a.rot('tail', [(0, [40, 0, 0])], 'lin')
    a.rot('head', [(0, [20, 0, 0])], 'lin')
    return a


GERBIN = [g_idle, g_walk, g_chop, g_wash, g_look, g_shocked, g_cower]


# ===================================================================== the boss

class B(Pinned):
    # the phase-two parts are pinned too: only the "form" controller (class F) shows them
    pins = ['bottle', 'handle', 'big_spatula', 'molotov', 'lighter', 'keys', 'sweat', 'tears', 'potato_gun',
            'blood', 'butter', 'fry_crown', 'wing_r', 'wing_l', 'giant_shaker', 'torch', 'salt_shaker']


ANGRY_R = [0, 0, 18]
ANGRY_L = [0, 0, -18]
DRINK_R = [-92, -25, 0]        # right fist in front of his mouth (arm stretched 1.6x)
BOTTLE_UP = [-55, 0, 0]        # the bottle turned up off the fist: cap at his lips, bottom in the air
SPAT_UP = [-170, 30, 40]       # spatula raised behind his head
CROUCH = 40


def angry(a, t=0):
    for brow, ang in (('brow_r', ANGRY_R), ('brow_l', ANGRY_L)):
        a.pos(brow, [(t, [0, -0.8, 0])], 'lin')
        a.rot(brow, [(t, ang)], 'lin')
    return a


def drunk_sway(a, length, amount=5):
    a.rot('root', [(0, [0, 0, -amount]), (length / 2, [0, 0, amount]), (length, [0, 0, -amount])])


def chug(a, prop, t0, t1, glugs):
    """Arm up, the bottle turned up, its liquid draining from t0 to t1, a glug per gulp, the body
    swelling with each one."""
    a.scale(prop + '_liquid', [(0, SHOWN), (t0, SHOWN), (t1, [1, 0, 1])], 'lin')
    for i in range(glugs):
        t = t0 + (t1 - t0) * (i + 0.5) / glugs
        a.sound(round(t, 2), 'glug')


def b_leap_out():
    """1.5 s. From the rage crouch: launches out of the serving window head first, arms forward,
    lands in the lane in a three-point slam (1.0 s). The arc itself is the entity's motion."""
    a = angry(B('leap_out', 1.5, 'hold'))
    a.pos('root', [(0, [0, -3.5, 0]), (0.25, [0, -5, 0], 'easeInQuad'), (0.35, [0, 0, 0], 'easeOutQuad'),
                   (1.0, [0, 0, 0]), (1.05, [0, -4, 0], 'easeOutQuad'), (1.5, [0, 0, 0], 'easeInOutSine')], 'lin')
    a.rot('root', [(0, [28, 0, 0]), (0.25, [34, 0, 0]), (0.4, [70, 0, 0], 'easeOutQuad'), (0.8, [50, 0, 0]),
                   (1.0, [10, 0, 0], 'easeInQuad'), (1.05, [30, 0, 0]), (1.5, [0, 0, 0], 'easeInOutSine')], 'lin')
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [40, 0, 26 * s]), (0.3, [60, 0, 20 * s]), (0.4, [-170, 0, 10 * s], 'easeOutQuad'),
                    (0.95, [-150, 0, 10 * s]), (1.05, [-60, 0, 50 * s], 'easeOutQuad'), (1.5, [-30, 0, 40 * s])], 'lin')
    for leg in ('leg_r', 'leg_l'):
        a.rot(leg, [(0, [-44, 0, 0]), (0.3, [-50, 0, 0]), (0.4, [40, 0, 0], 'easeOutQuad'), (0.95, [10, 0, 0]),
                    (1.05, [-50, 0, 0]), (1.5, [0, 0, 0])], 'lin')
    a.scale('body', [(0, [1.18, 0.82, 1.18]), (0.3, [1.25, 0.75, 1.25]), (0.4, [0.85, 1.25, 0.85], 'easeOutQuad'),
                     (0.9, [0.95, 1.08, 0.95]), (1.02, [1.3, 0.72, 1.3], 'easeOutQuad'), (1.25, [0.97, 1.03, 0.97]),
                     (1.5, [1, 1, 1])], 'lin')
    a.sound(0.35, 'whoosh').sound(1.0, 'slam')
    return a


def b_chug():
    """5.0 s. The bottle comes out. Cap off with a flick. Bottoms up: the whole 750 ml goes down in
    2.5 s, visibly. Smashes the empty, wipes his mouth, belches."""
    a = angry(B('chug', 5.0, 'hold'))
    a.vis('bottle', False, [(0.3, True), (4.45, False)])
    a.rot('arm_r', [(0, [-30, 0, 30]), (0.3, [20, 0, 20]), (0.5, [-60, -20, 10], 'easeOutQuad'), (0.9, [-60, -20, 10]),
                    (1.25, DRINK_R, 'easeInOutSine'), (3.75, DRINK_R), (4.05, [-60, -10, 10], 'easeInOutSine'),
                    (4.25, [-160, 0, 20], 'easeOutQuad'), (4.45, [20, 0, 10], 'easeInQuad'), (4.6, [-100, -50, 0], 'easeOutQuad'),
                    (4.75, [-100, -30, 0]), (4.9, [-20, 0, 20], 'easeInOutSine'), (5.0, [-20, 0, 24])], 'lin')
    stretch(a, 'arm_r', [(0, 1), (0.9, 1), (1.25, 1.6, 'easeInOutSine'), (3.75, 1.6), (4.05, 1), (4.6, 1.4), (4.9, 1)])
    a.rot('bottle', [(0, [0, 0, 0]), (0.9, [0, 0, 0]), (1.25, BOTTLE_UP, 'easeInOutSine'), (3.75, BOTTLE_UP),
                     (4.05, [0, 0, 0], 'easeInOutSine'), (5.0, [0, 0, 0])], 'lin')
    # left hand twists the cap off and flicks it, then flaps for balance while he drinks
    a.rot('arm_l', [(0, [-30, 0, -30]), (0.5, [-30, 0, -30]), (0.65, [-64, 40, 0], 'easeOutQuad'), (0.8, [-60, 50, 0]),
                    (0.9, [-80, 10, -40], 'easeOutQuad'), (1.3, [-20, 0, -60]), (1.8, [-30, 0, -90]), (2.3, [-20, 0, -60]),
                    (2.8, [-30, 0, -90]), (3.3, [-20, 0, -60]), (3.75, [-20, 0, -40]), (5.0, [-20, 0, -30])])
    chug(a, 'bottle', 1.25, 3.75, 8)
    a.rot('torso', [(0, [10, 0, 0]), (1.0, [4, 0, 0]), (1.4, [-30, 0, 0], 'easeInOutSine'), (3.75, [-34, 0, 0]),
                    (4.05, [0, 0, 0], 'easeInOutSine'), (4.45, [16, 0, 0], 'easeOutQuad'), (4.8, [0, 0, 0]), (5.0, [0, 0, 0])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (1.25, [1, 1, 1])]
            + [(round(1.25 + 0.3125 * i + 0.15, 3), [1.03 + 0.008 * i, 1.0, 1.03 + 0.008 * i]) for i in range(8)]
            + [(3.9, [1.08, 1.04, 1.08]), (4.85, [1.1, 0.95, 1.1], 'easeOutQuad'), (5.0, [1.04, 1.02, 1.04])], 'cat')
    a.scale('blush', [(0, [1, 1, 1]), (1.25, [1, 1, 1]), (3.75, [1.1, 1.6, 1]), (5.0, [1.1, 1.6, 1])])
    a.pos('root', [(0, [0, 0, 0]), (4.4, [0, 0, 0]), (4.45, [0, -1, 0]), (4.6, [0, 0, 0]), (5.0, [0, 0, 0])])
    a.sound(0.35, 'cloth').sound(0.85, 'pop').sound(4.45, 'smash').sound(4.85, 'burp')
    return a


def b_roar():
    """2.0 s. Arms wide, head back, ROAR (0.3 s). The fight is on."""
    a = angry(B('roar', 2.0, 'hold'))
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-20, 0, 24 * s]), (0.25, [-10, 0, 10 * s]), (0.35, [-60, 0, 110 * s], 'easeOutExpo'),
                    (1.5, [-60, 0, 110 * s]), (2.0, [-20, 0, 30 * s], 'easeInOutSine')], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.25, [14, 0, 0]), (0.35, [-24, 0, 0], 'easeOutExpo')]
          + [(round(0.45 + 0.08 * i, 2), [-24, 0, 2 if i % 2 else -2]) for i in range(13)]
          + [(2.0, [0, 0, 0], 'easeInOutSine')], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (0.25, [1.1, 0.9, 1.1]), (0.35, [0.88, 1.24, 0.88], 'easeOutExpo')]
            + [(round(0.45 + 0.08 * i, 2), [0.9, 1.2, 0.9] if i % 2 else [0.86, 1.25, 0.86]) for i in range(13)]
            + [(2.0, [1, 1, 1], 'easeInOutSine')], 'lin')
    a.rot('wisps', [(round(0.35 + 0.06 * i, 2), [0, 0, 30 if i % 2 else -30]) for i in range(20)])
    a.sound(0.3, 'roar')
    return a


def b_idle():
    """2.0 s loop. Drunk: swaying, loose arms, a hiccup."""
    a = angry(B('idle', 2.0, True))
    drunk_sway(a, 2.0, 5)
    a.rot('arm_r', [(0, [-10, 0, 14]), (1.0, [10, 0, 20]), (2.0, [-10, 0, 14])])
    a.rot('arm_l', [(0, [10, 0, -20]), (1.0, [-10, 0, -14]), (2.0, [10, 0, -20])])
    a.rot('torso', [(0, [6, 4, 0]), (1.0, [2, -4, 0]), (2.0, [6, 4, 0])])
    a.pos('root', [(0, [0, 0, 0]), (1.45, [0, 0, 0]), (1.55, [0, 1.2, 0]), (1.7, [0, 0, 0]), (2.0, [0, 0, 0])])
    a.scale('body', [(0, [1, 1, 1]), (1.45, [1, 1, 1]), (1.55, [0.95, 1.08, 0.95]), (1.7, [1, 1, 1]), (2.0, [1, 1, 1])])
    a.sound(1.5, 'hiccup')
    return a


def b_walk(name='walk', keys=False):
    """1.0 s loop. A drunk's march: wide, uneven, listing to one side."""
    a = angry(B(name, 1.0, True))
    a.rot('leg_r', [(0, [30, 0, 6]), (0.5, [-26, 0, 6]), (1.0, [30, 0, 6])])
    a.rot('leg_l', [(0, [-26, 0, -6]), (0.5, [30, 0, -6]), (1.0, [-26, 0, -6])])
    a.rot('arm_r', [(0, [-30, 0, 18]), (0.5, [24, 0, 30]), (1.0, [-30, 0, 18])])
    if keys:
        a.scale('keys', [(0, SHOWN)], 'lin')
        a.rot('arm_l', [(round(0.1 * i, 2), [-110, 10, -20 + (8 if i % 2 else -8)]) for i in range(11)], 'lin')
        for i in range(5):
            a.sound(round(0.1 + 0.2 * i, 2), 'keys')
    else:
        a.rot('arm_l', [(0, [24, 0, -30]), (0.5, [-30, 0, -18]), (1.0, [24, 0, -30])])
    a.pos('root', [(0, [0, 0, 0]), (0.25, [0, 1.4, 0]), (0.5, [0, 0, 0]), (0.75, [0, 1.0, 0]), (1.0, [0, 0, 0])])
    a.rot('root', [(0, [0, 6, -7]), (0.5, [0, -6, 4]), (1.0, [0, 6, -7])])
    a.scale('body', [(0, [1.05, 0.95, 1.05]), (0.25, [0.98, 1.03, 0.98]), (0.5, [1.05, 0.95, 1.05]),
                     (0.75, [0.98, 1.03, 0.98]), (1.0, [1.05, 0.95, 1.05])])
    return a


def b_walk_keys():
    return b_walk('walk_keys', True)


def b_run():
    """0.6 s loop. Charging, head down."""
    a = angry(B('run', 0.6, True))
    a.rot('leg_r', [(0, [40, 0, 0]), (0.3, [-40, 0, 0]), (0.6, [40, 0, 0])])
    a.rot('leg_l', [(0, [-40, 0, 0]), (0.3, [40, 0, 0]), (0.6, [-40, 0, 0])])
    a.rot('arm_r', [(0, [-60, 0, 20]), (0.3, [40, 0, 20]), (0.6, [-60, 0, 20])])
    a.rot('arm_l', [(0, [40, 0, -20]), (0.3, [-60, 0, -20]), (0.6, [40, 0, -20])])
    a.rot('torso', [(0, [18, 0, 0])], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (0.15, [0, 2, 0]), (0.3, [0, 0, 0]), (0.45, [0, 2, 0]), (0.6, [0, 0, 0])])
    return a


def b_swipe():
    """1.4 s (BossState.SWIPE, 28 t). The spatula flurry: a forehand across (hit 0.5 s), re-aims, a
    backhand (hit 1.0 s). Each swing comes with a lunge (the entity's motion) sized to the gap."""
    a = angry(B('swipe', 1.4, 'hold'))
    a.scale('big_spatula', [(0, SHOWN)], 'lin')
    a.rot('arm_r', [(0, [-20, 0, 24]), (0.3, [-130, 40, 70], 'easeOutQuad'), (0.4, [-136, 44, 74]),
                    (0.5, [-80, -60, 0], 'easeInExpo'), (0.62, [-60, -80, -10], 'easeOutQuad'),
                    (0.8, [-96, -70, -14], 'easeInOutQuad'), (0.9, [-100, -74, -16]),
                    (1.0, [-90, 50, 50], 'easeInExpo'), (1.12, [-70, 70, 60], 'easeOutQuad'),
                    (1.4, [-40, 30, 40], 'easeInOutSine')], 'lin')
    stretch(a, 'arm_r', [(0, 1), (0.4, 1.15), (0.5, 1.35, 'easeInExpo'), (0.7, 1.1), (1.0, 1.35, 'easeInExpo'), (1.4, 1)])
    a.rot('torso', [(0, [0, 0, 0]), (0.35, [-8, 34, 0], 'easeOutQuad'), (0.5, [14, -40, 0], 'easeInExpo'),
                    (0.85, [8, -34, 0]), (1.0, [14, 40, 0], 'easeInExpo'), (1.15, [10, 44, 0]),
                    (1.4, [4, 10, 0], 'easeInOutSine')], 'lin')
    a.rot('arm_l', [(0, [-20, 0, -24]), (0.35, [-40, 0, -60]), (0.5, [20, 0, -40]), (1.0, [-40, 0, -70]),
                    (1.4, [-20, 0, -24])])
    for leg, s in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [0, 0, 0]), (0.45, [-20 * s, 0, 0]), (0.55, [24 * s, 0, 0]), (0.95, [-24 * s, 0, 0]),
                    (1.05, [20 * s, 0, 0]), (1.4, [0, 0, 0])])
    a.sound(0.2, 'grunt').sound(0.46, 'swing').sound(0.96, 'swing')
    return a


def b_swipe_overhead():
    """1.3 s (BossState.SWIPE_OVERHEAD, 26 t). The finisher of the flurry: both hands up, the
    spatula held high and trembling while he tracks you (to 0.8 s), then brought straight down
    (1.0 s): the hit, and a shockwave racing along the ground in front of him."""
    a = angry(B('swipe_overhead', 1.3, 'hold'))
    a.scale('big_spatula', [(0, SHOWN)], 'lin')
    a.rot('arm_r', [(0, [-60, 60, 50]), (0.4, [-188, 6, 12], 'easeOutQuad'), (0.6, [-194, 4, 10]), (0.7, [-190, 6, 12]),
                    (0.8, [-196, 4, 10]), (1.0, [-38, 0, 4], 'easeInExpo'), (1.3, [-34, 0, 14])], 'lin')
    a.rot('arm_l', [(0, [-20, 0, -24]), (0.4, [-188, -6, -12], 'easeOutQuad'), (0.8, [-194, -4, -10]),
                    (1.0, [-38, 0, -4], 'easeInExpo'), (1.3, [-34, 0, -14])], 'lin')
    for arm in ('arm_r', 'arm_l'):
        stretch(a, arm, [(0, 1), (0.4, 1.3), (0.8, 1.35), (1.0, 1.5, 'easeInExpo'), (1.3, 1.2)])
    a.rot('torso', [(0, [10, 30, 0]), (0.4, [-22, 0, 0], 'easeOutQuad'), (0.8, [-26, 0, 0]), (1.0, [38, 0, 0], 'easeInExpo'),
                    (1.3, [26, 0, 0])], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (0.4, [0, 1.2, 0]), (0.8, [0, 1.4, 0]), (1.0, [0, -2.5, 0], 'easeInExpo'),
                   (1.3, [0, -1.5, 0])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (0.4, [0.92, 1.12, 0.92]), (0.8, [0.9, 1.14, 0.9]), (1.0, [1.22, 0.8, 1.22], 'easeInExpo'),
                     (1.3, [1.06, 0.95, 1.06])], 'lin')
    a.sound(0.3, 'grunt').sound(0.95, 'swing').sound(1.0, 'slam')
    return a


def b_leap():
    """2.4 s, held (BossState.LEAP is AI-driven): crouch (to 0.45 s), launch (0.5 s: the entity
    jumps), tucked at the top with the spatula raised while he hovers and tracks you, then the dive
    (from 1.45 s: head first, arms out). The landing is leap_land."""
    a = angry(B('leap', 2.4, 'hold'))
    a.scale('big_spatula', [(0, SHOWN)], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (0.4, [0, -4.5, 0], 'easeInOutSine'), (0.5, [0, 0, 0], 'easeOutQuad')], 'lin')
    a.rot('root', [(0, [0, 0, 0]), (0.4, [22, 0, 0]), (0.5, [-12, 0, 0], 'easeOutQuad'), (0.9, [-16, 0, 0]),
                   (1.4, [-10, 0, 0]), (1.55, [62, 0, 0], 'easeInQuad'), (2.4, [70, 0, 0])], 'lin')
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-20, 0, 24 * s]), (0.4, [44, 0, 22 * s]), (0.5, [-176, 0, 18 * s], 'easeOutQuad'),
                    (0.9, [-190, 0, 30 * s]), (1.4, [-196, 0, 26 * s]), (1.55, [-160, 0, 40 * s], 'easeInQuad'),
                    (2.4, [-160, 0, 40 * s])], 'lin')
    for leg in ('leg_r', 'leg_l'):
        a.rot(leg, [(0, [0, 0, 0]), (0.4, [-52, 0, 0]), (0.5, [26, 0, 0]), (0.9, [-70, 0, 0]), (1.4, [-74, 0, 0]),
                    (1.55, [10, 0, 0]), (2.4, [16, 0, 0])])
    a.scale('body', [(0, [1, 1, 1]), (0.4, [1.18, 0.82, 1.18]), (0.5, [0.86, 1.22, 0.86], 'easeOutQuad'),
                     (0.9, [1.04, 0.98, 1.04]), (1.4, [1.06, 0.96, 1.06]), (1.55, [0.9, 1.14, 0.9]), (2.4, [0.9, 1.14, 0.9])], 'lin')
    a.sound(0.45, 'grunt').sound(0.5, 'jump').sound(1.45, 'whoosh').sound(1.5, 'roar_small')
    return a


def b_leap_land():
    """1.5 s (BossState.LEAP_LAND, 30 t). The belly-flop impact (0.0 s) and the shockwave rings that
    follow it; pushes himself up, back on his feet by 1.2 s."""
    a = angry(B('leap_land', 1.5, 'hold'))
    a.scale('big_spatula', [(0, SHOWN)], 'lin')
    a.pos('root', [(0, [0, -6, 0]), (0.7, [0, -6, 0]), (1.2, [0, 0, 0], 'easeInOutSine'), (1.5, [0, 0, 0])], 'lin')
    a.rot('root', [(0, [88, 0, 0]), (0.7, [84, 0, 0]), (1.2, [0, 0, 0], 'easeInOutSine'), (1.5, [0, 0, 0])], 'lin')
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-90, 0, 90 * s]), (0.7, [-90, 0, 80 * s]), (0.9, [-150, 0, 10 * s]),
                    (1.5, [-20, 0, 24 * s], 'easeInOutSine')], 'lin')
    for leg in ('leg_r', 'leg_l'):
        a.rot(leg, [(0, [10, 0, 0]), (0.8, [10, 0, 0]), (1.2, [0, 0, 0])])
    a.scale('body', [(0, [1.32, 0.72, 1.32]), (0.25, [1.05, 0.95, 1.05], 'easeOutQuad'), (1.5, [1, 1, 1])], 'lin')
    a.sound(0.05, 'roar_small').sound(0.8, 'grunt')
    return a


def b_charge_windup():
    """0.8 s (BossState.CHARGE_WINDUP, 16 t). Head down like a bull, paws the ground twice with his
    right foot, arms swept back: the tequila rush is coming."""
    a = angry(B('charge_windup', 0.8, 'hold'))
    a.rot('torso', [(0, [0, 0, 0]), (0.25, [34, 0, 0], 'easeOutQuad'), (0.8, [38, 0, 0])], 'lin')
    a.rot('leg_r', [(0, [0, 0, 0]), (0.15, [-30, 0, 0]), (0.3, [30, 0, 0], 'easeInQuad'), (0.45, [-30, 0, 0]),
                    (0.6, [30, 0, 0], 'easeInQuad'), (0.8, [16, 0, 0])])
    a.rot('leg_l', [(0, [0, 0, 0]), (0.8, [-16, 0, 0])])
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-20, 0, 24 * s]), (0.25, [56, 0, 26 * s], 'easeOutQuad'), (0.8, [62, 0, 22 * s])], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (0.25, [0, -2, 0]), (0.8, [0, -2.5, 0])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (0.25, [1.08, 0.92, 1.08]), (0.5, [1.1, 0.9, 1.1]), (0.8, [1.12, 0.88, 1.12])], 'lin')
    a.sound(0.3, 'step').sound(0.6, 'step').sound(0.7, 'roar_small')
    return a


def b_charge():
    """0.4 s loop (BossState.CHARGE). Flat-out, head down, arms back, short furious strides."""
    a = angry(B('charge', 0.4, True))
    a.rot('torso', [(0, [38, 0, -3]), (0.2, [38, 0, 3]), (0.4, [38, 0, -3])])
    a.rot('leg_r', [(0, [50, 0, 0]), (0.2, [-56, 0, 0]), (0.4, [50, 0, 0])])
    a.rot('leg_l', [(0, [-56, 0, 0]), (0.2, [50, 0, 0]), (0.4, [-56, 0, 0])])
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [64, 0, 22 * s]), (0.2, [54, 0, 26 * s]), (0.4, [64, 0, 22 * s])])
    a.pos('root', [(0, [0, -1.5, 0]), (0.1, [0, 0.5, 0]), (0.2, [0, -1.5, 0]), (0.3, [0, 0.5, 0]), (0.4, [0, -1.5, 0])])
    a.sound(0.0, 'step').sound(0.2, 'step')
    return a


def b_stagger():
    """2.0 s (BossState.STAGGER, 40 t). Ran face first into something: knocked back on his heels,
    seeing stars, wobbling, shaking it off. Wide open."""
    a = B('stagger', 2.0, 'hold')
    a.rot('root', [(0, [-24, 0, 0]), (0.2, [-30, 0, 6], 'easeOutQuad'), (0.5, [-10, 0, -12]), (0.8, [6, 0, 12]),
                   (1.1, [-4, 0, -10]), (1.4, [4, 0, 8]), (1.7, [0, 0, -4]), (2.0, [0, 0, 0])])
    a.pos('root', [(0, [0, 0, 3]), (0.2, [0, -2, 4]), (0.6, [0, -1, 2]), (2.0, [0, 0, 0])])
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-40, 0, 60 * s]), (0.4, [-10, 0, 40 * s]), (0.9, [-40, 0, 70 * s]), (1.4, [-20, 0, 30 * s]),
                    (2.0, [-20, 0, 24 * s])])
    for leg, s in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [-20 * s, 0, 10 * s]), (0.5, [20 * s, 0, 6 * s]), (1.0, [-14 * s, 0, 8 * s]), (2.0, [0, 0, 0])])
    for brow, s in (('brow_r', 1), ('brow_l', -1)):
        a.pos(brow, [(0, [0, 1.2, 0]), (2.0, [0, 0.4, 0])])
        a.rot(brow, [(0, [0, 0, -12 * s]), (2.0, [0, 0, 6 * s])])
    a.scale('body', [(0, [1.15, 0.85, 1.15]), (0.3, [0.95, 1.05, 0.95]), (2.0, [1, 1, 1])], 'lin')
    a.sound(0.0, 'thud').sound(0.5, 'grunt')
    return a


def b_blink():
    """0.6 s loop (BossState.BLINK, a cycle per blink: 12 t, 9 in phase two at 1.33x speed). He comes
    out of the puff mid-wind-up (0.3 s: the teleport, body popping back to size), then a straight
    right (0.45 s: the hit), then recoils into the next vanish."""
    a = angry(B('blink', 0.6, True))
    a.rot('arm_r', [(0, [20, 0, 30]), (0.3, [40, 10, 26]), (0.45, [-92, -6, 6], 'easeInExpo'), (0.52, [-88, -6, 6]),
                    (0.6, [20, 0, 30], 'easeInOutQuad')], 'lin')
    stretch(a, 'arm_r', [(0, 1), (0.3, 1), (0.45, 1.9, 'easeInExpo'), (0.52, 1.8), (0.6, 1)])
    a.rot('arm_l', [(0, [-60, 0, -20]), (0.6, [-60, 0, -20])])
    a.rot('torso', [(0, [0, 30, 0]), (0.3, [6, 36, 0]), (0.45, [14, -30, 0], 'easeInExpo'), (0.6, [0, 30, 0])], 'lin')
    a.scale('root', [(0, [0.15, 1.5, 0.15]), (0.25, [0.15, 1.5, 0.15]), (0.32, [1.15, 0.9, 1.15], 'easeOutBack'),
                     (0.42, [1, 1, 1]), (0.55, [1, 1, 1]), (0.6, [0.15, 1.5, 0.15], 'easeInExpo')], 'lin')
    a.sound(0.3, 'whoosh').sound(0.43, 'swing')
    return a


def b_gatling():
    """3.5 s (BossState.GATLING, 70 t). Pulls the potato gatling (0.3 s), levels it (0.7 s), barrels
    spin up, fires from 0.75 s to 2.75 s with recoil shaking him, lowers it smoking."""
    a = angry(B('gatling', 3.5, 'hold'))
    a.vis('potato_gun', False, [(0.3, True), (3.3, False)])
    recoil = [(round(0.75 + 0.05 * i, 2), [-94 if i % 2 else -88, -8, 8]) for i in range(41)]
    a.rot('arm_r', [(0, [-20, 0, 24]), (0.3, [30, 0, 30]), (0.7, [-90, -8, 8], 'easeOutBack')] + recoil
          + [(3.0, [-60, -8, 14]), (3.5, [-20, 0, 24], 'easeInOutSine')], 'lin')
    stretch(a, 'arm_r', [(0, 1), (0.7, 1.25), (2.75, 1.25), (3.5, 1)])
    a.rot('arm_l', [(0, [-20, 0, -24]), (0.7, [-80, 30, 0]), (2.75, [-80, 30, 0]), (3.5, [-20, 0, -24])])
    stretch(a, 'arm_l', [(0, 1), (0.7, 1.4), (2.75, 1.4), (3.5, 1)])
    a.rot('gun_barrels', [(0, [0, 0, 0]), (0.7, [0, 0, 0]), (0.75, [0, 120, 0])]
          + [(round(0.8 + 0.1 * i, 2), [0, 120 + 300 * (i + 1), 0]) for i in range(20)]
          + [(3.3, [0, 6720, 0], 'easeOutQuad')], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.7, [-10, 0, 0])]
          + [(round(0.75 + 0.1 * i, 2), [-12 if i % 2 else -8, 0, 2 if i % 2 else -2]) for i in range(21)]
          + [(3.5, [0, 0, 0])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (0.7, [1.05, 0.97, 1.05])]
            + [(round(0.75 + 0.05 * i, 2), [1.03, 0.98, 1.03] if i % 2 else [1.06, 0.95, 1.06]) for i in range(41)]
            + [(3.5, [1, 1, 1])], 'lin')
    a.sound(0.3, 'cloth').sound(0.6, 'keys').sound(3.0, 'hiccup')
    return a


def b_uppercut():
    """1.1 s (BossState.UPPERCUT, 22 t). Spatula low behind him, crouched (tracks you to 0.4 s), then
    an uppercut that scoops you into the air (0.6 s)."""
    a = angry(B('uppercut', 1.1, 'hold'))
    a.scale('big_spatula', [(0, SHOWN)], 'lin')
    a.rot('arm_r', [(0, [-20, 0, 24]), (0.4, [48, 0, 30], 'easeOutQuad'), (0.5, [52, 0, 28]),
                    (0.6, [-200, 0, 16], 'easeInExpo'), (0.8, [-206, 0, 14]), (1.1, [-120, 0, 30], 'easeInOutSine')], 'lin')
    stretch(a, 'arm_r', [(0, 1), (0.5, 1.1), (0.6, 1.5, 'easeInExpo'), (0.9, 1.4), (1.1, 1.1)])
    a.rot('arm_l', [(0, [-20, 0, -24]), (0.4, [-50, 0, -40]), (0.6, [30, 0, -30]), (1.1, [-20, 0, -24])])
    a.rot('torso', [(0, [0, 0, 0]), (0.4, [30, 18, 0], 'easeOutQuad'), (0.6, [-28, -10, 0], 'easeInExpo'),
                    (1.1, [-10, 0, 0])], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (0.4, [0, -3.5, 0]), (0.6, [0, 2.5, 0], 'easeInExpo'), (1.1, [0, 0, 0])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (0.4, [1.14, 0.86, 1.14]), (0.6, [0.86, 1.2, 0.86], 'easeInExpo'), (1.1, [1, 1, 1])], 'lin')
    a.sound(0.3, 'grunt').sound(0.56, 'swing')
    return a


def b_air_spike():
    """1.5 s, held (BossState.AIR_SPIKE). Leaps after you (0.0 s), spatula over his head on the way
    up, spikes you back down at the top (0.45 s), and falls (the landing is leap_land)."""
    a = angry(B('air_spike', 1.5, 'hold'))
    a.scale('big_spatula', [(0, SHOWN)], 'lin')
    a.rot('arm_r', [(0, [-120, 0, 30]), (0.3, [-206, 10, 20], 'easeOutQuad'), (0.38, [-210, 10, 20]),
                    (0.45, [-30, 0, 10], 'easeInExpo'), (1.5, [-60, 0, 40])], 'lin')
    stretch(a, 'arm_r', [(0, 1.1), (0.38, 1.3), (0.45, 1.6, 'easeInExpo'), (0.8, 1.2), (1.5, 1)])
    a.rot('arm_l', [(0, [-20, 0, -24]), (0.3, [-160, 0, -30]), (0.45, [20, 0, -40], 'easeInExpo'), (1.5, [-60, 0, -60])])
    a.rot('root', [(0, [-10, 0, 0]), (0.38, [-18, 0, 0]), (0.45, [40, 0, 0], 'easeInExpo'), (1.5, [30, 0, 0])], 'lin')
    for leg in ('leg_r', 'leg_l'):
        a.rot(leg, [(0, [20, 0, 0]), (0.3, [-60, 0, 0]), (0.45, [-30, 0, 0]), (1.5, [10, 0, 0])])
    a.sound(0.0, 'jump').sound(0.42, 'swing').sound(0.45, 'roar_small')
    return a


def b_slam():
    """2.5 s. Crouch, jump (0.6 s), tuck, belly-flop on the target (1.5 s), push himself back up."""
    a = angry(B('slam', 2.5, 'hold'))
    a.pos('root', [(0, [0, 0, 0]), (0.5, [0, -4, 0], 'easeInOutSine'), (0.6, [0, 0, 0], 'easeOutQuad'),
                   (1.5, [0, 0, 0]), (1.55, [0, -6, 0], 'easeOutQuad'), (2.1, [0, -6, 0]), (2.5, [0, 0, 0], 'easeInOutSine')], 'lin')
    a.rot('root', [(0, [0, 0, 0]), (0.5, [20, 0, 0]), (0.6, [-10, 0, 0], 'easeOutQuad'), (1.1, [-20, 0, 0]),
                   (1.45, [70, 0, 0], 'easeInQuad'), (1.55, [88, 0, 0]), (2.1, [86, 0, 0]), (2.5, [0, 0, 0], 'easeInOutSine')], 'lin')
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-20, 0, 24 * s]), (0.5, [40, 0, 20 * s]), (0.6, [-170, 0, 20 * s], 'easeOutQuad'),
                    (1.2, [-160, 0, 30 * s]), (1.5, [-90, 0, 90 * s], 'easeInQuad'), (2.1, [-90, 0, 80 * s]),
                    (2.3, [-150, 0, 10 * s]), (2.5, [-20, 0, 24 * s], 'easeInOutSine')], 'lin')
    for leg in ('leg_r', 'leg_l'):
        a.rot(leg, [(0, [0, 0, 0]), (0.5, [-50, 0, 0]), (0.6, [30, 0, 0]), (1.1, [-60, 0, 0]), (1.5, [10, 0, 0]),
                    (2.2, [10, 0, 0]), (2.5, [0, 0, 0])])
    a.scale('body', [(0, [1, 1, 1]), (0.5, [1.15, 0.85, 1.15]), (0.6, [0.88, 1.2, 0.88], 'easeOutQuad'),
                     (1.45, [0.95, 1.08, 0.95]), (1.55, [1.3, 0.75, 1.3], 'easeOutQuad'), (1.8, [1.05, 0.95, 1.05]),
                     (2.5, [1, 1, 1])], 'lin')
    a.sound(0.6, 'jump').sound(1.5, 'slam').sound(1.52, 'roar_small').sound(2.2, 'grunt')
    return a


def b_breath():
    """3.0 s. A swig held in his cheeks, the lighter under his chin, and a cone of burning tequila
    sprayed from 0.8 s to 2.6 s."""
    a = angry(B('breath', 3.0, 'hold'))
    a.vis('bottle', True, [(0.55, False)])
    a.scale('bottle_liquid', [(0, [1, 0.5, 1])], 'lin')
    a.rot('arm_r', [(0, [-20, 0, 24]), (0.15, DRINK_R, 'easeInOutSine'), (0.45, DRINK_R), (0.6, [-20, 0, 24], 'easeInOutSine'),
                    (3.0, [-20, 0, 24])], 'lin')
    stretch(a, 'arm_r', [(0, 1), (0.15, 1.6), (0.45, 1.6), (0.6, 1)])
    a.rot('bottle', [(0, [0, 0, 0]), (0.15, BOTTLE_UP), (0.45, BOTTLE_UP), (0.6, [0, 0, 0])])
    a.scale('lighter', [(0, SHOWN)], 'lin')
    a.rot('arm_l', [(0, [-20, 0, -24]), (0.5, [-20, 0, -24]), (0.75, [-90, 40, 0], 'easeOutQuad'), (2.6, [-90, 40, 0]),
                    (3.0, [-20, 0, -24], 'easeInOutSine')], 'lin')
    stretch(a, 'arm_l', [(0, 1), (0.5, 1), (0.75, 1.4), (2.6, 1.4), (3.0, 1)])
    a.scale('body', [(0, [1, 1, 1]), (0.45, [1.12, 1.05, 1.12], 'easeOutBack'), (0.8, [1.14, 1.06, 1.14])]
            + [(round(0.9 + 0.1 * i, 2), [0.96, 1.04, 0.96] if i % 2 else [0.94, 1.06, 0.94]) for i in range(17)]
            + [(3.0, [1, 1, 1])], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.5, [-14, 0, 0]), (0.8, [14, 0, 0], 'easeOutQuad'), (1.7, [14, -16, 0]),
                    (2.6, [14, 16, 0]), (3.0, [0, 0, 0], 'easeInOutSine')])
    a.sound(0.2, 'glug').sound(0.7, 'flick').sound(0.8, 'whoosh').sound(1.2, 'flame').sound(1.8, 'flame').sound(2.4, 'flame')
    return a


def b_throw():
    """2.0 s. Lights the rag (0.5 s), winds up over his head, hurls the molotov (1.1 s)."""
    a = angry(B('throw', 2.0, 'hold'))
    a.vis('molotov', True, [(1.12, False)])
    a.vis('lighter', False, [(0.3, True), (0.8, False)])
    a.rot('arm_r', [(0, [-40, -20, 10]), (0.3, [-60, -40, 0]), (0.75, [-60, -40, 0]), (1.0, [-200, 20, 20], 'easeInOutSine'),
                    (1.12, [-90, -10, 10], 'easeInExpo'), (1.35, [-20, -30, 0], 'easeOutQuad'), (2.0, [-20, 0, 24])], 'lin')
    a.rot('arm_l', [(0, [-20, 0, -24]), (0.3, [-70, 40, 0], 'easeOutQuad'), (0.75, [-70, 40, 0]), (1.0, [-60, 0, -60]),
                    (1.4, [-20, 0, -30]), (2.0, [-20, 0, -24])], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (1.0, [-18, 20, 0], 'easeInOutSine'), (1.12, [20, -20, 0], 'easeInExpo'),
                    (1.5, [12, -10, 0]), (2.0, [0, 0, 0], 'easeInOutSine')], 'lin')
    a.sound(0.4, 'flick').sound(0.55, 'flame').sound(1.08, 'swing')
    return a


def b_drink_handle():
    """2.5 s. THE WARNING: next to his van, the 1.75 l handle comes out and goes down in one, the
    level sinking for all to see (0.5-2.1 s). Then he throws it over his shoulder."""
    a = angry(B('drink_handle', 2.5, 'hold'))
    a.vis('handle', False, [(0.1, True), (2.3, False)])
    a.rot('arm_r', [(0, [-20, 0, 24]), (0.1, [10, 0, 20]), (0.5, DRINK_R, 'easeInOutSine'), (2.1, DRINK_R),
                    (2.25, [-190, 0, 20], 'easeInOutSine'), (2.35, [-200, 0, 20]), (2.5, [-20, 0, 24], 'easeInOutSine')], 'lin')
    stretch(a, 'arm_r', [(0, 1), (0.1, 1), (0.5, 1.6, 'easeInOutSine'), (2.1, 1.6), (2.3, 1.2), (2.5, 1)])
    a.rot('handle', [(0, [0, 0, 0]), (0.2, [0, 0, 0]), (0.5, BOTTLE_UP, 'easeInOutSine'), (2.1, BOTTLE_UP), (2.3, [0, 0, 0])])
    chug(a, 'handle', 0.5, 2.1, 6)
    a.rot('torso', [(0, [0, 0, 0]), (0.5, [-36, 0, 0], 'easeInOutSine'), (2.1, [-40, 0, 0]), (2.3, [-10, 0, 0]),
                    (2.5, [0, 0, 0])], 'lin')
    a.rot('arm_l', [(0, [-20, 0, -24]), (0.6, [-30, 0, -100]), (1.0, [-20, 0, -80]), (1.4, [-30, 0, -100]),
                    (1.8, [-20, 0, -80]), (2.5, [-20, 0, -24])])
    a.scale('body', [(0, [1, 1, 1]), (0.5, [1, 1, 1])]
            + [(round(0.5 + 0.267 * i + 0.13, 3), [1.04 + 0.01 * i, 1.0, 1.04 + 0.01 * i]) for i in range(6)]
            + [(2.2, [1.12, 1.0, 1.12]), (2.5, [1.04, 1.02, 1.04])], 'cat')
    a.scale('blush', [(0, [1.1, 1.6, 1]), (2.1, [1.1, 2.4, 1]), (2.5, [1.1, 2.0, 1])])
    a.sound(0.15, 'cloth').sound(2.3, 'whoosh')
    return a


def b_enter_car():
    """0.8 s. Hauls himself in behind the wheel (he is already seated: this is the settle)."""
    a = angry(B('enter_car', 0.8, 'hold'))
    seated(a)
    a.pos('root', [(0, [0, 3, 2]), (0.4, [0, -1, 0], 'easeInQuad'), (0.8, [0, 0, 0], 'easeOutBack')], 'lin')
    a.sound(0.4, 'thud')
    return a


def seated(a):
    for leg in ('leg_r', 'leg_l'):
        a.rot(leg, [(0, [-85, 0, 0])], 'lin')
    a.pos('root', [(0, [0, 0, 0])], 'lin')
    return a


def b_drive():
    """1.0 s loop. Both stubs on the wheel, leaning into it, bouncing on the seat."""
    a = angry(seated(B('drive', 1.0, True)))
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-80, -20 * s, 0]), (0.25, [-84, -18 * s, 4]), (0.5, [-80, -20 * s, 0]), (0.75, [-84, -22 * s, -4]),
                    (1.0, [-80, -20 * s, 0])])
        stretch(a, arm, [(0, 1.3)])
    a.rot('torso', [(0, [14, 0, -2]), (0.5, [16, 0, 2]), (1.0, [14, 0, -2])])
    a.pos('torso', [(0, [0, 0, 0]), (0.25, [0, 0.6, 0]), (0.5, [0, 0, 0]), (0.75, [0, 0.6, 0]), (1.0, [0, 0, 0])])
    return a


def b_exit_stumble():
    """4.0 s. Falls out of the van plastered: staggers, windmills, nearly goes down, spins, hiccups,
    rubs his head, finds his feet. Wide open the whole time."""
    a = B('exit_stumble', 4.0, 'hold')
    a.rot('root', [(0, [0, 0, 0]), (0.4, [14, 0, 18]), (0.8, [-6, 30, -20]), (1.2, [20, 60, 14]), (1.6, [30, 80, -6]),
                   (1.9, [8, 160, -22]), (2.3, [-4, 260, 10]), (2.7, [6, 360, -8]), (3.2, [0, 360, 6]), (3.6, [0, 360, -3]),
                   (3.6 + EPS, [0, 0, -3], 'lin'), (4.0, [0, 0, 0])])
    a.pos('root', [(0, [0, 0, 0]), (1.4, [0, 0, 0]), (1.6, [0, -3, 0]), (1.9, [0, 0, 0]), (4.0, [0, 0, 0])])
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(round(0.2 * i, 2), [(-160 if (i + (s > 0)) % 2 else 20), 0, 60 * s]) for i in range(10)]
              + [(2.6, [-20, 0, 30 * s]), (4.0, [-20, 0, 24 * s])])
    a.rot('arm_r', [(2.6, [-20, 0, 30]), (2.9, [-176, -24, 10], 'easeInOutSine'), (3.1, [-168, -24, 10]),
                    (3.3, [-176, -24, 10]), (3.6, [-20, 0, 24], 'easeInOutSine'), (4.0, [-20, 0, 24])], 'lin')
    stretch(a, 'arm_r', [(0, 1), (2.6, 1), (2.9, 2.0), (3.3, 2.0), (3.6, 1)])
    for leg, s in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(round(0.25 * i, 2), [30 * s if i % 2 else -30 * s, 0, 8 * s]) for i in range(11)] + [(4.0, [0, 0, 0])])
    for brow, s in (('brow_r', 1), ('brow_l', -1)):
        a.pos(brow, [(0, [0, 1.2, 0]), (1.0, [0, -0.6, 0]), (2.0, [0, 1.0, 0]), (4.0, [0, -0.8, 0])])
        a.rot(brow, [(0, [0, 0, -10 * s]), (2.0, [0, 0, 14 * s]), (4.0, [0, 0, 18 * s])])
    a.scale('blush', [(0, [1.1, 2.4, 1])], 'lin')
    a.sound(0.2, 'thud').sound(1.0, 'hiccup').sound(1.6, 'grunt').sound(2.4, 'hiccup').sound(3.0, 'pat')
    return a


def b_phase():
    """3.0 s. Half his health gone: drops to one knee, punches the ground twice, rises and roars."""
    a = angry(B('phase', 3.0, 'hold'))
    a.pos('root', [(0, [0, 0, 0]), (0.3, [0, -5, 0], 'easeOutQuad'), (1.0, [0, -5, 0]), (1.5, [0, 0, 0], 'easeInOutSine'),
                   (3.0, [0, 0, 0])], 'lin')
    a.rot('leg_r', [(0, [0, 0, 0]), (0.3, [-80, 0, 0]), (1.0, [-80, 0, 0]), (1.5, [0, 0, 0])])
    a.rot('leg_l', [(0, [0, 0, 0]), (0.3, [40, 0, 0]), (1.0, [40, 0, 0]), (1.5, [0, 0, 0])])
    a.rot('arm_r', [(0, [-20, 0, 24]), (0.3, [-150, 0, 20]), (0.45, [-20, 0, 10], 'easeInExpo'), (0.6, [-150, 0, 20]),
                    (0.75, [-20, 0, 10], 'easeInExpo'), (1.2, [-60, 0, 110], 'easeOutExpo'), (2.4, [-60, 0, 110]),
                    (3.0, [-20, 0, 24])], 'lin')
    a.rot('arm_l', [(0, [-20, 0, -24]), (1.0, [-20, 0, -24]), (1.2, [-60, 0, -110], 'easeOutExpo'), (2.4, [-60, 0, -110]),
                    (3.0, [-20, 0, -24])], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.3, [30, 0, 0]), (1.0, [30, 0, 0]), (1.2, [-24, 0, 0], 'easeOutExpo'), (2.4, [-24, 0, 0]),
                    (3.0, [0, 0, 0])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (1.0, [1, 1, 1]), (1.2, [0.88, 1.24, 0.88], 'easeOutExpo')]
            + [(round(1.3 + 0.08 * i, 2), [0.9, 1.2, 0.9] if i % 2 else [0.86, 1.25, 0.86]) for i in range(14)]
            + [(3.0, [1, 1, 1])], 'lin')
    a.scale('blush', [(0, [1.1, 1.6, 1]), (1.2, [1.1, 2.6, 1]), (3.0, [1.1, 2.6, 1])])
    a.sound(0.45, 'slam').sound(0.75, 'slam').sound(1.2, 'roar')
    return a


def b_death():
    """5.0 s. Staggers, sinks to his knees, both hands on his bald head, sobs, topples backwards."""
    a = B('death', 5.0, 'hold')
    a.pos('root', [(0, [0, 0, 0]), (0.8, [0, 0, 2]), (1.0, [0, 0, 2]), (1.5, [0, -5, 2], 'easeInQuad'), (3.8, [0, -5, 2]),
                   (4.4, [0, -7, 10], 'easeInQuad'), (5.0, [0, -7, 10])], 'lin')
    a.rot('root', [(0, [0, 0, 0]), (0.4, [-8, 0, 10]), (0.8, [-4, 0, -8]), (1.5, [10, 0, 0]), (3.8, [10, 0, 0]),
                   (4.4, [-90, 0, 0], 'easeInQuad'), (4.55, [-84, 0, 0]), (4.7, [-90, 0, 0]), (5.0, [-90, 0, 0])], 'lin')
    for leg in ('leg_r', 'leg_l'):
        a.rot(leg, [(0, [0, 0, 0]), (1.0, [0, 0, 0]), (1.5, [-90, 0, 0]), (3.8, [-90, 0, 0]), (4.4, [-20, 0, 0])])
    for arm, s, on in (('arm_r', 1, [-166, -20, 4]), ('arm_l', -1, [-166, 20, -4])):
        a.rot(arm, [(0, [-20, 0, 24 * s]), (1.5, [-20, 0, 24 * s]), (2.0, on, 'easeInOutSine')]
              + [(round(2.1 + 0.12 * i, 2), [on[0] + (3 if i % 2 else -3), on[1], on[2]]) for i in range(14)]
              + [(4.4, [-140, 0, 70 * s], 'easeInQuad'), (5.0, [-140, 0, 70 * s])], 'lin')
        stretch(a, arm, [(0, 1), (1.5, 1), (2.0, 1.8), (3.8, 1.8), (4.4, 1)])
    a.vis('tears', False, [(2.2, True)])
    a.scale('body', [(0, [1, 1, 1]), (2.2, [1, 1, 1])]
            + [(round(2.3 + 0.15 * i, 2), [1.03, 0.97, 1.03] if i % 2 else [1, 1, 1]) for i in range(10)]
            + [(4.4, [1.15, 0.85, 1.15], 'easeOutQuad'), (5.0, [1, 1, 1])], 'lin')
    for brow, s in (('brow_r', 1), ('brow_l', -1)):
        a.pos(brow, [(0, [0, -0.8, 0]), (1.5, [0, 1.5, 0]), (5.0, [0, 1.5, 0])])
        a.rot(brow, [(0, [0, 0, 18 * s]), (1.5, [0, 0, -20 * s]), (5.0, [0, 0, -20 * s])])
    a.sound(0.4, 'grunt').sound(1.5, 'thud').sound(2.3, 'sob').sound(3.0, 'sob').sound(4.4, 'slam')
    return a


# ===================================================================== phase one, more combos

def b_dash_combo():
    """2.5 s (BossState.DASH_COMBO, 50 t). The Taco Tornado: three dashes, a swing at the end of
    each (hits 0.4 / 0.9 / 1.4 s: forehand, backhand, a chop from overhead), then he spins on the
    spot twice with the spatula held out (1.6-2.2 s, the hit at 1.9 s). The dashes are the entity's
    motion; the swings re-aim between them."""
    a = angry(B('dash_combo', 2.5, 'hold'))
    a.scale('big_spatula', [(0, SHOWN)], 'lin')
    a.rot('arm_r', [(0, [-20, 0, 24]), (0.2, [-120, 40, 70], 'easeOutQuad'), (0.4, [-70, -70, 0], 'easeInExpo'),
                    (0.6, [-100, -74, -16], 'easeOutQuad'), (0.9, [-90, 50, 50], 'easeInExpo'),
                    (1.1, [-200, 0, 20], 'easeOutQuad'), (1.4, [-40, 0, 10], 'easeInExpo'), (1.6, [-90, 0, 90], 'easeOutQuad'),
                    (2.2, [-90, 0, 90]), (2.5, [-20, 0, 24], 'easeInOutSine')], 'lin')
    stretch(a, 'arm_r', [(0, 1), (0.4, 1.4, 'easeInExpo'), (0.6, 1.1), (0.9, 1.4, 'easeInExpo'), (1.1, 1.1),
                         (1.4, 1.5, 'easeInExpo'), (1.6, 1.6), (2.2, 1.6), (2.5, 1)])
    a.rot('arm_l', [(0, [-20, 0, -24]), (0.4, [20, 0, -40]), (0.9, [-40, 0, -70]), (1.4, [30, 0, -30]),
                    (1.6, [-90, 0, -90], 'easeOutQuad'), (2.2, [-90, 0, -90]), (2.5, [-20, 0, -24], 'easeInOutSine')], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.2, [10, 34, 0], 'easeOutQuad'), (0.4, [20, -40, 0], 'easeInExpo'), (0.6, [16, -30, 0]),
                    (0.9, [20, 40, 0], 'easeInExpo'), (1.1, [-20, 0, 0], 'easeOutQuad'), (1.4, [30, 0, 0], 'easeInExpo'),
                    (1.6, [6, 0, 0]), (2.2, [6, 0, 0]), (2.5, [0, 0, 0])], 'lin')
    a.rot('root', [(0, [0, 0, 0]), (1.6, [0, 0, 0]), (2.2, [0, -720, 0], 'easeInOutQuad'), (2.2 + EPS, [0, 0, 0]),
                   (2.5, [0, 0, 0])], 'lin')
    for leg, s in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [0, 0, 0]), (0.2, [-40 * s, 0, 0]), (0.4, [30 * s, 0, 0]), (0.7, [-40 * s, 0, 0]), (0.9, [30 * s, 0, 0]),
                    (1.2, [-40 * s, 0, 0]), (1.4, [30 * s, 0, 0]), (1.6, [0, 0, 8 * s]), (2.5, [0, 0, 0])])
    a.pos('root', [(0, [0, 0, 0]), (0.2, [0, 1.5, 0]), (0.4, [0, 0, 0]), (0.7, [0, 1.5, 0]), (0.9, [0, 0, 0]),
                   (1.2, [0, 1.5, 0]), (1.4, [0, -1, 0]), (1.6, [0, 0, 0]), (2.5, [0, 0, 0])])
    for t in (0.2, 0.7, 1.2):
        a.sound(t, 'whoosh')
    a.sound(0.38, 'swing').sound(0.88, 'swing').sound(1.38, 'swing').sound(1.6, 'roar_small').sound(1.85, 'swing')
    return a


def b_van_kick():
    """1.2 s (BossState.VAN_KICK, 24 t). Plants his foot, swings his leg back, and boots the van
    (0.55 s): it goes skidding at you, spinning."""
    a = angry(B('van_kick', 1.2, 'hold'))
    a.rot('leg_r', [(0, [0, 0, 0]), (0.45, [70, 0, 0], 'easeOutQuad'), (0.55, [-110, 0, 0], 'easeInExpo'),
                    (0.8, [-100, 0, 0]), (1.2, [0, 0, 0], 'easeInOutSine')], 'lin')
    a.rot('leg_l', [(0, [0, 0, 0]), (0.45, [-14, 0, 0]), (0.55, [10, 0, 0]), (1.2, [0, 0, 0])])
    a.rot('torso', [(0, [0, 0, 0]), (0.45, [16, 0, 0]), (0.55, [-26, 0, 0], 'easeInExpo'), (1.2, [0, 0, 0])], 'lin')
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-20, 0, 24 * s]), (0.45, [-60, 0, 60 * s]), (0.55, [20, 0, 90 * s], 'easeInExpo'),
                    (1.2, [-20, 0, 24 * s])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (0.45, [1.08, 0.94, 1.08]), (0.55, [0.92, 1.1, 0.92], 'easeInExpo'), (1.2, [1, 1, 1])], 'lin')
    a.sound(0.4, 'grunt').sound(0.55, 'slam')
    return a


def b_grab_lunge():
    """1.2 s, held (BossState.GRAB, AI-driven). Crouches with both stubs back (to 0.4 s), then throws
    himself at you, arms out to grab (the lunge is the entity's motion), and holds there."""
    a = angry(B('grab_lunge', 1.2, 'hold'))
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-20, 0, 24 * s]), (0.35, [40, 0, 30 * s], 'easeOutQuad'), (0.5, [-95, -14 * s, 6 * s], 'easeInExpo'),
                    (1.2, [-95, -14 * s, 6 * s])], 'lin')
        stretch(a, arm, [(0, 1), (0.35, 1), (0.5, 1.8, 'easeInExpo'), (1.2, 1.8)])
    a.rot('torso', [(0, [0, 0, 0]), (0.35, [26, 0, 0]), (0.5, [-8, 0, 0], 'easeInExpo'), (1.2, [-4, 0, 0])], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (0.35, [0, -3, 0]), (0.5, [0, 1, 0], 'easeInExpo'), (1.2, [0, 0, 0])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (0.35, [1.12, 0.88, 1.12]), (0.5, [0.9, 1.12, 0.9], 'easeInExpo'), (1.2, [1, 1, 1])], 'lin')
    a.sound(0.3, 'grunt').sound(0.45, 'whoosh')
    return a


def b_grab_combo():
    """4.5 s (BossState.GRAB_COMBO, 90 t). He has you, held up in his right fist (the entity holds
    you there): the blowtorch out of his apron (0.6 s), lit (0.8 s), and you are browned for a second
    and a half (0.8-2.2 s); torch tossed, the salt shaker (2.5 s), shaken over you (2.5-3.4 s: Brittle);
    then he winds up (3.7 s) and hurls you (3.95 s)."""
    a = angry(B('grab_combo', 4.5, 'hold'))
    hold = [-112, -10, 8]
    a.rot('arm_r', [(0, [-95, -14, 6]), (0.5, hold, 'easeOutQuad')]
          + [(round(0.8 + 0.2 * i, 2), [hold[0] + (4 if i % 2 else -4), hold[1], hold[2]]) for i in range(14)]
          + [(3.5, hold), (3.75, [-178, 10, 40], 'easeInOutSine'), (3.95, [-50, 0, 10], 'easeInExpo'), (4.5, [-20, 0, 24], 'easeInOutSine')],
          'lin')
    stretch(a, 'arm_r', [(0, 1.8), (0.5, 1.7), (3.5, 1.7), (3.75, 1.4), (3.95, 1.9, 'easeInExpo'), (4.5, 1)])
    a.vis('torch', False, [(0.6, True), (2.35, False)])
    a.scale('torch_flame', [(0, HIDDEN), (0.8 - EPS, HIDDEN), (0.8, [1.3, 1.3, 1.3]), (0.85, SHOWN)]
            + [(round(0.9 + 0.1 * i, 2), [1.1, 1.1, 1.3] if i % 2 else [0.9, 0.9, 1.0]) for i in range(13)]
            + [(2.2, SHOWN), (2.2 + EPS, HIDDEN), (4.5, HIDDEN)], 'lin')
    a.vis('salt_shaker', False, [(2.5, True), (3.45, False)])
    torch_at = [-88, 26, 0]                    # left fist out under you, the nozzle turned up at you
    shake_at = [-96, 30, 0]                    # left fist straight out at you
    l_keys = [(0, [-95, 14, -6]), (0.4, [20, 0, -30], 'easeOutQuad'), (0.6, [10, 0, -20]), (0.8, torch_at, 'easeOutBack')]
    l_keys += [(round(0.9 + 0.15 * i, 2), [torch_at[0] + (6 if i % 2 else -6), torch_at[1] + (8 if i % 2 else -8), 0]) for i in range(9)]
    l_keys += [(2.25, [-120, 0, -60], 'easeOutQuad'), (2.4, [10, 0, -30], 'easeInQuad'), (2.55, shake_at, 'easeOutBack')]
    l_keys += [(round(2.6 + 0.1 * i, 2), [shake_at[0] + (10 if i % 2 else -6), shake_at[1], 0]) for i in range(8)]
    l_keys += [(3.45, [-90, 0, -50], 'easeOutQuad'), (3.75, [-40, 0, -60]), (4.5, [-20, 0, -24], 'easeInOutSine')]
    a.rot('arm_l', l_keys, 'lin')
    stretch(a, 'arm_l', [(0, 1.8), (0.4, 1), (0.8, 1.5), (2.25, 1.5), (2.4, 1), (2.55, 2.0), (3.45, 2.0), (3.75, 1)])
    a.rot('torch', [(0, [75, 0, 0])], 'lin')
    # straight out along his arm, cap first, at you: shaken with flicks of the wrist
    a.rot('salt_shaker', [(0, [0, 0, 0]), (2.5, [0, 0, 0])]
          + [(round(2.6 + 0.1 * i, 2), [14 if i % 2 else -8, 0, 12 if i % 2 else -12]) for i in range(9)], 'lin')
    a.rot('torso', [(0, [-4, 0, 0]), (0.5, [-12, 0, 0]), (2.2, [-10, 0, 0]), (3.5, [-10, 0, 0]), (3.75, [-24, 30, 0], 'easeInOutSine'),
                    (3.95, [24, -30, 0], 'easeInExpo'), (4.5, [0, 0, 0])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (3.75, [1.04, 0.97, 1.04]), (3.95, [0.94, 1.08, 0.94], 'easeInExpo'), (4.5, [1, 1, 1])], 'lin')
    a.sound(0.6, 'cloth').sound(0.8, 'flick')
    for t in (0.85, 1.35, 1.85):
        a.sound(t, 'torch')
    for t in (1.1, 1.6, 2.1):
        a.sound(t, 'sizzle')
    a.sound(2.35, 'whoosh').sound(2.5, 'cloth')
    for i in range(4):
        a.sound(round(2.65 + 0.2 * i, 2), 'shake')
    a.sound(3.7, 'grunt').sound(3.95, 'roar_small')
    return a


# ===================================================================== phase two

KNEEL_ROOT = [0, -5, 0]
KNEEL_LEG_R = [-80, 0, 0]
KNEEL_LEG_L = [40, 0, 0]


def kneel(a, t0, t1):
    a.pos('root', [(t0, KNEEL_ROOT), (t1, KNEEL_ROOT)], 'lin')
    a.rot('leg_r', [(t0, KNEEL_LEG_R), (t1, KNEEL_LEG_R)], 'lin')
    a.rot('leg_l', [(t0, KNEEL_LEG_L), (t1, KNEEL_LEG_L)], 'lin')


def b_p2_kneel():
    """4.0 s (BossState.P2_KNEEL, 80 t). Three quarters of his health gone: he reels, drops to one
    knee, coughs blood into his hand three times (1.2 / 2.0 / 2.8 s, a drop falls each time),
    staring at it; then his head comes up, slowly, to look at you (3.2-4.0 s)."""
    a = B('p2_kneel', 4.0, 'hold')
    a.scale('blood', [(0, SHOWN)], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (0.5, [0, 0, 2.5], 'easeOutQuad'), (0.9, KNEEL_ROOT, 'easeInQuad'), (4.0, KNEEL_ROOT)], 'lin')
    a.rot('root', [(0, [0, 0, 0]), (0.5, [-12, 0, 6], 'easeOutQuad'), (0.9, [0, 0, 0], 'easeInQuad'), (4.0, [0, 0, 0])], 'lin')
    a.rot('leg_r', [(0, [0, 0, 0]), (0.5, [-20, 0, 0]), (0.9, KNEEL_LEG_R, 'easeInQuad'), (4.0, KNEEL_LEG_R)], 'lin')
    a.rot('leg_l', [(0, [0, 0, 0]), (0.5, [10, 0, 0]), (0.9, KNEEL_LEG_L, 'easeInQuad'), (4.0, KNEEL_LEG_L)], 'lin')
    torso = [(0, [0, 0, 0]), (0.5, [-14, 0, 0]), (0.9, [34, 0, 0], 'easeInQuad')]
    hand = [-150, 30, 0]
    arm_l = [(0, [-20, 0, -24]), (0.9, [-30, 0, -30]), (1.05, hand, 'easeOutQuad')]
    for c in (1.2, 2.0, 2.8):
        torso += [(round(c - 0.1, 2), [30, 0, 0]), (c, [46, 0, 0], 'easeInExpo'), (round(c + 0.25, 2), [36, 0, 0], 'easeOutQuad')]
    torso += [(3.2, [36, 0, 0]), (4.0, [4, 0, 0], 'easeInOutSine')]
    arm_l += [(2.95, hand), (3.25, [-60, 10, -20], 'easeInOutSine'), (4.0, [-40, 0, -30])]
    a.rot('torso', torso, 'lin')
    a.rot('arm_l', arm_l, 'lin')
    stretch(a, 'arm_l', [(0, 1), (0.9, 1), (1.05, 1.5), (2.95, 1.5), (3.25, 1)])
    a.rot('arm_r', [(0, [-20, 0, 24]), (0.9, [20, 0, 20], 'easeInQuad'), (4.0, [24, 0, 18])])
    stretch(a, 'arm_r', [(0, 1), (0.9, 1.3), (4.0, 1.3)])
    # a drop of blood falls from his chin with each cough
    drop = [(0, [0, 0, 0])]
    for c in (1.2, 2.0, 2.8):
        drop += [(round(c - EPS, 2), [0, 0, 0]), (c, [0, 0, 0]), (round(c + 0.35, 2), [0, -34, -2], 'easeInQuad'),
                 (round(c + 0.35 + EPS, 2), [0, 0, 0])]
    a.pos('blood_drop', drop, 'lin')
    a.scale('body', [(0, [1, 1, 1])] + [k for c in (1.2, 2.0, 2.8) for k in
                                         ((round(c - 0.1, 2), [1, 1, 1]), (c, [1.08, 0.92, 1.08], 'easeInExpo'),
                                          (round(c + 0.25, 2), [1, 1, 1]))], 'lin')
    for brow, s in (('brow_r', 1), ('brow_l', -1)):
        a.pos(brow, [(0, [0, 1.0, 0]), (3.2, [0, 1.0, 0]), (4.0, [0, -0.6, 0])])
        a.rot(brow, [(0, [0, 0, -12 * s]), (3.2, [0, 0, -12 * s]), (4.0, [0, 0, 16 * s])])
    a.sound(0.9, 'thud')
    for c in (1.2, 2.0, 2.8):
        a.sound(c, 'cough')
    for t in (0.2, 1.0, 1.8, 2.6, 3.4):
        a.sound(t, 'heartbeat')
    return a


def b_p2_talk():
    """3.5 s (BossState.P2_TALK, 70 t). Still on one knee: wipes the blood off with the back of his
    hand, chuckles ("Okay... enough playing around."), gets up, and cracks his neck both ways."""
    a = angry(B('p2_talk', 3.5, 'hold'))
    a.scale('blood', [(0, SHOWN)], 'lin')
    a.pos('root', [(0, KNEEL_ROOT), (2.0, KNEEL_ROOT), (2.9, [0, 0, 0], 'easeInOutSine'), (3.5, [0, 0, 0])], 'lin')
    a.rot('leg_r', [(0, KNEEL_LEG_R), (2.0, KNEEL_LEG_R), (2.9, [0, 0, 0], 'easeInOutSine'), (3.5, [0, 0, 0])], 'lin')
    a.rot('leg_l', [(0, KNEEL_LEG_L), (2.0, KNEEL_LEG_L), (2.9, [0, 0, 0], 'easeInOutSine'), (3.5, [0, 0, 0])], 'lin')
    a.rot('arm_l', [(0, [-40, 0, -30]), (0.3, [-120, 70, 0], 'easeInOutSine'), (0.9, [-120, -20, 0], 'easeInOutSine'),
                    (1.2, [-30, 0, -26], 'easeInOutSine'), (3.5, [-20, 0, -24])], 'lin')
    stretch(a, 'arm_l', [(0, 1), (0.3, 1.5), (0.9, 1.5), (1.2, 1)])
    a.rot('arm_r', [(0, [24, 0, 18]), (2.0, [24, 0, 18]), (2.6, [-30, 0, 30], 'easeInOutSine'), (3.5, [-20, 0, 24])])
    stretch(a, 'arm_r', [(0, 1.3), (2.0, 1.3), (2.6, 1)])
    torso = [(0, [4, 0, 0]), (1.2, [4, 0, 0])]
    torso += [(round(1.2 + 0.12 * i, 2), [8 if i % 2 else 2, 0, 0]) for i in range(7)]
    torso += [(2.0, [10, 0, 0]), (2.9, [0, 0, 0]), (3.05, [0, 0, -14], 'easeOutExpo'), (3.2, [0, 0, 0]),
              (3.32, [0, 0, 14], 'easeOutExpo'), (3.5, [0, 0, 0])]
    a.rot('torso', torso, 'lin')
    a.sound(0.4, 'cloth').sound(1.25, 'chuckle').sound(1.6, 'chuckle').sound(2.2, 'grunt').sound(3.05, 'crack').sound(3.32, 'crack')
    return a


def b_p2_season():
    """5.0 s (BossState.P2_SEASON, 100 t). Seasoned by the heavens: arms up, he calls it down (0.0-0.6
    s); a huge salt shaker appears over his head, upside down (0.6-1.2 s), and shakes itself over him
    (1.2-3.2 s) while he basks in it, arms wide, swelling with every wave (the entity pours the salt
    and runs the aura); it bursts (3.3 s), he clenches his fists as the aura flares and his skin
    bakes golden (the entity swaps the texture at 4.0 s), and grins."""
    a = angry(B('p2_season', 5.0, 'hold'))
    a.scale('blood', [(0, SHOWN)], 'lin')
    a.scale('giant_shaker', [(0, HIDDEN), (0.6, HIDDEN), (0.95, [1.15, 1.15, 1.15], 'easeOutBack'), (1.15, SHOWN), (3.2, SHOWN),
                             (3.3, [1.35, 1.35, 1.35], 'easeOutQuad'), (3.4, HIDDEN, 'easeInExpo'), (5.0, HIDDEN)], 'lin')
    a.pos('giant_shaker', [(0, [0, 18, 0]), (0.6, [0, 18, 0]), (1.15, [0, 0, 0], 'easeOutQuad')]
          + [(round(1.2 + 0.12 * i, 2), [1.5 if i % 2 else -1.5, 1.0 if i % 2 else 0, 0]) for i in range(17)]
          + [(3.3, [0, 0, 0])], 'lin')
    a.rot('giant_shaker', [(0, [0, 0, 0]), (1.15, [0, 0, 0])]
          + [(round(1.2 + 0.12 * i, 2), [6 if i % 3 == 0 else -4, 0, 18 if i % 2 else -18]) for i in range(17)]
          + [(3.3, [0, 0, 0])], 'lin')
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-20, 0, 24 * s]), (0.5, [-170, 0, 20 * s], 'easeOutBack'), (1.0, [-170, 0, 20 * s]),
                    (1.3, [-60, 0, 110 * s], 'easeInOutSine'), (3.2, [-60, 0, 120 * s]), (3.5, [-30, 0, 30 * s], 'easeInOutSine'),
                    (3.7, [10, 0, 20 * s], 'easeOutQuad'), (4.0, [-50, 0, 60 * s], 'easeOutExpo'), (5.0, [-30, 0, 40 * s])], 'lin')
        stretch(a, arm, [(0, 1), (0.5, 1.4), (1.0, 1.4), (1.3, 1.2), (3.2, 1.2), (3.5, 1)])
    a.rot('torso', [(0, [0, 0, 0]), (0.5, [-20, 0, 0]), (1.3, [-28, 0, 0])]
          + [(round(1.4 + 0.3 * i, 2), [-28, 0, 4 if i % 2 else -4]) for i in range(6)]
          + [(3.5, [10, 0, 0]), (4.0, [-14, 0, 0], 'easeOutExpo'), (5.0, [0, 0, 0])], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (1.3, [0, 0, 0]), (3.2, [0, 3, 0], 'easeInOutSine'), (3.6, [0, 0, 0], 'easeInQuad'),
                   (3.7, [0, -1.5, 0]), (4.0, [0, 0.5, 0]), (5.0, [0, 0, 0])], 'lin')
    # swelling with power as the salt comes down, a hard pulse when the aura flares
    a.scale('body', [(0, [1, 1, 1]), (1.3, [1, 1, 1])]
            + [(round(1.5 + 0.3 * i, 2), [1 + 0.02 * (i + 1), 1 + 0.025 * (i + 1), 1 + 0.02 * (i + 1)]) for i in range(6)]
            + [(3.6, [1.06, 1.06, 1.06]), (4.0, [1.22, 0.86, 1.22], 'easeOutExpo'), (4.3, [1.08, 1.1, 1.08]),
               (5.0, [1.04, 1.04, 1.04])], 'lin')
    a.rot('wisps', [(round(1.2 + 0.08 * i, 2), [0, 0, 30 if i % 2 else -30]) for i in range(25)])
    a.sound(0.4, 'roar_small').sound(0.9, 'ding')
    for i in range(10):
        a.sound(round(1.25 + 0.2 * i, 2), 'shake')
    a.sound(3.3, 'smash').sound(4.0, 'roar_small').sound(4.4, 'chuckle')
    return a


def b_p2_scream():
    """3.5 s (BossState.P2_SCREAM, 70 t). Hunched and shaking, fists clenched; the SCREAM (0.75 s):
    arms flung wide, head back, he swells and lifts off the ground while the butter lands on his
    head, the fry wings burst out and the crown comes down (the form controller's p2_sprout); a
    last power pose."""
    a = angry(B('p2_scream', 3.5, 'hold'))
    a.scale('blood', [(0, SHOWN)], 'lin')
    jitter = [(round(0.05 * i, 2), [0.3 if i % 2 else -0.3, 0, 0]) for i in range(15)]
    a.pos('root', jitter + [(0.75, [0, 0, 0]), (1.4, [0, 6, 0], 'easeOutQuad')]
          + [(round(1.5 + 0.1 * i, 2), [0.4 if i % 2 else -0.4, 6, 0]) for i in range(15)]
          + [(3.5, [0, 4, 0])], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.5, [30, 0, 0], 'easeOutQuad'), (0.7, [34, 0, 0]), (0.8, [-32, 0, 0], 'easeOutExpo')]
          + [(round(0.9 + 0.1 * i, 2), [-32, 0, 2 if i % 2 else -2]) for i in range(21)]
          + [(3.5, [-4, 0, 0], 'easeInOutSine')], 'lin')
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-20, 0, 24 * s]), (0.5, [-40, -30 * s, 10 * s]), (0.7, [-44, -30 * s, 6 * s]),
                    (0.8, [-60, 0, 120 * s], 'easeOutExpo'), (3.0, [-60, 0, 120 * s]), (3.5, [-40, 0, 40 * s], 'easeInOutSine')], 'lin')
        stretch(a, arm, [(0, 1), (0.7, 1), (0.8, 1.4), (3.0, 1.4), (3.5, 1.1)])
    for leg, s in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [0, 0, 0]), (0.7, [0, 0, 6 * s]), (1.4, [16, 0, 10 * s]), (3.5, [10, 0, 6 * s])])
    a.scale('body', [(0, [1, 1, 1]), (0.7, [1.08, 0.92, 1.08]), (0.8, [0.86, 1.28, 0.86], 'easeOutExpo')]
            + [(round(0.9 + 0.1 * i, 2), [0.9, 1.22, 0.9] if i % 2 else [0.86, 1.28, 0.86]) for i in range(21)]
            + [(3.5, [1.04, 1.04, 1.04], 'easeInOutSine')], 'lin')
    a.rot('wisps', [(round(0.8 + 0.05 * i, 2), [0, 0, 40 if i % 2 else -40]) for i in range(50)])
    a.sound(0.75, 'scream').sound(0.8, 'roar').sound(1.0, 'sprout').sound(1.3, 'sprout').sound(1.7, 'flap').sound(2.2, 'flap')
    return a


def b_p2_arrive():
    """2.5 s (BossState.P2_ARRIVE, 50 t). In his potato dimension: hanging in the air, arms folded,
    wings beating; he points at you (1.6 s) and laughs."""
    a = angry(B('p2_arrive', 2.5, 'hold'))
    a.pos('root', [(0, [0, 2, 0]), (0.6, [0, 0, 0]), (1.2, [0, 1.5, 0]), (1.8, [0, 0, 0]), (2.5, [0, 1, 0])])
    a.rot('arm_r', [(0, [-80, -55, 0]), (1.4, [-80, -55, 0]), (1.6, [-92, 0, 6], 'easeOutBack'), (2.5, [-92, 0, 6])], 'lin')
    stretch(a, 'arm_r', [(0, 1.1), (1.4, 1.1), (1.6, 1.7), (2.5, 1.7)])
    a.rot('arm_l', [(0, [-80, 55, 0]), (2.5, [-80, 55, 0])], 'lin')
    stretch(a, 'arm_l', [(0, 1.1)])
    for leg, s in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [14, 0, 4 * s]), (1.0, [22, 0, 6 * s]), (2.5, [14, 0, 4 * s])])
    a.rot('torso', [(0, [-6, 0, 0]), (1.6, [-6, 0, 0])]
          + [(round(1.7 + 0.1 * i, 2), [-12 if i % 2 else -4, 0, 0]) for i in range(8)], 'lin')
    a.sound(1.6, 'chuckle').sound(1.9, 'chuckle')
    return a


def b_fly():
    """1.0 s loop. Airborne: legs trailing, arms out for balance, bobbing on each wingbeat."""
    a = angry(B('fly', 1.0, True))
    a.pos('root', [(0, [0, 0, 0]), (0.25, [0, 1.6, 0]), (0.5, [0, 0, 0]), (0.75, [0, 1.6, 0]), (1.0, [0, 0, 0])])
    a.rot('torso', [(0, [12, 0, -3]), (0.5, [16, 0, 3]), (1.0, [12, 0, -3])])
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-30, 0, 40 * s]), (0.25, [-24, 0, 52 * s]), (0.5, [-30, 0, 40 * s]), (0.75, [-24, 0, 52 * s]),
                    (1.0, [-30, 0, 40 * s])])
    for leg, s in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [24 + 8 * s, 0, 4 * s]), (0.5, [24 - 8 * s, 0, 4 * s]), (1.0, [24 + 8 * s, 0, 4 * s])])
    return a


def b_p2_barrage():
    """2.0 s (BossState.P2_BARRAGE, 40 t). Point-blank: his fists hammer
    away in turns (a punch every 0.15 s, 0.2-1.55 s), then both fists overhead and down (1.85 s)."""
    a = angry(B('p2_barrage', 2.0, 'hold'))
    out, back = [-92, 0, 6], [24, 0, 30]
    r_keys, l_keys, tor = [(0, [-20, 0, 24])], [(0, [-20, 0, -24])], [(0, [10, 0, 0])]
    for i in range(10):
        t = round(0.2 + 0.15 * i, 2)
        rp, lp = (out, back) if i % 2 == 0 else (back, out)
        r_keys.append((t, rp, 'easeOutExpo'))
        l_keys.append((t, [lp[0], 0, -lp[2]], 'easeOutExpo'))
        tor.append((t, [14, 22 if i % 2 == 0 else -22, 0], 'easeOutExpo'))
        a.sound(t, 'punch')
    r_keys += [(1.7, [-190, 0, 10], 'easeOutQuad'), (1.85, [-60, 0, 10], 'easeInExpo'), (2.0, [-40, 0, 20])]
    l_keys += [(1.7, [-190, 0, -10], 'easeOutQuad'), (1.85, [-60, 0, -10], 'easeInExpo'), (2.0, [-40, 0, -20])]
    tor += [(1.7, [-20, 0, 0], 'easeOutQuad'), (1.85, [36, 0, 0], 'easeInExpo'), (2.0, [20, 0, 0])]
    a.rot('arm_r', r_keys, 'lin')
    a.rot('arm_l', l_keys, 'lin')
    a.rot('torso', tor, 'lin')
    stretch(a, 'arm_r', [(0, 1)] + [(round(0.2 + 0.15 * i, 2), 1.9 if i % 2 == 0 else 1.0, 'easeOutExpo') for i in range(10)]
            + [(1.7, 1.4), (1.85, 1.6), (2.0, 1.2)])
    stretch(a, 'arm_l', [(0, 1)] + [(round(0.2 + 0.15 * i, 2), 1.0 if i % 2 == 0 else 1.9, 'easeOutExpo') for i in range(10)]
            + [(1.7, 1.4), (1.85, 1.6), (2.0, 1.2)])
    a.scale('body', [(0, [1, 1, 1]), (1.7, [0.94, 1.08, 0.94]), (1.85, [1.2, 0.84, 1.2], 'easeInExpo'), (2.0, [1, 1, 1])], 'lin')
    a.sound(1.85, 'slam')
    return a


def b_p2_meteor():
    """3.5 s (BossState.P2_METEOR, 70 t). High in the dome, both arms raised: he calls down a rain of
    burning potatoes and bottles, shaking with the effort."""
    a = angry(B('p2_meteor', 3.5, 'hold'))
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-20, 0, 24 * s]), (0.4, [-176, 0, 30 * s], 'easeOutBack')]
              + [(round(0.5 + 0.1 * i, 2), [-176 + (4 if i % 2 else -4), 0, 30 * s]) for i in range(27)]
              + [(3.5, [-30, 0, 40 * s], 'easeInOutSine')], 'lin')
        stretch(a, arm, [(0, 1), (0.4, 1.5), (3.2, 1.5), (3.5, 1)])
    a.rot('torso', [(0, [0, 0, 0]), (0.4, [-24, 0, 0]), (3.2, [-24, 0, 0]), (3.5, [0, 0, 0])])
    for leg, s in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [20, 0, 6 * s]), (3.5, [20, 0, 6 * s])], 'lin')
    a.sound(0.3, 'roar').sound(0.6, 'rumble').sound(1.6, 'rumble').sound(2.6, 'rumble')
    return a


def b_van_throw():
    """2.6 s (BossState.VAN_THROW, 52 t). Got it: both stubs under the van, up it goes over his head
    (0.2-1.2 s), a heave back (1.6 s), and he throws it at you (1.8 s)."""
    a = angry(B('van_throw', 2.6, 'hold'))
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-60, 0, 30 * s]), (0.2, [-80, 0, 40 * s]), (1.2, [-180, 0, 20 * s], 'easeInOutSine'),
                    (1.6, [-200, 0, 16 * s], 'easeInOutSine'), (1.8, [-60, 0, 10 * s], 'easeInExpo'), (2.6, [-30, 0, 30 * s])], 'lin')
        stretch(a, arm, [(0, 1), (0.2, 1.2), (1.2, 1.7), (1.8, 1.4), (2.6, 1)])
    a.rot('torso', [(0, [10, 0, 0]), (0.2, [20, 0, 0]), (1.2, [-10, 0, 0], 'easeInOutSine'), (1.6, [-28, 0, 0]),
                    (1.8, [32, 0, 0], 'easeInExpo'), (2.6, [0, 0, 0])], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (0.2, [0, -2, 0]), (1.2, [0, 0, 0]), (1.6, [0, 0, 0]), (2.6, [0, 0, 0])])
    for leg, s in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [0, 0, 10 * s]), (1.6, [0, 0, 14 * s]), (1.8, [-20 * s, 0, 10 * s]), (2.6, [0, 0, 0])])
    a.scale('body', [(0, [1, 1, 1]), (0.2, [1.08, 0.92, 1.08]), (1.2, [0.96, 1.06, 0.96]), (1.8, [0.94, 1.08, 0.94]), (2.6, [1, 1, 1])])
    a.sound(0.3, 'grunt').sound(1.0, 'lift').sound(1.75, 'roar_small').sound(1.8, 'whoosh')
    return a


def b_p2_dive():
    """0.5 s loop (BossState.P2_DIVE, AI-held). Head first out of the sky, corkscrewing, fists out."""
    a = angry(B('p2_dive', 0.5, True))
    a.rot('root', [(0, [70, 0, 0]), (0.5, [70, -360, 0])], 'lin')
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-170, 0, 16 * s])], 'lin')
        stretch(a, arm, [(0, 1.5)])
    for leg in ('leg_r', 'leg_l'):
        a.rot(leg, [(0, [20, 0, 0])], 'lin')
    a.sound(0, 'whoosh')
    return a


# ===================================================================== phase two: the limbs controller

class F(Pinned):
    """The "form" controller (phase two, over the main animation): the fry wings, the crown, the
    butter and the blood. Shows them (the main animations pin them to 0) and moves them."""
    pins = []


# fan angles of the fries, top to bottom (gen_vendor.WING_FRIES); their rest rotation is the fan
FAN = [62, 44, 27, 11, -4, -18, -31]
FAN_MID = 14


def form_shown(a):
    for b in ('blood', 'butter', 'fry_crown', 'wing_r', 'wing_l'):
        a.scale(b, [(0, SHOWN)], 'lin')
    return a


def wing(a, side, s, keys, ripple=0.0):
    """keys: [(t, back, up, spread)] for one wing. back/up turn the whole wing (right wing: +Y sweeps
    it back, +Z raises it; mirrored on the left); spread 1 is the fan as built, 0 folds every fry
    onto the middle one, above 1 fans them wider. `ripple` delays the fries down the fan."""
    a.rot('wing_' + side, [(k[0], [0, k[1] * s, k[2] * s]) + tuple(k[4:]) for k in keys])
    for i, ang in enumerate(FAN):
        off = (ang - FAN_MID) * s
        a.rot('fry_%s%d' % (side, i + 1),
              [(round(k[0] + ripple * i, 3), [0, 0, off * (1 - k[3])]) + tuple(k[4:]) for k in keys])


def crown_spin(a, length, turns=1):
    """The crown turns a quarter (its pattern repeats every 90 degrees) per `turns` loops."""
    a.rot('fry_crown', [(0, [0, 0, 0]), (length, [0, 90 * turns, 0])], 'lin')
    a.pos('fry_crown', [(0, [0, 0, 0]), (length / 2, [0, 1.0, 0]), (length, [0, 0, 0])])


def f_sprout():
    """3.5 s (with P2_SCREAM). At the scream (0.75 s) the butter drops onto his head with a plop,
    the wings burst out folded and fan open fry by fry, beat twice, and the crown comes down out
    of the air over his head, turning."""
    a = F('p2_sprout', 3.5, 'hold')
    a.scale('blood', [(0, SHOWN)], 'lin')
    a.scale('butter', [(0, HIDDEN), (0.9, HIDDEN), (1.05, [1.4, 0.5, 1.4], 'easeOutQuad'), (1.3, [0.9, 1.2, 0.9]),
                       (1.5, [1, 1, 1])], 'lin')
    a.pos('butter', [(0, [0, 0, 0]), (0.9, [0, 8, 0]), (1.05, [0, 0, 0], 'easeInQuad'), (3.5, [0, 0, 0])], 'lin')
    for side, s in (('r', 1), ('l', -1)):
        a.scale('wing_' + side, [(0, HIDDEN), (0.95, HIDDEN), (1.15, [1.15, 1.15, 1.15], 'easeOutBack'), (1.35, SHOWN)], 'lin')
        wing(a, side, s, [(0, 70, -10, 0), (1.1, 70, -10, 0), (1.5, 10, 30, 1.25, 'easeOutBack'), (1.9, 20, -20, 0.6),
                          (2.3, 6, 40, 1.3), (2.7, 20, -20, 0.6), (3.1, 6, 40, 1.3), (3.5, 22, 14, 1.0)], ripple=0.03)
    a.scale('fry_crown', [(0, HIDDEN), (1.2, HIDDEN), (1.25, SHOWN)], 'lin')
    a.pos('fry_crown', [(0, [0, 16, 0]), (1.25, [0, 16, 0]), (2.2, [0, 0, 0], 'easeOutBounce'), (3.5, [0, 0, 0])], 'lin')
    a.rot('fry_crown', [(0, [0, 0, 0]), (1.25, [0, 0, 0]), (2.2, [0, 270, 0], 'easeOutQuad'), (3.5, [0, 360, 0])], 'lin')
    a.sound(1.05, 'plop').sound(1.5, 'flap').sound(2.3, 'flap').sound(3.1, 'flap').sound(2.2, 'ding')
    return a


def f_idle():
    """2.4 s loop. Wings half spread, breathing, the fries rippling; the crown turns; the butter
    slumps a little as it melts."""
    a = form_shown(F('p2_form_idle', 2.4, True))
    for side, s in (('r', 1), ('l', -1)):
        wing(a, side, s, [(0, 22, 12, 1.0), (1.2, 26, 22, 1.12), (2.4, 22, 12, 1.0)], ripple=0.06)
    crown_spin(a, 2.4)
    a.scale('butter', [(0, [1, 1, 1]), (1.2, [1.06, 0.9, 1.06]), (2.4, [1, 1, 1])])
    return a


def f_fly():
    """1.2 s loop. Flying: two big beats, fries fanned wide on the downstroke, folded on the way up."""
    a = form_shown(F('p2_form_fly', 1.2, True))
    for side, s in (('r', 1), ('l', -1)):
        wing(a, side, s, [(0, 8, 46, 0.7), (0.3, 18, -24, 1.35), (0.6, 8, 46, 0.7), (0.9, 18, -24, 1.35), (1.2, 8, 46, 0.7)],
             ripple=0.025)
    crown_spin(a, 1.2)
    a.sound(0.25, 'flap').sound(0.85, 'flap')
    return a


def f_attack():
    """0.8 s loop. Wings flared high and wide over him, quivering; the crown spins fast."""
    a = form_shown(F('p2_form_attack', 0.8, True))
    for side, s in (('r', 1), ('l', -1)):
        wing(a, side, s, [(0, 4, 34, 1.3), (0.4, 6, 38, 1.4), (0.8, 4, 34, 1.3)], ripple=0.03)
    crown_spin(a, 0.8)
    return a


def f_lift():
    """1.0 s, held. Wings thrown straight up and fanned to the full (the van over his head, the meteors)."""
    a = form_shown(F('p2_form_lift', 1.0, 'hold'))
    for side, s in (('r', 1), ('l', -1)):
        wing(a, side, s, [(0, 22, 12, 1.0), (0.4, -6, 58, 1.5, 'easeOutBack'), (1.0, -6, 58, 1.5)], ripple=0.03)
    crown_spin(a, 1.0, 2)
    return a


FORM = [f_sprout, f_idle, f_fly, f_attack, f_lift]


BOSS = [b_leap_out, b_chug, b_roar, b_idle, b_walk, b_walk_keys, b_run, b_swipe, b_slam, b_breath, b_throw,
        b_drink_handle, b_enter_car, b_drive, b_exit_stumble, b_phase, b_death,
        b_swipe_overhead, b_leap, b_leap_land, b_charge_windup, b_charge, b_stagger, b_uppercut, b_air_spike,
        b_blink, b_gatling, b_dash_combo, b_van_kick, b_p2_kneel, b_p2_talk, b_p2_scream, b_p2_arrive, b_fly,
        b_p2_barrage, b_p2_meteor, b_van_throw, b_p2_dive, b_p2_season,
        b_grab_lunge, b_grab_combo] + FORM


def write(name, fns, prefix):
    data = {'format_version': '1.8.0', 'animations': {}}
    for fn in fns:
        a = fn()
        data['animations'][prefix + a.name] = guard_brows(a.compile())
    path = os.path.join(OUT_DIR, name + '.animation.json')
    with open(path, 'w', newline='\n') as f:
        json.dump(data, f, indent='\t')
    return path


if __name__ == '__main__':
    print(write('gerbin', GERBIN, 'animation.gerbin.'))
    print(write('papon_boss', BOSS, 'animation.papon_boss.'))
