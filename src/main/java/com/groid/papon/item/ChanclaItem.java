package com.groid.papon.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The only thing that can wake a sleeping Papon early (2% per whack, see CommonEvents#onAttack). */
public class ChanclaItem extends Item {
    public ChanclaItem(Properties properties) { super(properties.stacksTo(1)); }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.papon.chancla.tooltip").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
