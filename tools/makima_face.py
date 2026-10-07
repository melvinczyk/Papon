#!/usr/bin/env python3
"""Makima's face rig (tools/gen_makima_mesh.py builds it into meshes/makima.json; client/MakimaFace plays it).

The model's face is one closed surface with the eye whites and the lips painted on it (the whites inside deep sockets), and
decals floating in front of it: the irises (concave dishes), the upper and lower lashes, a lid crease and a brow per eye.
There is no hole to close, so the face gets what an anime face rig has, built from the model itself:

  lids        per eye an upper and a lower lid: a sheet over the socket's opening, rim to rim, bulged a little and never
              behind the iris wherever it looks, sliding along a track per column from the white's edge to the other.
              Closure c puts row r of the sheet at c * r / ROWS along its column's track; its first row lifts to the cap
              (where the lash reached at rest) as soon as it moves, covering what the lash hid. Each lid wears the face's
              own skin next to it (uv's sampled from just above / below the eye). Closed, the upper lash has ridden down
              onto the lower one: an anime eye shut.
  followers   the lashes ride their lid's edge (the crease part of the way): each moves as its column's edge moves.
  gaze        the irises are bones of their own (eye_l, eye_r, not in the geo: MakimaRenderer puts them on the head's
              pose) sliding GAZE across the eye.
  morphs      linear shapes on the face's own vertices (and the decals'): smile (the painted mouth's corners up), open
              (the jaw dropped under the lower lip in the mouth's lens, the upper lip lifted, a mouth sheet opening
              between them), brow_worry (inner ends up), brow_down (knit), brow_raise.
  blush       a translucent patch on each cheek (its own index run, "blush", drawn after the body with the blush's alpha).

The lids, the mouth and the blush are new geometry appended after the model's (meshes/makima.json "base" says where the
model's own ends, so gen_makima_mesh.recover reads only that back); the open mouth and the blush are patches painted into
the atlas's free corner (paint()). Everything is measured in the model's metres; client/MakimaFace and tools/makima_view.py
play it the same way (apply()).
"""
import numpy as np

SKIN, TROUSER, SHIRT, HAIR, EYE = range(5)
LID_COLS, LID_ROWS, LID_SAMPLES = 14, 6, 13
LID_GAP = 0.00035        # metres in front of whatever is under a lid
GAZE = (0.0028, 0.0013)  # metres the irises slide aside / up and down at most
OPEN_H, UP_H = 0.0062, 0.0012    # the mouth wide open: the lower lip down, the upper up (metres)
MOUTH_COLS, MOUTH_ROWS = 12, 4
# atlas patches (x, y, w, h: pixels of the 2048 atlas, y down) in its free corner (gen_makima_mesh.QUADS leaves it)
PATCH = {'mouth': (1920, 1280, 128, 128), 'blush': (1792, 1408, 128, 128)}
SKIN_QUAD = (0, 0, 1024)    # gen_makima_mesh.QUADS['skin']: the face's texture in the atlas
MORPHS = ['smile', 'open', 'brow_worry', 'brow_down', 'brow_raise']


def unit(v):
    v = np.asarray(v, float)
    return v / np.linalg.norm(v)


def smoothstep(e0, e1, x):
    t = np.clip((np.asarray(x, float) - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def cast(P, T, O, D):
    """Rays O + tD (t > 0) against triangles T: per ray the nearest hit (t, triangle row, u, v); t inf for a miss."""
    a, b, c = P[T[:, 0]], P[T[:, 1]], P[T[:, 2]]
    e1, e2 = b - a, c - a
    O = np.atleast_2d(O)
    D = np.broadcast_to(D, O.shape)
    out = np.full(len(O), np.inf), np.zeros(len(O), int), np.zeros(len(O)), np.zeros(len(O))
    for s in range(0, len(O), 128):
        o, d = O[s:s + 128, None, :], D[s:s + 128, None, :]
        h = np.cross(d, e2[None])
        det = (e1[None] * h).sum(-1)
        ok = np.abs(det) > 1e-14
        inv = np.where(ok, 1 / np.where(ok, det, 1), 0)
        q0 = o - a[None]
        u = inv * (q0 * h).sum(-1)
        q = np.cross(q0, e1[None])
        v = inv * (q * d).sum(-1)
        t = inv * (e2[None] * q).sum(-1)
        hit = ok & (u >= -1e-9) & (v >= -1e-9) & (u + v <= 1 + 1e-9) & (t > 0)
        t = np.where(hit, t, np.inf)
        i = t.argmin(1)
        r = np.arange(len(i))
        out[0][s:s + 128] = t[r, i]
        out[1][s:s + 128] = i
        out[2][s:s + 128] = u[r, i]
        out[3][s:s + 128] = v[r, i]
    return out


def vertex_normals(P, tris):
    N = np.zeros_like(P)
    fn = np.cross(P[tris[:, 1]] - P[tris[:, 0]], P[tris[:, 2]] - P[tris[:, 0]])
    for k in range(3):
        np.add.at(N, tris[:, k], fn)
    return N / np.maximum(np.linalg.norm(N, axis=1, keepdims=True), 1e-12)


class Face:
    """The face's pieces found in the model, and the frame of her eyes."""

    def __init__(self, P, tris, uvs, mats, groups, mat_of, skin_tex):
        self.P, self.tris, self.uvs, self.tex = P, tris, uvs, skin_tex
        self.N = vertex_normals(P, tris)
        head = [g for g in groups if P[g, 2].mean() > 1.3]
        self.face = max((g for g in head if mat_of(g) == SKIN), key=len)
        irises = [g for g in head if mat_of(g) == EYE and len(g) < 400]
        assert len(irises) == 2, 'expected two irises'
        self.iris = {('l' if P[g, 0].mean() > 0 else 'r'): g for g in irises}
        self.c = {s: P[g].mean(0) for s, g in self.iris.items()}
        ex = unit(self.c['l'] - self.c['r'])
        near = self.face[np.minimum(*(np.linalg.norm(P[self.face] - self.c[s], axis=1) for s in 'lr')) < 0.03]
        f = self.N[near].mean(0)
        f = unit(f - (f @ ex) * ex)
        if f[1] > 0:
            f = -f
        ey = unit(np.cross(f, ex))
        if ey[2] < 0:
            ey = -ey
        self.ex, self.ey, self.f = ex, ey, f
        # the decals round each eye: the biggest is the upper lash, then by height the brow, the crease, the lower lash
        self.decal = {}
        for s in 'lr':
            mine = [g for g in head if mat_of(g) == SKIN and len(g) < 400 and g is not self.face
                    and np.linalg.norm(P[g].mean(0) - self.c[s]) < 0.035 and (P[g].mean(0) - self.c[s]) @ f > -0.01]
            mine.sort(key=len, reverse=True)
            lash, rest = mine[0], sorted(mine[1:4], key=lambda g: (P[g].mean(0) - self.c[s]) @ ey)
            self.decal[s] = {'lash': lash, 'lower': rest[0], 'crease': rest[1], 'brow': rest[2]}
        self.ftri = np.where(np.isin(self.tris[:, 0], self.face))[0]
        self.itri = {s: np.where(np.isin(self.tris[:, 0], g))[0] for s, g in self.iris.items()}

    def local(self, p, o):
        d = np.asarray(p, float) - o
        return d @ self.ex, d @ self.ey, d @ self.f

    def at(self, o, u, v):
        return o + np.multiply.outer(u, self.ex) + np.multiply.outer(v, self.ey)

    def sample(self, O, uv_out=False):
        """Rays straight into the face from points O (pushed out in front first): hit points, face normals there, colours
        (and the atlas uv there: pixels / 2048, y down)."""
        t, i, u, v = cast(self.P, self.tris[self.ftri], O + 0.05 * self.f, -self.f)
        T = self.tris[self.ftri[i]]
        w = np.stack([1 - u - v, u, v], 1)
        pts = O + 0.05 * self.f - np.multiply.outer(t, self.f)
        nrm = (self.N[T] * w[:, :, None]).sum(1)
        nrm /= np.linalg.norm(nrm, axis=1, keepdims=True)
        uv = (self.uvs[self.ftri[i]] * w[:, :, None]).sum(1)
        h, wd = self.tex.shape[:2]
        row = np.clip(((1 - uv[:, 1]) * (h - 1)).astype(int), 0, h - 1)
        col = self.tex[row, np.clip((uv[:, 0] * (wd - 1)).astype(int), 0, wd - 1)]
        col[~np.isfinite(t)] = np.nan
        if uv_out:
            x, y, sz = SKIN_QUAD
            return pts, nrm, col, np.stack([(x + uv[:, 0] * sz) / 2048, (y + (1 - uv[:, 1]) * sz) / 2048], 1)
        return pts, nrm, col

    def front(self, O, side):
        """The frontmost of the face and the iris wherever it can look, along -f from in front of O: points GAP before it."""
        P0 = O + 0.05 * self.f
        best = cast(self.P, self.tris[self.ftri], P0, -self.f)[0]
        T = self.tris[self.itri[side]]
        for gx in (-GAZE[0], 0, GAZE[0]):
            for gy in (-GAZE[1], 0, GAZE[1]):
                Ps = self.P + gx * self.ex + gy * self.ey
                best = np.minimum(best, cast(Ps, T, P0, -self.f)[0])
        best = np.where(np.isfinite(best), best, 0.05)
        return P0 - np.multiply.outer(best - LID_GAP, self.f)

    # ---------------------------------------------------------------- eyes
    def white(self, side):
        """The painted eye white's top and bottom edge per column (u along the eye, from the iris's centre), and the skin
        colour just under the eye."""
        c = self.c[side]
        us = np.arange(-0.034, 0.0341, 0.0006)
        vs = np.arange(-0.018, 0.0181, 0.0004)
        U, V = np.meshgrid(us, vs)
        _, _, col = self.sample(self.at(c, U.ravel(), V.ravel()))
        col = col.reshape(U.shape + (3,))
        sat = np.nanmax(col, -1) - np.nanmin(col, -1)
        white = (sat < 0.08) & (np.nanmean(col, -1) > 0.45)
        cols = np.where(white.sum(0) >= 2)[0]
        # the run of columns round the iris
        mid = np.argmin(np.abs(us))
        lo = hi = min(cols, key=lambda k: abs(k - mid))
        while lo - 1 in cols:
            lo -= 1
        while hi + 1 in cols:
            hi += 1
        ucol = us[lo:hi + 1]
        top = np.array([vs[white[:, k]].max() for k in range(lo, hi + 1)])
        bot = np.array([vs[white[:, k]].min() for k in range(lo, hi + 1)])
        k3 = np.array([0.25, 0.5, 0.25])
        top = np.convolve(np.pad(top, 1, mode='edge'), k3, 'valid')
        bot = np.convolve(np.pad(bot, 1, mode='edge'), k3, 'valid')
        # the skin round the eye (not the bangs' shadow painted above it)
        near = np.concatenate([self.sample(self.at(c, ucol, top + 0.003))[2], self.sample(self.at(c, ucol, bot - 0.005))[2]])
        near = near[np.isfinite(near).all(1) & (near[:, 0] - near[:, 1] < 0.16)]
        return ucol, top, bot, np.median(near, 0)

    def lids(self, side):
        """Both lids of one eye: column u's, and per column a track of LID_SAMPLES points (upper lid: from above the white
        down past its bottom; lower lid: from under it up past its top), with normals."""
        ucol, top, bot, skin = self.white(side)
        c = self.c[side]
        # past the corners onto the skin (the socket's walls show there from aside): the sheet's edge always lies on skin
        uj = np.linspace(ucol[0] - 0.0025, ucol[-1] + 0.0025, LID_COLS + 1)
        tj, bj = np.interp(uj, ucol, top), np.interp(uj, ucol, bot)
        mid = (tj + bj) / 2
        corner = smoothstep(ucol[0] - 0.002, ucol[0] + 0.002, uj) * smoothstep(ucol[-1] + 0.002, ucol[-1] - 0.002, uj)
        tj = mid + np.maximum((tj - mid) * corner, 0.0006)
        bj = mid + np.minimum((bj - mid) * corner, -0.0006)
        k = np.clip((tj - bj) / 0.004, 0.35, 1)
        s = np.linspace(0, 1, LID_SAMPLES)

        def extent(decal, sign):
            """How far the lash reaches past the white per column (its top, or the lower lash's bottom)."""
            d = self.P[self.decal[side][decal]] - c
            lu, lv = d @ self.ex, d @ self.ey
            out = []
            for u in uj:
                k_ = np.abs(lu - u) < 0.0025
                out.append(sign * (sign * lv[k_]).max() if k_.any() else np.nan)
            return np.array(out)
        out = {}
        r = np.linspace(0, 1, LID_ROWS + 1)[:, None]
        # each lid wears the skin next to it: the upper the skin over the eye (its root the higher), the lower the skin under
        skins = {'up': tj[None] + 0.0032 - (0.0032 - 0.0016) * r, 'lo': bj[None] - 0.004 + (0.004 - 0.0012) * r}
        # the eye is a deep socket (the white painted inside it, the iris floating in it): a lid spans its opening, from rim
        # to rim, bulged a little like the eye under it, and never behind the iris
        rim_t = (self.sample(self.at(c, uj, tj + 0.0003))[0] - c) @ self.f
        rim_b = (self.sample(self.at(c, uj, bj - 0.0003))[0] - c) @ self.f
        for name, a, b, cap in (('up', tj + 0.0006, bj - 0.001, np.fmax(extent('lash', 1), tj + 0.0012)),
                                ('lo', bj - 0.0006, tj + 0.0012, np.fmin(extent('lower', -1), bj - 0.0012))):
            V = a[:, None] + (b - a)[:, None] * s[None, :]
            O = self.at(c, np.repeat(uj, LID_SAMPLES), V.ravel())
            env = (self.front(O, side) - c) @ self.f
            fr = np.clip((V - bj[:, None]) / np.maximum(tj - bj, 1e-5)[:, None], 0, 1)        # 0 bottom rim .. 1 top rim
            span = rim_b[:, None] + (rim_t - rim_b)[:, None] * fr + 0.0012 * np.sin(np.pi * fr) * k[:, None] + LID_GAP
            depth = np.maximum(span.ravel(), env)
            track = (O + np.multiply.outer(depth, self.f)).reshape(LID_COLS + 1, LID_SAMPLES, 3)
            mids = self.at(c, uj, (a + b) / 2)
            nrm = self.sample(mids)[1]
            uv = self.sample(self.at(c, np.broadcast_to(uj, skins[name].shape).ravel(), skins[name].ravel()), True)[3]
            out[name] = {'u': uj, 'track': track, 'normal': nrm, 'cap': self.front(self.at(c, uj, cap), side),
                         'uv': uv.reshape(LID_ROWS + 1, LID_COLS + 1, 2)}
        return out, skin

    # ---------------------------------------------------------------- mouth
    def mouth(self, guess):
        """The painted mouth: its middle, half width and the line's slope (along ex) and the lip colour."""
        o = np.asarray(guess, float)
        us = np.arange(-0.024, 0.0241, 0.0005)
        vs = np.arange(-0.007, 0.0071, 0.0004)
        U, V = np.meshgrid(us, vs)
        _, _, col = self.sample(self.at(o, U.ravel(), V.ravel()))
        lip = (col[:, 0] - col[:, 1]) > 0.22
        u, v = U.ravel()[lip], V.ravel()[lip]
        slope, icpt = np.polyfit(u, v, 1)
        lo, hi = np.percentile(u, 1), np.percentile(u, 99)
        mid = (lo + hi) / 2
        centre = o + mid * self.ex + (icpt + slope * mid) * self.ey
        return centre, (hi - lo) / 2, slope, np.nanmedian(col[lip], 0)


# ---------------------------------------------------------------- the morph fields
def lens(s):
    s = np.asarray(s, float)
    return np.where(np.abs(s) < 1, np.clip(1 - s * s, 0, 1) ** 0.6, 0)


def mouth_frame(fc, M0, slope):
    """Axes along and across the mouth's line (its slope folded in)."""
    ax = unit(fc.ex + slope * fc.ey)
    ay = unit(np.cross(fc.f, ax))
    if ay @ fc.ey < 0:
        ay = -ay
    return ax, ay


def morph_fields(fc, X, M0, hw, slope, brows):
    """Each morph's offsets (metres) for points X: {name: (n, 3)}. brows: {side: (indices into X, inner-to-outer t)}."""
    ax, ay = mouth_frame(fc, M0, slope)
    d = X - M0
    u, v, w = d @ ax, d @ ay, d @ fc.f
    s = u / hw
    front = smoothstep(-0.04, -0.022, w)
    out = {}
    # open: under the line the lower lip and jaw go down (the lip in the mouth's lens shape, the chin broadly), the upper
    # lip up in the same lens
    near = smoothstep(-0.007, -0.0015, v)
    amount = near * lens(s) + (1 - near) * smoothstep(hw * 1.6, hw * 0.6, np.abs(u))
    chin = smoothstep(-0.062, -0.046, v)
    down = np.where(v < -0.00005, -OPEN_H * amount * chin * front, 0)
    up = np.where(v >= -0.00005, UP_H * lens(s) ** 1.2 * smoothstep(0.006, 0.0006, v) * front, 0)
    out['open'] = np.outer(down + up, ay)
    # smile: the corners up and out
    sm = np.zeros_like(X)
    for sg in (-1, 1):
        k = M0 + sg * hw * ax
        f = smoothstep(0.014, 0.0, np.linalg.norm(X - k, axis=1)) * front
        sm += np.outer(f, 0.0016 * ay + sg * 0.0005 * ax)
    out['smile'] = sm
    for name in ('brow_worry', 'brow_down', 'brow_raise'):
        out[name] = np.zeros_like(X)
    for side, (idx, t) in brows.items():
        sg = 1 if side == 'l' else -1
        out['brow_worry'][idx] = np.outer(0.0024 * (1 - t) ** 1.5 - 0.0004 * t, fc.ey)
        out['brow_down'][idx] = np.outer(-0.0013 * (1 - t) ** 0.8, fc.ey) + np.outer(-sg * 0.0007 * (1 - t), fc.ex)
        out['brow_raise'][idx] = np.outer(0.0018 + 0.0006 * np.sin(np.pi * t), fc.ey)
    return out


# ---------------------------------------------------------------- build
def build(P, tris, uvs, mats, groups, mat_of, skin_tex, mouth_guess):
    """The face rig in the model's metres. Returns a dict: 'extra' geometry (points, normals, atlas uv in 0..1 of the
    patch named, triangles, run name), 'lids', 'followers', 'morph' (model-vertex and extra-vertex offsets), 'eyes',
    'colours' (for paint())."""
    fc = Face(P, tris, uvs, mats, groups, mat_of, skin_tex)
    pts, nrm, uv, tri, run = [], [], [], [], []

    def grid(points, normals, patch, rows, cols, name, flip=False):
        """A sheet of (rows+1) x (cols+1) points, textured from an atlas patch (by name) or given uv's."""
        base = sum(len(p) for p in pts)
        pts.append(points.reshape(-1, 3))
        nrm.append(normals.reshape(-1, 3))
        if isinstance(patch, str):
            x, y, w, h = PATCH[patch]
            gu = np.linspace(2, w - 2, cols + 1)
            gv = np.linspace(2, h - 2, rows + 1)
            UU, VV = np.meshgrid(gu, gv)
            uv.append(np.stack([(x + UU.ravel()) / 2048, (y + VV.ravel()) / 2048], 1))
        else:
            uv.append(patch.reshape(-1, 2))
        for r in range(rows):
            for k in range(cols):
                a, b = base + r * (cols + 1) + k, base + r * (cols + 1) + k + 1
                c, d = a + cols + 1, b + cols + 1
                tri.extend([(a, c, b), (b, c, d)] if not flip else [(a, b, c), (b, d, c)])
                run.extend([name, name])
        return base

    lids, followers, eyes, colours = [], [], {}, {}
    for side in 'lr':
        sets, skin = fc.lids(side)
        colours['skin'] = skin
        for name in ('up', 'lo'):
            L = sets[name]
            T = L['track']                                     # (cols+1, samples, 3)
            rest = np.repeat(T[:, :1, :], LID_ROWS + 1, 1)      # closure 0: every row at the track's start
            normals = np.repeat(L['normal'][:, None, :], LID_ROWS + 1, 1)
            # rows outer (r), columns inner (j): transpose to (rows+1, cols+1)
            base = grid(rest.transpose(1, 0, 2), normals.transpose(1, 0, 2), L['uv'], LID_ROWS, LID_COLS, 'body')
            lids.append({'name': name + '_' + side, 'base': base, 'track': T, 'u': L['u'], 'cap': L['cap']})
        # the lashes ride their lid's edge (the crease part of the way)
        for decal, lid, w in (('lash', 'up', 1.0), ('crease', 'up', 0.4), ('lower', 'lo', 0.75)):
            g = fc.decal[side][decal]
            u = (P[g] - fc.c[side]) @ fc.ex
            uj = sets[lid]['u']
            col = np.clip((u - uj[0]) / (uj[-1] - uj[0]) * LID_COLS, 0, LID_COLS)
            followers.append({'lid': len(lids) - (2 if lid == 'up' else 1), 'verts': g, 'col': col, 'w': w})
        eyes[side] = fc.c[side]
    # the mouth: a sheet on the painted line, opening into a lens between the lips
    M0, hw, slope, lipcol = fc.mouth(mouth_guess)
    colours['lip'] = lipcol
    ax, ay = mouth_frame(fc, M0, slope)
    sc = np.linspace(-1, 1, MOUTH_COLS + 1)
    line = M0 + np.outer(sc * hw, ax)
    surf = fc.sample(line)[0] + LID_GAP * 1.2 * fc.f
    rest = np.repeat(surf[None], MOUTH_ROWS + 1, 0)
    top, bot = UP_H * lens(sc) ** 1.2, -OPEN_H * lens(sc)
    rr = np.linspace(0, 1, MOUTH_ROWS + 1)[:, None]
    openv = top[None, :] + (bot - top)[None, :] * rr
    mouth_base = grid(rest, np.repeat(fc.sample(line)[1][None], MOUTH_ROWS + 1, 0), 'mouth', MOUTH_ROWS, MOUTH_COLS, 'body')
    mouth_open = (openv[..., None] * ay).reshape(-1, 3)
    # the blush: a patch on each cheek
    for side in 'lr':
        sg = 1 if side == 'l' else -1
        ucol, top_, bot_, _ = fc.white(side)
        o = fc.c[side] + sg * 0.004 * fc.ex + (bot_.min() - 0.0095) * fc.ey
        U, V = np.meshgrid(np.linspace(-0.014, 0.014, 7), np.linspace(0.0065, -0.0065, 5))
        hit, n_, _ = fc.sample(fc.at(o, U.ravel(), V.ravel()))
        grid((hit + 0.00025 * n_).reshape(5, 7, 3), n_.reshape(5, 7, 3), 'blush', 4, 6, 'blush')
    X = np.concatenate(pts)
    # morphs over the face, the brows, the mouth sheet and the blush
    brows = {}
    for side in 'lr':
        g = fc.decal[side]['brow']
        sg = 1 if side == 'l' else -1
        out = sg * ((P[g] - (fc.c['l'] + fc.c['r']) / 2) @ fc.ex)
        brows[side] = (g, (out - out.min()) / (out.max() - out.min()))
    model_idx = np.concatenate([fc.face] + [fc.decal[s]['brow'] for s in 'lr'])
    allX = np.concatenate([P[fc.face]] + [P[fc.decal[s]['brow']] for s in 'lr'] + [X])
    nb = len(fc.face)
    off = {}
    for side in 'lr':
        g = fc.decal[side]['brow']
        off[side] = (np.arange(nb, nb + len(g)), brows[side][1])
        nb += len(g)
    fields = morph_fields(fc, allX, M0, hw, slope, off)
    nm = len(model_idx)
    fields['open'][nm + mouth_base: nm + mouth_base + len(mouth_open)] = mouth_open      # the sheet opens on its own
    morph = {k: (model_idx, v[:nm], v[nm:]) for k, v in fields.items()}
    return {'extra': (X, np.concatenate(nrm), np.concatenate(uv), np.array(tri), run), 'lids': lids, 'followers': followers,
            'morph': morph, 'eyes': eyes, 'iris': fc.iris, 'frame': (fc.ex, fc.ey, fc.f), 'colours': colours,
            'mouth': (M0, hw)}


# ---------------------------------------------------------------- the atlas patches
def paint(img, colours):
    """The open mouth and the blush into the atlas (an RGBA PIL image) at PATCH."""
    lip = np.clip(np.asarray(colours['lip'], float), 0, 1)
    a = np.asarray(img).astype(float) / 255
    for name, (x, y, w, h) in PATCH.items():
        yy, xx = np.mgrid[0:h, 0:w]
        fy, fx = (yy + 0.5) / h, (xx + 0.5) / w
        if name == 'mouth':
            # the open mouth: dark inside, the upper teeth along the top, the tongue at the bottom, lip-coloured rim
            dx = (fx - 0.5) * 2
            rgb = np.zeros((h, w, 3)) + np.array([0.30, 0.08, 0.10])
            rgb *= (0.75 + 0.25 * fy)[..., None]
            teeth = 0.85 * smoothstep(0.2, 0.11, fy) * smoothstep(0.9, 0.5, np.abs(dx))
            rgb = rgb * (1 - teeth[..., None]) + np.array([0.95, 0.93, 0.91]) * teeth[..., None]
            tongue = smoothstep(1.0, 0.55, np.hypot(dx / 0.7, (fy - 1.02) / 0.42))
            rgb = rgb * (1 - tongue[..., None]) + np.array([0.80, 0.40, 0.44]) * tongue[..., None]
            rim = np.maximum(smoothstep(0.08, 0.0, fy), smoothstep(0.92, 1.0, fy))
            rim = np.maximum(rim, smoothstep(0.86, 1.0, np.abs(dx)))
            rgb = rgb * (1 - rim[..., None]) + lip * 0.85 * rim[..., None]
            al = np.ones_like(fy)
        else:
            # the blush: soft pink, three anime hatch strokes across it
            dx, dy = (fx - 0.5) * 2, (fy - 0.5) * 2
            soft = smoothstep(1.0, 0.2, np.hypot(dx, dy * 1.1))
            rgb = np.zeros((h, w, 3)) + np.array([0.96, 0.42, 0.48])
            hatch = np.zeros_like(fx)
            for cx in (-0.38, 0.0, 0.38):
                t = (dx - cx) * 0.94 + dy * 0.34       # leaning strokes
                hatch = np.maximum(hatch, smoothstep(0.07, 0.025, np.abs(t)) * smoothstep(0.62, 0.4, np.abs(dy)))
            rgb = rgb * (1 - 0.35 * hatch[..., None]) + np.array([0.86, 0.26, 0.34]) * 0.35 * hatch[..., None]
            al = np.clip(0.5 * soft + 0.45 * hatch * soft ** 0.3, 0, 1)
        a[y:y + h, x:x + w, :3] = np.clip(rgb, 0, 1)
        a[y:y + h, x:x + w, 3] = al
    from PIL import Image
    return Image.fromarray((a * 255).round().astype(np.uint8), 'RGBA')


# ---------------------------------------------------------------- playing it (tools/makima_view.py; client/MakimaFace the same)
def apply(mesh, R, expr):
    """Rest positions R (rig pixels, as the mesh stores them) with an expression put on: morph weights by name, lids
    'lid_up_l'.. closures 0..1. Returns a new array."""
    face = mesh.get('face')
    if not face:
        return R
    X = R.copy()
    for name, m in face['morphs'].items():
        k = expr.get(name, 0.0)
        if k:
            X[m['v']] += k * np.array(m['d']).reshape(-1, 3)
    edge = {}
    for lid in face['lids']:
        side = lid['name'][-1]
        c = expr.get('lid_' + lid['name'], 0.0)
        other = expr.get('lid_' + ('lo_' if lid['name'].startswith('up') else 'up_') + side, 0.0)
        if c + other > 0.98:
            c = c * 0.98 / (c + other)
        cols, rows, n = face['cols'], face['rows'], face['samples']
        T = np.array(lid['track']).reshape(cols + 1, n, 3)

        def at(s):
            x = np.clip(s, 0, 1) * (n - 1)
            i = np.minimum(x.astype(int), n - 2)
            f = (x - i)[..., None]
            return T[np.arange(cols + 1)[:, None], i] * (1 - f) + T[np.arange(cols + 1)[:, None], i + 1] * f
        s = c * np.arange(rows + 1)[None, :] / rows * np.ones((cols + 1, 1))
        pos = at(s)                                               # (cols+1, rows+1, 3)
        cap = np.array(lid['cap']).reshape(cols + 1, 3)
        pos[:, 0] += (cap - pos[:, 0]) * min(1.0, c * 12)
        idx = lid['base'] + np.arange((rows + 1) * (cols + 1)).reshape(rows + 1, cols + 1).T
        X[idx] = pos
        edge[len(edge)] = pos[:, -1] - T[:, 0]
    for fo in face['followers']:
        e = edge[fo['lid']]
        col = np.array(fo['col'])
        i = np.minimum(col.astype(int), face['cols'] - 1)
        f = (col - i)[:, None]
        X[fo['v']] += fo['w'] * (e[i] * (1 - f) + e[i + 1] * f)
    return X
