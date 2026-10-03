package com.groid.papon.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Food Poisoning: a cleaver from a dirty kitchen. While it lasts you cannot heal at all (no
 * regeneration, no potions, no lifesteal: {@link com.groid.papon.event.StunEvents#onHeal}). Milk does
 * nothing for it.
 */
public class FoodPoisoningEffect extends MobEffect {
    public FoodPoisoningEffect() {
        super(MobEffectCategory.HARMFUL, 0x7FA43A);
    }

    @Override
    public List<ItemStack> getCurativeItems() {
        return List.of();
    }
}
