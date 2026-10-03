#!/usr/bin/env python3
"""Generates Chef Papon's boss bar sprite sheet.

Writes src/main/resources/assets/papon/textures/gui/boss_bar.png (256x64), drawn by
client/BossBarRenderer:
  frame          0,0   220x24  a crisp taco shell with rounded ends, lettuce frilling over its top
                               edge, cheese and tomato bits, a dark channel for the fill
  fill           0,24  212x10  salsa roja: red, chunky, onion and cilantro flecks (8 px repeat)
  phase-2 fill   0,34  212x10  hot sauce on fire: scrolled sideways each tick (8 px repeat)
  potato         0,44  16x16   Chef Papon's face: bald, horseshoe hair, angry, red mustache mark
  taco           16,44 16x16   the taco at the empty end of the bar
  potato, angry  32,44 16x16   phase two: beet red, steaming

Also the Stunned effect icon, textures/mob_effect/stunned.png (18x18): a dizzy swirl with stars.
"""
import math
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'src/main/resources/assets/papon/textures/gui/boss_bar.png')

SHELL = (238, 190, 80)
SHELL_DK = (190, 128, 46)
SHELL_LT = (252, 220, 132)
TOAST = (168, 104, 40)
CHANNEL = (46, 26, 18)
LETTUCE = (98, 180, 62)
LETTUCE_LT = (150, 214, 96)
TOMATO = (220, 52, 44)
CHEESE = (252, 214, 70)

rng = random.Random(1945)


def rgba(c, a=255):
    return tuple(int(max(0, min(255, v))) for v in c[:3]) + (a,)


def shade(c, k):
    return rgba([v * k for v in c])


def frame(px):
    w, h = 220, 24
    r = 11
    cy = 13
    for x in range(w):
        for y in range(2, h):
            # stadium shape: rectangle with round ends
            dx = 0
            if x < r:
                dx = r - x
            elif x > w - 1 - r:
                dx = x - (w - 1 - r)
            if dx * dx + (y - cy) * (y - cy) > r * r:
                continue
            edge = dx * dx + (y - cy) * (y - cy) > (r - 2) * (r - 2) or y in (2, 3, h - 1, h - 2)
            c = SHELL_DK if edge else SHELL
            if not edge and rng.random() < 0.08:
                c = TOAST
            if not edge and y in (4, 5):
                c = SHELL_LT
            px[x, y] = shade(c, 1 + rng.uniform(-0.05, 0.05))
    # the channel the sauce runs in
    for x in range(8, 212):
        for y in range(7, 17):
            c = CHANNEL
            if y == 7:
                c = (24, 12, 8)
            px[x, y] = shade(c, 1 + rng.uniform(-0.08, 0.08))
    # lettuce frill along the top, with cheese and tomato
    for x in range(6, 214):
        top = 1 + int(1.5 + 1.5 * math.sin(x * 0.9) + rng.uniform(-0.5, 0.5))
        for y in range(top, 6):
            px[x, y] = shade(LETTUCE_LT if y == top else LETTUCE, 1 + rng.uniform(-0.08, 0.08))
        if rng.random() < 0.12:
            px[x, 5] = rgba(TOMATO)
            if x + 1 < 214:
                px[x + 1, 5] = rgba(TOMATO)
        elif rng.random() < 0.1:
            px[x, 4] = rgba(CHEESE)


def sauce(px, y0, phase2):
    for x in range(212):
        for y in range(10):
            if phase2:
                t = (x % 8) / 8.0
                flame = math.sin((x * 0.785) + y * 0.9)
                c = (250, 120 + 70 * max(0, flame) * (1 - y / 10), 30) if y < 6 else (220, 50, 26)
                if y < 3 and (x + y * 3) % 8 < 2:
                    c = (255, 236, 120)
            else:
                c = (196, 34, 30)
                if y == 0:
                    c = (236, 84, 70)
                elif y == 9:
                    c = (140, 20, 20)
                ph = (x % 8, y)
                if ph in ((2, 3), (6, 6), (3, 7)):
                    c = (240, 236, 220)          # onion
                elif ph in ((5, 2), (0, 5), (7, 8)):
                    c = (70, 150, 50)            # cilantro
            px[x, y0 + y] = shade(c, 1 + rng.uniform(-0.04, 0.04))


POTATO = [
    "................",
    ".....tttttt.....",
    "...tttWWtttttt..",
    "..ttttWtttttttt.",
    ".hhttttttttttthh",
    ".hhbbbttttbbbthh",
    ".hhtwktttttwkthh",
    "..htttttttttth..",
    "..tttttnnttttt..",
    "..tttttnnttttt..",
    "..ttrrrrrrrrtt..",
    "..ttmmmmmmmmtt..",
    "..tttttttttttt..",
    "...tttttttttt...",
    "....tttttttt....",
    "................",
]
TACO = [
    "................",
    "................",
    "................",
    "....lLlLlLlL....",
    "..lLrLcLrLcLl...",
    ".lsSsSsSsSsSsl..",
    ".sSSSSSSSSSSSs..",
    "sSSSSSSSSSSSSSs.",
    "sSSSSSSSSSSSSSs.",
    "sSSSSSSSSSSSSSs.",
    ".sSSSSSSSSSSSs..",
    "..ssSSSSSSSss...",
    "....sssssss.....",
    "................",
    "................",
    "................",
]


def stamp(px, x0, y0, rows, pal):
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch in pal:
                px[x0 + i, y0 + j] = rgba(pal[ch])


def main():
    im = Image.new('RGBA', (256, 64), (0, 0, 0, 0))
    px = im.load()
    frame(px)
    sauce(px, 24, False)
    sauce(px, 34, True)
    tan = (218, 168, 82)
    stamp(px, 0, 44, POTATO, {'t': tan, 'W': (255, 246, 210), 'h': (44, 32, 26), 'b': (60, 40, 24), 'w': (250, 250, 250),
                              'k': (20, 20, 20), 'n': (190, 140, 66), 'r': (214, 120, 104), 'm': (90, 50, 30)})
    stamp(px, 16, 44, TACO, {'s': SHELL_DK, 'S': SHELL, 'l': LETTUCE, 'L': LETTUCE_LT, 'r': TOMATO, 'c': CHEESE})
    stamp(px, 32, 44, POTATO, {'t': (226, 98, 70), 'W': (255, 210, 190), 'h': (44, 32, 26), 'b': (40, 20, 12),
                               'w': (250, 250, 250), 'k': (120, 0, 0), 'n': (196, 80, 56), 'r': (240, 150, 140),
                               'm': (80, 20, 10)})
    for x, y in ((35, 44), (38, 45), (44, 44), (41, 44)):     # steam
        px[x, y] = rgba((236, 236, 236), 200)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    im.save(OUT)
    print(OUT)


STUN = [
    '..................',
    '...y..............',
    '..yYy.......y.....',
    '...y.......yYy....',
    '.....oooooo.y.....',
    '....o......o......',
    '...o..oooo..o.....',
    '...o.o....o.o.....',
    '...o.o.oo.o.o.....',
    '...o.o..o.o.o..y..',
    '...o..oo..o.o.yYy.',
    '....o....o..o..y..',
    '.....oooo..o......',
    '..........o.......',
    '..y....ooo........',
    '.yYy..............',
    '..y...............',
    '..................',
]


def stun_icon():
    """The Stunned effect: an orange swirl and twinkling stars."""
    im = Image.new('RGBA', (18, 18), (0, 0, 0, 0))
    pal = {'o': (242, 160, 40), 'y': (250, 222, 80), 'Y': (255, 255, 230)}
    for y, row in enumerate(STUN):
        for x, ch in enumerate(row):
            if ch in pal:
                im.putpixel((x, y), rgba(pal[ch]))
    out = os.path.join(ROOT, 'src/main/resources/assets/papon/textures/mob_effect/stunned.png')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    im.save(out)
    print(out)


if __name__ == '__main__':
    main()
    stun_icon()
