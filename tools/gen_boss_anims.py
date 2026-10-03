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
  boss:   leap_out 1.5, chug 5.0, roar 2.0, swipe 2.0 (hits 0.6 / 1.3), slam 2.5 (jump 0.6,
          lands 1.5), breath 3.0 (fire 0.8-2.6), throw 2.0 (release 1.1), drink_handle 2.5,
          enter_car 0.8, exit_stumble 4.0, phase 3.0 (roar 1.2), death 5.0
"""
import json
import os

from gen_anims import EPS
from gen_npc_anims import Pinned, osc

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, 'src/main/resources/assets/papon/animations/entity')
SHOWN, HIDDEN = [1, 1, 1], [0, 0, 0]


def stretch(a, arm, keys):
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
    pins = ['bottle', 'handle', 'big_spatula', 'molotov', 'lighter', 'keys', 'sweat', 'tears']


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
    """2.0 s. The giant spatula: wind-up, a forehand across (0.6 s), a backhand (1.3 s)."""
    a = angry(B('swipe', 2.0, 'hold'))
    a.scale('big_spatula', [(0, SHOWN)], 'lin')
    a.rot('arm_r', [(0, [-20, 0, 24]), (0.4, SPAT_UP, 'easeInOutSine'), (0.5, [-176, 36, 46]),
                    (0.62, [-80, -60, 0], 'easeInExpo'), (0.85, [-60, -80, -10], 'easeOutQuad'),
                    (1.15, [-90, -70, -10], 'easeInOutSine'), (1.32, [-90, 50, 50], 'easeInExpo'),
                    (1.55, [-70, 70, 60], 'easeOutQuad'), (2.0, [-20, 0, 24], 'easeInOutSine')], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.45, [-8, 30, 0], 'easeInOutSine'), (0.62, [14, -40, 0], 'easeInExpo'),
                    (1.15, [10, -30, 0]), (1.32, [14, 40, 0], 'easeInExpo'), (1.55, [10, 44, 0]),
                    (2.0, [0, 0, 0], 'easeInOutSine')], 'lin')
    a.rot('arm_l', [(0, [-20, 0, -24]), (0.45, [-40, 0, -60]), (0.62, [20, 0, -40]), (1.32, [-40, 0, -70]), (2.0, [-20, 0, -24])])
    a.pos('root', [(0, [0, 0, 0]), (0.5, [0, 0, 2]), (0.62, [0, 0, -3], 'easeInExpo'), (1.32, [0, 0, -4], 'easeInExpo'),
                   (2.0, [0, 0, 0])], 'lin')
    a.sound(0.4, 'grunt').sound(0.58, 'swing').sound(1.28, 'swing')
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


BOSS = [b_leap_out, b_chug, b_roar, b_idle, b_walk, b_walk_keys, b_run, b_swipe, b_slam, b_breath, b_throw,
        b_drink_handle, b_enter_car, b_drive, b_exit_stumble, b_phase, b_death]


def write(name, fns, prefix):
    data = {'format_version': '1.8.0', 'animations': {}}
    for fn in fns:
        a = fn()
        data['animations'][prefix + a.name] = a.compile()
    path = os.path.join(OUT_DIR, name + '.animation.json')
    with open(path, 'w', newline='\n') as f:
        json.dump(data, f, indent='\t')
    return path


if __name__ == '__main__':
    print(write('gerbin', GERBIN, 'animation.gerbin.'))
    print(write('papon_boss', BOSS, 'animation.papon_boss.'))
