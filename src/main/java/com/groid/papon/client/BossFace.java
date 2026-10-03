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
                        float open, float width, float lean, float sweat, float eyes, float aside, float down) {
        Look(float lidR, float lidL, float cornerR, float cornerL, float browR, float browL, float tilt, float open, float width,
             float lean, float sweat) {
            this(lidR, lidL, cornerR, cornerL, browR, browL, tilt, open, width, lean, sweat, 1, 0, 0);
        }

        /** The same look with its eyes: how open (1 as painted, 2 wide with shock, 0.6 narrowed), a glance aside (px), down (px). */
        Look eyes(float eyes, float aside, float down) {
            return new Look(lidR, lidL, cornerR, cornerL, browR, browL, tilt, open, width, lean, sweat, eyes, aside, down);
        }
    }

    private static final Look NEUTRAL = new Look(0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0);
    private static final Map<String, Look> MOODS = new HashMap<>();

    static {
        MOODS.put("warm", new Look(0.2F, 0.2F, 0.6F, 0.6F, 0.5F, 0.5F, -5, 1, 1.04F, 3, 0).eyes(1.15F, 0, 0));
        MOODS.put("smug", new Look(0.45F, 0.35F, 0.9F, 0.15F, 0F, 1.0F, 0, 0.95F, 1, -4, 0).eyes(0.8F, 0.6F, 0));
        MOODS.put("sly", new Look(0.4F, 0.4F, 0.7F, 0.3F, 0.2F, 0.4F, 6, 1, 1, 4, 0).eyes(0.75F, 0.9F, 0));
        MOODS.put("angry", new Look(0.05F, 0.05F, -0.6F, -0.6F, 0, 0, 14, 1.15F, 1.05F, 0, 0).eyes(0.7F, 0, 0));
        MOODS.put("shout", new Look(0, 0, -0.2F, -0.2F, 1.2F, 1.2F, 6, 1.3F, 1.1F, 0, 0).eyes(1.8F, 0, 0));
        MOODS.put("pained", new Look(0.5F, 0.6F, -0.6F, -0.4F, 0.6F, 0.6F, -14, 0.9F, 1, -3, 1).eyes(0.8F, 0, 0.5F));
        MOODS.put("surprised", new Look(0, 0, -0.3F, -0.3F, 1.4F, 1.4F, -6, 1.1F, 0.95F, 0, 0).eyes(2.0F, 0, 0));
        MOODS.put("sad", new Look(0.55F, 0.55F, -0.8F, -0.8F, 0.4F, 0.4F, -16, 0.8F, 0.95F, 2, 0).eyes(0.9F, 0, 0.8F));
        MOODS.put("drunk", new Look(0.6F, 0.35F, 0.4F, -0.3F, 0.3F, 0.8F, 0, 1.05F, 1, 0, 0).eyes(0.9F, 0, 0.3F));
        MOODS.put("bliss", new Look(1.0F, 1.0F, 0.7F, 0.7F, 0.8F, 0.8F, -6, 1, 1, 2, 0));
        MOODS.put("smoulder", new Look(0.5F, 0.45F, 0.8F, 0.2F, 0.1F, 0.9F, 2, 1, 1, -5, 0).eyes(0.85F, 0.7F, 0));
    }

    /** What his face is doing now, per boss (eased toward the look he should have). */
    private static final class Face {
        float lidR, lidL, cornerR, cornerL, browR, browL, tilt, open = 1, width = 1, lean, sweat;
        float eyes = 1, aside, down;            // the mood's eyes, eased
        float eyeX, eyeY;                       // where they are now (px in the socket)
        int dartUntil, nextDart = 40;           // a glance away and back now and then
        float dartX, dartY;
        int asideSign = 1;
        double last = Double.NaN;
        float mouth;            // the eased mouth opening, px (after the line ends it closes softly)
        /** Per bone channel: the value it was left at and what was added to get there (see {@link #add}). */
        final Map<String, float[]> added = new HashMap<>();
    }

    private enum Ch { POS_Y, ROT_X, ROT_Z }

    /**
     * Adds {@code amount} to a channel of a bone the animations also move. GeckoLib only resets a bone
     * that no animation keys when nothing touched it that frame, and setting it counts as touching it, so
     * a plain addition would pile up frame after frame: if the bone still holds what it was left at, the
     * last addition is taken off first; if the animation wrote it since, it is added to that.
     */
    private static void add(Face f, CoreGeoBone b, Ch ch, float amount) {
        if (b == null) return;
        String key = b.getName() + ch;
        float cur = switch (ch) {
            case POS_Y -> b.getPosY();
            case ROT_X -> b.getRotX();
            case ROT_Z -> b.getRotZ();
        };
        float[] was = f.added.get(key);
        float base = was != null && Math.abs(cur - was[0]) < 1e-5F ? cur - was[1] : cur;
        float v = base + amount;
        switch (ch) {
            case POS_Y -> b.setPosY(v);
            case ROT_X -> b.setRotX(v);
            case ROT_Z -> b.setRotZ(v);
        }
        f.added.put(key, new float[]{v, amount});
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
        f.eyes += (want.eyes - f.eyes) * k;
        f.aside += (want.aside - f.aside) * k * 0.5F;
        f.down += (want.down - f.down) * k;

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

        // ---- where he looks: at you (the camera), with a glance aside for a sly look, a dart away now and then
        eyes(anim, f, boss, partial, saying, accent);

        // ---- the brows: the mood, up on the high notes, a pop on the stressed syllables
        float lift = (Mth.clamp(pitch / 6F, -0.3F, 1F) * Math.min(1F, loud * 2) + accent * 0.5F);
        brow(f, anim.getBone("brow_r"), f.browR + lift, f.tilt);
        brow(f, anim.getBone("brow_l"), f.browL + lift, -f.tilt);

        // ---- the head (his whole body): leaning into the line, a nod on each stress, tilted by the mood
        CoreGeoBone body = anim.getBone("body");
        float nod = accent * 4F + (saying ? loud * 1.5F : 0);
        add(f, body, Ch.ROT_X, -nod * Mth.DEG_TO_RAD);          // + nod in the files' convention: forward
        add(f, body, Ch.ROT_Z, f.lean * Mth.DEG_TO_RAD);
        if (f.sweat > 0.5F) scale(anim.getBone("sweat"), 1);
        // the apron: torn off in P2_TALK (its animation flings it), gone for the rest of phase two
        BossState st = boss.getBossState();
        if (boss.isPhaseTwo() && st != BossState.P2_KNEEL && st != BossState.P2_TALK) scale(anim.getBone("apron"), 0);
    }

    /**
     * The eyes move about in their sockets (1 px either way, 0.6 up or down): onto whoever is looking at him
     * (the camera, so in a cutscene he plays to it), plus the mood's glance aside (a side-eye, alternating
     * sides) or down, and every 2-4 s a quick dart somewhere else and back. Wide or narrowed by the mood,
     * wider for a moment on a stressed word.
     */
    private static void eyes(AnimationProcessor<PaponBossEntity> anim, Face f, PaponBossEntity boss, float partial, boolean saying,
                             float accent) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.world.phys.Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        float yaw = Mth.rotLerp(partial, boss.yBodyRotO, boss.yBodyRot) * Mth.DEG_TO_RAD;
        double dx = cam.x - Mth.lerp(partial, boss.xo, boss.getX()), dz = cam.z - Mth.lerp(partial, boss.zo, boss.getZ());
        double dy = cam.y - (Mth.lerp(partial, boss.yo, boss.getY()) + boss.getBbHeight() * 0.75);
        double fwd = -dx * Mth.sin(yaw) + dz * Mth.cos(yaw);
        double left = dx * Mth.cos(yaw) + dz * Mth.sin(yaw);          // toward his left (the model's +x)
        float tx = (float) Mth.clamp(Math.atan2(left, Math.max(0.3, fwd)) / 0.9, -1, 1) * EYE_SIDE;
        float ty = (float) Mth.clamp(Math.atan2(dy, Math.max(0.5, Math.hypot(fwd, left))) / 0.8, -0.6, 0.6);
        if (fwd < 0) tx = 0;                                           // behind him: eyes front
        int now = boss.tickCount;
        if (now >= f.nextDart) {
            f.dartUntil = now + 5 + Math.floorMod(now * 7 + boss.getId(), 9);
            f.dartX = ((now * 13 + boss.getId()) % 3 - 1) * 1.0F;
            f.dartY = ((now * 5) % 3 - 1) * 0.4F;
            f.nextDart = now + (saying ? 70 : 40) + Math.floorMod(now * 31 + boss.getId() * 17, 50);
            if (f.aside > 0.2F && Math.floorMod(now, 2) == 0) f.asideSign = -f.asideSign;
        }
        float wantX = tx + f.aside * f.asideSign, wantY = ty * 0.6F - f.down;
        if (now < f.dartUntil) {
            wantX = f.dartX;
            wantY = f.dartY;
        }
        float k = now < f.dartUntil || Math.abs(wantX - f.eyeX) > 0.6F ? 0.7F : 0.25F;   // a saccade snaps, pursuit glides
        f.eyeX += (Mth.clamp(wantX, -1, 1) - f.eyeX) * k;
        f.eyeY += (Mth.clamp(wantY, -0.6F, 0.6F) - f.eyeY) * k;
        float open = f.eyes * (1 + 0.25F * accent);
        for (String n : new String[]{"eye_r", "eye_l"}) {
            CoreGeoBone e = anim.getBone(n);
            if (e == null) continue;
            e.setPosX(f.eyeX);
            e.setPosY(f.eyeY);
            e.setScaleY(Mth.clamp(open, 0.4F, 2.2F));
            e.setScaleX(open > 1.4F ? 1.12F : 1F);
        }
    }

    /** +1 if a bone moved +x lands on his left (the model's +x as authored), set from the calibration shots. */
    private static final float EYE_SIDE = 1F;

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

    private static void brow(Face f, CoreGeoBone b, float lift, float tilt) {
        // a scowl brings the inner end down onto the eye: lifted as much (gen_anims.guard_brows' rule)
        float guard = 3F * (float) Math.sin(Math.abs(tilt) * Mth.DEG_TO_RAD);
        add(f, b, Ch.POS_Y, Math.max(-0.3F, lift) + guard);
        add(f, b, Ch.ROT_Z, tilt * Mth.DEG_TO_RAD);
    }

    private static void scale(CoreGeoBone b, float k) {
        if (b == null) return;
        b.setScaleX(k);
        b.setScaleY(k);
        b.setScaleZ(k);
    }
}
