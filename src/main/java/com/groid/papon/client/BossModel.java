package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.boss.PaponBossEntity;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/** Chef Papon's model: the generated geo and animations, and his face over them ({@link BossFace}). */
public class BossModel extends DefaultedEntityGeoModel<PaponBossEntity> {
    public BossModel() {
        super(PaponMod.id("papon_boss"));
    }

    @Override
    public void setCustomAnimations(PaponBossEntity boss, long instanceId, AnimationState<PaponBossEntity> state) {
        super.setCustomAnimations(boss, instanceId, state);
        BossFace.apply(getAnimationProcessor(), boss, state.getPartialTick());
    }
}
