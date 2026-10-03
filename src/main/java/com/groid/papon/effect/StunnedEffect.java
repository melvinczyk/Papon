package com.groid.papon.effect;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Stunned: flattened by the van. Whoever has it cannot walk (movement speed to nothing; knockback
 * still carries them), cannot jump ({@link com.groid.papon.event.StunEvents}) and lies in the crawl
 * pose with stars circling their head. Applied by {@link com.groid.papon.vehicle.CaravanEntity} on
 * every hit, with a hard screen shake for the player it lands on.
 */
public class StunnedEffect extends MobEffect {
    public StunnedEffect() {
        super(MobEffectCategory.HARMFUL, 0xF2D33C);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, "9a3c7d2e-5b41-4f6a-8e0d-2c7b6a1f4e93", -1.0,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @Override
    public void applyEffectTick(LivingEntity e, int amplifier) {
        if (!(e.level() instanceof ServerLevel level)) return;
        // a star round the head
        double t = e.tickCount * 0.45;
        double y = e.getY() + (e.getBbHeight() < 1.0 ? 0.7 : e.getBbHeight() + 0.25);
        com.groid.papon.fx.Fx.glint(level, new net.minecraft.world.phys.Vec3(e.getX() + Math.cos(t) * 0.45, y, e.getZ() + Math.sin(t) * 0.45),
                0.3F, 0xFFE070);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 8 == 0;
    }
}
