#!/usr/bin/env python3
"""Generates the Taco Coupon item texture (16x16).

Writes src/main/resources/assets/papon/textures/item/taco_coupon.png
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'src/main/resources/assets/papon/textures/item/taco_coupon.png')

_ = None
PAPER = (246, 232, 190, 255)
PAPER_DK = (222, 202, 150, 255)
RED = (200, 50, 42, 255)
RED_DK = (140, 30, 28, 255)
SHELL = (238, 190, 80, 255)
SHELL_DK = (196, 136, 52, 255)
LETTUCE = (96, 178, 60, 255)
TOMATO = (226, 64, 50, 255)
GOLD = (255, 214, 72, 255)

# Ticket: rows 3..12, red border, perforated stub on the right, taco on the left, gold star on the stub.
GRID = [
    "................",
    "................",
    "................",
    ".RRRRRRRRRRRRRR.",
    ".RppppppppRpppR.",
    ".RppgltgpppRpGpR",
    ".RpgsgstgpRpGGGR",
    ".RpsssssspRppGpR",
    ".RpSsssSspRpppR.",
    ".RpppppppppRppR.",
    ".RddddddddRdddR.",
    ".rrrrrrrrrrrrrr.",
    "................",
    "................",
    "................",
    "................",
]
PALETTE = {'.': None, 'R': RED, 'r': RED_DK, 'p': PAPER, 'd': PAPER_DK, 's': SHELL, 'S': SHELL_DK,
           'l': LETTUCE, 't': TOMATO, 'g': LETTUCE, 'G': GOLD}


def main():
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(GRID):
        row = row.ljust(16, '.')[:16]
        for x, ch in enumerate(row):
            c = PALETTE.get(ch)
            if c:
                im.putpixel((x, y), c)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    im.save(OUT)
    print(OUT)


if __name__ == '__main__':
    main()
