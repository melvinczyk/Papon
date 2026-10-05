package com.groid.papon.boss;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.groid.papon.PaponMod;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Chef Papon's phase-two voice (tools/gen_boss_voice.py: his cloned voice, in English), as
 * assets/papon/voice/boss2.json describes it, read off the classpath so the server has it too: how long
 * every line is (the cutscene waits for him to finish) and, for the client's lip sync (client/BossFace),
 * the Rhubarb mouth shapes, how loud he is, his pitch and the stressed syllables, plus the mood he says
 * it in; and the cutscene's beats timed to them ({@link #beat}). A line here is said as "speech.papon.boss2.<line>" with the sound "voice.boss2.<line>".
 */
public final class VoiceLines {
    public record Line(String id, double length, String mood, float[] mouthT, char[] mouthS, float[] env, float[] pitch,
                       float[] accent, int rate) {
        /** Ticks the line takes, rounded up. */
        public int ticks() { return (int) Math.ceil(length * 20); }

        /** The mouth shape (Rhubarb: A-H, X) at {@code t} seconds into the line, the one before it, and when it started. */
        public char mouthAt(double t) {
            int k = index(t);
            return k < 0 ? 'X' : mouthS[k];
        }

        public char mouthBefore(double t) {
            int k = index(t);
            return k <= 0 ? 'X' : mouthS[k - 1];
        }

        public double mouthSince(double t) {
            int k = index(t);
            return k < 0 ? 0 : t - mouthT[k];
        }

        private int index(double t) {
            int lo = 0, hi = mouthT.length - 1;
            if (hi < 0 || t < mouthT[0]) return -1;
            while (lo < hi) {
                int mid = (lo + hi + 1) >>> 1;
                if (mouthT[mid] <= t) lo = mid;
                else hi = mid - 1;
            }
            return lo;
        }

        /** How loud he is, 0..1. */
        public float envAt(double t) { return sample(env, t); }

        /** Semitones over his middle (0 when unvoiced). */
        public float pitchAt(double t) { return sample(pitch, t); }

        /** 0..1, a pulse on each stressed syllable. */
        public float accentAt(double t) { return sample(accent, t); }

        private float sample(float[] a, double t) {
            double x = t * rate;
            int i = (int) Math.floor(x);
            if (i < 0 || a.length == 0 || i >= a.length) return 0;
            if (i == a.length - 1) return a[i];
            float f = (float) (x - i);
            return a[i] + (a[i + 1] - a[i]) * f;
        }
    }

    private final Map<String, Line> lines = new HashMap<>();
    private final Map<String, Integer> beats = new HashMap<>();

    @Nullable private static VoiceLines instance;
    private static boolean tried;

    private VoiceLines(JsonObject j) {
        int rate = j.get("rate").getAsInt();
        for (Map.Entry<String, JsonElement> e : j.getAsJsonObject("lines").entrySet()) {
            JsonObject l = e.getValue().getAsJsonObject();
            JsonArray m = l.getAsJsonArray("mouth");
            float[] mt = new float[m.size()];
            char[] ms = new char[m.size()];
            for (int k = 0; k < mt.length; k++) {
                JsonArray c = m.get(k).getAsJsonArray();
                mt[k] = c.get(0).getAsFloat();
                ms[k] = c.get(1).getAsString().charAt(0);
            }
            lines.put(e.getKey(), new Line(e.getKey(), l.get("len").getAsDouble(), l.get("mood").getAsString(), mt, ms,
                    floats(l.getAsJsonArray("env")), floats(l.getAsJsonArray("pitch")), floats(l.getAsJsonArray("accent")), rate));
        }
        readBeats(j);
    }

    private void readBeats(JsonObject j) {
        if (j.has("beats")) for (Map.Entry<String, JsonElement> e : j.getAsJsonObject("beats").entrySet()) beats.put(e.getKey(), e.getValue().getAsInt());
    }

    private static float[] floats(JsonArray a) {
        float[] out = new float[a.size()];
        for (int k = 0; k < out.length; k++) out[k] = a.get(k).getAsFloat();
        return out;
    }

    /** The voice data, or null if this build has none (then he speaks his old lines). */
    @Nullable
    public static synchronized VoiceLines get() {
        if (!tried) {
            tried = true;
            try (InputStream in = VoiceLines.class.getResourceAsStream("/assets/papon/voice/boss2.json")) {
                if (in != null) instance = new VoiceLines(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (Exception e) {
                PaponMod.LOG.warn("[voice] could not read the voice data", e);
            }
        }
        return instance;
    }

    @Nullable
    public static Line line(String id) {
        VoiceLines v = get();
        return v == null ? null : v.lines.get(id);
    }

    /**
     * A moment of the phase-two cutscene, in ticks into its state ("talk.enough", "apology.aura", ...):
     * gen_boss_voice.beats works them out from how long his lines are, and the animations
     * (gen_boss_anims) are cut to the same numbers. {@code fallback} when this build has no voice.
     */
    public static int beat(String key, int fallback) {
        VoiceLines v = get();
        Integer b = v == null ? null : v.beats.get(key);
        return b == null ? fallback : b;
    }

    /** Ticks {@code id} takes to say, or {@code fallback} if it has no recording. */
    public static int ticks(String id, int fallback) {
        Line l = line(id);
        return l == null ? fallback : l.ticks();
    }
}
