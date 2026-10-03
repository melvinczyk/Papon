package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.boss.BandRole;
import com.groid.papon.boss.BandState;
import com.groid.papon.boss.MariachiEntity;
import com.groid.papon.boss.Song;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

import java.util.HashMap;
import java.util.Map;

/**
 * The band's model (gen_band.py) and the music laid over its animations while they play, all from
 * the song's own data at the moment SongPlayer is at ({@link Song}):
 *   the singer   lip sync: Rhubarb's mouth shapes (A-H, X) eased into one another, opened wider the
 *                louder he sings; brows up on the high notes, leaning back into the long ones;
 *                strumming on the guitar's real attacks
 *   guitars      the same strums; violins bow a stroke a beat; trumpets raise their horns only
 *                when the song is loud and rest them in the quiet parts
 *   everyone     a foot tapping and a bounce on the beat; a blink now and then
 * Angles here are in the animation files' convention (GeckoLib loads keyframe rotations as -x, -y,
 * z in radians, so that is how they are added).
 */
public class MariachiModel extends GeoModel<MariachiEntity> {
    private static final ResourceLocation GEO = PaponMod.id("geo/entity/mariachi.geo.json");
    private static final ResourceLocation TEX = PaponMod.id("textures/entity/mariachi.png");
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

    /** Per member, how far the trumpet is lowered (eased between the loud and the quiet parts). */
    private final Map<Integer, Float> hornDown = new HashMap<>();

    @Override
    public ResourceLocation getModelResource(MariachiEntity m) {
        return GEO;
    }

    @Override
    public ResourceLocation getTextureResource(MariachiEntity m) {
        return TEX;
    }

    @Override
    public ResourceLocation getAnimationResource(MariachiEntity m) {
        return ANIM;
    }

    @Override
    public void setCustomAnimations(MariachiEntity m, long instanceId, AnimationState<MariachiEntity> state) {
        float partial = state.getPartialTick();
        BandState s = m.getBandState();
        blink(m, partial, s);
        if (s != BandState.PLAY) return;
        Song song = Song.get();
        double t = SongPlayer.songTime(partial);
        if (song == null || Double.isNaN(t)) return;
        BandRole role = m.getRole();
        double beat = song.beatAt(t);
        if (beat < 0) beat = 0;
        int b = (int) Math.floor(beat);
        float ph = (float) (beat - b);
        float energy = SongPlayer.energy();
        // the beat: a bounce, the right foot tapping
        float bounce = (1 - ph) * (1 - ph) * (0.35F + 0.5F * energy);
        add(bone("root"), 0, 0, 0, 0, bounce, 0);
        rot(bone("leg_r"), b % 2 == 0 ? -14 * (float) Math.pow(1 - ph, 4) : 0, 0, 0);
        float strum = song.strumAt(t);
        switch (role.kind) {
            case "singer" -> {
                strum(strum, beat);
                sing(song, t);
            }
            case "guitar" -> strum(strum, beat);
            case "violin" -> {
                float stroke = (float) Math.cos(Math.PI * beat);
                rot(bone("arm_r"), 0, 24 * stroke, 0);
                rot(bone("torso"), 0, 0, 3 * stroke);
            }
            case "trumpet" -> {
                float down = hornDown.getOrDefault(m.getId(), 1F);
                float want = energy >= 0.7F ? 0F : 1F;
                down += (want - down) * 0.04F;
                hornDown.put(m.getId(), down);
                float up = 1 - down;
                rot(bone("trumpet"), 52 * down - 4 * up * (float) Math.sin(Math.PI * 2 * ph), 0, 0);
                rot(bone("arm_r"), 50 * down, 18 * down, 0);
                rot(bone("arm_l"), 50 * down, -18 * down, 0);
                rot(bone("torso"), -6 * up * (float) Math.sin(Math.PI * 2 * beat / 4), 0, 0);
            }
            default -> {}
        }
    }

    /** The strumming hand, down and up on the eighths, as hard as the guitars are hitting. */
    private void strum(float strum, double beat) {
        float amp = 8 + 26 * Mth.clamp(strum, 0, 1);
        float swing = (float) Math.sin(Math.PI * 2 * 2 * beat);
        rot(bone("arm_r"), amp * swing, 0, 0);
    }

    private void sing(Song song, double t) {
        char sh = song.mouthAt(t);
        Shape now = SHAPES.getOrDefault(sh, SHAPES.get('X'));
        Shape was = SHAPES.getOrDefault(song.mouthBefore(t), SHAPES.get('X'));
        float k = (float) Mth.clamp(song.mouthSince(t) / 0.07, 0, 1);
        k = k * k * (3 - 2 * k);
        float v = song.voiceAt(t);
        float open = Mth.lerp(k, was.open, now.open) * (0.6F + 0.75F * v);
        if (v < 0.03F) open *= v / 0.03F;
        float width = Mth.lerp(k, was.width, now.width);
        float teeth = Mth.lerp(k, was.teeth, now.teeth) * Mth.clamp(open, 0, 1);
        float tongue = Mth.lerp(k, was.tongue, now.tongue) * Mth.clamp(open - 0.5F, 0, 1);
        CoreGeoBone lip = bone("lip_low");
        if (lip != null) lip.setPosY(lip.getPosY() - open);
        CoreGeoBone in = bone("mouth_in");
        if (in != null) in.setScaleY(Math.max(0.001F, open - 0.2F));
        scale(bone("teeth"), teeth);
        scale(bone("tongue"), tongue);
        CoreGeoBone mouth = bone("mouth");
        if (mouth != null) mouth.setScaleX(width);
        // the high notes: brows up, head (all of him) back into the long loud ones
        float pitch = song.pitchAt(t);
        float lift = Mth.clamp(pitch / 7F, -0.3F, 1F) * Math.min(1F, v * 2);
        for (String brow : new String[]{"brow_r", "brow_l"}) {
            CoreGeoBone br = bone(brow);
            if (br != null) br.setPosY(br.getPosY() + lift * 0.9F);
        }
        rot(bone("torso"), -9 * v * Math.max(0, lift), 0, 0);
    }

    private void blink(MariachiEntity m, float partial, BandState s) {
        if (s.asleep()) return;
        int period = 70 + Math.floorMod(m.getId() * 31, 60);
        float phase = (m.tickCount + partial + Math.floorMod(m.getId() * 17, period)) % period;
        if (phase < 3) scale(bone("eyelids"), 1);
    }

    private CoreGeoBone bone(String name) {
        return getAnimationProcessor().getBone(name);
    }

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
