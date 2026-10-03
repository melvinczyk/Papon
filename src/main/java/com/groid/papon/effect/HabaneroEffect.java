package com.groid.papon.effect;

import com.groid.papon.registry.ModDamage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Habanero: the heat of Chef Papon's fire and salsa, in stacks (amplifier + 1, up to five). Every
 * second it burns away 1% of your maximum health per stack, through any armor; a fifth stack boils
 * over (see {@link com.groid.papon.boss.Mechanics#heat}): a third of your maximum health at once. It
 * cools off by itself (each new stack restarts a six-second fuse); milk does nothing for it. Keep out
 * of the fire, however tough you are.
 */
public class HabaneroEffect extends MobEffect {
    public HabaneroEffect() {
        super(MobEffectCategory.HARMFUL, 0xE0401C);
    }

    @Override
    public void applyEffectTick(LivingEntity e, int amplifier) {
        if (!(e.level() instanceof ServerLevel level)) return;
        e.invulnerableTime = 0;
        e.hurt(ModDamage.of(level, ModDamage.HABANERO, null, null), e.getMaxHealth() * 0.01F * (amplifier + 1));
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Override
    public List<ItemStack> getCurativeItems() {
        return List.of();
    }
}
