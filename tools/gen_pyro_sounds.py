#!/usr/bin/env python3
"""The arena's stage pyrotechnics (client/PyroShow), all maths (numpy, soundfile):

  pyro/whoosh.ogg   a flame projector firing: the valve's click, a deep chesty WHOOMP as the propane catches, the roar
                    of the column and its tail lifting off (1.1 s); pitched per column so a row of them is not one note
  pyro/burst.ogg    the big one (every column at once): the same with a sub-bass thump and a longer, wider roar (1.8 s)
  pyro/gerb.ogg     a spark fountain: a hard hiss with crackle in it, an attack and a tail (1.4 s)

Writes under src/main/resources/assets/papon/sounds/; events pyro.* in sounds.json (geo_util.write_sounds).

  python tools/gen_pyro_sounds.py
"""
import numpy as np

from gen_sounds import SR, save, t_of, fft_filter, lowpass, highpass, band
import geo_util

rng = np.random.default_rng(1810)


def env(n, attack, decay):
    t = t_of(n)
    a = np.clip(t / attack, 0, 1) ** 0.5
    return a * np.exp(-np.maximum(0, t - attack) / decay)


def whoosh(seconds, size):
    n = int(seconds * SR)
    t = t_of(n)
    # the catch: a low thump swept down (the fireball's pressure wave)
    f = 95 * size ** -0.3 * np.exp(-t / 0.08) + 38
    thump = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / (0.09 + 0.05 * size))
    # the roar: low noise, a band that opens as the column grows and closes as it lifts off
    roar = fft_filter(rng.normal(0, 1, n), lambda fr: lowpass(fr, 900 + 500 * size, 2) * highpass(fr, 30, 2))
    roar *= env(n, 0.05, 0.28 + 0.2 * size)
    flutter = 1 + 0.35 * np.sin(2 * np.pi * (11 + 3 * rng.random()) * t) * np.exp(-t / 0.5)
    hiss = fft_filter(rng.normal(0, 1, n), lambda fr: band(fr, 1800, 6000)) * env(n, 0.01, 0.12) * 0.25
    click = np.zeros(n)
    click[:int(0.004 * SR)] = rng.normal(0, 1, int(0.004 * SR)) * 0.4
    out = thump * (1.0 + 0.6 * size) + roar * flutter * 1.3 + hiss + click
    if size > 1:
        sub = np.sin(2 * np.pi * 32 * t) * np.exp(-t / 0.35)
        out += sub * 0.9
    fade = np.ones(n)
    k = int(0.15 * SR)
    fade[-k:] = np.linspace(1, 0, k) ** 2
    return out * fade


def gerb(seconds):
    n = int(seconds * SR)
    t = t_of(n)
    hiss = fft_filter(rng.normal(0, 1, n), lambda fr: band(fr, 2500, 11000))
    # crackle: sparse sharp ticks, brighter and denser at the start
    crack = np.zeros(n)
    for i in range(int(seconds * 260)):
        at = int(rng.random() ** 1.4 * (n - 200))
        crack[at:at + 40] += rng.normal(0, 1, 40) * np.exp(-np.arange(40) / 8) * (0.5 + rng.random())
    crack = fft_filter(crack, lambda fr: highpass(fr, 1500, 2))
    e = np.clip(t / 0.03, 0, 1) * np.clip((seconds - t) / 0.45, 0, 1)
    return (hiss * 0.7 + crack * 1.1) * e


def main():
    save('pyro/whoosh.ogg', whoosh(1.1, 1.0))
    save('pyro/burst.ogg', whoosh(1.8, 1.6))
    save('pyro/gerb.ogg', gerb(1.4))
    sounds = geo_util.read_sounds()
    for k in ('whoosh', 'burst', 'gerb'):
        sounds['pyro.' + k] = {'sounds': [{'name': 'papon:pyro/' + k}], 'subtitle': 'subtitles.papon.pyro.' + k}
    geo_util.write_sounds(sounds)


if __name__ == '__main__':
    main()
