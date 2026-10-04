package com.groid.papon.client;

import com.groid.papon.boss.Song;
import com.groid.papon.network.PaponNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Duelo de Guitarras (phase three): the challenger's screen becomes a guitar-hero fretboard while Ascended Papon shreds
 * in front of them. Four lanes on D F J K; the gems are charted on the beat of the song the band is playing (the song's
 * own beat grid, cut into triplets: it is in 6/8), with the melody's notes, streams, trills and chords, getting denser as
 * it goes; they fall in 0.8 s. A hit is within 75 ms of its note (35 ms: perfect). The rock meter climbs on hits and drops
 * hard on misses; empty, and it is over. Survive the chart with 85% of the notes hit and you win. Timed on the real clock
 * from the moment it starts, lined up with the song where it was then (SongPlayer), so it plays to the music.
 */
final class GuitarDuel {
    static final int[] KEYS = {GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_K};
    private static final String[] NAMES = {"D", "F", "J", "K"};
    private static final int[] COLORS = {0xFF3CD25A, 0xFFE8403A, 0xFFF2D23C, 0xFF3C8CF0};
    /** Seconds of countdown before the first gem can reach the line. */
    private static final double LEAD = 2.2;
    private static final double FALL = 0.8;
    private static final double GOOD = 0.075, PERFECT = 0.035;
    private static final float PASS = 0.85F;

    private record Note(double t, int lane) {}

    private final int bossId;
    private final List<Note> notes = new ArrayList<>();
    private final boolean[] hit;
    private final boolean[] held = new boolean[4];
    private final float[] flash = new float[4];
    private final long startNanos;
    private final double length;
    private int next;                 // first note not yet hit or missed
    private int hits, perfects, misses, combo, best;
    private float meter = 0.6F;
    private boolean over, sent;
    private float shake;
    private String judge = "";
    private float judgeTime = 99;

    GuitarDuel(int bossId, int seed, int lengthTicks, int songId) {
        this.bossId = bossId;
        this.startNanos = System.nanoTime();
        this.length = lengthTicks / 20.0;
        chart(seed, songId);
        hit = new boolean[notes.size()];
    }

    /** Seconds since the duel started (the real clock: input is judged against it, not the game's ticks). */
    private double now() {
        return (System.nanoTime() - startNanos) / 1e9;
    }

    /**
     * The chart: the band's beats from LEAD to the end, in 6/8 three to a beat; the first stretch on the beats and the
     * odd off-beat, then every triplet, then bursts; chords on the bars' downbeats; the lanes a walk that runs in streams
     * and trills, never the same lane three times running.
     */
    private void chart(int seed, int songId) {
        RandomSource r = RandomSource.create(seed);
        Song song = Song.get(songId);
        double songAt = SongPlayer.isActive() ? SongPlayer.songTime(0) : Double.NaN;
        List<Double> beats = new ArrayList<>();
        if (song != null && !Double.isNaN(songAt) && song.beats.length > 4) {
            for (double b : song.beats) {
                double t = b - songAt;
                if (t >= LEAD && t < length - 1.0) beats.add(t);
            }
        }
        if (beats.size() < 8) {
            beats.clear();
            for (double t = LEAD; t < length - 1.0; t += 0.58) beats.add(t);
        }
        int lane = r.nextInt(4), run = 0, dir = r.nextBoolean() ? 1 : -1;
        double span = beats.get(beats.size() - 1) - beats.get(0);
        for (int i = 0; i < beats.size(); i++) {
            double b0 = beats.get(i);
            double b1 = i + 1 < beats.size() ? beats.get(i + 1) : b0 + 0.58;
            double k = span <= 0 ? 0 : (b0 - beats.get(0)) / span;          // 0..1 through the chart
            for (int sub = 0; sub < 3; sub++) {
                // how many of the triplets get a gem: the beat always; the others more often as it goes on
                boolean on = sub == 0 || r.nextFloat() < (k < 0.25 ? 0.3F : k < 0.6 ? 0.75F : 0.95F);
                if (!on) continue;
                double t = b0 + (b1 - b0) * sub / 3.0;
                // the lane: streams (a step each note), trills (back and forth), the odd jump
                float p = r.nextFloat();
                if (p < 0.45F) lane += dir;
                else if (p < 0.7F) lane += (run % 2 == 0 ? dir : -dir);
                else lane = r.nextInt(4);
                if (lane < 0 || lane > 3) {
                    dir = -dir;
                    lane = Mth.clamp(lane, 0, 3);
                }
                if (!notes.isEmpty() && notes.get(notes.size() - 1).lane == lane && run >= 1) lane = (lane + 1 + r.nextInt(3)) % 4;
                run = !notes.isEmpty() && notes.get(notes.size() - 1).lane == lane ? run + 1 : 0;
                notes.add(new Note(t, lane));
                // chords on the bar's downbeat (every third beat in 6/8), more of them late
                if (sub == 0 && i % 3 == 0 && r.nextFloat() < 0.35F + 0.4F * k) notes.add(new Note(t, (lane + 2) % 4));
            }
        }
        // in the last stretch a burst of 16ths between some beats: the shred-off
        for (int i = (int) (beats.size() * 0.75); i < beats.size() - 1; i += 3) {
            double b0 = beats.get(i), b1 = beats.get(i + 1);
            for (int sub = 1; sub < 4; sub++) {
                double t = b0 + (b1 - b0) * sub / 4.0;
                boolean clash = false;
                for (Note n : notes) clash |= Math.abs(n.t - t) < 0.06;
                if (!clash) notes.add(new Note(t, (sub + i) % 4));
            }
        }
        notes.sort((a, b) -> Double.compare(a.t, b.t));
    }

    boolean over() {
        return over;
    }

    /** Every client tick: notes that went past the line unhit are misses; the meter, the end. */
    void tick() {
        if (over) return;
        double t = now();
        while (next < notes.size() && (hit[next] || notes.get(next).t < t - GOOD)) {
            if (!hit[next]) miss();
            next++;
        }
        for (int i = 0; i < 4; i++) flash[i] = Math.max(0, flash[i] - 0.15F);
        shake = Math.max(0, shake - 0.1F);
        if (meter <= 0) finish(false);
        else if (t > length - 0.4 || next >= notes.size() && t > notes.get(notes.size() - 1).t + 0.5) finish(accuracy() >= PASS);
    }

    float accuracy() {
        int judged = hits + misses;
        return judged == 0 ? 1F : hits / (float) judged;
    }

    private void miss() {
        misses++;
        combo = 0;
        meter -= 0.07F;
        shake = 1F;
        judge = "miss";
        judgeTime = 0;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.get(), 0.5F, 0.9F));
    }

    private void finish(boolean won) {
        over = true;
        if (sent) return;
        sent = true;
        // everything still to come counts as missed for the score
        int left = 0;
        for (int i = next; i < notes.size(); i++) if (!hit[i]) left++;
        int score = Math.round(100F * hits / Math.max(1, hits + misses + left));
        PaponNetwork.qteResult(bossId, 6, won, score);
    }

    /** A fret pressed: the nearest unhit note in its lane inside the window is hit, or it is a bum note. */
    void press(int key) {
        int lane = -1;
        for (int i = 0; i < 4; i++) if (KEYS[i] == key) lane = i;
        if (lane < 0 || over) return;
        held[lane] = true;
        double t = now();
        int best = -1;
        double bestD = GOOD;
        for (int i = next; i < notes.size() && notes.get(i).t < t + GOOD; i++) {
            if (hit[i] || notes.get(i).lane != lane) continue;
            double d = Math.abs(notes.get(i).t - t);
            if (d <= bestD) {
                bestD = d;
                best = i;
            }
        }
        if (best < 0) {
            // nothing there: a bum note, and the meter knows it
            meter -= 0.03F;
            combo = 0;
            shake = 0.5F;
            judge = "bum";
            judgeTime = 0;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_DIDGERIDOO.get(), 0.6F, 0.5F));
            return;
        }
        hit[best] = true;
        hits++;
        combo++;
        this.best = Math.max(this.best, combo);
        boolean perfect = bestD <= PERFECT;
        if (perfect) perfects++;
        meter = Math.min(1F, meter + (perfect ? 0.035F : 0.025F));
        flash[lane] = 1F;
        judge = perfect ? "perfect" : "good";
        judgeTime = 0;
        float pitch = new float[]{0.84F, 1.0F, 1.12F, 1.26F}[lane] * (perfect ? 1.0F : 0.94F);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_GUITAR.get(), pitch, 0.7F));
    }

    void release(int key) {
        for (int i = 0; i < 4; i++) if (KEYS[i] == key) held[i] = false;
    }

    private int multiplier() {
        return combo >= 30 ? 4 : combo >= 20 ? 3 : combo >= 10 ? 2 : 1;
    }

    /** The fretboard: a highway narrowing into the distance, four lanes, gems coming down to the frets. */
    void render(GuiGraphics g, int w, int h, float partial) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        judgeTime += mc.getDeltaFrameTime();
        double t = now();
        int cx = w / 2 + (shake > 0 ? (int) ((mc.level.random.nextFloat() - 0.5F) * 6 * shake) : 0);
        int lineY = h - 46;
        int topY = Math.max(40, h - 250);
        int halfBottom = 92, halfTop = 40;
        // the highway
        for (int y = topY; y < lineY + 18; y += 2) {
            float k = (y - topY) / (float) (lineY - topY);
            int half = (int) Mth.lerp(Math.min(1F, k), halfTop, halfBottom);
            int a = (int) (110 + 90 * Math.min(1F, k));
            g.fill(cx - half - 2, y, cx + half + 2, y + 2, a << 24 | 0x1A0E08);
            for (int l = 1; l < 4; l++) {
                int x = cx - half + half * 2 * l / 4;
                g.fill(x, y, x + 1, y + 2, 0x50FFFFFF);
            }
        }
        g.fill(cx - halfBottom - 2, lineY - 1, cx + halfBottom + 2, lineY + 1, 0xFFFFFFFF);
        // the frets on the line, lit while held, flashing on a hit
        for (int l = 0; l < 4; l++) {
            int x = cx - halfBottom + halfBottom * 2 * l / 4 + halfBottom / 4;
            int c = COLORS[l];
            int r = 13;
            int dim = held[l] ? c : (c & 0x00FFFFFF) | 0x70000000;
            g.fill(x - r, lineY - 7, x + r, lineY + 7, 0xFF101010);
            g.fill(x - r + 2, lineY - 5, x + r - 2, lineY + 5, dim);
            if (flash[l] > 0) {
                int fa = (int) (200 * flash[l]);
                g.fill(x - r - 4, lineY - 11, x + r + 4, lineY + 11, fa << 24 | 0xFFFFFF);
            }
            g.drawString(font, NAMES[l], x - font.width(NAMES[l]) / 2, lineY + 10, 0xFFFFFFFF, true);
        }
        // the gems
        for (int i = Math.max(0, next - 4); i < notes.size(); i++) {
            Note n = notes.get(i);
            double dt = n.t - t;
            if (dt > FALL) break;
            if (hit[i] || dt < -0.15) continue;
            float k = (float) (1 - dt / FALL);                       // 0 at the top, 1 on the line
            float kk = k * k * 0.35F + k * 0.65F;                    // a touch of perspective: faster near the line
            int y = (int) Mth.lerp(kk, topY, lineY);
            int half = (int) Mth.lerp(kk, halfTop, halfBottom);
            int x = cx - half + half * 2 * n.lane / 4 + half / 4;
            int r = (int) Mth.lerp(kk, 6, 12);
            int c = COLORS[n.lane];
            g.fill(x - r, y - r / 2 - 1, x + r, y + r / 2 + 1, 0xFF000000);
            g.fill(x - r + 1, y - r / 2, x + r - 1, y + r / 2, c);
            g.fill(x - r / 2, y - r / 4, x + r / 2, y + r / 4, 0xC0FFFFFF);
        }
        // the countdown
        if (t < LEAD) {
            int n = (int) Math.ceil(LEAD - 0.4 - t);
            Component c = n > 0 ? Component.literal(String.valueOf(n)) : Component.translatable("qte.papon.duel.go");
            g.pose().pushPose();
            g.pose().translate(cx, topY + 40, 0);
            float s = 3.5F + (float) ((LEAD - t) % 1.0);
            g.pose().scale(s, s, 1);
            g.drawString(font, c.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), -font.width(c) / 2, -4, 0xFFFFFF, true);
            g.pose().popPose();
        }
        // the rock meter: red .. yellow .. green, a needle
        int mx = cx + halfBottom + 18, my0 = lineY - 120, my1 = lineY;
        g.fill(mx - 1, my0 - 1, mx + 9, my1 + 1, 0xFF000000);
        g.fill(mx, my0, mx + 8, my0 + (my1 - my0) / 3, 0xFF3CD25A);
        g.fill(mx, my0 + (my1 - my0) / 3, mx + 8, my0 + 2 * (my1 - my0) / 3, 0xFFF2D23C);
        g.fill(mx, my0 + 2 * (my1 - my0) / 3, mx + 8, my1, 0xFFE8403A);
        int ny = (int) Mth.lerp(Mth.clamp(meter, 0, 1), my1, my0);
        g.fill(mx - 4, ny - 1, mx + 12, ny + 2, 0xFFFFFFFF);
        // combo, multiplier, accuracy, the judgement
        Component combo = Component.translatable("qte.papon.duel.combo", this.combo, multiplier());
        g.drawString(font, combo, cx - halfBottom - 10 - font.width(combo), lineY - 30, 0xFFFFD27A, true);
        Component acc = Component.translatable("qte.papon.duel.accuracy", Math.round(accuracy() * 100), Math.round(PASS * 100));
        g.drawString(font, acc, cx - halfBottom - 10 - font.width(acc), lineY - 18, accuracy() >= PASS ? 0xFF8CFF8C : 0xFFFF8C8C, true);
        if (judgeTime < 14 && !judge.isEmpty()) {
            Component j = Component.translatable("qte.papon.duel." + judge);
            int col = judge.equals("perfect") ? 0xFF7CF0FF : judge.equals("good") ? 0xFF8CFF8C : 0xFFFF5A4A;
            g.pose().pushPose();
            g.pose().translate(cx, lineY - 60, 0);
            float s = 1.6F + Math.max(0, (4 - judgeTime) * 0.12F);
            g.pose().scale(s, s, 1);
            g.drawString(font, j, -font.width(j) / 2, -4, col, true);
            g.pose().popPose();
        }
        // the time left
        float left = (float) Mth.clamp(1 - t / length, 0, 1);
        g.fill(cx - halfTop, topY - 8, cx + halfTop, topY - 5, 0xA0000000);
        g.fill(cx - halfTop, topY - 8, cx - halfTop + (int) (halfTop * 2 * left), topY - 5, 0xFFFFD27A);
    }

    /** Dev (AutoShot): play the next note perfectly when it is due. */
    void devPlay() {
        if (over) return;
        double t = now();
        for (int i = next; i < notes.size() && notes.get(i).t < t + 0.03; i++) {
            if (!hit[i] && notes.get(i).t > t - 0.03) {
                press(KEYS[notes.get(i).lane]);
                release(KEYS[notes.get(i).lane]);
            }
        }
    }
}
