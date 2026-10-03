#!/usr/bin/env python3
"""Badlands Chugs as a semi-photorealistic mesh (see mh_human.py for how it is built).

  python3 tools/gen_chugs_mesh.py

Big, heavy build with a round bald head; his face from a frontal photo, with his mustache and goatee
strengthened; his black BADLANDS / CHUGS tee, black pants and shoes; a gallon jug of lemonade in his
left hand and a taco for his right. Writes meshes/badlands_chugs.json and the badlands_chugs_mesh
texture and geo.

The jug and taco keep the size and placement his animation poses were solved for (gen_npc_anims.py
JUG_*, TACO_*): change them and re-solve those poses.
"""
import numpy as np

import mh_human as H
from mh_human import Box, Prop, smoothstep

OLD = 0.2               # MakeHuman age mix: ~38 years (older reads gaunt on him)
WEIGHT = 1.8            # past MakeHuman's max-weight target

TEE, PANTS, SHOE, SOLE = (np.array(c, float) for c in ((30, 30, 33), (36, 37, 42), (24, 24, 26), (70, 68, 66)))
NECK_HOLE = 1.05        # radius of the tee's neck hole around the neck axis, decimetres
# his facial hair in the photo (pixels): (cx, cy, rx, ry) ellipses for the mustache, the chin beard and
# the thin strips joining them round the mouth
BEARD = [(683, 352, 50, 13), (683, 470, 52, 24), (627, 418, 12, 42), (739, 418, 12, 42)]
MOUTH = (682, 388, 42, 27)


def collar_height(x, z, J):
    """Height of the crew neck's edge: at the neck base, dipping at the front."""
    return J['neck01'][0][1] - 0.12 - 0.3 * smoothstep(0.2, 1.2, z) * (1 - smoothstep(0.3, 0.9, np.abs(x)))


def tee_masks(pos, w, J):
    x, y, z = pos[:, 0], pos[:, 1], pos[:, 2]
    arm = sum(w[:, H.BONES.index(b)] for b in ('arm_r', 'arm_l', 'forearm_r', 'forearm_l'))
    head = sum(w[:, H.BONES.index(b)] for b in H.HEAD_BONES)
    ankle = (J['foot.L'][0][1] + J['foot.R'][0][1]) / 2 + 0.25
    hem = (J['upperleg01.L'][0][1] + J['upperleg01.R'][0][1]) / 2 + 0.55
    collar = collar_height(x, z, J)
    opening = 1 - smoothstep(NECK_HOLE - 0.05, NECK_HOLE + 0.05, np.hypot(x, z - J['neck01'][0][2]))
    sleeve = np.zeros(len(pos))
    for side in ('L', 'R'):
        sh, el = J['upperarm01.' + side][0], J['lowerarm01.' + side][0]
        axis = el - sh
        t = ((pos - sh) @ axis) / (axis @ axis)
        mine = (np.sign(x) == (1 if side == 'L' else -1))
        sleeve = np.maximum(sleeve, mine * (1 - smoothstep(0.55, 0.62, t)))
    tee = np.clip(np.clip(1 - arm, 0, 1) + arm * sleeve, 0, 1) * smoothstep(hem - 0.04, hem + 0.04, y) \
        * (1 - opening * smoothstep(collar - 0.03, collar + 0.03, y)) * (1 - smoothstep(0.6, 0.8, head))
    shoes = 1 - smoothstep(ankle - 0.03, ankle + 0.03, y)
    pants = (1 - smoothstep(hem - 0.04, hem + 0.04, y)) * np.clip(1 - arm, 0, 1) * (1 - shoes)
    return tee, pants, shoes, ankle


# ---------------------------------------------------------------- props
def jug_body(face, u, v):
    """Gallon jug of lemonade: cream plastic, liquid line a quarter down, a white label band."""
    h = 5.6
    if face in ('up', 'down'):
        return np.full(u.shape + (3,), 1.0) * ([236, 228, 196] if face == 'up' else [226, 200, 92])
    liquid = v > h * 0.24
    c = np.where(liquid[..., None], [240, 212, 86], [232, 230, 220])
    label = (v > h * 0.45) & (v < h * 0.7)
    c = np.where(label[..., None], [246, 244, 238], c)
    stripe = label & (np.abs(v - h * 0.575) < 0.18)
    c = np.where(stripe[..., None], [200, 40, 44], c)
    edge = (u < 0.25) | (u > u.max() - 0.25)
    return c * np.where(edge, 0.88, 1.0)[..., None]


def solid(rgb, ridge=False):
    def paint(face, u, v):
        c = np.ones(u.shape + (3,)) * rgb
        if ridge and face not in ('up', 'down'):
            c *= np.where((u * 4).astype(int) % 2 == 0, 1.0, 0.86)[..., None]
        return c
    return paint


def taco_shell(face, u, v):
    """Hard shell: golden with toasted speckles; the open top shows lettuce, beef, cheese, tomato."""
    rng = np.random.default_rng(int(u.size + v.size))
    shell = np.array([222, 176, 92.0]) * (0.9 + 0.2 * rng.random(u.shape))[..., None]
    if face == 'up':
        k = rng.random(u.shape)
        kk = k[..., None]
        fill = np.select([kk < 0.35, kk < 0.6, kk < 0.82], [np.array([104, 168, 58.0]), np.array([118, 70, 40.0]),
                                                           np.array([238, 196, 70.0])], np.array([196, 52, 42.0]))
        rim = (u < 0.25) | (u > u.max() - 0.25)
        return np.where(rim[..., None], shell, fill)
    if face in ('north', 'south'):      # the ends: a U of shell round the fillings
        inside = (np.abs(u - u.max() / 2) < u.max() * 0.3) & (v < v.max() * 0.55)
        return np.where(inside[..., None], [118, 70, 40], shell)
    return shell


def chugs_props():
    hand_l = lambda B: CH.hand(B, 'L')
    hand_r = lambda B: CH.hand(B, 'R')
    return [
        Prop('jug', 'forearm_l', hand_l, [
            Box([-2.0, -4.0, -4.4], [4.0, 5.6, 4.0], jug_body),
            Box([-0.8, 1.6, -3.2], [1.6, 0.8, 1.6], solid([242, 242, 238], ridge=True)),
            Box([2.0, -2.4, -2.8], [0.8, 3.2, 1.6], solid([228, 224, 208])),
        ]),
        Prop('taco', 'forearm_r', hand_r, [Box([-0.8, -2.4, -4.4], [1.6, 2.4, 4.0], taco_shell)]),
    ]


# ---------------------------------------------------------------- him
class Chugs(H.Character):
    name = 'badlands_chugs'
    head_top_px = 35.0
    targets = [
        (H.ELVS + 'elvs_jowls1', 0.8), (H.ELVS + 'elvs_chipmunkcheeks_l1', 0.6), (H.ELVS + 'elvs_malebackfat1', 1.0),
        ('bodyshapes/bodyshapes-elvs-man-apple', 0.6),
        ('macrodetails/african-male-young', 1 - OLD), ('macrodetails/african-male-old', OLD),
        ('macrodetails/universal-male-young-averagemuscle-maxweight', (1 - OLD) * WEIGHT),
        ('macrodetails/universal-male-old-averagemuscle-maxweight', OLD * WEIGHT),
        ('stomach/stomach-pregnant-incr', 1.0), ('neck/neck-double-incr', 1.0), ('head/head-fat-incr', 1.0),
        ('head/head-round', 0.4), ('neck/neck-scale-horiz-incr', 1.0), ('neck/neck-scale-depth-incr', 0.8),
        ('torso/torso-scale-depth-incr', 0.5), ('torso/torso-scale-horiz-incr', 0.3),
        # his face: wide and round, full cheeks, broad nose, full lips, long chin, smiling eyes
        ('head/head-scale-horiz-incr', 0.5), ('chin/chin-height-incr', 0.4), ('chin/chin-width-incr', 0.5),
        ('cheek/l-cheek-volume-incr', 1.0), ('cheek/r-cheek-volume-incr', 1.0),
        ('cheek/l-cheek-inner-incr', 0.5), ('cheek/r-cheek-inner-incr', 0.5),
        ('nose/nose-scale-horiz-incr', 0.6), ('nose/nose-flaring-incr', 0.6), ('nose/nose-nostrils-width-incr', 0.5),
        ('nose/nose-volume-incr', 0.4), ('mouth/mouth-scale-horiz-incr', 0.4), ('mouth/mouth-angles-up', 1.0),
        ('mouth/mouth-lowerlip-volume-incr', 0.4), ('mouth/mouth-upperlip-volume-incr', 0.3),
        ('eyes/l-eye-height2-decr', 0.4), ('eyes/r-eye-height2-decr', 0.4),
        ('eyes/l-eye-bag-incr', 0.4), ('eyes/r-eye-bag-incr', 0.4),
    ]
    fat = {'torso': 0.6, 'uarm': 0.38, 'larm': 0.15, 'uleg': 0.35, 'lleg': 0.15, 'neck': 0.55}
    bellies = [(0.43, 1.3, (2.6, 1.6), 0.65, 0.0)]
    arm_hang_deg = 16.0
    trousers = (0.22, 0.26)
    photo_url = 'https://i.ytimg.com/vi/kYnmaUPVqcA/maxresdefault.jpg'   # frontal, bald, goatee, even light
    # Photo sides are mirrored from his unobstructed left side about x = 685 (a glass covers his right).
    landmarks = [
        ('r_eye', (642.5, 269.0)), ('l_eye', (732.5, 271.0)),
        (5054, (682.5, 327.5)),                             # under the nose
        (5057, (647.5, 322.0)), (11674, (715.0, 322.0)),    # nostrils
        (411, (637.5, 370.0)), (7171, (722.5, 375.0)),      # mouth corners
        (466, (682.5, 380.0)),                              # mouth
        (5162, (677.0, 468.0)),                             # chin
        (881, (685.0, 140.0)),                              # top of the head
        (873, (581.0, 270.0)), (7578, (789.0, 270.0)),      # sides of the face at the eyes (in front of the ears)
        (('side', 9.28, -1), (601.0, 200.0)), (('side', 9.28, 1), (769.0, 200.0)),   # temples
        (('side', 8.05, -1), (571.0, 375.0)), (('side', 8.05, 1), (799.0, 375.0)),   # cheeks
        (('side', 7.77, -1), (595.0, 450.0)), (('side', 7.77, 1), (775.0, 450.0)),   # jowls
        (220, (685.0, 250.0)),                              # between the brows
    ]
    head_ellipse = (685.0, 310.0, 118.0, 175.0)
    face_floor = 7.55
    skin = np.array([116, 77, 57], float)

    @property
    def props(self):
        return chugs_props()

    mirror_face = 1                       # a glass hides his right cheek: both halves from his left
    photo_holes = [(683, 388, 58, 34)]    # he is laughing: the open mouth stays the skin's own lips
    beard = BEARD
    beard_gaps = [MOUTH]

    def covered(self, pos, w, J):
        tee, pants, _, _ = tee_masks(pos, w, J)
        return np.clip(tee + pants, 0, 1)

    def clothes(self, T):
        tee, pants, shoes, ankle = tee_masks(T.pos, T.w, T.J)
        tee_col = T.cloth(TEE)
        collar = collar_height(T.x, T.z, T.J)
        hole = np.hypot(T.x, T.z - T.J['neck01'][0][2]) < NECK_HOLE + 0.05
        rib = smoothstep(collar - 0.14, collar - 0.1, T.y) * (T.y < collar) * hole
        tee_col = tee_col * (1 + 0.35 * rib)[:, None]
        front = smoothstep(0.1, 0.3, T.nrm[:, 2])
        for text, top, height, half in (('BADLANDS', 5.95, 0.5, 1.45), ('CHUGS', 5.32, 0.36, 0.75)):
            m = H.text_mask(text, 600, 120)
            u = (T.x + half) / (2 * half)
            v = (top - T.y) / height
            inside = (u >= 0) & (u < 1) & (v >= 0) & (v < 1) & (front > 0)
            ink = np.zeros(len(T.pos))
            ink[inside] = m[(v[inside] * m.shape[0]).astype(int), (u[inside] * m.shape[1]).astype(int)]
            ink *= front * (0.88 + 0.12 * T.n_fine)
            tee_col = tee_col + (np.array([232, 230, 224.0]) * (0.75 + 0.3 * T.ao)[:, None] - tee_col) * ink[:, None]
        sole = T.y < (ankle - 0.62)
        shoe_col = np.where(sole[:, None], SOLE, SHOE) * (0.6 + 0.5 * T.ao)[:, None]
        return [(tee, tee_col), (pants, T.cloth(PANTS, 0.1)), (shoes, shoe_col)]


CH = Chugs()

if __name__ == '__main__':
    CH.generate()
