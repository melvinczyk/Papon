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
import java.util.ArrayList;
import java.util.List;

/**
 * The phase-two song, "Canción del Mariachi", as tools/gen_song.py cut it up
 * (assets/papon/song/cancion.json, read off the classpath so the server has it too): the segments
 * the adaptive score is built from (client/SongPlayer), the beats, and what the singer does at
 * every moment: the Rhubarb mouth shapes, how loud he sings, the note (semitones over his middle),
 * and the guitar's strums.
 *
 * Times are seconds in the original recording. The server only needs the intro: the fight starts
 * when the singer starts to sing, after {@link #INTRO_PASSES} passes of the guitar intro.
 */
public final class Song {
    public static final int INTRO_PASSES = 2;

    public record Segment(int index, String id, double start, double length, double pre, String level, float energy, boolean sung) {
        public double end() {
            return start + length;
        }
    }

    public final List<Segment> segments = new ArrayList<>();
    public final double[] beats;
    private final float[] mouthT;
    private final char[] mouthS;
    private final int rate;
    private final float[] voice;
    private final float[] pitch;
    private final float[] strum;

    @Nullable private static Song instance;
    private static boolean tried;

    private Song(JsonObject j) {
        int i = 0;
        for (JsonElement e : j.getAsJsonArray("segments")) {
            JsonObject s = e.getAsJsonObject();
            segments.add(new Segment(i++, s.get("id").getAsString(), s.get("start").getAsDouble(), s.get("length").getAsDouble(),
                    s.get("pre").getAsDouble(), s.get("level").getAsString(), s.get("energy").getAsFloat(), s.get("sung").getAsBoolean()));
        }
        JsonArray b = j.getAsJsonArray("beats");
        beats = new double[b.size()];
        for (int k = 0; k < beats.length; k++) beats[k] = b.get(k).getAsDouble();
        JsonArray m = j.getAsJsonArray("mouth");
        mouthT = new float[m.size()];
        mouthS = new char[m.size()];
        for (int k = 0; k < mouthT.length; k++) {
            JsonArray c = m.get(k).getAsJsonArray();
            mouthT[k] = c.get(0).getAsFloat();
            mouthS[k] = c.get(1).getAsString().charAt(0);
        }
        rate = j.get("rate").getAsInt();
        voice = floats(j.getAsJsonArray("voice"));
        pitch = floats(j.getAsJsonArray("pitch"));
        strum = floats(j.getAsJsonArray("strum"));
    }

    private static float[] floats(JsonArray a) {
        float[] out = new float[a.size()];
        for (int k = 0; k < out.length; k++) out[k] = a.get(k).getAsFloat();
        return out;
    }

    /** The song, or null if this build has none (then phase two plays the old score). */
    @Nullable
    public static synchronized Song get() {
        if (!tried) {
            tried = true;
            try (InputStream in = Song.class.getResourceAsStream("/assets/papon/song/cancion.json")) {
                if (in != null) instance = new Song(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (Exception e) {
                PaponMod.LOG.warn("[song] could not read the song data", e);
            }
        }
        return instance;
    }

    @Nullable
    public Segment byId(String id) {
        for (Segment s : segments) if (s.id.equals(id)) return s;
        return null;
    }

    public Segment intro() {
        Segment s = byId("intro");
        return s != null ? s : segments.get(0);
    }

    /** Ticks from the song's first downbeat to the first sung word (the guitar intro, twice). */
    public int fightStartTicks() {
        return (int) Math.round(intro().length * INTRO_PASSES * 20);
    }

    /** Index of the beat at or before {@code t}, plus how far into it (0..1); negative before the first. */
    public double beatAt(double t) {
        if (beats.length < 2 || t < beats[0]) return -1;
        int lo = 0, hi = beats.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (beats[mid] <= t) lo = mid;
            else hi = mid - 1;
        }
        if (lo >= beats.length - 1) return lo;
        return lo + (t - beats[lo]) / (beats[lo + 1] - beats[lo]);
    }

    /** The mouth shape (Rhubarb: A-H, X) sung at {@code t}, and when it started (for easing into it). */
    public char mouthAt(double t) {
        int k = mouthIndex(t);
        return k < 0 ? 'X' : mouthS[k];
    }

    public double mouthSince(double t) {
        int k = mouthIndex(t);
        return k < 0 ? 0 : t - mouthT[k];
    }

    public char mouthBefore(double t) {
        int k = mouthIndex(t);
        return k <= 0 ? 'X' : mouthS[k - 1];
    }

    private int mouthIndex(double t) {
        int lo = 0, hi = mouthT.length - 1;
        if (hi < 0 || t < mouthT[0]) return -1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (mouthT[mid] <= t) lo = mid;
            else hi = mid - 1;
        }
        return lo;
    }

    /** How loud he sings at {@code t}, 0..1. */
    public float voiceAt(double t) {
        return sample(voice, t);
    }

    /** The note he sings, in semitones over his middle (0 when not singing). */
    public float pitchAt(double t) {
        return sample(pitch, t);
    }

    /** The guitars' attack at {@code t}, 0..1 (spikes on the strums). */
    public float strumAt(double t) {
        return sample(strum, t);
    }

    private float sample(float[] a, double t) {
        double x = t * rate;
        int i = (int) Math.floor(x);
        if (i < 0 || a.length == 0) return 0;
        if (i >= a.length - 1) return a[a.length - 1];
        float f = (float) (x - i);
        return a[i] + (a[i + 1] - a[i]) * f;
    }
}
