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
          leap (AI-held; launch 0.5, dive 1.45-1.55), leap_land 1.5, charge_windup 0.45, charge (loop),
          stagger 2.0, uppercut 1.1 (hit 0.6), air_spike (AI-held; spike 0.45), slam 2.5 (unused),
          breath 3.0 (fire 0.8-2.6), throw 2.0 (release 1.1), drink_handle 2.5, enter_car 0.8,
          exit_stumble 4.0, phase 3.0 (roar 1.2), death 5.0,
          dash_combo 2.5 (hits 0.4 / 0.9 / 1.4, spin 1.9), van_kick 1.2 (kick 0.55),
          grab_lunge (AI-held; lunge 0.45), grab_combo 4.5 (torch 0.8-2.2, salt 2.5-3.4, throw 3.95),
          p2_kneel 6.0, p2_talk 3.5, p2_season 5.0 (shaker 0.6-3.3, golden at 4.0), p2_scream 3.5 (scream 0.75), p2_arrive 2.5 (no coliseum), fly (loop),
          the coliseum: p2_arrive_sky 5.0 (lands 1.8, snap 3.7), p2_notice 3.0, p2_march (loop), p2_wake 3.5
          (clangs 0.85 / 1.6 / 2.1), p2_wait (loop), p2_kick 1.5 (kick 0.6), p2_count 4.0, p2_groove (loop, a bar),
          p2_apology 8.9 (aura from 5.5), p2_resume 3.0 (held), p2_hype 8.0 (ends on the first sung word),
          p2_solo 3.0,
          p2_barrage 2.0 (slam 1.85), p2_meteor 3.5, van_throw 2.6 (throw 1.8), p2_dive (loop),
          guard (AI-held, crossed cleavers), riposte 0.9 (shove 0.25, scissor 0.6), evade 0.5, cleaver_toss 1.5 (echoes 0.1 / 0.4 / 0.7), echo_throw 2.2 (throws 0.8 / 1.4),
          burrito_toss 1.8 (throw 1.0, lands 1.3), drunk_swig 0.7, drunk_flurry 3.0 (hits 0.5/0.85/1.2/1.55/1.9, hammer 2.5),
          haymaker 2.5 (glint 1.5, hit 1.8), comal_slam 2.0 (slam 0.9), brick 1.8 (pedal 1.0),
          qte_hold_cleaver / qte_hold_bottle / qte_hold_suplex (loops), qte_fail_* 2.0 (hit 1.2), qte_broken 4.0,
          p2_fry_volley 3.0 (fries 1.0-2.2), p2_mash_slam 2.5 (slams 0.8 / 1.6), p2_tot_throw 2.0 (throw 1.1),
          p2_stomp 1.8 (stomp 0.7, sprouts 1.4), p2_gravity 5.0 (falls 4.2)
          phase three: p3_knockback 2.5 (lands 0.7), p3_glare (beats glare.*), p3_glare_hold / p3_stalk / p3_carry /
          p3_carry_hold (loops), p3_snatch (snatch.*: the grab), p3_blackout 2.5 (the spotlight 1.5), p3_serenade
          (serenade.end: the song's opening, the hands moved by client/BossModel), p3_verdict, p3_toss, p3_dance (beats)
  boss "form" controller (phase two only): p2_sprout 3.5 (with p2_scream), p2_form_idle, p2_form_fly,
          p2_form_attack (loops), p2_form_lift 1.0, p2_form_aura 4.0
"""
import json
import math
import os

from gen_anims import guard_brows, EPS
from gen_npc_anims import Pinned, osc

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, 'src/main/resources/assets/papon/animations/entity')
SHOWN, HIDDEN = [1, 1, 1], [0, 0, 0]


def _load_beats():
    path = os.path.join(ROOT, 'src/main/resources/assets/papon/voice/boss2.json')
    try:
        return json.load(open(path)).get('beats', {})
    except (OSError, ValueError):
        return {}


VOICE_BEATS = _load_beats()


def beat(key, default):
    """A moment of the phase-two cutscene in SECONDS, timed to his voice (gen_boss_voice.beats, ticks in
    voice/boss2.json); `default` (ticks) without it. The Java reads the same numbers (VoiceLines.beat)."""
    return round(VOICE_BEATS.get(key, default) / 20.0, 2)


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
    pins = ['knife', 'tomato', 'brush', 'lettuce_held', 'chop_station', 'sink_station', 'pillow']


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


def g_carry():
    """0.8 s loop. Walking over with the body pillow hugged to his chest (the renderer puts it on the
    `pillow` bone), arms wrapped round it, head down, a little shy."""
    a = G('carry', 0.8, True)
    a.scale('pillow', [(0, SHOWN)], 'lin')
    a.rot('leg_r', [(0, [24, 0, 0]), (0.4, [-24, 0, 0]), (0.8, [24, 0, 0])])
    a.rot('leg_l', [(0, [-24, 0, 0]), (0.4, [24, 0, 0]), (0.8, [-24, 0, 0])])
    a.rot('arm_r', [(0, [-62, -38, 0])], 'lin')
    a.rot('arm_l', [(0, [-62, 38, 0])], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (0.2, [0, 0.6, 0]), (0.4, [0, 0, 0]), (0.6, [0, 0.6, 0]), (0.8, [0, 0, 0])])
    a.rot('root', [(0, [0, 0, -2]), (0.4, [0, 0, 2]), (0.8, [0, 0, -2])])
    a.rot('head', [(0, [14, 0, 0])], 'lin')
    a.rot('tail', [(0, [10, 16, 0]), (0.4, [10, -16, 0]), (0.8, [10, 16, 0])])
    a.pos('pillow', [(0, [0, 0, 0]), (0.2, [0, 0.4, 0]), (0.4, [0, 0, 0]), (0.6, [0, 0.4, 0]), (0.8, [0, 0, 0])])
    return a


def g_give():
    """3.0 s (GerbinState.GIVE). Holds the pillow out to you at arm's length with both hands, eyes on
    the floor, ears down (0.2-0.8); you take it (1.5 s: it leaves his hands); he twiddles his thumbs, a
    quick wave, and he is off."""
    a = G('give', 3.0, 'hold')
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-62, -38 * s, 0]), (0.6, [-96, -12 * s, 0], 'easeInOutSine'), (1.5, [-96, -12 * s, 0]),
                    (1.8, [-50, -30 * s, 0], 'easeInOutSine'), (2.0, [-56, -36 * s, 0]), (2.2, [-50, -30 * s, 0]),
                    (2.4, [-56, -36 * s, 0]), (3.0, [-10, 0, 4 * s], 'easeInOutSine')], 'lin')
    a.pos('pillow', [(0, [0, 0, 0]), (0.6, [0, 1.5, -4], 'easeInOutSine'), (1.45, [0, 1.5, -4]), (1.5, [0, 1.5, -9], 'easeOutQuad')], 'lin')
    a.scale('pillow', [(0, SHOWN), (1.5 - EPS, SHOWN), (1.5, HIDDEN), (3.0, HIDDEN)], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.6, [16, 0, 0], 'easeInOutSine'), (1.5, [16, 0, 0]), (1.9, [0, 0, 0]), (3.0, [0, 0, 0])])
    a.rot('head', [(0, [14, 0, 0]), (0.6, [30, -20, 0], 'easeInOutSine'), (1.5, [30, -20, 0]), (1.9, [6, 0, 0]), (3.0, [0, 0, 0])])
    for ear, s in (('ear_r', 1), ('ear_l', -1)):
        a.rot(ear, [(0, [0, 0, 0]), (0.5, [0, 0, 26 * s]), (1.9, [0, 0, 26 * s]), (2.2, [0, 0, 0])])
    a.rot('tail', [(0, [0, 0, 0]), (0.6, [20, 30, 0]), (1.2, [20, -30, 0]), (1.9, [0, 0, 0]), (3.0, [0, 0, 0])])
    a.sound(1.5, 'pick').sound(2.6, 'hey')
    return a


GERBIN = [g_idle, g_walk, g_chop, g_wash, g_look, g_shocked, g_cower, g_carry, g_give]


# ===================================================================== the boss

class B(Pinned):
    # the phase-two parts are pinned too: only the "form" controller (class F) shows them
    pins = ['bottle', 'handle', 'big_spatula', 'molotov', 'lighter', 'keys', 'sweat', 'tears', 'potato_gun',
            'blood', 'hand_blood', 'butter', 'fry_crown', 'wing_r', 'wing_l', 'giant_shaker', 'torch', 'salt_shaker',
            'comal', 'cleaver_r', 'cleaver_l', 'tortilla', 'brick', 'tot', 'lid_r', 'lid_l', 'p3_guitar', 'p3_guitar_carry']


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
    """0.45 s (BossState.CHARGE_WINDUP, 9 t). Head snaps down like a bull, one paw of the right foot,
    arms swept back: the tequila rush is coming, now."""
    a = angry(B('charge_windup', 0.45, 'hold'))
    a.rot('torso', [(0, [0, 0, 0]), (0.12, [34, 0, 0], 'easeOutExpo'), (0.45, [40, 0, 0])], 'lin')
    a.rot('leg_r', [(0, [0, 0, 0]), (0.1, [-30, 0, 0]), (0.25, [30, 0, 0], 'easeInQuad'), (0.45, [16, 0, 0])])
    a.rot('leg_l', [(0, [0, 0, 0]), (0.45, [-16, 0, 0])])
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-20, 0, 24 * s]), (0.12, [56, 0, 26 * s], 'easeOutExpo'), (0.45, [62, 0, 22 * s])], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (0.12, [0, -2, 0]), (0.45, [0, -2.5, 0])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (0.12, [1.08, 0.92, 1.08]), (0.45, [1.12, 0.88, 1.12])], 'lin')
    a.sound(0.25, 'step').sound(0.3, 'roar_small')
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


# ===================================================================== the rework: defence, chef, drunk, QTE

GUARD_R = [-100, -22, 0]        # both fists up in front of his face, crossed at the wrists:
GUARD_L = [-100, 22, 0]         # the cleavers make an X he looks at you through
GUARD_BLADE_R = [-140, 0, 115]  # solved on the rig (GeckoLib: X and Z flipped): upright, crossing, flat to you
GUARD_BLADE_L = [-140, 0, -115]


def b_guard():
    """1.2 s, held (BossState.GUARD, AI-held up to 24 t). The parry: both cleavers whipped up crossed in
    front of his face (0.12 s), an X he glares through, blades grinding; hit it and he answers at once."""
    a = angry(B('guard', 1.2, 'hold'))
    for c in ('cleaver_r', 'cleaver_l'):
        a.scale(c, [(0, SHOWN)], 'lin')
    a.rot('arm_r', [(0, [-20, 0, 24]), (0.12, GUARD_R, 'easeOutBack')]
          + [(round(0.2 + 0.1 * i, 2), [GUARD_R[0] + (2 if i % 2 else -2), GUARD_R[1], 0]) for i in range(10)], 'lin')
    a.rot('arm_l', [(0, [-20, 0, -24]), (0.12, GUARD_L, 'easeOutBack')]
          + [(round(0.2 + 0.1 * i, 2), [GUARD_L[0] + (-2 if i % 2 else 2), GUARD_L[1], 0]) for i in range(10)], 'lin')
    stretch(a, 'arm_r', [(0, 1), (0.12, 2.0)])          # the fists in front of his face, crossed
    stretch(a, 'arm_l', [(0, 1), (0.12, 2.0)])
    a.rot('torso', [(0, [0, 0, 0]), (0.12, [12, 0, 0], 'easeOutQuad'), (1.2, [12, 0, 0])], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (0.12, [0, -2, 0], 'easeOutQuad'), (1.2, [0, -2, 0])], 'lin')
    for leg, s in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [0, 0, 0]), (0.12, [-24 * s, 0, 8 * s]), (1.2, [-24 * s, 0, 8 * s])], 'lin')
    # the blades turned up off the fists and flat to you, leaning in to cross
    a.rot('cleaver_r', [(0, [0, 0, 0]), (0.12, GUARD_BLADE_R, 'easeOutBack'), (1.2, GUARD_BLADE_R)], 'lin')
    a.rot('cleaver_l', [(0, [0, 0, 0]), (0.12, GUARD_BLADE_L, 'easeOutBack'), (1.2, GUARD_BLADE_L)], 'lin')
    a.sound(0.08, 'clank')
    return a


def b_riposte():
    """0.9 s (BossState.RIPOSTE, 18 t). Off the parry: the crossed cleavers shoved into you (0.25 s),
    then scissored apart, both blades out and down across you (0.6 s)."""
    a = angry(B('riposte', 0.9, 'hold'))
    for c in ('cleaver_r', 'cleaver_l'):
        a.scale(c, [(0, SHOWN)], 'lin')
    a.rot('cleaver_r', [(0, GUARD_BLADE_R), (0.25, [0, 0, 0], 'easeInExpo')], 'lin')
    a.rot('cleaver_l', [(0, GUARD_BLADE_L), (0.25, [0, 0, 0], 'easeInExpo')], 'lin')
    a.rot('arm_r', [(0, GUARD_R), (0.25, [-96, -30, 0], 'easeInExpo'), (0.4, [-150, -20, 10], 'easeOutQuad'),
                    (0.6, [-50, 50, 50], 'easeInExpo'), (0.9, [-20, 0, 24], 'easeInOutSine')], 'lin')
    a.rot('arm_l', [(0, GUARD_L), (0.25, [-96, 30, 0], 'easeInExpo'), (0.4, [-150, 20, -10], 'easeOutQuad'),
                    (0.6, [-50, -50, -50], 'easeInExpo'), (0.9, [-20, 0, -24], 'easeInOutSine')], 'lin')
    for arm in ('arm_r', 'arm_l'):
        stretch(a, arm, [(0, 2.2), (0.25, 2.4, 'easeInExpo'), (0.4, 1.4), (0.6, 1.8, 'easeInExpo'), (0.9, 1)])
    a.rot('torso', [(0, [12, 0, 0]), (0.25, [20, 0, 0], 'easeInExpo'), (0.4, [-16, 0, 0]), (0.6, [30, 0, 0], 'easeInExpo'),
                    (0.9, [6, 0, 0])], 'lin')
    a.pos('root', [(0, [0, -2, 0]), (0.25, [0, -1, -3], 'easeInExpo'), (0.6, [0, -2, -2], 'easeInExpo'), (0.9, [0, 0, 0])], 'lin')
    a.sound(0.22, 'clank').sound(0.5, 'swing').sound(0.6, 'slam')
    return a


def b_evade():
    """0.5 s (BossState.EVADE, 10 t). A drunk's sidestep: a quick hop, leaning away, arms flung out.
    The hop itself is the entity's motion; the next attack comes straight after."""
    a = angry(B('evade', 0.5, 'hold'))
    a.rot('root', [(0, [0, 0, 0]), (0.1, [-10, 0, 20], 'easeOutQuad'), (0.35, [-6, 0, 14]), (0.5, [0, 0, 0], 'easeInOutSine')], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (0.08, [0, -2, 0]), (0.2, [0, 3, 0], 'easeOutQuad'), (0.35, [0, 0, 0], 'easeInQuad'),
                   (0.42, [0, -1.5, 0]), (0.5, [0, 0, 0])], 'lin')
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-20, 0, 24 * s]), (0.15, [-40, 0, 90 * s], 'easeOutQuad'), (0.5, [-20, 0, 30 * s])], 'lin')
    for leg, s in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [0, 0, 0]), (0.15, [-20 * s, 0, 14 * s]), (0.35, [10 * s, 0, 6 * s]), (0.5, [0, 0, 0])])
    a.scale('body', [(0, [1, 1, 1]), (0.08, [1.12, 0.88, 1.12]), (0.2, [0.92, 1.1, 0.92]), (0.42, [1.08, 0.94, 1.08]),
                     (0.5, [1, 1, 1])], 'lin')
    a.sound(0.05, 'whoosh').sound(0.4, 'step')
    return a


def b_cleaver_toss():
    """1.5 s (BossState.CLEAVER_TOSS, 30 t). Los Tres Chefs: both cleavers out and crossed high over his
    head (0.15 s), clashed together three times as his echoes blink in (0.1 / 0.4 / 0.7 s), then
    pointed at you (1.0 s), crouched to come after them."""
    a = angry(B('cleaver_toss', 1.5, 'hold'))
    for c in ('cleaver_r', 'cleaver_l'):
        a.scale(c, [(0, SHOWN)], 'lin')
    up_r, up_l = [-165, -20, 10], [-165, 20, -10]
    clash_r, clash_l = [-170, -32, 4], [-170, 32, -4]
    keys_r = [(0, [-20, 0, 24]), (0.15, up_r, 'easeOutBack')]
    keys_l = [(0, [-20, 0, -24]), (0.15, up_l, 'easeOutBack')]
    for t in (0.1, 0.4, 0.7):
        keys_r += [(round(t + 0.08, 2), clash_r, 'easeInExpo'), (round(t + 0.2, 2), up_r, 'easeOutQuad')]
        keys_l += [(round(t + 0.08, 2), clash_l, 'easeInExpo'), (round(t + 0.2, 2), up_l, 'easeOutQuad')]
    keys_r = [k for k in keys_r if k[0] >= 0.15 or k[0] == 0]
    keys_l = [k for k in keys_l if k[0] >= 0.15 or k[0] == 0]
    keys_r += [(1.0, [-90, 10, 20], 'easeInExpo'), (1.5, [-70, 0, 24], 'easeInOutSine')]
    keys_l += [(1.0, [-90, -10, -20], 'easeInExpo'), (1.5, [-70, 0, -24], 'easeInOutSine')]
    a.rot('arm_r', keys_r, 'lin')
    a.rot('arm_l', keys_l, 'lin')
    stretch(a, 'arm_r', [(0, 1), (0.15, 1.3), (0.9, 1.3), (1.0, 1.5, 'easeInExpo'), (1.5, 1.1)])
    stretch(a, 'arm_l', [(0, 1), (0.15, 1.3), (0.9, 1.3), (1.0, 1.5, 'easeInExpo'), (1.5, 1.1)])
    a.rot('torso', [(0, [0, 0, 0]), (0.15, [-14, 0, 0], 'easeOutQuad'), (0.9, [-14, 0, 0]), (1.0, [22, 0, 0], 'easeInExpo'),
                    (1.5, [16, 0, 0])], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (0.15, [0, 1, 0]), (0.9, [0, 1, 0]), (1.0, [0, -2.5, -1], 'easeInExpo'), (1.5, [0, -2, 0])], 'lin')
    for leg, sg in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [0, 0, 0]), (0.9, [0, 0, 4 * sg]), (1.0, [-26 * sg, 0, 10 * sg]), (1.5, [-24 * sg, 0, 8 * sg])], 'lin')
    for t in (0.18, 0.48, 0.78):
        a.sound(t, 'clank')
    a.sound(0.95, 'roar_small')
    return a


def b_echo_throw():
    """2.2 s (PaponEchoEntity.LIFE, 44 t; the echo plays it on his model). Blinks in crouched, the
    right cleaver cocked back over his shoulder (0.3-0.75 s), hurled at 0.8 s (THROW_1, 16 t), then the
    left (cocked 0.95-1.35 s, hurled 1.4 s, THROW_2, 28 t), then drifts back as he fades."""
    a = angry(B('echo_throw', 2.2, 'hold'))
    a.vis('cleaver_r', True, [(0.8, False)])
    a.vis('cleaver_l', True, [(1.4, False)])
    a.rot('arm_r', [(0, [-20, 0, 24]), (0.3, [-200, 0, 30], 'easeOutQuad'), (0.75, [-215, 0, 34]),
                    (0.8, [-70, -20, 10], 'easeInExpo'), (1.0, [-50, -10, 20]), (2.2, [-20, 0, 24], 'easeInOutSine')], 'lin')
    stretch(a, 'arm_r', [(0, 1), (0.3, 1.2), (0.75, 1.25), (0.8, 1.7, 'easeInExpo'), (1.0, 1)])
    a.rot('arm_l', [(0, [-20, 0, -24]), (0.95, [-30, 0, -30]), (1.3, [-200, 0, -30], 'easeOutQuad'), (1.35, [-215, 0, -34]),
                    (1.4, [-70, 20, -10], 'easeInExpo'), (1.6, [-50, 10, -20]), (2.2, [-20, 0, -24], 'easeInOutSine')], 'lin')
    stretch(a, 'arm_l', [(0, 1), (1.3, 1.2), (1.35, 1.25), (1.4, 1.7, 'easeInExpo'), (1.6, 1)])
    a.rot('torso', [(0, [10, 0, 0]), (0.3, [-18, 20, 0], 'easeOutQuad'), (0.75, [-22, 26, 0]), (0.8, [26, -24, 0], 'easeInExpo'),
                    (1.0, [8, 0, 0]), (1.3, [-18, -20, 0], 'easeOutQuad'), (1.35, [-22, -26, 0]), (1.4, [26, 24, 0], 'easeInExpo'),
                    (1.6, [8, 0, 0]), (2.2, [0, 0, 0])], 'lin')
    for leg, sg in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [-20 * sg, 0, 6 * sg]), (2.2, [-10 * sg, 0, 4 * sg])], 'lin')
    a.pos('root', [(0, [0, -3, 0]), (0.3, [0, 0, 0], 'easeOutBack'), (1.8, [0, 0, 0]), (2.2, [0, 2, 4], 'easeInQuad')], 'lin')
    a.sound(0.78, 'swing').sound(1.38, 'swing')
    return a


def b_burrito_toss():
    """1.8 s (BossState.BURRITO_TOSS, 36 t). A giant tortilla up over his head, spun like pizza dough
    (0.2-0.9 s), then flung flat at you (1.0 s): where it lands, whoever is under it is a burrito."""
    a = angry(B('burrito_toss', 1.8, 'hold'))
    a.vis('tortilla', False, [(0.15, True), (1.0, False)])
    a.rot('arm_r', [(0, [-20, 0, 24]), (0.2, [-176, 0, 10], 'easeOutBack'), (0.9, [-176, 0, 10]),
                    (1.0, [-80, 0, 10], 'easeInExpo'), (1.2, [-60, 0, 20]), (1.8, [-20, 0, 24], 'easeInOutSine')], 'lin')
    stretch(a, 'arm_r', [(0, 1), (0.2, 1.4), (0.9, 1.4), (1.0, 1.7, 'easeInExpo'), (1.4, 1)])
    a.rot('tortilla', [(0, [0, 0, 0]), (0.2, [0, 0, 0]), (0.9, [0, -1080, 0], 'easeInQuad')], 'lin')
    a.rot('arm_l', [(0, [-20, 0, -24]), (0.2, [-150, 0, -40]), (0.9, [-150, 0, -40]), (1.0, [-40, 0, -50]), (1.8, [-20, 0, -24])])
    a.rot('torso', [(0, [0, 0, 0]), (0.2, [-14, 0, 0]), (0.9, [-18, 0, 0]), (1.0, [24, 0, 0], 'easeInExpo'), (1.8, [0, 0, 0])], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (0.9, [0, 0, 0]), (1.0, [0, -1.5, -1], 'easeInExpo'), (1.8, [0, 0, 0])], 'lin')
    a.sound(0.2, 'cloth').sound(0.4, 'whoosh').sound(0.7, 'whoosh').sound(0.98, 'swing')
    return a


def b_drunk_swig():
    """0.7 s (BossState.DRUNK_SWIG, 14 t). THE TELL for the drunken flurry: a fast swig off the bottle
    (0.1-0.5 s), tossed, eyes going wide. What follows cannot be stopped."""
    a = angry(B('drunk_swig', 0.7, 'hold'))
    a.vis('bottle', False, [(0.05, True), (0.55, False)])
    a.scale('bottle_liquid', [(0, [1, 0.6, 1]), (0.15, [1, 0.6, 1]), (0.45, [1, 0, 1])], 'lin')
    a.rot('arm_r', [(0, [-20, 0, 24]), (0.12, DRINK_R, 'easeOutQuad'), (0.45, DRINK_R), (0.55, [-190, 0, 30], 'easeOutQuad'),
                    (0.7, [-40, 0, 40])], 'lin')
    stretch(a, 'arm_r', [(0, 1), (0.12, 1.6), (0.45, 1.6), (0.55, 1)])
    a.rot('bottle', [(0, [0, 0, 0]), (0.12, BOTTLE_UP), (0.45, BOTTLE_UP)], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.15, [-30, 0, 0], 'easeOutQuad'), (0.45, [-34, 0, 0]), (0.6, [20, 0, 0], 'easeOutBack'),
                    (0.7, [14, 0, 0])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (0.45, [1.06, 1.02, 1.06]), (0.6, [1.18, 0.9, 1.18], 'easeOutExpo'), (0.7, [1.06, 1, 1.06])], 'lin')
    a.scale('blush', [(0, [1.1, 1.6, 1]), (0.6, [1.2, 2.6, 1])], 'lin')
    a.sound(0.15, 'glug').sound(0.35, 'glug').sound(0.55, 'smash').sound(0.62, 'roar_small')
    return a


def b_drunk_flurry():
    """3.0 s (BossState.DRUNK_FLURRY, 60 t). Plastered and unstoppable: five wild, lurching haymakers,
    alternating fists, each a lunge (hits 0.5 / 0.85 / 1.2 / 1.55 / 1.9 s), then a spinning
    double-fisted hammer (2.5 s). He lists and staggers between them, which is the only rhythm."""
    a = angry(B('drunk_flurry', 3.0, 'hold'))
    hits = [0.5, 0.85, 1.2, 1.55, 1.9]
    r, l, tor, root = [(0, [-40, 0, 40])], [(0, [-20, 0, -30])], [(0, [14, 0, 0])], [(0, [0, 0, 0])]
    sr, sl = [(0, 1)], [(0, 1)]
    for i, t in enumerate(hits):
        right = i % 2 == 0
        wind = round(t - 0.15, 2)
        if right:
            r += [(wind, [20, 30, 60], 'easeOutQuad'), (t, [-96, -40, 10], 'easeInExpo'), (round(t + 0.1, 2), [-80, -60, 0])]
            sr += [(wind, 1), (t, 1.9, 'easeInExpo'), (round(t + 0.12, 2), 1.2)]
            l += [(wind, [-60, 0, -60]), (t, [10, 0, -40])]
        else:
            l += [(wind, [20, -30, -60], 'easeOutQuad'), (t, [-96, 40, -10], 'easeInExpo'), (round(t + 0.1, 2), [-80, 60, 0])]
            sl += [(wind, 1), (t, 1.9, 'easeInExpo'), (round(t + 0.12, 2), 1.2)]
            r += [(wind, [-60, 0, 60]), (t, [10, 0, 40])]
        tor += [(wind, [-6, 40 if right else -40, 10 if right else -10]), (t, [24, -40 if right else 40, -8 if right else 8], 'easeInExpo')]
        root += [(wind, [0, 0, 0]), (t, [0, 0, -2.5], 'easeInExpo'), (round(t + 0.15, 2), [0, 0, 0])]
        a.sound(wind, 'hiccup' if i == 2 else 'grunt').sound(round(t - 0.02, 2), 'swing')
    r += [(2.2, [-200, 0, 30], 'easeOutQuad'), (2.5, [-60, 0, 0], 'easeInExpo'), (3.0, [-20, 0, 24], 'easeInOutSine')]
    l += [(2.2, [-200, 0, -30], 'easeOutQuad'), (2.5, [-60, 0, 0], 'easeInExpo'), (3.0, [-20, 0, -24], 'easeInOutSine')]
    sr += [(2.2, 1.4), (2.5, 1.8, 'easeInExpo'), (3.0, 1)]
    sl += [(2.2, 1.4), (2.5, 1.8, 'easeInExpo'), (3.0, 1)]
    tor += [(2.2, [-24, 0, 0]), (2.5, [40, 0, 0], 'easeInExpo'), (3.0, [0, 0, 0])]
    a.rot('arm_r', r, 'lin')
    a.rot('arm_l', l, 'lin')
    stretch(a, 'arm_r', sr)
    stretch(a, 'arm_l', sl)
    a.rot('torso', tor, 'lin')
    a.pos('torso', root, 'lin')
    a.rot('root', [(0, [0, 0, -6]), (0.7, [0, 0, 8]), (1.4, [0, 0, -8]), (2.0, [0, 0, 6]), (2.2, [0, 0, 0]),
                   (2.5, [0, 360, 0], 'easeInQuad'), (2.5 + EPS, [0, 0, 0], 'lin'), (3.0, [0, 0, 0])], 'lin')
    a.scale('body', [(0, [1.06, 1, 1.06]), (2.2, [0.94, 1.1, 0.94]), (2.5, [1.25, 0.8, 1.25], 'easeInExpo'), (3.0, [1, 1, 1])], 'lin')
    a.scale('blush', [(0, [1.2, 2.6, 1])], 'lin')
    a.sound(2.45, 'swing').sound(2.5, 'slam')
    return a


def b_haymaker():
    """2.5 s (BossState.HAYMAKER, 50 t). The feint: he wobbles, nearly topples, hiccups, looks done for
    (0-1.4 s); then his eye catches the light (1.5 s), he winds his right all the way back (1.75 s)
    and lunges eight blocks with a haymaker that sends you flying (1.8 s). Then he falls on his face."""
    a = angry(B('haymaker', 2.5, 'hold'))
    a.rot('root', [(0, [0, 0, 0]), (0.3, [10, 0, 16]), (0.6, [-6, 0, -14]), (0.9, [16, 0, 10]), (1.2, [4, 0, -18]),
                   (1.45, [0, 0, 0]), (1.75, [-8, 30, 0], 'easeOutQuad'), (1.8, [24, -20, 0], 'easeInExpo'),
                   (2.1, [60, -10, 0], 'easeInQuad'), (2.3, [80, 0, 0], 'easeOutBounce'), (2.5, [70, 0, 0])], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (1.45, [0, 0, 0]), (1.75, [0, -2, 2]), (1.8, [0, 0, -4], 'easeInExpo'),
                   (2.3, [0, -6, -6], 'easeOutBounce'), (2.5, [0, -6, -6])], 'lin')
    a.rot('arm_r', [(round(0.25 * i, 2), [-140 if i % 2 else 10, 0, 60]) for i in range(6)]
          + [(1.45, [-20, 0, 30]), (1.75, [40, 50, 70], 'easeOutQuad'), (1.8, [-100, -30, 10], 'easeInExpo'),
             (2.5, [-170, 0, 20])], 'lin')
    stretch(a, 'arm_r', [(0, 1), (1.75, 1), (1.8, 2.2, 'easeInExpo'), (2.0, 1.8), (2.5, 1.4)])
    a.rot('arm_l', [(round(0.25 * i, 2), [10 if i % 2 else -140, 0, -60]) for i in range(6)]
          + [(1.45, [-20, 0, -30]), (1.75, [-80, 0, -60]), (1.8, [20, 0, -40]), (2.5, [-170, 0, -20])], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (1.45, [0, 0, 0]), (1.75, [-10, 50, 0], 'easeOutQuad'), (1.8, [26, -40, 0], 'easeInExpo'),
                    (2.5, [10, 0, 0])], 'lin')
    for leg, s in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(round(0.3 * i, 2), [24 * s if i % 2 else -24 * s, 0, 8 * s]) for i in range(5)]
              + [(1.45, [0, 0, 0]), (1.75, [-30 * s, 0, 0]), (1.8, [40 * s, 0, 0]), (2.5, [10, 0, 0])])
    for brow, s in (('brow_r', 1), ('brow_l', -1)):
        a.pos(brow, [(0, [0, 1.0, 0]), (1.45, [0, 1.0, 0]), (1.5, [0, -1.0, 0], 'easeOutExpo')])
        a.rot(brow, [(0, [0, 0, -10 * s]), (1.45, [0, 0, -10 * s]), (1.5, [0, 0, 24 * s], 'easeOutExpo')])
    a.scale('blush', [(0, [1.2, 2.6, 1])], 'lin')
    a.sound(0.4, 'hiccup').sound(1.0, 'hiccup').sound(1.5, 'ding').sound(1.7, 'roar_small').sound(1.78, 'swing').sound(2.3, 'thud')
    return a


def b_comal_slam():
    """2.0 s (BossState.COMAL_SLAM, 40 t). The comal raised in both hands over his head (0.8 s), and
    slammed flat on the ground (0.9 s): the ground around him becomes a hot griddle."""
    a = angry(B('comal_slam', 2.0, 'hold'))
    a.scale('comal', [(0, SHOWN)], 'lin')
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-20, 0, 24 * s]), (0.7, [-180, -10 * s, 10 * s], 'easeOutBack'), (0.8, [-190, -10 * s, 8 * s]),
                    (0.9, [-60, 0, 10 * s], 'easeInExpo'), (1.5, [-60, 0, 10 * s]), (2.0, [-20, 0, 24 * s], 'easeInOutSine')], 'lin')
        stretch(a, arm, [(0, 1), (0.7, 1.3), (0.9, 1.9, 'easeInExpo'), (1.5, 1.9), (2.0, 1)])
    a.rot('comal', [(0, [0, 0, 0]), (0.7, [0, 0, -30]), (0.9, [0, 0, 0])], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.7, [-24, 0, 0], 'easeOutQuad'), (0.9, [48, 0, 0], 'easeInExpo'), (1.5, [44, 0, 0]),
                    (2.0, [0, 0, 0], 'easeInOutSine')], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (0.7, [0, 1.5, 0]), (0.9, [0, -3, 0], 'easeInExpo'), (1.5, [0, -3, 0]), (2.0, [0, 0, 0])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (0.7, [0.92, 1.12, 0.92]), (0.9, [1.25, 0.8, 1.25], 'easeInExpo'), (1.3, [1, 1, 1])], 'lin')
    a.sound(0.3, 'grunt').sound(0.88, 'clank').sound(0.9, 'slam').sound(1.1, 'sizzle')
    return a


def b_brick():
    """1.8 s (BossState.BRICK, 36 t). At the van's window with a brick: leans in (0.3-1.0 s), wedges it
    on the gas pedal (1.0 s), steps back and slaps the door. The van goes for you on its own."""
    a = angry(B('brick', 1.8, 'hold'))
    a.vis('brick', False, [(0.15, True), (1.0, False)])
    a.rot('arm_l', [(0, [-20, 0, -24]), (0.3, [-60, 20, -10]), (0.9, [-100, 30, 0], 'easeInOutSine'),
                    (1.0, [-60, 20, 0], 'easeInExpo'), (1.3, [-20, 0, -30]), (1.8, [-20, 0, -24])], 'lin')
    stretch(a, 'arm_l', [(0, 1), (0.3, 1.2), (0.9, 1.8), (1.0, 1.4), (1.3, 1)])
    a.rot('arm_r', [(0, [-20, 0, 24]), (1.2, [-20, 0, 24]), (1.4, [-90, -40, 20], 'easeOutQuad'), (1.5, [-70, 20, 30]),
                    (1.8, [-20, 0, 24])], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.3, [10, 50, 0]), (0.9, [40, 60, 0], 'easeInOutSine'), (1.2, [0, 20, 0]),
                    (1.8, [0, 0, 0])], 'lin')
    a.sound(0.2, 'cloth').sound(1.0, 'thud').sound(1.45, 'slam').sound(1.5, 'chuckle')
    return a


# ---- the QTEs: he has you, held in front of him; the camera is on him and the world slows down
QTE_HOLD_R = [-100, -14, 6]


def qte_hold(name, prop=None, liquid=None):
    """1.0 s loop. Holding you up at arm's length in his right fist, glaring, the threat in his left."""
    a = angry(B(name, 1.0, True))
    a.rot('arm_r', [(0, QTE_HOLD_R), (0.5, [QTE_HOLD_R[0] - 4, -14, 6]), (1.0, QTE_HOLD_R)])
    stretch(a, 'arm_r', [(0, 1.6)])
    a.rot('torso', [(0, [-8, 0, 0]), (0.5, [-10, 0, 0]), (1.0, [-8, 0, 0])])
    if prop:
        a.scale(prop, [(0, SHOWN)], 'lin')
    if liquid:
        a.scale(liquid, [(0, SHOWN)], 'lin')
    return a


def b_qte_hold_cleaver():
    """1.0 s loop (QTE: La Última Cena). The cleaver raised high behind his head, trembling."""
    a = qte_hold('qte_hold_cleaver', 'cleaver_l')
    a.rot('arm_l', [(0, [-196, 20, -30]), (0.5, [-200, 20, -30]), (1.0, [-196, 20, -30])])
    stretch(a, 'arm_l', [(0, 1.3)])
    return a


def b_qte_hold_bottle():
    """1.0 s loop (QTE: Last Call). The handle tipped toward your mouth, sloshing."""
    a = qte_hold('qte_hold_bottle', 'handle', 'handle_liquid')
    a.rot('arm_l', [(0, [-110, 30, -10]), (0.5, [-116, 28, -10]), (1.0, [-110, 30, -10])])
    stretch(a, 'arm_l', [(0, 1.5)])
    a.rot('handle', [(0, [-60, 0, 0]), (0.5, [-70, 0, 0]), (1.0, [-60, 0, 0])])
    return a


def b_qte_hold_suplex():
    """1.0 s loop (QTE: La Papa Suplex). Both fists under you, lifted to his chest, knees bent."""
    a = qte_hold('qte_hold_suplex')
    a.rot('arm_l', [(0, [-100, 14, -6]), (0.5, [-104, 14, -6]), (1.0, [-100, 14, -6])])
    stretch(a, 'arm_l', [(0, 1.6)])
    a.pos('root', [(0, [0, -2, 0]), (0.5, [0, -2.5, 0]), (1.0, [0, -2, 0])])
    for leg, s in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [-30 * s, 0, 10 * s])], 'lin')
    return a


def b_qte_fail_cleaver():
    """2.0 s (QTE failed: La Última Cena). The cleaver comes down on you (1.2 s), hard."""
    a = angry(B('qte_fail_cleaver', 2.0, 'hold'))
    a.scale('cleaver_l', [(0, SHOWN)], 'lin')
    a.rot('arm_r', [(0, QTE_HOLD_R), (1.3, QTE_HOLD_R), (1.5, [-40, 0, 30], 'easeOutQuad'), (2.0, [-20, 0, 24])], 'lin')
    stretch(a, 'arm_r', [(0, 1.6), (1.3, 1.6), (1.5, 1)])
    a.rot('arm_l', [(0, [-196, 20, -30]), (1.0, [-210, 20, -30], 'easeInOutSine'), (1.2, [-80, 10, -10], 'easeInExpo'),
                    (1.5, [-60, 0, -20]), (2.0, [-20, 0, -24])], 'lin')
    stretch(a, 'arm_l', [(0, 1.3), (1.0, 1.4), (1.2, 1.9, 'easeInExpo'), (1.6, 1)])
    a.rot('torso', [(0, [-8, 0, 0]), (1.0, [-24, 0, 0]), (1.2, [30, 0, 0], 'easeInExpo'), (2.0, [0, 0, 0])], 'lin')
    a.sound(0.9, 'roar_small').sound(1.18, 'swing').sound(1.2, 'chop')
    return a


def b_qte_fail_bottle():
    """2.0 s (QTE failed: Last Call). The handle jammed in your mouth and emptied (0.4-1.3 s), then a
    headbutt sends you off (1.4 s)."""
    a = angry(B('qte_fail_bottle', 2.0, 'hold'))
    a.vis('handle', True, [(1.35, False)])
    a.scale('handle_liquid', [(0, SHOWN), (0.4, SHOWN), (1.3, [1, 0, 1])], 'lin')
    a.rot('arm_r', [(0, QTE_HOLD_R), (1.4, QTE_HOLD_R), (1.6, [-40, 0, 30]), (2.0, [-20, 0, 24])], 'lin')
    stretch(a, 'arm_r', [(0, 1.6), (1.4, 1.6), (1.6, 1)])
    a.rot('arm_l', [(0, [-110, 30, -10]), (0.4, [-120, 20, 0], 'easeOutQuad'), (1.3, [-120, 20, 0]), (1.5, [-20, 0, -40]),
                    (2.0, [-20, 0, -24])], 'lin')
    stretch(a, 'arm_l', [(0, 1.5), (0.4, 1.7), (1.3, 1.7), (1.5, 1)])
    a.rot('handle', [(0, [-60, 0, 0]), (0.4, [-110, 0, 0]), (1.3, [-110, 0, 0])], 'lin')
    a.rot('torso', [(0, [-8, 0, 0]), (1.3, [-20, 0, 0]), (1.4, [30, 0, 0], 'easeInExpo'), (2.0, [0, 0, 0])], 'lin')
    for i in range(4):
        a.sound(round(0.5 + 0.22 * i, 2), 'glug')
    a.sound(1.38, 'thud').sound(1.5, 'chuckle')
    return a


def b_qte_fail_suplex():
    """2.0 s (QTE failed: La Papa Suplex). Up over his head (0.3-0.9 s), and he falls back with you,
    driving you into the floor behind him (1.2 s)."""
    a = angry(B('qte_fail_suplex', 2.0, 'hold'))
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-100, -14 * s, 6 * s]), (0.6, [-180, 0, 10 * s], 'easeInOutSine'), (0.9, [-200, 0, 10 * s]),
                    (1.2, [-220, 0, 10 * s], 'easeInExpo'), (1.6, [-200, 0, 10 * s]), (2.0, [-20, 0, 24 * s], 'easeInOutSine')], 'lin')
        stretch(a, arm, [(0, 1.6), (0.6, 1.5), (1.2, 1.6), (2.0, 1)])
    a.rot('root', [(0, [0, 0, 0]), (0.9, [-10, 0, 0]), (1.2, [-80, 0, 0], 'easeInExpo'), (1.6, [-80, 0, 0]),
                   (2.0, [0, 0, 0], 'easeInOutSine')], 'lin')
    a.pos('root', [(0, [0, -2, 0]), (0.6, [0, 0, 0]), (1.2, [0, -4, 4], 'easeInExpo'), (1.6, [0, -4, 4]), (2.0, [0, 0, 0])], 'lin')
    a.sound(0.4, 'grunt').sound(0.9, 'roar_small').sound(1.2, 'slam')
    return a


def b_qte_broken():
    """4.0 s (BossState.QTE_BROKEN, 80 t). You broke free: he goes over backwards (0.3 s), flat on his
    back seeing stars, legs kicking (wide open, he takes double damage), and only gets up at 3.3 s."""
    a = B('qte_broken', 4.0, 'hold')
    a.rot('root', [(0, [0, 0, 0]), (0.3, [-90, 0, 0], 'easeOutBounce'), (3.2, [-90, 0, 0]), (3.6, [-30, 0, 0], 'easeInOutSine'),
                   (4.0, [0, 0, 0])], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (0.3, [0, -6, 6], 'easeOutBounce'), (3.2, [0, -6, 6]), (4.0, [0, 0, 0], 'easeInOutSine')], 'lin')
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-100, 0, 30 * s]), (0.3, [-20, 0, 80 * s]), (1.5, [-30, 0, 90 * s]), (3.2, [-20, 0, 80 * s]),
                    (4.0, [-20, 0, 24 * s])])
    for leg, s in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(round(0.3 + 0.25 * i, 2), [(-40 if (i + (s > 0)) % 2 else -10), 0, 10 * s]) for i in range(12)]
              + [(4.0, [0, 0, 0])])
    for brow, s in (('brow_r', 1), ('brow_l', -1)):
        a.pos(brow, [(0, [0, 1.2, 0]), (4.0, [0, 0.8, 0])])
        a.rot(brow, [(0, [0, 0, -14 * s]), (4.0, [0, 0, -8 * s])])
    a.scale('sweat', [(0, SHOWN)], 'lin')
    a.sound(0.0, 'yelp').sound(0.3, 'thud').sound(1.2, 'grunt').sound(3.4, 'grunt')
    return a


# ---- phase two: potato attacks
def b_p2_fry_volley():
    """3.0 s (P2_FRY_VOLLEY, 60 t). Arms thrown forward, commanding: the fries leave his wings one by
    one (the wings fan out on the form controller); they hang aimed and fire from 1.0 to 2.2 s."""
    a = angry(B('p2_fry_volley', 3.0, 'hold'))
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-20, 0, 24 * s]), (0.4, [-60, 0, 100 * s], 'easeOutBack'), (0.9, [-60, 0, 100 * s]),
                    (1.0, [-92, -20 * s, 10 * s], 'easeInExpo')]
              + [(round(1.0 + 0.2 * i, 2), [-92 + (6 if i % 2 else 0), -20 * s, 10 * s]) for i in range(7)]
              + [(3.0, [-30, 0, 30 * s], 'easeInOutSine')], 'lin')
        stretch(a, arm, [(0, 1), (0.4, 1.3), (1.0, 1.6, 'easeInExpo'), (2.4, 1.6), (3.0, 1)])
    a.rot('torso', [(0, [0, 0, 0]), (0.4, [-20, 0, 0]), (1.0, [14, 0, 0], 'easeInExpo'), (2.4, [10, 0, 0]), (3.0, [0, 0, 0])], 'lin')
    a.sound(0.4, 'roar_small')
    return a


def b_p2_mash_slam():
    """2.5 s (P2_MASH, 50 t). Down out of the air onto both fists (0.8 s) and again (1.6 s): each slam
    sends a ring of mashed potato rolling out across the floor. Jump it."""
    a = angry(B('p2_mash_slam', 2.5, 'hold'))
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-20, 0, 24 * s]), (0.6, [-190, 0, 10 * s], 'easeOutBack'), (0.8, [-50, 0, 10 * s], 'easeInExpo'),
                    (1.3, [-190, 0, 10 * s], 'easeInOutSine'), (1.6, [-50, 0, 10 * s], 'easeInExpo'),
                    (2.5, [-20, 0, 24 * s], 'easeInOutSine')], 'lin')
        stretch(a, arm, [(0, 1), (0.6, 1.4), (0.8, 1.9, 'easeInExpo'), (1.3, 1.4), (1.6, 1.9, 'easeInExpo'), (2.5, 1)])
    a.rot('torso', [(0, [0, 0, 0]), (0.6, [-24, 0, 0]), (0.8, [44, 0, 0], 'easeInExpo'), (1.3, [-24, 0, 0]),
                    (1.6, [44, 0, 0], 'easeInExpo'), (2.5, [0, 0, 0])], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (0.6, [0, 2, 0]), (0.8, [0, -3, 0], 'easeInExpo'), (1.3, [0, 1, 0]), (1.6, [0, -3, 0], 'easeInExpo'),
                   (2.5, [0, 0, 0])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (0.8, [1.25, 0.8, 1.25], 'easeInExpo'), (1.1, [1, 1, 1]), (1.6, [1.25, 0.8, 1.25], 'easeInExpo'),
                     (2.0, [1, 1, 1])], 'lin')
    a.sound(0.5, 'roar_small').sound(0.8, 'slam').sound(1.6, 'slam')
    return a


def b_p2_tot_throw():
    """2.0 s (P2_TOT, 40 t). A tater tot the size of his head, wound up overhand and lobbed (1.1 s)."""
    a = angry(B('p2_tot_throw', 2.0, 'hold'))
    a.vis('tot', False, [(0.2, True), (1.12, False)])
    a.rot('arm_r', [(0, [-20, 0, 24]), (0.3, [-40, 0, 30]), (0.9, [-210, 20, 20], 'easeInOutSine'), (1.1, [-100, -10, 10], 'easeInExpo'),
                    (1.4, [-30, -20, 10], 'easeOutQuad'), (2.0, [-20, 0, 24])], 'lin')
    stretch(a, 'arm_r', [(0, 1), (0.9, 1.3), (1.1, 1.6, 'easeInExpo'), (1.5, 1)])
    a.rot('arm_l', [(0, [-20, 0, -24]), (0.9, [-90, 0, -30]), (1.2, [-10, 0, -30]), (2.0, [-20, 0, -24])])
    a.rot('torso', [(0, [0, 0, 0]), (0.9, [-20, 24, 0], 'easeInOutSine'), (1.1, [22, -24, 0], 'easeInExpo'), (2.0, [0, 0, 0])], 'lin')
    a.sound(0.85, 'grunt').sound(1.08, 'swing')
    return a


def b_p2_stomp():
    """1.8 s (P2_SNARE, 36 t). Knee up (0.5 s) and a stomp (0.7 s): the floor under you sprouts."""
    a = angry(B('p2_stomp', 1.8, 'hold'))
    a.rot('leg_r', [(0, [0, 0, 0]), (0.5, [-90, 0, 0], 'easeOutQuad'), (0.7, [0, 0, 0], 'easeInExpo'), (1.8, [0, 0, 0])], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.5, [-14, 0, 0]), (0.7, [20, 0, 0], 'easeInExpo'), (1.8, [0, 0, 0])], 'lin')
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-20, 0, 24 * s]), (0.5, [-60, 0, 70 * s]), (0.7, [10, 0, 40 * s], 'easeInExpo'), (1.8, [-20, 0, 24 * s])])
    a.pos('root', [(0, [0, 0, 0]), (0.5, [0, 1, 0]), (0.7, [0, -2, 0], 'easeInExpo'), (1.0, [0, 0, 0])], 'lin')
    a.sound(0.68, 'slam').sound(1.3, 'sprout')
    return a


def b_p2_gravity():
    """9.0 s (P2_GRAVITY, 180 t: El Gran Meteoro). Up over the middle of the sand, arms flung up to call it
    down (0.4 s), then clawing at the sky, dragging it lower as the pull drags everyone in (1.0-6.6 s), a last
    heave back (6.6-7.0) and both arms brought down (7.0 s): it falls; he lands the pose as it hits (7.5)."""
    a = angry(B('p2_gravity', 9.0, 'hold'))
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-20, 0, 24 * s]), (0.4, [-170, 0, 40 * s], 'easeOutBack')]
              + [(round(0.6 + 0.2 * i, 2), [-170 + (8 if i % 2 else -8), 0, 40 * s + (6 if i % 2 else 0)]) for i in range(30)]
              + [(6.6, [-170, 0, 40 * s]), (7.0, [-205, 0, 20 * s], 'easeInOutSine'), (7.2, [-40, 0, 20 * s], 'easeInExpo'),
                 (7.6, [-60, 0, 70 * s], 'easeOutBack'), (9.0, [-40, 0, 50 * s])], 'lin')
        stretch(a, arm, [(0, 1), (0.4, 1.5), (6.6, 1.6), (7.0, 1.7), (7.2, 1.9, 'easeInExpo'), (7.6, 1.4), (9.0, 1.1)])
    a.rot('torso', [(0, [0, 0, 0]), (0.4, [-26, 0, 0])] + [(round(0.6 + 0.4 * i, 2), [-26, 0, 4 if i % 2 else -4]) for i in range(15)]
          + [(6.6, [-26, 0, 0]), (7.0, [-34, 0, 0]), (7.2, [32, 0, 0], 'easeInExpo'), (7.6, [-10, 0, 0], 'easeOutBack'), (9.0, [0, 0, 0])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (7.0, [1.06, 1.06, 1.06]), (7.2, [0.9, 1.2, 0.9], 'easeInExpo'), (7.6, [1.08, 0.94, 1.08]),
                     (9.0, [1, 1, 1])], 'lin')
    a.sound(0.3, 'roar').sound(1.0, 'rumble').sound(2.5, 'rumble').sound(4.0, 'rumble').sound(5.5, 'rumble').sound(7.2, 'slam')
    return a


REWORK = [b_guard, b_riposte, b_evade, b_cleaver_toss, b_echo_throw, b_burrito_toss, b_drunk_swig, b_drunk_flurry, b_haymaker, b_comal_slam,
          b_brick, b_qte_hold_cleaver, b_qte_hold_bottle, b_qte_hold_suplex, b_qte_fail_cleaver, b_qte_fail_bottle,
          b_qte_fail_suplex, b_qte_broken, b_p2_fry_volley, b_p2_mash_slam, b_p2_tot_throw, b_p2_stomp, b_p2_gravity]


# ===================================================================== phase two

KNEEL_ROOT = [0, -5, 0]
KNEEL_LEG_R = [-80, 0, 0]
KNEEL_LEG_L = [40, 0, 0]


def kneel(a, t0, t1):
    a.pos('root', [(t0, KNEEL_ROOT), (t1, KNEEL_ROOT)], 'lin')
    a.rot('leg_r', [(t0, KNEEL_LEG_R), (t1, KNEEL_LEG_R)], 'lin')
    a.rot('leg_l', [(t0, KNEEL_LEG_L), (t1, KNEEL_LEG_L)], 'lin')


def b_p2_kneel():
    """6.0 s (BossState.P2_KNEEL, 120 t). Three quarters of his health gone: he reels and drops to one
    knee, his left fist braced on his knee, and coughs blood onto the ground three times (1.2 / 2.0 / 2.8
    s, a drop falls from his chin each time) while he stares down at it (his point of view from 1.4 s).
    Then he brings his left hand up in front of his eyes (3.0-3.6 s): it is bleeding too. He stares at it,
    the hand trembling, turns it a little (3.6-4.7 s), lets it fall (4.7-5.1 s) and his head comes up,
    slowly, to look at you (5.0-6.0 s)."""
    a = B('p2_kneel', 6.0, 'hold')
    a.scale('blood', [(0, SHOWN)], 'lin')
    a.scale('hand_blood', [(0, SHOWN)], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (0.5, [0, 0, 2.5], 'easeOutQuad'), (0.9, KNEEL_ROOT, 'easeInQuad'), (6.0, KNEEL_ROOT)], 'lin')
    a.rot('root', [(0, [0, 0, 0]), (0.5, [-12, 0, 6], 'easeOutQuad'), (0.9, [0, 0, 0], 'easeInQuad'), (6.0, [0, 0, 0])], 'lin')
    a.rot('leg_r', [(0, [0, 0, 0]), (0.5, [-20, 0, 0]), (0.9, KNEEL_LEG_R, 'easeInQuad'), (6.0, KNEEL_LEG_R)], 'lin')
    a.rot('leg_l', [(0, [0, 0, 0]), (0.5, [10, 0, 0]), (0.9, KNEEL_LEG_L, 'easeInQuad'), (6.0, KNEEL_LEG_L)], 'lin')
    torso = [(0, [0, 0, 0]), (0.5, [-14, 0, 0]), (0.9, [34, 0, 0], 'easeInQuad')]
    for c in (1.2, 2.0, 2.8):
        torso += [(round(c - 0.1, 2), [30, 0, 0]), (c, [46, 0, 0], 'easeInExpo'), (round(c + 0.25, 2), [36, 0, 0], 'easeOutQuad')]
    # sitting back a little to look at the hand, a flinch when he sees it, then the slow rise to you
    torso += [(3.0, [36, 0, 0]), (3.6, [24, 0, 0], 'easeInOutSine'), (3.75, [20, 0, 0], 'easeOutQuad'),
              (4.0, [24, 0, 0]), (5.0, [26, 0, 0]), (6.0, [4, 0, 0], 'easeInOutSine')]
    a.rot('torso', torso, 'lin')
    brace = [-24, 0, -34]                    # the fist on his knee
    view = [-78, 32, 0]                      # held out low in front of his eyes, toward his middle
    arm_l = [(0, [-20, 0, -24]), (0.9, [-30, 0, -30]), (1.1, brace, 'easeOutQuad'), (3.0, brace),
             (3.6, view, 'easeInOutSine')]
    # the tremble, and a slow turn of the hand to see all of it
    t, k = 3.6, 0
    while t < 4.6:
        t = round(t + 0.1, 2)
        k += 1
        twist = 18 * min(1.0, (t - 3.6) / 0.6)
        arm_l.append((t, [view[0] + (2.5 if k % 2 else -2.5), view[1] + (1.5 if k % 2 else -1.5), twist]))
    arm_l += [(4.7, [view[0], view[1], 18]), (5.1, brace, 'easeInOutSine'), (6.0, [-40, 0, -30], 'easeInOutSine')]
    a.rot('arm_l', arm_l, 'lin')
    stretch(a, 'arm_l', [(0, 1), (3.0, 1), (3.6, 1.25), (4.7, 1.25), (5.1, 1)])
    a.rot('arm_r', [(0, [-20, 0, 24]), (0.9, [20, 0, 20], 'easeInQuad'), (6.0, [24, 0, 18])])
    stretch(a, 'arm_r', [(0, 1), (0.9, 1.3), (6.0, 1.3)])
    # a drop of blood falls from his chin with each cough
    drop = [(0, [0, 0, 0])]
    for c in (1.2, 2.0, 2.8):
        drop += [(round(c - EPS, 2), [0, 0, 0]), (c, [0, 0, 0]), (round(c + 0.35, 2), [0, -34, -2], 'easeInQuad'),
                 (round(c + 0.35 + EPS, 2), [0, 0, 0])]
    a.pos('blood_drop', drop, 'lin')
    a.scale('body', [(0, [1, 1, 1])] + [k for c in (1.2, 2.0, 2.8) for k in
                                         ((round(c - 0.1, 2), [1, 1, 1]), (c, [1.08, 0.92, 1.08], 'easeInExpo'),
                                          (round(c + 0.25, 2), [1, 1, 1]))]
            + [(3.62, [1, 1, 1]), (3.72, [1.04, 1.04, 1.04], 'easeOutQuad'), (3.9, [1, 1, 1])], 'lin')
    for brow, s in (('brow_r', 1), ('brow_l', -1)):
        # worried over the blood, shot up in shock at the hand, then down into a glare as he looks up
        a.pos(brow, [(0, [0, 1.0, 0]), (3.6, [0, 1.0, 0]), (3.7, [0, 2.0, 0], 'easeOutQuad'), (4.7, [0, 1.6, 0]),
                     (5.2, [0, 1.0, 0]), (6.0, [0, -0.6, 0])])
        a.rot(brow, [(0, [0, 0, -12 * s]), (3.6, [0, 0, -12 * s]), (3.7, [0, 0, -20 * s]), (5.2, [0, 0, -12 * s]),
                     (6.0, [0, 0, 16 * s])])
    a.sound(0.9, 'thud')
    for c in (1.2, 2.0, 2.8):
        a.sound(c, 'cough')
    for t in (0.2, 1.0, 1.8, 2.6, 3.3, 3.7, 4.1, 4.5, 4.9, 5.4, 5.9):        # racing when he sees the hand
        a.sound(t, 'heartbeat')
    return a


def b_p2_talk():
    """BossState.P2_TALK, as long as his two lines (beats talk.*). Still on one knee he looks at the blood
    on his hand and laughs at it, shoulders shaking ("Heh... heh heh."), wipes his mouth with the back of
    his hand on "You made me... BLEED, amigo"; then on "Okay... OKAY." a finger up, he gets up through
    "Enough... playing around.", tears his apron off and flings it away (talk.rip: he is done being a
    cook; phase two has no apron, client/BossFace keeps it hidden), and cracks his neck both ways."""
    tb, te, end = beat('talk.bleed', 4), beat('talk.enough', 4), beat('talk.end', 70)
    rip = beat('talk.rip', 60)
    up = round(te + 0.25, 2)                 # starts rising on the second "OKAY"
    stood = round(up + 0.9, 2)
    crack = round(end - 0.45, 2)
    a = angry(B('p2_talk', end, 'hold'))
    a.scale('blood', [(0, SHOWN)], 'lin')
    a.scale('hand_blood', [(0, SHOWN)], 'lin')
    a.pos('root', [(0, KNEEL_ROOT), (up, KNEEL_ROOT), (stood, [0, 0, 0], 'easeInOutSine'), (end, [0, 0, 0])], 'lin')
    a.rot('leg_r', [(0, KNEEL_LEG_R), (up, KNEEL_LEG_R), (stood, [0, 0, 0], 'easeInOutSine'), (end, [0, 0, 0])], 'lin')
    a.rot('leg_l', [(0, KNEEL_LEG_L), (up, KNEEL_LEG_L), (stood, [0, 0, 0], 'easeInOutSine'), (end, [0, 0, 0])], 'lin')
    # the left hand: up in front of his eyes (the blood on it) through the laugh, then the wipe across his mouth
    look = [-62, 46, -14]                    # low, off to his side, so the camera sees his face over it
    wipe0 = round(te - 1.3, 2) if te - tb > 2.0 else round(tb + 0.6, 2)
    a.rot('arm_l', [(0, [-40, 0, -30]), (round(tb + 0.3, 2), look, 'easeInOutSine'), (wipe0, look),
                    (round(wipe0 + 0.2, 2), [-120, 70, 0], 'easeInOutSine'), (round(wipe0 + 0.75, 2), [-120, -20, 0], 'easeInOutSine'),
                    (round(wipe0 + 1.05, 2), [-30, 0, -26], 'easeInOutSine'), (end, [-20, 0, -24])], 'lin')
    stretch(a, 'arm_l', [(0, 1), (round(tb + 0.3, 2), 1.4), (wipe0, 1.4), (round(wipe0 + 0.2, 2), 1.5), (round(wipe0 + 0.75, 2), 1.5),
                         (round(wipe0 + 1.05, 2), 1)])
    # the right: a finger up on "Okay... OKAY.", then a push off his knee to get up
    a.rot('arm_r', [(0, [24, 0, 18]), (round(te, 2), [24, 0, 18]), (round(te + 0.25, 2), [-110, -10, 10], 'easeOutBack'),
                    (round(up + 0.2, 2), [-100, -10, 10]), (round(up + 0.5, 2), [10, 0, 20], 'easeInOutSine'),
                    (stood, [-30, 0, 30], 'easeInOutSine'),
                    # the apron: a grab at it (rip - 0.3), torn off and flung out to his right (rip + 0.25)
                    (round(rip - 0.3, 2), [-30, 0, 30]), (rip, [-28, 40, 6], 'easeInOutSine'),
                    (round(rip + 0.25, 2), [-110, -10, 95], 'easeOutExpo'), (round(rip + 0.6, 2), [-60, 0, 60]),
                    (round(rip + 0.8, 2), [-20, 0, 24], 'easeInOutSine'), (end, [-20, 0, 24])], 'lin')
    stretch(a, 'arm_r', [(0, 1.3), (te, 1.3), (round(te + 0.25, 2), 1.5), (round(up + 0.2, 2), 1.5), (round(up + 0.5, 2), 1.3), (stood, 1),
                         (round(rip - 0.3, 2), 1), (rip, 1.4), (round(rip + 0.25, 2), 1.3), (round(rip + 0.8, 2), 1)])
    a.pos('apron', [(0, [0, 0, 0]), (rip, [0, 0, 0]), (round(rip + 0.1, 2), [-2, 1, -1.5], 'easeOutQuad'),
                    (round(rip + 0.3, 2), [-16, 9, -4], 'easeOutQuad'), (round(rip + 0.7, 2), [-34, -4, 2], 'easeInQuad')], 'lin')
    a.rot('apron', [(0, [0, 0, 0]), (rip, [0, 0, 0]), (round(rip + 0.3, 2), [30, 20, 140], 'easeOutQuad'),
                    (round(rip + 0.7, 2), [70, 40, 300], 'easeInQuad')], 'lin')
    a.scale('apron', [(0, SHOWN), (round(rip + 0.65, 2), SHOWN), (round(rip + 0.7, 2), HIDDEN), (end, HIDDEN)], 'lin')
    # the laugh: the shoulders shaking for the "heh heh" at the start of the line
    torso = [(0, [4, 0, 0]), (tb, [4, 0, 0])]
    torso += [(round(tb + 0.12 * (i + 1), 2), [10 if i % 2 else 3, 0, 0]) for i in range(9)]
    torso += [(round(tb + 1.3, 2), [6, 0, 0]), (te, [8, 0, 0]), (round(te + 0.3, 2), [2, 0, 0]),
              (up, [12, 0, 0]), (stood, [0, 0, 0], 'easeInOutSine'), (round(rip - 0.05, 2), [0, 0, 0]),
              (round(rip + 0.2, 2), [-6, 18, 8], 'easeOutExpo'), (round(rip + 0.7, 2), [0, 0, 0], 'easeInOutSine'), (crack, [0, 0, 0]),
              (round(crack + 0.15, 2), [0, 0, -14], 'easeOutExpo'), (round(crack + 0.27, 2), [0, 0, 0]),
              (round(crack + 0.39, 2), [0, 0, 14], 'easeOutExpo'), (end, [0, 0, 0])]
    a.rot('torso', torso, 'lin')
    a.sound(round(wipe0 + 0.2, 2), 'cloth').sound(round(up + 0.3, 2), 'grunt').sound(rip, 'rip').sound(round(rip + 0.25, 2), 'whoosh').sound(round(crack + 0.15, 2), 'crack').sound(round(crack + 0.39, 2), 'crack')
    return a


def b_p2_season():
    """5.0 s (BossState.P2_SEASON, 100 t). Seasoned by the heavens: arms up, he calls it down (0.0-0.6
    s); a huge salt shaker appears over his head, upside down (0.6-1.2 s), and shakes itself over him
    (1.2-3.2 s) while he basks in it, arms wide, swelling with every wave (the entity pours the salt
    and runs the aura); it bursts (3.3 s), he clenches his fists as the aura flares and his skin
    bakes golden (the entity swaps the texture at 4.0 s), and grins."""
    end, kiss = max(5.0, beat('season.end', 100)), beat('season.kiss', 96)
    a = angry(B('p2_season', end, 'hold'))
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
                    (3.7, [10, 0, 20 * s], 'easeOutQuad'), (4.0, [-50, 0, 60 * s], 'easeOutExpo'), (4.6, [-30, 0, 40 * s])]
                + chefs_kiss(kiss, s, end), 'lin')
        stretch(a, arm, [(0, 1), (0.5, 1.4), (1.0, 1.4), (1.3, 1.2), (3.2, 1.2), (3.5, 1)] + chefs_kiss_reach(kiss, s, end))
    a.pos('root', [(0, [0, 0, 0]), (1.3, [0, 0, 0]), (3.2, [0, 3, 0], 'easeInOutSine'), (3.6, [0, 0, 0], 'easeInQuad'),
                   (3.7, [0, -1.5, 0]), (4.0, [0, 0.5, 0]), (5.0, [0, 0, 0])], 'lin')
    # swelling with power as the salt comes down, a hard pulse when the aura flares
    a.scale('body', [(0, [1, 1, 1]), (1.3, [1, 1, 1])]
            + [(round(1.5 + 0.3 * i, 2), [1 + 0.02 * (i + 1), 1 + 0.025 * (i + 1), 1 + 0.02 * (i + 1)]) for i in range(6)]
            + [(3.6, [1.06, 1.06, 1.06]), (4.0, [1.22, 0.86, 1.22], 'easeOutExpo'), (4.3, [1.08, 1.1, 1.08]),
               (end, [1.04, 1.04, 1.04])], 'lin')
    a.rot('wisps', [(round(1.2 + 0.08 * i, 2), [0, 0, 30 if i % 2 else -30]) for i in range(25)])
    a.sound(0.4, 'roar_small').sound(0.9, 'ding')
    for i in range(10):
        a.sound(round(1.25 + 0.2 * i, 2), 'shake')
    a.sound(3.3, 'smash').sound(4.0, 'roar_small')
    a.rot('torso', [(0, [0, 0, 0]), (0.5, [-20, 0, 0]), (1.3, [-28, 0, 0])]
          + [(round(1.4 + 0.3 * i, 2), [-28, 0, 4 if i % 2 else -4]) for i in range(6)]
          + [(3.5, [10, 0, 0]), (4.0, [-14, 0, 0], 'easeOutExpo'), (4.6, [0, 0, 0]),
             (round(kiss - 0.3, 2), [4, 0, 0]), (round(kiss + 0.1, 2), [-10, 0, 0], 'easeOutBack'), (end, [-4, 0, 0])], 'lin')
    return a


def chefs_kiss(kiss, s, end):
    """The right hand's chef's kiss on "GOLDEN": fingers to his lips (kiss - 0.35 s), flung open (kiss + 0.1);
    the left arm stays out."""
    if s < 0 or kiss + 0.1 > end:
        return [(end, [-30, 0, 40 * s])]
    return [(round(kiss - 0.6, 2), [-30, 0, 40]), (round(kiss - 0.3, 2), [-96, -40, 0], 'easeInOutSine'),
            (round(kiss + 0.1, 2), [-130, 20, 60], 'easeOutBack'), (round(min(end, kiss + 0.6), 2), [-120, 20, 60]),
            (end, [-40, 0, 40], 'easeInOutSine')] if kiss + 0.6 < end else [(round(kiss - 0.3, 2), [-96, -40, 0]), (end, [-130, 20, 60])]


def chefs_kiss_reach(kiss, s, end):
    if s < 0 or kiss + 0.1 > end:
        return []
    return [(round(kiss - 0.6, 2), 1), (round(kiss - 0.3, 2), 1.55), (round(kiss + 0.1, 2), 1.3), (end, 1)]


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


# ===================================================================== phase two: El Coliseo Papa

POINT_R = [-95, -6, 4]          # pointing at the band stand, arm stretched
CROSS_R = [-72, -62, 0]         # arms folded
CROSS_L = [-72, 62, 0]


def b_p2_arrive_sky():
    """5.0 s (BossState.P2_ARRIVE in his coliseum, 100 t). Down out of the sky into the middle of the
    sand (the entity's motion, 0-1.8 s) in a superhero landing (1.8 s), up again with his arms thrown
    up to the stands ("¿ESTÁN LISTOS?" at 2.3), a fist pump each way; then the grand gesture at the
    band stand with a snap of his fingers (3.7 s): "¡MÚSICA, MAESTRO!"... and he holds it."""
    m = beat('arrive.musica', 74)
    end = max(5.0, beat('arrive.end', 100))
    a = angry(B('p2_arrive_sky', end, 'hold'))
    a.pos('root', [(0, [0, 2, 0]), (1.7, [0, 1, 0]), (1.8, [0, -6, 0], 'easeInQuad'), (2.0, [0, -6, 0]),
                   (2.3, [0, 0, 0], 'easeOutBack'), (5.0, [0, 0, 0])], 'lin')
    a.rot('leg_r', [(0, [30, 0, 6]), (1.7, [24, 0, 6]), (1.8, [-80, 0, 0], 'easeInQuad'), (2.0, [-80, 0, 0]), (2.3, [0, 0, 6]), (5.0, [0, 0, 6])], 'lin')
    a.rot('leg_l', [(0, [10, 0, -6]), (1.7, [16, 0, -6]), (1.8, [40, 0, 0], 'easeInQuad'), (2.0, [40, 0, 0]), (2.3, [0, 0, -6]), (5.0, [0, 0, -6])], 'lin')
    a.rot('torso', [(0, [-8, 0, 0]), (1.7, [-4, 0, 0]), (1.8, [40, 0, 0], 'easeInQuad'), (2.0, [42, 0, 0]),
                    (2.3, [-14, 0, 0], 'easeOutBack'), (2.8, [-10, 0, 0]), (round(m - 0.2, 2), [-6, 0, 0]), (m, [4, 0, 0], 'easeOutQuad'), (end, [2, 0, 0])], 'lin')
    a.rot('arm_r', [(0, [-170, 0, 10]), (1.7, [-170, 0, 10]), (1.8, [-20, 0, 30], 'easeInQuad'), (2.0, [-10, 0, 40]),
                    (2.3, [-160, 0, 40], 'easeOutBack')] + pumps(2.3, m - 0.5, 1) + [
                    (round(m - 0.2, 2), [-40, 0, 20], 'easeInOutSine'), (m, POINT_R, 'easeOutBack'), (end, POINT_R)], 'lin')
    a.rot('arm_l', [(0, [-20, 0, -40]), (1.7, [-20, 0, -40]), (1.8, [-60, 0, -10], 'easeInQuad'), (2.0, [-70, 0, -6]),
                    (2.3, [-160, 0, -40], 'easeOutBack')] + pumps(2.45, m - 0.4, -1) + [
                    (round(m - 0.1, 2), [-20, 0, -24], 'easeInOutSine'), (end, [-16, 0, -22])], 'lin')
    stretch(a, 'arm_r', [(0, 1.2), (1.8, 1.0), (2.3, 1.3), (round(m - 0.2, 2), 1.0), (m, 1.7, 'easeOutBack'), (end, 1.7)])
    stretch(a, 'arm_l', [(0, 1.0), (2.0, 1.3), (2.3, 1.3), (round(m - 0.1, 2), 1.0)])
    a.scale('body', [(0, [1, 1, 1]), (1.8, [1.25, 0.8, 1.25], 'easeInQuad'), (2.0, [1.1, 0.92, 1.1]), (2.3, [0.92, 1.1, 0.92]),
                     (2.6, [1.03, 1.03, 1.03]), (m, [1.03, 1.03, 1.03]), (round(m + 0.1, 2), [0.97, 1.06, 0.97]),
                     (round(m + 0.3, 2), [1.02, 1.02, 1.02])], 'lin')
    a.sound(1.8, 'slam').sound(m, 'snap')
    return a


def pumps(t0, t1, s):
    """Fists pumped at the stands, one arm then the other, every 0.6 s from t0 to t1."""
    keys, t = [], t0 + 0.3
    while t + 0.15 < t1:
        keys += [(round(t, 2), [-150, 0, 50 * s]), (round(t + 0.15, 2), [-175, 0, 30 * s])]
        t += 0.6
    return keys


def b_p2_notice():
    """3.0 s (BossState.P2_NOTICE, 60 t). Still pointing. Nothing. The brows go up, the arm sags
    (1.4-2.0 s) as he looks round at the stand and back at you (the entity turns him), and then the
    fists clench and he swells up, fuming."""
    f = beat('notice.fume', 48)              # 2.4 s in the old cut
    end = beat('notice.end', 60)
    sag0, sag1 = round(f - 1.2, 2), round(f - 0.4, 2)
    a = B('p2_notice', end, 'hold')
    a.rot('arm_r', [(0, POINT_R), (sag0, POINT_R), (sag1, [-40, -10, 14], 'easeInOutSine'), (round(f - 0.1, 2), [-10, 0, 24]),
                    (round(f + 0.1, 2), [-24, 0, 30], 'easeOutBack'), (end, [-24, 0, 30])], 'lin')
    stretch(a, 'arm_r', [(0, 1.7), (sag0, 1.7), (sag1, 1.0)])
    a.rot('arm_l', [(0, [-16, 0, -22]), (round(f - 0.1, 2), [-10, 0, -24]), (round(f + 0.1, 2), [-24, 0, -30], 'easeOutBack'), (end, [-24, 0, -30])], 'lin')
    for brow, sgn in (('brow_r', 1), ('brow_l', -1)):
        a.pos(brow, [(0, [0, 0, 0]), (0.6, [0, 1.0, 0]), (round(f - 0.2, 2), [0, 1.0, 0]), (f, [0, -0.8, 0], 'easeOutQuad'), (end, [0, -0.8, 0])], 'lin')
        a.rot(brow, [(0, [0, 0, 0]), (0.6, [0, 0, -8 * sgn]), (round(f - 0.2, 2), [0, 0, -8 * sgn]), (f, [0, 0, 18 * sgn], 'easeOutQuad'), (end, [0, 0, 18 * sgn])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (round(f - 0.2, 2), [1, 1, 1]), (round(f + 0.05, 2), [1.12, 0.94, 1.12], 'easeOutQuad'),
                     (round(f + 0.2, 2), [1.08, 0.97, 1.08]), (round(f + 0.35, 2), [1.13, 0.94, 1.13]), (end, [1.1, 0.96, 1.1])], 'lin')
    a.rot('torso', [(0, [2, 0, 0]), (1.0, [2, 0, 4]), (round(f - 0.2, 2), [0, 0, 0]), (round(f + 0.1, 2), [10, 0, 0]), (end, [8, 0, 0])])
    a.sound(f, 'grunt')
    return a


def b_p2_march():
    """0.7 s loop. Storming over: high stamping steps, fists pumping, leaning into it."""
    a = angry(B('p2_march', 0.7, True))
    a.rot('leg_r', [(0, [-40, 0, 4]), (0.35, [24, 0, 4]), (0.7, [-40, 0, 4])])
    a.rot('leg_l', [(0, [24, 0, -4]), (0.35, [-40, 0, -4]), (0.7, [24, 0, -4])])
    a.rot('arm_r', [(0, [30, 0, 22]), (0.35, [-60, 0, 22]), (0.7, [30, 0, 22])])
    a.rot('arm_l', [(0, [-60, 0, -22]), (0.35, [30, 0, -22]), (0.7, [-60, 0, -22])])
    a.rot('torso', [(0, [14, 0, -4]), (0.35, [14, 0, 4]), (0.7, [14, 0, -4])])
    a.pos('root', [(0, [0, 0, 0]), (0.1, [0, -0.8, 0]), (0.25, [0, 1.6, 0]), (0.35, [0, 0, 0]), (0.45, [0, -0.8, 0]),
                   (0.6, [0, 1.6, 0]), (0.7, [0, 0, 0])])
    a.sound(0.1, 'thud').sound(0.45, 'thud')
    return a


def b_p2_wake():
    """BossState.P2_WAKE, as long as his bellow needs (gen_boss_voice.beats 'wake.end'). He arrives in front
    of the sleeping singer mid-stride and the big spatula is already going up behind his head (0-0.55 s,
    leaning back on his heels); down it comes on the singer's head (0.7 s: BONK, the whole arm stretched
    out over the stage edge) and bounces back off it, boing (0.85); he glares at the band jolting awake,
    the spatula up (1.2 s on); then "WAKE UP, YOU LAZY BUMS!!" ('wake.line'), the spatula jabbed at them
    on the stressed words, his other hand pointing; and the spatula onto his shoulder as the bellow ends."""
    yell, end = beat('wake.line', 42), beat('wake.end', 70)
    yell = max(yell, 1.3)
    down = round(max(yell + 1.8, end - 0.9), 2)
    a = angry(B('p2_wake', end, 'hold'))
    a.scale('big_spatula', [(0, SHOWN)], 'lin')
    # the last step of the walk, into a planted stance
    a.rot('leg_r', [(0, [-30, 0, 4]), (0.3, [0, 0, 6], 'easeOutQuad'), (0.6, [12, 0, 6]), (0.75, [-20, 0, 6], 'easeOutQuad'),
                    (1.1, [0, 0, 6]), (end, [0, 0, 6])], 'lin')
    a.rot('leg_l', [(0, [20, 0, -4]), (0.3, [0, 0, -6], 'easeOutQuad'), (0.6, [-14, 0, -6]), (0.75, [16, 0, -6]),
                    (1.1, [0, 0, -6]), (end, [0, 0, -6])], 'lin')
    keys = [(0, [-20, 0, 24]), (0.45, [-196, 10, 20], 'easeOutQuad'), (0.55, [-200, 8, 18]),
            (0.7, [-70, 0, 6], 'easeInExpo'), (0.85, [-108, 0, 10], 'easeOutQuad'), (1.0, [-82, 0, 8], 'easeInQuad'),
            (1.2, [-150, -10, 30], 'easeInOutSine'), (round(yell, 2), [-150, -10, 30])]
    # through the bellow: the spatula jabbed at the band on the stressed words
    keys += [(round(yell + 0.2, 2), [-100, -10, 20]), (round(yell + 0.5, 2), [-80, -10, 20]), (round(yell + 1.3, 2), [-100, -10, 20]),
             (round(yell + 1.6, 2), [-80, -10, 20]), (down, [-110, -20, 30]), (round(down + 0.6, 2), [-176, -60, 10], 'easeInOutSine'),
             (max(end, round(down + 0.6, 2)), [-176, -60, 10])]
    a.rot('arm_r', keys, 'lin')
    stretch(a, 'arm_r', [(0, 1), (0.45, 1.3), (0.55, 1.3), (0.7, 1.9, 'easeInExpo'), (0.85, 1.6), (1.0, 1.75), (1.2, 1.3),
                         (round(yell, 2), 1.3), (round(yell + 0.2, 2), 1.6), (down, 1.4), (round(down + 0.6, 2), 1.1)])
    a.rot('big_spatula', [(0, [0, 0, 0]), (0.55, [-20, 0, 0]), (0.7, [30, 0, 0], 'easeInExpo'), (0.85, [-10, 0, 0]), (1.0, [8, 0, 0]),
                          (1.2, [0, 0, 0]), (end, [0, 0, 0])], 'lin')
    a.rot('arm_l', [(0, [-20, 0, -24]), (0.45, [-40, 0, -40]), (0.7, [-10, 0, -50]), (round(yell - 0.15, 2), [-10, 0, -40]),
                    (round(yell + 0.1, 2), [-100, 20, -8], 'easeOutBack'), (down, [-100, 20, -8]), (round(down + 0.5, 2), [-20, 0, -30], 'easeInOutSine'),
                    (max(end, round(down + 0.5, 2)), [-20, 0, -30])], 'lin')
    stretch(a, 'arm_l', [(0, 1), (round(yell - 0.15, 2), 1), (round(yell + 0.1, 2), 1.7), (down, 1.7), (round(down + 0.5, 2), 1)])
    a.rot('torso', [(0, [10, 0, 0]), (0.45, [-24, 10, 0], 'easeOutQuad'), (0.55, [-26, 10, 0]), (0.7, [42, -4, 0], 'easeInExpo'),
                    (0.85, [30, -2, 0]), (1.2, [6, 0, 0], 'easeInOutSine'), (round(yell - 0.1, 2), [-4, 0, 0]),
                    (round(yell + 0.15, 2), [16, 0, 0], 'easeOutQuad'), (round(yell + 1.0, 2), [12, 0, 0]), (round(yell + 1.5, 2), [18, 0, 0]),
                    (down, [10, 0, 0]), (max(end, round(down + 0.3, 2)), [2, 0, 0])], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (0.45, [0, 1.6, 0], 'easeOutQuad'), (0.7, [0, -2.0, 0], 'easeInExpo'), (0.9, [0, 0, 0]),
                   (end, [0, 0, 0])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (0.5, [0.92, 1.12, 0.92]), (0.7, [1.2, 0.84, 1.2], 'easeInExpo'), (0.9, [0.98, 1.03, 0.98]),
                     (1.1, [1, 1, 1]), (round(yell - 0.1, 2), [1.04, 1.04, 1.04]), (round(yell + 0.15, 2), [1.08, 1.12, 1.08], 'easeOutQuad'),
                     (down, [1.06, 1.08, 1.06]), (max(end, round(down + 0.3, 2)), [1.02, 1.02, 1.02])], 'lin')
    a.sound(0.3, 'grunt').sound(0.62, 'swing')
    return a


def b_p2_wait():
    """4.0 s loop. Waiting on the band: arms folded, a foot tapping; a look at his (bare) wrist."""
    a = angry(B('p2_wait', 4.0, True))
    a.rot('arm_r', [(0, CROSS_R), (4.0, CROSS_R)], 'lin')
    a.rot('arm_l', [(0, CROSS_L), (2.1, CROSS_L), (2.4, [-96, 20, -6], 'easeInOutSine'), (3.1, [-96, 20, -6]),
                    (3.4, CROSS_L, 'easeInOutSine'), (4.0, CROSS_L)], 'lin')
    stretch(a, 'arm_r', [(0, 1.25)])
    stretch(a, 'arm_l', [(0, 1.25), (2.1, 1.25), (2.4, 1.4), (3.1, 1.4), (3.4, 1.25)])
    taps = []
    for i in range(8):
        tt = 0.25 + i * 0.5
        if 2.1 < tt < 3.3:
            continue
        taps += [(round(tt - 0.12, 2), [-16, 0, 6]), (round(tt, 2), [0, 0, 6], 'easeInQuad')]
        a.sound(round(tt, 2), 'tap')
    a.rot('leg_r', [(0, [0, 0, 6])] + taps + [(4.0, [0, 0, 6])], 'lin')
    a.rot('torso', [(0, [0, 0, -3]), (1.0, [0, 0, 3]), (2.0, [0, 0, -3]), (2.4, [8, 0, 0]), (3.1, [8, 0, 0]), (3.4, [0, 0, -3]), (4.0, [0, 0, -3])])
    a.sound(2.6, 'hmm')
    return a


def b_p2_kick():
    """1.5 s (BossState.P2_KICK, 30 t). A wind-up and a boot to the guitarron's chair (0.6 s)."""
    k, end = beat('kick.boot', 12), beat('kick.end', 30)
    w = round(k - 0.2, 2)
    a = angry(B('p2_kick', end, 'hold'))
    a.rot('leg_r', [(0, [0, 0, 0]), (max(0.05, round(w - 0.2, 2)), [0, 0, 0]), (w, [40, 0, 0], 'easeOutQuad'), (k, [-95, 0, 0], 'easeOutExpo'),
                    (round(k + 0.25, 2), [-80, 0, 0]), (round(k + 0.6, 2), [0, 0, 0], 'easeInOutSine'), (end, [0, 0, 0])], 'lin')
    # leaning in over him through the shout, finger wagging, then the boot
    a.rot('torso', [(0, [0, 0, 0]), (0.2, [14, 0, 0]), (w, [10, 0, 0]), (k, [-20, 0, 0], 'easeOutExpo'), (round(k + 0.6, 2), [0, 0, 0])], 'lin')
    wag = [(round(0.15 + 0.16 * i, 2), [-110, -20 + (14 if i % 2 else -14), 10]) for i in range(int(max(0, w - 0.3) / 0.16))]
    a.rot('arm_r', [(0, [-20, 0, 24])] + wag + [(w, [-30, 0, 40]), (k, [-40, 0, 70], 'easeOutExpo'), (round(k + 0.6, 2), [-20, 0, 24])], 'lin')
    stretch(a, 'arm_r', [(0, 1), (0.15, 1.4), (w, 1.4), (k, 1)])
    a.rot('arm_l', [(0, [-20, 0, -24]), (w, [-30, 0, -40]), (k, [-40, 0, -70], 'easeOutExpo'), (round(k + 0.6, 2), [-20, 0, -24])], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (w, [0, 0, 0]), (k, [0, 1.2, 0]), (round(k + 0.3, 2), [0, 0, 0])])
    a.sound(round(k - 0.05, 2), 'whoosh')
    return a


def b_p2_count():
    """4.0 s (BossState.P2_COUNT, 80 t). Turned to the band, nodding along to the singer's count
    (0.2 / 1.15 / 2.1 / 2.55 / 3.05 s), and on the downbeat (4.0) both fists up."""
    a = angry(B('p2_count', 4.0, 'hold'))
    t = [(0, [0, 0, 0])]
    for c in [0.2, 1.15, 2.1, 2.55, 3.05, 3.5]:
        t += [(round(c - 0.1, 2), [-4, 0, 0]), (c, [12, 0, 0], 'easeInQuad'), (round(c + 0.2, 2), [0, 0, 0])]
    t += [(3.85, [0, 0, 0]), (4.0, [-16, 0, 0], 'easeOutBack')]
    a.rot('torso', t, 'lin')
    for arm, sgn in (('arm_r', 1), ('arm_l', -1)):
        cross = CROSS_R if sgn > 0 else CROSS_L
        a.rot(arm, [(0, cross), (3.7, cross), (4.0, [-170, 0, 30 * sgn], 'easeOutBack')], 'lin')
        stretch(a, arm, [(0, 1.25), (3.7, 1.25), (4.0, 1.4)])
    return a


def b_p2_groove():
    """1.88 s loop (a bar of the song). Grooving to the guitar: a bounce on every beat, the shoulders
    rolling, his head going."""
    bar = 1.882
    beat = bar / 4
    a = B('p2_groove', bar, True)
    a.pos('root', [k for i in range(4) for k in ((round(i * beat, 3), [0, 0, 0]), (round(i * beat + beat / 2, 3), [0, 1.0, 0]))] + [(bar, [0, 0, 0])])
    a.rot('torso', [(0, [6, 0, -6]), (beat, [0, 0, 0]), (2 * beat, [6, 0, 6]), (3 * beat, [0, 0, 0]), (bar, [6, 0, -6])])
    for arm, sgn in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-30, 0, 20 * sgn]), (2 * beat, [-10, 0, 30 * sgn]), (bar, [-30, 0, 20 * sgn])])
    a.rot('leg_r', [(0, [0, 0, 0]), (beat * 0.5, [-12, 0, 0]), (beat, [0, 0, 0]), (bar, [0, 0, 0])])
    return a


def b_p2_apology():
    """BossState.P2_APOLOGY, until "Now... where... were we?" would end on the singer's first word
    (beats apology.*). Over to you (the entity walks him, 0-2.5 s), and the apology as he comes: a hand on
    his heart and a little bow on "My apologies, amigo...", both hands thrown up on "Such POOR hospitality",
    an open-handed shrug through "Making my guest... wait like that." Then the aura farm (apology.aura on):
    he straightens, turns half away, runs a hand back over his bald head, folds his arms and looks back at
    you over his shoulder (the wink, apology.wink); later a slow roll of the shoulders, and he holds it."""
    ln, aura, end = beat('apology.line', 30), beat('apology.aura', 110), beat('apology.end', 178)
    say = round(aura - 0.4 - ln, 2)                  # how long the apology is
    q = lambda f: round(ln + say * f, 2)              # a point through the line
    a = B('p2_apology', end, 'hold')
    steps = [(round(i * 0.42, 2), [22 if i % 2 else -22, 0, 0]) for i in range(6)]
    a.rot('leg_r', steps + [(2.6, [0, 0, 0]), (end, [0, 0, 0])])
    a.rot('leg_l', [(tt, [-v[0], 0, 0]) for tt, v in steps] + [(2.6, [0, 0, 0]), (end, [0, 0, 0])])
    h0, h1, h2, h3 = round(aura + 0.8, 2), round(aura + 1.3, 2), round(aura + 1.7, 2), round(aura + 2.1, 2)
    roll = round(min(end - 1.0, aura + 4.5), 2)
    a.rot('arm_r', [(0, [-10, 0, 20]), (max(0.1, round(ln - 0.1, 2)), [-10, 0, 20]), (round(ln + 0.3, 2), [-82, -62, 0], 'easeInOutSine'),
                    (q(0.35), [-82, -62, 0]), (q(0.42), [-150, 10, 50], 'easeOutBack'), (q(0.58), [-140, 10, 50]),
                    (q(0.68), [-40, 10, 40], 'easeInOutSine'), (q(1.0), [-16, 0, 20]), (aura, [-16, 0, 20]),
                    (h0, [-165, -40, 10], 'easeInOutSine'), (h1, [-180, -10, 10], 'easeInOutSine'), (h2, [-150, 20, 20]),
                    (h3, CROSS_R, 'easeInOutSine'), (end, CROSS_R)], 'lin')
    stretch(a, 'arm_r', [(0, 1), (round(ln - 0.1, 2), 1), (round(ln + 0.3, 2), 1.3), (q(0.35), 1.3), (q(0.42), 1.4), (q(0.58), 1.4),
                         (q(0.68), 1.1), (q(1.0), 1), (aura, 1), (h0, 1.6), (h2, 1.6), (h3, 1.25), (end, 1.25)])
    a.rot('arm_l', [(0, [-10, 0, -20]), (q(0.35), [-10, 0, -20]), (q(0.42), [-150, -10, -50], 'easeOutBack'), (q(0.58), [-140, -10, -50]),
                    (q(0.68), [-50, 30, -50], 'easeInOutSine'), (q(0.95), [-50, 30, -50]), (round(aura - 0.1, 2), [-16, 0, -20], 'easeInOutSine'),
                    (round(h3 - 0.3, 2), [-16, 0, -20]), (h3, CROSS_L, 'easeInOutSine'), (end, CROSS_L)], 'lin')
    stretch(a, 'arm_l', [(0, 1), (q(0.35), 1), (q(0.42), 1.4), (q(0.58), 1.4), (q(0.68), 1.3), (q(0.95), 1.3), (round(aura - 0.1, 2), 1),
                         (round(h3 - 0.3, 2), 1), (h3, 1.25), (end, 1.25)])
    a.rot('torso', [(0, [6, 0, 0]), (round(ln + 0.1, 2), [6, 0, 0]), (round(ln + 0.7, 2), [22, 0, 6], 'easeInOutSine'), (q(0.33), [20, 0, 6]),
                    (q(0.42), [-10, 0, 0], 'easeOutBack'), (q(0.6), [-8, 0, 0]), (q(0.7), [4, 0, -6], 'easeInOutSine'),
                    (round(aura - 0.2, 2), [0, 0, -4]), (round(aura + 0.4, 2), [-8, 0, 0], 'easeInOutSine'),
                    (round(h3 - 0.2, 2), [-8, 0, 0]), (round(h3 + 0.3, 2), [-6, -38, 0], 'easeInOutSine'),
                    (roll, [-6, -40, 0]), (round(roll + 0.4, 2), [-10, -40, 4], 'easeInOutSine'), (round(roll + 0.8, 2), [-6, -40, -2], 'easeInOutSine'),
                    (end, [-6, -40, 0])], 'lin')
    a.rot('root', [(0, [0, 0, 0]), (aura, [0, 0, 0]), (round(aura + 0.8, 2), [0, 58, 0], 'easeInOutSine'), (end, [0, 60, 0])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (aura, [1, 1, 1]), (round(aura + 0.5, 2), [1.05, 1.05, 1.05], 'easeInOutSine'),
                     (round(aura + 1.5, 2), [1.07, 1.04, 1.07]), (round(aura + 2.5, 2), [1.05, 1.05, 1.05]), (end, [1.06, 1.05, 1.06])])
    for brow, sgn in (('brow_r', 1), ('brow_l', -1)):
        a.pos(brow, [(0, [0, 0.5, 0]), (round(aura - 0.1, 2), [0, 0.5, 0]), (round(aura + 0.3, 2), [0, -0.3, 0]), (end, [0, -0.3, 0])])
        a.rot(brow, [(0, [0, 0, -10 * sgn]), (round(aura - 0.1, 2), [0, 0, -10 * sgn]), (round(aura + 0.3, 2), [0, 0, 10 * sgn]), (end, [0, 0, 10 * sgn])])
    a.sound(round(ln + 0.3, 2), 'cloth').sound(round(aura + 1.0, 2), 'cloth').sound(round(h3 + 0.4, 2), 'chuckle')
    return a


def b_p2_resume():
    """BossState.P2_RESUME (beats resume.*: the line, then it holds until the singing starts). "Now...
    where... were we?": he turns back round to you on "Now...", cracks his knuckles in the pauses, and
    drops into his stance on "were we?"."""
    ln, end = beat('resume.line', 2), max(1.5, beat('resume.total', 60))
    talk = round(end - 0.4 - ln, 2)
    q = lambda f: round(ln + talk * f, 2)
    a = angry(B('p2_resume', end, 'hold'))
    a.rot('root', [(0, [0, 60, 0]), (q(0.25), [0, 0, 0], 'easeInOutSine'), (end, [0, 0, 0])], 'lin')
    a.rot('torso', [(0, [-6, -40, 0]), (q(0.25), [0, 0, 0], 'easeInOutSine'), (q(0.72), [0, 0, 0]), (q(0.86), [14, 0, 0], 'easeOutBack'), (end, [12, 0, 0])], 'lin')
    c1, c2 = q(0.38), q(0.55)
    for arm, sgn in (('arm_r', 1), ('arm_l', -1)):
        cross = CROSS_R if sgn > 0 else CROSS_L
        a.rot(arm, [(0, cross), (q(0.25), cross), (round(c1 - 0.1, 2), [-80, -30 * sgn, 0], 'easeInOutSine'), (c1, [-86, -34 * sgn, 0]),
                    (round(c2 - 0.1, 2), [-80, -30 * sgn, 0]), (c2, [-86, -34 * sgn, 0]),
                    (q(0.86), [-100, -20 * sgn, 30 * sgn], 'easeOutBack'), (end, [-100, -20 * sgn, 30 * sgn])], 'lin')
        stretch(a, arm, [(0, 1.25), (q(0.25), 1.25), (c1, 1.3), (q(0.86), 1.2), (end, 1.2)])
    for leg, sgn in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [0, 0, 0]), (q(0.72), [0, 0, 0]), (q(0.86), [-10, 0, 12 * sgn], 'easeOutBack'), (end, [-10, 0, 12 * sgn])], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (q(0.72), [0, 0, 0]), (q(0.86), [0, -1.5, 0], 'easeOutBack'), (end, [0, -1.5, 0])], 'lin')
    a.sound(c1, 'crack').sound(c2, 'crack').sound(q(0.86), 'grunt')
    return a


def roof_pumps(t0, up, down, n=4, step=0.5):
    """Raise-the-roof: up on each beat, back down on the off-beat."""
    out = []
    for i in range(n):
        out += [(round(t0 + step * i, 2), up, 'easeOutQuad'), (round(t0 + step / 2 + step * i, 2), down, 'easeInQuad')]
    return out


def b_p2_hype():
    """8.0 s (BossState.P2_HYPE, ending on the first sung word). Working the stands: a hand cupped to
    his ear at the near side (0.6-2.0, "NO LOS OIGO!" 0.9; they roar 1.6); a slow shake of the head,
    no, not good enough (2.0-2.6), round to the other side (the entity turns him, 2.2-3.0), the other
    ear (3.0-3.9, "MAS FUERTE!" 3.3; louder, 3.7); then raise the roof, both palms pumping at the
    sky on every beat (4.0-6.0) with the whole bowl chanting; round to you (6.0-6.4) and a finger out:
    "TU!" (6.4); fists together, BANG (6.9); and the roar, arms flung wide and head back (7.5-8.0),
    down into his stance as the singer comes in."""
    a = B('p2_hype', 8.0, 'hold')
    a.rot('arm_r', [(0, [-20, 0, 20]), (0.6, [-150, -60, 30], 'easeInOutSine'), (2.0, [-150, -60, 30]), (2.3, [-20, 0, 24], 'easeInOutSine'),
                    (3.75, [-20, 0, 24]), (4.0, [-160, 0, 34], 'easeInOutSine')]
          + roof_pumps(4.0, [-178, 0, 34], [-150, 0, 40])
          + [(6.0, [-90, 0, 20], 'easeInOutSine'), (6.4, [-96, -12, 4], 'easeOutBack'), (6.75, [-96, -12, 4]),
             (6.9, [-80, -60, 0], 'easeInQuad'), (7.4, [-80, -60, 0]), (7.6, [-150, 0, 70], 'easeOutBack'), (7.85, [-150, 0, 70]),
             (8.0, [-100, -20, 30], 'easeInOutSine')], 'lin')
    stretch(a, 'arm_r', [(0, 1), (0.6, 1.45), (2.0, 1.45), (2.3, 1), (3.75, 1), (4.0, 1.3), (6.0, 1.3), (6.4, 1.9), (6.75, 1.9),
                         (6.9, 1.3), (7.4, 1.3), (7.6, 1.5), (8.0, 1.2)])
    a.rot('arm_l', [(0, [-20, 0, -20]), (2.9, [-20, 0, -20]), (3.2, [-150, 60, -30], 'easeInOutSine'), (3.75, [-150, 60, -30]),
                    (4.0, [-160, 0, -34], 'easeInOutSine')]
          + roof_pumps(4.0, [-178, 0, -34], [-150, 0, -40])
          + [(6.0, [-30, 0, -24], 'easeInOutSine'), (6.75, [-30, 0, -24]), (6.9, [-80, 60, 0], 'easeInQuad'), (7.4, [-80, 60, 0]),
             (7.6, [-150, 0, -70], 'easeOutBack'), (7.85, [-150, 0, -70]), (8.0, [-100, 20, -30], 'easeInOutSine')], 'lin')
    stretch(a, 'arm_l', [(0, 1), (2.9, 1), (3.2, 1.45), (3.75, 1.45), (4.0, 1.3), (6.0, 1.3), (6.75, 1), (6.9, 1.3), (7.4, 1.3),
                         (7.6, 1.5), (8.0, 1.2)])
    a.rot('torso', [(0, [0, 0, 0]), (0.6, [-4, 0, -10], 'easeInOutSine'), (2.0, [-4, 0, -12]), (2.15, [0, 14, 0]), (2.3, [0, -14, 0]),
                    (2.45, [0, 12, 0]), (2.6, [0, 0, 0]), (3.2, [-4, 0, 10], 'easeInOutSine'), (3.75, [-4, 0, 12])]
          + roof_pumps(4.0, [-8, 0, 0], [6, 0, 0])
          + [(6.0, [0, 0, 0]), (6.4, [14, 0, 0], 'easeOutBack'), (6.75, [12, 0, 0]), (6.9, [24, 0, 0], 'easeInQuad'),
             (7.4, [20, 0, 0]), (7.6, [-26, 0, 0], 'easeOutBack'), (7.85, [-28, 0, 0]), (8.0, [12, 0, 0], 'easeInOutSine')], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (3.75, [0, 0, 0])] + roof_pumps(4.0, [0, 1.4, 0], [0, 0, 0])
          + [(6.0, [0, 0, 0]), (6.9, [0, -1.2, 0], 'easeInQuad'), (7.4, [0, -1.0, 0]), (7.6, [0, 1.0, 0], 'easeOutBack'),
             (8.0, [0, -1.5, 0], 'easeInOutSine')], 'lin')
    for leg, sgn in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [0, 0, 6 * sgn]), (6.75, [0, 0, 6 * sgn]), (6.9, [-10, 0, 14 * sgn], 'easeInQuad'), (7.6, [-10, 0, 16 * sgn]),
                    (8.0, [-10, 0, 12 * sgn])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (6.85, [1, 1, 1]), (6.95, [1.12, 0.92, 1.12], 'easeOutQuad'), (7.3, [1.04, 1.0, 1.04]),
                     (7.6, [1.1, 1.08, 1.1], 'easeOutBack'), (8.0, [1.04, 1.02, 1.04])], 'lin')
    for brow, sgn in (('brow_r', 1), ('brow_l', -1)):
        a.pos(brow, [(0, [0, 0.6, 0]), (6.2, [0, 0.6, 0]), (6.4, [0, -0.8, 0]), (8.0, [0, -0.8, 0])], 'lin')
        a.rot(brow, [(0, [0, 0, -8 * sgn]), (6.2, [0, 0, -8 * sgn]), (6.4, [0, 0, 18 * sgn]), (8.0, [0, 0, 18 * sgn])], 'lin')
    a.sound(0.9, 'hmm').sound(2.2, 'hmm').sound(6.4, 'grunt').sound(6.9, 'punch').sound(7.55, 'roar')
    return a


def b_p2_solo():
    """3.0 s (BossState.P2_SOLO). The singer takes his solo and the boss cannot help himself: the left
    hand up an air-guitar neck, the right hand picking, head banging on every beat, a windmill on
    the big note (1.6 s), and a finger pointed at the stand: that's my band (2.5)."""
    beat = 1.882 / 4
    a = B('p2_solo', 3.0, 'hold')
    a.rot('arm_l', [(0, [-20, 0, -20]), (0.3, [-70, 40, -30], 'easeInOutSine'), (1.4, [-74, 40, -30]), (2.3, [-74, 40, -30]),
                    (2.5, [-100, 10, -10], 'easeOutBack'), (3.0, [-100, 10, -10])], 'lin')
    stretch(a, 'arm_l', [(0, 1), (0.3, 1.6), (2.3, 1.6), (2.5, 1.8), (3.0, 1.8)])
    picks = [(0, [-20, 0, 20]), (0.3, [-50, -40, 10], 'easeInOutSine')]
    t = 0.3
    while t < 1.35:
        picks += [(round(t + 0.06, 2), [-58, -40, 10]), (round(t + 0.12, 2), [-48, -40, 10])]
        t += 0.12
    picks += [(1.45, [-190, -10, 30], 'easeOutQuad'), (1.6, [-40, 0, 40], 'easeInQuad'), (1.75, [-20, 0, 30]), (2.3, [-30, 0, 24]),
              (3.0, [-30, 0, 24])]
    a.rot('arm_r', picks, 'lin')
    stretch(a, 'arm_r', [(0, 1), (0.3, 1.3), (1.4, 1.3), (1.6, 1.6), (1.8, 1.2), (3.0, 1.1)])
    bangs = [(0, [0, 0, 0])]
    t = 0.0
    while t + beat < 2.3:
        bangs += [(round(t + beat * 0.5, 3), [-10, 0, 0]), (round(t + beat, 3), [16, 0, 0], 'easeInQuad')]
        t += beat
    bangs += [(2.5, [4, 0, 0]), (3.0, [0, 0, 0])]
    a.rot('torso', bangs, 'lin')
    a.rot('root', [(0, [0, 0, 0]), (1.4, [0, 0, 0]), (1.6, [0, 0, -8]), (2.0, [0, 0, 4]), (2.4, [0, 0, 0]), (3.0, [0, 0, 0])])
    a.sound(1.6, 'whoosh').sound(2.5, 'chuckle')
    return a


COLISEUM = [b_p2_arrive_sky, b_p2_notice, b_p2_march, b_p2_wake, b_p2_wait, b_p2_kick, b_p2_count, b_p2_groove,
            b_p2_apology, b_p2_resume, b_p2_hype, b_p2_solo]

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


def f_aura():
    """4.0 s, held (the aura farm in P2_APOLOGY). The wings open, slowly, wider and wider, the fries
    fanning out one after another, and stay spread; the crown turns, unhurried."""
    a = form_shown(F('p2_form_aura', 4.0, 'hold'))
    for side, sgn in (('r', 1), ('l', -1)):
        wing(a, side, sgn, [(0, 22, 12, 1.0), (2.5, 4, 40, 1.45, 'easeInOutSine'), (4.0, 2, 44, 1.5)], ripple=0.08)
    a.rot('fry_crown', [(0, [0, 0, 0]), (4.0, [0, 90, 0])], 'lin')
    a.sound(1.2, 'flap')
    return a


# ---- Ascended Papon's eagle wings (the "form" controller; gen_ascended.py's wing_r > wing_r2..4 and _l), after the
# Spiritcaller's (Illage & Spillage): folded down behind him, they open a segment at a time, overshoot, and beat
WING_BONES = ['wing_%s', 'wing_%s2', 'wing_%s3', 'wing_%s4']


def wings_shown(a):
    for side in ('r', 'l'):
        a.scale('wing_' + side, [(0, SHOWN)], 'lin')


def wing_keys(a, keys):
    """keys: [(t, root (x, y, z), segments' fold (y), ease)] for the right wing; the left mirrors it (y, z negated)."""
    for side, sgn in (('r', 1), ('l', -1)):
        a.rot('wing_' + side, [(t, [r[0], r[1] * sgn, r[2] * sgn]) + ((e,) if e else ()) for t, r, f, e in keys], 'lin')
        for i in range(1, 4):
            a.rot(WING_BONES[i] % side, [(t, [0, f[i - 1] * sgn, 0]) + ((e,) if e else ()) for t, r, f, e in keys], 'lin')


FOLDED = ([0, 70, -48], [60, 60, 60])


def f_wings_unfurl():
    """2.0 s: folded down behind his back, the wings swing out (0-0.5), each segment opens after the one before
    (0.35-1.1, overshooting), and one great beat down and up (1.2-2.0)."""
    a = F('a_wings_unfurl', 2.0, 'hold')
    wings_shown(a)
    r0, f0 = FOLDED
    wing_keys(a, [(0, r0, f0, None), (0.5, [0, 8, 14], [60, 60, 60], 'easeOutBack'), (0.7, [0, 4, 10], [-8, 60, 60], 'easeOutBack'),
                  (0.9, [0, 2, 8], [0, -8, 60], 'easeOutBack'), (1.1, [0, 0, 6], [0, 0, -8], 'easeOutBack'),
                  (1.3, [0, 0, 6], [2, 2, 0], None), (1.6, [0, -6, -22], [6, 8, 12], 'easeInQuad'), (2.0, [0, 0, 0], [0, 0, 0], 'easeOutQuad')])
    return a


def f_wings_sprout():
    """1.4 s (from the moment they tear out of his back): nothing, then the wings burst out folded and grow to full size
    in a heartbeat, shuddering, and fling open a segment at a time, and beat once."""
    a = F('a_wings_sprout', 1.4, 'hold')
    for side in ('r', 'l'):
        a.scale('wing_' + side, [(0, [0.05, 0.05, 0.05]), (0.12, [0.7, 0.7, 0.7], 'easeOutQuad'), (0.2, [0.6, 0.6, 0.6]),
                                 (0.32, [1.15, 1.15, 1.15], 'easeOutBack'), (0.5, [1, 1, 1])], 'lin')
    r0, f0 = FOLDED
    wing_keys(a, [(0, r0, f0, None), (0.3, [0, 30, -20], [40, 60, 60], 'easeOutQuad'), (0.45, [0, 6, 14], [0, 40, 60], 'easeOutBack'),
                  (0.6, [0, 2, 10], [0, 0, 30], 'easeOutBack'), (0.75, [0, 0, 8], [0, 0, 0], 'easeOutBack'),
                  (1.0, [0, -6, -22], [6, 8, 12], 'easeInQuad'), (1.4, [0, 0, 0], [0, 0, 0], 'easeOutQuad')])
    return a


def f_wings_idle():
    """3.0 s loop: open, breathing, the tips stirring."""
    a = F('a_wings_idle', 3.0, True)
    wings_shown(a)
    wing_keys(a, [(0, [0, 0, 0], [0, 0, 0], None), (1.5, [0, -3, -5], [2, 3, 4], 'easeInOutSine'), (3.0, [0, 0, 0], [0, 0, 0], 'easeInOutSine')])
    return a


def f_wings_fly():
    """0.8 s loop: the beat, the outer segments lagging behind the root (a whip through the wing)."""
    a = F('a_wings_fly', 0.8, True)
    wings_shown(a)
    wing_keys(a, [(0, [0, 0, 22], [-6, -10, -14], None), (0.4, [0, 6, -30], [8, 12, 16], 'easeInOutSine'), (0.8, [0, 0, 22], [-6, -10, -14], 'easeInOutSine')])
    return a


FORM = [f_sprout, f_idle, f_fly, f_attack, f_lift, f_aura, f_wings_unfurl, f_wings_sprout, f_wings_idle, f_wings_fly]


def b_salsa_burst():
    """1.3 s (BossState.SALSA_BURST, 26 t). Face-tanked: he balls up, arms clamped round himself, shaking and
    swelling red-hot (0-0.45 s), then BURSTS (0.5 s): arms and legs flung out in a star, body stretched tall,
    a hop off the ground; and settles, panting."""
    a = angry(B('salsa_burst', 1.3, 'hold'))
    shake = [(round(0.04 * i, 2), [0.4 if i % 2 else -0.4, -2.5, 0]) for i in range(12)]
    a.pos('root', [(0, [0, 0, 0])] + shake + [(0.5, [0, 3, 0], 'easeOutExpo'), (0.75, [0, 0, 0], 'easeInQuad'), (1.3, [0, 0, 0])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (0.45, [1.18, 0.82, 1.18], 'easeInQuad'), (0.5, [0.84, 1.32, 0.84], 'easeOutExpo'),
                     (0.7, [1.06, 0.96, 1.06]), (0.9, [1, 1, 1]), (1.3, [1.02, 1.02, 1.02])], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.45, [24, 0, 0], 'easeInQuad'), (0.5, [-26, 0, 0], 'easeOutExpo'), (0.9, [6, 0, 0]),
                    (1.3, [10, 0, 0])], 'lin')
    for arm, sg in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-20, 0, 24 * sg]), (0.4, [-70, -60 * sg, 0], 'easeInQuad'), (0.5, [-40, 0, 130 * sg], 'easeOutExpo'),
                    (0.85, [-30, 0, 110 * sg]), (1.3, [10, 0, 30 * sg], 'easeInOutSine')], 'lin')
        stretch(a, arm, [(0, 1), (0.4, 1.2), (0.5, 1.6, 'easeOutExpo'), (0.85, 1.4), (1.3, 1)])
    for leg, sg in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [0, 0, 0]), (0.45, [-10, 0, 0]), (0.5, [0, 0, 30 * sg], 'easeOutExpo'), (0.8, [0, 0, 6 * sg]),
                    (1.3, [0, 0, 0])], 'lin')
    a.scale('sweat', [(0, HIDDEN), (0.1, SHOWN), (1.3, SHOWN)], 'lin')
    a.sound(0.05, 'nervous').sound(0.5, 'slam').sound(0.55, 'roar')
    return a


def b_arena_fryer():
    """2.0 s (BossState.ARENA_FRYER, 40 t: La Freidora). A crouch (0.3 s), up off the sand (0.5), and down in a
    stomp (0.7 s, the sand cracks in its pattern); a second stomp (1.2 s); then a sweep of the hand over the
    sand, "¡A LA PARRILLA!", and a chuckle."""
    a = angry(B('arena_fryer', 2.0, 'hold'))
    a.pos('root', [(0, [0, 0, 0]), (0.3, [0, -2.5, 0], 'easeOutQuad'), (0.5, [0, 7, 0], 'easeOutQuad'), (0.7, [0, -2, 0], 'easeInExpo'),
                   (0.85, [0, 0, 0]), (1.05, [0, 3, 0], 'easeOutQuad'), (1.2, [0, -2, 0], 'easeInExpo'), (1.35, [0, 0, 0]), (2.0, [0, 0, 0])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (0.3, [1.12, 0.88, 1.12]), (0.5, [0.9, 1.14, 0.9]), (0.7, [1.22, 0.8, 1.22], 'easeInExpo'),
                     (0.85, [1, 1, 1]), (1.2, [1.18, 0.84, 1.18], 'easeInExpo'), (1.35, [1, 1, 1]), (2.0, [1, 1, 1])], 'lin')
    for arm, sg in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-20, 0, 24 * sg]), (0.3, [30, 0, 30 * sg]), (0.5, [-175, 0, 30 * sg], 'easeOutQuad'),
                    (0.7, [-20, 0, 70 * sg], 'easeInExpo'), (1.05, [-160, 0, 40 * sg]), (1.2, [-20, 0, 70 * sg], 'easeInExpo'),
                    (1.5, [-60, 40 * sg, 40 * sg] if sg > 0 else [-20, 0, -30], 'easeInOutSine'),
                    (1.8, [-60, -40, 40] if sg > 0 else [-20, 0, -30], 'easeInOutSine'), (2.0, [-20, 0, 24 * sg])], 'lin')
        stretch(a, arm, [(0, 1), (0.5, 1.5), (0.7, 1.2), (1.05, 1.4), (1.2, 1.2), (1.5, 1.5 if sg > 0 else 1), (2.0, 1)])
    for leg, sg in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [0, 0, 0]), (0.3, [-20, 0, 8 * sg]), (0.5, [20, 0, 12 * sg]), (0.7, [-30, 0, 16 * sg], 'easeInExpo'),
                    (0.9, [0, 0, 8 * sg]), (1.2, [-26, 0, 14 * sg], 'easeInExpo'), (1.5, [0, 0, 0])], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.3, [20, 0, 0]), (0.5, [-14, 0, 0]), (0.7, [26, 0, 0], 'easeInExpo'), (1.0, [4, 0, 0]),
                    (1.2, [24, 0, 0], 'easeInExpo'), (1.5, [6, 0, 0]), (1.6, [-6, 0, 3]), (1.7, [-2, 0, -3]), (1.8, [-6, 0, 3]), (2.0, [0, 0, 0])], 'lin')
    a.sound(0.5, 'jump').sound(0.7, 'slam').sound(1.2, 'slam').sound(1.6, 'chuckle')
    return a


def b_arena_heckle():
    """1.6 s (BossState.ARENA_HECKLE, 32 t: ¡Abucheo!). He turns to the stands with both arms up, working the
    crowd (0-0.8 s), both thumbs down (1.0 s: the boos, and they start throwing), then points at you (1.3)."""
    a = angry(B('arena_heckle', 1.6, 'hold'))
    a.rot('root', [(0, [0, 0, 0]), (0.3, [0, 70, 0], 'easeInOutSine'), (1.1, [0, 70, 0]), (1.3, [0, 0, 0], 'easeOutBack'), (1.6, [0, 0, 0])], 'lin')
    for arm, sg in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-20, 0, 24 * sg]), (0.3, [-170, 0, 30 * sg], 'easeOutBack'), (0.5, [-150, 0, 50 * sg]), (0.7, [-175, 0, 30 * sg]),
                    (0.9, [-70, -30 * sg, 30 * sg], 'easeInOutSine'), (1.0, [-50, -30 * sg, 20 * sg], 'easeOutQuad'),
                    (1.3, POINT_R if sg > 0 else [-20, 0, -30], 'easeOutBack'), (1.6, POINT_R if sg > 0 else [-20, 0, -30])], 'lin')
        stretch(a, arm, [(0, 1), (0.3, 1.4), (0.9, 1.2), (1.3, 1.7 if sg > 0 else 1), (1.6, 1.7 if sg > 0 else 1)])
    a.rot('torso', [(0, [0, 0, 0]), (0.3, [-12, 0, 0]), (0.7, [-16, 0, 0]), (1.0, [14, 0, 0], 'easeOutQuad'), (1.3, [-4, 0, 0]), (1.6, [0, 0, 0])], 'lin')
    a.sound(0.3, 'hype').sound(1.0, 'grunt')
    return a


def b_p2_masher():
    """6.5 s (BossState.P2_MASHER, 130 t: El Machacador). Up over the sand, both fists up on the masher's
    handle overhead (0.4 s), hauling it down hand over hand as it looms (0.5-1.0 s), then three slams
    (PaponBossEntity.MASHER_SLAMS: they land at 2.5, 4.1 and 5.6 s): a heave up and DOWN with all his
    weight each time, hauling it back up between them; letting go after the last (5.8 s)."""
    slams = [2.5, 4.1, 5.6]
    a = angry(B('p2_masher', 6.5, 'hold'))
    for arm, sg in (('arm_r', 1), ('arm_l', -1)):
        keys = [(0, [-20, 0, 24 * sg]), (0.4, [-175, 0, 10 * sg], 'easeOutBack')]
        for i in range(3):
            t0 = round(0.5 + 0.18 * i, 2)
            up = (i + (0 if sg > 0 else 1)) % 2 == 0
            keys.append((t0, [-175 if up else -140, 0, 10 * sg]))
        st = []
        for k, sl in enumerate(slams):
            keys += [(round(sl - 0.45, 2), [-160, 0, 10 * sg]), (round(sl - 0.2, 2), [-190, 0, 6 * sg], 'easeOutQuad'),
                     (sl, [-60, 0, 10 * sg], 'easeInExpo'), (round(sl + 0.2, 2), [-60, 0, 10 * sg])]
            st += [(round(sl - 0.45, 2), 1.6), (round(sl - 0.2, 2), 1.8), (sl, 1.4, 'easeInExpo')]
            if k < 2:
                keys += [(round(sl + 0.6, 2), [-175, 0, 10 * sg], 'easeInOutSine')]
                st += [(round(sl + 0.6, 2), 1.6)]
        keys += [(6.5, [-30, 0, 30 * sg], 'easeInOutSine')]
        a.rot(arm, keys, 'lin')
        stretch(a, arm, [(0, 1), (0.4, 1.6)] + st + [(6.5, 1)])
    torso = [(0, [0, 0, 0]), (0.4, [-20, 0, 0])]
    body = [(0, [1, 1, 1])]
    for k, sl in enumerate(slams):
        torso += [(round(sl - 0.45, 2), [-18, 0, 0]), (round(sl - 0.2, 2), [-28, 0, 0]), (sl, [34, 0, 0], 'easeInExpo'),
                  (round(sl + 0.2, 2), [30, 0, 0])]
        if k < 2:
            torso += [(round(sl + 0.6, 2), [-18, 0, 0], 'easeInOutSine')]
        body += [(round(sl - 0.2, 2), [1.06, 1.06, 1.06]), (sl, [1.2, 0.84, 1.2], 'easeInExpo'), (round(sl + 0.3, 2), [1, 1, 1])]
        a.sound(round(sl - 0.2, 2), 'roar' if k == 2 else 'grunt').sound(sl, 'slam')
    torso += [(6.5, [0, 0, 0])]
    a.rot('torso', torso, 'lin')
    a.scale('body', body, 'lin')
    a.sound(0.4, 'grunt')
    return a


def b_arena_ola():
    """1.8 s (BossState.ARENA_OLA, 36 t: ¡La Ola!). Both arms up to the stands (0.3 s), swinging them side to
    side with his whole body to start the wave going round (0.3-1.2 s), then a big sweep down at you (1.4 s)."""
    a = angry(B('arena_ola', 1.8, 'hold'))
    a.rot('root', [(0, [0, 0, 0]), (0.3, [0, 0, 0]), (0.55, [0, 30, 0], 'easeInOutSine'), (0.85, [0, -30, 0], 'easeInOutSine'),
                   (1.1, [0, 20, 0], 'easeInOutSine'), (1.4, [0, 0, 0], 'easeOutBack'), (1.8, [0, 0, 0])], 'lin')
    for arm, sg in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-20, 0, 24 * sg]), (0.3, [-170, 0, 20 * sg], 'easeOutBack'), (0.55, [-160, 0, 45 * sg], 'easeInOutSine'),
                    (0.85, [-160, 0, 5 * sg], 'easeInOutSine'), (1.1, [-165, 0, 40 * sg], 'easeInOutSine'),
                    (1.4, [-80, -20 * sg, 30 * sg], 'easeOutQuad'), (1.8, [-70, -20 * sg, 30 * sg])], 'lin')
        stretch(a, arm, [(0, 1), (0.3, 1.5), (1.1, 1.5), (1.4, 1.6), (1.8, 1.3)])
    a.rot('torso', [(0, [0, 0, 0]), (0.3, [-14, 0, 0]), (1.1, [-14, 0, 0]), (1.4, [16, 0, 0], 'easeOutQuad'), (1.8, [4, 0, 0])], 'lin')
    a.sound(0.3, 'hype').sound(1.4, 'grunt')
    return a


def b_arena_stampede():
    """1.8 s (BossState.ARENA_STAMPEDE, 36 t: ¡Estampida!). Two fingers to his mouth, a whistle to the stands
    (0.2-0.6 s), then both arms flung out to the long sides of the sand where the potatoes come from (0.8 s)."""
    a = angry(B('arena_stampede', 1.8, 'hold'))
    a.rot('arm_r', [(0, [-20, 0, 24]), (0.2, [-125, -45, 0], 'easeOutQuad'), (0.6, [-125, -45, 0]),
                    (0.8, [-90, 0, 85], 'easeOutBack'), (1.4, [-90, 0, 85]), (1.8, [-20, 0, 24], 'easeInOutSine')], 'lin')
    stretch(a, 'arm_r', [(0, 1), (0.2, 1.4), (0.6, 1.4), (0.8, 1.6), (1.4, 1.6), (1.8, 1)])
    a.rot('arm_l', [(0, [-20, 0, -24]), (0.6, [-30, 0, -30]), (0.8, [-90, 0, -85], 'easeOutBack'), (1.4, [-90, 0, -85]),
                    (1.8, [-20, 0, -24], 'easeInOutSine')], 'lin')
    stretch(a, 'arm_l', [(0, 1), (0.6, 1), (0.8, 1.6), (1.4, 1.6), (1.8, 1)])
    a.rot('torso', [(0, [0, 0, 0]), (0.2, [-8, 0, 0]), (0.6, [-12, 0, 0]), (0.8, [6, 0, 0], 'easeOutQuad'), (1.8, [0, 0, 0])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (0.25, [1, 1, 1]), (0.5, [1.06, 0.96, 1.06]), (0.6, [1, 1, 1])], 'lin')
    a.sound(0.8, 'hype')
    return a


HARDENING = [b_salsa_burst, b_arena_fryer, b_arena_heckle, b_p2_masher, b_arena_ola, b_arena_stampede]


# ===================================================================== phase three: "Para Siempre"

def guitar_poses():
    """His hands on the singer's requinto (p3_guitar, on his belly where the singer holds it), solved on his rig
    like the band's (band_ik): the strumming fist over the soundhole, the fretting hand low and high on the
    neck. client/BossModel keeps the same numbers (STRUM, FRET_LOW, FRET_HIGH) and moves between them."""
    import numpy as np
    import band_ik
    band_ik.GEO = os.path.join(ROOT, 'src/main/resources/assets/papon/geo/entity/papon_boss.geo.json')
    band_ik.RIG = None
    R = band_ik.rig()
    piv = R.pivot('p3_guitar')
    k = GUITAR_SCALE
    xs, _ = band_ik.reach('r', 'hand_r', R.pivot('hand_r'), R.to_world('p3_guitar', piv + k * np.array([0, 0.5, -3.8]), {}),
                          np.array([-50, -40, 0, 2.0]))
    out = {'strum': [round(float(v), 1) for v in xs]}
    x0 = np.array([-60, 30, 0, 2.0])
    for name, y in (('low', 5.0), ('mid', 7.5), ('high', 10.5)):
        x, _ = band_ik.reach('l', 'hand_l', R.pivot('hand_l'), R.to_world('p3_guitar', piv + k * np.array([0, y, -3.0]), {}), x0)
        out[name] = [round(float(v), 1) for v in x]
        x0 = x
    return out


GUITAR = None
GUITAR_SCALE = 1.5
SHOWN_G = [GUITAR_SCALE] * 3     # the requinto as he holds it (both bones), bigger than on the singer


def gp(name):
    global GUITAR
    if GUITAR is None:
        GUITAR = guitar_poses()
    return GUITAR[name]


def guitar_arms(a, t0, t1, ease='easeInOutSine'):
    """Both hands onto the requinto by t0 and held there to t1 (the strum, the fret hand mid-neck)."""
    st, md = gp('strum'), gp('mid')
    return st, md


def b_p3_knockback():
    """2.5 s (BossState.P3_KNOCKBACK). The hit lands (0): he folds round it, arms and legs flung forward; flung back
    through the air (the entity flies him, 0-0.7), tipping back as he goes; down flat on his back (0.7) with a
    squash and his limbs flopping out; the skid (to 1.3), a shudder; still, his chest heaving; he sits up with a
    hand pushing on the sand (1.7-2.1), onto one knee and up (to 2.5), hunched."""
    a = B('p3_knockback', 2.5, 'hold')
    a.scale('blood', [(0, SHOWN), (2.5, SHOWN)], 'lin')
    a.rot('root', [(0, [0, 0, 0]), (0.06, [-10, 0, 0], 'easeOutQuad'), (0.65, [-62, 0, 0], 'easeInQuad'), (0.7, [-88, 0, 0]),
                   (0.78, [-84, 0, 0]), (0.9, [-88, 0, 3]), (1.0, [-88, 0, -3]), (1.1, [-88, 0, 2]), (1.3, [-88, 0, 0]),
                   (1.7, [-88, 0, 0]), (2.05, [-34, 0, 0], 'easeInOutSine'), (2.3, [-10, 0, 0], 'easeOutQuad'), (2.5, [0, 0, 0])], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (0.6, [0, 1.5, 0]), (0.7, [0, 3.2, 0]), (1.7, [0, 3.2, 0]), (2.05, [0, 1.0, 0], 'easeInOutSine'),
                   (2.3, [0, -1.6, 0], 'easeOutQuad'), (2.5, [0, -0.8, 0])], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.05, [34, 0, 0], 'easeOutExpo'), (0.6, [24, 0, 0]), (0.7, [-6, 0, 0], 'easeOutQuad'),
                    (1.7, [0, 0, 0]), (2.05, [26, 0, 0], 'easeInOutSine'), (2.5, [18, 0, 0])], 'lin')
    for arm, sgn in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-20, 0, 20 * sgn]), (0.05, [-80, 20 * sgn, 30 * sgn], 'easeOutExpo'), (0.6, [-110, 10 * sgn, 40 * sgn]),
                    (0.72, [-150, 0, 80 * sgn], 'easeOutQuad'), (1.7, [-150, 0, 76 * sgn]),
                    (2.0, [30 if sgn > 0 else -20, 0, 30 * sgn], 'easeInOutSine'), (2.3, [-10, 0, 24 * sgn]), (2.5, [-6, 0, 18 * sgn])], 'lin')
    stretch(a, 'arm_r', [(0, 1), (1.7, 1), (1.95, 1.5), (2.2, 1.2), (2.5, 1)])
    for leg, sgn in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [0, 0, 0]), (0.05, [-40, 0, 6 * sgn], 'easeOutExpo'), (0.6, [-50, 0, 10 * sgn]), (0.72, [-20, 0, 16 * sgn]),
                    (1.7, [-14, 0, 16 * sgn]), (2.05, [-70 if sgn > 0 else 10, 0, 6 * sgn], 'easeInOutSine'), (2.3, [-30 if sgn > 0 else 0, 0, 0]),
                    (2.5, [-8, 0, 6 * sgn])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (0.05, [1.1, 0.88, 1.1], 'easeOutExpo'), (0.6, [1, 1, 1]), (0.7, [1.24, 0.78, 1.24]),
                     (0.85, [0.96, 1.04, 0.96], 'easeOutQuad'), (1.0, [1, 1, 1]),
                     (1.35, [1.03, 1.03, 1.03]), (1.5, [1, 0.98, 1]), (1.65, [1.03, 1.03, 1.03]), (2.5, [1.02, 0.98, 1.02])], 'lin')
    for brow, sgn in (('brow_r', 1), ('brow_l', -1)):
        a.pos(brow, [(0, [0, 0.8, 0]), (1.8, [0, 0.4, 0]), (2.5, [0, -0.8, 0])], 'lin')
        a.rot(brow, [(0, [0, 0, -12 * sgn]), (1.8, [0, 0, -6 * sgn]), (2.5, [0, 0, 18 * sgn])], 'lin')
    a.sound(0.0, 'punch').sound(0.05, 'whoosh').sound(0.7, 'slam').sound(0.75, 'grunt').sound(1.9, 'grunt')
    return a


def b_p3_glare():
    """BossState.P3_GLARE (beats glare.*). Up, hunched and heaving; the blood thumbed off his lip (0.6-1.4); his
    eyes go to the band and he straightens, slowly; "This... is NOT... my style." with a slow shake of the head and
    a flick of the back of his hand at the stand on "NOT"; "Let me show you how to REALLY play.": a thumb to his
    own chest on "me", a finger at the singer on "REALLY", and he holds it."""
    ns, rp, end = beat('glare.notstyle', 40), beat('glare.realplay', 100), beat('glare.end', 160)
    a = angry(B('p3_glare', end, 'hold'))
    a.scale('blood', [(0, SHOWN), (1.15, SHOWN), (1.3, HIDDEN, 'easeInQuad'), (end, HIDDEN)], 'lin')
    a.pos('root', [(0, [0, -0.8, 0]), (1.6, [0, -0.6, 0]), (2.4, [0, 0, 0], 'easeInOutSine'), (end, [0, 0, 0])], 'lin')
    a.rot('torso', [(0, [18, 0, 0]), (0.6, [16, 0, 0]), (1.6, [14, 0, 0]), (2.4, [2, 0, 0], 'easeInOutSine')]
          + [(round(ns + 0.5 + 0.55 * i, 2), [2, 7 if i % 2 == 0 else -7, 0]) for i in range(3)]
          + [(round(ns + 2.2, 2), [0, 0, 0]), (rp, [-4, 0, 0]), (round(rp + 0.6, 2), [-6, 0, 0]), (end, [-4, 0, 0])])
    a.scale('body', [(round(0.4 * i, 2), [1.03, 1.03, 1.03] if i % 2 == 0 else [1, 0.98, 1]) for i in range(5)] + [(2.4, [1, 1, 1]), (end, [1, 1, 1])])
    flick = round(ns + 0.75, 2)
    a.rot('arm_r', [(0, [-6, 0, 18]), (0.55, [-6, 0, 18]), (0.9, DRINK_R, 'easeInOutSine'), (1.05, [-92, -42, 0]),
                    (1.25, [-80, -60, 10], 'easeInOutSine'), (1.5, [-6, 0, 18], 'easeInOutSine'), (round(rp + 0.05, 2), [-6, 0, 18]),
                    (round(rp + 0.35, 2), [-72, -64, 0], 'easeInOutSine'), (round(rp + 0.7, 2), [-72, -64, 0]),
                    (round(rp + 1.1, 2), [-96, -6, 6], 'easeOutBack'), (end, [-96, -6, 6])], 'lin')
    stretch(a, 'arm_r', [(0, 1), (0.55, 1), (0.9, 1.6), (1.25, 1.5), (1.5, 1), (round(rp + 0.05, 2), 1), (round(rp + 0.35, 2), 1.15),
                         (round(rp + 0.7, 2), 1.15), (round(rp + 1.1, 2), 1.9), (end, 1.9)])
    a.rot('arm_l', [(0, [-6, 0, -18]), (round(flick - 0.3, 2), [-6, 0, -18]), (round(flick - 0.05, 2), [-70, 40, -10], 'easeInOutSine'),
                    (round(flick + 0.12, 2), [-64, -20, -60], 'easeOutQuad'), (round(flick + 0.6, 2), [-60, -20, -60]),
                    (round(flick + 1.0, 2), [-6, 0, -18], 'easeInOutSine'), (end, [-6, 0, -18])], 'lin')
    stretch(a, 'arm_l', [(0, 1), (round(flick - 0.3, 2), 1), (round(flick + 0.12, 2), 1.4), (round(flick + 0.6, 2), 1.4), (round(flick + 1.0, 2), 1)])
    a.sound(0.9, 'cloth').sound(flick, 'flick').sound(round(rp + 1.1, 2), 'snap')
    return a


def b_p3_glare_hold():
    """2.0 s loop: hunched, fists, breathing hard (the stalk when he is not moving)."""
    a = angry(B('p3_glare_hold', 2.0, True))
    a.rot('torso', [(0, [10, 0, 0]), (1.0, [12, 0, 0]), (2.0, [10, 0, 0])])
    a.scale('body', [(0, [1.02, 1.02, 1.02]), (1.0, [1, 0.98, 1]), (2.0, [1.02, 1.02, 1.02])])
    a.rot('arm_r', [(0, [-6, 0, 20])], 'lin')
    a.rot('arm_l', [(0, [-6, 0, -20])], 'lin')
    return a


def b_p3_stalk():
    """1.2 s loop. The walk to the band stand: slow, heavy, head down and eyes up, fists, each foot planted (a
    step every 0.6 s: the thuds are the server's)."""
    a = angry(B('p3_stalk', 1.2, True))
    a.rot('leg_r', [(0, [26, 0, 4]), (0.6, [-22, 0, 4], 'easeInOutSine'), (1.2, [26, 0, 4], 'easeInOutSine')])
    a.rot('leg_l', [(0, [-22, 0, -4]), (0.6, [26, 0, -4], 'easeInOutSine'), (1.2, [-22, 0, -4], 'easeInOutSine')])
    a.rot('arm_r', [(0, [-14, 0, 18]), (0.6, [12, 0, 22]), (1.2, [-14, 0, 18])])
    a.rot('arm_l', [(0, [12, 0, -22]), (0.6, [-14, 0, -18]), (1.2, [12, 0, -22])])
    a.rot('torso', [(0, [12, 3, 0]), (0.6, [12, -3, 0]), (1.2, [12, 3, 0])])
    a.pos('root', [(0, [0, 0, 0]), (0.15, [0, -0.6, 0], 'easeOutQuad'), (0.45, [0, 0.4, 0]), (0.6, [0, 0, 0]),
                   (0.75, [0, -0.6, 0], 'easeOutQuad'), (1.05, [0, 0.4, 0]), (1.2, [0, 0, 0])], 'lin')
    a.rot('root', [(0, [0, 0, -3]), (0.6, [0, 0, 3]), (1.2, [0, 0, -3])])
    a.scale('body', [(0, [1.04, 0.96, 1.04]), (0.15, [1.06, 0.94, 1.06]), (0.45, [0.99, 1.02, 0.99]), (0.6, [1.04, 0.96, 1.04]),
                     (0.75, [1.06, 0.94, 1.06]), (1.05, [0.99, 1.02, 0.99]), (1.2, [1.04, 0.96, 1.04])])
    return a


def b_p3_snatch():
    """BossState.P3_SNATCH (beats snatch.*). He looms (drawn up tall, leaning back), a hand out, palm up, with a
    flick of the fingers: "Give me THAT."; on the word (snatch.grab) the hand shoots out to the requinto on the
    singer's belly and rips it away, up over his head by the neck like a trophy; then he swings it down onto
    his own belly, the left hand on the neck (it stays slung there until he plays it)."""
    g, grab, end = beat('snatch.gimme', 6), beat('snatch.grab', 22), beat('snatch.end', 56)
    sw = round(grab + 0.95, 2)              # swung down onto his belly
    a = angry(B('p3_snatch', end, 'hold'))
    md = gp('mid')
    a.scale('p3_guitar_carry', [(0, HIDDEN), (round(grab - 0.01, 2), HIDDEN), (grab, SHOWN_G), (round(sw - 0.01, 2), SHOWN_G), (sw, HIDDEN), (end, HIDDEN)], 'lin')
    a.scale('p3_guitar', [(0, HIDDEN), (round(sw - 0.01, 2), HIDDEN), (sw, SHOWN_G), (end, SHOWN_G)], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (0.3, [0.97, 1.08, 0.97], 'easeOutQuad'), (round(grab - 0.15, 2), [0.97, 1.08, 0.97]),
                     (grab, [1.04, 0.98, 1.04], 'easeOutQuad'), (round(grab + 0.4, 2), [1, 1.02, 1]), (end, [1, 1, 1])], 'lin')
    a.rot('torso', [(0, [6, 0, 0]), (0.3, [-8, 0, 0], 'easeOutQuad'), (round(grab - 0.15, 2), [-8, 0, 0]), (grab, [18, 0, 0], 'easeOutQuad'),
                    (round(grab + 0.3, 2), [-12, -12, 0], 'easeOutBack'), (round(sw - 0.1, 2), [-10, -6, 0]), (sw, [6, 0, 0], 'easeInQuad'),
                    (end, [0, 0, 0])], 'lin')
    a.rot('arm_r', [(0, [-6, 0, 18]), (round(g + 0.1, 2), [-6, 0, 18]), (round(g + 0.35, 2), [-80, -14, 0], 'easeInOutSine'),
                    (round(g + 0.55, 2), [-96, -14, 0], 'easeOutQuad'), (round(g + 0.7, 2), [-80, -14, 0]),
                    (round(grab - 0.15, 2), [-80, -14, 0]), (grab, [-58, -24, 0], 'easeInQuad'),
                    (round(grab + 0.25, 2), [-168, -6, 24], 'easeOutBack'), (round(sw - 0.15, 2), [-160, -6, 28]),
                    (sw, [-40, -10, 18], 'easeInQuad'), (round(sw + 0.4, 2), [-14, 0, 20], 'easeOutQuad'), (end, [-14, 0, 20])], 'lin')
    stretch(a, 'arm_r', [(0, 1), (round(g + 0.1, 2), 1), (round(g + 0.35, 2), 1.4), (round(grab - 0.15, 2), 1.4), (grab, 2.1),
                         (round(grab + 0.25, 2), 1.3), (round(sw - 0.15, 2), 1.3), (sw, 1.2), (round(sw + 0.4, 2), 1)])
    a.rot('arm_l', [(0, [-6, 0, -18]), (round(sw - 0.1, 2), [-6, 0, -18]), (round(sw + 0.3, 2), md[:3], 'easeInOutSine'), (end, md[:3])], 'lin')
    stretch(a, 'arm_l', [(0, 1), (round(sw - 0.1, 2), 1), (round(sw + 0.3, 2), md[3]), (end, md[3])])
    a.sound(round(g + 0.55, 2), 'flick').sound(grab, 'rip').sound(round(grab + 0.05, 2), 'strum').sound(sw, 'cloth').sound(round(sw + 0.1, 2), 'strum')
    return a


def guitar_slung(a, t0=0):
    """The requinto slung on his belly, the left hand on its neck (from t0)."""
    md = gp('mid')
    a.scale('p3_guitar', [(t0, SHOWN_G)], 'lin')
    a.rot('arm_l', [(t0, md[:3])], 'lin')
    stretch(a, 'arm_l', [(t0, md[3])])


def b_p3_carry():
    """1.2 s loop. Back to the middle of the sand with the requinto slung on his belly, the left hand on its
    neck: an unhurried walk, chin up, the free arm swinging."""
    a = B('p3_carry', 1.2, True)
    guitar_slung(a)
    a.rot('leg_r', [(0, [22, 0, 4]), (0.6, [-20, 0, 4], 'easeInOutSine'), (1.2, [22, 0, 4], 'easeInOutSine')])
    a.rot('leg_l', [(0, [-20, 0, -4]), (0.6, [22, 0, -4], 'easeInOutSine'), (1.2, [-20, 0, -4], 'easeInOutSine')])
    a.rot('arm_r', [(0, [-24, 0, 20]), (0.6, [10, 0, 22]), (1.2, [-24, 0, 20])])
    a.rot('torso', [(0, [-4, 3, 0]), (0.6, [-4, -3, 0]), (1.2, [-4, 3, 0])])
    a.pos('root', [(0, [0, 0, 0]), (0.3, [0, 0.6, 0]), (0.6, [0, 0, 0]), (0.9, [0, 0.6, 0]), (1.2, [0, 0, 0])])
    for brow, sgn in (('brow_r', 1), ('brow_l', -1)):
        a.pos(brow, [(0, [0, -0.3, 0])], 'lin')
        a.rot(brow, [(0, [0, 0, 8 * sgn])], 'lin')
    return a


def b_p3_carry_hold():
    """2.0 s loop: standing, the requinto slung on his belly."""
    a = B('p3_carry_hold', 2.0, True)
    guitar_slung(a)
    a.rot('arm_r', [(0, [-14, 0, 20])], 'lin')
    a.rot('torso', [(0, [-2, 0, 0]), (1.0, [-3, 0, 0]), (2.0, [-2, 0, 0])])
    return a


def b_p3_blackout():
    """2.5 s (BossState.P3_BLACKOUT). Head bowed in the dark, the requinto on his belly (0-1.5); the spotlight
    slams on (1.5) and he does not move; then, slowly, his right hand comes up onto the strings (1.7-2.3) and
    his fingers find the chord, head still down."""
    st = gp('strum')
    a = B('p3_blackout', 2.5, 'hold')
    guitar_slung(a)
    a.rot('torso', [(0, [0, 0, 0]), (0.6, [16, 0, 0], 'easeInOutSine'), (2.5, [16, 0, 0])], 'lin')
    a.rot('arm_r', [(0, [-14, 0, 20]), (1.7, [-14, 0, 20]), (2.3, st[:3], 'easeInOutSine'), (2.5, st[:3])], 'lin')
    stretch(a, 'arm_r', [(0, 1), (1.7, 1), (2.3, st[3]), (2.5, st[3])])
    a.scale('body', [(0, [1, 1, 1]), (0.6, [1.02, 0.98, 1.02]), (2.5, [1.02, 0.98, 1.02])])
    a.sound(2.35, 'strum')
    return a


def b_p3_serenade():
    """BossState.P3_SERENADE (to serenade.end): the song's first 18 s, his. The hands are client/BossModel's (the
    record's strums, chord shapes along the neck); this is the rest of him: over the strings through the guitar
    intro, head bowed, swaying with it; his head comes up with his voice (11.2 s into the song, with the 0.2 s it
    starts after the state) and goes back on the long notes, his heart in it; down over the guitar again for the
    run after the line; and on the last chord the strumming hand flung to the sky, held."""
    off = beat('serenade.start', 4)
    end = beat('serenade.end', 380)
    v0, v1, last = round(off + 11.0, 2), round(off + 15.6, 2), round(off + 18.45, 2)
    st, md = gp('strum'), gp('mid')
    a = B('p3_serenade', end, 'hold')
    a.scale('p3_guitar', [(0, SHOWN_G), (end, SHOWN_G)], 'lin')
    a.rot('arm_r', [(0, st[:3]), (round(last - 0.1, 2), st[:3]), (round(last + 0.15, 2), [-168, -6, 42], 'easeOutBack'), (end, [-170, -6, 44])], 'lin')
    stretch(a, 'arm_r', [(0, st[3]), (round(last - 0.1, 2), st[3]), (round(last + 0.15, 2), 1.5), (end, 1.5)])
    a.rot('arm_l', [(0, md[:3]), (end, md[:3])], 'lin')
    stretch(a, 'arm_l', [(0, md[3]), (end, md[3])])
    sway = 3.48         # two bars of the 6/8
    torso = [(0, [16, 0, 0]), (1.2, [10, 0, -3], 'easeInOutSine')]
    t = 1.2
    i = 0
    while t + sway / 2 < v0 - 0.4:
        t = round(t + sway / 2, 2)
        torso.append((t, [10, 0, 3 if i % 2 == 0 else -3], 'easeInOutSine'))
        i += 1
    torso += [(v0, [-8, 0, 0], 'easeInOutSine'), (round(v0 + 1.2, 2), [-14, 0, 4], 'easeInOutSine'), (round(v0 + 2.2, 2), [-6, 0, -3], 'easeInOutSine'),
              (round(v0 + 3.4, 2), [-16, 0, 2], 'easeInOutSine'), (v1, [-8, 0, 0]), (round(v1 + 0.6, 2), [12, 0, -3], 'easeInOutSine'),
              (round(last - 0.6, 2), [14, 0, 3], 'easeInOutSine'), (round(last + 0.15, 2), [-18, 0, 0], 'easeOutBack'), (end, [-20, 0, 0])]
    a.rot('torso', torso, 'lin')
    a.rot('leg_r', [(0, [0, 0, 4]), (v0, [0, 0, 4]), (round(v0 + 0.6, 2), [-8, 0, 8], 'easeInOutSine'), (end, [-8, 0, 8])], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (round(last - 0.1, 2), [0, 0, 0]), (round(last + 0.15, 2), [0, 0.8, 0], 'easeOutBack'), (end, [0, 0.6, 0])], 'lin')
    a.scale('body', [(0, [1.02, 0.98, 1.02]), (v0, [1, 1, 1]), (round(v0 + 1.2, 2), [1.04, 1.04, 1.04]), (v1, [1.02, 1.02, 1.02]),
                     (round(last + 0.15, 2), [1.06, 1.06, 1.06], 'easeOutBack'), (end, [1.05, 1.05, 1.05])], 'lin')
    for brow, sgn in (('brow_r', 1), ('brow_l', -1)):
        a.rot(brow, [(0, [0, 0, -8 * sgn]), (end, [0, 0, -8 * sgn])], 'lin')
    return a


def b_p3_verdict():
    """BossState.P3_VERDICT (beats verdict.*). The strumming hand comes down off the sky onto the strings (the
    guitar stays slung); "THIS...": that hand flat on his chest, his chin up; "...is more my style.";
    "Learn, muchachos.": a finger raised and wagged over his shoulder at the band behind him."""
    ln, end = beat('verdict.line', 10), beat('verdict.end', 80)
    say = max(1.5, end - ln - 0.5)
    q = lambda f: round(ln + say * f, 2)
    a = B('p3_verdict', end, 'hold')
    guitar_slung(a)
    st = gp('strum')
    a.rot('arm_r', [(0, [-170, -6, 44]), (0.45, st[:3], 'easeInOutSine'), (q(0.05), st[:3]), (q(0.15), [-84, -62, 0], 'easeInOutSine'),
                    (q(0.5), [-84, -62, 0]), (q(0.62), [-150, 30, 30], 'easeInOutSine')]
          + [(q(0.62 + 0.06 * i), [-150, 30 + (12 if i % 2 else -12), 30]) for i in range(1, 6)]
          + [(q(1.0), [-150, 30, 30]), (end, [-150, 30, 30])], 'lin')
    stretch(a, 'arm_r', [(0, 1.5), (0.45, st[3]), (q(0.05), st[3]), (q(0.15), 1.3), (q(0.5), 1.3), (q(0.62), 1.4), (end, 1.4)])
    a.rot('torso', [(0, [-20, 0, 0]), (0.5, [-4, 0, 0], 'easeInOutSine'), (q(0.15), [-10, 0, 0]), (q(0.5), [-6, 0, 0]),
                    (q(0.62), [-4, -14, 0], 'easeInOutSine'), (end, [-4, -14, 0])], 'lin')
    for brow, sgn in (('brow_r', 1), ('brow_l', -1)):
        a.pos(brow, [(0, [0, 0.4, 0]), (q(0.5), [0, 0.4, 0]), (q(0.62), [0, 0.9 if sgn > 0 else -0.2, 0]), (end, [0, 0.9 if sgn > 0 else -0.2, 0])], 'lin')
    a.sound(q(0.15), 'pat')
    return a


def b_p3_toss():
    """BossState.P3_TOSS (beats toss.*). The requinto swung up off his belly and flung back over his shoulder
    without a look (toss.throw); arms folded, chin up, he does not watch it go. Then nothing happens: the band
    look at each other (toss.glance on); his foot starts tapping, his head tips over, a long breath out."""
    thr, gl, end = beat('toss.throw', 20), beat('toss.glance', 58), beat('toss.end', 98)
    a = B('p3_toss', end, 'hold')
    a.scale('p3_guitar', [(0, SHOWN_G), (0.04, SHOWN_G), (0.05, HIDDEN), (end, HIDDEN)], 'lin')
    a.scale('p3_guitar_carry', [(0, HIDDEN), (0.04, HIDDEN), (0.05, SHOWN_G), (round(thr - 0.01, 2), SHOWN_G), (thr, HIDDEN), (end, HIDDEN)], 'lin')
    a.rot('arm_r', [(0, gp('strum')[:3]), (0.05, [-60, -20, 10]), (round(thr - 0.2, 2), [-120, 0, 20], 'easeInOutSine'), (thr, [-190, 0, 24], 'easeOutQuad'),
                    (round(thr + 0.25, 2), [-160, 0, 30]), (round(thr + 0.6, 2), CROSS_R, 'easeInOutSine'), (end, CROSS_R)], 'lin')
    stretch(a, 'arm_r', [(0, gp('strum')[3]), (0.05, 1.3), (thr, 1.3), (round(thr + 0.6, 2), 1.25), (end, 1.25)])
    a.rot('arm_l', [(0, gp('mid')[:3]), (0.2, [-6, 0, -18], 'easeInOutSine'), (round(thr + 0.3, 2), [-6, 0, -18]),
                    (round(thr + 0.6, 2), CROSS_L, 'easeInOutSine'), (end, CROSS_L)], 'lin')
    stretch(a, 'arm_l', [(0, gp('mid')[3]), (round(thr + 0.6, 2), 1.25), (end, 1.25)])
    taps = [k for i in range(max(0, int((end - gl - 0.6) / 0.5))) for k in ((round(gl + 0.4 + 0.5 * i, 2), [-14, 0, 0]),
                                                                            (round(gl + 0.6 + 0.5 * i, 2), [0, 0, 0], 'easeInQuad'))]
    a.rot('leg_r', [(0, [0, 0, 0]), (round(gl + 0.3, 2), [0, 0, 0])] + taps + [(end, [0, 0, 0])], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (thr, [-10, 0, 0], 'easeOutQuad'), (round(thr + 0.6, 2), [-8, 0, 0]), (round(gl + 0.6, 2), [-8, 0, 0]),
                    (round(gl + 1.2, 2), [-4, 0, 9], 'easeInOutSine'), (end, [-4, 0, 9])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (round(end - 0.9, 2), [1, 1, 1]), (round(end - 0.6, 2), [1.04, 1.05, 1.04], 'easeInOutSine'),
                     (round(end - 0.2, 2), [0.98, 0.97, 0.98], 'easeInOutSine'), (end, [1, 1, 1])], 'lin')
    a.sound(thr, 'whoosh').sound(round(end - 0.5, 2), 'sigh')
    return a


def b_p3_felt():
    """BossState.P3_FELT (beats felt.*). Ascended: arms loose and a little out from his sides, palms open, feeling
    it; a long breath in that swells him; his head rolls round once, slowly; "Hmmm... I haven't felt this way...
    in a LOOOOONG time." with his fists closing on "LOOOOONG"; and the smile (felt.smile): chin down, shoulders
    up, a slow lean toward you, held."""
    ln, sm, end = beat('felt.line', 14), beat('felt.smile', 100), beat('felt.end', 140)
    a = B('p3_felt', end, 'hold')
    long_ = round(ln + (sm - ln) * 0.62, 2)
    for arm, sgn in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-10, 0, 20 * sgn]), (0.8, [-24, 0, 44 * sgn], 'easeInOutSine'), (long_, [-30, 0, 48 * sgn]),
                    (round(long_ + 0.3, 2), [-36, -10 * sgn, 34 * sgn], 'easeOutQuad'), (sm, [-30, -10 * sgn, 26 * sgn]),
                    (round(sm + 0.5, 2), [-50, -24 * sgn, 22 * sgn], 'easeInOutSine'), (end, [-50, -24 * sgn, 22 * sgn])], 'lin')
        stretch(a, arm, [(0, 1), (0.8, 1.15), (long_, 1.2), (round(long_ + 0.3, 2), 1.3), (end, 1.3)])
    a.scale('body', [(0, [1, 1, 1]), (round(ln - 0.1, 2), [1.07, 1.08, 1.07], 'easeInOutSine'), (round(ln + 0.6, 2), [1.02, 1.02, 1.02]),
                     (long_, [1.04, 1.06, 1.04]), (round(long_ + 0.3, 2), [1.08, 1.0, 1.08], 'easeOutQuad'), (end, [1.05, 1.02, 1.05])], 'lin')
    roll = [(round(ln + 0.4 + 0.25 * i, 2), [6 * math.cos(i * 1.57), 0, 8 * math.sin(i * 1.57)]) for i in range(5)]
    a.rot('torso', [(0, [0, 0, 0]), (ln, [-6, 0, 0], 'easeInOutSine')] + roll
          + [(sm, [0, 0, 0]), (round(sm + 0.6, 2), [16, 0, 0], 'easeInOutSine'), (end, [16, 0, 0])], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (sm, [0, 0, 0]), (round(sm + 0.6, 2), [0, -1.0, -1.0], 'easeInOutSine'), (end, [0, -1.0, -1.0])], 'lin')
    a.sound(round(ln - 0.6, 2), 'breath').sound(round(long_ + 0.3, 2), 'crack').sound(round(sm + 0.2, 2), 'chuckle')
    return a


def b_p3_crush():
    """BossState.P3_CRUSH (beats crush.*). Both arms up under the van as it comes up over his head (to
    crush.lifted); crushed between his hands, pressing in, shaking with the effort (crush.squeeze to crush.ball);
    the ball up in his right fist, glowing (to crush.reform); the flash, and down it comes in both hands to the
    ground in front of him (crush.down), the slam; he straightens, a hand on his hip, and admires it."""
    lifted, sq, ball, ref, down, end = (beat('crush.lifted', 14), beat('crush.squeeze', 18), beat('crush.ball', 42), beat('crush.reform', 52),
                                        beat('crush.down', 66), beat('crush.end', 96))
    a = angry(B('p3_crush', end, 'hold'))
    n = max(1, int((ball - sq) / 0.1))
    shake = [(round(sq + 0.1 * i, 2), [-170 + (6 if i % 2 else -6), 0, 24]) for i in range(n)]
    a.rot('arm_r', [(0, [-20, 0, 20]), (round(lifted * 0.5, 2), [-80, 0, 30], 'easeInOutSine'), (lifted, [-170, 0, 32], 'easeOutQuad'),
                    (sq, [-170, 0, 24])] + shake + [(ball, [-176, 0, 10], 'easeOutBack'), (ref, [-176, 0, 10]),
                   (round((ref + down) / 2, 2), [-110, 0, 20], 'easeInOutSine'), (down, [-40, 0, 24], 'easeInQuad'),
                   (round(down + 0.4, 2), [10, 30, 40], 'easeOutQuad'), (end, [10, 30, 40])], 'lin')
    stretch(a, 'arm_r', [(0, 1), (lifted, 1.6), (ball, 1.5), (ref, 1.6), (down, 1.6), (round(down + 0.4, 2), 1.2), (end, 1.2)])
    shake_l = [(round(sq + 0.1 * i, 2), [-170 + (-6 if i % 2 else 6), 0, -24]) for i in range(n)]
    a.rot('arm_l', [(0, [-20, 0, -20]), (round(lifted * 0.5, 2), [-80, 0, -30], 'easeInOutSine'), (lifted, [-170, 0, -32], 'easeOutQuad'),
                    (sq, [-170, 0, -24])] + shake_l + [(ball, [-60, 20, -30], 'easeInOutSine'), (ref, [-60, 20, -30]),
                   (round((ref + down) / 2, 2), [-110, 0, -20], 'easeInOutSine'), (down, [-40, 0, -24], 'easeInQuad'),
                   (round(down + 0.4, 2), [-14, 0, -20], 'easeOutQuad'), (end, [-14, 0, -20])], 'lin')
    stretch(a, 'arm_l', [(0, 1), (lifted, 1.6), (ball, 1.3), (ref, 1.3), (down, 1.6), (round(down + 0.4, 2), 1), (end, 1)])
    a.rot('torso', [(0, [0, 0, 0]), (lifted, [-14, 0, 0], 'easeInOutSine'), (sq, [-12, 0, 0]), (ball, [-8, 0, 0]),
                    (down, [26, 0, 0], 'easeInQuad'), (round(down + 0.5, 2), [-6, 0, 0], 'easeOutQuad'), (end, [-6, 8, 0])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (sq, [1, 1, 1])]
            + [(round(sq + 0.2 * i, 2), [1.06, 0.96, 1.06] if i % 2 else [1.02, 1.03, 1.02]) for i in range(max(1, int((ball - sq) / 0.2)))]
            + [(ball, [1, 1, 1]), (down, [1.12, 0.9, 1.12], 'easeInQuad'), (round(down + 0.4, 2), [1, 1.02, 1], 'easeOutBack'), (end, [1.02, 1.02, 1.02])], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (down, [0, -1.6, 0], 'easeInQuad'), (round(down + 0.5, 2), [0, 0, 0], 'easeOutQuad'), (end, [0, 0, 0])], 'lin')
    a.sound(round(lifted * 0.5, 2), 'lift').sound(sq, 'grunt').sound(ball, 'roar_small').sound(down, 'slam')
    return a


def b_p3_go():
    """BossState.P3_GO (beats go.*). He whirls round on the band (the entity turns him), both arms flung out at
    them: "What are you WAITING for?! GO!!!", and a finger thrust at the singer."""
    ln, end = beat('go.line', 8), beat('go.end', 64)
    a = angry(B('p3_go', end, 'hold'))
    a.rot('arm_r', [(0, [-20, 0, 20]), (round(ln / 2, 2), [-70, 0, 74], 'easeOutBack'), (round(ln + 0.6, 2), [-70, 0, 74]),
                    (round(ln + 1.0, 2), [-96, -10, 8], 'easeOutBack'), (end, [-96, -10, 8])], 'lin')
    stretch(a, 'arm_r', [(0, 1), (round(ln / 2, 2), 1.5), (round(ln + 1.0, 2), 1.9), (end, 1.9)])
    a.rot('arm_l', [(0, [-20, 0, -20]), (round(ln / 2, 2), [-70, 0, -74], 'easeOutBack'), (end, [-60, 0, -60])], 'lin')
    stretch(a, 'arm_l', [(0, 1), (round(ln / 2, 2), 1.5), (end, 1.3)])
    a.rot('torso', [(0, [0, 0, 0]), (round(ln / 2, 2), [16, 0, 0], 'easeOutBack'), (round(ln + 0.8, 2), [12, 0, 0]), (end, [8, 0, 0])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (round(ln / 2, 2), [1.08, 1.06, 1.08], 'easeOutBack'), (end, [1.03, 1.02, 1.03])], 'lin')
    a.sound(0.0, 'swing')
    return a


def b_p3_dance():
    """BossState.P3_DANCE (beats dance.*). Turned to you while the singer sings the pickup, head on one side,
    listening; the band crashes in (dance.drop) and it goes through him: chest out, arms back, head back, the fire
    roaring up round the bowl; he rolls his shoulders, cracks his neck one way and the other; and at the very
    end, very quietly, "Now... let's DANCE." (dance.line): down into his stance, the left hand behind his back,
    the right held out to you, palm up, two fingers curling: come here."""
    drop, ln, end = beat('dance.drop', 42), beat('dance.line', 86), beat('dance.end', 150)
    a = B('p3_dance', end, 'hold')
    d1, d2 = round(drop + 0.3, 2), round(drop + 0.9, 2)
    c1, c2 = round(d2 + 0.35, 2), round(d2 + 0.7, 2)
    a.rot('torso', [(0, [0, 0, 8]), (round(drop - 0.1, 2), [0, 0, 8]), (d1, [-26, 0, 0], 'easeOutBack'), (d2, [-6, 0, 0], 'easeInOutSine'),
                    (round(c1 - 0.05, 2), [-4, 0, 0]), (c1, [-4, 0, 14], 'easeOutQuad'), (round(c1 + 0.15, 2), [-4, 0, 0]),
                    (c2, [-4, 0, -14], 'easeOutQuad'), (round(c2 + 0.15, 2), [-2, 0, 0]), (ln, [2, 0, 0]), (round(ln + 0.6, 2), [12, 0, 0], 'easeInOutSine'),
                    (end, [12, 0, 0])], 'lin')
    a.rot('arm_r', [(0, [-6, 0, 16]), (round(drop - 0.1, 2), [-6, 0, 16]), (d1, [30, 0, 50], 'easeOutBack'), (d2, [-10, 0, 22], 'easeInOutSine'),
                    (ln, [-10, 0, 22]), (round(ln + 0.6, 2), [-84, -12, 4], 'easeInOutSine')]
          + [(round(ln + 1.0 + 0.25 * i, 2), [-84 if i % 2 == 0 else -100, -12, 4]) for i in range(5)]
          + [(end, [-84, -12, 4])], 'lin')
    stretch(a, 'arm_r', [(0, 1), (ln, 1), (round(ln + 0.6, 2), 1.6), (end, 1.6)])
    a.rot('arm_l', [(0, [-6, 0, -16]), (round(drop - 0.1, 2), [-6, 0, -16]), (d1, [30, 0, -50], 'easeOutBack'), (d2, [-10, 0, -22], 'easeInOutSine'),
                    (ln, [-10, 0, -22]), (round(ln + 0.6, 2), [30, 30, -10], 'easeInOutSine'), (end, [30, 30, -10])], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (ln, [0, 0, 0]), (round(ln + 0.6, 2), [0, -1.4, 0], 'easeInOutSine'), (end, [0, -1.4, 0])], 'lin')
    for leg, sgn in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [0, 0, 4 * sgn]), (ln, [0, 0, 4 * sgn]), (round(ln + 0.6, 2), [-14 if sgn > 0 else 10, 0, 12 * sgn], 'easeInOutSine'),
                    (end, [-14 if sgn > 0 else 10, 0, 12 * sgn])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (round(drop - 0.1, 2), [1, 1, 1]), (d1, [1.12, 1.1, 1.12], 'easeOutBack'), (d2, [1.03, 1.03, 1.03]),
                     (end, [1.03, 1.03, 1.03])], 'lin')
    angry(a, ln)
    a.sound(d1, 'roar_small').sound(c1, 'crack').sound(c2, 'crack').sound(round(ln + 0.6, 2), 'grunt')
    return a


def b_p3_ascend():
    """BossState.P3_ASCEND (beats ascend.*). In the spotlight, the requinto slung on his belly: he doubles over,
    hands on his knees, groaning (groan1), his back heaving (the body swelling and shuddering), worse (groan2), a
    hand clawing at his back; then the wings tear out of it (ascend.burst): thrown upright, arms flung back, chest
    out; the roar (ascend.roar), head back, arms wide; and he settles, his hands finding the guitar's strings."""
    g1, g2, burst, roar, end = beat('ascend.groan1', 14), beat('ascend.groan2', 60), beat('ascend.burst', 100), beat('ascend.roar', 108), beat('ascend.end', 150)
    st = gp('strum')
    a = B('p3_ascend', end, 'hold')
    guitar_slung(a)
    pre = round(burst - 0.3, 2)
    heave = [(round(0.3 + 0.35 * i, 2), [1.05, 0.95, 1.05] if i % 2 else [0.98, 1.04, 0.98]) for i in range(int((pre - 0.3) / 0.35))]
    a.scale('body', [(0, [1, 1, 1])] + heave + [(pre, [1.1, 0.9, 1.1]), (burst, [0.92, 1.12, 0.92], 'easeOutExpo'),
                                                (round(burst + 0.4, 2), [1.04, 1.04, 1.04]), (roar, [1.06, 1.08, 1.06]), (end, [1.03, 1.03, 1.03])], 'lin')
    shudder = [(round(g1 + 0.1 * i, 2), [34, 0, 3 if i % 2 else -3]) for i in range(int((pre - g1) / 0.1))]
    a.rot('torso', [(0, [0, 0, 0]), (g1, [32, 0, 0], 'easeOutQuad')] + shudder
          + [(burst, [-24, 0, 0], 'easeOutExpo'), (round(burst + 0.3, 2), [-16, 0, 0]), (roar, [-30, 0, 0], 'easeOutBack'),
             (round(roar + 1.2, 2), [-26, 0, 0]), (end, [0, 0, 0], 'easeInOutSine')], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (g1, [0, -2, 0], 'easeOutQuad'), (pre, [0, -2.4, 0]), (burst, [0, 0.6, 0], 'easeOutExpo'), (end, [0, 0, 0])], 'lin')
    claw = round((g2 + pre) / 2, 2)
    a.rot('arm_r', [(0, [-14, 0, 20]), (g1, [-50, -20, 10], 'easeOutQuad'), (g2, [-46, -20, 10]), (claw, [-170, 30, 30], 'easeInOutSine'),
                    (pre, [-160, 40, 30]), (burst, [30, 0, 70], 'easeOutExpo'), (roar, [-60, 0, 100], 'easeOutBack'), (round(roar + 1.2, 2), [-60, 0, 100]),
                    (end, st[:3], 'easeInOutSine')], 'lin')
    stretch(a, 'arm_r', [(0, 1), (g1, 1.3), (claw, 1.6), (pre, 1.6), (burst, 1.3), (roar, 1.6), (round(roar + 1.2, 2), 1.6), (end, st[3])])
    md = gp('mid')
    a.rot('arm_l', [(0, md[:3]), (g1, [-50, 20, -10], 'easeOutQuad'), (pre, [-50, 20, -10]), (burst, [30, 0, -70], 'easeOutExpo'),
                    (roar, [-60, 0, -100], 'easeOutBack'), (round(roar + 1.2, 2), [-60, 0, -100]), (end, md[:3], 'easeInOutSine')], 'lin')
    stretch(a, 'arm_l', [(0, md[3]), (g1, 1.3), (pre, 1.3), (burst, 1.3), (roar, 1.6), (round(roar + 1.2, 2), 1.6), (end, md[3])])
    for leg, sgn in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [0, 0, 0]), (g1, [-20, 0, 12 * sgn], 'easeOutQuad'), (pre, [-20, 0, 12 * sgn]), (burst, [0, 0, 16 * sgn], 'easeOutExpo'),
                    (end, [0, 0, 6 * sgn])], 'lin')
    for brow, sgn in (('brow_r', 1), ('brow_l', -1)):
        a.pos(brow, [(0, [0, 0, 0]), (g1, [0, -0.6, 0]), (burst, [0, 1.0, 0]), (end, [0, -0.6, 0])], 'lin')
        a.rot(brow, [(0, [0, 0, 0]), (g1, [0, 0, 16 * sgn]), (burst, [0, 0, -10 * sgn]), (end, [0, 0, 16 * sgn])], 'lin')
    a.sound(g1, 'grunt').sound(g2, 'grunt').sound(burst, 'rip').sound(roar, 'roar')
    return a


PHASE_THREE = [b_p3_knockback, b_p3_glare, b_p3_glare_hold, b_p3_stalk, b_p3_snatch, b_p3_carry, b_p3_carry_hold, b_p3_blackout,
               b_p3_serenade, b_p3_verdict, b_p3_toss, b_p3_dance, b_p3_felt, b_p3_crush, b_p3_go, b_p3_ascend]


BOSS = [b_leap_out, b_chug, b_roar, b_idle, b_walk, b_walk_keys, b_run, b_swipe, b_slam, b_breath, b_throw,
        b_drink_handle, b_enter_car, b_drive, b_exit_stumble, b_phase, b_death,
        b_swipe_overhead, b_leap, b_leap_land, b_charge_windup, b_charge, b_stagger, b_uppercut, b_air_spike,
        b_blink, b_gatling, b_dash_combo, b_van_kick, b_p2_kneel, b_p2_talk, b_p2_scream, b_p2_arrive, b_fly,
        b_p2_barrage, b_p2_meteor, b_van_throw, b_p2_dive, b_p2_season,
        b_grab_lunge, b_grab_combo] + REWORK + COLISEUM + FORM + HARDENING + PHASE_THREE




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
    print('guitar poses (client/BossModel):', guitar_poses())
