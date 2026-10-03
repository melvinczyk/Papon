package com.groid.papon.client;

import com.groid.papon.boss.PaponBossEntity;
import com.groid.papon.boss.VoiceLines;
import net.minecraft.client.Minecraft;

/**
 * While Chef Papon says one of his phase-two lines, the band (SongPlayer) and the crowd (CrowdSounds)
 * come down under him, like a mix ducking music under dialogue, and come back up when he is done.
 */
public final class VoiceDuck {
    private static final float UNDER = 0.32F;
    private static final double RANGE = 64;

    private static float level = 1F;
    private static long ticked = Long.MIN_VALUE;

    private VoiceDuck() {}

    /** 1 normally, down to {@link #UNDER} while he talks (eased: quick down, slower back up). */
    public static float get() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return 1F;
        long now = mc.level.getGameTime();
        if (now != ticked) {
            ticked = now;
            boolean talking = false;
            for (PaponBossEntity b : mc.level.getEntitiesOfClass(PaponBossEntity.class, mc.player.getBoundingBox().inflate(RANGE), e -> true)) {
                VoiceLines.Line l = b.talking();
                if (l != null && b.talkTime(0) <= l.length() + 0.15) talking = true;
            }
            float want = talking ? UNDER : 1F;
            level += (want - level) * (want < level ? 0.45F : 0.12F);
        }
        return level;
    }
}
