package com.groid.papon.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Bound in place: Wrapped (rolled up in a giant tortilla: no walking, jumping, attacking or using
 * anything) or Rooted (potato sprouts round your ankles: no walking or jumping, you can still swing).
 * The rest is in {@link com.groid.papon.event.StunEvents}. Milk does nothing for either.
 */
public class BoundEffect extends MobEffect {
    public final boolean hands;

    public BoundEffect(int color, String uuid, boolean hands) {
        super(MobEffectCategory.HARMFUL, color);
        this.hands = hands;
        addAttributeModifier(Attributes.MOVEMENT_SPEED, uuid, -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @Override
    public List<ItemStack> getCurativeItems() {
        return List.of();
    }
}
