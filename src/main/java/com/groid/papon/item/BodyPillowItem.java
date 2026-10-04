package com.groid.papon.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The trophy for beating Chef Papon, handed over by Gerbin the dishwasher himself (see
 * GerbinEntity#reward): a body pillow, Makima on the front, "Woof" on the back. Use it to give it
 * a hug (hearts, a soft thump, a line of what Gerbin would say).
 */
public class BodyPillowItem extends Item {
    public BodyPillowItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.HEART, player.getX(), player.getEyeY() - 0.2, player.getZ(), 4, 0.35, 0.25, 0.35, 0.02);
            server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.0F, 0.7F);
            server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CAT_PURR, SoundSource.PLAYERS, 0.6F, 1.1F);
            player.displayClientMessage(Component.translatable("message.papon.body_pillow." + level.random.nextInt(4))
                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), true);
        }
        player.getCooldowns().addCooldown(this, 30);
        player.swing(hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.papon.body_pillow.tooltip.0").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.papon.body_pillow.tooltip.1").withStyle(ChatFormatting.GRAY));
    }
}
