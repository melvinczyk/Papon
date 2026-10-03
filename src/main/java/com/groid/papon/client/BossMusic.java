package com.groid.papon.client;

import com.groid.papon.PaponConfig;
import com.groid.papon.boss.PaponBossEntity;
import com.groid.papon.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The boss music (music/boss.ogg, a mariachi piece that swings between slow and fast, synthesized
 * by tools/gen_sounds.py). Plays while a fighting Chef Papon is within {@link #RANGE}, on the
 * Jukebox/Note Blocks slider, with vanilla music held off; fades out when the fight ends or you
 * leave.
 */
public final class BossMusic {
    private static final double RANGE = 72;
    @Nullable private static Track track;

    private BossMusic() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean want = mc.level != null && mc.player != null && PaponConfig.BOSS_MUSIC.get() && fightNearby(mc);
        PaponBossEntity boss = mc.level == null || mc.player == null ? null : nearest(mc);
        if (track != null) {
            // the phase-two cutscene: the music cuts out, fast; in his dimension it comes back hotter
            track.cut = boss != null && boss.getBossState().silent();
            track.setPitch(boss != null && boss.isRisen() ? 1.12F : 1.0F);   // risen with no coliseum to go to
        }
        if (boss != null && boss.getBossState().silent()) {
            mc.getMusicManager().stopPlaying();
            want = false;
        }
        if (SongPlayer.isActive() || (boss != null && boss.inColiseum())) {
            // in his coliseum the band plays (SongPlayer); before they do, only the crowd
            want = false;
            if (track != null) track.cut = true;
        }
        if (want) {
            mc.getMusicManager().stopPlaying();
            if (track == null || track.isStopped()) {
                track = new Track();
                mc.getSoundManager().play(track);
            }
        }
        if (track != null) track.want = want;
    }

    @Nullable
    private static PaponBossEntity nearest(Minecraft mc) {
        AABB box = mc.player.getBoundingBox().inflate(RANGE);
        return mc.level.getEntitiesOfClass(PaponBossEntity.class, box, PaponBossEntity::isAlive).stream()
                .min(java.util.Comparator.comparingDouble(b -> b.distanceToSqr(mc.player))).orElse(null);
    }

    private static boolean fightNearby(Minecraft mc) {
        AABB box = mc.player.getBoundingBox().inflate(RANGE);
        return !mc.level.getEntitiesOfClass(PaponBossEntity.class, box,
                b -> b.isAlive() && b.getBossState().fighting()).isEmpty();
    }

    private static final class Track extends AbstractTickableSoundInstance {
        boolean want = true;
        boolean cut;
        private float level = 0.01F;

        Track() {
            super(ModSounds.BOSS_MUSIC.get(), SoundSource.RECORDS, SoundInstance.createUnseededRandom());
            looping = true;
            delay = 0;
            relative = true;
            attenuation = Attenuation.NONE;
            volume = level;
            x = y = z = 0;
        }

        void setPitch(float p) {
            pitch = p;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            level += want ? 0.04F : cut ? -0.15F : -0.02F;
            if (level <= 0) {
                stop();
                return;
            }
            level = Math.min(level, 1F);
            volume = level * 0.85F;
        }
    }
}
