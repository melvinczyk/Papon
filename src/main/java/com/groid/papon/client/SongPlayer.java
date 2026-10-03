package com.groid.papon.client;

import com.groid.papon.PaponConfig;
import com.groid.papon.PaponMod;
import com.groid.papon.boss.BandState;
import com.groid.papon.boss.BossState;
import com.groid.papon.boss.MariachiEntity;
import com.groid.papon.boss.PaponBossEntity;
import com.groid.papon.boss.Song;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * The band plays "Canción del Mariachi" through the whole of phase two, and it follows the fight.
 *
 * The song is cut into bar-aligned segments (tools/gen_song.py, {@link Song}), each tagged low, mid
 * or high. It starts on the downbeat the server set when the singer counted in
 * ({@link MariachiEntity#songStart()}), plays the guitar intro twice (the close-up, his apology), and
 * from then on, at the end of every segment, picks the next: the next one in the song while that
 * suits the fight, otherwise the nearest one that does: the climax when he is on his last legs or
 * doing his worst, the breakdown or a guitar break while he is staggered, and so on round and
 * round, as long as the fight lasts. His fall gets the song's own ending. Each segment carries a
 * short lead-in and tail, so the joins overlap and crossfade.
 *
 * {@link #songTime} (where in the recording we are) drives the singer's lips and the band's hands
 * (MariachiModel) and the crowd's clapping and chanting.
 */
public final class SongPlayer {
    private static final double RANGE = 96;

    @Nullable private static Song.Segment current;
    /** Client game tick (fractional) the current segment's first beat falls on. */
    private static double currentAt;
    @Nullable private static Segment playing;
    private static int introPasses;
    private static int runStart;
    private static boolean ending;
    private static boolean active;

    private SongPlayer() {}

    public static boolean isActive() {
        return active && current != null;
    }

    /** Seconds into the recording now (NaN when the song is not playing). */
    public static double songTime(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (!isActive() || mc.level == null) return Double.NaN;
        double e = (mc.level.getGameTime() + partial - currentAt) / 20.0;
        if (e < -current.pre()) return Double.NaN;
        return current.start() + Math.min(e, current.length() + 0.2);
    }

    /** How hard the music hits right now, 0..1 (0 when it is not playing). */
    public static float energy() {
        return isActive() ? current.energy() : 0F;
    }

    @Nullable
    public static Song.Segment segment() {
        return isActive() ? current : null;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        Song song = Song.get();
        if (mc.level == null || mc.player == null || song == null || !PaponConfig.BOSS_MUSIC.get()) {
            stop();
            return;
        }
        MariachiEntity singer = singer(mc);
        if (singer == null) {
            stop();
            return;
        }
        long now = mc.level.getGameTime();
        if (singer.songStart() != runStart) {
            stop();
            runStart = singer.songStart();
        }
        active = true;
        mc.getMusicManager().stopPlaying();
        PaponBossEntity boss = boss(mc);
        if (boss != null && boss.getBossState() == BossState.DEATH && !ending) {
            // his fall: the song's own last line and final hit, right now
            Song.Segment end = song.byId("ending");
            if (end != null) {
                if (playing != null) playing.fadeOut();
                ending = true;
                begin(mc, end, now + end.pre() * 20);
            }
            return;
        }
        if (current == null) {
            long first = unwrap(runStart, now);
            Song.Segment intro = song.intro();
            double late = (now - first) / 20.0;
            if (late < -intro.pre() - 0.05) return;                       // still counting in
            if (late > intro.length() * Song.introPasses()) {
                // joined (or came back) mid-song: straight in with a segment that suits the fight
                introPasses = Song.introPasses();
                Song.Segment s = choose(song, intro, boss);
                begin(mc, s, now + s.pre() * 20);
            } else {
                int pass = (int) Math.max(0, Math.floor(late / intro.length()));
                introPasses = pass;
                begin(mc, intro, first + pass * intro.length() * 20);
            }
            return;
        }
        if (ending) return;
        double endAt = currentAt + current.length() * 20;
        Song.Segment next = choose(song, current, boss);
        if (now >= endAt - next.pre() * 20 - 0.5) begin(mc, next, endAt);
    }

    private static void begin(Minecraft mc, Song.Segment s, double at) {
        if (s.id().equals("intro")) introPasses++;
        current = s;
        currentAt = at;
        playing = new Segment(s);
        mc.getSoundManager().play(playing);
    }

    /** The next segment after {@code cur} for how the fight is going. */
    private static Song.Segment choose(Song song, Song.Segment cur, @Nullable PaponBossEntity boss) {
        if (cur.id().equals("intro") && introPasses < Song.introPasses()) return cur;
        int n = song.segments.size();
        Song.Segment natural = null;
        for (int k = 1; k <= n; k++) {
            Song.Segment s = song.segments.get((cur.index() + k) % n);
            if (!s.level().equals("intro") && !s.level().equals("end")) {
                natural = s;
                break;
            }
        }
        if (natural == null) return cur;
        String want = wanted(boss);
        // mid: just let the song run (its breaks and its build are part of it)
        if (want.equals("mid") || natural.level().equals(want)) return natural;
        Song.Segment best = null;
        int bestD = Integer.MAX_VALUE;
        for (Song.Segment s : song.segments) {
            if (!s.level().equals(want) || s == cur) continue;
            int d = Math.floorMod(s.index() - cur.index(), n);
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        return best != null ? best : natural;
    }

    /** How hard the music should hit for the fight as it is. */
    private static String wanted(@Nullable PaponBossEntity boss) {
        if (boss == null) return "low";
        BossState s = boss.getBossState();
        float hp = boss.getHealth() / boss.getMaxHealth();
        if (s == BossState.STAGGER || s == BossState.EXIT_STUMBLE || s == BossState.QTE_BROKEN || s == BossState.GUARD) return "low";
        if (hp < 0.35F || s.unstoppable() || s == BossState.DRIVING || s == BossState.P2_METEOR || s == BossState.P2_GRAVITY
                || s == BossState.VAN_THROW || s == BossState.P2_DIVE || s.qte()) return "high";
        return "mid";
    }

    private static void stop() {
        if (playing != null) playing.fadeOut();
        playing = null;
        current = null;
        introPasses = 0;
        ending = false;
        active = false;
    }

    private static long unwrap(int tick31, long now) {
        long base = now & ~0x7FFFFFFFL;
        long t = base | (tick31 & 0x7FFFFFFFL);
        if (t > now + (1L << 30)) t -= 1L << 31;
        else if (t < now - (1L << 30)) t += 1L << 31;
        return t;
    }

    @Nullable
    private static MariachiEntity singer(Minecraft mc) {
        AABB box = mc.player.getBoundingBox().inflate(RANGE);
        return mc.level.getEntitiesOfClass(MariachiEntity.class, box, m -> m.getRole().singer() && m.songStart() != 0
                        && (m.getBandState() == BandState.COUNT || m.getBandState() == BandState.PLAY || m.getBandState() == BandState.BOW)).stream()
                .min(Comparator.comparingDouble(m -> m.distanceToSqr(mc.player))).orElse(null);
    }

    @Nullable
    private static PaponBossEntity boss(Minecraft mc) {
        AABB box = mc.player.getBoundingBox().inflate(RANGE);
        return mc.level.getEntitiesOfClass(PaponBossEntity.class, box, e -> true).stream()
                .min(Comparator.comparingDouble(b -> b.distanceToSqr(mc.player))).orElse(null);
    }

    /** One segment, played once through, from wherever you are (the band is on a PA). */
    private static final class Segment extends AbstractTickableSoundInstance {
        private boolean fading;
        private float level = 1F;

        Segment(Song.Segment s) {
            super(SoundEvent.createVariableRangeEvent(PaponMod.id("music.song." + s.id())), SoundSource.RECORDS,
                    SoundInstance.createUnseededRandom());
            looping = false;
            delay = 0;
            relative = true;
            attenuation = Attenuation.NONE;
            volume = 0.95F;
            x = y = z = 0;
        }

        void fadeOut() {
            fading = true;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            if (!fading) return;
            level -= 0.08F;
            if (level <= 0) {
                stop();
                return;
            }
            volume = 0.95F * level;
        }
    }
}
