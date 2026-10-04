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
    # (rewritten 2026-10-04 to sound like a man talking, not reading: natural phrasing, a word or two leaned on, the
    # little sounds people make; the bubbles show them as written)
    # ---- the transformation (on the ground)
    'bleed':      ("Heh... heh heh. You made me bleed, amigo.", 'pained'),
    'enough':     ("Okay. Okay, okay... enough playing around.", 'smug'),
    'salt':       ("Just a little bit of salt...", 'warm'),
    'golden':     ("Mmm! And nice and golden.", 'warm'),
    # ---- El Coliseo Papa
    'listos':     ("Are you READY?!", 'shout'),
    'musica':     ("Music, maestro!", 'shout'),
    'maestro':    ("Maestro? ...Maestro?!", 'surprised'),
    'despierten': ("Wake UP, you lazy bums! We have an audience!", 'angry'),
    'momentito':  ("One moment, amigo. Just one little moment.", 'warm'),
    'ay':         ("Oh, no. No, no, no!", 'surprised'),
    'gordo':      ("You too, Gordo! Get UP!", 'angry'),
    'apology':    ("My apologies, amigo. Such poor hospitality, making my guest wait like that.", 'warm'),
    'resume':     ("So... where were we?", 'sly'),
    # ---- the fight (his old lines, in English now)
    'salud':      ("Cheers, amigo!", 'shout'),
    'intro':      ("Nobody sees my bald head and LIVES to tell!", 'angry'),
    'taunt0':     ("I'm going to make tacos out of you, amigo.", 'sly'),
    'taunt1':     ("My bald head shines brighter than your future!", 'smug'),
    'taunt2':     ("Come here, you coward!", 'angry'),
    'taunt3':     ("Ahh, this is pure salsa!", 'sly'),
    'keys':       ("Now where did I leave my keys? Ah! Here they are!", 'surprised'),
    'handle':     ("One little sip for the road...", 'sly'),
    'ram':        ("Out of my WAY!", 'shout'),
    'again':      ("Again! Again!", 'shout'),
    'reverse':    ("And now in reverse!", 'shout'),
    'stumble':    ("Whoa... who moved the floor?", 'drunk'),
    'stolen':     ("Hey! That's MY van!", 'angry'),
    'phase':      ("Now I'm really mad!", 'angry'),
    'death':      ("My hair... my beautiful hair...", 'sad'),
    'kick':       ("Here it comes!", 'shout'),
    'bail':       ("This is my stop!", 'shout'),
    'door':       ("Ooh, mind the door!", 'sly'),
    'meteor':     ("It's raining potatoes!", 'shout'),
    'catch':      ("Catch!", 'shout'),
    'foil':       ("My tinfoil! You animal!", 'angry'),
    'grab':       ("Gotcha, amigo.", 'sly'),
    'torch':      ("Nice and golden...", 'warm'),
    'season':     ("And a pinch of salt!", 'sly'),
    'toss':       ("On the grill!", 'shout'),
    'dodge':      ("Ha! You didn't even touch me!", 'smug'),
    'burrito':    ("One burrito... to go!", 'sly'),
    'borracho':   ("I'm fine! I'm fine, I'm fine!", 'drunk'),
    'haymaker':   ("I told you I was fine!", 'drunk'),
    'brick':      ("You drive. I hit.", 'sly'),
    'qte0':       ("Your last supper, amigo.", 'sly'),
    'qte1':       ("Last call! Open wide!", 'shout'),
    'qte2':       ("Mashed tourist!", 'shout'),
    'qte_broken': ("Ow! Ow, my back!", 'pained'),
    'gravity':    ("Everything revolves around the potato!", 'shout'),
    'echoes':     ("The three chefs!", 'shout'),
    # ---- the Taco Truck: Papon the Taquero (him in disguise), said as speech.papon.boss2.vendor_*
    'vendor_order0':     ("Coming right up!", 'warm'),
    'vendor_order1':     ("It's coming out right now, my friend!", 'warm'),
    'vendor_order2':     ("But of course, amigo!", 'warm'),
    'vendor_serve0':     ("Enjoy your meal!", 'warm'),
    'vendor_serve1':     ("Here you go, amigo!", 'warm'),
    'vendor_serve2':     ("Enjoy it, my friend!", 'warm'),
    'vendor_inspect':    ("Ooh, a coupon? Let me see, let me see...", 'warm'),
    'vendor_suspicious': ("Hmm... this doesn't look right.", 'sly'),
    'vendor_fraud':      ("A fake coupon?! Fraud! Nobody steals MY tacos!", 'angry'),
    'vendor_exposed':    ("Heh. Heh heh heh.", 'sly'),
    'vendor_unmask':     ("You want the truth? Here's the truth!", 'angry'),
    'vendor_realize':    ("Wait... no, no. Don't look!", 'surprised'),
    'vendor_rage':       ("What are you LOOKING at?!", 'shout'),
    # ---- his charm in the coliseum (phase-two taunts and reactions)
    'charm0':     ("Come on, amigo. Dance with me.", 'sly'),
    'charm1':     ("Listen to them. They love me!", 'smug'),
    'charm2':     ("Ay, you're making me sweat, and I'm already baked!", 'warm'),
    'charm3':     ("Every great potato needs a worthy audience.", 'smug'),
    'charm4':     ("Don't worry, I'll make this quick. And beautiful.", 'sly'),
    'charm5':     ("Olé!", 'shout'),
    'hit0':       ("Ah! Not the face!", 'pained'),
    'hit1':       ("You'll pay for that. In tacos.", 'angry'),
    'landed0':    ("Gracias, gracias. You're too kind.", 'smug'),
    'landed1':    ("And the crowd goes wild!", 'shout'),
    # phase three: half health, the band's song is not his style; he takes the guitar and shows them
    'notstyle':   ("This is NOT my style! Who do you think I am?!", 'angry'),
    'realplay':   ("Let me show you how to really play.", 'sly'),
    'gimme':      ("Give me that.", 'angry'),
    'mystyle':    ("Now THAT is more my style. Learn, muchachos.", 'smug'),
    'go':         ("What are you waiting for?! Go!", 'shout'),
    'dance':      ("Now... let's dance.", 'cold'),
    'felt':       ("Mmm... I haven't felt like this in a long time.", 'menace'),
    # (2026-10-04) the knockback: beaten, getting up; the noise of the band in his ears; the power after the ascension;
    # the car before he crushes it
    'p3_hurt':    ("Ugh... okay. That one hurt.", 'pained'),
    'p3_noise':   ("What... what is that noise?", 'contempt'),
    'p3_power':   ("Whoa. Whoa, whoa... so much power.", 'surprised'),
    'upgrade':    ("You know what? It's about time I gave this thing an upgrade.", 'menace'),
    # the QTEs: his charm while he has you (the squeeze reacts to how you mash)
    'qte_strong': ("Ay! You're stronger than you look, amigo!", 'surprised'),
    'qte_weak':   ("Shh, shh... just let it happen.", 'sly'),
    'qte_crush':  ("Like an avocado!", 'shout'),
    'qte_slip':   ("You slippery little potato!", 'angry'),
    'qte_quick':  ("Ooh, quick hands, amigo.", 'smug'),
    'qte_slow':   ("Too slow, amigo!", 'smug'),
    'tango_good': ("Ooh, you can dance, amigo!", 'smug'),
    'tango_bad':  ("Two left feet, amigo. Two left feet.", 'smug'),
    'duel_good':  ("Not bad, amigo. Not bad at all!", 'surprised'),
    'duel_bad':   ("Is that a guitar, or a broom?!", 'smug'),
    # the ascension: in pain, the wings tearing out of his back
    'groan1':     ("Nnngh... hhhAAAGH... AAAAAGH!", 'pained'),
    'groan2':     ("Hhhhh... HAAAAAAGH!!!", 'pained'),
    'ascend':     ("RRRAAAAAAAAAAAGH!!!", 'shout'),
    # ---- Ascended Papon's fight (phase three): a demigod's moves, his charm, the crowd in the palm of his hand
    'p3_combo':     ("Feel these guns, amigo!", 'smug'),
    'p3_flash':     ("Too slow!", 'smug'),
    'p3_comet':     ("From the heavens, amigo!", 'shout'),
    'p3_laser':     ("Look into my eyes, amigo.", 'sly'),
    'p3_solar':     ("Behold! The sun of my ancestors!", 'shout'),
    'p3_hide':      ("Hide, hide, little potato. The sun sees everything.", 'sly'),
    'p3_mariachi':  ("Maestro! Something with a little more fire!", 'shout'),
    'p3_duel':      ("A duel, amigo! Guitar against guitar! Let the crowd decide!", 'shout'),
    'p3_duel_won':  ("And that, amigo, is how a legend plays.", 'smug'),
    'p3_duel_lost': ("Impossible... they're cheering for YOU?!", 'surprised'),
    'p3_squeeze':   ("Come here. Let me squeeze you like an avocado.", 'sly'),
    'p3_pinata':    ("Who wants candy?!", 'shout'),
    'p3_braziers':  ("Light up my coliseum!", 'shout'),
    'p3_nova':      ("Feel the heat of a thousand suns, baked into one potato!", 'shout'),
    'p3_siesta':    ("Ay... even a god needs a siesta...", 'pained'),
    'p3_nitro':     ("Get in, amigo! Oh, wait... you're the road.", 'sly'),
    'p3_tango':     ("Now you dance for me.", 'sly'),
    'p3_charm0':    ("Louder! I can't hear my fans!", 'shout'),
    'p3_charm1':    ("They came to see a god tonight, amigo. Not you.", 'smug'),
    'p3_charm2':    ("Every star needs a little background dancer.", 'sly'),
    'p3_charm3':    ("Smile for the cameras, amigo. This is your last show.", 'sly'),
    'p3_charm4':    ("Feel that? That's what perfection feels like.", 'smug'),
    'p3_charm5':    ("Who's your favorite potato?! Say it!", 'shout'),
    'p3_hit0':      ("Ay! Careful with the merchandise!", 'pained'),
    'p3_hit1':      ("You dare touch a god?!", 'angry'),
    'p3_landed0':   ("Did you see that? Did everybody see that?!", 'shout'),
    'p3_landed1':   ("Thank you, thank you. I'm here all night.", 'smug'),
    'p3_low':       ("No, no, no... the show must go on!", 'angry'),
    # (2026-10-04) his new moves
    'p3_sombrero':  ("Hats off to the champion... that's me!", 'shout'),
    'p3_volcan':    ("Even the earth dances for me!", 'shout'),
    'p3_calaveras': ("My ancestors would love to meet you, amigo.", 'sly'),
    'p3_swing':     ("Swing... swing... and break!", 'shout'),
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
}
# sampling: a little warmer than the model's default (0.9), and each take a little different (voice_modal varies it)
GEN = {'temperature': 1.0}

# line -> (text, mood). The text is directed: capitals are stressed, an ellipsis is a pause (one at most, and
# only where a pause belongs: too many and he reads word by word); the bubble shows it as written. No scream:
# the roars under it do that job better than a voice can.
LINES = {
    # ---- the transformation (on the ground)
    'bleed':      ("Heh heh... You made me BLEED, amigo.", 'pained'),
    'enough':     ("Okay, OKAY. Enough playing around.", 'smug'),
    'salt':       ("A little bit of salt...", 'warm'),
    'golden':     ("Mmm, and nice and GOLDEN!", 'warm'),
    # ---- El Coliseo Papa
    'listos':     ("ARE YOU READY?!", 'shout'),
    'musica':     ("MUSIC, MAESTRO!!!", 'shout'),
    'maestro':    ("Maestro? ...MAESTRO?!", 'surprised'),
    'despierten': ("WAKE UP, you lazy bums! We have an AUDIENCE!", 'angry'),
    'momentito':  ("One moment, amigo. Just ONE moment.", 'warm'),
    'ay':         ("Oh, NO!!!", 'surprised'),
    'gordo':      ("YOU TOO, GORDO!!!", 'angry'),
    'apology':    ("My apologies, amigo. Such POOR hospitality... making my guest wait like that.", 'warm'),
    'resume':     ("Now, where were we?", 'sly'),
    # ---- the fight (his old lines, in English now)
    'salud':      ("CHEERS, amigo!!!", 'shout'),
    'intro':      ("NOBODY sees my bald head and LIVES to tell!!!", 'angry'),
    'taunt0':     ("I am going to make TACOS out of YOU!", 'sly'),
    'taunt1':     ("My bald head shines BRIGHTER than your FUTURE!", 'smug'),
    'taunt2':     ("Come HERE, coward!!!", 'angry'),
    'taunt3':     ("This is PURE SALSA!", 'sly'),
    'keys':       ("Where did I leave the keys? AH! Here!", 'surprised'),
    'handle':     ("One little sip for the road...", 'sly'),
    'ram':        ("MAKE WAY!!!", 'shout'),
    'again':      ("AGAIN!!!", 'shout'),
    'reverse':    ("And in REVERSE!!!", 'shout'),
    'stumble':    ("Who moved the FLOOR?", 'drunk'),
    'stolen':     ("HEY!!! MY VAN!!!", 'angry'),
    'phase':      ("NOW I am REALLY MAD!!!", 'angry'),
    'death':      ("My hair... my beautiful HAIR...", 'sad'),
    'kick':       ("HERE IT COMES!!!", 'shout'),
    'bail':       ("THIS is my STOP!!!", 'shout'),
    'door':       ("Mind the DOOR!", 'sly'),
    'meteor':     ("It's RAINING POTATOES!!!", 'shout'),
    'catch':      ("CATCH!!!", 'shout'),
    'foil':       ("My TINFOIL!!! You ANIMAL!", 'angry'),
    'grab':       ("Gotcha, amigo.", 'sly'),
    'torch':      ("Nice and golden...", 'warm'),
    'season':     ("And a PINCH of salt!", 'sly'),
    'toss':       ("ON THE GRILL!!!", 'shout'),
    'dodge':      ("HA! You didn't even TOUCH me!", 'smug'),
    'burrito':    ("One burrito, TO GO!", 'sly'),
    'borracho':   ("I'm FINE!!! I'm FINE!!!", 'drunk'),
    'haymaker':   ("I TOLD you I was FINE!!!", 'drunk'),
    'brick':      ("You drive, I HIT.", 'sly'),
    'qte0':       ("Your last supper, amigo.", 'sly'),
    'qte1':       ("LAST CALL!!! Open WIDE!", 'shout'),
    'qte2':       ("MASHED TOURIST!!!", 'shout'),
    'qte_broken': ("OW!!! My BACK!!!", 'pained'),
    'gravity':    ("EVERYTHING revolves around the POTATO!!!", 'shout'),
    'echoes':     ("The THREE CHEFS!!!", 'shout'),
    # ---- the Taco Truck: Papon the Taquero (him in disguise), said as speech.papon.boss2.vendor_*
    'vendor_order0':     ("Coming RIGHT up!", 'warm'),
    'vendor_order1':     ("It's coming out RIGHT now!", 'warm'),
    'vendor_order2':     ("But of COURSE, amigo!", 'warm'),
    'vendor_serve0':     ("ENJOY your meal!", 'warm'),
    'vendor_serve1':     ("Here you go, amigo!", 'warm'),
    'vendor_serve2':     ("Enjoy it, my friend!", 'warm'),
    'vendor_inspect':    ("A coupon? Let me see, let me SEE...", 'warm'),
    'vendor_suspicious': ("Hmm... this does NOT look right.", 'sly'),
    'vendor_fraud':      ("A FAKE coupon?! FRAUD!!! NOBODY steals MY tacos!", 'angry'),
    'vendor_exposed':    ("Heh. Heh heh. Heh.", 'sly'),
    'vendor_unmask':     ("You want the TRUTH? HERE is the truth!", 'angry'),
    'vendor_realize':    ("Wait. No... DON'T LOOK!", 'surprised'),
    'vendor_rage':       ("WHAT ARE YOU LOOKING AT?!", 'shout'),
    # ---- new: his charm in the coliseum (phase-two taunts and reactions)
    'charm0':     ("Come, amigo. DANCE with me.", 'sly'),
    'charm1':     ("Listen to them. They LOVE me.", 'smug'),
    'charm2':     ("Ay, you are making me sweat, and I am ALREADY baked.", 'warm'),
    'charm3':     ("Every great potato needs a WORTHY audience.", 'smug'),
    'charm4':     ("Do not worry. I will make this quick... and BEAUTIFUL.", 'sly'),
    'charm5':     ("OLÉ!!!", 'shout'),
    'hit0':       ("AH!!! Not the FACE!", 'pained'),
    'hit1':       ("You will PAY for that, in TACOS.", 'angry'),
    'landed0':    ("Gracias, gracias. You are TOO kind.", 'smug'),
    'landed1':    ("And the crowd goes WILD!!!", 'shout'),
    # phase three: half health, the band's song is not his style; he takes the guitar and shows them
    'notstyle':   ("This is NOT my style... who do you think I AM?!", 'angry'),
    'realplay':   ("Let me show you how to REALLY play.", 'sly'),
    'gimme':      ("Give me THAT.", 'angry'),
    'mystyle':    ("THIS... is more my style. Learn, muchachos.", 'smug'),
    'go':         ("What are you WAITING for?! GO!!!", 'shout'),
    'dance':      ("Now... let's DANCE.", 'cold'),
    'felt':       ("Hmmm... I haven't felt this way in a LONG time.", 'menace'),
    # (2026-10-04) the knockback: beaten, getting up; the noise of the band in his ears; the power after the ascension;
    # the car before he crushes it
    'p3_hurt':    ("Hhh... that one... HURT.", 'pained'),
    'p3_noise':   ("What... is that... NOISE?", 'contempt'),
    'p3_power':   ("Whoa, WHOA... so much POWER.", 'surprised'),
    'upgrade':    ("It's about time I gave this thing an UPGRADE.", 'menace'),
    # the QTEs: his charm while he has you (the squeeze reacts to how you mash)
    'qte_strong': ("Ay! You are STRONGER than you look, amigo!", 'surprised'),
    'qte_weak':   ("Shhh... just let it HAPPEN.", 'sly'),
    'qte_crush':  ("Like an AVOCADO!!!", 'shout'),
    'qte_slip':   ("You slippery little POTATO!", 'angry'),
    'qte_quick':  ("Ooh, QUICK hands, amigo.", 'smug'),
    'qte_slow':   ("Too SLOW, amigo!", 'smug'),
    'tango_good': ("Ooh, you can DANCE, amigo!", 'smug'),
    'tango_bad':  ("Two LEFT feet, amigo!", 'smug'),
    'duel_good':  ("Not BAD, amigo... not bad at ALL!", 'surprised'),
    'duel_bad':   ("Is that a GUITAR, or a BROOM?!", 'smug'),
    # the ascension: in pain, the wings tearing out of his back
    'groan1':     ("Nnngh... hhhAAAGH... AAAAAGH!", 'pained'),
    'groan2':     ("Hhhhh... HAAAAAAGH!!!", 'pained'),
    'ascend':     ("RRRAAAAAAAAAAAGH!!!", 'shout'),
    # ---- Ascended Papon's fight (phase three): a demigod's moves, his charm, the crowd in the palm of his hand
    'p3_combo':     ("Feel these GUNS, amigo!", 'smug'),
    'p3_flash':     ("Too SLOW!", 'smug'),
    'p3_comet':     ("From the HEAVENS, amigo!!!", 'shout'),
    'p3_laser':     ("Look into my EYES, amigo.", 'sly'),
    'p3_solar':     ("BEHOLD... the SUN of my ancestors!!!", 'shout'),
    'p3_hide':      ("Hide, hide, little potato. The sun sees EVERYTHING.", 'sly'),
    'p3_mariachi':  ("Maestro! Something with a little more FIRE!", 'shout'),
    'p3_duel':      ("A DUEL, amigo! Guitar against guitar! Let the crowd decide!", 'shout'),
    'p3_duel_won':  ("And THAT... is how a legend plays.", 'smug'),
    'p3_duel_lost': ("Impossible... They cheer for YOU?!", 'surprised'),
    'p3_squeeze':   ("Come here. Let me SQUEEZE you like an avocado.", 'sly'),
    'p3_pinata':    ("Who wants CANDY?!", 'shout'),
    'p3_braziers':  ("Light up my COLISEUM!!!", 'shout'),
    'p3_nova':      ("Feel the heat of a THOUSAND suns... baked into ONE potato!!!", 'shout'),
    'p3_siesta':    ("Ay... even a god needs a SIESTA...", 'pained'),
    'p3_nitro':     ("Get in, amigo! Oh, wait... you are the ROAD.", 'sly'),
    'p3_tango':     ("Now you DANCE for me.", 'sly'),
    'p3_charm0':    ("LOUDER! I cannot hear my FANS!", 'shout'),
    'p3_charm1':    ("They came to see a GOD tonight, amigo. Not you.", 'smug'),
    'p3_charm2':    ("Every star needs a little background dancer.", 'sly'),
    'p3_charm3':    ("Smile for the cameras, amigo. This is your LAST show.", 'sly'),
    'p3_charm4':    ("Feel that? THAT is what perfection feels like.", 'smug'),
    'p3_charm5':    ("Who is your favorite potato?! SAY IT!!!", 'shout'),
    'p3_hit0':      ("Ay! Careful with the MERCHANDISE!", 'pained'),
    'p3_hit1':      ("You DARE touch a demigod?!", 'angry'),
    'p3_landed0':   ("Did you see that?! Did EVERYBODY see that?!", 'shout'),
    'p3_landed1':   ("Thank you, thank you. I am here all NIGHT.", 'smug'),
    'p3_low':       ("No, no, no... the show must go ON!", 'angry'),
    # (2026-10-04) his new moves
    'p3_sombrero':  ("Hats OFF to the champion... ME!!!", 'shout'),
    'p3_volcan':    ("The EARTH itself dances for me!!!", 'shout'),
    'p3_calaveras': ("My ancestors would like to MEET you, amigo.", 'sly'),
    'p3_swing':     ("Swing... swing... and BREAK!!!", 'shout'),
}

# what the voice model is given to say, where the written line reads two ways ("lives": he said it like "alive")
SPOKEN = {
    'intro': "Nobody sees my bald head and livs to tell!",
    'upgrade': "It's about time I gave this thing an upgrade.",     # in capitals it came out "upi-grad"
}

NO_CHECK = {'charm5', 'vendor_exposed', 'groan1', 'groan2', 'ascend'}          # Whisper cannot judge a scream or a lone word


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
    # the coliseum: down he comes (36), "ARE... YOU... READY?!", then the point at the band stand: "MUSIC, MAESTRO!!!"
    b['arrive.listos'] = 42
    b['arrive.musica'] = max(74, b['arrive.listos'] + L('listos') + 22)
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
    # (2026-10-04) cutaways while he groans (the band clutching each other) and after the roar (the stands on their feet,
    # screaming; the band backing away from him)
    b['ascend.cut'] = b['ascend.groan1'] + max(10, L('groan1') // 2 - 10)
    b['ascend.crowd'] = b['ascend.roar'] + L('ascend') + 4
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
    # (2026-10-04) first, a hand on its roof: "It's about time I gave this thing an UPGRADE."
    b['crush.upgrade'] = 6
    b['crush.lifted'] = b['crush.upgrade'] + L('upgrade') + 14
    b['crush.squeeze'] = b['crush.lifted'] + 4
    b['crush.ball'] = b['crush.squeeze'] + 24
    b['crush.reform'] = b['crush.ball'] + 10
    b['crush.down'] = b['crush.reform'] + 14
    b['crush.rev'] = b['crush.down'] + 10
    b['crush.end'] = b['crush.rev'] + 20
    # he whirls round on the band: "What are you WAITING for?! GO!!!"
    b['go.line'] = 8
    b['go.end'] = b['go.line'] + L('go') + 4
    # the singer's pickup, a cappella, then the band crashes in and the fire comes back; and at the very end, very
    # quietly: "Now... let's DANCE."
    b['dance.drop'] = int(round(pickup * 20))
    b['dance.line'] = b['dance.drop'] + 34
    b['dance.end'] = b['dance.line'] + L('dance') + 16
    return b


def build(meta):
    out_dir = os.path.join(ASSETS, 'sounds/voice/boss2')
    os.makedirs(out_dir, exist_ok=True)
    data = {'rate': RATE, 'lines': {}}
    lang_path = os.path.join(ASSETS, 'lang/en_us.json')
    lang = json.load(open(lang_path, encoding='utf-8'))
    sounds_path = os.path.join(ASSETS, 'sounds.json')
    sounds = json.load(open(sounds_path, encoding='utf-8'))
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
    with open(sounds_path, 'w', newline='\n', encoding='utf-8') as f:
        json.dump(sounds, f, indent=2, ensure_ascii=False)
        f.write('\n')
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
