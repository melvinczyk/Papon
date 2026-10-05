package com.groid.papon.client;

import com.groid.papon.boss.Fiesta;
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
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * El Último Tango (phase four, QTE kind 7; the user: "a dance-off... get creative, don't just let it be pressing buttons, make
 * it interactive... a tango where you are the opponent; he sees this fight as a dance where you have to keep up with him").
 * Everything happens on the beat of the band's song (its own beat grid, like the guitar duel):
 *
 *   the count-in    "5, 6, 7, 8!"
 *   ¡Sígueme!       he dances a phrase (forward, back, left, right, a spin), the steps lighting up on his side of the strip
 *   ¡Tu turno!      you dance it back on the same beats: W S A D and SPACE for the spin, judged to the beat
 *   (three rounds, each phrase longer and faster: 4 steps, 6 with an "and", 8 on every half beat)
 *   El Abrazo       the embrace, between the second and the third: his hand leads you round the floor (a ring tracing a figure
 *                   of eight on the dance floor), and you follow it with your mouse; out of the ring and you have let go
 *   ¡El Dip!        the last beat: a ring closing on the downbeat; SPACE exactly on it
 *
 * The stands decide: the applause meter swings to whoever dances better (his steps win the crowd too, so you have to keep
 * up). It ends with the song's last beat: yours over half, you win. Timed on the real clock from the moment it starts. While it
 * runs, his model (BossModel.tango) and yours (QtePlayerPose) dance the steps.
 */
final class DanceOff {
    /** The steps: forward, back, left, right, spin. */
    static final int FWD = 0, BACK = 1, LEFT = 2, RIGHT = 3, SPIN = 4;
    static final int[] KEYS = {GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_SPACE};
    private static final String[] GLYPH = {"↑", "↓", "←", "→", "↻"};
    private static final String[] KEY_NAME = {"W", "S", "A", "D", "␣"};
    private static final double GOOD = 0.13, PERFECT = 0.06;
    private static final int JADE = 0xFF52FF84, GOLD = 0xFFFFD27A, ROSA = 0xFFEC2E8C;

    enum Part { COUNT, CALL, RESPONSE, EMBRACE, DIP, DONE }

    private record Section(Part part, double t0, double t1, int round) {}

    private record Step(double t, int move, boolean his, int round) {}

    private final int bossId;
    private final long startNanos;
    private final double length;
    private final List<Section> sections = new ArrayList<>();
    private final List<Step> steps = new ArrayList<>();
    private final int[] state;                 // per step: 0 to come, 1 hit, 2 perfect, 3 missed
    private final List<Double> beats = new ArrayList<>();
    private double dipAt;
    private boolean dipDone;
    private int dipResult;                     // 0 none, 1 hit, 2 missed
    private float meter = 0.5F;
    private int hits, misses;
    private int embraceIn, embraceTicks;
    private boolean over, sent;
    /** Your last step (for your model) and when; and the mouse, in GUI pixels. */
    private int lastMove = -1;
    private double lastMoveAt = -9;
    private double mouseX = -1, mouseY = -1;
    private String judge = "";
    private double judgeAt = -9;
    private float shake;
    private int countSaid = -1;

    DanceOff(int bossId, int seed, int lengthTicks, int songId) {
        this.bossId = bossId;
        this.startNanos = System.nanoTime();
        this.length = lengthTicks / 20.0;
        chart(seed, songId);
        state = new int[steps.size()];
    }

    double now() { return (System.nanoTime() - startNanos) / 1e9; }

    private double beat(int k) {
        if (k < beats.size()) return beats.get(k);
        double last = beats.get(beats.size() - 1), step = beats.size() > 1 ? last - beats.get(beats.size() - 2) : 0.464;
        return last + (k - beats.size() + 1) * step;
    }

    /** Between beat k and k+1, {@code f} of the way. */
    private double beat(int k, double f) { return beat(k) + (beat(k + 1) - beat(k)) * f; }

    private void chart(int seed, int songId) {
        RandomSource r = RandomSource.create(seed);
        Song song = Song.get(songId);
        double songAt = SongPlayer.isActive() ? SongPlayer.songTime(0) : Double.NaN;
        if (song != null && !Double.isNaN(songAt) && song.beats.length > 8) {
            for (double b : song.beats) {
                double t = b - songAt;
                if (t >= 1.0 && t < length + 2) beats.add(t);
            }
        }
        if (beats.size() < 44) {
            beats.clear();
            double bt = Fiesta.BEAT / 20.0;
            for (double t = 1.0; t < length + 2; t += bt) beats.add(t);
        }
        int k = 0;
        sections.add(new Section(Part.COUNT, beat(k), beat(k + 4), 0));
        k += 4;
        // the rounds: the beats (in beats from the round's start, within 4) of his phrase
        double[][] rhythm = {{0, 1, 2, 3}, {0, 1, 1.5, 2, 3, 3.5}, {0, 0.5, 1, 1.5, 2, 2.5, 3, 3.5}};
        int[] spins = {0, 1, 2};
        for (int round = 0; round < 3; round++) {
            if (round == 2) {
                sections.add(new Section(Part.EMBRACE, beat(k), beat(k + 12), 0));
                k += 12;
            }
            int[] moves = new int[rhythm[round].length];
            int prev = -1, run = 0;
            for (int i = 0; i < moves.length; i++) {
                int mv;
                do mv = r.nextInt(4); while (mv == prev && run >= 1);
                run = mv == prev ? run + 1 : 0;
                prev = mv;
                moves[i] = mv;
            }
            for (int sp = 0; sp < spins[round]; sp++) moves[1 + r.nextInt(moves.length - 1)] = SPIN;
            sections.add(new Section(Part.CALL, beat(k), beat(k + 4), round));
            for (int i = 0; i < moves.length; i++) {
                double b = rhythm[round][i];
                steps.add(new Step(beat(k + (int) b, b - Math.floor(b)), moves[i], true, round));
            }
            sections.add(new Section(Part.RESPONSE, beat(k + 4), beat(k + 8), round));
            for (int i = 0; i < moves.length; i++) {
                double b = rhythm[round][i];
                steps.add(new Step(beat(k + 4 + (int) b, b - Math.floor(b)), moves[i], false, round));
            }
            k += 8;
        }
        sections.add(new Section(Part.DIP, beat(k), beat(k + 2), 0));
        dipAt = beat(k + 1);
        sections.add(new Section(Part.DONE, beat(k + 2), beat(k + 2) + 0.8, 0));
    }

    boolean over() { return over; }

    float meter() { return Mth.clamp(meter, 0F, 1F); }

    @Nullable
    private Section section(double t) {
        for (Section s : sections) if (t >= s.t0 && t < s.t1) return s;
        return null;
    }

    Part part() {
        Section s = section(now());
        return s == null ? Part.COUNT : s.part;
    }

    /** His step now (for his model): the step and how far into it (0..1), or -1. */
    float[] hisStep() {
        double t = now();
        for (Step s : steps) {
            if (!s.his) continue;
            double into = t - s.t;
            if (into >= -0.05 && into < 0.42) return new float[]{s.move, (float) Mth.clamp((into + 0.05) / 0.47, 0, 1)};
        }
        return new float[]{-1, 0};
    }

    /** Your last step (for your model): the step and how long ago (seconds). */
    float[] yourStep() { return new float[]{lastMove, (float) (now() - lastMoveAt)}; }

    /** Where in the beat it is (0..1), for the bob. */
    float beatPhase() {
        double t = now();
        for (int i = 0; i + 1 < beats.size(); i++) {
            if (t >= beats.get(i) && t < beats.get(i + 1)) return (float) ((t - beats.get(i)) / (beats.get(i + 1) - beats.get(i)));
        }
        return 0;
    }

    /** The embrace: his hand's place on the floor widget (-1..1 each way). */
    private double[] lead(double t) {
        Section s = null;
        for (Section x : sections) if (x.part == Part.EMBRACE) s = x;
        if (s == null) return new double[]{0, 0};
        double u = Mth.clamp((t - s.t0) / (s.t1 - s.t0), 0, 1);
        double speed = 1 + u * 0.8;
        double a = (t - s.t0) * Math.PI * 2 * 0.24 * speed;
        return new double[]{Math.sin(a) * 0.78, Math.sin(a * 2) * 0.5 + 0.12 * Math.sin(a * 3)};
    }

    /** The ring round his hand in the embrace (0..1 of the widget's radius): shrinking as it goes. */
    private double ring(double t) {
        Section s = null;
        for (Section x : sections) if (x.part == Part.EMBRACE) s = x;
        if (s == null) return 0.3;
        double u = Mth.clamp((t - s.t0) / (s.t1 - s.t0), 0, 1);
        return 0.3 - 0.12 * u;
    }

    void mouse(double x, double y) {
        mouseX = x;
        mouseY = y;
    }

    // the widget's place on the screen (set while drawing; the mouse is judged against it)
    private int fx, fy, fr;

    void tick() {
        if (over) return;
        double t = now();
        Section s = section(t);
        // the count-in: "5, 6, 7, 8"
        if (s != null && s.part == Part.COUNT) {
            for (int i = 0; i < 4; i++) {
                if (t >= beat(i) && countSaid < i) {
                    countSaid = i;
                    play(SoundEvents.NOTE_BLOCK_HAT.get(), 1.4F, 0.8F);
                }
            }
        }
        // his steps: the stands love them (the meter leans his way; you have to answer)
        for (int i = 0; i < steps.size(); i++) {
            Step st = steps.get(i);
            if (state[i] == 0 && st.his && t >= st.t) {
                state[i] = 1;
                meter -= 0.006F;
                play(st.move == SPIN ? SoundEvents.PLAYER_ATTACK_SWEEP : SoundEvents.NOTE_BLOCK_BASEDRUM.value(), st.move == SPIN ? 1.2F : 0.9F, 0.7F);
            }
            if (state[i] == 0 && !st.his && st.t < t - GOOD) miss(i);
        }
        // the embrace: holding on to his hand
        if (s != null && s.part == Part.EMBRACE && fr > 0 && mouseX >= 0) {
            double[] l = lead(t);
            double hx = fx + l[0] * fr, hy = fy + l[1] * fr;
            double d = Math.hypot(mouseX - hx, mouseY - hy) / fr;
            embraceTicks++;
            if (d <= ring(t)) {
                embraceIn++;
                meter += 0.0075F;
            } else meter -= 0.011F;
        }
        // the dip: missed it
        if (!dipDone && t > dipAt + GOOD) {
            dipDone = true;
            dipResult = 2;
            meter -= 0.12F;
            judge("miss");
        }
        shake = Math.max(0, shake - 0.1F);
        if (meter <= 0) finish(false);
        else if (s != null && s.part == Part.DONE || t > length - 0.3) finish(meter >= 0.5F);
    }

    private void miss(int i) {
        state[i] = 3;
        misses++;
        meter -= 0.07F;
        shake = 1F;
        judge("miss");
        play(SoundEvents.NOTE_BLOCK_BASS.get(), 0.6F, 0.8F);
    }

    private void judge(String j) {
        judge = j;
        judgeAt = now();
    }

    private static void play(net.minecraft.sounds.SoundEvent e, float pitch, float vol) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(e, pitch, vol));
    }

    private void finish(boolean won) {
        over = true;
        if (sent) return;
        sent = true;
        PaponNetwork.qteResult(bossId, 7, won, Math.round(meter() * 100));
    }

    void press(int key) {
        if (over) return;
        int mv = -1;
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i] == key) mv = i;
        if (mv < 0) return;
        double t = now();
        Section s = section(t);
        if (s == null || s.part == Part.COUNT) return;
        lastMove = mv;
        lastMoveAt = t;
        if (s.part == Part.DIP) {
            if (dipDone) return;
            dipDone = true;
            double d = Math.abs(t - dipAt);
            if (d <= GOOD * 1.2) {
                dipResult = 1;
                meter += d <= PERFECT ? 0.16F : 0.12F;
                judge(d <= PERFECT ? "perfect" : "good");
                play(SoundEvents.PLAYER_LEVELUP, 1.6F, 0.7F);
            } else {
                dipResult = 2;
                meter -= 0.12F;
                judge("miss");
            }
            return;
        }
        if (s.part != Part.RESPONSE) return;              // his turn: watch him
        // the nearest of your steps to come, inside the window
        int best = -1;
        double bestD = GOOD;
        for (int i = 0; i < steps.size(); i++) {
            Step st = steps.get(i);
            if (st.his || state[i] != 0) continue;
            double d = Math.abs(st.t - t);
            if (d <= bestD) {
                bestD = d;
                best = i;
            }
        }
        if (best < 0) {
            // a step off the beat
            meter -= 0.02F;
            shake = 0.4F;
            judge("off");
            return;
        }
        if (steps.get(best).move != mv) {
            miss(best);
            return;
        }
        boolean perfect = bestD <= PERFECT;
        state[best] = perfect ? 2 : 1;
        hits++;
        meter += perfect ? 0.05F : 0.035F;
        judge(perfect ? "perfect" : "good");
        play(mv == SPIN ? SoundEvents.PLAYER_ATTACK_SWEEP : SoundEvents.NOTE_BLOCK_SNARE.value(), 1.0F + mv * 0.08F, 0.8F);
    }

    // ------------------------------------------------------------------ drawing

    void render(GuiGraphics g, int w, int h, float partial) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        double t = now();
        Section s = section(t);
        Part part = s == null ? Part.COUNT : s.part;
        int cx = w / 2 + (shake > 0 ? (int) ((mc.level.random.nextFloat() - 0.5F) * 6 * shake) : 0);
        // the applause: a tug of war, him (jade) against you (gold)
        int bw = Math.min(260, w - 80), by = 40;
        g.fill(cx - bw / 2 - 2, by - 2, cx + bw / 2 + 2, by + 10, 0xFF000000);
        int mid = cx - bw / 2 + (int) (bw * (1 - meter()));
        g.fill(cx - bw / 2, by, mid, by + 8, JADE);
        g.fill(mid, by, cx + bw / 2, by + 8, GOLD);
        g.fill(cx - 1, by - 4, cx + 1, by + 12, 0xFFFFFFFF);
        g.fill(mid - 2, by - 3, mid + 2, by + 11, 0xFFFFFFFF);
        Component him = Component.translatable("qte.papon.tango.him"), you = Component.translatable("qte.papon.tango.you");
        g.drawString(font, him, cx - bw / 2 - font.width(him) - 6, by, JADE, true);
        g.drawString(font, you, cx + bw / 2 + 6, by, GOLD, true);
        Component applause = Component.translatable("qte.papon.tango.applause");
        g.drawString(font, applause, cx - font.width(applause) / 2, by + 13, 0xFFE8E0D0, true);
        // what this part is
        Component cap = switch (part) {
            case COUNT -> Component.translatable("qte.papon.tango.count");
            case CALL -> Component.translatable("qte.papon.tango.call");
            case RESPONSE -> Component.translatable("qte.papon.tango.response");
            case EMBRACE -> Component.translatable("qte.papon.tango.embrace");
            case DIP, DONE -> Component.translatable("qte.papon.tango.dip");
        };
        int capCol = part == Part.CALL ? JADE : part == Part.EMBRACE ? ROSA : GOLD;
        g.pose().pushPose();
        g.pose().translate(cx, by + 30, 0);
        float pop = 1.6F + (float) Math.max(0, 0.4 - (t - (s != null ? s.t0 : 0))) * 1.5F;
        pop = Math.min(pop, w * 0.8F / Math.max(1, font.width(cap)));
        g.pose().scale(pop, pop, 1);
        g.drawString(font, cap.copy().withStyle(ChatFormatting.BOLD), -font.width(cap) / 2 - 2, -4, capCol, true);
        g.pose().popPose();
        if (part == Part.COUNT) countIn(g, font, cx, h, t);
        else if (part == Part.EMBRACE) embrace(g, cx, h, t);
        else if (part == Part.DIP || part == Part.DONE) dip(g, font, cx, h, t);
        if (part == Part.CALL || part == Part.RESPONSE) strip(g, font, cx, h, t, s);
        // the judgement
        if (t - judgeAt < 0.6 && !judge.isEmpty()) {
            Component j = Component.translatable("qte.papon.tango." + judge);
            int col = judge.equals("perfect") ? 0xFF7CF0FF : judge.equals("good") ? 0xFF8CFF8C : 0xFFFF5A4A;
            g.pose().pushPose();
            g.pose().translate(cx, h * 0.42F, 0);
            float sc = 1.8F + (float) Math.max(0, 0.15 - (t - judgeAt)) * 4;
            g.pose().scale(sc, sc, 1);
            g.drawString(font, j, -font.width(j) / 2, -4, col, true);
            g.pose().popPose();
        }
    }

    private void countIn(GuiGraphics g, Font font, int cx, int h, double t) {
        int n = -1;
        for (int i = 0; i < 4; i++) if (t >= beat(i)) n = i;
        String txt = n < 0 ? "" : n < 3 ? String.valueOf(5 + n) : "8!";
        if (txt.isEmpty()) return;
        double since = t - beat(Math.max(0, n));
        g.pose().pushPose();
        g.pose().translate(cx, h * 0.5F, 0);
        float s = 5F - (float) Math.min(1.5, since * 6);
        g.pose().scale(s, s, 1);
        g.drawString(font, Component.literal(txt).withStyle(ChatFormatting.BOLD), -font.width(txt) / 2, -4, ROSA, true);
        g.pose().popPose();
    }

    /** The phrase strip: his steps on top (lighting up as he dances them), yours under them (to dance back on the beat). */
    private void strip(GuiGraphics g, Font font, int cx, int h, double t, Section s) {
        int round = s.round;
        double call0 = 0, call1 = 0, resp0 = 0, resp1 = 0;
        for (Section x : sections) {
            if (x.round != round) continue;
            if (x.part == Part.CALL) {
                call0 = x.t0;
                call1 = x.t1;
            }
            if (x.part == Part.RESPONSE) {
                resp0 = x.t0;
                resp1 = x.t1;
            }
        }
        int sw = 300, x0 = cx - sw / 2;
        int topY = h - 92, botY = h - 52;
        g.fill(x0 - 6, topY - 18, x0 + sw + 6, botY + 18, 0x90000000);
        // the beat lines
        for (int k = 0; k <= 4; k++) {
            int x = x0 + sw * k / 4;
            g.fill(x, topY - 14, x + 1, botY + 14, 0x40FFFFFF);
        }
        boolean responding = s.part == Part.RESPONSE;
        double ph = responding ? (t - resp0) / (resp1 - resp0) : (t - call0) / (call1 - call0);
        int px = x0 + (int) (sw * Mth.clamp(ph, 0, 1));
        g.fill(px - 1, (responding ? botY : topY) - 16, px + 1, (responding ? botY : topY) + 16, 0xFFFFFFFF);
        for (int i = 0; i < steps.size(); i++) {
            Step st = steps.get(i);
            if (st.round != round) continue;
            double a0 = st.his ? call0 : resp0, a1 = st.his ? call1 : resp1;
            int x = x0 + (int) (sw * (st.t - a0) / (a1 - a0));
            int y = st.his ? topY : botY;
            boolean lit = st.his ? t >= st.t : state[i] == 1 || state[i] == 2;
            boolean bad = !st.his && state[i] == 3;
            int sz = 11;
            int edge = bad ? 0xFFFF4444 : lit ? (st.his ? JADE : (state[i] == 2 ? 0xFF7CF0FF : 0xFF55FF55)) : st.his ? 0xFF3C6A4A : 0xFF8A7A5A;
            // yours are hidden until he has danced them: you have to remember
            boolean shown = st.his ? t >= st.t - 0.05 : responding || t >= call1 - 0.2;
            g.fill(x - sz - 1, y - sz - 1, x + sz + 1, y + sz + 1, edge);
            g.fill(x - sz, y - sz, x + sz, y + sz, 0xE0201812);
            if (shown) {
                String glyph = GLYPH[st.move];
                g.pose().pushPose();
                g.pose().translate(x, y, 0);
                float sc = lit && st.his && t - st.t < 0.2 ? 2.2F : 1.6F;
                g.pose().scale(sc, sc, 1);
                g.drawString(font, glyph, -font.width(glyph) / 2, -4, st.his ? 0xFFDFFFE8 : 0xFFFFF0C8, true);
                g.pose().popPose();
                if (!st.his) g.drawString(font, KEY_NAME[st.move], x - font.width(KEY_NAME[st.move]) / 2, y + sz + 3, 0xFFB0A080, true);
            } else {
                g.drawString(font, "?", x - font.width("?") / 2, y - 4, 0xFF6A5A4A, true);
            }
        }
        Component lhs = Component.translatable("qte.papon.tango.him"), rhs = Component.translatable("qte.papon.tango.you");
        g.drawString(font, lhs, x0 - 6 - font.width(lhs) - 4, topY - 4, JADE, true);
        g.drawString(font, rhs, x0 - 6 - font.width(rhs) - 4, botY - 4, GOLD, true);
    }

    /** The embrace: the dance floor seen from above, his hand leading round it; keep your cursor in the ring. */
    private void embrace(GuiGraphics g, int cx, int h, double t) {
        fr = Math.min(90, h / 4);
        fx = cx;
        fy = (int) (h * 0.58F);
        // the floor: rings of colour
        for (int r = fr; r > 0; r -= 6) {
            int col = Fiesta.colour(r / 6) | 0x30000000;
            circle(g, fx, fy, r, col);
        }
        circle(g, fx, fy, fr, 0x60000000);
        // his path ahead, dotted
        for (int k = 1; k <= 12; k++) {
            double[] l = lead(t + k * 0.08);
            int x = (int) (fx + l[0] * fr), y = (int) (fy + l[1] * fr);
            g.fill(x - 1, y - 1, x + 1, y + 1, 0x80EC2E8C);
        }
        double[] l = lead(t);
        int hx = (int) (fx + l[0] * fr), hy = (int) (fy + l[1] * fr);
        int rr = (int) Math.round(ring(t) * fr);
        boolean in = mouseX >= 0 && Math.hypot(mouseX - hx, mouseY - hy) <= rr;
        ringOutline(g, hx, hy, rr, in ? 0xFF55FF55 : ROSA);
        g.fill(hx - 3, hy - 3, hx + 3, hy + 3, 0xFFFFFFFF);
        if (mouseX >= 0) g.fill((int) mouseX - 2, (int) mouseY - 2, (int) mouseX + 2, (int) mouseY + 2, GOLD);
        float held = embraceTicks == 0 ? 1 : embraceIn / (float) embraceTicks;
        Component c = Component.translatable("qte.papon.tango.held", Math.round(held * 100));
        Font font = Minecraft.getInstance().font;
        g.drawString(font, c, fx - font.width(c) / 2, fy + fr + 8, in ? 0xFF8CFF8C : 0xFFFF8C8C, true);
    }

    /** The dip: a ring closing on the last downbeat. */
    private void dip(GuiGraphics g, Font font, int cx, int h, double t) {
        int cy = (int) (h * 0.55F);
        int r0 = 14;
        double k = Mth.clamp((dipAt - t) / 0.9, 0, 1);
        int r = r0 + (int) (k * 70);
        ringOutline(g, cx, cy, r0, 0xFFFFFFFF);
        if (!dipDone) ringOutline(g, cx, cy, r, ROSA);
        String txt = dipResult == 1 ? "¡OLÉ!" : KEY_NAME[SPIN];
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(2.2F, 2.2F, 1);
        g.drawString(font, txt, -font.width(txt) / 2, -4, dipResult == 1 ? 0xFF8CFF8C : 0xFFFFFFFF, true);
        g.pose().popPose();
    }

    private static void circle(GuiGraphics g, int x, int y, int r, int col) {
        for (int dy = -r; dy <= r; dy += 2) {
            int dx = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            g.fill(x - dx, y + dy, x + dx, y + dy + 2, col);
        }
    }

    private static void ringOutline(GuiGraphics g, int x, int y, int r, int col) {
        int n = Math.max(16, r);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            int px = (int) (x + Math.cos(a) * r), py = (int) (y + Math.sin(a) * r);
            g.fill(px - 1, py - 1, px + 1, py + 1, col);
        }
    }

    /** Dev (AutoShot): dance it perfectly. */
    void devPlay() {
        if (over) return;
        double t = now();
        for (int i = 0; i < steps.size(); i++) {
            Step st = steps.get(i);
            if (!st.his && state[i] == 0 && Math.abs(st.t - t) < 0.03) press(KEYS[st.move]);
        }
        if (part() == Part.EMBRACE && fr > 0) {
            double[] l = lead(t);
            mouse(fx + l[0] * fr, fy + l[1] * fr);
        }
        if (!dipDone && Math.abs(t - dipAt) < 0.03) press(KEYS[SPIN]);
    }
}
