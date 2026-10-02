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
 * Jarritos: radon soda in four flavours. Drinkable by players (3 hunger, 2 saturation, nausea and
 * speed), and handed to the Wandering Papon for a 1.5x speed boost - except the yellow one, which
 * he smashes (see WanderingPaponEntity#offerJarrito).
 */
public class JarritoItem extends Item {
    public enum Flavor {
        RED, ORANGE, YELLOW, GREEN;

        public static Flavor byId(int id) {
            Flavor[] all = values();
            return id >= 0 && id < all.length ? all[id] : null;
        }
    }

    /** nutrition 3 restores 3 hunger points; saturation gained = nutrition * mod * 2 = 2. */
    private static final FoodProperties FOOD = new FoodProperties.Builder()
            .nutrition(3)
            .saturationMod(1F / 3F)
            .alwaysEat()
            .effect(() -> new MobEffectInstance(MobEffects.CONFUSION, 200), 1F)
            .effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 900), 1F)
            .build();

    private final Flavor flavor;

    public JarritoItem(Flavor flavor, Properties properties) {
        super(properties.food(FOOD).stacksTo(16));
        this.flavor = flavor;
    }

    public Flavor flavor() {
        return flavor;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 32;
    }

    @Override
    public SoundEvent getDrinkingSound() {
        return SoundEvents.GENERIC_DRINK;
    }

    @Override
    public SoundEvent getEatingSound() {
        return SoundEvents.GENERIC_DRINK;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        boolean creative = entity instanceof Player p && p.getAbilities().instabuild;
        ItemStack rest = super.finishUsingItem(stack, level, entity);
        if (entity instanceof ServerPlayer sp) {
            sp.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
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
        tooltip.add(Component.translatable("item.papon.jarrito.tooltip").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        if (flavor == Flavor.YELLOW) {
            tooltip.add(Component.translatable("item.papon.jarrito_yellow.tooltip").withStyle(ChatFormatting.DARK_RED));
        }
    }
}
