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
          Ascended's moves: a_fly / a_flash / a_comet / a_duel_fly / a_shred (loops), a_combo 2.8, a_comet_land 1.5, a_laser 5.5,
          a_solar 8.5, a_conduct 8.0, a_duel_smash 2.5, a_duel_shame 4.0, a_pinata 7.0, a_braziers 5.5, a_nova 7.5, a_spent 4.0,
          qte_hold_crush (loop), qte_fail_crush 2.0
          phase four: p4_rise / p4_hammer / p4_strike / p4_reveal / p4_trueform (beats p4.*), p4_moonrays (loop);
          El Rey de los Muertos: d_fly / d_gigante / d_petalos / d_conduct / d_danza / d_luna (loops), d_martillo 3.2,
          d_grieta 10.0
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
    # the blush too: he only goes red while he drinks (chug, the handle, a swig), fading out after
    pins = ['blush', 'bottle', 'handle', 'big_spatula', 'molotov', 'lighter', 'keys', 'sweat', 'tears', 'potato_gun',
            'blood', 'hand_blood', 'butter', 'fry_crown', 'wing_r', 'wing_l', 'giant_shaker', 'torch', 'salt_shaker',
            'comal', 'cleaver_r', 'cleaver_l', 'tortilla', 'brick', 'tot', 'lid_r', 'lid_l', 'p3_guitar', 'p3_guitar_carry',
            # phase four's (papon_muertos; the hammer on papon_ascended too): client/BossModel.spiritProps shows them
            'spirit_hammer', 'penacho', 'aztec_bow', 'bow_drawn', 'aztec_arrow', 'scar', 'scar_blood']


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
    a.scale('blush', [(0, [0, 0, 0]), (1.25, [0, 0, 0]), (3.75, [1.1, 1.6, 1]), (5.0, [1.1, 1.6, 1])])
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
    a.scale('blush', [(0, [1.1, 1.6, 1]), (1.2, [0, 0, 0])], 'lin')          # the tequila flush fading
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
    a.scale('blush', [(0, [0, 0, 0]), (0.5, [0, 0, 0]), (2.1, [1.1, 2.4, 1]), (2.5, [1.1, 2.0, 1])])
    a.sound(0.15, 'cloth').sound(2.3, 'whoosh')
    return a


def b_enter_car():
    """0.8 s. Hauls himself in behind the wheel (he is already seated: this is the settle)."""
    a = angry(B('enter_car', 0.8, 'hold'))
    seated(a)
    a.pos('root', [(0, [0, 3, 2]), (0.4, [0, -1, 0], 'easeInQuad'), (0.8, [0, 0, 0], 'easeOutBack')], 'lin')
    a.scale('blush', [(0, [1.1, 2.0, 1]), (0.8, [0, 0, 0])], 'lin')
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
    a.scale('blush', [(0, [0, 0, 0]), (0.6, [1.2, 2.6, 1])], 'lin')
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
    a.scale('blush', [(0, [1.2, 2.6, 1]), (0.6, [0, 0, 0])], 'lin')
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
    a.scale('blush', [(0, [1.2, 2.6, 1]), (0.6, [0, 0, 0])], 'lin')
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
    """BossState.P2_ARRIVE in his coliseum (to arrive.end). Down out of the sky into the middle of the
    sand (the entity's motion, 0-1.8 s) in a superhero landing (1.8 s), up again with his arms thrown
    up to the stands; "WELCOME... to MY COLISEUM!!!" with his arms spread wide to all of it (arrive.welcome),
    both fists up into their roar (roar1), "ARE YOU READY?!" and a fist pump each way; then the grand gesture
    at the band stand with a snap of his fingers: "MUSIC, MAESTRO!"... and he holds it."""
    m = beat('arrive.musica', 74)
    w, r1 = beat('arrive.welcome', 40), beat('arrive.roar1', 84)
    end = max(5.0, beat('arrive.end', 100))
    a = angry(B('p2_arrive_sky', end, 'hold'))
    a.pos('root', [(0, [0, 2, 0]), (1.7, [0, 1, 0]), (1.8, [0, -6, 0], 'easeInQuad'), (2.0, [0, -6, 0]),
                   (2.3, [0, 0, 0], 'easeOutBack'), (5.0, [0, 0, 0])], 'lin')
    a.rot('leg_r', [(0, [30, 0, 6]), (1.7, [24, 0, 6]), (1.8, [-80, 0, 0], 'easeInQuad'), (2.0, [-80, 0, 0]), (2.3, [0, 0, 6]), (5.0, [0, 0, 6])], 'lin')
    a.rot('leg_l', [(0, [10, 0, -6]), (1.7, [16, 0, -6]), (1.8, [40, 0, 0], 'easeInQuad'), (2.0, [40, 0, 0]), (2.3, [0, 0, -6]), (5.0, [0, 0, -6])], 'lin')
    a.rot('torso', [(0, [-8, 0, 0]), (1.7, [-4, 0, 0]), (1.8, [40, 0, 0], 'easeInQuad'), (2.0, [42, 0, 0]),
                    (2.3, [-14, 0, 0], 'easeOutBack'), (2.8, [-10, 0, 0]), (round(w + 0.3, 2), [-16, 0, 0], 'easeInOutSine'),
                    (round(r1 + 0.25, 2), [-20, 0, 0], 'easeOutBack'), (round(m - 0.2, 2), [-6, 0, 0]), (m, [4, 0, 0], 'easeOutQuad'), (end, [2, 0, 0])], 'lin')
    a.rot('arm_r', [(0, [-170, 0, 10]), (1.7, [-170, 0, 10]), (1.8, [-20, 0, 30], 'easeInQuad'), (2.0, [-10, 0, 40]),
                    (2.3, [-160, 0, 40], 'easeOutBack'), (round(w + 0.3, 2), [-72, -18, 78], 'easeInOutSine'),
                    (round(r1 - 0.1, 2), [-76, -18, 82]), (round(r1 + 0.25, 2), [-168, 0, 34], 'easeOutBack')] + pumps(r1 + 0.4, m - 0.5, 1) + [
                    (round(m - 0.2, 2), [-40, 0, 20], 'easeInOutSine'), (m, POINT_R, 'easeOutBack'), (end, POINT_R)], 'lin')
    a.rot('arm_l', [(0, [-20, 0, -40]), (1.7, [-20, 0, -40]), (1.8, [-60, 0, -10], 'easeInQuad'), (2.0, [-70, 0, -6]),
                    (2.3, [-160, 0, -40], 'easeOutBack'), (round(w + 0.3, 2), [-72, 18, -78], 'easeInOutSine'),
                    (round(r1 - 0.1, 2), [-76, 18, -82]), (round(r1 + 0.25, 2), [-168, 0, -34], 'easeOutBack')] + pumps(r1 + 0.55, m - 0.4, -1) + [
                    (round(m - 0.1, 2), [-20, 0, -24], 'easeInOutSine'), (end, [-16, 0, -22])], 'lin')
    stretch(a, 'arm_r', [(0, 1.2), (1.8, 1.0), (2.3, 1.3), (round(w + 0.3, 2), 1.5), (round(r1 - 0.1, 2), 1.5), (round(r1 + 0.25, 2), 1.3),
                         (round(m - 0.2, 2), 1.0), (m, 1.7, 'easeOutBack'), (end, 1.7)])
    stretch(a, 'arm_l', [(0, 1.0), (2.0, 1.3), (2.3, 1.3), (round(w + 0.3, 2), 1.5), (round(r1 - 0.1, 2), 1.5), (round(r1 + 0.25, 2), 1.3),
                         (round(m - 0.1, 2), 1.0)])
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


def f_wings_folded():
    """1.0 s loop: folded down behind his back (phase four: beaten, on the ground)."""
    a = F('a_wings_folded', 1.0, True)
    wings_shown(a)
    r0, f0 = FOLDED
    wing_keys(a, [(0, r0, f0, None), (1.0, r0, f0, None)])
    return a


FORM = [f_sprout, f_idle, f_fly, f_attack, f_lift, f_aura, f_wings_unfurl, f_wings_sprout, f_wings_idle, f_wings_fly, f_wings_folded]


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
    """His hands on the singer's requinto (p3_guitar, on his right hip, the neck under his mouth), solved on his rig
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
    # along the neck from the body (4) to the head (12): kept out at his left edge, clear of his mouth
    for name, y in (('low', 8.0), ('mid', 9.5), ('high', 11.2)):
        x, _ = band_ik.reach('l', 'hand_l', R.pivot('hand_l'), R.to_world('p3_guitar', piv + k * np.array([0, y, -3.0]), {}), x0)
        out[name] = [round(float(v), 1) for v in x]
        x0 = x
    return out


GUITAR = None
GUITAR_SCALE = 1.35
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
    """BossState.P3_KNOCKBACK (beats knock.*, reworked 2026-10-04: weaker, taking a beating). The hit lands (0): he folds
    round it; flung back through the air (the entity flies him, 0-0.7), down flat on his back (0.7) with a squash, the
    skid (to 1.3); flat out, chest heaving, a hand flopping; an arm pushes him up halfway and gives way (knock.fall), down
    again with a bounce; he rolls onto his side, gets a knee under him (knock.knee), head hanging; "Hhh... that one...
    HURT." (knock.hurt), the back of his hand to his lip; and up (knock.up), swaying, hunched."""
    fall, knee, hurt, up, end = (beat('knock.fall', 54), beat('knock.knee', 76), beat('knock.hurt', 84), beat('knock.up', 130),
                                 beat('knock.end', 150))
    a = B('p3_knockback', end, 'hold')
    a.scale('blood', [(0, SHOWN), (end, SHOWN)], 'lin')
    push = round(fall - 0.55, 2)
    roll = round((fall + knee) / 2, 2)
    heave = [(round(1.4 + 0.42 * i, 2), [-88, 0, 0] if i % 2 else [-86, 0, 0]) for i in range(max(0, int((push - 1.5) / 0.42)))]
    a.rot('root', [(0, [0, 0, 0]), (0.06, [-10, 0, 0], 'easeOutQuad'), (0.65, [-62, 0, 0], 'easeInQuad'), (0.7, [-88, 0, 0]),
                   (0.78, [-84, 0, 0]), (0.9, [-88, 0, 3]), (1.0, [-88, 0, -3]), (1.1, [-88, 0, 2]), (1.3, [-88, 0, 0])] + heave
          + [(push, [-88, 0, 0]), (round(fall - 0.1, 2), [-48, 0, 6], 'easeInOutSine'), (fall, [-88, 0, 0], 'easeInQuad'),
             (round(fall + 0.15, 2), [-82, 0, 0], 'easeOutQuad'), (round(fall + 0.3, 2), [-88, 0, 0], 'easeInQuad'),
             (roll, [-70, 0, -40], 'easeInOutSine'), (knee, [0, 0, -6], 'easeInOutSine'), (up, [0, 0, -4]),
             (round(up + 0.5, 2), [6, 0, 4], 'easeInOutSine'), (round(up + 0.8, 2), [4, 0, -3]), (end, [3, 0, 2])], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (0.6, [0, 1.5, 0]), (0.7, [0, 3.2, 0]), (push, [0, 3.2, 0]), (round(fall - 0.1, 2), [0, 2.4, 0]),
                   (fall, [0, 3.2, 0], 'easeInQuad'), (roll, [0, 1.5, 0]), (knee, [0, -4.2, 0], 'easeInOutSine'), (up, [0, -4.2, 0]),
                   (round(up + 0.5, 2), [0, -0.8, 0], 'easeInOutSine'), (end, [0, -0.6, 0])], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.05, [34, 0, 0], 'easeOutExpo'), (0.6, [24, 0, 0]), (0.7, [-6, 0, 0], 'easeOutQuad'),
                    (push, [0, 0, 0]), (round(fall - 0.1, 2), [18, 0, 0]), (fall, [0, 0, 0]), (knee, [34, 0, 0], 'easeInOutSine'),
                    (hurt, [30, 0, 0]), (round(hurt + 0.8, 2), [20, 0, 0]), (up, [26, 0, 0]), (round(up + 0.5, 2), [20, 0, 0]), (end, [18, 0, 0])], 'lin')
    wipe = round(hurt + 0.6, 2)
    a.rot('arm_r', [(0, [-20, 0, 20]), (0.05, [-80, 20, 30], 'easeOutExpo'), (0.6, [-110, 10, 40]), (0.72, [-150, 0, 80], 'easeOutQuad'),
                    (push, [-150, 0, 76]), (round(push + 0.15, 2), [20, 0, 30], 'easeInOutSine'), (round(fall - 0.1, 2), [30, 0, 24]),
                    (fall, [-150, 0, 80], 'easeOutQuad'), (roll, [-60, 0, 30]), (knee, [-70, -10, 10], 'easeInOutSine'),
                    (hurt, [-60, -10, 10]), (wipe, DRINK_R, 'easeInOutSine'), (round(wipe + 0.3, 2), [-92, -42, 0]),
                    (round(wipe + 0.6, 2), [-50, -10, 14], 'easeInOutSine'), (up, [-50, -10, 14]), (end, [-8, 0, 18], 'easeInOutSine')], 'lin')
    stretch(a, 'arm_r', [(0, 1), (push, 1), (round(push + 0.15, 2), 1.5), (round(fall - 0.1, 2), 1.4), (fall, 1), (knee, 1.3), (hurt, 1.3),
                         (wipe, 1.6), (round(wipe + 0.6, 2), 1.3), (up, 1.3), (end, 1)])
    a.rot('arm_l', [(0, [-20, 0, -20]), (0.05, [-80, -20, -30], 'easeOutExpo'), (0.6, [-110, -10, -40]), (0.72, [-150, 0, -80], 'easeOutQuad'),
                    (roll, [-150, 0, -70]), (knee, [-40, 10, -8], 'easeInOutSine'), (up, [-40, 10, -8]), (end, [-8, 0, -18], 'easeInOutSine')], 'lin')
    stretch(a, 'arm_l', [(0, 1), (roll, 1), (knee, 1.4), (up, 1.4), (end, 1)])
    for leg, sgn in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [0, 0, 0]), (0.05, [-40, 0, 6 * sgn], 'easeOutExpo'), (0.6, [-50, 0, 10 * sgn]), (0.72, [-20, 0, 16 * sgn]),
                    (roll, [-30, 0, 10 * sgn]), (knee, [-80 if sgn > 0 else 70, 0, 6 * sgn], 'easeInOutSine'),
                    (up, [-80 if sgn > 0 else 70, 0, 6 * sgn]), (round(up + 0.5, 2), [-10, 0, 8 * sgn], 'easeInOutSine'), (end, [-6, 0, 8 * sgn])], 'lin')
    pant = [(round(1.4 + 0.42 * i, 2), [1.05, 0.96, 1.05] if i % 2 else [0.98, 1.03, 0.98]) for i in range(max(1, int((end - 1.5) / 0.42)))]
    a.scale('body', [(0, [1, 1, 1]), (0.05, [1.1, 0.88, 1.1], 'easeOutExpo'), (0.6, [1, 1, 1]), (0.7, [1.24, 0.78, 1.24]),
                     (0.85, [0.96, 1.04, 0.96], 'easeOutQuad'), (1.0, [1, 1, 1])] + pant, 'lin')
    for brow, sgn in (('brow_r', 1), ('brow_l', -1)):
        a.pos(brow, [(0, [0, 0.8, 0]), (knee, [0, 0.6, 0]), (hurt, [0, 0.9, 0]), (end, [0, 0.2, 0])], 'lin')
        a.rot(brow, [(0, [0, 0, -12 * sgn]), (knee, [0, 0, -14 * sgn]), (end, [0, 0, -4 * sgn])], 'lin')
    a.sound(0.0, 'punch').sound(0.05, 'whoosh').sound(0.7, 'slam').sound(0.75, 'grunt').sound(fall, 'thud').sound(knee, 'grunt')
    return a


def b_p3_glare():
    """BossState.P3_GLARE (beats glare.*, reworked 2026-10-04). Dazed on his feet, swaying, while the camera is his eyes
    (to glare.face); then outrage: "This is NOT my style..." with the back of his hand flicked at the band, "...who do you
    think I AM?!" both arms flung wide, chest out; "Let me show you how to REALLY play.": a thumb to his own chest, a finger
    at the singer, held."""
    face, ns, rp, end = beat('glare.face', 148), beat('glare.notstyle', 158), beat('glare.realplay', 230), beat('glare.end', 290)
    a = angry(B('p3_glare', end, 'hold'), face)
    a.scale('blood', [(0, SHOWN), (face, SHOWN), (round(face + 0.15, 2), HIDDEN, 'easeInQuad'), (end, HIDDEN)], 'lin')
    sway = [(round(0.5 * i, 2), [0, 0, 5 if i % 2 else -5]) for i in range(max(1, int(face / 0.5)))]
    a.rot('root', [(0, [0, 0, 0])] + sway + [(face, [0, 0, 0], 'easeInOutSine'), (end, [0, 0, 0])], 'lin')
    a.pos('root', [(0, [0, -0.6, 0]), (face, [0, -0.4, 0]), (round(face + 0.3, 2), [0, 0, 0], 'easeOutQuad'), (end, [0, 0, 0])], 'lin')
    who = round(ns + 1.6, 2)
    a.rot('torso', [(0, [20, 0, 0]), (face, [16, 0, 0]), (round(face + 0.3, 2), [4, 0, 0], 'easeOutQuad'), (ns, [4, 0, 0]),
                    (round(ns + 0.6, 2), [6, -8, 0]), (who, [-18, 0, 0], 'easeOutBack'), (round(who + 0.8, 2), [-14, 0, 0]),
                    (rp, [-4, 0, 0]), (round(rp + 0.6, 2), [-6, 0, 0]), (end, [-4, 0, 0])], 'lin')
    a.scale('body', [(round(0.42 * i, 2), [1.04, 0.97, 1.04] if i % 2 == 0 else [0.99, 1.02, 0.99]) for i in range(max(1, int(face / 0.42)))]
            + [(face, [1, 1, 1]), (who, [1.08, 1.06, 1.08], 'easeOutBack'), (round(who + 0.8, 2), [1.04, 1.03, 1.04]), (end, [1.02, 1.02, 1.02])])
    flick = round(ns + 0.5, 2)
    a.rot('arm_r', [(0, [-6, 0, 18]), (face, [-6, 0, 18]), (round(who - 0.1, 2), [-6, 0, 18]), (who, [-40, 0, 90], 'easeOutBack'),
                    (round(who + 0.8, 2), [-40, 0, 86]), (round(rp + 0.05, 2), [-6, 0, 18], 'easeInOutSine'),
                    (round(rp + 0.35, 2), [-72, -64, 0], 'easeInOutSine'), (round(rp + 0.7, 2), [-72, -64, 0]),
                    (round(rp + 1.1, 2), [-96, -6, 6], 'easeOutBack'), (end, [-96, -6, 6])], 'lin')
    stretch(a, 'arm_r', [(0, 1), (who, 1.5), (round(who + 0.8, 2), 1.5), (round(rp + 0.05, 2), 1), (round(rp + 0.35, 2), 1.15),
                         (round(rp + 0.7, 2), 1.15), (round(rp + 1.1, 2), 1.9), (end, 1.9)])
    a.rot('arm_l', [(0, [-6, 0, -18]), (round(flick - 0.3, 2), [-6, 0, -18]), (round(flick - 0.05, 2), [-70, 40, -10], 'easeInOutSine'),
                    (round(flick + 0.12, 2), [-64, -20, -60], 'easeOutQuad'), (round(who - 0.1, 2), [-50, -10, -40]),
                    (who, [-40, 0, -90], 'easeOutBack'), (round(who + 0.8, 2), [-40, 0, -86]), (round(rp + 0.3, 2), [-6, 0, -18], 'easeInOutSine'),
                    (end, [-6, 0, -18])], 'lin')
    stretch(a, 'arm_l', [(0, 1), (round(flick - 0.3, 2), 1), (round(flick + 0.12, 2), 1.4), (who, 1.5), (round(who + 0.8, 2), 1.5),
                         (round(rp + 0.3, 2), 1)])
    a.sound(flick, 'flick').sound(who, 'roar_small').sound(round(rp + 1.1, 2), 'snap')
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
    up0 = round(max(0.05, lifted - 0.7), 2)          # (2026-10-04) after "It's about time I gave this thing an UPGRADE."
    a = angry(B('p3_crush', end, 'hold'))
    n = max(1, int((ball - sq) / 0.1))
    shake = [(round(sq + 0.1 * i, 2), [-170 + (6 if i % 2 else -6), 0, 24]) for i in range(n)]
    a.rot('arm_r', [(0, [-20, 0, 20]), (0.4, [-64, -20, 30], 'easeInOutSine'), (round(up0 - 0.1, 2), [-64, -20, 30]),
                    (round(up0 + 0.35, 2), [-80, 0, 30], 'easeInOutSine'), (lifted, [-170, 0, 32], 'easeOutQuad'),
                    (sq, [-170, 0, 24])] + shake + [(ball, [-176, 0, 10], 'easeOutBack'), (ref, [-176, 0, 10]),
                   (round((ref + down) / 2, 2), [-110, 0, 20], 'easeInOutSine'), (down, [-40, 0, 24], 'easeInQuad'),
                   (round(down + 0.4, 2), [10, 30, 40], 'easeOutQuad'), (end, [10, 30, 40])], 'lin')
    stretch(a, 'arm_r', [(0, 1), (0.4, 1.7), (up0, 1.7), (lifted, 1.6), (ball, 1.5), (ref, 1.6), (down, 1.6), (round(down + 0.4, 2), 1.2), (end, 1.2)])
    shake_l = [(round(sq + 0.1 * i, 2), [-170 + (-6 if i % 2 else 6), 0, -24]) for i in range(n)]
    a.rot('arm_l', [(0, [-20, 0, -20]), (0.5, [-40, 30, -40], 'easeInOutSine'), (round(up0 * 0.6, 2), [-46, 40, -50]),
                    (round(up0 - 0.1, 2), [-20, 0, -20]), (round(up0 + 0.35, 2), [-80, 0, -30], 'easeInOutSine'), (lifted, [-170, 0, -32], 'easeOutQuad'),
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
    a.sound(round(up0 + 0.35, 2), 'lift').sound(sq, 'grunt').sound(ball, 'roar_small').sound(down, 'slam')
    return a


def b_p3_showcar():
    """BossState.P3_SHOWCAR (beats showcar.*): the Skyline shown off. Hand on his hip after the slam; the key fob up
    in his left hand at the car (showcar.keys: the chirp), the lamps and the engine coming on; both hands on his hips,
    chin up, while it revs, a nod with each blip and a lean back into the scream of it (showcar.rev); then the
    showroom: his right hand swept out to it, palm up, as he says "...and in with the NEW." (showcar.line)."""
    keys, rev, ln, end = beat('showcar.keys', 10), beat('showcar.rev', 56), beat('showcar.line', 128), beat('showcar.end', 180)
    a = B('p3_showcar', end, 'hold')
    k0, k1 = round(keys - 0.25, 2), round(keys + 0.9, 2)
    a.scale('keys', [(0, HIDDEN), (round(k0 - 0.01, 2), HIDDEN), (k0, SHOWN), (k1, SHOWN), (round(k1 + 0.01, 2), HIDDEN), (end, HIDDEN)], 'lin')
    a.rot('arm_l', [(0, [-14, 0, -20]), (k0, [-14, 0, -20]), (keys, [-112, 14, -12], 'easeOutBack'), (round(keys + 0.15, 2), [-118, 14, -12]),
                    (round(keys + 0.3, 2), [-112, 14, -12]), (k1, [-112, 14, -12]), (round(k1 + 0.4, 2), [10, -30, -40], 'easeInOutSine'),
                    (end, [10, -30, -40])], 'lin')
    stretch(a, 'arm_l', [(0, 1), (k0, 1), (keys, 1.5), (k1, 1.5), (round(k1 + 0.4, 2), 1.2), (end, 1.2)])
    a.rot('arm_r', [(0, [10, 30, 40]), (round(ln - 0.35, 2), [10, 30, 40]), (round(ln + 0.15, 2), [-78, -30, 34], 'easeOutBack'),
                    (end, [-74, -30, 36])], 'lin')
    stretch(a, 'arm_r', [(0, 1.2), (round(ln - 0.35, 2), 1.2), (round(ln + 0.15, 2), 1.6), (end, 1.6)])
    pull, peak = round(rev + 1.45, 2), round(rev + 2.7, 2)
    a.rot('torso', [(0, [-6, 8, 0]), (keys, [-4, 0, 0], 'easeInOutSine'), (round(rev + 0.15, 2), [-4, 0, 0]), (round(rev + 0.3, 2), [4, 0, 0], 'easeOutQuad'),
                    (round(rev + 0.55, 2), [-4, 0, 0]), (round(rev + 0.75, 2), [-4, 0, 0]), (round(rev + 0.9, 2), [4, 0, 0], 'easeOutQuad'),
                    (round(rev + 1.15, 2), [-4, 0, 0]), (pull, [-6, 0, 0]), (peak, [-18, 0, 0], 'easeInQuad'), (round(peak + 0.5, 2), [-8, 0, 0], 'easeOutQuad'),
                    (round(ln - 0.2, 2), [-8, 0, 0]), (round(ln + 0.3, 2), [-12, -14, 0], 'easeOutBack'), (end, [-12, -14, 0])], 'lin')
    a.scale('body', [(0, [1.02, 1.02, 1.02]), (pull, [1.02, 1.02, 1.02]), (peak, [1.08, 1.06, 1.08], 'easeInQuad'),
                     (round(peak + 0.5, 2), [1.02, 1.02, 1.02], 'easeOutQuad'), (end, [1.03, 1.03, 1.03])], 'lin')
    for brow, sgn in (('brow_r', 1), ('brow_l', -1)):
        a.pos(brow, [(0, [0, -0.3, 0]), (end, [0, -0.3, 0])], 'lin')
        a.rot(brow, [(0, [0, 0, 8 * sgn]), (end, [0, 0, 8 * sgn])], 'lin')
    a.sound(keys, 'keys')
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
    if g2 <= g1:
        g2 = round(g1 + (burst - g1) * 0.45, 2)          # one groan now (gen_boss_voice.beats): the hand to his back halfway through it
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


def b_p3_power():
    """BossState.P3_POWER (beats power.*, 2026-10-04). Just ascended, the power goes through him: a jolt (0.1), and he
    staggers back three steps, arms flung out for balance, nearly over (to 1.3); catches himself; looks down at his hands,
    turning them over, flexing ("Whoa, WHOA... so much POWER.", power.line); and the grin, the fists closing slowly
    (power.grin), the chest swelling. The requinto stays slung on his belly."""
    line, grin, end = beat('power.line', 22), beat('power.grin', 60), beat('power.end', 86)
    a = B('p3_power', end, 'hold')
    guitar_slung(a)
    a.scale('p3_guitar', [(0, SHOWN_G), (end, SHOWN_G)], 'lin')
    a.rot('root', [(0, [0, 0, 0]), (0.1, [-14, 0, 0], 'easeOutExpo'), (0.4, [-10, 0, 8]), (0.75, [-14, 0, -10]), (1.1, [-18, 0, 6]),
                   (1.3, [-4, 0, 0], 'easeOutBack'), (end, [0, 0, 0])], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (0.4, [0, -0.6, 0]), (0.75, [0, 0.4, 0]), (1.1, [0, -0.8, 0]), (1.3, [0, 0, 0]), (end, [0, 0, 0])], 'lin')
    for leg, sgn in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [0, 0, 4 * sgn]), (0.4, [30 if sgn > 0 else -20, 0, 8 * sgn]), (0.75, [-20 if sgn > 0 else 30, 0, 8 * sgn]),
                    (1.1, [34 if sgn > 0 else -14, 0, 10 * sgn]), (1.3, [0, 0, 8 * sgn], 'easeOutBack'), (end, [0, 0, 6 * sgn])], 'lin')
    look = round(line - 0.2, 2)
    for arm, sgn in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-14, 0, 20 * sgn]), (0.1, [-40, 0, 90 * sgn], 'easeOutExpo'), (0.6, [-70, 0, 110 * sgn]), (1.0, [-30, 0, 80 * sgn]),
                    (1.3, [-20, 0, 40 * sgn], 'easeOutQuad'), (look, [-70, -40 * sgn, 10 * sgn], 'easeInOutSine'),
                    (round(look + 0.6, 2), [-70, -46 * sgn, 22 * sgn]), (round(look + 1.2, 2), [-74, -36 * sgn, 0]),
                    (grin, [-60, -30 * sgn, 6 * sgn], 'easeInOutSine'), (round(grin + 0.5, 2), [-20, 0, 34 * sgn], 'easeOutBack'),
                    (end, [-20, 0, 30 * sgn])], 'lin')
        stretch(a, arm, [(0, 1), (0.1, 1.3), (1.3, 1), (look, 1.5), (grin, 1.4), (round(grin + 0.5, 2), 1), (end, 1)])
    a.rot('torso', [(0, [0, 0, 0]), (0.1, [-18, 0, 0], 'easeOutExpo'), (1.3, [6, 0, 0]), (look, [22, 0, 0], 'easeInOutSine'),
                    (grin, [10, 0, 0]), (round(grin + 0.5, 2), [-12, 0, 0], 'easeOutBack'), (end, [-10, 0, 0])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (0.1, [1.12, 1.1, 1.12], 'easeOutExpo'), (0.5, [1.02, 1.02, 1.02]), (grin, [1.02, 1.02, 1.02]),
                     (round(grin + 0.5, 2), [1.1, 1.06, 1.1], 'easeOutBack'), (end, [1.07, 1.05, 1.07])], 'lin')
    a.sound(0.1, 'whoosh').sound(0.4, 'thud').sound(0.75, 'thud').sound(1.1, 'thud')
    return a


def b_winded():
    """2.5 s (BossState.WINDED). Winded after a big move: down onto the sand, hands on his knees, his back heaving, head
    hung and coming up to look at you; the window to hit him."""
    a = B('winded', 2.5, 'hold')
    a.pos('root', [(0, [0, 0, 0]), (0.25, [0, -1.6, 0], 'easeOutQuad'), (2.5, [0, -1.4, 0])], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.3, [36, 0, 0], 'easeOutQuad')] + [(round(0.5 + 0.4 * i, 2), [36 if i % 2 else 30, 0, 0]) for i in range(5)]
          + [(2.5, [20, 0, 0], 'easeInOutSine')], 'lin')
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-20, 0, 20 * s]), (0.3, [-56, -14 * s, 10 * s], 'easeOutQuad'), (2.5, [-50, -12 * s, 10 * s])], 'lin')
        stretch(a, arm, [(0, 1), (0.3, 1.45), (2.5, 1.4)])
    for leg, s in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [0, 0, 0]), (0.3, [-24, 0, 10 * s], 'easeOutQuad'), (2.5, [-20, 0, 10 * s])], 'lin')
    a.scale('body', [(0, [1, 1, 1])] + [(round(0.4 + 0.4 * i, 2), [1.05, 0.96, 1.05] if i % 2 else [0.98, 1.03, 0.98]) for i in range(6)], 'lin')
    for brow, sgn in (('brow_r', 1), ('brow_l', -1)):
        a.pos(brow, [(0, [0, 0.6, 0])], 'lin')
        a.rot(brow, [(0, [0, 0, -12 * sgn])], 'lin')
    a.sound(0.25, 'thud')
    return a


# ===================================================================== Ascended Papon's moves (phase three's fight)
# A demigod's poses, none of the chef's: chest out, fists clenched, flying. (BossState.P3_*; lengths in its docstring)

def god(a, t=0):
    """Ascended's face: brows down hard (angry) and the chest out (the body a touch broad)."""
    angry(a, t)
    a.scale('body', [(t, [1.04, 1.03, 1.04])], 'lin')
    return a


def flying_legs(a, length, trail=24):
    for leg, s in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [trail + 6 * s, 0, 5 * s]), (length / 2, [trail - 6 * s, 0, 5 * s]), (length, [trail + 6 * s, 0, 5 * s])])


def b_a_fly():
    """1.0 s loop. Ascended in the air between attacks: upright, chest out, fists clenched low and ready, legs trailing,
    rising and falling on each wingbeat."""
    a = god(B('a_fly', 1.0, True))
    a.pos('root', [(0, [0, 0, 0]), (0.4, [0, 1.8, 0]), (1.0, [0, 0, 0])])
    a.rot('torso', [(0, [-6, 0, 0]), (0.4, [-10, 0, 0]), (1.0, [-6, 0, 0])])
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-28, 0, 26 * s]), (0.4, [-36, 0, 32 * s]), (1.0, [-28, 0, 26 * s])])
    flying_legs(a, 1.0)
    return a


def b_a_combo():
    """2.8 s (BossState.P3_COMBO, 56 t). Puños de Oro: in fists first (0-0.35), three jabs (0.45 / 0.7 / 0.95), an uppercut
    (1.4), up over you (1.9: a flash), both fists driven down (2.3), the landing."""
    a = god(B('a_combo', 2.8, 'hold'))
    jab, back = [-96, -6, 8], [20, 0, 34]
    r, l = [(0, [-40, 0, 30])], [(0, [-40, 0, -30])]
    tor = [(0, [16, 0, 0])]
    for i, t in enumerate((0.45, 0.7, 0.95)):
        pre = round(t - 0.1, 2)
        if i % 2 == 0:
            r += [(pre, back), (t, jab, 'easeOutExpo')]
            l += [(t, [-60, 0, -30])]
        else:
            l += [(pre, [20, 0, -34]), (t, [-96, 6, -8], 'easeOutExpo')]
            r += [(t, [-60, 0, 30])]
        tor.append((t, [12, 18 if i % 2 == 0 else -18, 0], 'easeOutExpo'))
        a.sound(t, 'punch')
    r += [(1.25, [30, 0, 20], 'easeInQuad'), (1.4, [-170, 0, 14], 'easeOutExpo'), (1.8, [-150, 0, 20])]
    l += [(1.25, [-40, 0, -30]), (1.8, [-60, 0, -40])]
    tor += [(1.25, [24, 0, 0]), (1.4, [-22, 0, 0], 'easeOutExpo'), (1.85, [0, 0, 0])]
    # over you, both fists up, and down
    r += [(1.95, [-190, 0, 10]), (2.15, [-200, 0, 10]), (2.3, [-40, 0, 10], 'easeInExpo'), (2.8, [-20, 0, 24])]
    l += [(1.95, [-190, 0, -10]), (2.15, [-200, 0, -10]), (2.3, [-40, 0, -10], 'easeInExpo'), (2.8, [-20, 0, -24])]
    tor += [(2.15, [-18, 0, 0]), (2.3, [40, 0, 0], 'easeInExpo'), (2.8, [8, 0, 0])]
    a.rot('arm_r', r, 'lin')
    a.rot('arm_l', l, 'lin')
    a.rot('torso', tor, 'lin')
    stretch(a, 'arm_r', [(0, 1), (0.45, 2.0, 'easeOutExpo'), (0.6, 1.1), (0.95, 2.0, 'easeOutExpo'), (1.1, 1.1), (1.4, 1.8), (1.9, 1.3), (2.3, 1.6), (2.8, 1.1)])
    stretch(a, 'arm_l', [(0, 1), (0.6, 1.1), (0.7, 2.0, 'easeOutExpo'), (0.85, 1.1), (1.9, 1.3), (2.3, 1.6), (2.8, 1.1)])
    flying_legs(a, 2.8, 18)
    a.scale('body', [(0, [1.04, 1.03, 1.04]), (2.3, [1.18, 0.86, 1.18], 'easeInExpo'), (2.6, [1.04, 1.03, 1.04])], 'lin')
    a.sound(1.4, 'roar_small').sound(2.3, 'slam')
    return a


def b_a_flash():
    """0.4 s loop (BossState.P3_FLASH, AI-held). Relámpago: flat out like a thrown spear, fists first, legs together."""
    a = god(B('a_flash', 0.4, True))
    a.rot('root', [(0, [74, 0, -4]), (0.2, [74, 0, 4]), (0.4, [74, 0, -4])])
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-176, 0, 6 * s])], 'lin')
        stretch(a, arm, [(0, 1.6)])
    for leg in ('leg_r', 'leg_l'):
        a.rot(leg, [(0, [8, 0, 0])], 'lin')
    a.sound(0, 'whoosh')
    return a


def b_a_comet():
    """0.5 s loop (BossState.P3_COMET, AI-held). Up like a rocket and down like a comet: straight as a bolt, both fists
    over his head, shuddering."""
    a = god(B('a_comet', 0.5, True))
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-178, 0, 8 * s]), (0.25, [-176, 0, 12 * s]), (0.5, [-178, 0, 8 * s])])
        stretch(a, arm, [(0, 1.5)])
    a.rot('root', [(0, [0, 0, -2]), (0.25, [0, 0, 2]), (0.5, [0, 0, -2])])
    for leg in ('leg_r', 'leg_l'):
        a.rot(leg, [(0, [4, 0, 0])], 'lin')
    a.sound(0, 'whoosh')
    return a


def b_a_comet_land():
    """1.5 s (BossState.P3_COMET_LAND, 30 t). The superhero landing: down on one knee, a fist in the sand (0), the rings
    rolling out; he rises (from 0.8) and rolls his shoulders."""
    a = god(B('a_comet_land', 1.5, 'hold'))
    a.pos('root', [(0, [0, -5, 0]), (0.8, [0, -5, 0]), (1.2, [0, 0, 0], 'easeInOutSine'), (1.5, [0, 0, 0])], 'lin')
    a.rot('torso', [(0, [40, 0, 0]), (0.8, [36, 0, 0]), (1.2, [-8, 0, 0], 'easeInOutSine'), (1.5, [-4, 0, 0])], 'lin')
    a.rot('arm_r', [(0, [-40, 0, 6]), (0.8, [-40, 0, 6]), (1.2, [-20, 0, 26], 'easeInOutSine'), (1.5, [-24, 0, 24])], 'lin')
    stretch(a, 'arm_r', [(0, 1.7), (0.8, 1.7), (1.2, 1), (1.5, 1)])
    a.rot('arm_l', [(0, [10, 0, -70]), (0.8, [10, 0, -60]), (1.2, [-20, 0, -26], 'easeInOutSine'), (1.5, [-24, 0, -24])], 'lin')
    a.rot('leg_r', [(0, [-80, 0, 6]), (0.8, [-80, 0, 6]), (1.2, [0, 0, 4]), (1.5, [0, 0, 4])], 'lin')
    a.rot('leg_l', [(0, [60, 0, -10]), (0.8, [60, 0, -10]), (1.2, [0, 0, -4]), (1.5, [0, 0, -4])], 'lin')
    a.scale('body', [(0, [1.25, 0.8, 1.25]), (0.2, [1.04, 1.0, 1.04], 'easeOutQuad'), (1.5, [1.04, 1.03, 1.04])], 'lin')
    a.sound(0.05, 'slam').sound(1.25, 'crack')
    return a


def b_a_laser():
    """5.5 s (BossState.P3_LASER, 110 t). Ojos de Papa: fingers to his temples as his eyes charge (0.5-1.7, shaking harder),
    then flung back, chest out, head forward, as the beams burn (1.7-4.8); he lowers his head (to 5.5)."""
    a = god(B('a_laser', 5.5, 'hold'))
    temple_r, temple_l = [-150, -60, 20], [-150, 60, -20]
    shake = [(round(0.7 + 0.1 * i, 2), [temple_r[0] + (3 if i % 2 else -3), temple_r[1], temple_r[2]]) for i in range(10)]
    shake_l = [(round(0.7 + 0.1 * i, 2), [temple_l[0] + (-3 if i % 2 else 3), temple_l[1], temple_l[2]]) for i in range(10)]
    a.rot('arm_r', [(0, [-28, 0, 26]), (0.6, temple_r, 'easeOutBack')] + shake + [(1.7, temple_r), (1.85, [30, 0, 60], 'easeOutExpo'),
                    (4.8, [24, 0, 64]), (5.5, [-28, 0, 26], 'easeInOutSine')], 'lin')
    a.rot('arm_l', [(0, [-28, 0, -26]), (0.6, temple_l, 'easeOutBack')] + shake_l + [(1.7, temple_l), (1.85, [30, 0, -60], 'easeOutExpo'),
                    (4.8, [24, 0, -64]), (5.5, [-28, 0, -26], 'easeInOutSine')], 'lin')
    stretch(a, 'arm_r', [(0, 1), (0.6, 1.5), (1.7, 1.5), (1.85, 1.2), (5.5, 1)])
    stretch(a, 'arm_l', [(0, 1), (0.6, 1.5), (1.7, 1.5), (1.85, 1.2), (5.5, 1)])
    a.rot('torso', [(0, [-6, 0, 0]), (0.6, [8, 0, 0]), (1.7, [10, 0, 0]), (1.85, [-22, 0, 0], 'easeOutExpo'),
                    (3.2, [-18, 6, 0]), (4.8, [-22, -6, 0]), (5.5, [-6, 0, 0], 'easeInOutSine')], 'lin')
    flying_legs(a, 5.5, 20)
    a.sound(0.6, 'grunt').sound(1.7, 'roar')
    return a


def b_a_solar():
    """8.5 s (BossState.P3_SOLAR, 170 t). El Sol Azteca: both arms raised to the sky (0-1.2), held there trembling as the
    sun stone wheels up behind him (to 4.5), then both palms thrust at you (4.5) and held as the light pours out (to 6.8);
    the arms come down (to 8.5)."""
    a = god(B('a_solar', 8.5, 'hold'))
    up_r, up_l = [-176, 0, 30], [-176, 0, -30]
    tremble = lambda base, sgn: [(round(1.3 + 0.12 * i, 2), [base[0] + (2 if i % 2 else -2) * sgn, base[1], base[2]]) for i in range(26)]
    a.rot('arm_r', [(0, [-28, 0, 26]), (1.2, up_r, 'easeOutBack')] + tremble(up_r, 1) + [(4.4, up_r), (4.55, [-92, -10, 10], 'easeOutExpo'),
                    (6.8, [-94, -10, 12]), (8.5, [-28, 0, 26], 'easeInOutSine')], 'lin')
    a.rot('arm_l', [(0, [-28, 0, -26]), (1.2, up_l, 'easeOutBack')] + tremble(up_l, -1) + [(4.4, up_l), (4.55, [-92, 10, -10], 'easeOutExpo'),
                    (6.8, [-94, 10, -12]), (8.5, [-28, 0, -26], 'easeInOutSine')], 'lin')
    for arm in ('arm_r', 'arm_l'):
        stretch(a, arm, [(0, 1), (1.2, 1.6), (4.4, 1.6), (4.55, 2.0), (6.8, 2.0), (8.5, 1)])
    a.rot('torso', [(0, [-6, 0, 0]), (1.2, [-24, 0, 0], 'easeOutBack'), (4.4, [-26, 0, 0]), (4.55, [16, 0, 0], 'easeOutExpo'),
                    (6.8, [14, 0, 0]), (8.5, [-6, 0, 0], 'easeInOutSine')], 'lin')
    flying_legs(a, 8.5, 18)
    a.scale('body', [(0, [1.04, 1.03, 1.04]), (4.4, [1.08, 1.08, 1.08]), (4.55, [1.14, 1.0, 1.14], 'easeOutExpo'), (8.5, [1.04, 1.03, 1.04])], 'lin')
    a.sound(0.4, 'roar').sound(4.5, 'roar')
    return a


BEAT = 0.58          # Para Siempre's beat (about 103 bpm), for the conducting


def b_a_conduct():
    """8.0 s (BossState.P3_MARIACHI, 160 t). ¡Fuego, Mariachi!: he conducts the band at you: the right arm beating time
    (down, out, up, on every beat), the left cueing the trumpets and the violins in turn, the body swaying to it."""
    a = god(B('a_conduct', 8.0, 'hold'))
    r, l, tor = [(0, [-28, 0, 26])], [(0, [-28, 0, -26])], [(0, [-6, 0, 0])]
    t, i = 0.3, 0
    while t < 7.6:
        r.append((round(t, 2), [-140, -20, 30] if i % 3 == 0 else [-80, 10, 60] if i % 3 == 1 else [-160, 0, 40], 'easeInOutSine'))
        if i % 2 == 0:
            l.append((round(t, 2), [-110, 30, -50] if i % 4 == 0 else [-60, 10, -80], 'easeOutBack'))
        tor.append((round(t, 2), [-4, 8 if i % 2 else -8, 0], 'easeInOutSine'))
        t += BEAT
        i += 1
    r.append((8.0, [-28, 0, 26]))
    l.append((8.0, [-28, 0, -26]))
    tor.append((8.0, [-6, 0, 0]))
    a.rot('arm_r', r, 'lin')
    a.rot('arm_l', l, 'lin')
    a.rot('torso', tor, 'lin')
    stretch(a, 'arm_r', [(0, 1), (0.3, 1.5), (7.6, 1.5), (8.0, 1)])
    stretch(a, 'arm_l', [(0, 1), (0.3, 1.4), (7.6, 1.4), (8.0, 1)])
    flying_legs(a, 8.0, 14)
    a.sound(0.2, 'roar_small')
    return a


def b_a_duel_fly():
    """1.0 s loop. Back from the band stand with the guitar slung on his belly, flying."""
    a = god(B('a_duel_fly', 1.0, True))
    guitar_slung(a)
    a.pos('root', [(0, [0, 0, 0]), (0.4, [0, 1.6, 0]), (1.0, [0, 0, 0])])
    a.rot('arm_r', [(0, [-40, 0, 40]), (0.5, [-30, 0, 50]), (1.0, [-40, 0, 40])])
    flying_legs(a, 1.0)
    return a


def b_a_shred():
    """1.2 s loop (BossState.P3_DUEL, AI-held). The shred: the guitar slung on his belly (his hands are BossModel's),
    leaning back, knees bouncing, headbanging on every beat."""
    a = god(B('a_shred', 1.2, True))
    guitar_slung(a)
    st = gp('strum')
    a.rot('arm_r', [(0, st[:3])], 'lin')
    stretch(a, 'arm_r', [(0, st[3])])
    a.rot('torso', [(0, [-14, 0, 0]), (0.3, [6, 0, 0], 'easeInQuad'), (0.6, [-14, 0, 0]), (0.9, [6, 0, 0], 'easeInQuad'), (1.2, [-14, 0, 0])])
    a.pos('root', [(0, [0, 0, 0]), (0.3, [0, -1.2, 0]), (0.6, [0, 0, 0]), (0.9, [0, -1.2, 0]), (1.2, [0, 0, 0])])
    for leg, s in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [-12 if s > 0 else 8, 0, 14 * s]), (0.3, [-20 if s > 0 else 14, 0, 16 * s]), (0.6, [-12 if s > 0 else 8, 0, 14 * s]),
                    (0.9, [-20 if s > 0 else 14, 0, 16 * s]), (1.2, [-12 if s > 0 else 8, 0, 14 * s])])
    return a


def b_a_duel_smash():
    """2.5 s (BossState.P3_DUEL_SMASH, 50 t). He won: the guitar up by its neck in both hands over his head (to 1.0), and
    down over yours (1.2), splintering; he tosses what is left (1.8)."""
    a = god(B('a_duel_smash', 2.5, 'hold'))
    a.scale('p3_guitar_carry', [(0, SHOWN_G), (1.8, SHOWN_G), (1.9, HIDDEN)], 'lin')
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-40, 0, 20 * s]), (0.9, [-200, 0, 10 * s], 'easeInOutSine'), (1.05, [-205, 0, 10 * s]),
                    (1.2, [-70, 0, 10 * s], 'easeInExpo'), (1.8, [-60, 0, 30 * s]), (2.5, [-24, 0, 24 * s], 'easeInOutSine')], 'lin')
        stretch(a, arm, [(0, 1), (0.9, 1.4), (1.2, 1.7), (1.8, 1.3), (2.5, 1)])
    a.rot('torso', [(0, [0, 0, 0]), (0.9, [-26, 0, 0], 'easeInOutSine'), (1.2, [36, 0, 0], 'easeInExpo'), (1.8, [10, 0, 0]), (2.5, [0, 0, 0])], 'lin')
    a.sound(1.2, 'slam')
    return a


def b_a_duel_shame():
    """4.0 s (BossState.P3_DUEL_SHAME, 80 t). You won: he stares at the guitar on his belly, slumps, a hand over his face
    (1.0-2.6) as the stands cheer you; then he straightens, fuming (to 4.0)."""
    a = B('a_duel_shame', 4.0, 'hold')
    guitar_slung(a)
    a.rot('torso', [(0, [0, 0, 0]), (0.6, [24, 0, 0], 'easeOutQuad'), (2.8, [26, 0, 4]), (3.4, [-4, 0, 0], 'easeOutBack'), (4.0, [-4, 0, 0])], 'lin')
    a.rot('arm_r', [(0, [-30, 0, 20]), (1.0, [-150, -50, 10], 'easeInOutSine'), (2.6, [-150, -50, 10]), (3.2, [-20, 0, 20], 'easeInOutSine'),
                    (4.0, [-20, 0, 20])], 'lin')
    stretch(a, 'arm_r', [(0, 1), (1.0, 1.7), (2.6, 1.7), (3.2, 1), (4.0, 1)])
    a.pos('root', [(0, [0, 0, 0]), (0.6, [0, -1.2, 0]), (2.8, [0, -1.2, 0]), (3.4, [0, 0, 0]), (4.0, [0, 0, 0])], 'lin')
    for brow, sgn in (('brow_r', 1), ('brow_l', -1)):
        a.pos(brow, [(0, [0, 0.6, 0]), (3.2, [0, 0.6, 0]), (3.4, [0, -0.8, 0])], 'lin')
        a.rot(brow, [(0, [0, 0, -14 * sgn]), (3.2, [0, 0, -14 * sgn]), (3.4, [0, 0, 18 * sgn])], 'lin')
    a.sound(1.0, 'grunt')
    return a


def b_a_pinata():
    """7.0 s (BossState.P3_PINATA, 140 t). He hangs in the air beside the piñata, arms folded, waiting (0-1.6); winds up, a
    knee drawn in (to 2.0); the flying kick, the leg shot out (2.0-2.25); then arms thrown wide in the candy rain."""
    a = god(B('a_pinata', 7.0, 'hold'))
    a.rot('arm_r', [(0, [-72, -62, 0]), (1.6, [-72, -62, 0]), (2.0, [20, 0, 50], 'easeOutQuad'), (2.25, [10, 0, 70]),
                    (2.6, [-150, 0, 60], 'easeOutBack'), (7.0, [-150, 0, 60])], 'lin')
    a.rot('arm_l', [(0, [-72, 62, 0]), (1.6, [-72, 62, 0]), (2.0, [20, 0, -50], 'easeOutQuad'), (2.25, [10, 0, -70]),
                    (2.6, [-150, 0, -60], 'easeOutBack'), (7.0, [-150, 0, -60])], 'lin')
    a.rot('leg_r', [(0, [20, 0, 6]), (1.6, [20, 0, 6]), (2.0, [-110, 0, 6], 'easeOutQuad'), (2.25, [-90, 0, 4], 'easeOutExpo'),
                    (2.6, [20, 0, 6]), (7.0, [20, 0, 6])], 'lin')
    a.rot('leg_l', [(0, [26, 0, -6]), (7.0, [26, 0, -6])], 'lin')
    a.rot('root', [(0, [0, 0, 0]), (1.6, [0, 0, 0]), (2.0, [-20, 0, 0]), (2.25, [-50, 0, 0], 'easeOutExpo'), (2.6, [0, 0, 0], 'easeOutBack'),
                   (7.0, [0, 0, 0])], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (1.0, [0, 1.4, 0]), (1.6, [0, 0, 0]), (7.0, [0, 0, 0])])
    a.sound(2.2, 'punch').sound(2.6, 'roar_small')
    return a


def b_a_braziers():
    """5.5 s (BossState.P3_BRAZIERS, 110 t). Llamas del Coliseo: arms swept up and out round the bowl (0-1.0), then a fist
    pumped at the braziers in turn (one every 0.2 s), the body turning with it."""
    a = god(B('a_braziers', 5.5, 'hold'))
    r, l, tor = [(0, [-28, 0, 26]), (1.0, [-170, 0, 70], 'easeOutBack')], [(0, [-28, 0, -26]), (1.0, [-170, 0, -70], 'easeOutBack')], [(0, [0, 0, 0])]
    for i in range(20):
        t = round(1.1 + 0.2 * i, 2)
        if i % 2 == 0:
            r.append((t, [-120, 0, 90], 'easeOutExpo'))
            l.append((t, [-170, 0, -70]))
        else:
            l.append((t, [-120, 0, -90], 'easeOutExpo'))
            r.append((t, [-170, 0, 70]))
        tor.append((t, [-6, 14 if i % 2 == 0 else -14, 0]))
    r.append((5.5, [-28, 0, 26], 'easeInOutSine'))
    l.append((5.5, [-28, 0, -26], 'easeInOutSine'))
    tor.append((5.5, [0, 0, 0]))
    a.rot('arm_r', r, 'lin')
    a.rot('arm_l', l, 'lin')
    a.rot('torso', tor, 'lin')
    stretch(a, 'arm_r', [(0, 1), (1.0, 1.5), (5.1, 1.5), (5.5, 1)])
    stretch(a, 'arm_l', [(0, 1), (1.0, 1.5), (5.1, 1.5), (5.5, 1)])
    flying_legs(a, 5.5, 18)
    a.sound(0.5, 'roar')
    return a


def b_a_nova():
    """7.5 s (BossState.P3_NOVA, 150 t). La Supernova: curled into a ball in the air, knees to his chest, arms crossed over
    it, shaking harder and harder as the star grows (0.8-6.5); and he bursts open, spread-eagle (6.5), held."""
    a = god(B('a_nova', 7.5, 'hold'))
    shake = [(round(1.0 + 0.1 * i, 2), [40 + (3 if i % 2 else -3), 0, (2 if i % 2 else -2)]) for i in range(55)]
    a.rot('torso', [(0, [0, 0, 0]), (0.8, [40, 0, 0], 'easeOutQuad')] + shake + [(6.5, [-30, 0, 0], 'easeOutExpo'), (7.5, [-20, 0, 0])], 'lin')
    a.rot('arm_r', [(0, [-28, 0, 26]), (0.8, [-80, -70, 0], 'easeOutQuad'), (6.4, [-80, -70, 0]), (6.5, [-90, 0, 100], 'easeOutExpo'),
                    (7.5, [-90, 0, 100])], 'lin')
    a.rot('arm_l', [(0, [-28, 0, -26]), (0.8, [-80, 70, 0], 'easeOutQuad'), (6.4, [-80, 70, 0]), (6.5, [-90, 0, -100], 'easeOutExpo'),
                    (7.5, [-90, 0, -100])], 'lin')
    for arm in ('arm_r', 'arm_l'):
        stretch(a, arm, [(0, 1), (0.8, 1.3), (6.4, 1.3), (6.5, 2.0), (7.5, 1.9)])
    for leg, s in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [20, 0, 4 * s]), (0.8, [-90, 0, 6 * s], 'easeOutQuad'), (6.4, [-90, 0, 6 * s]), (6.5, [10, 0, 40 * s], 'easeOutExpo'),
                    (7.5, [10, 0, 36 * s])], 'lin')
    a.scale('body', [(0, [1.04, 1.03, 1.04]), (0.8, [0.9, 0.9, 0.9]), (6.4, [0.86, 0.86, 0.86]), (6.5, [1.3, 1.3, 1.3], 'easeOutExpo'),
                     (7.5, [1.1, 1.1, 1.1])], 'lin')
    a.sound(0.3, 'grunt').sound(6.5, 'roar')
    return a


def b_a_spent():
    """4.0 s (BossState.P3_SPENT, 80 t). Spent: down onto one knee, hands on it, head hung, panting (the body heaving)."""
    a = B('a_spent', 4.0, 'hold')
    a.pos('root', [(0, [0, 0, 0]), (0.4, [0, -4, 0], 'easeOutBounce'), (4.0, [0, -4, 0])], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.4, [32, 0, 0])] + [(round(0.6 + 0.35 * i, 2), [32 if i % 2 else 26, 0, 0]) for i in range(10)], 'lin')
    a.rot('leg_r', [(0, [0, 0, 0]), (0.4, [-80, 0, 6]), (4.0, [-80, 0, 6])], 'lin')
    a.rot('leg_l', [(0, [0, 0, 0]), (0.4, [70, 0, -8]), (4.0, [70, 0, -8])], 'lin')
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-20, 0, 20 * s]), (0.4, [-50, -10 * s, 8 * s]), (4.0, [-50, -10 * s, 8 * s])], 'lin')
        stretch(a, arm, [(0, 1), (0.4, 1.4), (4.0, 1.4)])
    a.scale('body', [(0, [1, 1, 1])] + [(round(0.6 + 0.35 * i, 2), [1.04, 0.97, 1.04] if i % 2 else [0.98, 1.03, 0.98]) for i in range(10)], 'lin')
    for brow, sgn in (('brow_r', 1), ('brow_l', -1)):
        a.pos(brow, [(0, [0, 0.4, 0])], 'lin')
        a.rot(brow, [(0, [0, 0, -12 * sgn])], 'lin')
    a.sound(0.4, 'slam')
    return a


def b_a_sombrero():
    """8.5 s (BossState.P3_SOMBRERO). Over the middle of the sand: he whips a sombrero off his head and flings it flat,
    the arm sweeping round (1.2, 2.4, 3.6 s), the body spinning with each throw; then arms wide, conducting them round the
    sand; at the end a hand up to catch one back onto his head."""
    a = god(B('a_sombrero', 8.5, 'hold'))
    flying_legs(a, 8.5)
    r = [(0, [-28, 0, 26])]
    yaw = [(0, [0, 0, 0])]
    for i, t in enumerate((1.2, 2.4, 3.6)):
        r += [(round(t - 0.35, 2), [-170, 20, 20], 'easeInOutSine'), (round(t - 0.1, 2), [-150, 60, 40]),
              (t, [-60, -60, 70], 'easeOutExpo'), (round(t + 0.3, 2), [-40, -30, 50])]
        yaw += [(round(t - 0.1, 2), [0, 30, 0]), (round(t + 0.2, 2), [0, -60, 0], 'easeOutExpo'), (round(t + 0.6, 2), [0, 0, 0])]
    r += [(4.4, [-80, 0, 90], 'easeOutBack'), (5.4, [-70, 0, 100]), (6.4, [-80, 0, 90]), (7.6, [-80, 0, 90]),
          (8.0, [-180, 0, 10], 'easeOutBack'), (8.5, [-170, 0, 14])]
    a.rot('arm_r', r, 'lin')
    a.rot('arm_l', [(0, [-28, 0, -26]), (4.4, [-80, 0, -90], 'easeOutBack'), (5.4, [-70, 0, -100]), (6.4, [-80, 0, -90]), (8.5, [-60, 0, -60])], 'lin')
    a.rot('torso', yaw + [(8.5, [0, 0, 0])], 'lin')
    a.sound(1.2, 'whoosh').sound(2.4, 'whoosh').sound(3.6, 'whoosh')
    return a


def b_a_volcan():
    """9.0 s (BossState.P3_VOLCAN). Up (0-0.8 s, a crouch first), down like a meteor with both fists over his head (0.8-1.1),
    a superhero landing on one knee, a fist in the sand (1.1); he pounds the sand as each ring of geysers goes up (2.3-4.5),
    rises sweeping his arms round as the cracks turn (4.6-5.6), and lifts the salsa up again palms up (5.6-7.5); then fists
    pumped as the crater spits rocks."""
    a = god(B('a_volcan', 9.0, 'hold'))
    a.pos('root', [(0, [0, 0, 0]), (0.3, [0, -2, 0], 'easeOutQuad'), (0.8, [0, 1, 0]), (1.1, [0, -4.2, 0], 'easeInQuad'),
                   (4.5, [0, -4.2, 0]), (5.4, [0, 0, 0], 'easeInOutSine'), (9.0, [0, 0, 0])], 'lin')
    a.rot('leg_r', [(0, [0, 0, 4]), (0.3, [-40, 0, 6]), (1.1, [-84, 0, 6], 'easeInQuad'), (4.5, [-84, 0, 6]), (5.4, [0, 0, 6]), (9.0, [0, 0, 6])], 'lin')
    a.rot('leg_l', [(0, [0, 0, -4]), (0.3, [-40, 0, -6]), (1.1, [70, 0, -8], 'easeInQuad'), (4.5, [70, 0, -8]), (5.4, [0, 0, -6]), (9.0, [0, 0, -6])], 'lin')
    pound = []
    for i in range(7):
        t = round(2.3 + 0.32 * i, 2)
        pound += [(round(t - 0.16, 2), [-150, 0, 20]), (t, [-30, 0, 10], 'easeInExpo')]
    a.rot('arm_r', [(0, [-28, 0, 26]), (0.8, [-176, 0, 10], 'easeOutQuad'), (1.1, [-20, 0, 6], 'easeInQuad')] + pound
          + [(4.6, [-30, 0, 10]), (5.6, [-100, 0, 110], 'easeInOutSine'), (6.6, [-160, -20, 40], 'easeInOutSine'), (7.5, [-170, 0, 20])]
          + [(round(7.6 + 0.3 * i, 2), [-170, 0, 20] if i % 2 else [-120, 0, 40]) for i in range(5)], 'lin')
    stretch(a, 'arm_r', [(0, 1), (1.1, 1.6), (4.5, 1.6), (5.4, 1.2), (9.0, 1.2)])
    a.rot('arm_l', [(0, [-28, 0, -26]), (0.8, [-176, 0, -10], 'easeOutQuad'), (1.1, [-60, 0, -40], 'easeInQuad'), (4.6, [-60, 0, -40]),
                    (5.6, [-100, 0, -110], 'easeInOutSine'), (6.6, [-160, 20, -40], 'easeInOutSine'), (7.5, [-170, 0, -20]),
                    (9.0, [-150, 0, -40])], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.3, [20, 0, 0]), (0.8, [-10, 0, 0]), (1.1, [30, 0, 0], 'easeInQuad'), (4.5, [26, 0, 0]),
                    (5.6, [-10, 40, 0], 'easeInOutSine'), (6.6, [-16, -40, 0], 'easeInOutSine'), (7.5, [-20, 0, 0]), (9.0, [-12, 0, 0])], 'lin')
    a.sound(1.1, 'slam')
    return a


def b_a_calaveras():
    """9.5 s (BossState.P3_CALAVERAS). Hovering, arms slowly raised palms up (the skulls rising, 0-0.6 s, again to 1.4),
    hands circling as they circle you; then a hand thrust at you for every skull that dives (from 3 s, one every 0.45 s,
    left and right in turn)."""
    a = god(B('a_calaveras', 9.5, 'hold'))
    flying_legs(a, 9.5)
    stir = [(round(1.4 + 0.4 * i, 2), [-110, 0, 60 if i % 2 else 90]) for i in range(4)]
    r = [(0, [-28, 0, 26]), (1.4, [-150, 0, 40], 'easeInOutSine')] + stir
    l = [(0, [-28, 0, -26]), (1.4, [-150, 0, -40], 'easeInOutSine')] + [(t, [v[0], v[1], -v[2]]) for t, v in stir]
    for i in range(14):
        t = round(3.0 + 0.45 * i, 2)
        if t > 9.2:
            break
        tgt = r if i % 2 == 0 else l
        sgn = 1 if i % 2 == 0 else -1
        tgt += [(round(t - 0.15, 2), [-60, 20 * sgn, 30 * sgn]), (t, [-96, -6 * sgn, 6 * sgn], 'easeOutExpo')]
    a.rot('arm_r', r + [(9.5, [-40, 0, 30])], 'lin')
    a.rot('arm_l', l + [(9.5, [-40, 0, -30])], 'lin')
    stretch(a, 'arm_r', [(0, 1), (3.0, 1.6), (9.5, 1.2)])
    stretch(a, 'arm_l', [(0, 1), (3.0, 1.6), (9.5, 1.2)])
    a.sound(0.6, 'whoosh')
    return a


def b_a_pinata_swing():
    """11.0 s (BossState.P3_PINATA_SWING). Arms flung up at the piñata coming down (0-1 s); hovering, a two-handed shove as
    each swing comes past (every 1.4 s); then, as he comes for it, a knee drawn in (7.6-8.5) and the flying kick (8.5);
    arms thrown wide in the candy storm."""
    a = god(B('a_pinata_swing', 11.0, 'hold'))
    flying_legs(a, 7.6)
    r = [(0, [-28, 0, 26]), (1.0, [-160, 0, 40], 'easeOutBack')]
    for i in range(5):
        t = round(1.6 + 1.4 * i, 2)
        if t > 7.4:
            break
        r += [(round(t - 0.25, 2), [-60, 0, 40]), (t, [-90, -30, 10], 'easeOutExpo'), (round(t + 0.5, 2), [-60, 0, 40])]
    r += [(7.6, [-72, -62, 0]), (8.3, [20, 0, 50], 'easeOutQuad'), (8.55, [10, 0, 70]), (8.9, [-150, 0, 60], 'easeOutBack'), (11.0, [-150, 0, 60])]
    a.rot('arm_r', r, 'lin')
    a.rot('arm_l', [(t, [v[0], -v[1], -v[2]]) + tuple(rest) for t, v, *rest in r], 'lin')
    a.rot('leg_r', [(7.6, [20, 0, 6]), (8.3, [-110, 0, 6], 'easeOutQuad'), (8.55, [-90, 0, 4], 'easeOutExpo'), (8.9, [20, 0, 6]),
                    (11.0, [20, 0, 6])], 'lin')
    a.rot('root', [(0, [0, 0, 0]), (7.6, [0, 0, 0]), (8.3, [-20, 0, 0]), (8.55, [-50, 0, 0], 'easeOutExpo'), (8.9, [0, 0, 0], 'easeOutBack'),
                   (11.0, [0, 0, 0])], 'lin')
    a.sound(8.5, 'punch').sound(8.9, 'roar_small')
    return a


def b_qte_hold_crush():
    """1.0 s loop (QTE: the squeeze). Both fists round you in front of his chest, crushing in pulses."""
    a = god(B('qte_hold_crush', 1.0, True))
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-96, -22 * s, 4 * s]), (0.5, [-96, -34 * s, 2 * s], 'easeInOutSine'), (1.0, [-96, -22 * s, 4 * s], 'easeInOutSine')])
        stretch(a, arm, [(0, 1.6)])
    a.rot('torso', [(0, [-8, 0, 0]), (0.5, [-4, 0, 0]), (1.0, [-8, 0, 0])])
    a.scale('body', [(0, [1.04, 1.03, 1.04]), (0.5, [1.1, 0.98, 1.1]), (1.0, [1.04, 1.03, 1.04])])
    return a


def b_qte_fail_crush():
    """2.0 s (QTE: the squeeze, failed). The squeeze goes all the way (to 1.2: the crack), and he hurls you away (1.3)."""
    a = god(B('qte_fail_crush', 2.0, 'hold'))
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-96, -22 * s, 4 * s]), (1.0, [-96, -44 * s, 0], 'easeInQuad'), (1.2, [-100, -50 * s, 0]),
                    (1.35, [-150, 20 * s, 40 * s], 'easeOutExpo'), (2.0, [-30, 0, 26 * s], 'easeInOutSine')], 'lin')
        stretch(a, arm, [(0, 1.6), (1.2, 1.5), (1.35, 1.8), (2.0, 1)])
    a.rot('torso', [(0, [-8, 0, 0]), (1.2, [6, 0, 0]), (1.35, [-24, 0, 0], 'easeOutExpo'), (2.0, [0, 0, 0])], 'lin')
    a.scale('body', [(0, [1.04, 1.03, 1.04]), (1.2, [1.18, 0.92, 1.18]), (1.35, [1.0, 1.08, 1.0]), (2.0, [1.04, 1.03, 1.04])], 'lin')
    a.sound(1.2, 'crack').sound(1.35, 'whoosh')
    return a


# ===================================================================== phase four: Dia de los Muertos
# The ritual (BossState.P4_*, beats p4.* from his lines: gen_boss_voice.beats) and El Rey de los Muertos's moves (b_d_*).
# The spirit hammer (spirit_hammer, off hand_r down the arm's line: a femur 20 px long, the head 27 px past the fist) is shown
# by client/BossModel while PaponBossEntity.spiritProp is 1, so every pose here keeps it off the floor unless it is slamming:
# the arm up, forward or out, never hanging.

HAM_CARRY = [-150, 8, 40]      # the hammer up and out at his right shoulder, ready
HAM_UP = [-176, 0, 14]         # raised straight up
HAM_BACK = [-212, 0, 10]       # wound up behind his head
HAM_SLAM = [-60, 0, 6]         # its head on the floor in front of him


def p4_beat(key, default):
    """A beat in TICKS (the Java's VoiceLines.beat), for beats defined from another one."""
    return VOICE_BEATS.get(key, default)


def hammer_arm(a, keys, stretch_keys=None):
    a.rot('arm_r', keys, 'lin')
    if stretch_keys:
        stretch(a, 'arm_r', stretch_keys)


# ---- the Final Guardian's awakening (2026-10-04, reworked to the user's script; beats p4.* from his lines)

PANT = 0.42            # a breath in and out, hurt


def pants(t0, t1, hard=1.0):
    """The body heaving with each breath, from t0 to t1."""
    keys = []
    i = 0
    t = t0
    while t <= t1:
        k = 0.05 * hard
        keys.append((round(t, 2), [1 + k, 1 - k * 0.8, 1 + k] if i % 2 else [1 - k * 0.4, 1 + k * 0.6, 1 - k * 0.4]))
        t += PANT
        i += 1
    return keys


def pained(a, t0, t1=None):
    """Brows up in the middle: hurting."""
    for brow, sgn in (('brow_r', 1), ('brow_l', -1)):
        a.pos(brow, [(t0, [0, 0.8, 0])] + ([(t1, [0, 0.8, 0])] if t1 else []), 'lin')
        a.rot(brow, [(t0, [0, 0, -14 * sgn])] + ([(t1, [0, 0, -14 * sgn])] if t1 else []), 'lin')


def walk_legs(a, t0, t1, cycle=1.4, swing=20):
    for leg, sgn in (('leg_r', 1), ('leg_l', -1)):
        keys = []
        t, i = t0, 0
        while t <= t1 + 1e-6:
            keys.append((round(t, 2), [swing * sgn * (1 if i % 2 == 0 else -1), 0, 4 * sgn]))
            t += cycle / 2
            i += 1
        keys.append((round(t1 + 0.3, 2), [0, 0, 4 * sgn]))
        a.rot(leg, keys)


SCAR_L = [-58, 46, -6]          # his left hand flat over the scar (low on his front)
CHIN_R = [-112, -42, 0]         # right hand to his chin
HEART_R = [-82, -52, 6]         # right hand on his heart
PALM_UP_R = [-78, -8, 34]       # right hand out, palm up, toward someone
POINT_UP_R = [-158, 0, 18]      # pointing at the sky


def b_p4_fall():
    """BossState.P4_FALL. The hit (0): flung back through the air (the entity flies him, 0-0.7), down flat on his back with a
    squash (p4.land), the skid; flat out, arms flung wide, chest heaving; he pushes up onto his right elbow (p4.elbow),
    looks down at himself (p4.look): the scar on his chest torn open, his left hand comes to it; "Huh... it reopened, didn't
    it." (p4.scar) with a pained breath of a laugh, still on his elbow."""
    land, elbow, look, line, end = (beat('p4.land', 14), beat('p4.elbow', 44), beat('p4.look', 58), beat('p4.scar', 76), beat('p4.fall_end', 150))
    a = B('p4_fall', end, 'hold')
    a.scale('blood', [(0, SHOWN), (end, SHOWN)], 'lin')
    up = round(elbow + 0.5, 2)
    a.rot('root', [(0, [0, 0, 0]), (0.06, [-12, 0, 0], 'easeOutQuad'), (round(land - 0.05, 2), [-64, 0, 0], 'easeInQuad'), (land, [-88, 0, 0]),
                   (round(land + 0.08, 2), [-83, 0, 0]), (round(land + 0.2, 2), [-88, 0, 3]), (round(land + 0.32, 2), [-88, 0, -2]),
                   (round(land + 0.5, 2), [-88, 0, 0]), (elbow, [-88, 0, 0]), (up, [-52, 0, 8], 'easeInOutSine'), (end, [-50, 0, 8])], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (round(land - 0.1, 2), [0, 1.8, 0]), (land, [0, 3.2, 0]), (elbow, [0, 3.2, 0]), (up, [0, 2.6, 0]),
                   (end, [0, 2.6, 0])], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.05, [34, 0, 0], 'easeOutExpo'), (round(land - 0.1, 2), [22, 0, 0]), (land, [-8, 0, 0], 'easeOutQuad'),
                    (elbow, [0, 0, 0]), (up, [10, 0, 0]), (look, [30, 0, 0], 'easeInOutSine'), (round(line - 0.1, 2), [28, 0, 0]),
                    (round(line + 0.3, 2), [22, 0, -4], 'easeInOutSine'), (end, [26, 0, -2])], 'lin')
    a.rot('arm_r', [(0, [-20, 0, 20]), (0.05, [-80, 20, 30], 'easeOutExpo'), (round(land - 0.1, 2), [-120, 10, 50]),
                    (round(land + 0.1, 2), [-150, 0, 80], 'easeOutQuad'), (elbow, [-150, 0, 78]), (up, [36, 0, 34], 'easeInOutSine'),
                    (end, [38, 0, 34])], 'lin')
    stretch(a, 'arm_r', [(0, 1), (elbow, 1), (up, 1.45), (end, 1.45)])
    a.rot('arm_l', [(0, [-20, 0, -20]), (0.05, [-80, -20, -30], 'easeOutExpo'), (round(land - 0.1, 2), [-120, -10, -50]),
                    (round(land + 0.1, 2), [-150, 0, -80], 'easeOutQuad'), (look, [-150, 0, -76]), (round(look + 0.6, 2), SCAR_L, 'easeInOutSine'),
                    (end, [SCAR_L[0] - 2, SCAR_L[1], SCAR_L[2]])], 'lin')
    stretch(a, 'arm_l', [(0, 1), (look, 1), (round(look + 0.6, 2), 1.3), (end, 1.3)])
    for leg, sgn in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [0, 0, 0]), (0.05, [-40, 0, 6 * sgn], 'easeOutExpo'), (round(land - 0.1, 2), [-50, 0, 10 * sgn]),
                    (round(land + 0.1, 2), [-20, 0, 18 * sgn]), (elbow, [-22, 0, 18 * sgn]), (up, [-40 if sgn > 0 else -10, 0, 14 * sgn]),
                    (end, [-40 if sgn > 0 else -10, 0, 14 * sgn])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (0.05, [1.1, 0.88, 1.1], 'easeOutExpo'), (round(land - 0.05, 2), [1, 1, 1]), (land, [1.24, 0.78, 1.24]),
                     (round(land + 0.15, 2), [0.96, 1.04, 0.96], 'easeOutQuad')] + pants(round(land + 0.3, 2), end, 1.3), 'lin')
    pained(a, land, end)
    a.sound(0.0, 'punch').sound(0.05, 'whoosh').sound(land, 'slam').sound(round(land + 0.05, 2), 'grunt').sound(up, 'grunt')
    return a


def b_p4_riseup():
    """BossState.P4_RISEUP. From his elbow over onto a knee (p4.knee), his right hand on it, head hanging; the cough
    (p4.cough): doubled over, twice, the back of his left hand to his mouth, then back over the scar; up onto his feet,
    unsteady, hunched; "Do you want to know... how I got this scar?" (p4.howgot), his head coming up to you, a lopsided
    smile, the right hand opening toward you."""
    knee, cough, line, end = beat('p4.knee', 16), beat('p4.cough', 36), beat('p4.howgot', 52), beat('p4.riseup_end', 110)
    a = B('p4_riseup', end, 'hold')
    a.scale('blood', [(0, SHOWN), (end, SHOWN)], 'lin')
    stand = round(line - 0.3, 2)
    a.rot('root', [(0, [-50, 0, 8]), (round(knee * 0.5, 2), [-60, 0, -30], 'easeInOutSine'), (knee, [0, 0, -6], 'easeInOutSine'),
                   (stand, [0, 0, -4]), (round(stand + 0.4, 2), [5, 0, 4], 'easeInOutSine'), (round(stand + 0.8, 2), [3, 0, -3]), (end, [3, 0, 2])], 'lin')
    a.pos('root', [(0, [0, 2.6, 0]), (round(knee * 0.5, 2), [0, 1.6, 0]), (knee, [0, -4.2, 0], 'easeInOutSine'), (round(stand - 0.5, 2), [0, -4.2, 0]),
                   (stand, [0, -0.8, 0], 'easeInOutSine'), (end, [0, -0.6, 0])], 'lin')
    c2 = round(cough + 0.35, 2)
    a.rot('torso', [(0, [26, 0, -2]), (knee, [34, 0, 0], 'easeInOutSine'), (round(cough - 0.1, 2), [30, 0, 0]), (cough, [52, 0, 0], 'easeOutExpo'),
                    (round(cough + 0.2, 2), [36, 0, 0]), (c2, [50, 0, 0], 'easeOutExpo'), (round(c2 + 0.3, 2), [34, 0, 0]),
                    (stand, [24, 0, 0]), (line, [18, 0, 0]), (round(line + 0.6, 2), [6, 0, 0], 'easeInOutSine'), (end, [8, 0, 0])], 'lin')
    a.rot('arm_r', [(0, [38, 0, 34]), (knee, [-40, -10, 10], 'easeInOutSine'), (stand, [-40, -10, 10]), (round(stand + 0.4, 2), [-14, 0, 20]),
                    (round(line + 0.8, 2), [-14, 0, 20]), (round(line + 1.3, 2), PALM_UP_R, 'easeInOutSine'), (end, PALM_UP_R)], 'lin')
    stretch(a, 'arm_r', [(0, 1.45), (knee, 1.35), (stand, 1.35), (round(stand + 0.4, 2), 1), (round(line + 1.3, 2), 1.3), (end, 1.3)])
    a.rot('arm_l', [(0, SCAR_L), (round(cough - 0.15, 2), SCAR_L), (cough, [-92, 25, 0], 'easeOutExpo'), (round(c2 + 0.35, 2), [-92, 25, 0]),
                    (round(c2 + 0.8, 2), SCAR_L, 'easeInOutSine'), (end, SCAR_L)], 'lin')
    stretch(a, 'arm_l', [(0, 1.3), (round(cough - 0.15, 2), 1.3), (cough, 1.6), (round(c2 + 0.35, 2), 1.6), (round(c2 + 0.8, 2), 1.3), (end, 1.3)])
    for leg, sgn in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [-40 if sgn > 0 else -10, 0, 14 * sgn]), (knee, [-80 if sgn > 0 else 70, 0, 6 * sgn], 'easeInOutSine'),
                    (round(stand - 0.5, 2), [-80 if sgn > 0 else 70, 0, 6 * sgn]), (stand, [-6, 0, 8 * sgn], 'easeInOutSine'), (end, [-4, 0, 8 * sgn])], 'lin')
    a.scale('body', pants(0, end, 1.1), 'lin')
    pained(a, 0, round(line + 0.4, 2))
    for brow, sgn in (('brow_r', 1), ('brow_l', -1)):
        # one brow up: the lopsided, knowing look
        a.pos(brow, [(round(line + 0.8, 2), [0, 0.9 if sgn > 0 else 0.1, 0])], 'lin')
        a.rot(brow, [(round(line + 0.8, 2), [0, 0, -16 * sgn if sgn > 0 else 6 * sgn])], 'lin')
    a.sound(knee, 'grunt').sound(cough, 'grunt').sound(c2, 'grunt')
    return a


def b_p4_ignis():
    """BossState.P4_IGNIS. His right hand goes behind his back (0.2-0.9) and comes round with something (p4.head_out): Ignis's
    head, held up by its crown at shoulder height, turned to show you, through the line (p4.ignis); thrown down at his feet
    (p4.throw: a wind-up and the fling); he dusts his hand off, then a hand opened toward you, head cocked, one brow up: "I
    assume you two have met before, no?" (p4.met)."""
    out, line, thr, met, end = (beat('p4.head_out', 20), beat('p4.ignis', 34), beat('p4.throw', 109), beat('p4.met', 138), beat('p4.ignis_end', 200))
    a = B('p4_ignis', end, 'hold')
    a.scale('blood', [(0, SHOWN), (end, SHOWN)], 'lin')
    show = [8, 0, 150]             # raised out to his side, above his shoulder: the head hangs from his fist beside his face
    turn = [(round(line + 0.5 + 0.9 * i, 2), [show[0] + (10 if i % 2 else -6), show[1], show[2] + (6 if i % 2 else -4)]) for i in range(int((thr - line - 1.2) / 0.9))]
    wind = round(thr - 0.3, 2)
    a.rot('arm_r', [(0, PALM_UP_R), (0.25, [10, -10, 30], 'easeInOutSine'), (0.6, [62, 20, 24], 'easeInOutSine'), (round(out - 0.05, 2), [60, 24, 20]),
                    (round(out + 0.45, 2), show, 'easeOutBack')] + turn + [(wind, [-150, 0, 34], 'easeInOutSine'),
                    (thr, [-36, -10, 16], 'easeOutExpo'), (round(thr + 0.5, 2), [-20, 0, 16]), (round(met - 0.1, 2), [-20, 0, 16]),
                    (round(met + 0.4, 2), PALM_UP_R, 'easeInOutSine'), (end, PALM_UP_R)], 'lin')
    stretch(a, 'arm_r', [(0, 1.3), (0.6, 1.2), (round(out + 0.45, 2), 1.5), (wind, 1.6), (thr, 1.4), (round(thr + 0.5, 2), 1), (round(met + 0.4, 2), 1.35), (end, 1.35)])
    a.rot('arm_l', [(0, SCAR_L), (round(thr + 0.6, 2), SCAR_L), (round(thr + 0.9, 2), [-70, 30, -14]), (round(thr + 1.1, 2), [-74, 40, -10]),
                    (round(met + 0.4, 2), [-14, 0, -28], 'easeInOutSine'), (end, [-14, 0, -28])], 'lin')
    stretch(a, 'arm_l', [(0, 1.3), (round(thr + 0.6, 2), 1.3), (round(met + 0.4, 2), 1), (end, 1)])
    a.rot('torso', [(0, [8, 0, 0]), (0.6, [14, -16, 0], 'easeInOutSine'), (round(out + 0.45, 2), [-2, 10, 0], 'easeOutBack'), (round(line + 0.8, 2), [-4, 8, 0]),
                    (wind, [-12, 14, 0]), (thr, [22, -10, 0], 'easeOutExpo'), (round(thr + 0.5, 2), [10, 0, 0]), (met, [6, 0, 0]),
                    (round(met + 0.5, 2), [-4, 0, 6], 'easeInOutSine'), (end, [-4, 0, 6])], 'lin')
    a.scale('body', pants(0, round(line + 2, 2), 0.6) + [(end, [1, 1, 1])], 'lin')
    pained(a, 0, round(out, 2))
    for brow, sgn in (('brow_r', 1), ('brow_l', -1)):
        a.pos(brow, [(round(out + 0.4, 2), [0, 0, 0]), (round(met + 0.3, 2), [0, 0.9 if sgn > 0 else -0.3, 0])], 'lin')
        a.rot(brow, [(round(out + 0.4, 2), [0, 0, 0]), (round(met + 0.3, 2), [0, 0, -16 * sgn if sgn > 0 else 10 * sgn])], 'lin')
    a.sound(0.3, 'whoosh').sound(out, 'grunt').sound(thr, 'whoosh')
    return a


def b_p4_prophecy():
    """BossState.P4_PROPHECY. A slow walk round, hands clasped behind his back (to p4.moon): "Ten thousand years..." with his
    head bowed, then up; "...the prophecy might finally be fulfilled." (p4.prophecy) a hand coming round in front of him,
    open, closing into a fist on "fulfilled"; he stops, looks up at the moon in the fog (p4.moon), and smiles: "Ha... la
    luna knows it too." (p4.lunaknows), a chuckle in his shoulders, a finger up at it."""
    years, line, moon, luna, end = (beat('p4.years', 8), beat('p4.prophecy', 102), beat('p4.moon', 186), beat('p4.lunaknows', 212),
                                    beat('p4.prophecy_end', 270))
    a = god(B('p4_prophecy', end, 'hold'))
    behind_r, behind_l = [38, -36, 6], [38, 36, -6]
    fist = round(line + 3.0, 2)
    a.rot('arm_r', [(0, PALM_UP_R), (0.6, behind_r, 'easeInOutSine'), (round(line + 0.2, 2), behind_r), (round(line + 0.9, 2), PALM_UP_R, 'easeInOutSine'),
                    (round(fist - 0.3, 2), [-84, -14, 30]), (fist, [-92, -30, 10], 'easeOutBack'), (round(moon - 0.2, 2), [-90, -30, 10]),
                    (round(luna + 0.1, 2), POINT_UP_R, 'easeInOutSine'), (round(luna + 1.6, 2), POINT_UP_R), (end, [-20, 0, 16], 'easeInOutSine')], 'lin')
    stretch(a, 'arm_r', [(0, 1.35), (0.6, 1.1), (round(line + 0.9, 2), 1.3), (fist, 1.2), (round(luna + 0.1, 2), 1.7), (round(luna + 1.6, 2), 1.7), (end, 1)])
    a.rot('arm_l', [(0, [-14, 0, -28]), (0.6, behind_l, 'easeInOutSine'), (round(moon + 0.4, 2), behind_l), (round(moon + 1.0, 2), [-10, 0, -22], 'easeInOutSine'),
                    (end, [-10, 0, -22])], 'lin')
    a.rot('torso', [(0, [-4, 0, 6]), (years, [16, 0, 0], 'easeInOutSine'), (round(years + 2.4, 2), [14, 0, 0]), (round(years + 3.4, 2), [0, 0, 0], 'easeInOutSine'),
                    (moon, [0, 0, 0]), (round(moon + 0.8, 2), [-26, 0, 0], 'easeInOutSine'), (luna, [-24, 0, 0])]
          + [(round(luna + 0.15 + 0.14 * i, 2), [-30 if i % 2 else -22, 0, 0]) for i in range(5)] + [(end, [-20, 0, 0])], 'lin')
    walk_legs(a, 0.4, round(moon - 0.3, 2), 1.6, 18)
    a.pos('root', [(round(0.4 + 0.4 * i, 2), [0, 0.6 if i % 2 else 0, 0]) for i in range(int((moon - 0.6) / 0.4))] + [(moon, [0, 0, 0])])
    a.sound(luna, 'chuckle')
    return a


def b_p4_brethren():
    """BossState.P4_BRETHREN. Turned to the stands, arms opening wide to them (0.2-0.8): "My brethren... this is it. This is
    what we were made for." (p4.brethren) a fist to his chest on "made for"; then up, both arms rising to the moon,
    "...grant me your STRENGTH!" (p4.grant), the fists clenching, head thrown back as the moon turns (p4.turn); held,
    shaking with it."""
    line, grant, turn, end = beat('p4.brethren', 14), beat('p4.grant', 114), beat('p4.turn', 194), beat('p4.brethren_end', 234)
    a = god(B('p4_brethren', end, 'hold'))
    wide_r, wide_l = [-100, 0, 78], [-100, 0, -78]
    chest = round(line + 3.2, 2)
    up_r, up_l = [-168, 0, 34], [-168, 0, -34]
    shake = [(round(turn + 0.1 * i, 2), [up_r[0] + (3 if i % 2 else -3), 0, up_r[2]]) for i in range(1, int((end - turn) / 0.1))]
    a.rot('arm_r', [(0, [-20, 0, 16]), (0.7, wide_r, 'easeOutBack'), (round(chest - 0.3, 2), [-104, 0, 82]), (chest, HEART_R, 'easeOutBack'),
                    (round(chest + 1.0, 2), HEART_R), (round(grant - 0.1, 2), wide_r, 'easeInOutSine'), (turn, up_r, 'easeInOutSine')] + shake
          + [(end, up_r)], 'lin')
    stretch(a, 'arm_r', [(0, 1), (0.7, 1.5), (chest, 1.2), (round(grant - 0.1, 2), 1.5), (turn, 1.8), (end, 1.8)])
    a.rot('arm_l', [(0, [-10, 0, -22]), (0.7, wide_l, 'easeOutBack'), (round(grant - 0.1, 2), [-104, 0, -82]), (turn, up_l, 'easeInOutSine'),
                    (end, up_l)], 'lin')
    stretch(a, 'arm_l', [(0, 1), (0.7, 1.5), (round(grant - 0.1, 2), 1.5), (turn, 1.8), (end, 1.8)])
    a.rot('torso', [(0, [-20, 0, 0]), (0.7, [-10, 0, 0]), (chest, [10, 0, 0], 'easeOutBack'), (round(chest + 1.0, 2), [4, 0, 0]),
                    (grant, [-6, 0, 0]), (turn, [-30, 0, 0], 'easeInOutSine'), (round(turn + 0.4, 2), [-36, 0, 0], 'easeOutBack'), (end, [-32, 0, 0])], 'lin')
    a.scale('body', [(0, [1.04, 1.03, 1.04]), (turn, [1.08, 1.08, 1.08]), (round(turn + 0.3, 2), [1.14, 1.1, 1.14], 'easeOutBack'), (end, [1.12, 1.1, 1.12])], 'lin')
    a.sound(turn, 'roar')
    return a


def b_p4_souls():
    """3.0 s loop (BossState.P4_SOULS). Floating, the souls pouring into him: arms open low, palms out, head back, breathing
    it in, swelling and settling with it."""
    a = god(B('p4_souls', 3.0, True))
    a.rot('torso', [(0, [-18, 0, 0]), (1.5, [-26, 0, 0]), (3.0, [-18, 0, 0])])
    a.rot('arm_r', [(0, [-40, 0, 62]), (1.5, [-48, 0, 70]), (3.0, [-40, 0, 62])])
    a.rot('arm_l', [(0, [-40, 0, -62]), (1.5, [-48, 0, -70]), (3.0, [-40, 0, -62])])
    stretch(a, 'arm_r', [(0, 1.3)])
    stretch(a, 'arm_l', [(0, 1.3)])
    a.scale('body', [(0, [1.04, 1.03, 1.04]), (1.5, [1.1, 1.08, 1.1]), (3.0, [1.04, 1.03, 1.04])])
    flying_legs(a, 3.0, 14)
    return a


def b_p4_halfbaked():
    """BossState.P4_HALFBAKED. Blazing green: a hand to his chin, "You know..." (p4.halfbaked), the finger wagging, "...it's
    not right to go into a fight half baked." and the smirk; the burst (p4.burst): arms flung wide and back, head back,
    swelling; the flash (p4.flash, his truest form); down in the new arena (p4.clear) in a hero's landing, one knee, a fist to
    the floor; up, slowly, into his stance as the wings open (the form controller)."""
    line, burst, flash, clear, end = (beat('p4.halfbaked', 12), beat('p4.burst', 106), beat('p4.flash', 136), beat('p4.clear', 162),
                                      beat('p4.halfbaked_end', 242))
    a = god(B('p4_halfbaked', end, 'hold'))
    wag = [(round(line + 1.0 + 0.18 * i, 2), [CHIN_R[0] - 10, CHIN_R[1] + (8 if i % 2 else -8), 20]) for i in range(5)]
    rise = round(clear + 1.2, 2)
    a.rot('arm_r', [(0, [-40, 0, 62]), (0.5, CHIN_R, 'easeInOutSine'), (line + 0.9, CHIN_R)] + wag
          + [(round(line + 2.4, 2), PALM_UP_R, 'easeInOutSine'), (round(burst - 0.1, 2), PALM_UP_R), (burst, [-130, 30, 110], 'easeOutExpo'),
             (flash, [-140, 30, 118]), (round(flash + 0.4, 2), [-30, 0, 10]), (clear, [-28, 0, 8]), (rise, [-24, 0, 30], 'easeInOutSine'),
             (end, [-24, 0, 30])], 'lin')
    stretch(a, 'arm_r', [(0, 1.3), (0.5, 1.4), (round(line + 2.4, 2), 1.3), (burst, 1.8), (flash, 1.8), (clear, 1.6), (rise, 1), (end, 1)])
    a.rot('arm_l', [(0, [-40, 0, -62]), (0.5, [-10, 0, -20]), (round(burst - 0.1, 2), [-10, 0, -20]), (burst, [-130, -30, -110], 'easeOutExpo'),
                    (flash, [-140, -30, -118]), (round(flash + 0.4, 2), [-60, 0, -60]), (clear, [-60, 0, -64]), (rise, [-24, 0, -30], 'easeInOutSine'),
                    (end, [-24, 0, -30])], 'lin')
    stretch(a, 'arm_l', [(0, 1.3), (0.5, 1), (burst, 1.8), (flash, 1.8), (clear, 1.2), (rise, 1), (end, 1)])
    a.rot('torso', [(0, [-14, 0, 0]), (0.5, [4, 0, 0]), (round(line + 2.4, 2), [0, 0, 4]), (burst, [-34, 0, 0], 'easeOutExpo'), (flash, [-38, 0, 0]),
                    (round(flash + 0.4, 2), [30, 0, 0]), (clear, [32, 0, 0]), (rise, [-6, 0, 0], 'easeInOutSine'), (end, [-8, 0, 0])], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (flash, [0, 0, 0]), (round(flash + 0.4, 2), [0, -4.5, 0]), (clear, [0, -4.5, 0]), (rise, [0, 0, 0], 'easeInOutSine'),
                   (end, [0, 0, 0])], 'lin')
    for leg, sgn in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [14, 0, 5 * sgn]), (flash, [14, 0, 5 * sgn]), (round(flash + 0.4, 2), [-80 if sgn > 0 else 64, 0, 6 * sgn]),
                    (clear, [-80 if sgn > 0 else 64, 0, 6 * sgn]), (rise, [0, 0, 8 * sgn], 'easeInOutSine'), (end, [0, 0, 8 * sgn])], 'lin')
    a.scale('body', [(0, [1.08, 1.06, 1.08]), (burst, [1.1, 1.08, 1.1]), (round(burst + 0.3, 2), [1.18, 1.16, 1.18], 'easeOutBack'),
                     (flash, [1.2, 1.18, 1.2]), (round(flash + 0.4, 2), [1.04, 1.04, 1.04]), (clear, [1.18, 0.88, 1.18], 'easeOutQuad'),
                     (round(clear + 0.3, 2), [1.04, 1.04, 1.04]), (end, [1.06, 1.05, 1.06])], 'lin')
    a.sound(burst, 'scream').sound(clear, 'slam').sound(rise, 'roar')
    return a


BOW_UP = [-138, 0, -12]          # the bow arm up at the moon (left)
NOCK_R = [-124, -54, 2]          # the right hand at the bow, nocking
DRAW_R = [-136, 30, 44]          # drawn back to his cheek


def bow_shot(a, t0, draw, loose):
    """The right hand: to the bow (nock), the draw, the loose; the drawn string and the arrow shown between."""
    a.vis('bow_drawn', False, [(draw, True), (loose, False)])
    a.vis('aztec_arrow', False, [(round(t0 + 0.15, 2), True), (loose, False)])


def b_p4_bow():
    """6.0 s (BossState.P4_BOW, 120 t). The bow forms in his left fist (0.4: PaponBossEntity.BOW_FORM), swung up at the moon;
    his right hand to it, nocking an arrow of light (1.3); the draw, slow, to his cheek, the body leaning back into it (1.7-3.2);
    the loose (3.3: BOW_LOOSE): the string hand flung back and open; he holds the bow up, watching the arrow go."""
    a = god(B('p4_bow', 6.0, 'hold'))
    nock, draw, loose = 1.3, 1.7, 3.3
    bow_shot(a, nock, draw, loose)
    a.rot('arm_l', [(0, [-24, 0, -30]), (0.4, [-60, 0, -40]), (0.9, BOW_UP, 'easeOutBack'), (loose, BOW_UP), (round(loose + 0.1, 2), [-142, 0, -12]),
                    (6.0, [-130, 0, -14])], 'lin')
    stretch(a, 'arm_l', [(0, 1), (0.9, 1.35), (6.0, 1.35)])
    a.rot('arm_r', [(0, [-24, 0, 30]), (nock - 0.3, [-60, -20, 20]), (nock, NOCK_R, 'easeOutQuad'), (draw, NOCK_R), (round(loose - 0.05, 2), DRAW_R, 'easeInOutSine'),
                    (round(loose + 0.15, 2), [-100, 50, 90], 'easeOutExpo'), (round(loose + 1.0, 2), [-60, 20, 60]), (6.0, [-30, 0, 30], 'easeInOutSine')], 'lin')
    stretch(a, 'arm_r', [(0, 1), (nock, 1.55), (draw, 1.55), (round(loose - 0.05, 2), 1.05), (round(loose + 0.15, 2), 1.3), (6.0, 1)])
    a.rot('torso', [(0, [-6, 0, 0]), (0.9, [-18, -10, 0]), (draw, [-18, -12, 0]), (round(loose - 0.05, 2), [-26, -22, 0], 'easeInOutSine'),
                    (round(loose + 0.1, 2), [-20, -14, 0], 'easeOutExpo'), (6.0, [-22, -10, 0])], 'lin')
    for leg, sgn in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [0, 0, 6 * sgn]), (0.9, [20 * sgn, 0, 10 * sgn]), (6.0, [20 * sgn, 0, 10 * sgn])], 'lin')
    a.sound(nock, 'swing').sound(loose, 'whoosh')
    return a


def b_p4_watch():
    """4.0 s loop (BossState.P4_SERPENTS). Watching his serpents come down out of the sky: the bow lowered in his left hand,
    his right on his hip, chin up, a slow turn of his head as they pass, a grin."""
    a = god(B('p4_watch', 4.0, True))
    a.rot('torso', [(0, [-22, 10, 0]), (2.0, [-26, -12, 0]), (4.0, [-22, 10, 0])])
    a.rot('arm_l', [(0, [-30, 0, -24]), (2.0, [-34, 0, -26]), (4.0, [-30, 0, -24])])
    a.rot('arm_r', [(0, [12, -40, 44]), (4.0, [12, -40, 44])])
    stretch(a, 'arm_r', [(0, 1.1)])
    a.scale('body', [(0, [1.04, 1.03, 1.04]), (2.0, [1.06, 1.05, 1.06]), (4.0, [1.04, 1.03, 1.04])])
    return a


def b_p4_talking():
    """BossState.P4_TALKING. "Now we're TALKING!" (p4.talking): his right fist pumped at the sky twice, the bow up in his
    left, a bounce in his knees."""
    line, end = beat('p4.talking', 6), beat('p4.talking_end', 56)
    a = god(B('p4_talking', end, 'hold'))
    p1, p2 = round(line + 0.4, 2), round(line + 0.9, 2)
    a.rot('arm_r', [(0, [12, -40, 44]), (round(line + 0.15, 2), [-120, 0, 30], 'easeInOutSine'), (p1, [-172, 0, 20], 'easeOutExpo'),
                    (round(p1 + 0.2, 2), [-140, 0, 26]), (p2, [-174, 0, 18], 'easeOutExpo'), (end, [-160, 0, 22])], 'lin')
    stretch(a, 'arm_r', [(0, 1.1), (p1, 1.8), (round(p1 + 0.2, 2), 1.5), (p2, 1.8), (end, 1.7)])
    a.rot('arm_l', [(0, [-30, 0, -24]), (end, [-60, 0, -40])])
    a.rot('torso', [(0, [-22, 0, 0]), (p1, [-12, 0, 0]), (p2, [-16, 0, 0]), (end, [-10, 0, 0])])
    a.pos('root', [(0, [0, 0, 0]), (p1, [0, -1.2, 0]), (round(p1 + 0.2, 2), [0, 0.4, 0]), (p2, [0, -1.2, 0]), (end, [0, 0, 0])])
    return a


def b_p4_walk():
    """1.2 s loop: the Final Guardian walking, unhurried: long strides, shoulders back, arms swinging a little."""
    a = god(B('p4_walk', 1.2, True))
    a.rot('leg_r', [(0, [24, 0, 4]), (0.6, [-24, 0, 4]), (1.2, [24, 0, 4])])
    a.rot('leg_l', [(0, [-24, 0, -4]), (0.6, [24, 0, -4]), (1.2, [-24, 0, -4])])
    a.rot('arm_r', [(0, [-16, 0, 14]), (0.6, [16, 0, 16]), (1.2, [-16, 0, 14])])
    a.rot('arm_l', [(0, [16, 0, -16]), (0.6, [-16, 0, -14]), (1.2, [16, 0, -16])])
    a.rot('torso', [(0, [-6, 4, 0]), (0.6, [-6, -4, 0]), (1.2, [-6, 4, 0])])
    a.pos('root', [(0, [0, 0, 0]), (0.3, [0, 0.9, 0]), (0.6, [0, 0, 0]), (0.9, [0, 0.9, 0]), (1.2, [0, 0, 0])])
    return a


def b_p4_honor():
    """BossState.P4_HONOR. Softly, to the band: his right hand on his heart, his head bowed (p4.honor: "Señores... it has been
    an honor."), the hand opening to the singer ("But I need you to play one last song."); the nod back; the brass strikes
    up (p4.song): he steps back, arms opening, head nodding on the beat, eyes closed."""
    line, nod, song, end = beat('p4.honor', 10), beat('p4.nod', 112), beat('p4.song', 128), beat('p4.honor_end', 172)
    a = B('p4_honor', end, 'hold')
    offer = round(line + 2.6, 2)
    a.rot('arm_r', [(0, [-20, 0, 16]), (round(line - 0.1, 2), HEART_R, 'easeInOutSine'), (offer, HEART_R), (round(offer + 0.6, 2), PALM_UP_R, 'easeInOutSine'),
                    (song, PALM_UP_R), (round(song + 0.6, 2), [-60, 0, 60], 'easeInOutSine'), (end, [-62, 0, 62])], 'lin')
    stretch(a, 'arm_r', [(0, 1), (round(line - 0.1, 2), 1.2), (round(offer + 0.6, 2), 1.4), (song, 1.4), (round(song + 0.6, 2), 1.3), (end, 1.3)])
    a.rot('arm_l', [(0, [-10, 0, -20]), (song, [-10, 0, -20]), (round(song + 0.6, 2), [-60, 0, -60], 'easeInOutSine'), (end, [-62, 0, -62])], 'lin')
    stretch(a, 'arm_l', [(0, 1), (song, 1), (round(song + 0.6, 2), 1.3), (end, 1.3)])
    groove = [(round(song + 0.6 + 0.47 * i, 2), [6 if i % 2 else -2, 0, 0]) for i in range(int((end - song - 0.6) / 0.47))]
    a.rot('torso', [(0, [0, 0, 0]), (round(line + 0.6, 2), [16, 0, 0], 'easeInOutSine'), (round(offer - 0.2, 2), [14, 0, 0]),
                    (round(offer + 0.6, 2), [4, 0, 0], 'easeInOutSine'), (nod, [6, 0, 0]), (round(song + 0.6, 2), [-8, 0, 0])] + groove, 'lin')
    for brow, sgn in (('brow_r', 1), ('brow_l', -1)):
        a.pos(brow, [(0, [0, 0.4, 0]), (end, [0, 0.4, 0])], 'lin')
        a.rot(brow, [(0, [0, 0, -6 * sgn]), (end, [0, 0, -6 * sgn])], 'lin')
    return a


def b_p4_muchachas():
    """BossState.P4_MUCHACHAS. Hands up, looking round: "Hey! Where are the muchachas?" (p4.muchachas); two claps over his head
    (p4.clap); he watches them rise (p4.rise) and walks over to the nearest; the twirl (p4.twirl): his left hand high over her
    head, circling, his right on his hip ("Ha ha! Still got it!"); the dip (p4.dip): leaning over her, his right arm under her
    back, his left flung out; up, and a few salsa steps with her, the hips going."""
    line, clap, rise, twirl, dip, end = (beat('p4.muchachas', 4), beat('p4.clap', 50), beat('p4.rise', 60), beat('p4.twirl', 112),
                                         beat('p4.dip', 170), beat('p4.muchachas_end', 222))
    a = god(B('p4_muchachas', end, 'hold'))
    c1, c2 = clap, round(clap + 0.3, 2)
    a.rot('arm_r', [(0, [-60, 0, 60]), (round(line + 0.2, 2), [-100, 0, 70], 'easeOutBack'), (round(clap - 0.2, 2), [-140, -20, 30]),
                    (c1, [-150, -50, 6], 'easeOutQuad'), (round(c1 + 0.15, 2), [-140, -20, 30]), (c2, [-150, -50, 6], 'easeOutQuad'),
                    (round(c2 + 0.4, 2), [-20, 0, 18], 'easeInOutSine'), (round(twirl - 0.2, 2), [-20, 0, 18]), (twirl, [12, -40, 44], 'easeInOutSine'),
                    (round(dip - 0.3, 2), [12, -40, 44]), (dip, [-72, -22, 12], 'easeInOutSine'), (round(dip + 1.1, 2), [-72, -22, 12]),
                    (round(dip + 1.6, 2), [-60, 0, 50], 'easeInOutSine'), (end, [-60, 0, 50])], 'lin')
    stretch(a, 'arm_r', [(0, 1.3), (c1, 1.6), (round(c2 + 0.4, 2), 1), (twirl, 1.1), (dip, 1.5), (round(dip + 1.1, 2), 1.5), (end, 1.2)])
    circle = [(round(twirl + 0.3 + 0.25 * i, 2), [-172, (14 if i % 2 else -14), -8]) for i in range(int((dip - twirl - 0.6) / 0.25))]
    a.rot('arm_l', [(0, [-60, 0, -60]), (round(line + 0.2, 2), [-100, 0, -70], 'easeOutBack'), (round(clap - 0.2, 2), [-140, 20, -30]),
                    (c1, [-150, 50, -6], 'easeOutQuad'), (round(c1 + 0.15, 2), [-140, 20, -30]), (c2, [-150, 50, -6], 'easeOutQuad'),
                    (round(c2 + 0.4, 2), [-20, 0, -18], 'easeInOutSine'), (round(twirl - 0.2, 2), [-60, 0, -20]), (round(twirl + 0.3, 2), [-172, 0, -8], 'easeOutBack')]
          + circle + [(dip, [-84, 0, -66], 'easeInOutSine'), (round(dip + 1.1, 2), [-86, 0, -70]), (round(dip + 1.6, 2), [-60, 0, -50], 'easeInOutSine'),
                      (end, [-60, 0, -50])], 'lin')
    stretch(a, 'arm_l', [(0, 1.3), (c1, 1.6), (round(c2 + 0.4, 2), 1), (round(twirl + 0.3, 2), 1.8), (round(dip - 0.2, 2), 1.8), (dip, 1.4), (end, 1.2)])
    look = [(round(line + 0.3 + 0.5 * i, 2), [-6, 22 if i % 2 else -22, 0]) for i in range(4)]
    salsa = [(round(dip + 1.6 + 0.47 * i, 2), [0, 4, 8 if i % 2 else -8]) for i in range(int((end - dip - 1.6) / 0.47) + 1)]
    a.rot('torso', [(0, [-10, 0, 0])] + look + [(clap, [-14, 0, 0]), (twirl, [-4, 0, 0]), (round(dip - 0.3, 2), [-4, 0, 0]), (dip, [30, 0, 0], 'easeInOutSine'),
                                                (round(dip + 1.1, 2), [32, 0, 0]), (round(dip + 1.6, 2), [0, 0, 0], 'easeInOutSine')] + salsa, 'lin')
    a.pos('root', [(0, [0, 0, 0]), (round(dip - 0.3, 2), [0, 0, 0]), (dip, [0, -2.2, 0], 'easeInOutSine'), (round(dip + 1.1, 2), [0, -2.2, 0]),
                   (round(dip + 1.6, 2), [0, 0, 0], 'easeInOutSine')] + [(round(dip + 1.6 + 0.47 * i, 2), [0, 0.6 if i % 2 else 0, 0])
                                                                    for i in range(int((end - dip - 1.6) / 0.47) + 1)], 'lin')
    walk_legs(a, round(rise + 0.4, 2), round(twirl - 0.4, 2), 1.2, 20)
    a.sound(c1, 'clap').sound(c2, 'clap')
    return a


def b_p4_goodtime():
    """BossState.P4_GOODTIME (held from its last pose until the first sung word). Striding at you, shoulders rolling (to
    p4.goodtime); leaning in, arms flung wide, "COME ON!!!" then beckoning you in with his right hand, "SHOW ME A GOOD
    TIME!!!", the smirk, one brow up; the eruption (p4.erupt): arms thrown up to the bowl; then down into his stance, fists up."""
    line, erupt = beat('p4.goodtime', 40), beat('p4.erupt', 103)
    end = round(erupt + 6.0, 2)
    a = god(B('p4_goodtime', end, 'hold'))
    walk_legs(a, 0.0, round(line - 0.3, 2), 1.0, 24)
    come = round(line + 1.0, 2)
    beck = [(round(come + 0.25 * i, 2), [-96, -10 if i % 2 else -40, 26 if i % 2 else 10]) for i in range(1, 6)]
    stance = round(erupt + 1.4, 2)
    a.rot('arm_r', [(0, [-16, 0, 14]), (round(line - 0.2, 2), [-30, 0, 30]), (line, [-96, 0, 86], 'easeOutBack'), (come, [-96, 0, 80])] + beck
          + [(round(erupt - 0.1, 2), [-100, -30, 20]), (erupt, [-170, 0, 36], 'easeOutExpo'), (round(erupt + 0.8, 2), [-168, 0, 34]),
             (stance, [-92, -30, 22], 'easeInOutSine'), (end, [-92, -32, 22])], 'lin')
    stretch(a, 'arm_r', [(0, 1), (line, 1.5), (come, 1.4), (erupt, 1.8), (round(erupt + 0.8, 2), 1.8), (stance, 1.2), (end, 1.2)])
    a.rot('arm_l', [(0, [16, 0, -16]), (round(line - 0.2, 2), [-30, 0, -30]), (line, [-96, 0, -86], 'easeOutBack'), (come, [-96, 0, -80]),
                    (round(come + 0.3, 2), [12, 40, -44], 'easeInOutSine'), (round(erupt - 0.1, 2), [12, 40, -44]), (erupt, [-170, 0, -36], 'easeOutExpo'),
                    (round(erupt + 0.8, 2), [-168, 0, -34]), (stance, [-96, 30, -20], 'easeInOutSine'), (end, [-96, 32, -20])], 'lin')
    stretch(a, 'arm_l', [(0, 1), (line, 1.5), (come, 1.4), (round(come + 0.3, 2), 1.1), (erupt, 1.8), (round(erupt + 0.8, 2), 1.8), (stance, 1.2), (end, 1.2)])
    a.rot('torso', [(0, [-4, 6, 0]), (round(line * 0.5, 2), [-4, -6, 0]), (round(line - 0.2, 2), [-2, 4, 0]), (line, [22, 0, 0], 'easeOutBack'),
                    (come, [18, 0, 0]), (round(erupt - 0.1, 2), [16, 0, 0]), (erupt, [-26, 0, 0], 'easeOutExpo'), (round(erupt + 0.8, 2), [-22, 0, 0]),
                    (stance, [14, 0, 0], 'easeInOutSine'), (end, [14, 0, 0])], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (stance, [0, 0, 0]), (round(stance + 0.3, 2), [0, -1.6, 0], 'easeOutQuad'), (end, [0, -1.6, 0])], 'lin')
    for leg, sgn in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(stance, [0, 0, 4 * sgn]), (round(stance + 0.3, 2), [-30 * sgn, 0, 16 * sgn], 'easeOutQuad'), (end, [-30 * sgn, 0, 16 * sgn])], 'lin')
    for brow, sgn in (('brow_r', 1), ('brow_l', -1)):
        a.pos(brow, [(round(come - 0.2, 2), [0, -0.8, 0]), (come, [0, 0.9 if sgn > 0 else -0.6, 0]), (round(erupt + 0.4, 2), [0, -0.8, 0])], 'lin')
        a.rot(brow, [(round(come - 0.2, 2), [0, 0, 18 * sgn]), (come, [0, 0, -14 * sgn if sgn > 0 else 14 * sgn]), (round(erupt + 0.4, 2), [0, 0, 18 * sgn])], 'lin')
    a.sound(erupt, 'roar')
    return a


def b_d_serpientes():
    """8.0 s (BossState.P4_SERPIENTES). In the air: both arms flung up to the sky, summoning (0.1-0.6, "My serpents...
    FEAST!"); then his right arm sweeps down at you, sending Ouros (0.3-0.9) and his left Boros (1.5-2.1); arms open,
    watching them come, laughing."""
    a = god(B('d_serpientes', 8.0, 'hold'))
    a.rot('arm_r', [(0, HAM_CARRY), (0.15, [-168, 0, 40], 'easeOutBack'), (0.5, [-170, 0, 40]), (0.85, POINT_R, 'easeOutExpo'), (1.5, POINT_R),
                    (2.2, [-60, 0, 70], 'easeInOutSine'), (8.0, [-64, 0, 72])], 'lin')
    stretch(a, 'arm_r', [(0, 1.1), (0.5, 1.8), (0.85, 1.8), (1.5, 1.8), (2.2, 1.3), (8.0, 1.3)])
    a.rot('arm_l', [(0, [-40, 0, -40]), (0.15, [-168, 0, -40], 'easeOutBack'), (1.5, [-170, 0, -40]), (2.0, [-95, 6, -4], 'easeOutExpo'), (2.6, [-95, 6, -4]),
                    (3.2, [-60, 0, -70], 'easeInOutSine'), (8.0, [-64, 0, -72])], 'lin')
    stretch(a, 'arm_l', [(0, 1), (0.15, 1.8), (2.0, 1.8), (2.6, 1.8), (3.2, 1.3), (8.0, 1.3)])
    a.rot('torso', [(0, [-6, 0, 0]), (0.3, [-28, 0, 0], 'easeOutBack'), (0.85, [12, -10, 0], 'easeOutExpo'), (2.0, [12, 10, 0], 'easeOutExpo'),
                    (3.2, [-18, 0, 0], 'easeInOutSine'), (8.0, [-16, 0, 0])], 'lin')
    flying_legs(a, 8.0, 22)
    return a


def b_d_flechas():
    """6.5 s (BossState.P4_FLECHAS). Hovering with the bow: three arrows up into the sky (draws from 0.4, 1.6, 2.8; looses at
    0.7, 1.9, 3.1), then three straight at you, fast (4.3, 4.6, 4.9 off one long draw)."""
    a = god(B('d_flechas', 6.5, 'hold'))
    up_l = [-150, 0, -10]
    at_l = [-92, 0, -6]
    a.rot('arm_l', [(0, [-24, 0, -30]), (0.35, up_l, 'easeOutBack'), (3.3, up_l), (3.8, at_l, 'easeInOutSine'), (5.2, at_l), (6.5, [-40, 0, -40], 'easeInOutSine')], 'lin')
    stretch(a, 'arm_l', [(0, 1), (0.35, 1.35), (5.2, 1.35), (6.5, 1)])
    keys = [(0, [-24, 0, 30])]
    drawn, arrow = [], []
    for d, l in ((0.4, 0.7), (1.6, 1.9), (2.8, 3.1)):
        keys += [(d, NOCK_R, 'easeOutQuad'), (round(l - 0.05, 2), [-152, 30, 44], 'easeInOutSine'), (round(l + 0.1, 2), [-110, 50, 90], 'easeOutExpo')]
        drawn += [(round(d + 0.1, 2), True), (l, False)]
        arrow += [(d, True), (l, False)]
    for d, l in ((4.1, 4.3), (4.4, 4.6), (4.7, 4.9)):
        keys += [(d, [-100, -50, 4], 'easeOutQuad'), (round(l - 0.03, 2), [-96, 28, 40], 'easeInOutSine'), (round(l + 0.08, 2), [-70, 50, 80], 'easeOutExpo')]
        drawn += [(round(d + 0.05, 2), True), (l, False)]
        arrow += [(d, True), (l, False)]
    keys += [(6.5, [-30, 0, 30], 'easeInOutSine')]
    a.rot('arm_r', keys, 'lin')
    a.vis('bow_drawn', False, drawn)
    a.vis('aztec_arrow', False, arrow)
    a.rot('torso', [(0, [-6, 0, 0]), (0.35, [-22, -12, 0]), (3.3, [-22, -12, 0]), (3.8, [0, -16, 0], 'easeInOutSine'), (5.2, [0, -16, 0]), (6.5, [-6, 0, 0])], 'lin')
    flying_legs(a, 6.5, 22)
    for t in (0.7, 1.9, 3.1, 4.3, 4.6, 4.9):
        a.sound(t, 'whoosh')
    return a


def b_d_fly():
    """1.0 s loop. El Rey de los Muertos in the air between moves: upright, the hammer up at his shoulder, the left fist low
    and ready, legs trailing, rising and falling on each wingbeat; a little swagger in the hips (the fiesta never stops)."""
    a = god(B('d_fly', 1.0, True))
    a.pos('root', [(0, [0, 0, 0]), (0.4, [0, 1.8, 0]), (1.0, [0, 0, 0])])
    a.rot('torso', [(0, [-6, 6, 0]), (0.5, [-10, -6, 0]), (1.0, [-6, 6, 0])])
    a.rot('arm_r', [(0, HAM_CARRY), (0.4, [-156, 8, 44]), (1.0, HAM_CARRY)])
    stretch(a, 'arm_r', [(0, 1.1)])
    a.rot('arm_l', [(0, [-28, 0, -30]), (0.4, [-36, 0, -36]), (1.0, [-28, 0, -30])])
    flying_legs(a, 1.0)
    return a


def b_d_martillo():
    """3.2 s (BossState.P4_MARTILLO, 64 t). Gone in a flash (0.3) and there beside you, already swinging: the forehand
    (0.5), the backhand (1.0) with the body twisting into each; up (1.4), the hammer wound back over his head at the top;
    down (2.0) and the hammer driven into the floor (2.2); the recovery."""
    a = god(B('d_martillo', 3.2, 'hold'))
    hammer_arm(a, [(0, HAM_CARRY), (0.3, [-100, 60, 60], 'easeInOutSine'), (0.42, [-104, 70, 60]), (0.5, [-92, -60, 10], 'easeOutExpo'),
                   (0.8, [-96, -72, 6]), (0.92, [-100, -76, 4]), (1.0, [-92, 66, 60], 'easeOutExpo'), (1.25, [-110, 50, 50]),
                   (1.6, HAM_BACK, 'easeOutQuad'), (2.0, HAM_BACK), (2.2, HAM_SLAM, 'easeInExpo'), (2.6, HAM_SLAM), (3.2, HAM_CARRY, 'easeInOutSine')],
               [(0, 1.1), (0.42, 1.3), (0.5, 1.8, 'easeOutExpo'), (0.92, 1.4), (1.0, 1.8, 'easeOutExpo'), (1.6, 1.3), (2.2, 1.6), (2.6, 1.6), (3.2, 1.1)])
    a.rot('arm_l', [(0, [-28, 0, -30]), (0.42, [-60, 30, -50]), (0.5, [-40, 0, -80], 'easeOutExpo'), (0.92, [-60, -20, -40]),
                    (1.0, [-90, 40, -20], 'easeOutExpo'), (1.6, [-200, 0, -10], 'easeOutQuad'), (2.0, [-200, 0, -10]),
                    (2.2, [-60, 0, -6], 'easeInExpo'), (2.6, [-60, 0, -6]), (3.2, [-28, 0, -30], 'easeInOutSine')], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.42, [6, 40, 0]), (0.5, [10, -40, 0], 'easeOutExpo'), (0.92, [10, -44, 0]), (1.0, [10, 46, 0], 'easeOutExpo'),
                    (1.25, [0, 20, 0]), (1.6, [-24, 0, 0], 'easeOutQuad'), (2.0, [-26, 0, 0]), (2.2, [42, 0, 0], 'easeInExpo'), (2.6, [38, 0, 0]),
                    (3.2, [0, 0, 0], 'easeInOutSine')], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (0.25, [0, -2, 0]), (0.35, [0, 0, 0]), (1.3, [0, -2.4, 0], 'easeOutQuad'), (1.45, [0, 0, 0]),
                   (2.2, [0, -3, 0], 'easeInExpo'), (2.6, [0, -2.8, 0]), (3.2, [0, 0, 0], 'easeInOutSine')], 'lin')
    for leg, sgn in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [0, 0, 5 * sgn]), (0.5, [-20 if sgn > 0 else 14, 0, 8 * sgn]), (1.0, [14 if sgn > 0 else -20, 0, 8 * sgn]),
                    (1.3, [-30, 0, 10 * sgn]), (1.6, [20, 0, 6 * sgn]), (2.2, [-40 if sgn > 0 else 30, 0, 12 * sgn], 'easeInExpo'),
                    (2.6, [-40 if sgn > 0 else 30, 0, 12 * sgn]), (3.2, [0, 0, 5 * sgn])], 'lin')
    a.scale('body', [(0, [1.04, 1.03, 1.04]), (2.2, [1.2, 0.86, 1.2], 'easeInExpo'), (2.5, [1.04, 1.03, 1.04])], 'lin')
    a.sound(0.4, 'swing').sound(0.9, 'swing').sound(1.4, 'whoosh').sound(2.2, 'slam')
    return a


def b_d_gigante():
    """1.2 s loop (BossState.P4_GIGANTE, AI-held). Perched in the air at his end of the arena, playing the spirit guitar
    for El Gigante (his hands are client/BossModel.shred): leaning back, knees bouncing, headbanging on the beat, the hips
    swinging; the wings beat on (the form controller)."""
    a = god(B('d_gigante', 1.2, True))
    guitar_slung(a)
    st = gp('strum')
    a.rot('arm_r', [(0, st[:3])], 'lin')
    stretch(a, 'arm_r', [(0, st[3])])
    a.rot('torso', [(0, [-18, 8, 0]), (0.3, [4, 0, 0], 'easeInQuad'), (0.6, [-18, -8, 0]), (0.9, [4, 0, 0], 'easeInQuad'), (1.2, [-18, 8, 0])])
    a.pos('root', [(0, [0, 0, 0]), (0.3, [0, -1.0, 0]), (0.6, [0, 0, 0]), (0.9, [0, -1.0, 0]), (1.2, [0, 0, 0])])
    for leg, s in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [-30 if s > 0 else 20, 0, 14 * s]), (0.6, [20 if s > 0 else -30, 0, 14 * s]), (1.2, [-30 if s > 0 else 20, 0, 14 * s])])
    return a


def b_d_grieta():
    """10.0 s (BossState.P4_GRIETA, 200 t). Down in front of you like a stone, fists over his head (0-0.7); both fists
    smashed into the floor (0.7); onto his knees, both hands in the crack, prying (1.0-2.2), straining harder; and he
    tears it open (2.2), arms wrenched apart, roaring; he holds it open on his knees, arms wide, shaking with the strain,
    glaring at you (to 8.9); "¡Ciérrate!" (8.9) and both palms clapped together (9.3): shut; up (to 10)."""
    a = god(B('d_grieta', 10.0, 'hold'))
    a.pos('root', [(0, [0, 2, 0]), (0.6, [0, 0, 0]), (0.7, [0, -4.4, 0], 'easeInQuad'), (9.4, [0, -4.4, 0]), (10.0, [0, 0, 0], 'easeInOutSine')], 'lin')
    a.rot('leg_r', [(0, [10, 0, 6]), (0.7, [-84, 0, 10], 'easeInQuad'), (9.4, [-84, 0, 10]), (10.0, [0, 0, 6], 'easeInOutSine')], 'lin')
    a.rot('leg_l', [(0, [10, 0, -6]), (0.7, [-84, 0, -10], 'easeInQuad'), (9.4, [-84, 0, -10]), (10.0, [0, 0, -6], 'easeInOutSine')], 'lin')
    pry_r, pry_l = [-46, -30, 6], [-46, 30, -6]
    wide_r, wide_l = [-40, 0, 80], [-40, 0, -80]
    strain = lambda base, sgn: [(round(2.6 + 0.12 * i, 2), [base[0] + (3 if i % 2 else -3), base[1], base[2] + (4 if i % 2 else -4) * sgn])
                                for i in range(int((8.7 - 2.6) / 0.12))]
    for arm, sgn, pry, wide in (('arm_r', 1, pry_r, wide_r), ('arm_l', -1, pry_l, wide_l)):
        keys = [(0, [-176, 0, 10 * sgn]), (0.6, [-180, 0, 6 * sgn]), (0.7, [-50, 0, 6 * sgn], 'easeInExpo'), (1.0, pry, 'easeOutQuad')]
        keys += [(round(1.0 + 0.2 * i, 2), [pry[0] + (4 if i % 2 else -4), pry[1] + (6 if i % 2 else 0) * sgn, pry[2]]) for i in range(6)]
        keys += [(2.1, [pry[0], pry[1] - 10 * sgn, pry[2]]), (2.3, wide, 'easeOutExpo')] + strain(wide, sgn)
        keys += [(8.9, wide), (9.25, [-80, -40 * sgn, 0], 'easeInExpo'), (9.6, [-82, -42 * sgn, 0]), (10.0, [-28, 0, 26 * sgn], 'easeInOutSine')]
        a.rot(arm, keys, 'lin')
        stretch(a, arm, [(0, 1.4), (0.7, 1.6), (1.0, 1.6), (2.3, 1.7), (8.9, 1.7), (9.25, 1.5), (10.0, 1)])
    a.rot('torso', [(0, [0, 0, 0]), (0.7, [40, 0, 0], 'easeInQuad'), (1.0, [44, 0, 0]), (2.1, [48, 0, 0]), (2.3, [-20, 0, 0], 'easeOutExpo'),
                    (3.0, [-12, 0, 0]), (8.9, [-14, 0, 0]), (9.25, [24, 0, 0], 'easeInExpo'), (9.6, [20, 0, 0]), (10.0, [0, 0, 0], 'easeInOutSine')], 'lin')
    a.scale('body', [(0, [1.04, 1.03, 1.04]), (0.7, [1.22, 0.84, 1.22], 'easeInQuad'), (1.0, [1.04, 1.03, 1.04]), (2.1, [1.0, 0.97, 1.0]),
                     (2.3, [1.12, 1.1, 1.12], 'easeOutExpo'), (3.0, [1.06, 1.05, 1.06]), (9.25, [1.12, 0.94, 1.12], 'easeInExpo'),
                     (9.6, [1.04, 1.03, 1.04]), (10.0, [1.04, 1.03, 1.04])], 'lin')
    a.sound(0.7, 'slam').sound(1.2, 'grunt').sound(1.8, 'grunt').sound(2.2, 'roar').sound(9.3, 'slam')
    return a


def b_d_petalos():
    """1.0 s loop (BossState.P4_PETALOS, AI-held). The spin (the entity turns 24° a tick): a dancer's whirl over the floor,
    the hammer flung out at arm's length, the left arm up over his head, leaning out into it, the legs tucked."""
    a = god(B('d_petalos', 1.0, True))
    a.rot('arm_r', [(0, [-100, 0, 96]), (0.5, [-96, 0, 100]), (1.0, [-100, 0, 96])])
    stretch(a, 'arm_r', [(0, 1.6)])
    a.rot('arm_l', [(0, [-176, 0, -20]), (0.5, [-170, 0, -30]), (1.0, [-176, 0, -20])])
    stretch(a, 'arm_l', [(0, 1.3)])
    a.rot('root', [(0, [0, 0, 10]), (0.5, [0, 0, 14]), (1.0, [0, 0, 10])])
    a.rot('torso', [(0, [-8, 0, 0]), (0.5, [-12, 0, 0]), (1.0, [-8, 0, 0])])
    a.rot('leg_r', [(0, [-40, 0, 10])], 'lin')
    a.rot('leg_l', [(0, [20, 0, -20])], 'lin')
    a.sound(0, 'whoosh')
    return a


CANCION_BEAT = 0.47      # Cancion del Mariachi, about 127.5 bpm (the band plays it in phase four)


def b_d_conduct():
    """4 beats loop (BossState.P4_PAPEL, P4_VELAS). Conducting the dead's fiesta with the hammer: it beats time on every
    beat (down, out, up), the left hand flung out at the floor on each downbeat, the hips swaying to it."""
    L = round(CANCION_BEAT * 4, 2)
    a = god(B('d_conduct', L, True))
    r = [(0, [-150, 0, 30])]
    l = [(0, [-40, 0, -40])]
    tor = [(0, [-6, 8, 0])]
    for i in range(4):
        t = round(CANCION_BEAT * i, 2)
        r += [(t, [-176, 0, 20] if i % 2 == 0 else [-120, 20, 70], 'easeInOutSine')]
        if i % 2 == 0:
            l += [(t, [-100, 20, -60], 'easeOutBack'), (round(t + CANCION_BEAT * 0.6, 2), [-50, 0, -50])]
        tor += [(t, [-6, 10 if i % 2 == 0 else -10, 0], 'easeInOutSine')]
    r.append((L, [-150, 0, 30]))
    l.append((L, [-40, 0, -40]))
    tor.append((L, [-6, 8, 0]))
    a.rot('arm_r', r, 'lin')
    a.rot('arm_l', l, 'lin')
    a.rot('torso', tor, 'lin')
    stretch(a, 'arm_r', [(0, 1.3)])
    stretch(a, 'arm_l', [(0, 1.4)])
    flying_legs(a, L, 14)
    return a


def b_d_danza():
    """2 beats loop (BossState.P4_DANZA). Over the ring of dancers he dances too: zapateado in the air, the left hand
    clapping against the hammer's haft on the off-beat, the hips and shoulders rolling."""
    L = round(CANCION_BEAT * 2, 2)
    h = round(L / 2, 2)
    a = god(B('d_danza', L, True))
    a.rot('arm_r', [(0, [-140, -30, 20]), (h, [-130, -40, 10]), (L, [-140, -30, 20])])
    stretch(a, 'arm_r', [(0, 1.2)])
    a.rot('arm_l', [(0, [-60, 20, -60]), (round(h - 0.08, 2), [-120, 50, -20], 'easeInQuad'), (h, [-128, 56, -14]), (L, [-60, 20, -60])])
    stretch(a, 'arm_l', [(0, 1.2), (h, 1.5), (L, 1.2)])
    a.rot('torso', [(0, [-6, 14, 4]), (h, [-6, -14, -4]), (L, [-6, 14, 4])])
    a.pos('root', [(0, [0, 0, 0]), (round(L / 4, 2), [0, 1.2, 0]), (h, [0, 0, 0]), (round(L * 0.75, 2), [0, 1.2, 0]), (L, [0, 0, 0])])
    a.rot('leg_r', [(0, [-40, 0, 6]), (h, [20, 0, 6]), (L, [-40, 0, 6])])
    a.rot('leg_l', [(0, [20, 0, -6]), (h, [-40, 0, -6]), (L, [20, 0, -6])])
    a.sound(h, 'snap')
    return a


def b_d_luna():
    """1.3 s loop (BossState.P4_LUNA, a volley every 26 t). Calling the moon down: the hammer and the left fist thrust up at it
    on each volley, the body arched back, then a breath."""
    a = god(B('d_luna', 1.3, True))
    a.rot('arm_r', [(0, [-150, 0, 30]), (0.15, [-178, 0, 10], 'easeOutExpo'), (0.7, [-174, 0, 14]), (1.3, [-150, 0, 30], 'easeInOutSine')], 'lin')
    a.rot('arm_l', [(0, [-140, 0, -40]), (0.15, [-176, 0, -14], 'easeOutExpo'), (0.7, [-170, 0, -18]), (1.3, [-140, 0, -40], 'easeInOutSine')], 'lin')
    stretch(a, 'arm_r', [(0, 1.2), (0.15, 1.7, 'easeOutExpo'), (0.7, 1.6), (1.3, 1.2)])
    stretch(a, 'arm_l', [(0, 1.2), (0.15, 1.7, 'easeOutExpo'), (0.7, 1.6), (1.3, 1.2)])
    a.rot('torso', [(0, [-14, 0, 0]), (0.15, [-30, 0, 0], 'easeOutExpo'), (0.7, [-28, 0, 0]), (1.3, [-14, 0, 0], 'easeInOutSine')], 'lin')
    a.scale('body', [(0, [1.04, 1.03, 1.04]), (0.15, [0.98, 1.1, 0.98], 'easeOutExpo'), (0.6, [1.05, 1.04, 1.05]), (1.3, [1.04, 1.03, 1.04])], 'lin')
    flying_legs(a, 1.3, 16)
    return a


PHASE_FOUR = [b_p4_fall, b_p4_riseup, b_p4_ignis, b_p4_prophecy, b_p4_brethren, b_p4_souls, b_p4_halfbaked, b_p4_bow, b_p4_watch,
              b_p4_talking, b_p4_walk, b_p4_honor, b_p4_muchachas, b_p4_goodtime, b_d_serpientes, b_d_flechas,
              b_d_fly, b_d_martillo, b_d_gigante, b_d_grieta, b_d_petalos, b_d_conduct, b_d_danza, b_d_luna]


ASCENDED_MOVES = [b_a_fly, b_a_combo, b_a_flash, b_a_comet, b_a_comet_land, b_a_laser, b_a_solar, b_a_conduct, b_a_duel_fly, b_a_shred,
                  b_a_duel_smash, b_a_duel_shame, b_a_pinata, b_a_braziers, b_a_nova, b_a_spent, b_qte_hold_crush, b_qte_fail_crush,
                  b_a_sombrero, b_a_volcan, b_a_calaveras, b_a_pinata_swing]


PHASE_THREE = [b_p3_knockback, b_p3_glare, b_p3_glare_hold, b_p3_stalk, b_p3_snatch, b_p3_carry, b_p3_carry_hold, b_p3_blackout,
               b_p3_serenade, b_p3_verdict, b_p3_toss, b_p3_dance, b_p3_felt, b_p3_crush, b_p3_showcar, b_p3_go, b_p3_ascend, b_p3_power,
               b_winded]


BOSS = [b_leap_out, b_chug, b_roar, b_idle, b_walk, b_walk_keys, b_run, b_swipe, b_slam, b_breath, b_throw,
        b_drink_handle, b_enter_car, b_drive, b_exit_stumble, b_phase, b_death,
        b_swipe_overhead, b_leap, b_leap_land, b_charge_windup, b_charge, b_stagger, b_uppercut, b_air_spike,
        b_blink, b_gatling, b_dash_combo, b_van_kick, b_p2_kneel, b_p2_talk, b_p2_scream, b_p2_arrive, b_fly,
        b_p2_barrage, b_p2_meteor, b_van_throw, b_p2_dive, b_p2_season,
        b_grab_lunge, b_grab_combo] + REWORK + COLISEUM + FORM + HARDENING + PHASE_THREE + ASCENDED_MOVES + PHASE_FOUR




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
