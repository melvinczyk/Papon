package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.boss.BandRole;
import com.groid.papon.boss.BandState;
import com.groid.papon.boss.MariachiEntity;
import com.groid.papon.boss.Song;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

import java.util.HashMap;
import java.util.Map;

/**
 * The band's model (gen_band.py) and the music laid over its animations while they play, all from
 * the song's own data at the moment SongPlayer is at ({@link Song}; tools/gen_song.py) and the hand
 * poses solved on the rig ({@link BandPoses}; tools/band_ik.py):
 *
 *   the singer   lip sync: Rhubarb's mouth shapes (A-H, X) eased into one another, opened wider the
 *                louder he sings; brows up on the high notes, leaning back into the long ones;
 *                strumming the requinto on the guitars' real attacks, his fretting hand moving
 *                between chords; in the guitar solos and breaks he takes the lead: the fretting hand
 *                runs up and down the neck with the melody line, picking every note, leaning back
 *                with his mouth open for the high ones
 *   trumpets     the horns come up to the lips half a second before each of the trumpets' parts
 *                (the data's horn track) with a breath in, and go down after; playing, the lips are
 *                pressed into the mouthpiece, the cheeks puff out with each note (a push on its
 *                attack: the tonguing), the valves go down in the real Bb fingering of the melody's
 *                notes, and the bells lift for the high ones
 *   violins      bowing stroke by stroke from the data's bow track (his syllables while he sings, the
 *                melody line around him): the arm moves the bow along its length from frog to tip and
 *                back, square across the strings; the left hand slides with the melody and shakes a
 *                vibrato on the long notes; they lower their fiddles under the intro, the first solo
 *                and the breakdown
 *   the coro     the backing vocals: where the song has its harmonies the hand mics come up and they
 *                sing his words with him (his mouth shapes); the maracas shake on the eighths
 *   everyone     a foot tapping and a bounce on the beat; a blink now and then
 *
 * Angles here are in the animation files' convention (GeckoLib loads keyframe rotations as -x, -y,
 * z in radians, so that is how they are added). Arms are set outright from the blended poses; the
 * play loops key every bone touched here, so each frame starts from the animation.
 */
public class MariachiModel extends GeoModel<MariachiEntity> {
    private static final ResourceLocation GEO = PaponMod.id("geo/entity/mariachi.geo.json");
    private static final ResourceLocation TEX = PaponMod.id("textures/entity/mariachi.png");
    /** Phase four: the band of the dead (tools/gen_muertos.py: bone faces painted as sugar skulls, black and silver charro suits). */
    private static final ResourceLocation TEX_DEAD = PaponMod.id("textures/entity/mariachi_muertos.png");
    private static final ResourceLocation ANIM = PaponMod.id("animations/entity/mariachi.animation.json");

    /** Rhubarb's shapes: how open (px), how wide, teeth showing, tongue showing. */
    private record Shape(float open, float width, float teeth, float tongue) {}

    private static final Map<Character, Shape> SHAPES = new HashMap<>();

    static {
        SHAPES.put('X', new Shape(0F, 1.0F, 0, 0));        // rest
        SHAPES.put('A', new Shape(0F, 0.92F, 0, 0));       // M, B, P: lips pressed
        SHAPES.put('B', new Shape(1.0F, 1.06F, 1, 0));     // most consonants: a little open, teeth
        SHAPES.put('C', new Shape(1.9F, 1.0F, 1, 0));      // EH, AE
        SHAPES.put('D', new Shape(3.0F, 1.12F, 1, 0.4F));  // AA: wide open
        SHAPES.put('E', new Shape(2.1F, 0.78F, 0, 0));     // AO, ER: rounded
        SHAPES.put('F', new Shape(1.3F, 0.58F, 0, 0));     // UW, OW, W: puckered
        SHAPES.put('G', new Shape(0.9F, 1.0F, 1, 0));      // F, V: teeth on the lip
        SHAPES.put('H', new Shape(2.0F, 0.95F, 1, 1));     // L: the tongue up
    }

    /**
     * Valves down for each written note of a Bb trumpet (C = 0): open, 1-2-3, 1-3, 2-3, 1-2, 1, 2,
     * open, 2-3, 1-2, 1, 2 (bit 0 = first valve).
     */
    private static final int[] FINGERING = {0, 7, 5, 6, 3, 1, 2, 0, 6, 3, 1, 2};

    /** What each member eases between frames (the horn coming up, the mic, the hand on the neck). */
    private static final class Mem {
        float horn = 0, mic = 0, neck = 0, fiddle = 1, solo = 0, breath = 0;
        final float[] valves = new float[3];
        double last = Double.NaN;
    }

    private final Map<Integer, Mem> mem = new HashMap<>();

    @Override
    public ResourceLocation getModelResource(MariachiEntity m) { return GEO; }

    @Override
    public ResourceLocation getTextureResource(MariachiEntity m) { return m.isDead() ? TEX_DEAD : TEX; }

    @Override
    public ResourceLocation getAnimationResource(MariachiEntity m) { return ANIM; }

    @Override
    public void setCustomAnimations(MariachiEntity m, long instanceId, AnimationState<MariachiEntity> state) {
        float partial = state.getPartialTick();
        BandState s = m.getBandState();
        blink(m, partial, s);
        if (s != BandState.PLAY && s != BandState.COUNT) talk(m, partial);
        if (s != BandState.PLAY) return;
        Song song = SongPlayer.song();
        BandPoses poses = BandPoses.get();
        double t = SongPlayer.songTime(partial);
        if (song == null || poses == null || Double.isNaN(t)) return;
        Mem mm = mem.computeIfAbsent(m.getId(), k -> new Mem());
        double now = m.tickCount + partial;
        float dt = Double.isNaN(mm.last) ? 1F : (float) Mth.clamp(now - mm.last, 0, 4);
        mm.last = now;
        if (mem.size() > 64) mem.keySet().removeIf(id -> Minecraft.getInstance().level == null || Minecraft.getInstance().level.getEntity(id) == null);
        BandRole role = m.getRole();
        double beat = Math.max(0, song.beatAt(t));
        int b = (int) Math.floor(beat);
        float ph = (float) (beat - b);
        float energy = SongPlayer.energy();
        // the beat: a bounce, the right foot tapping (the dead: a real hop on every beat, a sway of the bony hips)
        float bounce = (1 - ph) * (1 - ph) * (0.35F + 0.5F * energy) * (m.isDead() ? 2.6F : 1F);
        add(bone("root"), 0, 0, m.isDead() ? 9F * Mth.sin((float) (beat * Math.PI)) : 0, 0, bounce, 0);
        rot(bone("leg_r"), b % 2 == 0 ? -14 * (float) Math.pow(1 - ph, 4) : 0, 0, 0);
        switch (role.kind) {
            case "singer" -> singer(mm, song, poses, t, beat, dt);
            case "guitar" -> strum(song.strumAt(t), beat);
            case "violin" -> violin(mm, song, poses, t, beat, dt);
            case "trumpet" -> trumpet(mm, song, poses, t, beat, dt);
            case "coro" -> coro(mm, song, poses, t, beat, dt);
            default -> {}
        }
    }

    // ------------------------------------------------------------------ the singer

    /** The guitar solos and breaks are his. */
    private static boolean guitarFeature() {
        Song.Segment seg = SongPlayer.segment();
        return seg != null && (seg.id().startsWith("solo") || seg.id().equals("break1") || seg.id().equals("break"));
    }

    private void singer(Mem mm, Song song, BandPoses poses, double t, double beat, float dt) {
        boolean solo = guitarFeature();
        mm.solo = approach(mm.solo, solo ? 1 : 0, 0.12F, dt);
        int lead = song.leadAt(t);
        // the fretting hand: up and down the neck with the melody in a solo; between two chord
        // shapes a bar apart otherwise
        float chord = ((int) Math.floor(beat / 4) % 2 == 0) ? 0.1F : 0.35F;
        float melody = lead > 0 ? Mth.clamp((lead - 57) / 18F, 0, 1) : mm.neck;
        float want = Mth.lerp(mm.solo, chord, melody);
        mm.neck = approach(mm.neck, want, solo ? 0.6F : 0.25F, dt);
        BandPoses.apply(this::bone, "l", poses.fretLow.lerp(poses.fretHigh, mm.neck));
        // the picking hand: the strums, or every note of the line, harder in the solo
        float pick;
        if (mm.solo > 0.5F) {
            int n = song.noteIndex(t);
            double since = t - song.noteTime(n);
            pick = (n % 2 == 0 ? 1 : -1) * (float) Math.exp(-since / 0.06) * 14F;
        } else {
            float amp = 5 + 15 * Mth.clamp(song.strumAt(t), 0, 1);
            pick = amp * (float) Math.sin(Math.PI * 2 * 2 * beat);
        }
        BandPoses.apply(this::bone, "r", poses.strum.plus(pick, 0, 0));
        // into it: leaning back in the solo, the requinto lifted a little
        rot(bone("torso"), -7 * mm.solo, 0, 3 * mm.solo * (float) Math.sin(Math.PI * beat / 2));
        if (mm.solo > 0.05F && !singing(song, t)) {
            // the guitar face: mouth open on the high notes
            float open = mm.solo * (lead > 66 ? 1.6F : 0.7F);
            mouthOpen(open, 0.9F);
            for (String brow : new String[]{"brow_r", "brow_l"}) {
                CoreGeoBone br = bone(brow);
                if (br != null) br.setPosY(br.getPosY() + mm.solo * 0.6F);
            }
        } else { sing(song, t, 1F); }
    }

    private static boolean singing(Song song, double t) {
        return song.voiceAt(t) > 0.06F;
    }

    /** The strumming hand, down and up on the eighths, as hard as the guitars are hitting. */
    private void strum(float strum, double beat) {
        float amp = 5 + 15 * Mth.clamp(strum, 0, 1);       // the hand stays over the soundhole, under his mouth
        float swing = (float) Math.sin(Math.PI * 2 * 2 * beat);
        rot(bone("arm_r"), amp * swing, 0, 0);
    }

    /** The lips to the song's mouth shapes, {@code gain} 0..1 of the way (the coro fades in and out). */
    private void sing(Song song, double t, float gain) {
        char sh = song.mouthAt(t);
        Shape now = SHAPES.getOrDefault(sh, SHAPES.get('X'));
        Shape was = SHAPES.getOrDefault(song.mouthBefore(t), SHAPES.get('X'));
        float k = (float) Mth.clamp(song.mouthSince(t) / 0.07, 0, 1);
        k = k * k * (3 - 2 * k);
        float v = song.voiceAt(t);
        float open = Mth.lerp(k, was.open, now.open) * (0.6F + 0.75F * v) * gain;
        if (v < 0.03F) open *= v / 0.03F;
        float width = Mth.lerp(gain, 1, Mth.lerp(k, was.width, now.width));
        float teeth = Mth.lerp(k, was.teeth, now.teeth) * Mth.clamp(open, 0, 1);
        float tongue = Mth.lerp(k, was.tongue, now.tongue) * Mth.clamp(open - 0.5F, 0, 1);
        mouthOpen(open, width);
        scale(bone("teeth"), teeth);
        scale(bone("tongue"), tongue);
        // the high notes: brows up, head (all of him) back into the long loud ones
        float pitch = song.pitchAt(t);
        float lift = Mth.clamp(pitch / 7F, -0.3F, 1F) * Math.min(1F, v * 2) * gain;
        for (String brow : new String[]{"brow_r", "brow_l"}) {
            CoreGeoBone br = bone(brow);
            if (br != null) br.setPosY(br.getPosY() + lift * 0.9F);
        }
        rot(bone("torso"), -9 * v * Math.max(0, lift), 0, 0);
    }

    private void mouthOpen(float open, float width) {
        CoreGeoBone lip = bone("lip_low");
        if (lip != null) lip.setPosY(lip.getPosY() - open);
        CoreGeoBone in = bone("mouth_in");
        if (in != null) in.setScaleY(Math.max(0.001F, open - 0.2F));
        CoreGeoBone mouth = bone("mouth");
        if (mouth != null) mouth.setScaleX(width);
    }

    // ------------------------------------------------------------------ the trumpets

    private void trumpet(Mem mm, Song song, BandPoses poses, double t, double beat, float dt) {
        // up half a second before their part (on the lips for its first note), down a moment after
        boolean part = song.hornAt(t + 0.45) > 0.5F || song.hornAt(t - 0.2) > 0.5F;
        mm.horn = approach(mm.horn, part ? 1 : 0, part ? 0.16F : 0.08F, dt);
        float up = smooth(mm.horn);
        boolean blowing = song.hornAt(t) > 0.5F && up > 0.85F;
        int lead = song.leadAt(t);
        int n = song.noteIndex(t);
        double since = t - song.noteTime(n);
        // the high notes: the bell comes up
        float lift = blowing && lead > 0 ? Mth.clamp((lead - 67) / 9F, 0, 1) : 0;
        BandPoses.Arm r = poses.trumpetDownR.lerp(poses.trumpetPlayR, up).lerp(poses.trumpetLiftR, lift * up);
        BandPoses.Arm l = poses.trumpetDownL.lerp(poses.trumpetPlayL, up).lerp(poses.trumpetLiftL, lift * up);
        BandPoses.apply(this::bone, "r", r);
        BandPoses.apply(this::bone, "l", l);
        float horn = Mth.lerp(up, poses.trumpetDown, poses.trumpetPlay) + (poses.trumpetLift - poses.trumpetPlay) * lift * up;
        CoreGeoBone tr = bone("trumpet");
        if (tr != null) BandPoses.set(tr, horn, 0, 0);
        // the valves: the note's fingering (written pitch = concert + 2), each going down fast
        int fingers = blowing && lead > 0 ? FINGERING[Math.floorMod(lead + 2, 12)] : 0;
        for (int v = 0; v < 3; v++) {
            boolean down = (fingers & (1 << v)) != 0;
            mm.valves[v] = approach(mm.valves[v], down ? 1 : 0, 0.9F, dt);
            CoreGeoBone vb = bone("valve_" + (v + 1));
            if (vb != null) vb.setPosY(vb.getPosY() - 0.6F * mm.valves[v]);
        }
        // the breath before they come in: mouth open, a lean back
        boolean before = song.hornAt(t + 0.45) > 0.5F && song.hornAt(t) < 0.5F;
        mm.breath = approach(mm.breath, before ? 1 : 0, 0.3F, dt);
        if (blowing) {
            // lips pressed into the mouthpiece, the tonguing on each attack, the cheeks puffing
            float attack = (float) Math.exp(-since / 0.07);
            float body = 0.55F + 0.45F * (float) Math.exp(-since / 0.6);
            float loud = Mth.clamp(0.45F + 0.55F * song.strumAt(t) + 0.3F * lift, 0, 1);
            float puff = up * (0.35F + 0.65F * loud * body);
            CoreGeoBone mouth = bone("mouth");
            if (mouth != null) mouth.setScaleX(0.55F);
            CoreGeoBone lip = bone("lip_low");
            if (lip != null) lip.setPosY(lip.getPosY() + 0.25F - 0.3F * attack);
            for (String c : new String[]{"cheek_r", "cheek_l"}) {
                CoreGeoBone cb = bone(c);
                if (cb == null) continue;
                cb.setScaleX(0.85F + 0.3F * puff);
                cb.setScaleY(0.85F + 0.3F * puff);
                cb.setScaleZ(0.4F + 2.2F * puff + 0.4F * attack);
            }
            rot(bone("torso"), -5 * lift - 2 * attack, 0, 0);
        } else {
            mouthOpen(1.6F * mm.breath, 1F);
            rot(bone("torso"), -4 * mm.breath, 0, 0);
            // resting: nodding along
            rot(bone("body"), 4 * (float) Math.pow(1 - (beat - Math.floor(beat)), 2), 0, 0);
        }
    }

    // ------------------------------------------------------------------ the violins

    private void violin(Mem mm, Song song, BandPoses poses, double t, double beat, float dt) {
        mm.fiddle = approach(mm.fiddle, song.fiddleAt(t + 0.3) > 0.5F || song.fiddleAt(t - 0.2) > 0.5F ? 1 : 0, 0.1F, dt);
        float f = smooth(mm.fiddle);
        float p = song.bowAt(t);
        BandPoses.Arm bowArm = poses.bowAt(p);
        BandPoses.Arm restR = new BandPoses.Arm(-20, -20, 6, 1.2F, 0, 0, 0);
        BandPoses.Arm r = restR.lerp(bowArm, f);
        BandPoses.apply(this::bone, "r", r);
        CoreGeoBone bowBone = bone("bow");
        if (bowBone != null) BandPoses.set(bowBone, r.px(), r.py(), r.pz());
        // the left hand with the melody, a vibrato on the long notes
        int lead = song.leadAt(t);
        if (lead > 0) mm.neck = approach(mm.neck, Mth.clamp((lead - 62) / 14F, 0, 1), 0.35F, dt);
        int n = song.noteIndex(t);
        double since = t - song.noteTime(n);
        float vib = since > 0.25 ? 1.6F * (float) Math.sin((t - song.noteTime(n)) * Math.PI * 2 * 6) * Mth.clamp((float) (since - 0.25) * 4, 0, 1) : 0;
        BandPoses.Arm restL = new BandPoses.Arm(-40, 10, -16, 1.2F, 0, 0, 0);
        BandPoses.apply(this::bone, "l", restL.lerp(poses.violinLow.lerp(poses.violinHigh, mm.neck).plus(0, vib * f, 0), f));
        CoreGeoBone vio = bone("violin");
        if (vio != null) BandPoses.set(vio, 40 * (1 - f), 0, 0);
        // into the strokes: leaning with the bow, the head tilted over the fiddle
        int dir = song.bowDirection(t);
        rot(bone("torso"), 0, 0, f * (3 + 1.5F * dir * (float) Math.sin(Math.PI * Mth.clamp(p, 0, 1))));
    }

    // ------------------------------------------------------------------ the coro

    private void coro(Mem mm, Song song, BandPoses poses, double t, double beat, float dt) {
        float h = song.harmonyAt(t + 0.15);
        mm.mic = approach(mm.mic, h > 0.3F ? 1 : 0, 0.18F, dt);
        float up = smooth(mm.mic);
        BandPoses.apply(this::bone, "l", poses.micDown.lerp(poses.micUp, up));
        // the maraca on the eighths
        float shake = (float) Math.sin(Math.PI * 2 * 2 * beat);
        CoreGeoBone ar = bone("arm_r");
        if (ar != null) BandPoses.set(ar, -40 + 14 * shake, -10, 12 + 4 * (float) Math.sin(Math.PI * beat));
        float sing = Mth.clamp(song.harmonyAt(t) * up, 0, 1);
        if (sing > 0.02F) sing(song, t, sing);
        // swaying together, a step on each half bar
        rot(bone("torso"), 0, 0, 4 * (float) Math.sin(Math.PI * beat / 2));
    }

    // ------------------------------------------------------------------ helpers

    private void blink(MariachiEntity m, float partial, BandState s) {
        if (s.asleep()) return;
        int period = 70 + Math.floorMod(m.getId() * 31, 60);
        float phase = (m.tickCount + partial + Math.floorMod(m.getId() * 17, period)) % period;
        if (phase < 3) scale(bone("eyelids"), 1);
    }

    // ------------------------------------------------------------------ talking

    /** voice/band.json (tools/gen_band_voice.py): each recorded line's loudness, {@link #talkRate} a second. */
    @org.jetbrains.annotations.Nullable private static java.util.Map<String, float[]> talkEnv;
    private static float talkRate = 25;

    private static java.util.Map<String, float[]> talkEnv() {
        if (talkEnv != null) return talkEnv;
        talkEnv = new java.util.HashMap<>();
        try (java.io.InputStream in = MariachiModel.class.getResourceAsStream("/assets/papon/voice/band.json")) {
            if (in != null) {
                com.google.gson.JsonObject j = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                talkRate = j.get("rate").getAsFloat();
                for (var e : j.getAsJsonObject("lines").entrySet()) {
                    com.google.gson.JsonArray a = e.getValue().getAsJsonArray();
                    float[] env = new float[a.size()];
                    for (int i = 0; i < env.length; i++) env[i] = a.get(i).getAsFloat();
                    talkEnv.put(e.getKey(), env);
                }
            }
        } catch (Exception ignored) {}
        return talkEnv;
    }

    /** A recorded line of his: the mouth opens with its loudness (the clip runs faster at a higher pitch), on top of the pose's. */
    private void talk(MariachiEntity m, float partial) {
        String sp = m.getSpeech();
        if (sp.isEmpty()) return;
        int sep = sp.indexOf('\u001f');
        String key = sep < 0 ? sp : sp.substring(0, sep);
        if (!key.startsWith("speech.papon.band.")) return;
        float[] env = talkEnv().get(key.substring("speech.papon.band.".length()));
        if (env == null) return;
        float at = (m.speechAge() + partial) / 20F * m.voicePitch() * talkRate;
        int i = (int) at;
        if (i >= env.length - 1) return;
        float v = Mth.lerp(at - i, env[i], env[i + 1]);
        mouthOpen(2.2F * v, 1F - 0.12F * v);
    }

    /** Eases {@code v} toward {@code to} at {@code rate} per tick, frame-rate independent. */
    private static float approach(float v, float to, float rate, float dt) {
        return v + (to - v) * (1 - (float) Math.pow(1 - rate, dt));
    }

    private static float smooth(float k) {
        k = Mth.clamp(k, 0, 1);
        return k * k * (3 - 2 * k);
    }

    private CoreGeoBone bone(String name) { return getAnimationProcessor().getBone(name); }

    private static void rot(CoreGeoBone b, float x, float y, float z) {
        if (b == null) return;
        b.setRotX(b.getRotX() - x * Mth.DEG_TO_RAD);
        b.setRotY(b.getRotY() - y * Mth.DEG_TO_RAD);
        b.setRotZ(b.getRotZ() + z * Mth.DEG_TO_RAD);
    }

    private static void add(CoreGeoBone b, float rx, float ry, float rz, float px, float py, float pz) {
        if (b == null) return;
        rot(b, rx, ry, rz);
        b.setPosX(b.getPosX() + px);
        b.setPosY(b.getPosY() + py);
        b.setPosZ(b.getPosZ() + pz);
    }

    private static void scale(CoreGeoBone b, float k) {
        if (b == null) return;
        b.setScaleX(k);
        b.setScaleY(k);
        b.setScaleZ(k);
    }
}
