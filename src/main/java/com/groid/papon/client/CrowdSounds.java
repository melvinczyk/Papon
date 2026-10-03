package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.boss.ColiseumCrowdEntity;
import com.groid.papon.boss.CrowdMood;
import com.groid.papon.boss.Song;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * The noise of the stands (tools/gen_crowd.py): a murmur that swells and drops with the mood, and
 * on every change the reaction itself (the roar, the boos, the gasp, the swoon); while they chant,
 * "PA-PÓN!" on every other beat of the song, and while they clap, a clap on every beat. You hear
 * them all round you (no position): you are standing in the middle of them.
 */
public final class CrowdSounds {
    private static final double RANGE = 110;
    @Nullable private static Bed bed;
    private static int lastMood = -1;
    private static int lastCrowd = -1;
    private static long lastBeat = Long.MIN_VALUE;

    private CrowdSounds() {}

    private static SoundEvent ev(String name) {
        return SoundEvent.createVariableRangeEvent(PaponMod.id(name));
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ColiseumCrowdEntity crowd = mc.level == null || mc.player == null ? null
                : mc.level.getEntitiesOfClass(ColiseumCrowdEntity.class, mc.player.getBoundingBox().inflate(RANGE), e -> true).stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(mc.player))).orElse(null);
        if (crowd == null) {
            if (bed != null) bed.want = 0;
            lastMood = -1;
            return;
        }
        float near = (float) Math.max(0, 1 - Math.max(0, Math.sqrt(crowd.distanceToSqr(mc.player)) - 50) / 60);
        if (bed == null || bed.isStopped()) {
            bed = new Bed();
            mc.getSoundManager().play(bed);
        }
        CrowdMood mood = crowd.mood();
        bed.want = near * switch (mood) {
            case HUSH, MOURN -> 0.18F;
            case ROAR, GASP -> 0.75F;
            case CHANT, CLAP, WAVE -> 0.5F;
            default -> 0.55F;
        };
        if (crowd.getId() != lastCrowd) {
            lastCrowd = crowd.getId();
            lastMood = mood.ordinal();
        }
        if (mood.ordinal() != lastMood) {
            lastMood = mood.ordinal();
            String one = switch (mood) {
                case ROAR -> "crowd.roar";
                case BOO -> "crowd.boo";
                case GASP, MOURN -> "crowd.gasp";
                case SWOON -> "crowd.swoon";
                default -> null;
            };
            if (one != null) play(mc, one, near, 1.0F);
        }
        // on the beat: the chant and the claps
        Song song = Song.get();
        double t = SongPlayer.songTime(0);
        double beat = song != null && !Double.isNaN(t) ? song.beatAt(t) : mc.level.getGameTime() / 9.4;
        long b = (long) Math.floor(beat);
        if (b != lastBeat) {
            boolean first = lastBeat == Long.MIN_VALUE;
            lastBeat = b;
            if (!first && mood == CrowdMood.CHANT && b % 2 == 0) play(mc, "crowd.chant", near * 0.9F, 1.0F);
            if (!first && mood == CrowdMood.CLAP) play(mc, "crowd.clap", near * 0.7F, 0.95F + mc.level.random.nextFloat() * 0.1F);
        }
    }

    private static void play(Minecraft mc, String name, float volume, float pitch) {
        volume *= VoiceDuck.get();
        if (volume <= 0.01F) return;
        mc.getSoundManager().play(new SimpleSoundInstance(ev(name).getLocation(), SoundSource.AMBIENT, volume, pitch,
                SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
    }

    /** The murmur of the stands, looping, its level following {@link #want}. */
    private static final class Bed extends AbstractTickableSoundInstance {
        float want = 0.5F;
        private float level = 0.0F;

        Bed() {
            super(ev("crowd.ambience"), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            looping = true;
            delay = 0;
            relative = true;
            attenuation = Attenuation.NONE;
            volume = 0.001F;
            x = y = z = 0;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            level += (want - level) * 0.08F;
            if (want <= 0 && level < 0.01F) {
                stop();
                return;
            }
            volume = Math.max(0.001F, level * VoiceDuck.get());          // under him while he talks
        }
    }
}
