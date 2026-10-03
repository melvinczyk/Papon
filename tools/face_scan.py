"""Single-photo 3D face reconstruction for the mesh celebrities (MediaPipe Face Mesh, Apache-2.0).

MediaPipe finds 468 points on the face in a photo, each with a depth, laid out like its canonical
face model (tools/.cache/mediapipe/canonical_face_model.obj, which has the triangles). Here they
are turned into his face in 3D, seen straight on:

  scan(path)   the points, then a similarity fit (Umeyama: scale, 3D rotation, shift) onto the
               canonical face. Applying it undoes the head's turn and tilt in the photo, so `F` is
               the shape of his face in the canonical frame (centimetres, y up, nose toward +z).
               The fit is kept, so any point in that frame maps back to its photo pixel.

mh_human.py uses this to reshape the MakeHuman head to his face (tools/blender_face_fit.py does the
final surface conform in Blender) and to project the photo through the recovered camera pose.

Needs `pip install mediapipe==0.10.14` (its legacy Face Mesh runs on the CPU; newer releases need
the Metal GPU).
"""
import os

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(ROOT, 'tools/.cache/mediapipe')
CANON_URL = ('https://raw.githubusercontent.com/google-ai-edge/mediapipe/master/mediapipe/modules/'
             'face_geometry/data/canonical_face_model.obj')

# MediaPipe point ids (subject's right = image left in a frontal photo)
R_EYE = (33, 133, 159, 145)
L_EYE = (263, 362, 386, 374)
SUBNASALE, MOUTH_R, MOUTH_L, MOUTH_MID, CHIN, BROWS = 2, 61, 291, 13, 152, 168


def _sets():
    import mediapipe as mp
    fm = mp.solutions.face_mesh
    ids = lambda edges: sorted({i for e in edges for i in e})
    return ids(fm.FACEMESH_LIPS), ids(fm.FACEMESH_LEFT_EYE) + ids(fm.FACEMESH_RIGHT_EYE), fm.FACEMESH_FACE_OVAL


def canonical():
    """(468x3 vertices, triangles) of MediaPipe's canonical face."""
    path = os.path.join(CACHE, 'canonical_face_model.obj')
    if not os.path.exists(path):
        import subprocess
        os.makedirs(CACHE, exist_ok=True)
        subprocess.run(['curl', '-sfL', '-o', path, CANON_URL], check=True)
    V, F = [], []
    for line in open(path):
        if line.startswith('v '):
            V.append([float(x) for x in line.split()[1:4]])
        elif line.startswith('f '):
            F.append([int(t.split('/')[0]) - 1 for t in line.split()[1:4]])
    return np.array(V), np.array(F)


def umeyama(src, dst):
    """Similarity (s, R, t) with dst ~ s R src + t, least squares."""
    ms, md = src.mean(0), dst.mean(0)
    a, b = src - ms, dst - md
    U, S, Vt = np.linalg.svd(b.T @ a / len(src))
    D = np.eye(src.shape[1])
    if np.linalg.det(U @ Vt) < 0:
        D[-1, -1] = -1
    R = U @ D @ Vt
    s = (S * np.diag(D)).sum() / (a ** 2).sum(1).mean()
    return s, R, md - s * R @ ms


class Scan:
    def __init__(self, path):
        import mediapipe as mp
        from PIL import Image
        img = np.asarray(Image.open(path).convert('RGB'))
        h, w = img.shape[:2]
        with mp.solutions.face_mesh.FaceMesh(static_image_mode=True, max_num_faces=1, refine_landmarks=False) as fm:
            res = fm.process(img)
        if not res.multi_face_landmarks:
            raise SystemExit('no face found in ' + path)
        P = np.array([[p.x * w, p.y * h, p.z * w] for p in res.multi_face_landmarks[0].landmark])[:468]
        self.pixels = P[:, :2]
        self.Lc = P * [1, -1, -1]                     # y up, toward the camera +z
        self.C, self.tris = canonical()
        self.lips, self.eyes, self.oval_edges = _sets()
        self.s, self.R, self.t = umeyama(self.Lc, self.C)
        self.F = self.to_canon(self.Lc)
        # mirror twins on the canonical face
        self.twin = np.argmin(np.linalg.norm(self.C[:, None] - self.C[None] * [-1, 1, 1], axis=2), axis=1)

    def to_canon(self, p):
        return self.s * p @ self.R.T + self.t

    def to_pixel(self, q):
        """Canonical-frame points -> photo pixels."""
        p = ((q - self.t) @ self.R) / self.s
        return p[:, :2] * [1, -1]

    def dir_to_photo(self, n):
        """Canonical-frame directions -> photo frame (z toward the camera)."""
        return n @ self.R

    def symmetric(self, k=0.5):
        """F with each side averaged toward its mirror twin (k = 0.5: fully symmetric)."""
        return self.F * (1 - k) + self.F[self.twin] * [-1, 1, 1] * k

    def anchors(self):
        """Feature points (pixels) for the retouch masks: eyes, under the nose, mouth corners."""
        px = self.pixels
        return {'r_eye': px[list(R_EYE)].mean(0), 'l_eye': px[list(L_EYE)].mean(0), 5054: px[SUBNASALE],
                411: px[MOUTH_R], 7171: px[MOUTH_L], 466: px[MOUTH_MID]}

    def oval(self):
        """The face outline as a closed polygon of pixel points."""
        nxt = {}
        for a, b in self.oval_edges:
            nxt[a] = b
        start = next(iter(nxt))
        ring, i = [start], nxt[start]
        while i != start and len(ring) < 100:
            ring.append(i)
            i = nxt[i]
        return self.pixels[ring]
