package com.groid.papon.item;

import com.groid.papon.boss.HiredBand;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * El Requinto: the singer's little guitar, the one Chef Papon snatched off him in phase three, pressed into the hands of
 * whoever beat him ({@link #give}). A heavy, unbreakable swing (Better Combat: weapon_attributes/requinto.json, a hammer's
 * overhead smash), a note on every hit; right-click strums a chord and hires the next of the band out of work
 * (boss/HiredBand), sneak + right-click sends them all home.
 */
public class RequintoItem extends SwordItem {
    /** Never wears out (no durability: Unbreaking and Mending do not apply); enchants like a sword. */
    private static final Tier TIER = new Tier() {
        @Override public int getUses() { return 0; }
        @Override public float getSpeed() { return 1.0F; }
        @Override public float getAttackDamageBonus() { return 5.0F; }
        @Override public int getLevel() { return 0; }
        @Override public int getEnchantmentValue() { return 18; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
    };

    /** An E minor chord, low to high (note block pitches: 2^((semitones - 12) / 12)). */
    private static final float[] CHORD = {0.5F, 0.749F, 1.0F, 1.189F, 1.498F};

    public RequintoItem(Properties properties) { super(TIER, 3, -2.8F, properties.stacksTo(1)); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            strum(sl, player);
            if (player.isShiftKeyDown()) HiredBand.dismiss(sp);
            else HiredBand.summon(sp);
        }
        player.getCooldowns().addCooldown(this, 24);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** The chord, rolled from the low string up, and the notes floating off it. */
    private static void strum(ServerLevel level, Player p) {
        for (int i = 0; i < CHORD.length; i++) {
            level.playSound(null, p.getX(), p.getY() + 1, p.getZ(), SoundEvents.NOTE_BLOCK_GUITAR.value(), SoundSource.PLAYERS, 0.9F, CHORD[i]);
        }
        level.sendParticles(ParticleTypes.NOTE, p.getX(), p.getY() + 1.2, p.getZ(), 5, 0.4, 0.3, 0.4, 1);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (target.level() instanceof ServerLevel level) {
            float pitch = CHORD[level.random.nextInt(CHORD.length)];
            level.playSound(null, target.getX(), target.getY() + 1, target.getZ(), SoundEvents.NOTE_BLOCK_GUITAR.value(), SoundSource.PLAYERS, 1.0F, pitch);
            level.sendParticles(ParticleTypes.NOTE, target.getX(), target.getY() + target.getBbHeight() + 0.3, target.getZ(), 1, 0, 0, 0, pitch / 2);
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.papon.requinto.tooltip").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        for (int i = 1; i <= 7; i++) tooltip.add(Component.translatable("item.papon.requinto.tooltip." + i).withStyle(i == 1 ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
    }

    /** Chef Papon beaten: the singer's requinto for {@code p}, if they do not already carry one. */
    public static void give(ServerPlayer p) {
        net.minecraft.world.item.Item item = com.groid.papon.registry.ModItems.REQUINTO.get();
        if (p.getInventory().contains(new ItemStack(item))) return;
        ItemStack stack = new ItemStack(item);
        if (!p.getInventory().add(stack)) p.drop(stack, false);
        p.displayClientMessage(Component.translatable("message.papon.requinto_given").withStyle(ChatFormatting.GOLD), false);
    }
}
