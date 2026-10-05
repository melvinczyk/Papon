package com.groid.papon.item;

import com.groid.papon.registry.ModEntities;
import com.groid.papon.vehicle.CaravanEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The keys to a 2001 Dodge Caravan (Chef Papon drops his). One van per key: used on the ground
 * it parks the van there, if that van is not already out somewhere; sneak-used on the van it
 * pockets it again.
 */
public class CaravanKeysItem extends Item {
    protected static final String VAN = "Van";

    public CaravanKeysItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        ItemStack keys = ctx.getItemInHand();
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        Entity existing = vanOf(server, keys);
        if (existing != null) {
            if (player != null) {
                BlockPos at = existing.blockPosition();
                player.displayClientMessage(Component.translatable("message.papon.van_parked", at.getX(), at.getY(), at.getZ())
                        .withStyle(ChatFormatting.GOLD), true);
            }
            return InteractionResult.FAIL;
        }
        CaravanEntity van = ModEntities.CARAVAN.get().create(server);
        if (van == null) return InteractionResult.FAIL;
        BlockPos pos = ctx.getClickedPos().relative(ctx.getClickedFace());
        float yaw = player != null ? player.getYRot() : 0;
        van.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);
        dress(van);
        if (!server.noCollision(van)) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.papon.van_no_room").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }
        server.addFreshEntity(van);
        keys.getOrCreateTag().putUUID(VAN, van.getUUID());
        server.playSound(null, pos, SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.PLAYERS, 1.0F, 1.4F);
        parked(server, van);
        return InteractionResult.CONSUME;
    }

    /** Which car these keys bring out (SkylineFobItem: the Skyline). */
    protected void dress(CaravanEntity van) {}

    /** Just parked by these keys. */
    protected void parked(ServerLevel level, CaravanEntity van) {}

    @Override
    public InteractionResult interactLivingEntity(ItemStack keys, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof CaravanEntity van) || !player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (van.isVehicle()) {
            player.displayClientMessage(Component.translatable("message.papon.van_occupied").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        ItemStack held = player.getItemInHand(hand);
        CompoundTag tag = held.getTag();
        if (tag != null && tag.hasUUID(VAN) && !tag.getUUID(VAN).equals(van.getUUID())
                && player.level() instanceof ServerLevel s && s.getEntity(tag.getUUID(VAN)) != null) {
            player.displayClientMessage(Component.translatable("message.papon.van_wrong_keys").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        van.discard();
        held.removeTagKey(VAN);
        player.level().playSound(null, van.getX(), van.getY(), van.getZ(), SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.PLAYERS, 1.0F, 0.8F);
        return InteractionResult.CONSUME;
    }

    @Nullable
    protected static Entity vanOf(ServerLevel level, ItemStack keys) {
        CompoundTag tag = keys.getTag();
        if (tag == null || !tag.hasUUID(VAN)) return null;
        UUID id = tag.getUUID(VAN);
        Entity e = level.getEntity(id);
        return e instanceof CaravanEntity && e.isAlive() ? e : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.papon.caravan_keys.tooltip").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.papon.caravan_keys.tooltip2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
