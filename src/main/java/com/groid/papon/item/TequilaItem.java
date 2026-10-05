package com.groid.papon.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Chef Papon's tequila. Dropped by him; liquid courage for whoever drinks it: strength and
 * resistance, and the room spins.
 */
public class TequilaItem extends Item {
    private static final FoodProperties FOOD = new FoodProperties.Builder()
            .nutrition(1)
            .saturationMod(0.2F)
            .alwaysEat()
            .effect(() -> new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 60), 1F)
            .effect(() -> new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 30), 1F)
            .effect(() -> new MobEffectInstance(MobEffects.CONFUSION, 20 * 20), 1F)
            .build();

    public TequilaItem(Properties properties) { super(properties.food(FOOD).stacksTo(16)); }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.DRINK; }

    @Override
    public int getUseDuration(ItemStack stack) { return 40; }

    @Override
    public SoundEvent getDrinkingSound() { return SoundEvents.GENERIC_DRINK; }

    @Override
    public SoundEvent getEatingSound() { return SoundEvents.GENERIC_DRINK; }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        boolean creative = entity instanceof Player p && p.getAbilities().instabuild;
        ItemStack rest = super.finishUsingItem(stack, level, entity);
        if (entity instanceof ServerPlayer sp) {
            sp.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
            level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PLAYER_BURP, sp.getSoundSource(), 1.0F, 0.6F);
        }
        if (creative) return rest;
        if (rest.isEmpty()) return new ItemStack(Items.GLASS_BOTTLE);
        if (entity instanceof Player p && !p.getInventory().add(new ItemStack(Items.GLASS_BOTTLE))) {
            p.drop(new ItemStack(Items.GLASS_BOTTLE), false);
        }
        return rest;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.papon.tequila.tooltip").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
