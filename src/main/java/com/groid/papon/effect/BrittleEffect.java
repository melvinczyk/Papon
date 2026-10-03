package com.groid.papon.effect;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Brittle: blowtorched and then salted by Chef Papon's grab. Armor and armor toughness down 30%
 * while it lasts, and salt flakes off you.
 */
public class BrittleEffect extends MobEffect {
    public BrittleEffect() {
        super(MobEffectCategory.HARMFUL, 0xE8E4D8);
        addAttributeModifier(Attributes.ARMOR, "3f9e2b61-7c4d-4a18-9b5e-0d6a2c8f1e47", -0.3,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
        addAttributeModifier(Attributes.ARMOR_TOUGHNESS, "8b1d4e72-2a6f-4c39-a0e5-5f7c3b9d2a18", -0.3,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @Override
    public void applyEffectTick(LivingEntity e, int amplifier) {
        if (e.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.WHITE_ASH, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(),
                    4, e.getBbWidth() * 0.4, e.getBbHeight() * 0.3, e.getBbWidth() * 0.4, 0.01);
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 5 == 0;
    }
}
