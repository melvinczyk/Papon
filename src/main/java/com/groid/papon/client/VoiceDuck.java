package com.groid.papon.client;

import com.groid.papon.boss.MariachiEntity;
import com.groid.papon.boss.PaponBossEntity;
import com.groid.papon.boss.VoiceLines;
import com.groid.papon.entity.Speech;
import net.minecraft.client.Minecraft;

/**
 * The fight's mix, in order of who must be heard (2026-10-05, the user: "Papon loudest, then the music, then the
 * mariachi's voices, then the sound effects"; the music had got too quiet):
 *
 *   1. Chef Papon's voice      never ducked, full level (Speech: the Voice slider)
 *   2. the music               {@link #music}: only a little under him (it sat at 18% through every speech of his, and
 *                              phase four's cutscene is mostly speech); the band talking does not touch it
 *   3. the band's voices       played under the music (Speech.BAND), and ducked a little under him
 *   4. the crowd, every effect {@link #get}: well down under him, a little under the band
 *
 * Eased: quick down, slower back up.
 */
public final class VoiceDuck {
    /** Effects and the crowd under his voice, and under the band's. */
    private static final float UNDER = 0.22F, UNDER_BAND = 0.6F;
    /** The music under his voice (2026-10-06: 0.5 lost the excitement through his cutscenes; his lines still read over it). */
    private static final float MUSIC_UNDER = 0.8F;
    private static final double RANGE = 64;

    private static float level = 1F, musicLevel = 1F, bandLevel = 1F;
    private static long ticked = Long.MIN_VALUE;

    private VoiceDuck() {}

    /** The effects and the crowd: 1 normally, down to {@link #UNDER} while he talks, {@link #UNDER_BAND} while the band does. */
    public static float get() {
        update();
        return level;
    }

    /** The music: 1 normally, down to {@link #MUSIC_UNDER} while he talks. */
    public static float music() {
        update();
        return musicLevel;
    }

    /** The band's voices: down a little while he talks over them. */
    public static float band() {
        update();
        return bandLevel;
    }

    private static void update() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        long now = mc.level.getGameTime();
        if (now == ticked) return;
        ticked = now;
        boolean talking = false, band = false;
        for (PaponBossEntity b : mc.level.getEntitiesOfClass(PaponBossEntity.class, mc.player.getBoundingBox().inflate(RANGE), e -> true)) {
            VoiceLines.Line l = b.talking();
            if (l != null && b.talkTime(0) >= -0.1 && b.talkTime(0) <= l.length() + 0.3) talking = true;
        }
        for (MariachiEntity m : mc.level.getEntitiesOfClass(MariachiEntity.class, mc.player.getBoundingBox().inflate(RANGE), e -> true)) {
            if (Speech.voiced(m.getSpeech())) band = true;
        }
        float want = talking ? UNDER : band ? UNDER_BAND : 1F;
        level += (want - level) * (want < level ? 0.6F : 0.1F);
        float mw = talking ? MUSIC_UNDER : 1F;
        musicLevel += (mw - musicLevel) * (mw < musicLevel ? 0.3F : 0.12F);
        float bw = talking ? 0.6F : 1F;
        bandLevel += (bw - bandLevel) * (bw < bandLevel ? 0.5F : 0.1F);
    }
}
