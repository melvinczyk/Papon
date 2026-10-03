package com.groid.papon.client;

import com.groid.papon.boss.BossState;
import com.groid.papon.boss.PaponBossEntity;
import com.groid.papon.boss.VoiceLines;
import net.minecraft.util.Mth;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationProcessor;

import java.util.HashMap;
import java.util.Map;

/**
 * Chef Papon's face (the rig is gen_vendor.face_rig), laid over his animations every frame by
 * {@link BossModel}:
 *
 *   talking   phase two, a line in his cloned voice (boss/VoiceLines): the lips follow its Rhubarb mouth
 *             shapes (the mariachi singer's, MariachiModel) eased into one another and opened as wide as
 *             he is loud; his brows jump on the high notes and the stressed syllables, and he nods into
 *             them; the line's mood sets his eyes, brows and the corners of his mouth
 *   silent    in phase two a mood from what he is doing (pained on his knee, blissful under the salt,
 *             smouldering through the aura farm, with a wink, fuming at the band); in phase one only
 *             the drunk eyelids
 *   always    a blink every few seconds
 *
 * Expressions are eased (a fraction a frame, by real time), so moods melt into each other. Angles are in
 * the animation files' convention (rotation X and Y flipped as GeckoLib loads them, Z as is): a brow's
 * tilt is + for a scowl (inner end down), - for worry (inner end up).
 */
public final class BossFace {
    private BossFace() {}

    /** Rhubarb's shapes: how open (px), how wide, teeth, tongue (as MariachiModel). */
    private record Shape(float open, float width, float teeth, float tongue) {}

    private static final Map<Character, Shape> SHAPES = new HashMap<>();

    static {
        SHAPES.put('X', new Shape(0F, 1.0F, 0, 0));
        SHAPES.put('A', new Shape(0F, 0.9F, 0, 0));
        SHAPES.put('B', new Shape(0.9F, 1.06F, 1, 0));
        SHAPES.put('C', new Shape(1.6F, 1.04F, 1, 0));
        SHAPES.put('D', new Shape(2.3F, 1.12F, 1, 0.4F));
        SHAPES.put('E', new Shape(1.7F, 0.78F, 0, 0));
        SHAPES.put('F', new Shape(1.1F, 0.6F, 0, 0));
        SHAPES.put('G', new Shape(0.8F, 1.0F, 1, 0));
        SHAPES.put('H', new Shape(1.6F, 0.95F, 1, 1));
    }

    /**
     * A look: eyelids (0 open .. 1 the eye covered) right / left; the corners of the mouth (px, + up);
     * brows lifted (px) right / left and tilted (deg); how wide the mouth opens and how wide it stretches;
     * his whole head (body) tilted to one side (deg); a bead of sweat.
     */
    private record Look(float lidR, float lidL, float cornerR, float cornerL, float browR, float browL, float tilt,
                        float open, float width, float lean, float sweat) {}

    private static final Look NEUTRAL = new Look(0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0);
    private static final Map<String, Look> MOODS = new HashMap<>();

    static {
        MOODS.put("warm", new Look(0.2F, 0.2F, 0.6F, 0.6F, 0.5F, 0.5F, -5, 1, 1.04F, 3, 0));
        MOODS.put("smug", new Look(0.45F, 0.35F, 0.9F, 0.15F, 0F, 1.0F, 0, 0.95F, 1, -4, 0));
        MOODS.put("sly", new Look(0.4F, 0.4F, 0.7F, 0.3F, 0.2F, 0.4F, 6, 1, 1, 4, 0));
        MOODS.put("angry", new Look(0.05F, 0.05F, -0.6F, -0.6F, 0, 0, 14, 1.15F, 1.05F, 0, 0));
        MOODS.put("shout", new Look(0, 0, -0.2F, -0.2F, 1.2F, 1.2F, 6, 1.3F, 1.1F, 0, 0));
        MOODS.put("pained", new Look(0.5F, 0.6F, -0.6F, -0.4F, 0.6F, 0.6F, -14, 0.9F, 1, -3, 1));
        MOODS.put("surprised", new Look(0, 0, -0.3F, -0.3F, 1.4F, 1.4F, -6, 1.1F, 0.95F, 0, 0));
        MOODS.put("sad", new Look(0.55F, 0.55F, -0.8F, -0.8F, 0.4F, 0.4F, -16, 0.8F, 0.95F, 2, 0));
        MOODS.put("drunk", new Look(0.6F, 0.35F, 0.4F, -0.3F, 0.3F, 0.8F, 0, 1.05F, 1, 0, 0));
        MOODS.put("bliss", new Look(1.0F, 1.0F, 0.7F, 0.7F, 0.8F, 0.8F, -6, 1, 1, 2, 0));
        MOODS.put("smoulder", new Look(0.5F, 0.45F, 0.8F, 0.2F, 0.1F, 0.9F, 2, 1, 1, -5, 0));
    }

    /** What his face is doing now, per boss (eased toward the look he should have). */
    private static final class Face {
        float lidR, lidL, cornerR, cornerL, browR, browL, tilt, open = 1, width = 1, lean, sweat;
        double last = Double.NaN;
        float mouth;            // the eased mouth opening, px (after the line ends it closes softly)
    }

    private static final Map<Integer, Face> FACES = new HashMap<>();

    public static void apply(AnimationProcessor<PaponBossEntity> anim, PaponBossEntity boss, float partial) {
        Face f = FACES.computeIfAbsent(boss.getId(), k -> new Face());
        if (FACES.size() > 16) FACES.keySet().removeIf(id -> id != boss.getId());
        double now = boss.tickCount + partial;
        float dt = Double.isNaN(f.last) ? 1F : (float) Mth.clamp(now - f.last, 0, 4);
        f.last = now;

        VoiceLines.Line line = boss.talking();
        double t = line == null ? -1 : boss.talkTime(partial);
        boolean saying = line != null && t >= 0 && t <= line.length() + 0.1;

        Look want = saying ? MOODS.getOrDefault(line.mood(), NEUTRAL) : silentLook(boss);
        float k = 1F - (float) Math.exp(-dt * 0.45F);              // ~ a third of the way a tick
        f.lidR += (want.lidR - f.lidR) * k;
        f.lidL += (want.lidL - f.lidL) * k;
        f.cornerR += (want.cornerR - f.cornerR) * k;
        f.cornerL += (want.cornerL - f.cornerL) * k;
        f.browR += (want.browR - f.browR) * k;
        f.browL += (want.browL - f.browL) * k;
        f.tilt += (want.tilt - f.tilt) * k;
        f.open += (want.open - f.open) * k;
        f.width += (want.width - f.width) * k;
        f.lean += (want.lean - f.lean) * k;
        f.sweat += (want.sweat - f.sweat) * k;

        // ---- the mouth
        float open = 0, width = 1, teeth = 0, tongue = 0, pitch = 0, accent = 0, loud = 0;
        if (saying) {
            Shape sh = SHAPES.getOrDefault(line.mouthAt(t), SHAPES.get('X'));
            Shape was = SHAPES.getOrDefault(line.mouthBefore(t), SHAPES.get('X'));
            float e = (float) Mth.clamp(line.mouthSince(t) / 0.06, 0, 1);
            e = e * e * (3 - 2 * e);
            loud = line.envAt(t);
            open = Mth.lerp(e, was.open, sh.open) * (0.55F + 0.7F * loud) * f.open;
            if (loud < 0.04F) open *= loud / 0.04F;
            width = Mth.lerp(e, was.width, sh.width);
            teeth = Mth.lerp(e, was.teeth, sh.teeth);
            tongue = Mth.lerp(e, was.tongue, sh.tongue);
            pitch = line.pitchAt(t);
            accent = line.accentAt(t);
        }
        // never more than 2.6 px (past that the lower lip would sink behind his apron)
        open = Math.min(open, 2.6F);
        f.mouth += (open - f.mouth) * (open > f.mouth ? 1F : 1F - (float) Math.exp(-dt * 1.4F));
        open = f.mouth;
        width *= f.width;
        teeth *= Mth.clamp(open, 0, 1);
        tongue *= Mth.clamp(open - 0.5F, 0, 1);

        CoreGeoBone lip = anim.getBone("lip_low");
        if (lip != null) lip.setPosY(-open);
        CoreGeoBone in = anim.getBone("mouth_in");
        if (in != null) in.setScaleY(Math.max(0.001F, open - 0.2F));
        scale(anim.getBone("teeth"), teeth);
        scale(anim.getBone("tongue"), tongue);
        CoreGeoBone mouth = anim.getBone("mouth");
        if (mouth != null) mouth.setScaleX(width);
        // a smile lifts the corners off the line (and a little out); a scowl drops them, more as he opens up
        corner(anim.getBone("mouth_corner_r"), f.cornerR, open, -1);
        corner(anim.getBone("mouth_corner_l"), f.cornerL, open, 1);

        // ---- the eyes: the mood's lids, a blink now and then (a wink in the aura farm)
        float blink = blink(boss, now);
        float wink = wink(boss, partial);
        lid(anim.getBone("lid_r"), Math.max(f.lidR, blink));
        lid(anim.getBone("lid_l"), Math.max(f.lidL, Math.max(blink, wink)));

        // ---- the brows: the mood, up on the high notes, a pop on the stressed syllables
        float lift = (Mth.clamp(pitch / 6F, -0.3F, 1F) * Math.min(1F, loud * 2) + accent * 0.5F);
        brow(anim.getBone("brow_r"), f.browR + lift, f.tilt);
        brow(anim.getBone("brow_l"), f.browL + lift, -f.tilt);

        // ---- the head (his whole body): leaning into the line, a nod on each stress, tilted by the mood
        CoreGeoBone body = anim.getBone("body");
        if (body != null) {
            float nod = accent * 4F + (saying ? loud * 1.5F : 0);
            body.setRotX(body.getRotX() - nod * Mth.DEG_TO_RAD);          // + in the files' convention: forward
            body.setRotZ(body.getRotZ() + f.lean * Mth.DEG_TO_RAD);
        }
        if (f.sweat > 0.5F) scale(anim.getBone("sweat"), 1);
    }

    /** The look he wears when he is not talking. */
    private static Look silentLook(PaponBossEntity boss) {
        BossState s = boss.getBossState();
        int t = boss.ticksInState();
        if (s == BossState.DEATH) return MOODS.get("sad");
        if (s == BossState.EXIT_STUMBLE || s == BossState.STAGGER) return MOODS.get("drunk");
        if (!boss.isPhaseTwo()) return NEUTRAL;
        return switch (s) {
            case P2_KNEEL -> MOODS.get("pained");
            case P2_TALK -> t < VoiceLines.beat("talk.enough", 20) ? MOODS.get("pained") : MOODS.get("smug");
            case P2_SEASON -> t >= 20 && t < 70 ? MOODS.get("bliss") : MOODS.get("warm");
            case P2_SCREAM -> MOODS.get("shout");
            case P2_ARRIVE, P2_RESUME -> MOODS.get("sly");
            case P2_NOTICE -> t < VoiceLines.beat("notice.fume", 40) ? MOODS.get("surprised") : MOODS.get("angry");
            case P2_MARCH, P2_WAKE, P2_KICK -> MOODS.get("angry");
            case P2_WAIT -> MOODS.get("sly");
            case P2_COUNT, P2_SHOWTIME -> MOODS.get("bliss");
            case P2_APOLOGY -> t >= VoiceLines.beat("apology.aura", 110) ? MOODS.get("smoulder") : MOODS.get("warm");
            case IDLE -> MOODS.get("sly");
            default -> MOODS.get("angry");                // fighting
        };
    }

    /** 0..1: a blink every 3-5 s (a quick close, a slower open). */
    private static float blink(PaponBossEntity boss, double now) {
        int period = 64 + Math.floorMod(boss.getId() * 37, 40);
        double ph = (now + Math.floorMod(boss.getId() * 13, period)) % period;
        if (ph < 1.5) return (float) (ph / 1.5);
        if (ph < 4.5) return (float) (1 - (ph - 1.5) / 3.0);
        return 0;
    }

    /** The wink over his shoulder in the aura farm (P2_APOLOGY, beat apology.wink): his left eye. */
    private static float wink(PaponBossEntity boss, float partial) {
        if (boss.getBossState() != BossState.P2_APOLOGY) return 0;
        int at = VoiceLines.beat("apology.wink", 144);
        float t = boss.ticksInState() + partial;
        if (t < at || t > at + 16) return 0;
        float u = (t - at) / 16F;
        return u < 0.25F ? u / 0.25F : u < 0.7F ? 1 : 1 - (u - 0.7F) / 0.3F;
    }

    private static void lid(CoreGeoBone b, float amount) {
        if (b == null) return;
        // the lid hinges 1 px over the eye: 1/3 of its height reaches the top of the eye, 2/3 covers it
        float s = amount <= 0.001F ? 0 : 0.2F + 0.47F * Mth.clamp(amount, 0, 1);
        b.setScaleX(s > 0 ? 1 : 0);
        b.setScaleY(s);
        b.setScaleZ(s > 0 ? 1 : 0);
    }

    private static void corner(CoreGeoBone b, float up, float open, float side) {
        if (b == null) return;
        float y = up > 0 ? up * (1 - 0.25F * Math.min(open, 2F)) : up * (1 + 0.3F * Math.min(open, 2F));
        b.setPosY(y);
        b.setPosX(side * Math.max(0, up) * 0.4F);
        b.setRotZ(-side * up * 22F * Mth.DEG_TO_RAD);
    }

    private static void brow(CoreGeoBone b, float lift, float tilt) {
        if (b == null) return;
        // a scowl brings the inner end down onto the eye: lifted as much (gen_anims.guard_brows' rule)
        float guard = 3F * (float) Math.sin(Math.abs(tilt) * Mth.DEG_TO_RAD);
        b.setPosY(b.getPosY() + Math.max(-0.3F, lift) + guard);
        b.setRotZ(b.getRotZ() + tilt * Mth.DEG_TO_RAD);
    }

    private static void scale(CoreGeoBone b, float k) {
        if (b == null) return;
        b.setScaleX(k);
        b.setScaleY(k);
        b.setScaleZ(k);
    }
}
