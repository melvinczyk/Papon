#!/usr/bin/env python3
"""Copies the boss fight's particle textures into the mod, straight out of the Groid Pack's own mods (the
user's call: it is their pack, and everything should match its pixel art; no downloaded texture packs).
The jars are read from the instance's mods folder; override it with $PAPON_MODS.

Animated particles take every frame of a pack particle. Static ones (ground decals, sparks, chunks) take
single frames, by index. Sprites are kept pixel-sharp at their own size. Ones marked "white" are turned
to greyscale (brightest pixel white) so the particle's colour tints them (client/FxParticle), so one ring
serves every warning colour and one sweep the spatula, the cleavers and the fries.

Writes, under src/main/resources/assets/papon/:
  textures/particle/fx_<type>_<i>.png    the frames / variants
  particles/<type>.json                  one per particle type, in the order FxParticle indexes them
"""
import io
import json
import os
import zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')

MODS = os.environ.get('PAPON_MODS', os.path.expanduser(
    '~/Documents/curseforge/minecraft/Instances/Groid Pack OG/mods'))
BFB = ('block_factorys_bosses-2.1.2-forge-1.20.1.jar', 'block_factorys_bosses')
LM = ('legendarymonsters-2.2.3 MC 1.20.1.jar', 'legendary_monsters')
AC = ('alexscaves-2.0.2.jar', 'alexscaves')
CATA = ('L_Enders_Cataclysm-3.31.jar', 'cataclysm')
EEEA = ('eeeabsmobs-1.20.1-0.98.1.jar', 'eeeabsmobs')
DNL = ('Dungeon Now Loading-forge-1.20.1-2.2.jar', 'dungeonnowloading')
TF = ('twilightforest-1.20.1-4.3.2508-universal.jar', 'twilightforest')
VZ = ('vzling-0.98.7-1.20.1-forge.jar', 'vzling')
SKAR = ('skarrier_mobs-1.0.8-forge-1.20.1.jar', 'skarrier_mobs')
# vanilla Minecraft itself (the user's call, 2026-10-04: the smooth, vector-looking smoke, dust, sweeps, hits and rings
# read as stock art; Minecraft's own pixel particles replace them). $PAPON_MC_JAR overrides where the client jar is.
MC_JAR = os.environ.get('PAPON_MC_JAR', os.path.expanduser('~/Documents/curseforge/minecraft/Install/versions/1.20.1/1.20.1.jar'))
MC = (MC_JAR, 'minecraft')

# type -> (jar, its particle json): every frame of that particle, as it is
ANIMATED = {
    'fire': (BFB, 'blazing_flame'),          # a lick of flame that flares and gutters (16 frames)
    'flame': (LM, 'big_orange_flame'),       # a tall flame standing on the ground (12)
    'blast': (AC, 'tephra_flame'),           # a fireball blooming into dark smoke (11)
    'burst': (CATA, 'trap_flame'),           # a small burst of fire (8)
    'smoke': (MC, 'campfire_cosy_smoke'),    # vanilla's campfire smoke (12), whitened: tinted
    'dust': (MC, 'poof'),                    # vanilla's poof (8, big to small), whitened: tinted
    'slash': (MC, 'sweep_attack'),           # vanilla's sweep (8), white: tinted, laid flat at the swing's yaw
    'hit': (MC, 'explosion'),                # vanilla's explosion puff (16), whitened: tinted
    'shock': (MC, 'sonic_boom', 8),          # the warden's sonic boom from its disc on (8), whitened: laid flat
    'flash': (CATA, 'flare_explode'),        # a gold flare bursting into embers (7)
    'torch': (DNL, 'large_soul_flame'),      # a blue flame (8): the blowtorch
}
# animated ones whitened (brightest pixel white) so the particle's colour tints them
WHITEN = {'smoke', 'dust', 'hit', 'shock'}
# type -> [(jar, particle, frame, white)]: static variants, indexed
STATIC = {
    # flat on the ground (index = Fx.DECAL_*)
    'decal': [
        (MC, 'sonic_boom', 10, True),                 # RING: a warning ring (vanilla)
        (MC, 'sonic_boom', 12, True),                 # RING_SOFT: a lasting zone's edge (vanilla)
        (LM, 'soul_sigil', 0, True),                  # SIGIL: something is being summoned
        (DNL, 'redstone_hazard_indicator', 0, True),  # CROSS: a marked spot
        (VZ, 'scorch', 0, False),                     # SCORCH: a black burn
        (MC, 'flash', 0, True),                       # GLOW: a soft disc, drawn faint (vanilla's firework flash)
        (MC, 'sonic_boom', 11, True),                 # HALO (vanilla)
        (SKAR, 'blood_splat', 0, True),               # SPLAT: mash, salsa, blood
    ],
    'spark': [(TF, 'snow_warning', i, True) for i in range(4)],  # pixel stars; 3 is the glint
    'chunk': [(BFB, 'sand_rock', i, True) for i in (0, 1, 3)],   # debris lumps; 2 is the small one (salt flakes)
}


def from_jar(jar, ns, name):
    with zipfile.ZipFile(jar if os.path.isabs(jar) else os.path.join(MODS, jar)) as z:
        tex = json.loads(z.read('assets/%s/particles/%s.json' % (ns, name)))['textures']
        out = []
        for t in tex:
            tns, tp = t.split(':') if ':' in t else (ns, t)
            out.append(Image.open(io.BytesIO(z.read('assets/%s/textures/particle/%s.png' % (tns, tp)))).convert('RGBA'))
        return out


def whiten(im):
    """Greyscale, brightest pixel white, so the particle's colour tints it."""
    a = im.getchannel('A')
    g = im.convert('L')
    hi = max(1, g.getextrema()[1])
    g = g.point(lambda v: min(255, v * 255 // hi))
    return Image.merge('RGBA', (g, g, g, a))


def square(im):
    w, h = im.size
    s = max(w, h)
    out = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    out.paste(im, ((s - w) // 2, (s - h) // 2))
    return out


def save(im, name):
    im.save(os.path.join(ASSETS, 'textures/particle/fx_%s.png' % name))
    return 'papon:fx_' + name


def write(kind, tex):
    path = os.path.join(ASSETS, 'particles/%s.json' % kind)
    with open(path, 'w', newline='\n') as f:
        json.dump({'textures': tex}, f, indent=2)
    print(path, len(tex))


def main():
    os.makedirs(os.path.join(ASSETS, 'particles'), exist_ok=True)
    tex_dir = os.path.join(ASSETS, 'textures/particle')
    for f in os.listdir(tex_dir):
        if f.startswith('fx_'):
            os.remove(os.path.join(tex_dir, f))
    for kind, src in ANIMATED.items():
        (jar, ns), name = src[:2]
        frames = from_jar(jar, ns, name)[src[2] if len(src) > 2 else 0:]
        write(kind, [save(whiten(square(im)) if kind in WHITEN else im, '%s_%d' % (kind, i)) for i, im in enumerate(frames)])
    for kind, srcs in STATIC.items():
        out = []
        for i, ((jar, ns), name, frame, white) in enumerate(srcs):
            im = square(from_jar(jar, ns, name)[frame])
            out.append(save(whiten(im) if white else im, '%s_%d' % (kind, i)))
        write(kind, out)


if __name__ == '__main__':
    main()
