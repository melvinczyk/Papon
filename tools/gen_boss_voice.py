#!/usr/bin/env python3
"""Chef Papon's phase-two voice: every line he says from the moment he kneels (the cutscene, El Coliseo
Papa and the rest of the fight) in English, in a cloned voice, with the data his mouth needs to say it.

  1. takes   (Modal GPU, profile melvinczyk) Qwen3-TTS-12Hz-1.7B-Base clones the reference recording
             (tools/src/voice/reference.mp3, gitignored: Spanish-accented English, the charm the user wants:
             Antonio Banderas). Each line is directed: its text marks the stresses (capitals) and the
             dramatic pauses (ellipses), and its mood picks the clip to clone from (REFS: shouts from his
             most worked-up sentence, the rest from his liveliest stretch) and an instruction for the
             delivery (DIRECTION). `TAKES` takes a line, different seeds; Whisper transcribes each back,
             and Qwen's speaker embedding scores it against the reference. Cached in
             tools/.cache/voice/takes: only new or changed lines go back to the GPU (tools/voice_modal.py).
  2. pick    never a take the model cut off mid-word; then one that says the line (Whisper, to a quarter
             of the words); then the most expressive: widest pitch range, loudness swing, the pauses.
  3. build   trimmed (a quarter second kept after the last sound, so no word's end is clipped),
             loudness-matched, mono Vorbis at sounds/voice/boss2/<line>.ogg, a sound event
             "voice.boss2.<line>", the bubble text "speech.papon.boss2.<line>", and for the lip sync
             assets/papon/voice/boss2.json:
               lines.<line>.len      seconds
               lines.<line>.mouth    Rhubarb Lip Sync cues [[t, 'A'..'H'|'X'], ...] (PocketSphinx with
                                     the line's text as the dialog, so the phonemes are the right ones)
               lines.<line>.env      loudness 0..1 at `rate` Hz (how wide the mouth opens)
               lines.<line>.pitch    semitones over his median (brows), 0 when unvoiced
               lines.<line>.accent   0..1 spikes on the stressed syllables (nods, brow pops)
               lines.<line>.mood     the expression he wears saying it (client/BossFace)
               beats                 the phase-two cutscene timed to the lines (beats()): PaponBossEntity,
                                     client/Cinematic and gen_boss_anims all read them, so nothing is
                                     talked over and the gestures land on the words. Run
                                     gen_boss_anims.py after this.

  python3.11 tools/gen_boss_voice.py            # generate what is missing, then build
  python3.11 tools/gen_boss_voice.py --redo apology,resume   # new takes for these lines
  python3.11 tools/gen_boss_voice.py --build    # rebuild from the cached takes only (no GPU)
Needs: pip install modal librosa soundfile numpy; ffmpeg; Rhubarb in tools/.cache/rhubarb (macOS build
of 1.13.0); a Modal token for the melvinczyk profile (`python3.11 -m modal token new --profile melvinczyk`).
"""
import argparse
from geo_util import read_sounds, write_sounds
import glob
import json
import os
import re
import subprocess
import sys
import tempfile

import numpy as np
import soundfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')
CACHE = os.path.join(ROOT, 'tools/.cache/voice')
REFERENCE = os.path.join(ROOT, 'tools/src/voice/reference.mp3')
RHUBARB = sorted(glob.glob(os.path.join(ROOT, 'tools/.cache/rhubarb/Rhubarb-Lip-Sync-*/rhubarb*')))
TAKES = 12
RATE = 50                      # Hz of the env / pitch / accent tracks
# the Modal profile to run on: PAPON_MODAL_PROFILE, else whichever profile is active (~/.modal.toml)
MODAL_PROFILE = os.environ.get('PAPON_MODAL_PROFILE')
# Without the reference recording (it is gitignored: a fresh checkout has none), the clone is taken from his own
# lines already built: the same voice, so new lines match the old ones. name -> lines, joined with a breath
REFS_FROM_LINES = {
    # (2026-10-04: the lines in FRESH are written to sound like a man talking, not reading; the rest as they were built)
    'animated': ['apology', 'charm3'],
    'excited': ['intro', 'despierten'],
}

# The references: the same man worked up, or animated. The clone copies the delivery of the clip it is
# given, so shouts are cloned from his "He will never pay!" and everything else from the liveliest stretch
# of the recording; both with their words as Whisper heard them.
REFS = {
    'animated': ((36.3, 44.5), "Ser mono. Curious, because mono, to be a monkey. But to be a monkey in Spanish, ser mono is "
                               "just to be cute."),
    'excited': ((8.7, 12.1), "You're taking a coffee with somebody who is an encogido. He will never pay!"),
}

# A direction for each mood (the Base model takes an instruction prompt too: tested against none, it
# doubles his pitch range and keeps the voice as close to the reference). Reworked 2026-10-04 (the user: "awkward, not
# natural; more emotion, more charisma, more vocal difference like how a real person talks"): every direction asks for a
# real person talking, not a narrator reading: the pitch rising and falling freely, the pace speeding up and slowing down,
# words leaned into, a breath, a laugh in the voice where it belongs.
NATURAL = ('Talk like a real person in the moment, never like a narrator reading a line: let your pitch rise and fall '
           'freely, speed up and slow down, lean into the important words and throw the small ones away, and breathe. ')
STAR = 'a charismatic, larger-than-life Spanish movie-star villain with a warm Mexican accent'
DIRECTION = {
    'shout': ('excited', NATURAL + 'You are %s, and you are ROARING: explosive, raw, the voice cracking with fury and '
                         'adrenaline, the stressed words hit like punches.' % STAR),
    'angry': ('excited', NATURAL + 'You are %s, and you are furious and offended: big, hot emotion, the voice climbing as '
                         'the anger builds, biting the stressed words.' % STAR),
    'warm': ('animated', NATURAL + 'You are %s: warm, smooth and flirtatious, smiling as you talk, a little chuckle in the '
                         'voice, savouring the words.' % STAR),
    'smug': ('animated', NATURAL + 'You are %s: cocky, smug and delighted with yourself, grinning as you speak, a playful '
                         'lilt that swoops up and down, showing off for a crowd.' % STAR),
    'sly': ('animated', NATURAL + 'You are %s: sly and teasing, playing with your victim like a cat, a dangerous smile in '
                        'the voice, dropping low then lifting on the punchline.' % STAR),
    'pained': ('animated', NATURAL + 'You are %s, hurt and out of breath: winded, gasping between the words, wincing, '
                           'the voice strained and unsteady, but proud.' % STAR),
    'sad': ('animated', NATURAL + 'You are %s, heartbroken: the voice trembling and breaking, close to tears, slow, '
                        'wounded.' % STAR),
    'surprised': ('animated', NATURAL + 'You are %s, caught off guard: genuinely startled, the pitch jumping up, quick and '
                              'breathless, disbelieving.' % STAR),
    'drunk': ('animated', NATURAL + 'You are %s and very drunk: slurring, swaying, the pitch wobbling all over the place, '
                          'indignant and silly.' % STAR),
    'cold': ('animated', NATURAL + 'You are %s, suddenly deadly serious: quiet, low and controlled, the menace under '
                         'the calm.' % STAR),
    'evil': ('animated', NATURAL + 'You are %s, savouring your power: low, slow and dangerous, a villain who has just '
                         'remembered what he can do.' % STAR),
    'menace': ('animated', NATURAL + 'You are %s, savouring your power: low and dangerous, the voice purring with menace '
                           'and a hint of a smile. Natural word lengths, no stretched vowels.' % STAR),
    'contempt': ('animated', NATURAL + 'You are %s, seething, insulted to the core: contemptuous, the voice tight and '
                             'clipped, then flaring up.' % STAR),
    'sincere': ('animated', NATURAL + 'You are %s, and for once you mean every word: soft, quiet and sincere, grateful and a '
                            'little moved, speaking gently to old friends, no showing off.' % STAR),
    'prophet': ('animated', NATURAL + 'You are %s, an ancient god speaking to his worshippers: solemn and stirring, the voice '
                            'rising with fervour, slow and full of meaning.' % STAR),
}
# the directions every line was taken with before the rework: lines not in FRESH keep them (so they are not taken again)
OLD_DIRECTION = {
    "shout": (
        "excited",
        "SHOUT with explosive fury, like a passionate, theatrical Spanish movie star: loud, intense, the words in capitals hit hardest."
    ),
    "angry": (
        "excited",
        "Furious and indignant, like a passionate, theatrical Spanish movie star: big emotion, the words in capitals hit hardest."
    ),
    "warm": (
        "animated",
        "Speak like a passionate, theatrical Spanish movie star: smooth, warm and charming, with dramatic pauses at the ellipses."
    ),
    "smug": (
        "animated",
        "Speak like a passionate, theatrical Spanish movie star: smug, self-satisfied and seductive, with dramatic pauses at the ellipses."
    ),
    "sly": (
        "animated",
        "Speak like a passionate, theatrical Spanish movie star: sly, playful and menacing, with dramatic pauses at the ellipses."
    ),
    "pained": (
        "animated",
        "Speak like a passionate, theatrical Spanish movie star, wounded and breathless, with dramatic pauses at the ellipses."
    ),
    "sad": (
        "animated",
        "Speak like a passionate, theatrical Spanish movie star, heartbroken and tragic, almost crying, with long dramatic pauses."
    ),
    "surprised": (
        "animated",
        "Speak like a passionate, theatrical Spanish movie star, in shocked disbelief, with dramatic pauses."
    ),
    "drunk": (
        "animated",
        "Speak like a passionate, theatrical Spanish movie star, drunk and slurring, wobbly and indignant."
    ),
    "cold": (
        "animated",
        "Speak like a passionate, theatrical Spanish movie star: cold, quiet and deadly serious, low and slow, with a long dramatic pause at the ellipsis."
    ),
    "evil": (
        "animated",
        "Speak like a passionate, theatrical Spanish movie star: low, slow and dangerous, savouring every word like a villain who has just remembered his power, with long dramatic pauses at the ellipses, stretching the long vowels."
    ),
    "contempt": (
        "animated",
        "Speak like a passionate, theatrical Spanish movie star: seething with quiet contempt, offended to the core, slow, with dramatic pauses at the ellipses."
    )
}

# the lines taken with the new, more human delivery (the user, 2026-10-04: this session's new lines only)
FRESH = set(['intro', 'felt', 'notstyle', 'p3_hurt', 'p3_noise', 'p3_power', 'upgrade', 'qte_strong', 'qte_weak', 'qte_crush', 'qte_slip', 'qte_quick', 'qte_slow', 'tango_good', 'tango_bad', 'duel_good', 'duel_bad', 'p3_sombrero', 'p3_volcan', 'p3_calaveras', 'p3_swing'] + [
    k for k in ['p4_laugh', 'p4_rise', 'p4_hammer', 'p4_strike', 'p4_centuries', 'p4_true', 'p4_fiesta', 'p4_martillo', 'p4_luna', 'p4_gigante',
                'p4_grieta', 'p4_cierra', 'p4_petalos', 'p4_papel', 'p4_velas', 'p4_danza']] + ['p4_charm%d' % i for i in range(6)]
    + ['p4_hit%d' % i for i in range(3)] + ['p4_landed%d' % i for i in range(3)]
    + ['p4_pant', 'p4_scar', 'p4_howgot', 'p4_ignis', 'p4_met', 'p4_years', 'p4_prophecy', 'p4_lunaknows', 'p4_brethren',
       'p4_grant', 'p4_halfbaked', 'p4_talking', 'p4_honor', 'p4_muchachas', 'p4_twirl', 'p4_goodtime', 'p4_flechas',
       'p4_serpientes', 'p4_charm6', 'p4_charm7'])

# sampling: a little warmer than the model's default (0.9), and each take a little different (voice_modal varies it)
GEN = {'temperature': 1.0}

# line -> (text, mood). The text is directed: capitals are stressed, an ellipsis is a pause (one at most, and
# only where a pause belongs: too many and he reads word by word); the bubble shows it as written. No scream:
# the roars under it do that job better than a voice can.
LINES = {
    # ---- the transformation (on the ground)
    'bleed':     ("Heh heh... You made me BLEED, amigo.", 'pained'),
    'enough':    ("Okay, OKAY. Enough playing around.", 'smug'),
    'salt':      ("A little bit of salt...", 'warm'),
    'golden':    ("Mmm, and nice and GOLDEN!", 'warm'),
    # ---- El Coliseo Papa
    'listos':    ("ARE YOU READY?!", 'shout'),
    'musica':    ("MUSIC, MAESTRO!!!", 'shout'),
    'maestro':   ("Maestro? ...MAESTRO?!", 'surprised'),
    'despierten': ("WAKE UP, you lazy bums! We have an AUDIENCE!", 'angry'),
    'momentito': ("One moment, amigo. Just ONE moment.", 'warm'),
    'ay':        ("Oh, NO!!!", 'surprised'),
    'gordo':     ("YOU TOO, GORDO!!!", 'angry'),
    'apology':   ("My apologies, amigo. Such POOR hospitality... making my guest wait like that.", 'warm'),
    'resume':    ("Now, where were we?", 'sly'),
    # ---- the fight (his old lines, in English now)
    'salud':     ("CHEERS, amigo!!!", 'shout'),
    'intro':     ("Nobody sees my bald head and LIVES to tell!", 'angry'),
    'taunt0':    ("I am going to make TACOS out of YOU!", 'sly'),
    'taunt1':    ("My bald head shines BRIGHTER than your FUTURE!", 'smug'),
    'taunt2':    ("Come HERE, coward!!!", 'angry'),
    'taunt3':    ("This is PURE SALSA!", 'sly'),
    'keys':      ("Where did I leave the keys? AH! Here!", 'surprised'),
    'handle':    ("One little sip for the road...", 'sly'),
    'ram':       ("MAKE WAY!!!", 'shout'),
    'again':     ("AGAIN!!!", 'shout'),
    'reverse':   ("And in REVERSE!!!", 'shout'),
    'stumble':   ("Who moved the FLOOR?", 'drunk'),
    'stolen':    ("HEY!!! MY VAN!!!", 'angry'),
    'phase':     ("NOW I am REALLY MAD!!!", 'angry'),
    'death':     ("My hair... my beautiful HAIR...", 'sad'),
    'kick':      ("HERE IT COMES!!!", 'shout'),
    'bail':      ("THIS is my STOP!!!", 'shout'),
    'door':      ("Mind the DOOR!", 'sly'),
    'meteor':    ("It's RAINING POTATOES!!!", 'shout'),
    'catch':     ("CATCH!!!", 'shout'),
    'foil':      ("My TINFOIL!!! You ANIMAL!", 'angry'),
    'grab':      ("Gotcha, amigo.", 'sly'),
    'torch':     ("Nice and golden...", 'warm'),
    'season':    ("And a PINCH of salt!", 'sly'),
    'toss':      ("ON THE GRILL!!!", 'shout'),
    'dodge':     ("HA! You didn't even TOUCH me!", 'smug'),
    'burrito':   ("One burrito, TO GO!", 'sly'),
    'borracho':  ("I'm FINE!!! I'm FINE!!!", 'drunk'),
    'haymaker':  ("I TOLD you I was FINE!!!", 'drunk'),
    'brick':     ("You drive, I HIT.", 'sly'),
    'qte0':      ("Your last supper, amigo.", 'sly'),
    'qte1':      ("LAST CALL!!! Open WIDE!", 'shout'),
    'qte2':      ("MASHED TOURIST!!!", 'shout'),
    'qte_broken': ("OW!!! My BACK!!!", 'pained'),
    'gravity':   ("EVERYTHING revolves around the POTATO!!!", 'shout'),
    'echoes':    ("The THREE CHEFS!!!", 'shout'),
    # ---- the Taco Truck: Papon the Taquero (him in disguise), said as speech.papon.boss2.vendor_*
    'vendor_order0': ("Coming RIGHT up!", 'warm'),
    'vendor_order1': ("It's coming out RIGHT now!", 'warm'),
    'vendor_order2': ("But of COURSE, amigo!", 'warm'),
    'vendor_serve0': ("ENJOY your meal!", 'warm'),
    'vendor_serve1': ("Here you go, amigo!", 'warm'),
    'vendor_serve2': ("Enjoy it, my friend!", 'warm'),
    'vendor_inspect': ("A coupon? Let me see, let me SEE...", 'warm'),
    'vendor_suspicious': ("Hmm... this does NOT look right.", 'sly'),
    'vendor_fraud': ("A FAKE coupon?! FRAUD!!! NOBODY steals MY tacos!", 'angry'),
    'vendor_exposed': ("Heh. Heh heh. Heh.", 'sly'),
    'vendor_unmask': ("You want the TRUTH? HERE is the truth!", 'angry'),
    'vendor_realize': ("Wait. No... DON'T LOOK!", 'surprised'),
    'vendor_rage': ("WHAT ARE YOU LOOKING AT?!", 'shout'),
    # ---- new: his charm in the coliseum (phase-two taunts and reactions)
    'charm0':    ("Come, amigo. DANCE with me.", 'sly'),
    'charm1':    ("Listen to them. They LOVE me.", 'smug'),
    'charm2':    ("Ay, you are making me sweat, and I am ALREADY baked.", 'warm'),
    'charm3':    ("Every great potato needs a WORTHY audience.", 'smug'),
    'charm4':    ("Do not worry. I will make this quick... and BEAUTIFUL.", 'sly'),
    'charm5':    ("OLÉ!!!", 'shout'),
    'hit0':      ("AH!!! Not the FACE!", 'pained'),
    'hit1':      ("You will PAY for that, in TACOS.", 'angry'),
    'landed0':   ("Gracias, gracias. You are TOO kind.", 'smug'),
    'landed1':   ("And the crowd goes WILD!!!", 'shout'),
    # phase three: half health, the band's song is not his style; he takes the guitar and shows them
    'notstyle':  ("This is NOT my style! Who do you think I am?!", 'angry'),
    'realplay':  ("Let me show you how to REALLY play.", 'sly'),
    'gimme':     ("Give me THAT.", 'angry'),
    'mystyle':   ("THIS... is more my style. Learn, muchachos.", 'smug'),
    'go':        ("What are you WAITING for?! GO!!!", 'shout'),
    'dance':     ("Now... let's DANCE.", 'cold'),
    'felt':      ("Mmm... I haven't felt like this in a long time.", 'menace'),
    # (2026-10-04) the knockback: beaten, getting up; the noise of the band in his ears; the power after the ascension;
    # the car before he crushes it
    'p3_hurt':   ("Ugh... okay. That one hurt.", 'pained'),
    'p3_noise':  ("What... what is that noise?", 'contempt'),
    'p3_power':  ("Whoa. Whoa, whoa... so much power.", 'surprised'),
    'upgrade':   ("You know what? It's about time I gave this thing an upgrade.", 'menace'),
    # the QTEs: his charm while he has you (the squeeze reacts to how you mash)
    'qte_strong': ("Ay! You're stronger than you look, amigo!", 'surprised'),
    'qte_weak':  ("Shh, shh... just let it happen.", 'sly'),
    'qte_crush': ("Like an avocado!", 'shout'),
    'qte_slip':  ("You slippery little potato!", 'angry'),
    'qte_quick': ("Ooh, quick hands, amigo.", 'smug'),
    'qte_slow':  ("Too slow, amigo!", 'smug'),
    'tango_good': ("Ooh, you can dance, amigo!", 'smug'),
    'tango_bad': ("Two left feet, amigo. Two left feet.", 'smug'),
    'duel_good': ("Not bad, amigo. Not bad at all!", 'surprised'),
    'duel_bad':  ("Is that a guitar, or a broom?!", 'smug'),
    # the ascension: in pain, the wings tearing out of his back
    'groan1':    ("Nnngh... hhhAAAGH... AAAAAGH!", 'pained'),
    'groan2':    ("Hhhhh... HAAAAAAGH!!!", 'pained'),
    'ascend':    ("RRRAAAAAAAAAAAGH!!!", 'shout'),
    # ---- Ascended Papon's fight (phase three): a demigod's moves, his charm, the crowd in the palm of his hand
    'p3_combo':  ("Feel these GUNS, amigo!", 'smug'),
    'p3_flash':  ("Too SLOW!", 'smug'),
    'p3_comet':  ("From the HEAVENS, amigo!!!", 'shout'),
    'p3_laser':  ("Look into my EYES, amigo.", 'sly'),
    'p3_solar':  ("BEHOLD... the SUN of my ancestors!!!", 'shout'),
    'p3_hide':   ("Hide, hide, little potato. The sun sees EVERYTHING.", 'sly'),
    'p3_mariachi': ("Maestro! Something with a little more FIRE!", 'shout'),
    'p3_duel':   ("A DUEL, amigo! Guitar against guitar! Let the crowd decide!", 'shout'),
    'p3_duel_won': ("And THAT... is how a legend plays.", 'smug'),
    'p3_duel_lost': ("Impossible... They cheer for YOU?!", 'surprised'),
    'p3_squeeze': ("Come here. Let me SQUEEZE you like an avocado.", 'sly'),
    'p3_pinata': ("Who wants CANDY?!", 'shout'),
    'p3_braziers': ("Light up my COLISEUM!!!", 'shout'),
    'p3_nova':   ("Feel the heat of a THOUSAND suns... baked into ONE potato!!!", 'shout'),
    'p3_siesta': ("Ay... even a god needs a SIESTA...", 'pained'),
    'p3_nitro':  ("Get in, amigo! Oh, wait... you are the ROAD.", 'sly'),
    'p3_tango':  ("Now you DANCE for me.", 'sly'),
    'p3_charm0': ("LOUDER! I cannot hear my FANS!", 'shout'),
    'p3_charm1': ("They came to see a GOD tonight, amigo. Not you.", 'smug'),
    'p3_charm2': ("Every star needs a little background dancer.", 'sly'),
    'p3_charm3': ("Smile for the cameras, amigo. This is your LAST show.", 'sly'),
    'p3_charm4': ("Feel that? THAT is what perfection feels like.", 'smug'),
    'p3_charm5': ("Who is your favorite potato?! SAY IT!!!", 'shout'),
    'p3_hit0':   ("Ay! Careful with the MERCHANDISE!", 'pained'),
    'p3_hit1':   ("You DARE touch a demigod?!", 'angry'),
    'p3_landed0': ("Did you see that?! Did EVERYBODY see that?!", 'shout'),
    'p3_landed1': ("Thank you, thank you. I am here all NIGHT.", 'smug'),
    'p3_low':    ("No, no, no... the show must go ON!", 'angry'),
    # (2026-10-04) his new moves
    'p3_sombrero': ("Hats off to the champion... that's me!", 'shout'),
    'p3_volcan': ("Even the earth dances for me!", 'shout'),
    'p3_calaveras': ("My ancestors would love to meet you, amigo.", 'sly'),
    'p3_swing':  ("Swing... swing... and break!", 'shout'),
    # ---- 2026-10-04: more of him. Phase two's entrance is the showman's ("my coliseum, they all came for ME");
    # phase three is the ascended potato god, done playing
    'welcome':   ("WELCOME... to MY COLISEUM!!!", 'shout'),
    'p3_god':    ("No more games, amigo. Behold... the POTATO GOD.", 'evil'),
    'p3_out':    ("Out with the OLD...", 'cold'),
    'p3_new':    ("...and in with the NEW.", 'smug'),
    # his reactions to what you do (any phase; PaponBossEntity.react picks a variant, never the same twice running)
    'stolen1':   ("My VAN?! Get OUT of my van!!!", 'angry'),
    'stolen2':   ("Hey, HEY! Who gave YOU the keys?!", 'angry'),
    'stolen3':   ("That van is a FAMILY heirloom, amigo!", 'surprised'),
    'sky_stolen0': ("My SKYLINE?! Nobody drives the Skyline but ME!", 'angry'),
    'sky_stolen1': ("Careful! That is a COLLECTOR'S ITEM!", 'surprised'),
    'runover0':  ("You hit me... with my OWN van?!", 'surprised'),
    'runover1':  ("I have NO insurance, amigo!!!", 'angry'),
    'runover2':  ("Hit and RUN?! In front of my FANS?!", 'angry'),
    'honk0':     ("Do NOT honk at me!", 'angry'),
    'honk1':     ("Beep beep YOURSELF, amigo!", 'sly'),
    'vangone':   ("Where is my VAN?! Who took my VAN?!", 'surprised'),
    'shield0':   ("A shield? How CUTE.", 'smug'),
    'shield1':   ("Hiding behind a pot lid, amigo?", 'sly'),
    'ranged0':   ("Arrows? From ALL the way over there? COWARD!", 'angry'),
    'ranged1':   ("Come closer and say that to my FACE!", 'angry'),
    'snack0':    ("A snack break? In the middle of MY show?!", 'surprised'),
    'snack1':    ("Chew FASTER, amigo!", 'sly'),
    'mytaco':    ("Are you eating MY tacos?! In front of ME?!", 'angry'),
    'potion':    ("Drink up, amigo. You will NEED it.", 'sly'),
    'lowhp0':    ("Ay, you look TIRED, amigo.", 'smug'),
    'lowhp1':    ("Do not die yet! The crowd paid for the WHOLE show!", 'sly'),
    'kill0':     ("Another one for the GRILL!", 'shout'),
    'kill1':     ("Rest in PEACE... and in SALSA.", 'smug'),
    'totem':     ("A TOTEM?! CHEATER!!!", 'angry'),
    'high0':     ("Get DOWN from there!", 'angry'),
    'high1':     ("You think you can fly? I INVENTED flying!", 'smug'),
    'kite0':     ("Where are you GOING? The show is HERE!", 'angry'),
    'kite1':     ("Running away? There ARE no exits, amigo.", 'sly'),
    'miss0':     ("Stand STILL, you slippery little potato!", 'angry'),
    'miss1':     ("Ay, you dance better than my BAND!", 'warm'),
    'bighit':    ("OOF! Okay... THAT one I felt.", 'pained'),
    'qte_escape': ("You SLIPPERY little...!", 'angry'),
    # more variations: phase two's showman
    'charm6':    ("Welcome to MY house, amigo. Wipe your feet.", 'smug'),
    'charm7':    ("Look at all these people... and every SINGLE one came for ME.", 'smug'),
    'charm8':    ("Wave to the cameras, amigo! Oh, sorry... they are all pointed at ME.", 'sly'),
    'charm9':    ("SOLD OUT! Every night! Because of ME!", 'shout'),
    'landed2':   ("Did you SEE that?! Replay! REPLAY!", 'shout'),
    'landed3':   ("Encore? For you, amigo... ANYTHING.", 'sly'),
    'hit2':      ("Hey! Not in front of my PUBLIC!", 'angry'),
    'hit3':      ("Lucky shot! The crowd did not even SEE it!", 'angry'),
    # and phase three's god
    'p3_charm6': ("Kneel, amigo. Even the sun bows to me now.", 'cold'),
    'p3_charm7': ("I was a chef. Now... I am a GOD.", 'evil'),
    'p3_charm8': ("No more games. No more tacos. Only the END.", 'cold'),
    'p3_charm9': ("Pray, little mortal. Pray to the POTATO.", 'evil'),
    'p3_landed2': ("THAT is the difference... between a man and a GOD.", 'cold'),
    'p3_landed3': ("FEEL my power!!!", 'shout'),
    'p3_hit2':   ("You scratched a god. How... BRAVE.", 'contempt'),
    'p3_hit3':   ("Impressive. It changes NOTHING.", 'cold'),
    # ---- (2026-10-04) phase four, Dia de los Muertos: the ritual (he rises laughing, the spirit hammer, the moon struck,
    # the dead's coliseum, his truest form) and El Rey de los Muertos's fight
    'p4_laugh':  ("Heh heh heh... HA HA HA HA!", 'evil'),
    'p4_rise':   ("You think that was ALL? Amigo... the fiesta hasn't even started.", 'sly'),
    'p4_hammer': ("Death herself... lends me her HAMMER tonight.", 'evil'),
    'p4_strike': ("Wake up, LUNA!!!", 'shout'),
    'p4_centuries': ("It has been centuries... since I was brought to my TRUEST form.", 'menace'),
    'p4_true':   ("Behold... EL REY DE LOS MUERTOS!", 'shout'),
    'p4_fiesta': ("Now, amigo... we CELEBRATE!", 'shout'),
    'p4_martillo': ("Feel the weight of the DEAD!", 'shout'),
    'p4_luna':   ("Luna... show them your light.", 'evil'),
    'p4_gigante': ("A song for my oldest friend... RISE, GIGANTE!", 'shout'),
    'p4_grieta': ("The souls are HUNGRY, amigo!", 'evil'),
    'p4_cierra': ("And... SHUT.", 'cold'),
    'p4_petalos': ("Marigolds, to guide you HOME!", 'smug'),
    'p4_papel':  ("Duck, jump... DANCE!", 'sly'),
    'p4_velas':  ("Light a candle for yourself, amigo.", 'sly'),
    'p4_danza':  ("My ancestors want a DANCE!", 'smug'),
    'p4_charm0': ("Every year they leave me flowers. This year... I leave some for YOU.", 'sly'),
    'p4_charm1': ("The dead know how to PARTY, amigo!", 'shout'),
    'p4_charm2': ("Centuries of fiestas... and you are the BEST one.", 'smug'),
    'p4_charm3': ("Don't be scared. Death is just a long SIESTA.", 'sly'),
    'p4_charm4': ("Listen to them... the dead are cheering for ME.", 'smug'),
    'p4_charm5': ("Bones don't break, amigo. They DANCE!", 'shout'),
    'p4_hit0':   ("Ay! You rattled my BONES!", 'pained'),
    'p4_hit1':   ("You can't kill what is already DEAD!", 'angry'),
    'p4_hit2':   ("Careful! These bones are ANTIQUES!", 'surprised'),
    'p4_landed0': ("Welcome to the other SIDE!", 'shout'),
    'p4_landed1': ("Say hello to my ancestors for me.", 'sly'),
    'p4_landed2': ("OLE! The dead LOVE that one!", 'shout'),
    # (2026-10-04) phase four reworked: the reopened scar, Ignis's head, the prophecy, the souls, the Final Guardian
    'p4_pant':   ("Hah... hah... hah...", 'pained'),
    'p4_scar':   ("Huh... it reopened, didn't it.", 'pained'),
    'p4_howgot': ("Do you want to know... how I got this scar?", 'pained'),
    'p4_ignis':  ("I remember he did a little damage... but nothing notable.", 'sly'),
    'p4_met':    ("I assume you two have met before, no?", 'smug'),
    'p4_years':  ("Ten thousand years. Ten thousand years I have been waiting for this moment.", 'menace'),
    'p4_prophecy': ("After all this time... the prophecy might finally be fulfilled.", 'evil'),
    'p4_lunaknows': ("Ha... la luna knows it too.", 'sly'),
    'p4_brethren': ("My brethren... this is it. This is what we were made for.", 'prophet'),
    'p4_grant':  ("The prophecy will be fulfilled... so grant me your STRENGTH!", 'shout'),
    'p4_halfbaked': ("You know... it's not right to go into a fight half baked.", 'sly'),
    'p4_talking': ("Now we're TALKING!", 'shout'),
    'p4_honor':  ("Señores... it has been an honor. But I need you to play one last song.", 'sincere'),
    'p4_muchachas': ("Hey! Where are the muchachas?", 'smug'),
    'p4_twirl':  ("Ha ha! Still got it!", 'smug'),
    'p4_goodtime': ("COME ON!!! SHOW ME A GOOD TIME!!!", 'shout'),
    'p4_flechas': ("The sun's arrows never miss, amigo!", 'smug'),
    'p4_serpientes': ("My serpents... FEAST!", 'shout'),
    'p4_charm6': ("Ten thousand years I waited... and the prophecy sent me YOU? I love it.", 'smug'),
    'p4_charm7': ("The old gods are watching, amigo. Give them a show!", 'shout'),
}

# what the voice model is given to say, where the written line reads two ways ("lives": he said it like "alive")
SPOKEN = {
    'intro': "Nobody sees my bald head and livs to tell!",
    'upgrade': "It's about time I gave this thing an upgrade.",     # in capitals it came out "upi-grad"
}

NO_CHECK = {'charm5', 'vendor_exposed', 'groan1', 'groan2', 'ascend', 'p4_pant'}          # Whisper cannot judge a scream or a lone word


def norm(s):
    s = s.lower().replace("'", '')
    return re.sub(r'[^a-z ]+', ' ', s).split()


def wer(ref, hyp):
    r, h = norm(ref), norm(hyp)
    d = list(range(len(h) + 1))
    for i in range(1, len(r) + 1):
        prev, d[0] = d[0], i
        for j in range(1, len(h) + 1):
            cur = min(d[j] + 1, d[j - 1] + 1, prev + (r[i - 1] != h[j - 1]))
            prev, d[j] = d[j], cur
    return d[len(h)] / max(1, len(r))


def take_path(line, k):
    return os.path.join(CACHE, 'takes', '%s_%s.wav' % (line, k))


def meta_path():
    return os.path.join(CACHE, 'takes.json')


def signature(line):
    """What a line's takes were made from: if any of it changes, the line is taken again."""
    text, mood = LINES[line]
    if line not in FRESH:
        ref, direction = OLD_DIRECTION[mood]
        return {'text': SPOKEN.get(line, text), 'ref': ref, 'instruct': direction}
    ref, direction = DIRECTION[mood]
    return {'text': SPOKEN.get(line, text), 'ref': ref, 'instruct': direction, 'gen': GEN}


def have_ffmpeg():
    import shutil
    return shutil.which('ffmpeg') is not None


def wav_bytes(y, sr):
    import io
    buf = io.BytesIO()
    soundfile.write(buf, y, sr, format='WAV', subtype='PCM_16')
    return buf.getvalue()


def references():
    """{name: [wav bytes, transcript]}: cut from the reference recording, or (without it) his built lines strung
    together with a breath between them."""
    if os.path.exists(REFERENCE):
        return {name: [cut_reference(span), text] for name, (span, text) in REFS.items()}
    print('no %s: cloning from his own built lines (%s)' % (REFERENCE, REFS_FROM_LINES))
    out = {}
    for name, lines in REFS_FROM_LINES.items():
        parts, text = [], []
        for line in lines:
            y, sr = load_mono(os.path.join(ASSETS, 'sounds/voice/boss2/%s.ogg' % line))
            parts += [y, np.zeros(int(0.35 * sr), np.float32)]
            text.append(LINES[line][0].replace('...', ',').replace('!!!', '!'))
        out[name] = [wav_bytes(np.concatenate(parts[:-1]), sr), ' '.join(text)]
    return out


def cut_reference(span):
    return subprocess.run(['ffmpeg', '-loglevel', 'error', '-ss', str(span[0]), '-to', str(span[1]), '-i', REFERENCE,
                           '-ac', '1', '-ar', '24000', '-f', 'wav', 'pipe:1'], capture_output=True, check=True).stdout


def generate(lines, more=False):
    """Takes for `lines` on the GPU; saved under tools/.cache/voice/takes with what Whisper heard. With
    `more`, TAKES more takes are added to the ones a line already has (new seeds)."""
    if MODAL_PROFILE:
        os.environ['MODAL_PROFILE'] = MODAL_PROFILE
    sys.path.insert(0, os.path.join(ROOT, 'tools'))
    import modal
    import voice_modal
    refs = references()
    meta = json.load(open(meta_path(), encoding='utf-8')) if os.path.exists(meta_path()) else {}
    have = {k: len(meta[k]['takes']) if more and k in meta else 0 for k in lines}
    batch = [dict(id=k, seed0=have[k], **signature(k)) for k in lines]
    print('cloning %d lines x %d takes on Modal (%s)...' % (len(batch), TAKES, MODAL_PROFILE or 'active profile'))
    os.makedirs(os.path.join(CACHE, 'takes'), exist_ok=True)
    with modal.enable_output(), voice_modal.app.run():
        # a few containers at once
        n = min(10, len(batch))
        chunks = [batch[i::n] for i in range(n)]
        for result in voice_modal.clone.map([refs] * n, chunks, [TAKES] * n):
            for line, takes in result.items():
                if not have[line]:
                    for old in glob.glob(take_path(line, '*')):
                        os.remove(old)
                    meta[line] = dict(signature(line), takes=[])
                for k, t in enumerate(takes, start=have[line]):
                    with open(take_path(line, k), 'wb') as f:
                        f.write(t['wav'])
                    meta[line]['takes'].append({'heard': t['heard'], 'seconds': t['seconds'], 'sim': t.get('sim')})
                json.dump(meta, open(meta_path(), 'w'), indent=1)
    return meta


def load_mono(path):
    data, sr = soundfile.read(path, dtype='float32', always_2d=True)
    return data.mean(axis=1), sr


def clipped(y, sr):
    """Whether the model stopped mid-sound (the take ends on a word, not on silence)."""
    peak = np.abs(y).max() + 1e-9
    return np.sqrt(np.mean(y[-int(0.05 * sr):] ** 2)) / peak > 0.03


def expressiveness(y, sr):
    """How much the take moves: its pitch range (semitones between the 10th and 90th percentile, on a
    median-smoothed track with octave slips thrown out), loudness swing (dB), the pauses (0.18 s or more)
    inside it, and how bright the voice is (spectral centroid, Hz: a shout is brighter)."""
    import librosa
    import scipy.signal
    y = librosa.resample(y, orig_sr=sr, target_sr=16000)
    hop = 160
    f0, voiced, _ = librosa.pyin(y, fmin=60, fmax=500, sr=16000, frame_length=1024, hop_length=hop)
    rng, bright = 0.0, 0.0
    if np.count_nonzero(voiced) > 8:
        st = 12 * np.log2(np.where(voiced, f0, np.nan))
        med = np.nanmedian(st)
        st = np.where(np.abs(st - med) > 10, np.nan, st)
        good = st[~np.isnan(st)]
        if len(good) > 8:
            good = scipy.signal.medfilt(good, 5)
            rng = float(np.percentile(good, 90) - np.percentile(good, 10))
        cent = librosa.feature.spectral_centroid(y=y, sr=16000, n_fft=1024, hop_length=hop)[0][:len(voiced)]
        bright = float(np.mean(cent[voiced[:len(cent)]]))
    db = 20 * np.log10(librosa.feature.rms(y=y, frame_length=400, hop_length=hop)[0] + 1e-6)
    speech = db > np.percentile(db, 95) - 25
    dyn = float(np.percentile(db, 95) - np.percentile(db[speech], 20)) if speech.any() else 0.0
    idx = np.nonzero(speech)[0]
    pauses, run = 0, 0
    for x in (~speech[idx[0]:idx[-1]] if len(idx) else []):
        run = run + 1 if x else 0
        if run == int(0.18 * 16000 / hop):
            pauses += 1
    return {'range': rng, 'dyn': dyn, 'pauses': pauses, 'bright': bright}


def heard_energy(heard):
    """What Whisper's transcript gives away about the delivery: exclamation marks, words in capitals, a
    stretched vowel (a yell)."""
    caps = sum(1 for w in re.findall(r"[A-Za-z']+", heard) if len(w) > 1 and w.isupper())
    stretch = max((len(m.group(0)) for m in re.finditer(r'([a-zA-Z])\1{2,}', heard)), default=0)
    return heard.count('!'), caps, stretch


# takes chosen by ear over the automatic pick: line -> take index
PICKED = {}


def lives_ok(path):
    """intro: "...and LIVES to tell" said with the short i (the vowel before the v: first formant under about 600 Hz), not
    "lyves" (an open a gliding up: F1 about 750). The v found by Rhubarb (mouth shape G), the vowel the 0.2 s before it."""
    import librosa
    y, sr = load_mono(path)
    y = trim(y, sr)
    with tempfile.TemporaryDirectory() as tmp:
        w = os.path.join(tmp, 'a.wav')
        soundfile.write(w, y, sr, subtype='PCM_16')
        cues = rhubarb(w, LINES['intro'][0])
    gs = [c[0] for c in cues if c[1] == 'G']
    if not gs:
        return True
    gt = gs[-1]
    y = librosa.resample(y, orig_sr=sr, target_sr=10000)
    f1 = []
    for c in np.arange(gt - 0.2, gt - 0.02, 0.025):
        seg = y[int(c * 10000):int((c + 0.03) * 10000)]
        if len(seg) < 200:
            continue
        seg = seg * np.hamming(len(seg))
        seg = np.append(seg[0], seg[1:] - 0.63 * seg[:-1])
        r = np.roots(librosa.lpc(seg, order=12))
        f = sorted(x for x in np.angle(r[np.imag(r) >= 0.01]) * 10000 / (2 * np.pi) if 250 < x < 3500)
        if f:
            f1.append(f[0])
    return not f1 or float(np.median(f1)) < 600       # "A duel, amigo!" (Whisper heard the picked take as "A drill")


def pick(line, info):
    """The most expressive take that says the line (Whisper), sounds like him and is not cut off."""
    if line in PICKED and PICKED[line] < len(info['takes']):
        return PICKED[line], (0,)
    text, mood = LINES[line]
    loud = '!' in text
    cands = []
    for k, t in enumerate(info['takes']):
        path = take_path(line, k)
        if not os.path.exists(path):
            continue
        if 'expr2' not in t:
            y, sr = load_mono(path)
            t['expr2'] = expressiveness(y, sr)
            t['clipped'] = bool(clipped(y, sr))
        e = t['expr2']
        excl, caps, stretch = heard_energy(t['heard'])
        err = wer(text, t['heard'])
        # (2026-10-04) the liveliest take that still sounds like him: pitch range counts most (a voice that moves), up to
        # where it turns shrill, then the swing in loudness; how close to his voice only breaks near-ties
        score = 1.6 * min(e['range'], 16) - 1.5 * max(0.0, e['range'] - 20) + 0.6 * min(e['dyn'], 24) \
            + 60 * ((t.get('sim') or 0.95) - 0.95)
        if line not in FRESH:
            score = e['range'] + 0.4 * e['dyn'] + 40 * (t.get('sim') or 0)        # the pick they were built with
        # pauses: as many as the text has (its ellipses and full stops inside it), not more; and not word by word
        allowed = text.count('...') + len(re.findall(r'[.!?](?=\s+\S)', text.replace('...', '')))
        score -= 4.0 * max(0, e['pauses'] - allowed)
        words = max(1, len(norm(text)))
        score -= 12.0 * max(0.0, t['seconds'] / words - 0.55)
        if loud:
            score += 3.0 * min(excl, 3) + 2.0 * min(caps, 3)
        if mood in ('shout', 'angry'):
            score += e['bright'] / 250.0
        if line == 'intro' and 'lives_ok' not in t:
            t['lives_ok'] = bool(lives_ok(path))
        if line == 'intro' and not t['lives_ok']:
            err = 1.0                                    # "lyves": as good as the wrong words
        if line in NO_CHECK:
            # the scream / a lone word: whatever Whisper made of it, a yell (a stretched vowel), short of words
            err = max(0, len(norm(t['heard'])) - 2) / 4
            score += 4 * min(stretch, 6) + t['seconds']
        # a line written to be shouted: a take Whisper heard as flat (no "!") only if no take was exclaimed
        flat = loud and excl == 0 and line not in NO_CHECK
        cands.append((t['clipped'], round(min(err, 0.5) * 4) / 4, flat, -score, k))
    if not cands:
        return None, None
    # not cut off first, then said right (to a quarter of the words), then exclaimed if it should be, then the liveliest
    best = min(cands)
    return best[4], best


def trim(y, sr, floor_db=-55, lead=0.05, tail=0.25):
    """Silence off both ends, with room left after the last sound, so no word's end is clipped; t = 0 is
    (just before) the first sound."""
    win = int(sr * 0.01)
    rms = np.sqrt(np.convolve(y * y, np.ones(win) / win, mode='same') + 1e-12)
    loud = np.where(20 * np.log10(rms / (np.abs(y).max() + 1e-9) + 1e-12) > floor_db)[0]
    if len(loud) == 0:
        return y
    a = max(0, loud[0] - int(lead * sr))
    b = min(len(y), loud[-1] + int(tail * sr))
    out = y[a:b].copy()
    out[:int(0.005 * sr)] *= np.linspace(0, 1, int(0.005 * sr))
    fade = min(int(0.08 * sr), max(1, len(out) - (loud[-1] - a)))      # only over the quiet tail
    out[-fade:] *= np.linspace(1, 0, fade)
    return out


def loudness(y, sr, target=-9.5):
    if not have_ffmpeg():
        return loudness_py(y, sr)
    return loudness_ffmpeg(y, sr, target)


# what pyloudnorm measures on the lines mastered by loudness_ffmpeg (its meter reads them ~1.6 LU lower)
PY_TARGET = -11.1


def loudness_py(y, sr):
    """loudness_ffmpeg without ffmpeg (pip install pyloudnorm): one gain to the level the built lines measure at,
    then a soft limiter at 0.94."""
    import pyloudnorm
    lufs = pyloudnorm.Meter(sr).integrated_loudness(y)
    if not np.isfinite(lufs):
        return y
    out = y * 10 ** ((PY_TARGET - lufs) / 20)
    lim, knee = 0.94, 0.94 * 0.8
    a = np.abs(out)
    out = np.where(a > knee, np.sign(out) * (knee + (lim - knee) * np.tanh((a - knee) / (lim - knee))), out)
    return out.astype(np.float32)


def loudness_ffmpeg(y, sr, target=-9.5):
    """One gain for the whole line to an integrated loudness near `target` LUFS (ffmpeg ebur128), then a
    limiter, so he sits on top of the band and the crowd (they also duck under him: client/VoiceDuck)."""
    with tempfile.NamedTemporaryFile(suffix='.wav') as f:
        soundfile.write(f.name, y, sr, subtype='PCM_16')
        r = subprocess.run(['ffmpeg', '-hide_banner', '-nostats', '-i', f.name, '-af', 'ebur128', '-f', 'null', '-'],
                           capture_output=True, text=True).stderr
    m = re.findall(r'I:\s+(-?[\d.]+) LUFS', r)
    lufs = float(m[-1]) if m else -20.0
    with tempfile.TemporaryDirectory() as tmp:
        a, b = os.path.join(tmp, 'a.wav'), os.path.join(tmp, 'b.wav')
        soundfile.write(a, y, sr, subtype='FLOAT')
        subprocess.run(['ffmpeg', '-loglevel', 'error', '-y', '-i', a, '-af',
                        'volume=%.2fdB,alimiter=limit=0.94:attack=3:release=60:level=disabled' % (target - lufs), b], check=True)
        out, _ = soundfile.read(b, dtype='float32')
    return out[:len(y)] if len(out) >= len(y) else np.pad(out, (0, len(y) - len(out)))


def tracks(y, sr):
    """Loudness, pitch and accents at RATE Hz."""
    import librosa
    hop = sr // RATE
    rms = librosa.feature.rms(y=y, frame_length=hop * 2, hop_length=hop, center=True)[0]
    env = rms / (np.percentile(rms, 95) + 1e-9)
    env = np.clip(env, 0, 1) ** 0.8
    env = np.convolve(env, [0.25, 0.5, 0.25], mode='same')
    f0, voiced, _ = librosa.pyin(y, fmin=65, fmax=420, sr=sr, frame_length=hop * 8, hop_length=hop)
    n = len(env)
    f0, voiced = f0[:n], voiced[:n]
    semis = np.where(voiced, 12 * np.log2(np.nan_to_num(f0, nan=1.0) + 1e-9), np.nan)
    med = np.nanmedian(semis) if np.any(~np.isnan(semis)) else 0
    pitch = np.nan_to_num(semis - med, nan=0.0)
    pitch = np.clip(pitch, -8, 12)
    pitch = np.pad(pitch, (0, n - len(pitch)))
    # accents: where the loudness jumps (a syllable's onset), kept if it is a strong one
    rise = np.maximum(0, np.diff(env, prepend=0))
    rise = np.convolve(rise, [1, 1, 1], mode='same')
    acc = np.zeros(n)
    thr = max(0.12, np.percentile(rise, 90))
    last = -99
    for i in range(1, n - 1):
        if rise[i] >= thr and rise[i] >= rise[i - 1] and rise[i] >= rise[i + 1] and i - last > RATE * 0.22:
            acc[i] = min(1.0, rise[i] / (thr * 2))
            last = i
    # each accent decays over 0.25 s, so the client can read it as a pulse
    out = np.zeros(n)
    for i in np.nonzero(acc)[0]:
        for j in range(i, min(n, i + int(RATE * 0.25))):
            out[j] = max(out[j], acc[i] * (1 - (j - i) / (RATE * 0.25)))
    return env, pitch, out


def rhubarb(wav_path, text):
    if not RHUBARB:
        raise SystemExit('Rhubarb not found in tools/.cache/rhubarb (download Rhubarb-Lip-Sync-1.13.0-macOS.zip)')
    with tempfile.NamedTemporaryFile('w', suffix='.txt', delete=False) as d:
        d.write(re.sub(r'[^A-Za-z\' ,.!?]+', ' ', text))
    out = d.name[:-4] + '.json'
    try:
        # to a file: the Windows build prints nothing on stdout with -q
        subprocess.run([RHUBARB[0], '-q', '-f', 'json', '-o', out, '-r', 'pocketSphinx', '--extendedShapes', 'GHX',
                        '--dialogFile', d.name, wav_path], capture_output=True, text=True, check=True)
        cues = json.load(open(out, encoding='utf-8'))['mouthCues']
    finally:
        os.unlink(d.name)
        if os.path.exists(out):
            os.unlink(out)
    return [[round(c['start'], 3), c['value']] for c in cues]


SHOWTIME = 70            # BossState.P2_SHOWTIME: the guitar close-up before he turns to you
HYPE = 160               # BossState.P2_HYPE_LENGTH: after "where were we?" he works the stands up until the first sung word


def beats(lengths):
    """The phase-two cutscene's timing, in ticks from the start of each state, worked out from how long
    he takes to say things, so no line is cut off or talked over and every gesture lands on its words.
    PaponBossEntity, client/Cinematic, client/BossFace and gen_boss_anims all read these.
    `lengths`: line -> seconds."""
    import math
    L = lambda k: int(math.ceil(lengths.get(k, 2.0) * 20))
    b = {}
    # on one knee: the chuckle and "You made me... BLEED", then up on "Okay... OKAY. Enough..." and two neck cracks
    # (every line gets a beat of silence after it: he lets them land)
    b['talk.bleed'] = 6
    b['talk.enough'] = b['talk.bleed'] + L('bleed') + 24
    b['talk.rip'] = b['talk.enough'] + max(L('enough'), 24) + 6          # tears his apron off and flings it
    b['talk.end'] = b['talk.rip'] + 16 + 26
    # the salt, the bake (80), "Mmm... and nice... and GOLDEN!" with a chef's kiss on the last word
    b['season.salt'] = 4
    b['season.golden'] = 84
    b['season.kiss'] = b['season.golden'] + max(20, L('golden') - 16)
    b['season.end'] = max(100, b['season.golden'] + L('golden') + 16)
    # the scream (15): everyone is pulled up into the coliseum only once it is out of him
    b['scream.end'] = 70
    # the coliseum: down he comes (36) to a roar, spinning round to the stands: "WELCOME... to MY COLISEUM!!!", the
    # roar back (arrive.roar1); "ARE YOU READY?!", the roar back (roar2); then the point at the band stand: "MUSIC, MAESTRO!!!"
    b['arrive.welcome'] = 46
    b['arrive.roar1'] = b['arrive.welcome'] + L('welcome') + 1
    b['arrive.listos'] = b['arrive.roar1'] + 16
    b['arrive.roar2'] = b['arrive.listos'] + L('listos') + 1
    b['arrive.musica'] = max(74, b['arrive.roar2'] + 26)
    b['arrive.hush'] = b['arrive.musica'] + L('musica') + 6
    b['arrive.end'] = max(100, b['arrive.hush'] + 14)
    # nothing. "Maestro...? ...MAESTRO?!", and he fumes
    b['notice.maestro'] = 20
    b['notice.fume'] = b['notice.maestro'] + L('maestro') + 8
    b['notice.end'] = b['notice.fume'] + 14
    # three clangs on the comal, then the bellow
    b['wake.clang1'], b['wake.clang2'], b['wake.clang3'] = 17, 27, 37
    b['wake.line'] = 42
    b['wake.end'] = b['wake.line'] + L('despierten') + 16
    # foot tapping: "One moment, amigo..."; only after it does he spot the guitarron asleep again ("Oh... NO!!!")
    b['wait.momentito'] = 22
    b['wait.ay'] = b['wait.momentito'] + L('momentito') + 24
    # "YOU TOO, GORDO!!!", the boot on the last word
    b['kick.gordo'] = 2
    b['kick.boot'] = max(12, b['kick.gordo'] + L('gordo') - 4)
    b['kick.end'] = b['kick.boot'] + 18
    # the apology as he comes over, the aura farm, "Now... where... were we?", and the hype (HYPE ticks)
    # ending on the first sung word
    b['apology.line'] = 12
    b['apology.aura'] = b['apology.line'] + L('apology') + 14
    b['resume.line'] = 2
    b['resume.total'] = b['resume.line'] + L('resume') + 8
    song = json.load(open(os.path.join(ASSETS, 'song/cancion.json')))
    intro = next(s['length'] for s in song['segments'] if s['id'] == 'intro')
    need = SHOWTIME + b['apology.aura'] + 60 + b['resume.total'] + HYPE        # at least 3 s of aura farm
    b['song.passes'] = max(2, math.ceil(need / (intro * 20)))
    fight = int(round(intro * b['song.passes'] * 20))
    b['apology.end'] = fight - SHOWTIME - b['resume.total'] - HYPE
    b['apology.wink'] = b['apology.aura'] + min(40, (b['apology.end'] - b['apology.aura']) // 2)
    # phase three (half health), reworked 2026-10-04 (the user: "make him look weaker, like he is taking a beating"). The
    # knockback: flung (14), the crash and the skid (to 26), flat on his back, chest heaving; an arm pushes him up and gives
    # way (knock.fall), he rolls onto his side and gets a knee under him (knock.knee): "Hhh... that one... HURT." and the
    # lip wiped; up, swaying (knock.up)
    b['knock.fall'] = 54
    b['knock.knee'] = 76
    b['knock.hurt'] = 84
    b['knock.up'] = b['knock.hurt'] + L('p3_hurt') + 8
    b['knock.end'] = b['knock.up'] + 24
    # the glare, through his eyes: everything swimming, the band's song muffled in his ears ("What... is that... NOISE?"),
    # the blur pulled into focus on the band stand as his eyes zoom in on them (glare.focus); they see him staring and stop
    # dead, scared (glare.scare: the record grinds to a halt), a maraca rolls; then his face, furious: "This is NOT my
    # style... who do you think I AM?!" A beat. "Let me show you how to REALLY play."
    b['glare.noise'] = 34
    b['glare.focus'] = b['glare.noise'] + L('p3_noise') + 6
    b['glare.scare'] = b['glare.focus'] + 30
    b['glare.maraca'] = b['glare.scare'] + 14
    b['glare.face'] = b['glare.scare'] + 34
    b['glare.notstyle'] = b['glare.face'] + 10
    b['glare.realplay'] = b['glare.notstyle'] + L('notstyle') + 14
    b['glare.end'] = b['glare.realplay'] + L('realplay') + 10
    # looming over the singer: "Give me THAT." and the guitar ripped out of his hands on the last word
    b['snatch.gimme'] = 6
    b['snatch.grab'] = max(18, b['snatch.gimme'] + L('gimme') - 6)
    b['snatch.end'] = b['snatch.grab'] + 46         # holds on the band cowering after the rip
    # in the spotlight: the serenade is the song's first 18 s (guitar and voice), the last chord left ringing
    ps = json.load(open(os.path.join(ASSETS, 'song/para_siempre.json')))
    seren = next(s['length'] for s in ps['segments'] if s['id'] == 'serenade')
    pickup = next(s['length'] for s in ps['segments'] if s['id'] == 'pickup')
    b['serenade.start'] = 4
    b['serenade.end'] = b['serenade.start'] + int(math.ceil(seren * 20)) + 12
    # the ascension (before the song, in the spotlight): bent double, a groan, a worse one, the wings tear out of his
    # back (ascend.burst: the flash, his new form), the roar
    # one groan (the second made it 15 s of groaning): the wings tear out on its last cry
    b['ascend.groan1'] = 10
    b['ascend.groan2'] = -1
    b['ascend.burst'] = b['ascend.groan1'] + L('groan1') - 8
    b['ascend.roar'] = b['ascend.burst'] + 6
    # (main) risen, the god speaks, low: "No more games, amigo. Behold... the POTATO GOD."; (2026-10-04) cutaways while he
    # groans (the band clutching each other) and after it (the stands on their feet, screaming; the band backing away)
    b['ascend.cut'] = b['ascend.groan1'] + max(10, L('groan1') // 2 - 10)
    b['ascend.god'] = b['ascend.roar'] + L('ascend') + 2
    b['ascend.crowd'] = b['ascend.god'] + L('p3_god') + 6
    b['ascend.band'] = b['ascend.crowd'] + 34
    b['ascend.end'] = b['ascend.band'] + 36
    # the power goes through him and nearly puts him down: he staggers, catches himself, looks at his hands, "Whoa, WHOA...
    # so much POWER.", and the grin; then the serenade
    b['power.line'] = 22
    b['power.grin'] = b['power.line'] + L('p3_power') + 4
    b['power.end'] = b['power.grin'] + 26
    # the guitar down from his chin, the chord dying: "THIS... is more my style. Learn, muchachos."
    b['verdict.line'] = 10
    b['verdict.end'] = b['verdict.line'] + L('mystyle') + 10
    # over his shoulder without looking (toss.throw), the singer juggles it and has it (toss.catch); then nothing:
    # the band look at each other, scared, nobody moves (toss.glance on)
    b['toss.throw'] = 20
    b['toss.catch'] = 42
    b['toss.glance'] = 58
    # (2026-10-04) the band, scared, whispering to each other in a tighter shot: "What do we do?!", "Play, PLAY!"
    b['toss.talk1'] = b['toss.glance'] + 8
    b['toss.talk2'] = b['toss.glance'] + 30
    b['toss.end'] = b['toss.glance'] + 62
    # he turns to you, slowly, the power going through him: "Hmmm... I haven't felt this way... in a LOOOOONG
    # time." and an evil smile, held
    b['felt.line'] = 14
    b['felt.smile'] = b['felt.line'] + L('felt') + 2
    b['felt.end'] = b['felt.smile'] + 26
    # his car: hoisted over his head, crushed in his hands into a ball, reformed in a flash into a black Skyline
    # that grows in his hands and is slammed down on its wheels; it revs
    # (2026-10-04) first, a hand on its roof: "It's about time I gave this thing an UPGRADE."; then (main) "Out with the
    # OLD..." as it goes up over his head; then the crunch
    b['crush.upgrade'] = 6
    b['crush.out'] = b['crush.upgrade'] + L('upgrade') + 8
    b['crush.lifted'] = b['crush.out'] + 10
    b['crush.squeeze'] = max(b['crush.lifted'] + 4, b['crush.out'] + L('p3_out') + 2)
    b['crush.ball'] = b['crush.squeeze'] + 24
    b['crush.reform'] = b['crush.ball'] + 10
    b['crush.down'] = b['crush.reform'] + 14
    b['crush.rev'] = -1
    b['crush.end'] = b['crush.down'] + 14
    # the Skyline, shown off (a montage on its rev): the fob (keys), the engine and lamps (lamps), the underglow
    # stuttering on (glow), the rev (rev: the sound is 4.6 s, blips, flat out, the blow-off at bov), then him by it:
    # "...and in with the NEW." (line), the stands (roar)
    b['showcar.keys'] = 10
    b['showcar.lamps'] = 16
    b['showcar.glow'] = 34
    b['showcar.rev'] = 56
    b['showcar.bov'] = b['showcar.rev'] + 54
    b['showcar.line'] = b['showcar.rev'] + 72
    b['showcar.roar'] = b['showcar.line'] + L('p3_new') + 2
    b['showcar.end'] = b['showcar.roar'] + 26
    # he whirls round on the band: "What are you WAITING for?! GO!!!"
    b['go.line'] = 8
    b['go.end'] = b['go.line'] + L('go') + 4
    # the singer's pickup, a cappella, then the band crashes in and the fire comes back; and at the very end, very
    # quietly: "Now... let's DANCE."
    b['dance.drop'] = int(round(pickup * 20))
    b['dance.line'] = b['dance.drop'] + 34
    b['dance.end'] = b['dance.line'] + L('dance') + 16
    # ---- phase four (2026-10-04, reworked to the user's script): the Final Guardian's awakening, every beat from his lines
    # (PaponBossEntity.tickP4*, client/Cinematic, client/MuertosSky, gen_boss_anims b_p4_*)
    # P4_FALL: thrown flat (lands t14), panting on his back, up on an elbow, finds the scar: "Huh... it reopened, didn't it."
    b['p4.land'] = 14
    b['p4.elbow'] = 44
    b['p4.look'] = 58
    b['p4.scar'] = 76
    b['p4.fall_end'] = b['p4.scar'] + L('p4_scar') + 14
    # P4_RISEUP: up in two goes, the cough of blood, "Do you want to know... how I got this scar?"
    b['p4.knee'] = 16
    b['p4.cough'] = 36
    b['p4.howgot'] = 52
    b['p4.riseup_end'] = b['p4.howgot'] + L('p4_howgot') + 12
    # P4_IGNIS: the reach behind his back, Ignis's head out, held up beside his face for the line, thrown down; at you
    b['p4.head_out'] = 20
    b['p4.ignis'] = 34
    b['p4.throw'] = b['p4.ignis'] + L('p4_ignis') + 4
    b['p4.thud'] = b['p4.throw'] + 13
    b['p4.met'] = b['p4.thud'] + 16
    b['p4.ignis_end'] = b['p4.met'] + L('p4_met') + 14
    # P4_PROPHECY: walking, "Ten thousand years..."; the fog rolls in under it; "...the prophecy..."; the moon; the smile
    b['p4.years'] = 8
    b['p4.fog'] = b['p4.years'] + 30
    b['p4.prophecy'] = b['p4.years'] + L('p4_years') + 14
    b['p4.moon'] = b['p4.prophecy'] + L('p4_prophecy') + 4
    b['p4.lunaknows'] = b['p4.moon'] + 26
    b['p4.prophecy_end'] = b['p4.lunaknows'] + L('p4_lunaknows') + 14
    # P4_BRETHREN: the arms open to the stands; the two lines; the moon turns on the last word
    b['p4.brethren'] = 14
    b['p4.grant'] = b['p4.brethren'] + L('p4_brethren') + 10
    b['p4.turn'] = b['p4.grant'] + L('p4_grant') - 4
    b['p4.brethren_end'] = b['p4.turn'] + 40
    # P4_HALFBAKED: the line in the green glow; the burst; the flash (the arena turns behind it); the reveal; the title
    b['p4.halfbaked'] = 12
    b['p4.burst'] = b['p4.halfbaked'] + L('p4_halfbaked') + 10
    b['p4.flash'] = b['p4.burst'] + 30
    b['p4.clear'] = b['p4.flash'] + 26
    b['p4.title'] = b['p4.clear'] + 8
    b['p4.halfbaked_end'] = b['p4.clear'] + 80
    # P4_TALKING
    b['p4.talking'] = 6
    b['p4.talking_end'] = b['p4.talking'] + L('p4_talking') + 18
    # P4_HONOR: soft, to the band; the singer nods; they strike up (the song's downbeat); the trumpets
    b['p4.honor'] = 10
    b['p4.nod'] = b['p4.honor'] + L('p4_honor') + 4
    b['p4.song'] = b['p4.nod'] + 16
    b['p4.honor_end'] = b['p4.song'] + 44
    # P4_MUCHACHAS: "Where are the muchachas?", the clap, the dancers rise, he goes to one and twirls her
    b['p4.muchachas'] = 4
    b['p4.clap'] = b['p4.muchachas'] + L('p4_muchachas') + 2
    b['p4.rise'] = b['p4.clap'] + 10
    b['p4.twirl'] = b['p4.rise'] + 52
    b['p4.twirl_line'] = b['p4.twirl'] + 14
    b['p4.dip'] = b['p4.twirl'] + 58                 # the twirl, then a dip, then a few steps together
    b['p4.muchachas_end'] = b['p4.twirl'] + 110
    # P4_GOODTIME: round to you; "COME ON!!! SHOW ME A GOOD TIME!!!"; the stands erupt; he holds it till the first sung word
    b['p4.goodtime'] = 40
    b['p4.erupt'] = b['p4.goodtime'] + L('p4_goodtime')
    # passes of the song's brass intro from the downbeat to the fight: the rest of the honor, the muchachas, the line, a beat
    try:
        intro = json.load(open(os.path.join(ASSETS, 'song/persona_ideal.json')))['segments'][0]['length']
        need = (b['p4.honor_end'] - b['p4.song']) + b['p4.muchachas_end'] + b['p4.erupt'] + 30
        b['p4.passes'] = max(1, int(math.ceil(need / (intro * 20))))
    except (OSError, KeyError, IndexError):
        b['p4.passes'] = 2
    return b


def build(meta):
    out_dir = os.path.join(ASSETS, 'sounds/voice/boss2')
    os.makedirs(out_dir, exist_ok=True)
    data = {'rate': RATE, 'lines': {}}
    lang_path = os.path.join(ASSETS, 'lang/en_us.json')
    lang = json.load(open(lang_path, encoding='utf-8'))
    sounds = read_sounds()
    built_path = os.path.join(ASSETS, 'voice/boss2.json')
    built = json.load(open(built_path, encoding='utf-8')).get('lines', {}) if os.path.exists(built_path) else {}
    # lines with no takes here (a fresh checkout has no cache) keep what is built; only lines gone from LINES go
    for k in [k for k in sounds if k.startswith('voice.boss2.') and k[len('voice.boss2.'):] not in LINES]:
        del sounds[k]
    for k in [k for k in lang if k.startswith('speech.papon.boss2.') and k[len('speech.papon.boss2.'):] not in LINES]:
        del lang[k]
    report = []
    for line, (text, mood) in LINES.items():
        info = meta.get(line)
        if line not in FRESH and line in built and LINES[line][0] == lang.get('speech.papon.boss2.' + line):
            data['lines'][line] = built[line]               # as built (its audio untouched)
            continue
        if not info or any(info.get(k) != v for k, v in signature(line).items()):
            if line in built and LINES[line][0] == lang.get('speech.papon.boss2.' + line):
                data['lines'][line] = built[line]           # as built before (no takes in this checkout)
            else:
                print('  (no takes for %s yet)' % line)
            continue
        k, key = pick(line, info)
        if k is None:
            continue
        y, sr = load_mono(take_path(line, k))
        y = loudness(trim(y, sr), sr)
        with tempfile.TemporaryDirectory() as tmp:
            wav = os.path.join(tmp, 'l.wav')
            soundfile.write(wav, y, sr, subtype='PCM_16')
            mouth = rhubarb(wav, text)
        env, pitch, acc = tracks(y, sr)
        soundfile.write(os.path.join(out_dir, line + '.ogg'), y, sr, format='OGG', subtype='VORBIS')
        sounds['voice.boss2.' + line] = {'sounds': [{'name': 'papon:voice/boss2/' + line}]}
        lang['speech.papon.boss2.' + line] = text
        data['lines'][line] = {'len': round(len(y) / sr, 3), 'mood': mood, 'mouth': mouth,
                               'env': [round(float(v), 3) for v in env], 'pitch': [round(float(v), 2) for v in pitch],
                               'accent': [round(float(v), 3) for v in acc]}
        e = info['takes'][k]['expr2']
        report.append('%-10s %5.2fs take %d  range %4.1f dyn %4.1f pauses %d bright %4d%s | %s' % (
            line, len(y) / sr, k, e['range'], e['dyn'], e['pauses'], e['bright'], '  CLIPPED' if key[0] else '', info['takes'][k]['heard']))
    json.dump(meta, open(meta_path(), 'w'), indent=1)          # with the expressiveness, for next time
    data['beats'] = beats({k: v['len'] for k, v in data['lines'].items()})
    report.append('beats: ' + json.dumps(data['beats']))
    os.makedirs(os.path.join(ASSETS, 'voice'), exist_ok=True)
    with open(built_path, 'w', newline='\n') as f:
        json.dump(data, f, separators=(',', ':'))
    write_sounds(sounds)
    with open(lang_path, 'w', newline='\n', encoding='utf-8') as f:
        json.dump(lang, f, indent=2, ensure_ascii=False)
        f.write('\n')
    print('\n'.join(report))
    print(len(data['lines']), 'lines built')


def built_lines():
    """Lines already built into voice/boss2.json with their text unchanged (not taken again without --redo)."""
    path = os.path.join(ASSETS, 'voice/boss2.json')
    if not os.path.exists(path):
        return set()
    lang = json.load(open(os.path.join(ASSETS, 'lang/en_us.json'), encoding='utf-8'))
    return {k for k in json.load(open(path, encoding='utf-8')).get('lines', {})
            if k in LINES and lang.get('speech.papon.boss2.' + k) == LINES[k][0]}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--redo', default='', help='comma-separated lines to take again')
    ap.add_argument('--build', action='store_true', help='only build from cached takes')
    ap.add_argument('--more', default='', help='comma-separated lines to give TAKES more takes (keeping theirs)')
    a = ap.parse_args()
    meta = json.load(open(meta_path(), encoding='utf-8')) if os.path.exists(meta_path()) else {}
    if not a.build:
        redo = {s for s in a.redo.split(',') if s}
        built = built_lines()
        todo = [l for l in LINES
                if l in redo or (l not in meta and l not in built)
                or (l in meta and (any(meta[l].get(k) != v for k, v in signature(l).items())
                                   or not all(os.path.exists(take_path(l, k)) for k in range(len(meta[l]['takes'])))))]
        if todo:
            meta = generate(todo)
        if a.more:
            meta = generate([s for s in a.more.split(',') if s], more=True)
    build(meta)


def rebeat():
    """Only the cutscene's beats again, from the lines already in voice/boss2.json (no recording)."""
    path = os.path.join(ASSETS, 'voice/boss2.json')
    data = json.load(open(path))
    data['beats'] = beats({k: v['len'] for k, v in data['lines'].items()})
    with open(path, 'w', newline='\n') as f:
        json.dump(data, f, separators=(',', ':'))
    print('beats:', json.dumps(data['beats']))


if __name__ == '__main__':
    if '--beats' in sys.argv:
        rebeat()
    else:
        main()
