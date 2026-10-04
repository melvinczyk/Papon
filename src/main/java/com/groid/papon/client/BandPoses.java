package com.groid.papon.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.groid.papon.PaponMod;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.state.BoneSnapshot;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * The band's hand poses on their instruments, solved on the rig by tools/band_ik.py and written
 * to assets/papon/song/band_poses.json: the bowing arm along the bow (frog to tip), the violinist's
 * left hand low and high on the neck, both hands on the trumpet (up at the lips, lifted for the
 * high notes, lowered to rest), the singer's strumming hand and his fretting hand low and high on
 * the requinto, and the coro's hand mic up under the mouth and down at the chest.
 *
 * Angles are in the animation files' convention (degrees); {@link #apply} sets an arm the way
 * GeckoLib would from a keyframe, stretch included (MariachiModel blends the poses first).
 */
public final class BandPoses {
    /** An arm: rotation and stretch (the hand slides (k - 1) * 6.5 px down the arm), and what it holds, turned. */
    public record Arm(float x, float y, float z, float k, float px, float py, float pz) {
        static final Arm REST = new Arm(0, 0, 0, 1, 0, 0, 0);

        public Arm lerp(Arm o, float t) {
            return new Arm(Mth.lerp(t, x, o.x), Mth.lerp(t, y, o.y), Mth.lerp(t, z, o.z), Mth.lerp(t, k, o.k),
                    Mth.lerp(t, px, o.px), Mth.lerp(t, py, o.py), Mth.lerp(t, pz, o.pz));
        }

        public Arm plus(float dx, float dy, float dz) {
            return new Arm(x + dx, y + dy, z + dz, k, px, py, pz);
        }

        public Arm stretched(float kk) {
            return new Arm(x, y, z, kk, px, py, pz);
        }
    }

    public final Arm[] bow;
    public final float[] bowAt;
    public final Arm violinLow, violinHigh;
    public final Arm trumpetPlayR, trumpetPlayL, trumpetLiftR, trumpetLiftL, trumpetDownR, trumpetDownL;
    public final float trumpetPlay, trumpetLift, trumpetDown;
    public final Arm strum, fretLow, fretHigh;
    public final Arm micUp, micDown;

    @Nullable private static BandPoses instance;
    private static boolean tried;

    private BandPoses(JsonObject j) {
        JsonArray b = j.getAsJsonArray("bow");
        bow = new Arm[b.size()];
        bowAt = new float[b.size()];
        for (int i = 0; i < bow.length; i++) {
            bow[i] = arm(b.get(i).getAsJsonObject());
            bowAt[i] = b.get(i).getAsJsonObject().get("p").getAsFloat();
        }
        JsonObject v = j.getAsJsonObject("violin_l");
        violinLow = arm(v.getAsJsonObject("low"));
        violinHigh = arm(v.getAsJsonObject("high"));
        JsonObject t = j.getAsJsonObject("trumpet");
        trumpetPlayR = arm(t.getAsJsonObject("play").getAsJsonObject("r"));
        trumpetPlayL = arm(t.getAsJsonObject("play").getAsJsonObject("l"));
        trumpetLiftR = arm(t.getAsJsonObject("lift").getAsJsonObject("r"));
        trumpetLiftL = arm(t.getAsJsonObject("lift").getAsJsonObject("l"));
        trumpetDownR = arm(t.getAsJsonObject("down").getAsJsonObject("r"));
        trumpetDownL = arm(t.getAsJsonObject("down").getAsJsonObject("l"));
        trumpetPlay = t.getAsJsonObject("play").get("trumpet").getAsFloat();
        trumpetLift = t.getAsJsonObject("lift").get("trumpet").getAsFloat();
        trumpetDown = t.getAsJsonObject("down").get("trumpet").getAsFloat();
        JsonObject r = j.getAsJsonObject("requinto");
        strum = arm(r.getAsJsonObject("strum"));
        fretLow = arm(r.getAsJsonObject("low"));
        fretHigh = arm(r.getAsJsonObject("high"));
        JsonObject c = j.getAsJsonObject("coro");
        micUp = arm(c.getAsJsonObject("up"));
        micDown = arm(c.getAsJsonObject("down"));
    }

    private static Arm arm(JsonObject o) {
        JsonArray r = o.getAsJsonArray("rot");
        JsonElement p = o.get("prop");
        JsonArray pa = p != null ? p.getAsJsonArray() : null;
        return new Arm(r.get(0).getAsFloat(), r.get(1).getAsFloat(), r.get(2).getAsFloat(), o.get("k").getAsFloat(),
                pa != null ? pa.get(0).getAsFloat() : 0, pa != null ? pa.get(1).getAsFloat() : 0, pa != null ? pa.get(2).getAsFloat() : 0);
    }

    @Nullable
    public static synchronized BandPoses get() {
        if (!tried) {
            tried = true;
            try (InputStream in = BandPoses.class.getResourceAsStream("/assets/papon/song/band_poses.json")) {
                if (in != null) instance = new BandPoses(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (Exception e) {
                PaponMod.LOG.warn("[band] could not read the band's poses", e);
            }
        }
        return instance;
    }

    /** The bowing arm with the bow at {@code p} (0 frog .. 1 tip on the string). */
    public Arm bowAt(float p) {
        p = Mth.clamp(p, 0, 1);
        for (int i = 0; i < bow.length - 1; i++) {
            if (p <= bowAt[i + 1]) {
                float u = (p - bowAt[i]) / Math.max(1e-4F, bowAt[i + 1] - bowAt[i]);
                return bow[i].lerp(bow[i + 1], u);
            }
        }
        return bow[bow.length - 1];
    }

    /** A stretched arm thins out (gen_band_anims.girth). */
    public static float girth(float k) {
        return Mth.clamp(1 - (k - 1) * 0.26F, 0.55F, 1F);
    }

    /** Sets arm_{side} (rotation), its mesh (stretch, girth) and hand (slid to the end of it) outright. */
    public static void apply(Function<String, CoreGeoBone> bones, String side, Arm a) {
        CoreGeoBone arm = bones.apply("arm_" + side);
        if (arm != null) set(arm, a.x, a.y, a.z);
        CoreGeoBone mesh = bones.apply("arm_" + side + "_mesh");
        if (mesh != null) {
            float g = girth(a.k);
            mesh.setScaleX(g);
            mesh.setScaleY(a.k);
            mesh.setScaleZ(g);
        }
        CoreGeoBone hand = bones.apply("hand_" + side);
        if (hand != null) hand.setPosY(-(a.k - 1) * 6.5F);
    }

    /** A bone's rotation outright: its rest rotation plus (x, y, z) in the animation files' convention. */
    public static void set(CoreGeoBone b, float x, float y, float z) {
        BoneSnapshot rest = b.getInitialSnapshot();
        b.setRotX(rest.getRotX() - x * Mth.DEG_TO_RAD);
        b.setRotY(rest.getRotY() - y * Mth.DEG_TO_RAD);
        b.setRotZ(rest.getRotZ() + z * Mth.DEG_TO_RAD);
    }

    @FunctionalInterface
    public interface Function<A, B> {
        @Nullable B apply(A a);
    }
}
