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
 * ({@link MariachiEntity#songStart()}), plays the guitar intro three times (the close-up, his apology, the
 * crowd worked up), and
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
    /** The song playing (phase two's or phase three's). */
    @Nullable private static Song song;
    private static int songId = -1;
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

    /** The song now playing (its lip sync, beats, the band's parts); phase two's when nothing plays. */
    @Nullable
    public static Song song() {
        return isActive() && song != null ? song : Song.get();
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
        if (mc.level == null || mc.player == null || Song.get() == null || !PaponConfig.BOSS_MUSIC.get()) {
            stop();
            return;
        }
        PaponBossEntity boss = boss(mc);
        if (boss != null && boss.getBossState().bandStopped()) {
            phaseThree(mc, boss);
            return;
        }
        MariachiEntity singer = singer(mc);
        if (singer == null) {
            stop();
            return;
        }
        long now = mc.level.getGameTime();
        if (singer.songStart() != runStart || singer.songId() != songId) {
            stop();
            runStart = singer.songStart();
            songId = singer.songId();
        }
        Song song = Song.get(songId);
        if (song == null) {
            stop();
            return;
        }
        SongPlayer.song = song;
        active = true;
        mc.getMusicManager().stopPlaying();
        if (boss != null && boss.getBossState() == BossState.DEATH && !ending) {
            // his fall: the song's own last line and final hit, right now
            Song.Segment end = song.byLevel("end");
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
            if (late > intro.length() * passes(song)) {
                // joined (or came back) mid-song: straight in with a segment that suits the fight
                introPasses = passes(song);
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

    /** Passes of the intro before the song moves on: phase two's guitar intro covers his apology; phase three's pickup plays once. */
    private static int passes(Song song) {
        return song == Song.get() ? Song.introPasses() : 1;
    }

    /**
     * Phase three's cutscene: the band's song grinds to a halt as he is knocked out of the air (the record
     * slowing down under it), then silence; in the spotlight his own serenade (the song's opening, his guitar
     * and his voice), left to ring out under "Learn, muchachos"; the band takes the song back from the pickup.
     */
    private static void phaseThree(Minecraft mc, PaponBossEntity boss) {
        BossState st = boss.getBossState();
        mc.getMusicManager().stopPlaying();
        if (st == BossState.P3_SERENADE) {
            Song ps = Song.get(Song.PARA_SIEMPRE);
            Song.Segment seren = ps == null ? null : ps.byId("serenade");
            if (seren == null) return;
            if (current != seren) {
                if (playing != null) playing.fadeOut();
                song = ps;
                songId = Song.PARA_SIEMPRE;
                active = true;
                ending = false;
                long now = mc.level.getGameTime();
                begin(mc, seren, now - boss.ticksInState() + com.groid.papon.boss.VoiceLines.beat("serenade.start", 4));
            }
            return;
        }
        if (current != null && current.id().equals("serenade")) return;      // the last chord rings on under his lines
        if (playing != null && !playing.fading) {
            if (st == BossState.P3_KNOCKBACK) playing.windDown();
            else playing.fadeOut();
        }
        playing = null;
        current = null;
        introPasses = 0;
        ending = false;
        active = false;
        runStart = 0;
    }

    private static void begin(Minecraft mc, Song.Segment s, double at) {
        if (s.level().equals("intro")) introPasses++;
        if (s.id().startsWith("solo") && !ending) {
            // the singer's guitar solo: the server may cut to him for a moment (P2_SOLO)
            PaponBossEntity boss = boss(mc);
            if (boss != null && boss.inColiseum()) com.groid.papon.network.PaponNetwork.solo(boss.getId());
        }
        current = s;
        currentAt = at;
        playing = new Segment(s);
        mc.getSoundManager().play(playing);
    }

    /** The next segment after {@code cur} for how the fight is going. */
    private static Song.Segment choose(Song song, Song.Segment cur, @Nullable PaponBossEntity boss) {
        if (cur.level().equals("intro") && introPasses < passes(song)) return cur;
        int n = song.segments.size();
        Song.Segment natural = null;
        for (int k = 1; k <= n; k++) {
            Song.Segment s = song.segments.get((cur.index() + k) % n);
            if (Song.pickable(s)) {
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

        private int winding = -1;

        Segment(Song.Segment s) {
            super(SoundEvent.createVariableRangeEvent(PaponMod.id((song != null ? song.sounds : "music.song.") + s.id())), SoundSource.RECORDS,
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

        /** The record grinding to a halt: down in pitch as it fades (a second). */
        void windDown() {
            fading = true;
            winding = 0;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            if (winding >= 0) {
                winding++;
                pitch = Math.max(0.5F, 1F - winding * 0.035F);
                level = Math.max(0, 1F - (winding / 22F) * (winding / 22F));
                if (level <= 0) {
                    stop();
                    return;
                }
            } else if (fading) {
                level -= 0.08F;
                if (level <= 0) {
                    stop();
                    return;
                }
            }
            volume = Math.max(0.001F, 0.95F * level * VoiceDuck.get());     // under him while he talks
        }
    }
}
