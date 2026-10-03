#!/usr/bin/env python3
"""EDP445 as a semi-photorealistic mesh (see mh_human.py for how it is built).

  python3 tools/gen_edp_mesh.py

Short and extremely obese: a huge apron belly that hangs forward and down over his shorts, thick
arms and legs, a round bald head with a full beard; his face from a frontal photo. Dressed as in the
2023 documentary footage: an oversized black long-sleeve sweatshirt to mid-thigh, black basketball
shorts with white side stripes, black sneakers, and purple mirrored sunglasses pushed up on his head.
Props: his phone (left hand) and a taco (right). Writes meshes/edp445.json and the edp445_mesh
texture and geo.
"""
import numpy as np

import mh_human as H
from mh_human import Box, Prop, smoothstep

WEIGHT = 2.0
SWEAT, SHORTS, STRIPE, SHOE, SOLE = (np.array(c, float) for c in
                                     ((32, 32, 35), (26, 26, 29), (214, 214, 210), (22, 22, 24), (226, 224, 218)))
NECK_HOLE = 1.1


def collar_height(x, z, J):
    return J['neck01'][0][1] - 0.1 - 0.25 * smoothstep(0.2, 1.2, z) * (1 - smoothstep(0.3, 0.9, np.abs(x)))


def masks(pos, w, J):
    """Sweatshirt (long sleeves to the wrist, hem at mid-thigh), shorts to below the knee, shoes."""
    x, y, z = pos[:, 0], pos[:, 1], pos[:, 2]
    head = sum(w[:, H.BONES.index(b)] for b in H.HEAD_BONES)
    hand = np.zeros(len(pos))
    for side in 'LR':
        wr = J['wrist.' + side][0]
        el = J['lowerarm01.' + side][0]
        axis = wr - el
        t = ((pos - el) @ axis) / (axis @ axis)
        mine = np.sign(x) == (1 if side == 'L' else -1)
        hand = np.maximum(hand, mine * smoothstep(0.93, 0.97, t) * (w[:, H.BONES.index('forearm_' + side.lower())] > 0.5))
    hip = (J['upperleg01.L'][0][1] + J['upperleg01.R'][0][1]) / 2
    knee = (J['lowerleg01.L'][0][1] + J['lowerleg01.R'][0][1]) / 2
    ankle = (J['foot.L'][0][1] + J['foot.R'][0][1]) / 2 + 0.25
    hem = hip - 0.95
    collar = collar_height(x, z, J)
    opening = 1 - smoothstep(NECK_HOLE - 0.05, NECK_HOLE + 0.05, np.hypot(x, z - J['neck01'][0][2]))
    sweat = smoothstep(hem - 0.04, hem + 0.04, y) * (1 - opening * smoothstep(collar - 0.03, collar + 0.03, y)) \
        * (1 - smoothstep(0.6, 0.8, head)) * (1 - hand)
    shoes = 1 - smoothstep(ankle - 0.03, ankle + 0.03, y)
    shorts = (1 - sweat) * smoothstep(knee - 0.55, knee - 0.47, y) * (y < hem + 0.1) * (1 - hand)
    return sweat, shorts, shoes, dict(hem=hem, knee=knee, ankle=ankle, hip=hip, collar=collar)


# ---------------------------------------------------------------- props
def solid(rgb):
    return lambda face, u, v: np.ones(u.shape + (3,)) * rgb


def phone_body(face, u, v):
    """Black phone, screen lit on the front face (the side toward his face when he looks at it)."""
    c = np.ones(u.shape + (3,)) * [24, 24, 28]
    if face == 'south':
        scr = (u > 0.12) & (u < u.max() - 0.12) & (v > 0.25) & (v < v.max() - 0.2)
        glow = np.array([90, 150, 230.0]) * (0.85 + 0.3 * (v / v.max()))[..., None]
        c = np.where(scr[..., None], glow, c)
    return c


def lens(face, u, v):
    """Purple-blue mirrored lens: a vertical gradient with a bright streak."""
    t = (v / max(v.max(), 1e-6))[..., None]
    c = np.array([120, 70, 180.0]) * (1 - t) + np.array([40, 60, 150.0]) * t
    streak = np.abs(u - v * 0.8 - 0.3) < 0.18
    return np.where(streak[..., None], c * 0.5 + 120, c)


def taco_shell(face, u, v):
    rng = np.random.default_rng(int(u.size + v.size))
    shell = np.array([222, 176, 92.0]) * (0.9 + 0.2 * rng.random(u.shape))[..., None]
    if face == 'up':
        kk = rng.random(u.shape)[..., None]
        fill = np.select([kk < 0.35, kk < 0.6, kk < 0.82], [np.array([104, 168, 58.0]), np.array([118, 70, 40.0]),
                                                           np.array([238, 196, 70.0])], np.array([196, 52, 42.0]))
        rim = (u < 0.25) | (u > u.max() - 0.25)
        return np.where(rim[..., None], shell, fill)
    if face in ('north', 'south'):
        inside = (np.abs(u - u.max() / 2) < u.max() * 0.3) & (v < v.max() * 0.55)
        return np.where(inside[..., None], np.array([118, 70, 40.0]), shell)
    return shell


def head_band(B, below_top):
    """Head vertices in a band `below_top` decimetres under the crown."""
    V, bv = B['V'], B['body_verts']
    head = B['w'][:, H.BONES.index('head')]
    top = V[bv, 1].max()
    return bv[(head[bv] > 0.9) & (np.abs(V[bv, 1] - (top - below_top)) < 0.06)]


def shades_anchor(B):
    """The front of his scalp just under the crown, where pushed-up sunglasses sit (rig pixels)."""
    band = head_band(B, 0.32)
    band = band[np.abs(B['V'][band, 0]) < 0.12]
    return EDP.to_rig(B['V'][band[np.argmax(B['V'][band, 2])]], B)


def shades_boxes(B):
    """Sunglasses at real size (~14 cm across): lenses tilted back onto the scalp, arms along the sides."""
    band = head_band(B, 0.5)
    half = np.abs(B['V'][band, 0]).max() * B['scale']            # head half-width at the arms' height
    frame = solid([18, 18, 20])
    tilt, o = [-68, 0, 0], [0, 0, 0]
    return [
        Box([-1.42, 0.0, -0.16], [1.22, 0.82, 0.12], lens, tilt, o),
        Box([0.2, 0.0, -0.16], [1.22, 0.82, 0.12], lens, tilt, o),
        Box([-1.5, 0.8, -0.18], [3.0, 0.14, 0.16], frame, tilt, o),      # brow bar
        Box([-0.22, 0.45, -0.18], [0.44, 0.14, 0.16], frame, tilt, o),   # bridge
        Box([-half - 0.12, -0.25, 0.2], [0.12, 0.14, 3.0], frame, [-12, 0, 0], o),
        Box([half, -0.25, 0.2], [0.12, 0.14, 3.0], frame, [-12, 0, 0], o),
    ]


def edp_props():
    return [
        Prop('shades', 'head', shades_anchor, shades_boxes),
        Prop('phone', 'forearm_l', lambda B: EDP.hand(B, 'L'), [Box([-0.7, -1.2, -1.6], [1.4, 2.8, 0.35], phone_body)]),
        Prop('taco', 'forearm_r', lambda B: EDP.hand(B, 'R'), [Box([-0.8, -2.4, -4.4], [1.6, 2.4, 4.0], taco_shell)]),
    ]


class Edp(H.Character):
    name = 'edp445'
    head_top_px = 31.0
    targets = [
        ('macrodetails/african-male-young', 1.0),
        ('macrodetails/universal-male-young-averagemuscle-maxweight', WEIGHT),
        ('macrodetails/height/male-young-averagemuscle-maxweight-minheight', 0.6),
        ('stomach/stomach-pregnant-incr', 1.0), ('neck/neck-double-incr', 1.0), ('head/head-fat-incr', 0.6),
        ('head/head-round', 0.5), ('neck/neck-scale-horiz-incr', 1.0), ('neck/neck-scale-depth-incr', 1.0),
        ('torso/torso-scale-depth-incr', 0.8), ('torso/torso-scale-horiz-incr', 0.6),
        ('armslegs/l-upperarm-fat-incr', 1.0), ('armslegs/r-upperarm-fat-incr', 1.0),
        ('armslegs/l-lowerarm-fat-incr', 0.8), ('armslegs/r-lowerarm-fat-incr', 0.8),
        ('armslegs/l-upperleg-fat-incr', 1.0), ('armslegs/r-upperleg-fat-incr', 1.0),
        ('armslegs/l-lowerleg-fat-incr', 1.0), ('armslegs/r-lowerleg-fat-incr', 1.0),
        # his face: head height, width, forehead, chin, eye spacing and mouth height fitted to the
    # proportions averaged over four frontal photos (relative to eye-to-mouth distance:
    # top 1.36, chin 1.58, width at the eyes 1.73, eye spacing 0.86); a broad round dome, heavy cheeks and jowls, broad nose, full lips
        ('head/head-scale-horiz-decr', 0.5), ('head/head-scale-vert-incr', 0.7), ('forehead/forehead-scale-vert-decr', 0.6),
        ('chin/chin-width-incr', 0.5), ('chin/chin-height-incr', 0.35), ('chin/chin-jaw-drop-incr', 0.8),
        ('chin/chin-prominent-incr', 0.4), ('mouth/mouth-trans-down', 0.5),
        ('cheek/l-cheek-volume-incr', 0.5), ('cheek/r-cheek-volume-incr', 0.5),
        ('nose/nose-scale-horiz-incr', 0.8), ('nose/nose-flaring-incr', 0.8), ('nose/nose-nostrils-width-incr', 0.7),
        ('nose/nose-volume-incr', 0.6), ('mouth/mouth-scale-horiz-incr', 0.5),
        ('mouth/mouth-lowerlip-volume-incr', 1.0), ('mouth/mouth-upperlip-volume-incr', 0.6),
        ('eyes/l-eye-trans-out', 1.5), ('eyes/r-eye-trans-out', 1.5),
        ('eyes/l-eye-height1-incr', 0.5), ('eyes/r-eye-height1-incr', 0.5),     # his wide-open stare
        ('eyes/l-eye-height2-incr', 0.6), ('eyes/r-eye-height2-incr', 0.6),
    ]
    fat = {'torso': 0.85, 'uarm': 0.45, 'larm': 0.22, 'uleg': 0.45, 'lleg': 0.25, 'neck': 0.65}
    # the belly: a big forward dome, and the apron below it hanging over his shorts
    bellies = [(0.47, 0.9, (3.6, 2.7), 1.05, 0.2), (0.2, 1.3, (3.0, 1.2), 0.35, 0.35)]
    belly_smooth = 0.8
    arm_hang_deg = 20.0
    trousers = (0.2, 0.12)      # baggy basketball shorts
    # Two frontal phone selfies: his wide-eyed stare (detail) and a neutral one (tone and shading
    # averaged with it). Both tilted a few degrees; even daylight.
    photos = [
        H.Photo('photo.jpg', 'https://i.redd.it/tn5ttuws5py51.jpg', [
            ('r_eye', (180.0, 372.0)), ('l_eye', (398.0, 340.0)),
            (5054, (285.0, 510.0)),                              # under the nose
            (5057, (240.0, 500.0)), (11674, (330.0, 495.0)),     # nostrils
            (411, (205.0, 615.0)), (7171, (400.0, 605.0)),       # mouth corners
            (466, (295.0, 625.0)),                               # mouth
            (5162, (320.0, 830.0)),                              # chin
            (220, (295.0, 290.0)),                               # between the brows
            (881, (330.0, 28.0)),                                # top of the head
            (873, (115.0, 372.0)), (7578, (575.0, 345.0)),       # sides of the face at the eyes
            (('side', 7.25, -1), (122.0, 180.0)), (('side', 7.25, 1), (558.0, 170.0)),   # temples
            (('side', 6.04, -1), (125.0, 615.0)), (('side', 6.04, 1), (585.0, 600.0)),   # cheeks
        ], (345.0, 430.0, 250.0, 420.0), 1.0),
        H.Photo('photo2.jpg', 'https://i.redd.it/zvgthayh3c151.jpg', [
            ('r_eye', (692.0, 348.0)), ('l_eye', (872.0, 372.0)),
            (5054, (752.0, 492.0)), (5057, (726.0, 488.0)), (11674, (782.0, 492.0)),
            (411, (702.0, 567.0)), (7171, (830.0, 577.0)), (466, (765.0, 575.0)),
            (5162, (765.0, 690.0)), (220, (775.0, 318.0)), (881, (760.0, 48.0)),
            (873, (560.0, 360.0)), (7578, (925.0, 362.0)),
            (('side', 7.25, -1), (568.0, 200.0)), (('side', 7.25, 1), (952.0, 200.0)),
            (('side', 6.04, -1), (575.0, 575.0)), (('side', 6.04, 1), (925.0, 578.0)),
        ], (760.0, 380.0, 200.0, 340.0), 1.0),
    ]
    face_floor = 5.3
    skin = np.array([94, 60, 44], float)

    @property
    def props(self):
        return edp_props()

    def covered(self, pos, w, J):
        sweat, shorts, _, _ = masks(pos, w, J)
        return np.clip(sweat + shorts, 0, 1)

    def clothes(self, T):
        sweat, shorts, shoes, k = masks(T.pos, T.w, T.J)
        sw = T.cloth(SWEAT, 0.16)
        rib = smoothstep(k['collar'] - 0.12, k['collar'] - 0.08, T.y) * (T.y < k['collar']) \
            * (np.hypot(T.x, T.z - T.J['neck01'][0][2]) < NECK_HOLE + 0.05)
        hemrib = smoothstep(k['hem'] + 0.22, k['hem'] + 0.18, T.y)
        cuff = np.zeros(len(T.pos))
        for side in 'LR':
            t = T.along(side, 'lowerarm01', 'wrist')
            cuff = np.maximum(cuff, smoothstep(0.78, 0.82, t) * (T.bone('forearm_' + side.lower()) > 0.5))
        sw = sw * (1 + 0.25 * np.clip(rib + hemrib + cuff, 0, 1))[:, None]
        sh = T.cloth(SHORTS, 0.12)
        # white stripes down the outside of each leg, and a white hem
        stripe = np.zeros(len(T.pos))
        for side, sign in (('L', 1), ('R', -1)):
            leg = T.bone('leg_' + side.lower())
            centre = T.J['upperleg01.' + side][0]
            ang = np.arctan2(T.z - centre[2], (T.x - centre[0]) * sign)     # 0 = straight out to the side
            stripe = np.maximum(stripe, (leg + T.bone('shin_' + side.lower()) > 0.5) * (np.abs(ang) < 0.16))
        stripe = np.maximum(stripe, smoothstep(k['knee'] - 0.4, k['knee'] - 0.44, T.y) * 1.0)
        sh = sh + (STRIPE * (0.6 + 0.4 * T.ao)[:, None] - sh) * stripe[:, None]
        sole = T.y < (k['ankle'] - 0.62)
        shoe = np.where(sole[:, None], SOLE, SHOE) * (0.6 + 0.5 * T.ao)[:, None]
        return [(shorts, sh), (sweat, sw), (shoes, shoe)]


EDP = Edp()

if __name__ == '__main__':
    EDP.generate()
