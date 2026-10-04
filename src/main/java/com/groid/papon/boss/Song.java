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
 * when the singer starts to sing, after {@link #introPasses} passes of the guitar intro (the close-up, his apology and
 * aura farm, "where were we?", and him working the stands up).
 */
public final class Song {
    /** Passes of the guitar intro before he sings: at least two, more if his apology and aura farm need the room (VoiceLines). */
    public static int introPasses() {
        return VoiceLines.beat("song.passes", 2);
    }

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
    /** The backing vocals (0..1), where the trumpets play (0/1), the melody line (MIDI, 0 = none). */
    private final float[] harmony;
    private final float[] horn;
    private final float[] lead;
    private final float[] fiddle;
    /** The melody line's note starts (seconds). */
    private final double[] notes;
    /** The violins' bowing: stroke times and the bow position at each (0 frog .. 1 tip). */
    private final double[] bowT;
    private final float[] bowP;

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
        harmony = floats(j.getAsJsonArray("harmony"));
        horn = floats(j.getAsJsonArray("horn"));
        lead = floats(j.getAsJsonArray("lead"));
        fiddle = floats(j.getAsJsonArray("fiddle"));
        JsonArray n = j.has("notes") ? j.getAsJsonArray("notes") : new JsonArray();
        notes = new double[n.size()];
        for (int k = 0; k < notes.length; k++) notes[k] = n.get(k).getAsDouble();
        JsonArray bw = j.has("bow") ? j.getAsJsonArray("bow") : new JsonArray();
        bowT = new double[bw.size()];
        bowP = new float[bw.size()];
        for (int k = 0; k < bowT.length; k++) {
            JsonArray c = bw.get(k).getAsJsonArray();
            bowT[k] = c.get(0).getAsDouble();
            bowP[k] = c.get(1).getAsFloat();
        }
    }

    private static float[] floats(@Nullable JsonArray a) {
        if (a == null) return new float[0];
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
        return (int) Math.round(intro().length * introPasses() * 20);
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

    /** The backing vocals at {@code t}: 0 (the singer alone) .. 1 (the coro sings with him). */
    public float harmonyAt(double t) {
        return sample(harmony, t);
    }

    /** Whether the trumpets play at {@code t} (0..1, the edges eased by the caller). */
    public float hornAt(double t) {
        return sample(horn, t);
    }

    /** Whether the violins play at {@code t} (0..1, eased in and out already). */
    public float fiddleAt(double t) {
        return fiddle.length == 0 ? 1 : sample(fiddle, t);
    }

    /** The melody line's note at {@code t} (MIDI number; 0 when there is none to hear). */
    public int leadAt(double t) {
        int i = (int) Math.floor(t * rate);
        return i < 0 || i >= lead.length ? 0 : (int) lead[i];
    }

    /** Index of the melody note sounding at {@code t} (-1 before the first). */
    public int noteIndex(double t) {
        return floorIndex(notes, t);
    }

    /** When note {@code i} started. */
    public double noteTime(int i) {
        return i < 0 || i >= notes.length ? Double.NEGATIVE_INFINITY : notes[i];
    }

    /**
     * Where on the bow the violins are at {@code t} (0 frog .. 1 tip): straight from one stroke's
     * key to the next, at an even speed through the middle of the stroke and a quick turn at each
     * end, as a bow arm moves.
     */
    public float bowAt(double t) {
        int k = floorIndex(bowT, t);
        if (k < 0) return bowP.length == 0 ? 0.5F : bowP[0];
        if (k >= bowT.length - 1) return bowP[bowT.length - 1];
        double u = (t - bowT[k]) / Math.max(1e-3, bowT[k + 1] - bowT[k]);
        // linear in the middle, eased over the first and last 12% (the bow change)
        double e = 0.12;
        double w = u < e ? u * u / (2 * e * (1 - e)) : u > 1 - e ? 1 - (1 - u) * (1 - u) / (2 * e * (1 - e))
                : (u - e / 2) / (1 - e);
        return (float) (bowP[k] + (bowP[k + 1] - bowP[k]) * w);
    }

    /** Which way the bow is moving at {@code t}: +1 down-bow (towards the tip), -1 up-bow. */
    public int bowDirection(double t) {
        int k = floorIndex(bowT, t);
        if (k < 0 || k >= bowT.length - 1) return 1;
        return bowP[k + 1] >= bowP[k] ? 1 : -1;
    }

    private static int floorIndex(double[] a, double t) {
        if (a.length == 0 || t < a[0]) return -1;
        int lo = 0, hi = a.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (a[mid] <= t) lo = mid;
            else hi = mid - 1;
        }
        return lo;
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
