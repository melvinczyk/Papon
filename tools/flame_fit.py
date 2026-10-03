"""3D head reconstruction from photos with SMIRK (CVPR 2024, MIT) on the FLAME head model.

  python3.11 tools/flame_fit.py <name> <photo> [<photo> ...]

For each photo: MediaPipe finds the face, SMIRK's encoders estimate FLAME shape (identity),
expression, jaw, eyelids, head pose and an orthographic camera. His identity is the face-size
weighted mean of the shape codes over all photos; the neutral head (no expression, jaw closed,
looking ahead) is built from it. Each photo then keeps, per FLAME vertex, the pixel it lands on
(posed with that photo's own expression and pose, so a laugh or a turned head still maps right)
and how squarely that photo sees it. Writes tools/.cache/<name>/flame.npz, which mh_human.py
uses to reshape the MakeHuman head (Blender conform) and to texture it from all the photos.

Setup (once; tools/.cache is gitignored):
  git clone https://github.com/georgeretsi/smirk tools/.cache/smirk
  python3.11 -m pip install --user torch torchvision timm gdown scikit-image mediapipe==0.10.14
  python3.11 -m gdown 1T65uEd9dVLHgVw5KiUYL66NUee-MCzoE -O tools/.cache/smirk/pretrained_models/SMIRK_em1.pt
  FLAME 2020 (register at https://flame.is.tue.mpg.de and accept its license yourself):
    unzip so that tools/.cache/flame/generic_model.pkl exists
"""
import os
import sys

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(ROOT, 'tools/.cache')
SMIRK = os.path.join(CACHE, 'smirk')
FLAME_PKL = os.path.join(CACHE, 'flame/generic_model.pkl')
SIZE = 224


def crop_tform(landmarks, scale=1.4):
    """SMIRK's crop: a square round the landmarks, `scale` times their size, to 224 px."""
    from skimage.transform import estimate_transform
    lo, hi = landmarks.min(0), landmarks.max(0)
    size = int(((hi - lo).sum() / 2) * scale)
    c = (lo + hi) / 2
    src = np.array([[c[0] - size / 2, c[1] - size / 2], [c[0] - size / 2, c[1] + size / 2],
                    [c[0] + size / 2, c[1] - size / 2]])
    dst = np.array([[0, 0], [0, SIZE - 1], [SIZE - 1, 0]])
    return estimate_transform('similarity', src, dst)


def main(name, photos):
    import mediapipe as mp
    import torch
    from PIL import Image
    from skimage.transform import warp

    if not os.path.exists(FLAME_PKL):
        raise SystemExit('FLAME is missing: put generic_model.pkl at ' + FLAME_PKL + ' (see the docstring)')
    os.makedirs(os.path.join(SMIRK, 'assets/FLAME2020'), exist_ok=True)
    link = os.path.join(SMIRK, 'assets/FLAME2020/generic_model.pkl')
    if not os.path.exists(link):
        os.symlink(FLAME_PKL, link)
    cwd = os.getcwd()
    os.chdir(SMIRK)                     # SMIRK loads its assets by relative path
    sys.path.insert(0, SMIRK)
    from src.smirk_encoder import SmirkEncoder
    from src.FLAME.FLAME import FLAME
    import timm
    real_create = timm.create_model
    timm.create_model = lambda *a, **k: real_create(*a, **{**k, 'pretrained': False})   # weights come from SMIRK
    enc = SmirkEncoder()
    ck = torch.load('pretrained_models/SMIRK_em1.pt', map_location='cpu')
    enc.load_state_dict({k.replace('smirk_encoder.', ''): v for k, v in ck.items() if 'smirk_encoder' in k})
    enc.eval()
    flame = FLAME()
    os.chdir(cwd)

    fm = mp.solutions.face_mesh.FaceMesh(static_image_mode=True, max_num_faces=1)
    runs = []
    for path in photos:
        img = np.asarray(Image.open(path).convert('RGB'))
        res = fm.process(img)
        if not res.multi_face_landmarks:
            print('no face in', path)
            continue
        h, w = img.shape[:2]
        lm = np.array([[p.x * w, p.y * h] for p in res.multi_face_landmarks[0].landmark])
        tf = crop_tform(lm)
        crop = warp(img, tf.inverse, output_shape=(SIZE, SIZE), preserve_range=True).astype(np.float32) / 255
        with torch.no_grad():
            out = enc(torch.tensor(crop).permute(2, 0, 1)[None])
        runs.append(dict(path=path, out=out, tf=tf, size=float(np.ptp(lm[:, 1]))))
        print('%s: face %d px, jaw %.2f' % (os.path.basename(path), np.ptp(lm[:, 1]), float(out['jaw_params'][0, 0])))
    if not runs:
        raise SystemExit('no usable photos')

    wts = torch.tensor([r['size'] for r in runs])
    shape = sum(r['out']['shape_params'] * k for r, k in zip(runs, wts)) / wts.sum()
    with torch.no_grad():
        neutral = flame.forward({'shape_params': shape, 'expression_params': torch.zeros(1, 50),
                                 'pose_params': torch.zeros(1, 3), 'jaw_params': torch.zeros(1, 3),
                                 'eyelid_params': torch.zeros(1, 2)})['vertices'][0].numpy()
    faces = flame.faces_tensor.numpy()
    pix, facing, files = [], [], []
    for r in runs:
        o = dict(r['out'])
        o['shape_params'] = shape
        with torch.no_grad():
            v = flame.forward(o)['vertices'][0].numpy()
        cam = o['cam'][0].numpy()
        xy = (v[:, :2] + cam[1:]) * cam[0]
        xy[:, 1] *= -1
        crop_px = (xy + 1) * SIZE / 2
        pix.append(r['tf'].inverse(crop_px))
        n = vertex_normals(v, faces)
        facing.append(n[:, 2])
        files.append(os.path.relpath(r['path'], ROOT))
    out = os.path.join(CACHE, name, 'flame.npz')
    np.savez(out, neutral=neutral, faces=faces, pix=np.array(pix), facing=np.array(facing),
             files=np.array(files), sizes=wts.numpy(), shape=shape.numpy(),
             mp_faces=np.load(os.path.join(SMIRK, 'assets/mediapipe_landmark_embedding/mediapipe_landmark_embedding.npz'))['lmk_face_idx'],
             mp_bary=np.load(os.path.join(SMIRK, 'assets/mediapipe_landmark_embedding/mediapipe_landmark_embedding.npz'))['lmk_b_coords'],
             mp_index=np.load(os.path.join(SMIRK, 'assets/mediapipe_landmark_embedding/mediapipe_landmark_embedding.npz'))['landmark_indices'])
    print('wrote', out, len(runs), 'photos')


def vertex_normals(v, f):
    n = np.cross(v[f[:, 1]] - v[f[:, 0]], v[f[:, 2]] - v[f[:, 0]])
    N = np.zeros_like(v)
    for k in range(3):
        np.add.at(N, f[:, k], n)
    return N / np.maximum(np.linalg.norm(N, axis=1, keepdims=True), 1e-12)


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2:])
