package com.groid.papon.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Handed to a Walking Crops Papon (see CommonEvents) to start the Taco Quest. */
public class TacoCouponItem extends Item {
    public TacoCouponItem(Properties properties) { super(properties); }

    @Override
    public boolean isFoil(ItemStack stack) { return true; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.papon.taco_coupon.tooltip.0").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.papon.taco_coupon.tooltip.1").withStyle(ChatFormatting.GRAY));
    }
}
