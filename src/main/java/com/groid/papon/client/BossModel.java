package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.boss.BossState;
import com.groid.papon.boss.PaponBossEntity;
import com.groid.papon.boss.Song;
import net.minecraft.util.Mth;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * Chef Papon's model: the generated geo and animations, and his face over them ({@link BossFace}).
 *
 * Phase three's serenade: his hands on the singer's requinto follow the record he is playing. The strumming
 * hand over the soundhole picks harder on the guitar's attacks; the fretting hand moves between chord shapes
 * up and down the neck. The poses are solved on his rig (tools/gen_boss_anims.guitar_poses, band_ik); the
 * p3_serenade animation holds the middle of them, this moves them.
 */
public class BossModel extends DefaultedEntityGeoModel<PaponBossEntity> {
    // arm rotation (animation convention, degrees) and stretch, from gen_boss_anims.guitar_poses()
    private static final float[] STRUM = {-68.6F, -11.7F, -4.5F, 2.6F};
    private static final float[] FRET_LOW = {-68.6F, 21.2F, 7.7F, 2.4F};
    private static final float[] FRET_HIGH = {-71.0F, 6.8F, 2.3F, 2.3F};
    /** Where on the neck his chord shapes sit, bar by bar (0 low .. 1 high). */
    private static final float[] SHAPES = {0.15F, 0.55F, 0.3F, 0.8F, 0.15F, 0.4F, 0.65F, 0.25F};

    private static final net.minecraft.resources.ResourceLocation ASCENDED_GEO = PaponMod.id("geo/entity/papon_ascended.geo.json");
    /** El Rey de los Muertos (tools/gen_muertos_papon.py): Ascended's bones, bone white, a Catrin's hat, the spirit hammer. */
    private static final net.minecraft.resources.ResourceLocation MUERTOS_GEO = PaponMod.id("geo/entity/papon_muertos.geo.json");

    public BossModel() { super(PaponMod.id("papon_boss")); }

    /** Ascended Papon (tools/gen_ascended.py): the same bones, his better potato, white eyes, eagle wings. */
    @Override
    public net.minecraft.resources.ResourceLocation getModelResource(PaponBossEntity boss) {
        return boss.isMuertos() ? MUERTOS_GEO : boss.isAscended() ? ASCENDED_GEO : super.getModelResource(boss);
    }

    @Override
    public void setCustomAnimations(PaponBossEntity boss, long instanceId, AnimationState<PaponBossEntity> state) {
        super.setCustomAnimations(boss, instanceId, state);
        if (boss.getBossState() == BossState.P3_SERENADE) serenade(boss, state.getPartialTick());
        if (boss.getBossState() == BossState.P3_DUEL || boss.getBossState() == BossState.P4_GIGANTE) shred(boss, state.getPartialTick());
        if (boss.isMuertos() || boss.getBossState().phaseFour()) spiritProps(boss, state.getPartialTick());
        if (boss.getBossState() == BossState.P4_TANGO) tango(boss, state.getPartialTick());
        else if (boss.muertosFight()) groove(boss, state.getPartialTick());
        BossFace.apply(getAnimationProcessor(), boss, state.getPartialTick());
    }

    // ------------------------------------------------------------------ the Final Guardian dances (2026-10-04)
    // (the user: "make him move as if he was doing a dance, try to sync him up to the song; he sees this fight as a dance")

    /** What this frame added to a shared bone, by entity and bone channel: taken back off next frame (BossFace.add's way). */
    private static final java.util.Map<String, float[]> ADDED = new java.util.HashMap<>();

    private enum Ch { POS_Y, POS_X, POS_Z, ROT_X, ROT_Y, ROT_Z }

    private void add(PaponBossEntity boss, String name, Ch ch, float amount) {
        CoreGeoBone b = getAnimationProcessor().getBone(name);
        if (b == null) return;
        String key = boss.getId() + name + ch;
        float cur = switch (ch) {
            case POS_X -> b.getPosX();
            case POS_Y -> b.getPosY();
            case POS_Z -> b.getPosZ();
            case ROT_X -> b.getRotX();
            case ROT_Y -> b.getRotY();
            case ROT_Z -> b.getRotZ();
        };
        float[] was = ADDED.get(key);
        float base = was != null && Math.abs(cur - was[0]) < 1e-5F ? cur - was[1] : cur;
        float v = base + amount;
        switch (ch) {
            case POS_X -> b.setPosX(v);
            case POS_Y -> b.setPosY(v);
            case POS_Z -> b.setPosZ(v);
            case ROT_X -> b.setRotX(v);
            case ROT_Y -> b.setRotY(v);
            case ROT_Z -> b.setRotZ(v);
        }
        ADDED.put(key, new float[]{v, amount});
    }

    /** Where in the band's song he is, in beats (the song playing; his own count without one). */
    private static double beats(PaponBossEntity boss, float partial) {
        Song song = SongPlayer.song();
        double t = SongPlayer.songTime(partial);
        double b = song != null && !Double.isNaN(t) ? song.beatAt(t) : -1;
        return b >= 0 ? b : (boss.tickCount + partial) / com.groid.papon.boss.Fiesta.BEAT;
    }

    /**
     * Between and under his moves in phase four, he grooves to the band: a bounce on every beat, his hips and shoulders
     * swaying across two beats (salsa's side to side), a shimmy on the off-beats, his head nodding; bigger while he dances
     * (La Pista, Salsa Caliente), lighter while he channels.
     */
    private void groove(PaponBossEntity boss, float partial) {
        BossState s = boss.getBossState();
        float k = switch (s) {
            case IDLE, WINDED -> 1F;
            case P4_PISTA, P4_SALSA, P4_DANZA -> 1.3F;
            case P4_MARTILLO, P4_GRIETA, P4_TANGO_WIN, P4_TANGO_LOSE -> 0F;
            default -> 0.45F;
        };
        if (k <= 0) return;
        double beat = beats(boss, partial);
        float ph = (float) (beat - Math.floor(beat));
        float bounce = (float) Math.pow(1 - ph, 2.5);
        float sway = Mth.sin((float) (beat * Math.PI));
        float shimmy = Mth.sin((float) (beat * Math.PI * 4)) * (float) Math.pow(Math.sin(beat * Math.PI), 2);
        float d = Mth.DEG_TO_RAD;
        add(boss, "root", Ch.POS_Y, -1.2F * bounce * k);
        add(boss, "torso", Ch.ROT_Z, 7 * d * sway * k);
        add(boss, "torso", Ch.ROT_Y, 9 * d * sway * k);
        add(boss, "torso", Ch.ROT_X, -4 * d * bounce * k);
        add(boss, "body", Ch.ROT_Z, -4 * d * sway * k + 2 * d * shimmy * k);
        add(boss, "arm_r", Ch.ROT_Z, 10 * d * sway * k);
        add(boss, "arm_l", Ch.ROT_Z, 10 * d * sway * k);
        add(boss, "arm_r", Ch.ROT_X, -6 * d * bounce * k);
        add(boss, "arm_l", Ch.ROT_X, -6 * d * bounce * k);
        add(boss, "leg_r", Ch.ROT_X, 8 * d * Math.max(0, sway) * k);
        add(boss, "leg_l", Ch.ROT_X, 8 * d * Math.max(0, -sway) * k);
    }

    /**
     * El Último Tango: on the challenger's screen his steps are the dance-off's (DanceOff: a stride out and back the way the
     * step goes, a whole turn for the spin), in the tango's frame (one hand raised as if holding yours, the other round your
     * back); to everyone else the d_tango loop shows it.
     */
    private void tango(PaponBossEntity boss, float partial) {
        DanceOff dance = QteClient.dancing(boss) ? QteClient.dance() : null;
        if (dance == null) return;
        float[] st = dance.hisStep();
        int mv = (int) st[0];
        float k = st[1];
        float e = Mth.sin(k * Mth.PI);
        float d = Mth.DEG_TO_RAD;
        float bob = (float) Math.pow(1 - dance.beatPhase(), 3);
        add(boss, "root", Ch.POS_Y, -1.0F * bob);
        if (mv == DanceOff.SPIN) add(boss, "root", Ch.ROT_Y, Mth.TWO_PI * (k * k * (3 - 2 * k)));
        else if (mv >= 0) {
            float fz = mv == DanceOff.FWD ? -1 : mv == DanceOff.BACK ? 1 : 0, fx = mv == DanceOff.LEFT ? -1 : mv == DanceOff.RIGHT ? 1 : 0;
            add(boss, "root", Ch.POS_Z, 7 * fz * e);
            add(boss, "root", Ch.POS_X, 7 * fx * e);
            add(boss, "torso", Ch.ROT_X, 12 * d * -fz * e);
            add(boss, "torso", Ch.ROT_Z, 12 * d * fx * e);
            add(boss, "leg_r", Ch.ROT_X, -35 * d * e * (fz != 0 ? -fz : 1));
            add(boss, "leg_l", Ch.ROT_X, 20 * d * e);
        }
        set("l", -128, 0, -34, 1.35F, 1);
        set("r", -74, 34, 18, 1.3F, 1);
    }

    private void serenade(PaponBossEntity boss, float partial) {
        double t = boss.serenadeTime(partial);
        Song song = Song.get(Song.PARA_SIEMPRE);
        if (Double.isNaN(t) || song == null) return;
        // in over the first half second (the animation brings the guitar round), out after the last chord
        float in = (float) Mth.clamp((t + 0.2) / 0.5, 0, 1);
        double end = song.byId("serenade") != null ? song.byId("serenade").length() : 18.4;
        float out = (float) Mth.clamp((end + 0.3 - t) / 0.5, 0, 1);
        float k = in * out;
        if (k <= 0) return;
        float attack = Mth.clamp(song.strumAt(t) * 1.3F, 0, 1);
        float pick = (4 + 14 * attack) * Mth.sin((float) (t * Math.PI * 2 * 2.2));
        set("r", STRUM[0] + pick * k, STRUM[1], STRUM[2], STRUM[3], k);
        // a chord shape a bar (1.74 s), sliding between them over the last tenth of the bar
        double bar = t / 1.74;
        int b = (int) Math.floor(Math.max(0, bar));
        float into = (float) Mth.clamp((bar - b - 0.9) / 0.1, 0, 1);
        float pos = Mth.lerp(into, SHAPES[b % SHAPES.length], SHAPES[(b + 1) % SHAPES.length]);
        float wob = 1.5F * Mth.sin((float) (t * 31)) * Math.min(1, song.voiceAt(t) * 2);     // a little vibrato under his voice
        set("l", Mth.lerp(pos, FRET_LOW[0], FRET_HIGH[0]) + wob, Mth.lerp(pos, FRET_LOW[1], FRET_HIGH[1]),
                Mth.lerp(pos, FRET_LOW[2], FRET_HIGH[2]), Mth.lerp(pos, FRET_LOW[3], FRET_HIGH[3]), k);
    }

    /**
     * The guitar duel: he shreds. The picking fist a blur over the strings (a dozen strokes a second, digging in on the
     * accents); the fretting hand racing up and down the neck in runs, a new position every eighth of a second.
     */
    private void shred(PaponBossEntity boss, float partial) {
        double t = (boss.ticksInState() + partial) / 20.0;
        float k = (float) Mth.clamp(t / 0.4, 0, 1);
        float accent = (float) Math.pow(Math.max(0, Math.sin(t * Math.PI * 2 * 1.7)), 6);
        float pick = (6 + 10 * accent) * Mth.sin((float) (t * Math.PI * 2 * 12));
        set("r", STRUM[0] + pick * k, STRUM[1], STRUM[2], STRUM[3], k);
        int step = (int) Math.floor(t * 8);
        float run = RUN[Math.floorMod(step, RUN.length)];
        float into = (float) (t * 8 - step);
        float pos = Mth.lerp(Math.min(1F, into * 4), RUN[Math.floorMod(step - 1, RUN.length)], run);
        set("l", Mth.lerp(pos, FRET_LOW[0], FRET_HIGH[0]), Mth.lerp(pos, FRET_LOW[1], FRET_HIGH[1]),
                Mth.lerp(pos, FRET_LOW[2], FRET_HIGH[2]), Mth.lerp(pos, FRET_LOW[3], FRET_HIGH[3]), k);
    }

    /**
     * Phase four's things out of thin air, set every frame (no animation shows them): the spirit hammer while he holds it
     * (PaponBossEntity.spiritProp 1; the guitar when he plays for El Gigante, 2, is the requinto bone, shown by d_gigante),
     * his Aztec bow (3: swelling in out of the light), the penacho on the Final Guardian, and the scar: torn open and
     * bleeding from his fall (P4_FALL, the drips running longer) until his truest form, where it burns green.
     */
    private void spiritProps(PaponBossEntity boss, float partial) {
        int prop = boss.spiritProp();
        BossState s = boss.getBossState();
        float t = boss.ticksInState() + partial;
        scale("spirit_hammer", prop == 1 ? 1 : 0);
        float bow = prop == 3 ? 1 : 0;
        if (prop == 3 && s == BossState.P4_BOW) {
            float k = (t - PaponBossEntity.BOW_FORM) / 7F;
            bow = k >= 1 ? 1 : (float) Math.max(0.05, Mth.sin(k * Mth.PI * 0.5F) * (1 + 0.25F * Mth.sin(k * Mth.PI)));
        }
        scale("aztec_bow", bow);
        scale("penacho", boss.isMuertos() ? 1 : 0);
        boolean scarred = boss.isMuertos() || s.phaseFour();
        scale("scar", scarred ? 1 : 0);
        CoreGeoBone drips = getAnimationProcessor().getBone("scar_blood");
        if (drips != null) {
            float run = s == BossState.P4_FALL ? Mth.clamp((t - com.groid.papon.boss.VoiceLines.beat("p4.look", 58) + 20) / 60F, 0.15F, 1) : 1;
            drips.setScaleX(scarred ? 1 : 0);
            drips.setScaleZ(scarred ? 1 : 0);
            drips.setScaleY(scarred ? run : 0);
        }
    }

    private void scale(String name, float k) {
        CoreGeoBone bone = getAnimationProcessor().getBone(name);
        if (bone == null) return;
        bone.setScaleX(k);
        bone.setScaleY(k);
        bone.setScaleZ(k);
    }

    /** His shred's runs up and down the neck (0 low .. 1 high). */
    private static final float[] RUN = {0.1F, 0.3F, 0.5F, 0.75F, 1.0F, 0.8F, 0.55F, 0.35F, 0.9F, 0.6F, 0.2F, 0.45F};

    /** An arm outright (blended {@code k} of the way from what the animation has), as a keyframe would set it. */
    private void set(String side, float x, float y, float z, float stretch, float k) {
        CoreGeoBone arm = getAnimationProcessor().getBone("arm_" + side);
        CoreGeoBone mesh = getAnimationProcessor().getBone("arm_" + side + "_mesh");
        CoreGeoBone hand = getAnimationProcessor().getBone("hand_" + side);
        if (arm == null) return;
        var rest = arm.getInitialSnapshot();
        arm.setRotX(Mth.lerp(k, arm.getRotX(), rest.getRotX() - x * Mth.DEG_TO_RAD));
        arm.setRotY(Mth.lerp(k, arm.getRotY(), rest.getRotY() - y * Mth.DEG_TO_RAD));
        arm.setRotZ(Mth.lerp(k, arm.getRotZ(), rest.getRotZ() + z * Mth.DEG_TO_RAD));
        if (mesh != null) {
            // a stretched arm thins out, like the band's (BandPoses.girth): his fists stay readable on the strings
            float girth = Mth.lerp(k, 1F, BandPoses.girth(stretch));
            mesh.setScaleX(girth);
            mesh.setScaleZ(girth);
            mesh.setScaleY(Mth.lerp(k, mesh.getScaleY(), stretch));
        }
        if (hand != null) hand.setPosY(Mth.lerp(k, hand.getPosY(), -(stretch - 1) * 6.5F));
    }
}
