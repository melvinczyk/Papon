package com.groid.papon.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * A potato from the stands, right in the face: a tenth of the screen covered in mash
 * (client/PotatoSplat draws it) until it slides off, five seconds later. Nothing else: it is there
 * to be annoying. Milk does not help (it is potato).
 */
public class SplatteredEffect extends MobEffect {
    public SplatteredEffect() { super(MobEffectCategory.HARMFUL, 0xD9B65A); }

    @Override
    public List<ItemStack> getCurativeItems() { return List.of(); }
}
