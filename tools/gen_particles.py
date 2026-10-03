#!/usr/bin/env python3
"""Copies the boss fight's particle textures into the mod, from two places:

1. The Groid Pack's own boss mods (the user's call: it is their pack), read straight out of the jars in
   the instance's mods folder: animated pixel-art flames, explosions, smoke, sword sweeps, hit cuts and
   shockwave rings from Block Factory's Bosses, Legendary Monsters, Alex's Caves, L_Ender's Cataclysm
   and EEEAB's Mobs. Override the folder with $PAPON_MODS.
2. Kenney's CC0 packs (https://kenney.nl) for the smooth ground warnings, sparks and chunks, unzipped once
   into tools/.cache/kenney (gitignored):

  mkdir -p tools/.cache/kenney && cd tools/.cache/kenney
  curl -LO https://kenney.nl/media/pages/assets/particle-pack/f8fe0f8cb8-1677578741/kenney_particle-pack.zip
  curl -LO https://kenney.nl/media/pages/assets/smoke-particles/23249a0d35-1677695171/kenney_smoke-particles.zip
  unzip -o kenney_particle-pack.zip -d particle && unzip -o kenney_smoke-particles.zip -d smoke

White textures are tinted by the particle (client/FxParticle), so one sweep serves the spatula, the
cleavers and the fries. Animated ones play their frames over the particle's life.

Writes, under src/main/resources/assets/papon/:
  textures/particle/fx_<type>_<i>.png    the frames / variants
  particles/<type>.json                  one per particle type, in the order FxParticle indexes them
"""
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(ROOT, 'tools/.cache/kenney')
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')
PART = os.path.join(CACHE, 'particle/PNG (Transparent)')
SMOKE = os.path.join(CACHE, 'smoke/PNG')

MODS = os.environ.get('PAPON_MODS', os.path.expanduser(
    '~/Documents/curseforge/minecraft/Instances/Groid Pack OG/mods'))
BFB = ('block_factorys_bosses-2.1.2-forge-1.20.1.jar', 'block_factorys_bosses')
LM = ('legendarymonsters-2.2.3 MC 1.20.1.jar', 'legendary_monsters')
AC = ('alexscaves-2.0.2.jar', 'alexscaves')
CATA = ('L_Enders_Cataclysm-3.31.jar', 'cataclysm')
EEEA = ('eeeabsmobs-1.20.1-0.98.1.jar', 'eeeabsmobs')

# type -> (jar, its particle json): every frame of that particle, as it is (pixel art, kept pixel-sharp)
FROM_PACK = {
    'fire': (BFB, 'blazing_flame'),          # a lick of flame that flares and gutters (16 frames)
    'flame': (LM, 'big_orange_flame'),       # a tall flame standing on the ground (12)
    'blast': (AC, 'tephra_flame'),           # a fireball blooming into dark smoke (11)
    'burst': (CATA, 'trap_flame'),           # a small burst of fire (8)
    'smoke': (BFB, 'fancy_smoke'),           # a curling puff (16), white: tinted
    'dust': (BFB, 'dust_cloud'),             # a soft dust cloud (12), white: tinted
    'slash': (LM, 'knight_sweep'),           # a sword sweep (8), white: tinted, laid flat at the swing's yaw
    'hit': (EEEA, 'hit_cut'),                # an impact cut (6), white: tinted
    'shock': (BFB, 'yeti_death_shockwave'),  # a ring blown outward (6), white: laid flat
}
# Kenney: type -> (size, [source names]); static variants, indexed
KENNEY = {
    # flat on the ground: warnings (index = FxParticle.DECAL_*)
    'decal': (128, ['circle_02', 'circle_03', 'magic_02', 'magic_03', 'scorch_02', 'circle_05', 'light_03', 'dirt_03']),
    'spark': (32, ['star_04', 'star_06', 'flare_01', 'star_08']),
    'chunk': (32, ['dirt_01', 'dirt_02', 'circle_05']),
}
KENNEY_SEQ = {
    'flash': (96, ['Flash/flash%02d' % i for i in range(9)]),
}


def from_jar(jar, ns, name):
    import io
    import zipfile
    with zipfile.ZipFile(os.path.join(MODS, jar)) as z:
        tex = json.loads(z.read('assets/%s/particles/%s.json' % (ns, name)))['textures']
        out = []
        for t in tex:
            tns, tp = t.split(':') if ':' in t else (ns, t)
            out.append(Image.open(io.BytesIO(z.read('assets/%s/textures/particle/%s.png' % (tns, tp)))).convert('RGBA'))
        return out


def load(path):
    return Image.open(path + '.png').convert('RGBA')


def square(im):
    w, h = im.size
    s = max(w, h)
    out = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    out.paste(im, ((s - w) // 2, (s - h) // 2))
    return out


def save(im, name, size=None):
    if size:
        im = square(im).resize((size, size), Image.LANCZOS)
    im.save(os.path.join(ASSETS, 'textures/particle/fx_%s.png' % name))
    return 'papon:fx_' + name


def main():
    if not os.path.isdir(PART):
        raise SystemExit('Kenney packs missing: see the docstring of tools/gen_particles.py')
    os.makedirs(os.path.join(ASSETS, 'particles'), exist_ok=True)
    for kind, ((jar, ns), name) in FROM_PACK.items():
        write(kind, [save(im, '%s_%d' % (kind, i)) for i, im in enumerate(from_jar(jar, ns, name))])
    for kind, (size, srcs) in KENNEY.items():
        write(kind, [save(load(os.path.join(PART, src)), '%s_%d' % (kind, i), size) for i, src in enumerate(srcs)])
    for kind, (size, srcs) in KENNEY_SEQ.items():
        write(kind, [save(load(os.path.join(SMOKE, src)), '%s_%d' % (kind, i), size) for i, src in enumerate(srcs)])


def write(kind, tex):
    path = os.path.join(ASSETS, 'particles/%s.json' % kind)
    with open(path, 'w', newline='\n') as f:
        json.dump({'textures': tex}, f, indent=2)
    print(path, len(tex))


if __name__ == '__main__':
    main()
