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

    public BossModel() {
        super(PaponMod.id("papon_boss"));
    }

    /** Ascended Papon (tools/gen_ascended.py): the same bones, his better potato, white eyes, eagle wings. */
    @Override
    public net.minecraft.resources.ResourceLocation getModelResource(PaponBossEntity boss) {
        return boss.isAscended() ? ASCENDED_GEO : super.getModelResource(boss);
    }

    @Override
    public void setCustomAnimations(PaponBossEntity boss, long instanceId, AnimationState<PaponBossEntity> state) {
        super.setCustomAnimations(boss, instanceId, state);
        if (boss.getBossState() == BossState.P3_SERENADE) serenade(boss, state.getPartialTick());
        if (boss.getBossState() == BossState.P3_DUEL) shred(boss, state.getPartialTick());
        BossFace.apply(getAnimationProcessor(), boss, state.getPartialTick());
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
