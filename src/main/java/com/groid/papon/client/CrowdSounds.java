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
    /** The whole crowd, bed and one-shots: the stands under the band and him, not over them. */
    private static final float LOUDNESS = 0.55F;
    @Nullable private static Bed bed;
    private static int lastPulse = -1;
    /** A loud roar swelling the murmur under it for a couple of seconds, 1 .. 0. */
    private static float roarSwell;
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
            lastCrowd = -1;
            return;
        }
        float near = (float) Math.max(0, 1 - Math.max(0, Math.sqrt(crowd.distanceToSqr(mc.player)) - 50) / 60);
        if (bed == null || bed.isStopped()) {
            bed = new Bed();
            mc.getSoundManager().play(bed);
        }
        CrowdMood mood = crowd.mood();
        bed.want = near * LOUDNESS * switch (mood) {
            case HUSH, MOURN -> 0.18F;
            case LIGHTERS -> 0.05F;                 // his serenade: you could hear a pin drop
            case ROAR, FIESTA -> 0.9F;
            case GASP -> 0.75F;
            case CHANT, CLAP, WAVE -> 0.5F;
            default -> 0.55F;
        };
        // the reactions: one for every pulse the boss sends (a roar over a roar too), the loud ones over his voice
        int pulse = crowd.pulseData();
        if (crowd.getId() != lastCrowd) {
            lastCrowd = crowd.getId();
            // a fresh crowd (you just arrived): its last reaction still plays if it is the one going on now
            lastPulse = pulse >= 0 && CrowdMood.byId(pulse & 31) == mood && crowd.moodTicks() < 20 ? -1 : pulse;
        }
        if (pulse != lastPulse) {
            lastPulse = pulse;
            if (pulse >= 0) {
                CrowdMood m = CrowdMood.byId(pulse & 31);
                boolean loud = (pulse & 32) != 0;
                String one = switch (m) {
                    case ROAR, CHEER, FIESTA -> "crowd.roar";
                    case BOO -> "crowd.boo";
                    case GASP, MOURN -> "crowd.gasp";
                    case SWOON -> "crowd.swoon";
                    default -> null;
                };
                if (one != null) {
                    float v = near * (m == CrowdMood.CHEER ? 0.7F : 1.0F) * (loud ? 1.5F : 1.0F);
                    play(mc, one, v, 0.94F + mc.level.random.nextFloat() * 0.12F, loud);
                    if (loud && m == CrowdMood.ROAR) roarSwell = 1F;          // the whole bowl: the bed swells with it
                }
            }
        }
        roarSwell = Math.max(0, roarSwell - 0.02F);
        if (bed != null) bed.want = Math.min(1F, bed.want + roarSwell * 0.35F);
        // on the beat: the chant and the claps
        Song song = SongPlayer.song();
        double t = SongPlayer.songTime(0);
        double beat = song != null && !Double.isNaN(t) ? song.beatAt(t) : mc.level.getGameTime() / 9.4;
        long b = (long) Math.floor(beat);
        if (b != lastBeat) {
            boolean first = lastBeat == Long.MIN_VALUE;
            lastBeat = b;
            if (!first && mood == CrowdMood.CHANT && b % 2 == 0) play(mc, "crowd.chant", near * 0.9F, 1.0F, false);
            if (!first && mood == CrowdMood.FIESTA && b % 2 == 1) play(mc, "crowd.clap", near * 0.6F, 1.05F, false);
            if (!first && mood == CrowdMood.CLAP) play(mc, "crowd.clap", near * 0.7F, 0.95F + mc.level.random.nextFloat() * 0.1F, false);
        }
    }

    /** {@code loud}: the whole bowl on its feet, heard over him talking (ducked only a little). */
    private static void play(Minecraft mc, String name, float volume, float pitch, boolean loud) {
        volume *= (loud ? Math.max(0.75F, VoiceDuck.get()) : VoiceDuck.get()) * LOUDNESS;
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
            volume = Math.max(0.001F, level * Math.max(VoiceDuck.get(), roarSwell));          // under him while he talks
        }
    }
}
