#!/usr/bin/env python3
"""Copies the boss fight's particle textures into the mod, from two places:

1. The Groid Pack's own boss mods (the user's call: it is their pack), read straight out of the jars in
   the instance's mods folder: animated pixel-art flames, explosions, smoke, sword sweeps, hit cuts and
   shockwave rings from Block Factory's Bosses, Legendary Monsters, Alex's Caves, L_Ender's Cataclysm
   and EEEAB's Mobs (also the flash, the debris chunks and the scorch / splat decals: the user found
   Kenney's smoke too realistic next to the pixel art). Override the folder with $PAPON_MODS.
2. Kenney's CC0 particle pack (https://kenney.nl) for the clean ground rings, sigils and sparks, unzipped
   once into tools/.cache/kenney (gitignored):

  mkdir -p tools/.cache/kenney && cd tools/.cache/kenney
  curl -LO https://kenney.nl/media/pages/assets/particle-pack/f8fe0f8cb8-1677578741/kenney_particle-pack.zip
  unzip -o kenney_particle-pack.zip -d particle

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
    'flash': (CATA, 'flare_explode'),        # a gold flare bursting into embers (7)
}
VZ = ('vzling-0.98.7-1.20.1-forge.jar', 'vzling')
SKAR = ('skarrier_mobs-1.0.8-forge-1.20.1.jar', 'skarrier_mobs')
# single frames of pack particles used as static variants: (jar, particle, frame, whiten for tinting)
PACK_FRAME = {
    'scorch': (VZ, 'scorch', 0, False),      # a black pixel burn
    'splat': (SKAR, 'blood_splat', 0, True),  # a pixel splash, white: tinted (mash, salsa, blood)
    'rock_0': (BFB, 'sand_rock', 0, True),   # debris lumps, white: tinted (mash, salt, grit)
    'rock_1': (BFB, 'sand_rock', 1, True),
    'rock_3': (BFB, 'sand_rock', 3, True),
}
# type -> (size, [sources]); static variants, indexed. A name is a Kenney texture, a '@key' is PACK_FRAME[key]
KENNEY = {
    # flat on the ground: warnings (index = FxParticle.DECAL_*)
    'decal': (128, ['circle_02', 'circle_03', 'magic_02', 'magic_03', '@scorch', 'circle_05', 'light_03', '@splat']),
    'spark': (32, ['star_04', 'star_06', 'flare_01', 'star_08']),
    'chunk': (32, ['@rock_0', '@rock_1', '@rock_3']),  # index 2 is the small one (salt flakes)
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


def whiten(im):
    """Greyscale, brightest pixel white, so the particle's colour tints it."""
    a = im.getchannel('A')
    g = im.convert('L')
    hi = max(1, g.getextrema()[1])
    g = g.point(lambda v: min(255, v * 255 // hi))
    return Image.merge('RGBA', (g, g, g, a))


def source(src, size):
    """A Kenney texture scaled smooth, or a pack frame scaled pixel-sharp."""
    if not src.startswith('@'):
        return square(load(os.path.join(PART, src))).resize((size, size), Image.LANCZOS)
    (jar, ns), name, frame, white = PACK_FRAME[src[1:]]
    im = square(from_jar(jar, ns, name)[frame])
    if white:
        im = whiten(im)
    return im.resize((size, size), Image.NEAREST)


def square(im):
    w, h = im.size
    s = max(w, h)
    out = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    out.paste(im, ((s - w) // 2, (s - h) // 2))
    return out


def save(im, name):
    im.save(os.path.join(ASSETS, 'textures/particle/fx_%s.png' % name))
    return 'papon:fx_' + name


def main():
    if not os.path.isdir(PART):
        raise SystemExit('Kenney packs missing: see the docstring of tools/gen_particles.py')
    os.makedirs(os.path.join(ASSETS, 'particles'), exist_ok=True)
    for kind, ((jar, ns), name) in FROM_PACK.items():
        write(kind, [save(im, '%s_%d' % (kind, i)) for i, im in enumerate(from_jar(jar, ns, name))])
    for kind, (size, srcs) in KENNEY.items():
        write(kind, [save(source(src, size), '%s_%d' % (kind, i)) for i, src in enumerate(srcs)])


def write(kind, tex):
    path = os.path.join(ASSETS, 'particles/%s.json' % kind)
    with open(path, 'w', newline='\n') as f:
        json.dump({'textures': tex}, f, indent=2)
    print(path, len(tex))


if __name__ == '__main__':
    main()
