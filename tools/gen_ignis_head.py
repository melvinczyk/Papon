#!/usr/bin/env python3
"""Ignis's head (phase four: Papon pulls it out from behind his back, the scar's story, and throws it down): the user's
Ignis trophy from the pack (kubejs/assets/kubejs/models/block/cataclysm_ignis_trophy.obj + its texture, made with the
pack's trophy tools), copied in as the item model papon:fx_ignis_head (Forge's OBJ loader), so it can be held and thrown.

  python3.11 tools/gen_ignis_head.py        (PAPON_PACK: the pack instance, default the CurseForge Groid Pack OG)
"""
import json
import os
import shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')
PACK = os.environ.get('PAPON_PACK', os.path.expanduser('~/Documents/curseforge/minecraft/Instances/Groid Pack OG'))
SRC = os.path.join(PACK, 'kubejs/assets/kubejs')

obj = open(os.path.join(SRC, 'models/block/cataclysm_ignis_trophy.obj')).read()
obj = obj.replace('kubejs:models/block/cataclysm_ignis_trophy.mtl', 'papon:models/item/fx_ignis_head.mtl')
with open(os.path.join(ASSETS, 'models/item/fx_ignis_head.obj'), 'w', newline='\n') as f:
    f.write(obj)
with open(os.path.join(ASSETS, 'models/item/fx_ignis_head.mtl'), 'w', newline='\n') as f:
    f.write('newmtl m0\nmap_Kd papon:item/fx_ignis_head\n')
shutil.copy(os.path.join(SRC, 'textures/item/cataclysm_ignis_trophy.png'), os.path.join(ASSETS, 'textures/item/fx_ignis_head.png'))
model = {
    'loader': 'forge:obj', 'model': 'papon:models/item/fx_ignis_head.obj', 'flip_v': True, 'automatic_culling': False,
    'shade_quads': True, 'render_type': 'minecraft:cutout', 'textures': {'particle': 'papon:item/fx_ignis_head'},
    'display': {
        'gui': {'rotation': [30, 225, 0], 'translation': [0, -2.1, -1.2], 'scale': [0.32, 0.32, 0.32]},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.25, 0.25, 0.25]},
        'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.5, 0.5, 0.5]},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.1, -1.4], 'scale': [0.19, 0.19, 0.19]},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, -1.6, 0], 'scale': [0.2, 0.2, 0.2]},
    },
}
with open(os.path.join(ASSETS, 'models/item/fx_ignis_head.json'), 'w', newline='\n') as f:
    json.dump(model, f, indent=1)
print('ok')
