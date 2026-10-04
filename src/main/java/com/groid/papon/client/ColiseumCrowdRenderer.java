package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.boss.Coliseum;
import com.groid.papon.boss.ColiseumCrowdEntity;
import com.groid.papon.boss.CrowdMood;
import com.groid.papon.boss.PaponBossEntity;
import com.groid.papon.boss.Song;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.util.RenderUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The crowd of El Coliseo Papa: every fan in the stands, drawn in one go from one baked model
 * (arena_fan, gen_band.py) with no GeckoLib animation per fan. Each fan's pose is worked out here
 * from the crowd's mood, his own dice (Coliseum.Seat.seed: hat, prop, jersey, skin, size, how keen)
 * and the song's beat, blended over from the last mood; then the bones are set and the cubes
 * written straight to the buffer. Over the mood each fan does his own thing now and then
 * ({@link #lively}): jumps with his arms thrown up, pumps a fist, shakes both arms over his head,
 * waves his flag round, turns to his neighbour; the louder the stands, the more often.
 *
 * Half of them carry a little La Bandera Papa on a stick (the cloth drawn by {@link FlagCloth} off the
 * stick's tip, flapping harder the more he waves it).
 *
 * Cheap enough for a thousand-odd Papons: only fans in the view frustum are drawn, faces turned
 * away from the camera are skipped before they are written, the far ones leave out their brows and
 * mouths, and their light is looked up a few seats a frame.
 */
public class ColiseumCrowdRenderer extends EntityRenderer<ColiseumCrowdEntity> {
    private static final ResourceLocation GEO = PaponMod.id("geo/entity/arena_fan.geo.json");
    private static final ResourceLocation TEX = PaponMod.id("textures/entity/arena_fan.png");
    private static final String[] HATS = {"sombrero", "cap", "foam_hat"};
    private static final String[] PROPS = {"foam_finger", "flag", "maraca_r", "maraca_l"};
    private static final float[][] SKINS = {{1, 1, 1}, {0.84F, 0.68F, 0.52F}, {1, 0.74F, 0.68F}, {1, 0.96F, 0.84F},
            {0.8F, 0.64F, 0.84F}, {0.9F, 0.8F, 0.62F}};

    /** The level renderer's culling frustum, caught each frame (ClientEvents). */
    @Nullable public static Frustum frustum;

    private List<Coliseum.Seat> seats;
    private int seatsSeed = Integer.MIN_VALUE;
    private int[] lights = new int[0];
    private int lightCursor;
    @Nullable private BakedGeoModel model;
    private final Map<GeoBone, float[]> rest = new HashMap<>();
    private GeoBone root, torso, body, armR, armL, legR, legL, browR, browL, mouthIn, jersey;
    /** The flag stick's far tip (baked model units): where the cloth hangs from. */
    private final Vector3f flagTip = new Vector3f();
    private VertexConsumer flagVc;
    private float flagTime, flagPhase, flagAmp;
    private final GeoBone[] hats = new GeoBone[HATS.length];
    private final GeoBone[] props = new GeoBone[PROPS.length];

    public ColiseumCrowdRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override
    public ResourceLocation getTextureLocation(ColiseumCrowdEntity entity) {
        return TEX;
    }

    @Override
    public boolean shouldRender(ColiseumCrowdEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    private boolean bind() {
        BakedGeoModel m = GeckoLibCache.getBakedModels().get(GEO);
        if (m == null) return false;
        if (m != model) {
            model = m;
            rest.clear();
            for (GeoBone b : all(m.topLevelBones(), new java.util.ArrayList<>())) {
                rest.put(b, new float[]{b.getRotX(), b.getRotY(), b.getRotZ()});
            }
            root = m.getBone("root").orElse(null);
            torso = m.getBone("torso").orElse(null);
            body = m.getBone("body").orElse(null);
            armR = m.getBone("arm_r").orElse(null);
            armL = m.getBone("arm_l").orElse(null);
            legR = m.getBone("leg_r").orElse(null);
            legL = m.getBone("leg_l").orElse(null);
            browR = m.getBone("brow_r").orElse(null);
            browL = m.getBone("brow_l").orElse(null);
            mouthIn = m.getBone("mouth_in").orElse(null);
            jersey = m.getBone("jersey").orElse(null);
            for (int i = 0; i < HATS.length; i++) hats[i] = m.getBone(HATS[i]).orElse(null);
            for (int i = 0; i < PROPS.length; i++) props[i] = m.getBone(PROPS[i]).orElse(null);
            if (props[1] != null && !props[1].getCubes().isEmpty()) {
                // the stick: its far end (lowest y at rest) is where the cloth starts
                float minY = Float.MAX_VALUE, sx = 0, sz = 0;
                int n = 0;
                for (GeoQuad q : props[1].getCubes().get(0).quads()) {
                    if (q == null) continue;
                    for (GeoVertex v : q.vertices()) {
                        minY = Math.min(minY, v.position().y());
                        sx += v.position().x();
                        sz += v.position().z();
                        n++;
                    }
                }
                if (n > 0) flagTip.set(sx / n, minY, sz / n);
            }
        }
        return root != null && body != null;
    }

    private static List<GeoBone> all(List<GeoBone> bones, List<GeoBone> out) {
        for (GeoBone b : bones) {
            out.add(b);
            all(b.getChildBones(), out);
        }
        return out;
    }

    // ------------------------------------------------------------------ a fan's pose

    /** One fan's pose: angles in the animation files' convention (degrees), positions in pixels. */
    private static final class Pose {
        float rootY, yaw, torsoX, torsoZ, bodyS = 1, armRX, armRY, armRZ, armLX, armLY, armLZ, leg, brow, mouth;

        void set(Pose a, Pose b, float k) {
            rootY = Mth.lerp(k, a.rootY, b.rootY);
            yaw = Mth.lerp(k, a.yaw, b.yaw);
            torsoX = Mth.lerp(k, a.torsoX, b.torsoX);
            torsoZ = Mth.lerp(k, a.torsoZ, b.torsoZ);
            bodyS = Mth.lerp(k, a.bodyS, b.bodyS);
            armRX = Mth.lerp(k, a.armRX, b.armRX);
            armRY = Mth.lerp(k, a.armRY, b.armRY);
            armRZ = Mth.lerp(k, a.armRZ, b.armRZ);
            armLX = Mth.lerp(k, a.armLX, b.armLX);
            armLY = Mth.lerp(k, a.armLY, b.armLY);
            armLZ = Mth.lerp(k, a.armLZ, b.armLZ);
            leg = Mth.lerp(k, a.leg, b.leg);
            brow = Mth.lerp(k, a.brow, b.brow);
            mouth = Mth.lerp(k, a.mouth, b.mouth);
        }
    }

    private final Pose pa = new Pose(), pb = new Pose(), pose = new Pose();
    /** Phase three: the share of fans with a lighter up (ColiseumCrowdEntity.lighters), and this frame's flames (view space). */
    private float lightersNow;
    private final List<Vector3f> flames = new java.util.ArrayList<>();
    @Nullable private Vector3f flameAt;
    private boolean wantFlame;

    /** Whether this fan has his lighter up yet (they spread through the stands as the share grows). */
    private boolean lighter(int seed) {
        return rnd(seed, 13) < lightersNow;
    }

    private static float rnd(int seed, int k) {
        int h = seed * 0x9E3779B1 + k * 0x85EBCA6B;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return (h & 0xFFFF) / 65535F;
    }

    /** Which prop he brought: 0 none, 1 a foam finger, 2 a flag, 3 maracas. Half of them have a flag. */
    static int prop(int seed) {
        int k = (int) (rnd(seed, 11) * 10);
        return k == 2 ? 1 : k >= 3 && k <= 7 ? 2 : k == 8 ? 3 : 0;
    }

    private static void seated(Pose p) {
        p.rootY = -4;
        p.leg = -88;
    }

    private void moodPose(Pose p, CrowdMood mood, int seed, float t, double beat, float angle, float moodT) {
        moodPoseBase(p, mood, seed, t, beat, angle, moodT);
        lively(p, mood, seed, t, prop(seed) == 2);
    }

    private void moodPoseBase(Pose p, CrowdMood mood, int seed, float t, double beat, float angle, float moodT) {
        float r1 = rnd(seed, 1), r2 = rnd(seed, 2), r3 = rnd(seed, 3), r4 = rnd(seed, 4), r5 = rnd(seed, 5), r6 = rnd(seed, 6);
        boolean flag = prop(seed) == 2;
        p.rootY = 0;
        p.yaw = 0;
        p.torsoX = p.torsoZ = 0;
        p.bodyS = 1;
        p.armRX = -8;
        p.armRY = 0;
        p.armRZ = 10;
        p.armLX = -8;
        p.armLY = 0;
        p.armLZ = -10;
        p.leg = 0;
        p.brow = 0;
        p.mouth = 0;
        float bph = (float) (beat - Math.floor(beat));
        int bi = (int) Math.floor(beat);
        switch (mood) {
            case CHEER -> {
                if (r1 > 0.66F) seated(p);
                else p.rootY = 1.1F * Math.abs(Mth.sin(t * (0.2F + 0.1F * r2) + r6 * 6));
                float period = 34 + r2 * 40;
                float ph = ((t + r3 * 200) % period) / period;
                if (ph < 0.3F) {
                    float k = Mth.sin(ph / 0.3F * Mth.PI);
                    if (r4 < 0.5F) {
                        p.armRX = -8 - 160 * k;
                        p.armRZ = 10 + 8 * k;
                    } else {
                        p.armLX = -8 - 160 * k;
                        p.armLZ = -10 - 8 * k;
                    }
                    p.mouth = 2 * k;
                }
                p.torsoZ = 4 * Mth.sin(t * 0.09F + r5 * 6);
                if (flag) flagWave(p, t, r3, 0.6F);
            }
            case ROAR -> {
                // jumping up and down, each at his own rate: off the ground half the time, legs tucked at the top
                float jump = Mth.sin(t * (0.36F + 0.18F * r2) + r6 * 6);
                float air = Math.max(0, jump);
                p.rootY = 5.0F * air;
                p.leg = -22 * air;
                p.bodyS = jump < -0.8F ? 0.95F : 1.04F;
                float w = 18 * Mth.sin(t * (0.5F + 0.2F * r3) + r5 * 6);
                p.armRX = -168 + 8 * air;
                p.armRZ = 30 + w;
                p.armLX = -168 + 8 * air;
                p.armLZ = -30 + w;
                p.torsoX = -6 * air;
                p.mouth = 2.6F;
                p.brow = 0.6F;
                if (flag) flagWave(p, t, r3, 1F);
            }
            case BOO -> {
                if (r1 > 0.7F) seated(p);
                p.torsoX = 10;
                p.armRX = -62 + 10 * Mth.sin(t * 1.1F + r3 * 6);
                p.armRY = -20;
                p.armLX = -40 + 6 * Mth.sin(t * 0.9F + r4 * 6);
                p.armLY = 18;
                p.brow = -0.7F;
                p.mouth = 1.4F;
            }
            case HUSH -> {
                seated(p);
                p.armRX = -28;
                p.armRY = -12;
                p.armLX = -28;
                p.armLY = 12;
                p.torsoZ = 2 * Mth.sin(t * 0.05F + r5 * 6);
                if (r2 < 0.3F) p.yaw = 26 * Mth.sin(t * 0.03F + r3 * 6);      // whispering to the neighbour
            }
            case CHANT -> {
                float k = (float) Math.pow(1 - bph, 2);
                boolean up = bi % 2 == 0 || r1 < 0.2F;
                p.armRX = up ? -100 - 70 * k : -40;
                p.armRZ = 14;
                p.rootY = (r2 < 0.4F ? 2.2F : 0.8F) * k;
                p.mouth = 2.2F * k;
                p.brow = 0.3F;
                if (flag) {
                    p.armLX = up ? -150 - 20 * k : -120;
                    p.armLZ = -18 - 14 * k;
                }
            }
            case WAVE -> {
                float front = (t * 0.11F) % (Mth.PI * 2);
                float d = Math.abs(Mth.wrapDegrees((angle - front) * Mth.RAD_TO_DEG)) * Mth.DEG_TO_RAD;
                float k = d < 0.55F ? Mth.cos(d / 0.55F * Mth.HALF_PI) : 0;
                p.rootY = -4 * (1 - k) + 1.5F * k;
                p.leg = -88 * (1 - k);
                p.armRX = -8 - 165 * k;
                p.armLX = -8 - 165 * k;
                p.armRZ = 10 + 12 * k;
                p.armLZ = -10 - 12 * k;
                p.mouth = 2.4F * k;
            }
            case CLAP -> {
                if (r1 > 0.6F) seated(p);
                float half = (float) ((beat * 2) - Math.floor(beat * 2));
                float c = 1 - Math.min(1, half * 3);
                p.armRX = -80;
                p.armRY = -40 * c - 6;
                p.armLX = -80;
                p.armLY = 40 * c + 6;
                p.rootY += 0.4F * c;
                p.mouth = 0.6F;
            }
            case SWOON -> {
                p.armRX = -150;
                p.armRY = -58;
                p.armLX = -150;
                p.armLY = 58;
                p.torsoZ = 9 * Mth.sin(t * 0.07F + r5 * 6);
                p.brow = 0.8F;
                p.mouth = 1.0F;
                if (r2 < 0.15F && moodT > 30) {
                    float k = Math.min(1, (moodT - 30) / 10F);
                    p.torsoX = -38 * k;
                    p.armRX = -150 + 120 * k;
                    p.armLX = -150 + 120 * k;
                }
            }
            case MOURN -> {
                seated(p);
                p.torsoX = 24;
                p.armRX = -20;
                p.armLX = -20;
                p.brow = 0.4F;
            }
            case GASP -> {
                p.armRX = -168;
                p.armRY = -30;
                p.armLX = -168;
                p.armLY = 30;
                p.brow = 1F;
                p.mouth = 2.6F;
            }
            case LIGHTERS -> {
                // the whole bowl swaying together, slowly, a lighter held high; the rest still sitting, watching
                float sway = Mth.sin(t * 0.07F + angle * 0.5F);
                if (lighter(seed)) {
                    if (r1 > 0.55F) seated(p);
                    p.armRX = -158 - 8 * r2;
                    p.armRZ = 6 + 10 * sway;
                    p.torsoZ = 8 * sway;
                    p.armLX = -20;
                    p.brow = 0.5F;
                } else {
                    // in awe: hands clasped up under the chin, mouth open, brows up
                    seated(p);
                    p.armRX = -120;
                    p.armRY = -48;
                    p.armLX = -120;
                    p.armLY = 48;
                    p.torsoZ = 3 * sway;
                    p.brow = 0.9F;
                    p.mouth = 1.2F;
                }
            }
        }
    }

    /** The flag up over his head, swung from side to side (the cloth flaps harder for it). */
    private static void flagWave(Pose p, float t, float r, float energy) {
        float sw = Mth.sin(t * (0.22F + 0.12F * r) + r * 9);
        p.armLX = -150 - 14 * energy + 8 * Mth.cos(t * 0.31F + r * 5);
        p.armLY = 10 * sw * energy;
        p.armLZ = -20 + 34 * sw * energy;
    }

    /**
     * A fan's own moments over the mood: every so often (more often the louder the stands) he does
     * something, eased in and out over a second or two: jumps twice with both arms thrown up, shakes
     * both arms over his head, pumps a fist, waves his flag round in big circles (or claps over his
     * head), or turns to the fan beside him with a hop. Up off his seat for any of them.
     */
    private static void lively(Pose p, CrowdMood mood, int seed, float t, boolean flag) {
        float energy = switch (mood) {
            case ROAR -> 0.9F;
            case CHEER -> 0.7F;
            case CHANT -> 0.4F;
            case CLAP, BOO -> 0.3F;
            default -> 0;
        };
        if (energy <= 0) return;
        float period = (100 - 55 * energy) * (0.7F + 0.6F * rnd(seed, 20));
        float local = t + rnd(seed, 21) * 600;
        int cycle = (int) (local / period);
        float ph = (local - cycle * period) / period;
        if (rnd(seed * 31 + cycle, 22) > 0.3F + 0.65F * energy) return;
        float span = 0.55F;
        if (ph > span) return;
        float k = ph / span;
        float env = Math.min(1, Mth.sin(k * Mth.PI) * 1.6F);
        int act = (int) (rnd(seed * 17 + cycle, 23) * 5);
        if (mood == CrowdMood.BOO) act = 2;                 // a fist shaken at you
        // up off the seat
        p.rootY = Mth.lerp(env, p.rootY, Math.max(0, p.rootY));
        p.leg = Mth.lerp(env, p.leg, 0);
        switch (act) {
            case 0 -> {
                // two jumps, arms flung up and out
                float hop = Math.abs(Mth.sin(k * Mth.PI * 2));
                p.rootY += 5.5F * hop * env;
                p.leg = -26 * hop * env;
                p.armRX = Mth.lerp(env, p.armRX, -172);
                p.armRZ = Mth.lerp(env, p.armRZ, 38);
                p.armLX = Mth.lerp(env, p.armLX, -172);
                p.armLZ = Mth.lerp(env, p.armLZ, -38);
                p.armRY = Mth.lerp(env, p.armRY, 0);
                p.armLY = Mth.lerp(env, p.armLY, 0);
                p.torsoX = Mth.lerp(env, p.torsoX, -10 * hop);
                p.mouth = Math.max(p.mouth, 2.6F * env);
                p.brow = Math.max(p.brow, 0.7F * env);
            }
            case 1 -> {
                // both arms up, swaying from side to side, a bounce in the knees
                float sw = Mth.sin(t * 0.5F + seed);
                p.armRX = Mth.lerp(env, p.armRX, -165);
                p.armLX = Mth.lerp(env, p.armLX, -165);
                p.armRZ = Mth.lerp(env, p.armRZ, 20 + 26 * sw);
                p.armLZ = Mth.lerp(env, p.armLZ, -20 + 26 * sw);
                p.torsoZ = Mth.lerp(env, p.torsoZ, 9 * sw);
                p.rootY += 1.2F * Math.abs(Mth.sin(t * 0.5F)) * env;
                p.mouth = Math.max(p.mouth, 2.0F * env);
            }
            case 2 -> {
                // a fist pumped three times
                float pump = Math.abs(Mth.sin(k * Mth.PI * 3));
                p.armRX = Mth.lerp(env, p.armRX, -120 - 55 * pump);
                p.armRY = Mth.lerp(env, p.armRY, 0);
                p.armRZ = Mth.lerp(env, p.armRZ, 12);
                p.torsoX = Mth.lerp(env, p.torsoX, mood == CrowdMood.BOO ? 12 : -4 * pump);
                p.rootY += 0.9F * pump * env;
                p.mouth = Math.max(p.mouth, 2.2F * pump * env);
            }
            case 3 -> {
                if (flag) {
                    // the flag round in big circles over his head
                    float a = k * Mth.TWO_PI * 2;
                    p.armLX = Mth.lerp(env, p.armLX, -160 + 22 * Mth.sin(a));
                    p.armLZ = Mth.lerp(env, p.armLZ, -24 + 30 * Mth.cos(a));
                    p.armRX = Mth.lerp(env, p.armRX, -150);
                    p.armRZ = Mth.lerp(env, p.armRZ, 26);
                    p.rootY += 1.6F * Math.abs(Mth.sin(a)) * env;
                } else {
                    // a clap over his head
                    float c = Math.abs(Mth.sin(k * Mth.PI * 5));
                    p.armRX = Mth.lerp(env, p.armRX, -170);
                    p.armLX = Mth.lerp(env, p.armLX, -170);
                    p.armRZ = Mth.lerp(env, p.armRZ, 6 + 22 * c);
                    p.armLZ = Mth.lerp(env, p.armLZ, -6 - 22 * c);
                }
                p.mouth = Math.max(p.mouth, 1.8F * env);
            }
            default -> {
                // turns to his neighbour, a hop, an arm round the air
                float side = rnd(seed, 24) < 0.5F ? -1 : 1;
                p.yaw = Mth.lerp(env, p.yaw, 48 * side);
                p.rootY += 2.4F * Math.abs(Mth.sin(k * Mth.PI * 2)) * env;
                p.armRX = Mth.lerp(env, p.armRX, -140);
                p.armRZ = Mth.lerp(env, p.armRZ, 40);
                p.mouth = Math.max(p.mouth, 2.4F * env);
            }
        }
    }

    /**
     * A fan throwing a potato at you (ColiseumCrowdEntity.pelt): the right arm back over his head
     * through the wind-up, whipped forward as it leaves his hand, a lean into it, a shout; then back
     * to whatever the rest of the stands are doing.
     */
    private static void throwPose(Pose p, float tf) {
        float w = ColiseumCrowdEntity.WINDUP;
        float x, torso, back;
        if (tf < w) {
            float k = tf / w;
            x = Mth.lerp(k * k, p.armRX, -205);
            torso = -10 * k;
            back = 1;
        } else {
            float k = Math.min(1F, (tf - w) / 3F);
            x = Mth.lerp(k, -205, -45);
            torso = Mth.lerp(k, -10, 14);
            back = 1 - Mth.clamp((tf - w - 4) / 6F, 0, 1);
        }
        p.armRX = Mth.lerp(back, p.armRX, x);
        p.armRY = Mth.lerp(back, p.armRY, 0);
        p.armRZ = Mth.lerp(back, p.armRZ, 14);
        p.torsoX = Mth.lerp(back, p.torsoX, torso);
        p.mouth = Mth.lerp(back, p.mouth, 2.4F);
        p.brow = Mth.lerp(back, p.brow, -0.6F);
        if (p.rootY < -1) p.rootY = Mth.lerp(back, p.rootY, 0);     // on his feet to throw
        if (p.leg < -40) p.leg = Mth.lerp(back, p.leg, 0);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(ColiseumCrowdEntity crowd, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        if (!bind()) return;
        if (seats == null || seatsSeed != crowd.seed()) {
            seats = Coliseum.seats(crowd.seed());
            seatsSeed = crowd.seed();
            lights = new int[seats.size()];
            for (int i = 0; i < lights.length; i++) lights[i] = -1;
        }
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = entityRenderDispatcher.camera.getPosition();
        Vec3 at = crowd.getPosition(partial);
        float t = crowd.tickCount + partial;
        float moodT = crowd.moodTicks() + partial;
        float blend = Math.min(1F, moodT / 10F);
        CrowdMood mood = crowd.mood(), prev = crowd.previousMood();
        Song song = SongPlayer.song();
        double st = SongPlayer.songTime(partial);
        double beat = song != null && !Double.isNaN(st) ? Math.max(0, song.beatAt(st)) : t / 9.4;
        // they watch him
        Vec3 look = null;
        PaponBossEntity boss = mc.level.getEntitiesOfClass(PaponBossEntity.class, crowd.getBoundingBox().inflate(80), e -> true)
                .stream().findFirst().orElse(null);
        if (boss != null) look = boss.getPosition(partial).add(0, 1.5, 0);
        if (mood == CrowdMood.HUSH) look = at.add(0, 1, -Coliseum.B + 3);
        lightersNow = crowd.lighters();         // up through a roar too (their fists are up anyway)
        flames.clear();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(TEX));
        flagVc = buffers.getBuffer(FlagCloth.renderType());
        // a few seats' light a frame
        for (int k = 0; k < 24 && lights.length > 0; k++) {
            lightCursor = (lightCursor + 1) % lights.length;
            Coliseum.Seat s = seats.get(lightCursor);
            lights[lightCursor] = LevelRenderer.getLightColor(mc.level, BlockPos.containing(at.x + s.x(), at.y + s.y() + 0.5, at.z + s.z()));
        }
        for (int i = 0; i < seats.size(); i++) {
            Coliseum.Seat s = seats.get(i);
            double wx = at.x + s.x(), wy = at.y + s.y(), wz = at.z + s.z();
            if (frustum != null && !frustum.isVisible(new AABB(wx - 0.6, wy - 0.4, wz - 0.6, wx + 0.6, wy + 2.0, wz + 0.6))) continue;
            double d2 = cam.distanceToSqr(wx, wy, wz);
            boolean near = d2 < 48 * 48;
            int seed = s.seed();
            float angle = (float) Math.atan2(s.z() / (Coliseum.B + 10.0), s.x() / (Coliseum.A + 10.0));
            moodPose(pb, mood, seed, t, beat + rnd(seed, 9) * 0.06, angle, moodT);
            if (blend < 1) {
                moodPose(pa, prev, seed, t, beat, angle, 100);
                pose.set(pa, pb, blend * blend * (3 - 2 * blend));
            } else {
                pose.set(pb, pb, 1);
            }
            int tt = crowd.throwTicks(i);
            if (tt >= 0) throwPose(pose, tt + partial);
            float turn = 0;
            if (look != null) {
                float want = (float) (Mth.atan2(look.z - wz, look.x - wx) * Mth.RAD_TO_DEG) - 90F;
                turn = Mth.clamp(Mth.wrapDegrees(want - s.yaw()), -42F, 42F);
            }
            apply(pose, seed, near);
            flagTime = t;
            flagPhase = rnd(seed, 25) * 6.28F;
            // the harder his arm is swinging it, the harder the cloth flaps
            flagAmp = 0.05F + 0.05F * Math.min(1F, Math.abs(pose.armLZ + 20) / 30F);
            ps.pushPose();
            ps.translate(s.x(), s.y(), s.z());
            ps.mulPose(Axis.YP.rotationDegrees(180F - (s.yaw() + turn + pose.yaw)));
            float size = 0.78F + rnd(seed, 7) * 0.16F;
            ps.scale(size, size, size);
            float[] skin = SKINS[(int) (rnd(seed, 8) * SKINS.length) % SKINS.length];
            int lt = lights[i] < 0 ? light : lights[i];
            wantFlame = lightersNow > 0 && lighter(seed);
            flameAt = null;
            for (GeoBone b : model.topLevelBones()) drawBone(ps, b, vc, lt, skin, near);
            if (wantFlame && flameAt != null) flames.add(flameAt);
            ps.popPose();
        }
        if (!flames.isEmpty()) drawFlames(buffers.getBuffer(RenderType.lightning()), t);
    }

    /**
     * The lighters: a little flame over each raised fist, a hot core in a warm halo (additive, so a bowl full of
     * them reads as a sky of flames in the dark), each flickering on its own. Positions are in view space.
     */
    private void drawFlames(VertexConsumer vc, float t) {
        for (int i = 0; i < flames.size(); i++) {
            Vector3f c = flames.get(i);
            float flick = 0.85F + 0.15F * Mth.sin(t * 1.7F + i * 2.3F) + 0.06F * Mth.sin(t * 4.1F + i);
            // a warm halo (big enough to read as a point of light from across the bowl) round a hot core
            quad(vc, c.x(), c.y() + 0.12F * flick, c.z(), 0.32F * flick, 0.42F * flick, 1F, 0.5F, 0.12F, 0.28F);
            quad(vc, c.x(), c.y() + 0.08F * flick, c.z(), 0.07F, 0.17F * flick, 1F, 0.92F, 0.62F, 0.95F);
        }
    }

    /** A diamond facing the camera (view space: x right, y up) centred on (x, y, z). */
    private static void quad(VertexConsumer vc, float x, float y, float z, float w, float h, float r, float g, float b, float a) {
        vc.vertex(x, y - h, z).color(r, g, b, a).endVertex();
        vc.vertex(x + w, y, z).color(r, g, b, a * 0.4F).endVertex();
        vc.vertex(x, y + h, z).color(r, g, b, a * 0.1F).endVertex();
        vc.vertex(x - w, y, z).color(r, g, b, a * 0.4F).endVertex();
    }

    /** Sets the shared bones for this fan: rest + pose, hats and props per his dice. */
    private void apply(Pose p, int seed, boolean near) {
        root.setPosY(p.rootY);
        set(torso, p.torsoX, 0, p.torsoZ);
        body.setScaleY(p.bodyS);
        set(armR, p.armRX, p.armRY, p.armRZ);
        set(armL, p.armLX, p.armLY, p.armLZ);
        set(legR, p.leg, 0, 0);
        set(legL, p.leg, 0, 0);
        if (browR != null) {
            browR.setHidden(!near);
            browL.setHidden(!near);
            browR.setPosY(p.brow);
            browL.setPosY(p.brow);
        }
        if (mouthIn != null) {
            mouthIn.setHidden(!near || p.mouth < 0.2F);
            mouthIn.setScaleY(Math.max(0.01F, p.mouth));
        }
        int hat = (int) (rnd(seed, 10) * 5);
        for (int i = 0; i < hats.length; i++) if (hats[i] != null) hats[i].setHidden(hat != i + 1);
        int prop = prop(seed);
        if (props[0] != null) props[0].setHidden(prop != 1);
        if (props[1] != null) props[1].setHidden(prop != 2);
        if (props[2] != null) props[2].setHidden(prop != 3);
        if (props[3] != null) props[3].setHidden(prop != 3);
        if (jersey != null) jersey.setHidden(rnd(seed, 12) > 0.33F);
    }

    private void set(@Nullable GeoBone b, float x, float y, float z) {
        if (b == null) return;
        float[] r = rest.get(b);
        b.setRotX(r[0] - x * Mth.DEG_TO_RAD);
        b.setRotY(r[1] - y * Mth.DEG_TO_RAD);
        b.setRotZ(r[2] + z * Mth.DEG_TO_RAD);
    }

    private final Vector4f v4 = new Vector4f();
    private final Vector3f n3 = new Vector3f();

    private void drawBone(PoseStack ps, GeoBone bone, VertexConsumer vc, int light, float[] skin, boolean near) {
        if (bone.isHidden()) return;
        ps.pushPose();
        RenderUtils.prepMatrixForBone(ps, bone);
        boolean tinted = bone == body || bone == armR || bone == armL || bone == legR || bone == legL || bone == browR || bone == browL;
        float r = tinted ? skin[0] : 1, g = tinted ? skin[1] : 1, b = tinted ? skin[2] : 1;
        Matrix4f m = ps.last().pose();
        Matrix3f nm = ps.last().normal();
        if (wantFlame && bone == armR && flameAt == null) {
            // the fist: the middle of the arm cube's end face (down at rest; up over his head with the arm raised)
            for (GeoCube cube : bone.getCubes()) {
                for (GeoQuad quad : cube.quads()) {
                    if (quad == null || quad.normal().y() > -0.9F) continue;
                    float x = 0, y = 0, z = 0;
                    for (GeoVertex vert : quad.vertices()) {
                        m.transform(v4.set(vert.position().x(), vert.position().y(), vert.position().z(), 1));
                        x += v4.x() / 4;
                        y += v4.y() / 4;
                        z += v4.z() / 4;
                    }
                    flameAt = new Vector3f(x, y + 0.06F, z);
                }
                break;
            }
        }
        for (GeoCube cube : bone.getCubes()) {
            for (GeoQuad quad : cube.quads()) {
                if (quad == null) continue;
                nm.transform(n3.set(quad.normal()));
                GeoVertex first = quad.vertices()[0];
                m.transform(v4.set(first.position().x(), first.position().y(), first.position().z(), 1));
                // facing away from the camera (which sits at the origin here): skip it
                if (n3.x() * v4.x() + n3.y() * v4.y() + n3.z() * v4.z() > 0) continue;
                for (GeoVertex vert : quad.vertices()) {
                    Vector3f pos = vert.position();
                    m.transform(v4.set(pos.x(), pos.y(), pos.z(), 1));
                    vc.vertex(v4.x(), v4.y(), v4.z(), r, g, b, 1F, vert.texU(), vert.texV(), OverlayTexture.NO_OVERLAY, light,
                            n3.x(), n3.y(), n3.z());
                }
            }
        }
        if (bone == props[1]) {
            // the cloth: its hoist down the stick from the tip, flying off forward (toward -Z at rest)
            ps.pushPose();
            ps.translate(flagTip.x(), flagTip.y(), flagTip.z());
            ps.mulPose(Axis.YP.rotationDegrees(-90));
            ps.mulPose(Axis.ZP.rotationDegrees(180));
            FlagCloth.draw(ps, flagVc, 0.7F, 0.4F, light, flagTime, flagPhase, flagAmp, true, near ? 7 : 4, near ? 3 : 2);
            ps.popPose();
        }
        for (GeoBone child : bone.getChildBones()) drawBone(ps, child, vc, light, skin, near);
        ps.popPose();
    }
}
