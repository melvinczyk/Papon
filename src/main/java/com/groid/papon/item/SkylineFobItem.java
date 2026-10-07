package com.groid.papon.item;

import com.groid.papon.registry.ModEntities;
import com.groid.papon.registry.ModSounds;
import com.groid.papon.vehicle.CaravanEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The key fob to Ascended Papon's Skyline (the van he crushed and reformed in phase three): he drops it when he falls,
 * tied to the car he leaves behind. Like the van's keys, used on the ground it parks the car there and sneak-used on the
 * car it pockets it. Pressed in the air it is a remote: the fob chirps, the car's lamps blink and the engine catches, the
 * underglow coming on, from up to 64 blocks away (press again to shut it down).
 *
 * (2026-10-06, the user: "make it spawnable with its key fob") Pressed with no car out, the Skyline is summoned: it reforms
 * out of a ball of purple light beside you, side on, its driver's door toward you, the engine catching as it settles
 * ({@link CaravanEntity#materialize}). Sneak-pressed, or pressed out of range, the car you have out comes to you the same
 * way; used on the ground while it is out, it comes to that spot.
 */
public class SkylineFobItem extends CaravanKeysItem {
    private static final double RANGE = 64;

    public SkylineFobItem(Properties properties) { super(properties); }

    @Override
    protected void dress(CaravanEntity van) { van.setSkyline(true); }

    @Override
    protected void parked(ServerLevel level, CaravanEntity van) { arrive(level, van); }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (ctx.getLevel() instanceof ServerLevel server && vanOf(server, ctx.getItemInHand()) instanceof CaravanEntity car && player != null) {
            // it is out: it comes to where you pointed
            if (car.isVehicle()) {
                player.displayClientMessage(Component.translatable("message.papon.van_occupied").withStyle(ChatFormatting.RED), true);
                return InteractionResult.FAIL;
            }
            BlockPos pos = ctx.getClickedPos().relative(ctx.getClickedFace());
            Vec3 at = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            return call(server, player, car, at, player.getYRot()) ? InteractionResult.CONSUME : InteractionResult.FAIL;
        }
        return super.useOn(ctx);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack fob = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server)) return InteractionResultHolder.success(fob);
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.SKYLINE_CHIRP.get(), SoundSource.PLAYERS, 0.6F, 1.3F);
        player.getCooldowns().addCooldown(this, 20);
        Entity e = vanOf(server, fob);
        if (!(e instanceof CaravanEntity car)) {
            summon(server, player, fob);
            return InteractionResultHolder.consume(fob);
        }
        if (car.isVehicle()) {
            if (!car.hasPassenger(player)) {
                player.displayClientMessage(Component.translatable("message.papon.van_occupied").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.consume(fob);
        }
        if (player.isSecondaryUseActive() || car.distanceTo(player) > RANGE) {
            Vec3[] spot = spotBeside(server, player);
            if (spot == null) {
                player.displayClientMessage(Component.translatable("message.papon.fob_no_room").withStyle(ChatFormatting.RED), true);
            } else if (call(server, player, car, spot[0], (float) spot[1].x)) {
                player.displayClientMessage(Component.translatable("message.papon.fob_recall").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
            return InteractionResultHolder.consume(fob);
        }
        // the car answers: a chirp, the lamps, and the engine on (or off again)
        level.playSound(null, car.getX(), car.getY(), car.getZ(), ModSounds.SKYLINE_CHIRP.get(), SoundSource.PLAYERS, 1.4F, 1.0F);
        if (car.idling()) {
            car.remoteStart(0);
            player.displayClientMessage(Component.translatable("message.papon.fob_off").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        } else {
            car.remoteStart(20 * 60);
            if (car.underglowAge(0) < 0) {
                car.setUnderglow(true);
                level.playSound(null, car.getX(), car.getY(), car.getZ(), ModSounds.SKYLINE_GLOW.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            player.displayClientMessage(Component.translatable("message.papon.fob_start").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
        return InteractionResultHolder.consume(fob);
    }

    /** No car out: a new one reforms beside you, tied to this fob. */
    private void summon(ServerLevel level, Player player, ItemStack fob) {
        CaravanEntity car = ModEntities.CARAVAN.get().create(level);
        if (car == null) return;
        dress(car);
        Vec3[] spot = spotBeside(level, player);
        if (spot == null) {
            player.displayClientMessage(Component.translatable("message.papon.fob_no_room").withStyle(ChatFormatting.RED), true);
            return;
        }
        car.moveTo(spot[0].x, spot[0].y, spot[0].z, (float) spot[1].x, 0);
        level.addFreshEntity(car);
        fob.getOrCreateTag().putUUID(VAN, car.getUUID());
        arrive(level, car);
        player.displayClientMessage(Component.translatable("message.papon.fob_summon").withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    /** The car you have out, brought to {@code at} (it folds away where it was and reforms here). */
    private static boolean call(ServerLevel level, Player player, CaravanEntity car, Vec3 at, float yaw) {
        Vec3 from = car.position();
        if (car.level() != level) return false;
        car.moveTo(at.x, at.y, at.z, yaw, 0);
        if (!level.noCollision(car)) {
            car.moveTo(from.x, from.y, from.z, car.getYRot(), 0);
            player.displayClientMessage(Component.translatable("message.papon.fob_no_room").withStyle(ChatFormatting.RED), true);
            return false;
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, from.x, from.y + 0.8, from.z, 60, 1.0, 0.5, 2.0, 0.08);
        car.setDeltaMovement(Vec3.ZERO);
        car.setYBodyRot(yaw);
        car.setYHeadRot(yaw);
        arrive(level, car);
        return true;
    }

    /** It reforms, chirps, the engine catches and the underglow flickers on. */
    private static void arrive(ServerLevel level, CaravanEntity car) {
        car.materialize();
        level.playSound(null, car.getX(), car.getY(), car.getZ(), ModSounds.SKYLINE_SUMMON.get(), SoundSource.PLAYERS, 1.6F, 1.0F);
        level.playSound(null, car.getX(), car.getY(), car.getZ(), ModSounds.SKYLINE_CHIRP.get(), SoundSource.PLAYERS, 1.2F, 1.0F);
        car.setUnderglow(true);
        car.remoteStart(20 * 20);
    }

    /**
     * Where a car fits beside you: a few blocks ahead of you on the ground, side on to you with its driver's door (its
     * right, right-hand drive) your way; nearer or further, a little to either side, a step up or down. {position, (yaw)}.
     */
    @Nullable
    private static Vec3[] spotBeside(ServerLevel level, Player player) {
        float yaw = player.getYRot();
        Vec3 ahead = Vec3.directionFromRotation(0, yaw);
        Vec3 side = new Vec3(-ahead.z, 0, ahead.x);
        CaravanEntity probe = ModEntities.CARAVAN.get().create(level);
        if (probe == null) return null;
        // side on, facing your right: its right side (the driver's door) toward you
        float carYaw = Mth.wrapDegrees(yaw + 90);
        for (double d : new double[]{3.6, 4.6, 3.0, 5.8, 7.0}) {
            for (double s : new double[]{0, 1.5, -1.5, 3.0, -3.0}) {
                Vec3 c = player.position().add(ahead.scale(d)).add(side.scale(s));
                BlockPos col = BlockPos.containing(c.x, player.getY() + 3, c.z);
                for (int dy = 0; dy < 8; dy++) {
                    BlockPos p = col.below(dy);
                    BlockPos floor = p.below();
                    if (level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()) continue;
                    double y = floor.getY() + level.getBlockState(floor).getCollisionShape(level, floor).max(net.minecraft.core.Direction.Axis.Y);
                    probe.moveTo(c.x, y, c.z, carYaw, 0);
                    if (level.noCollision(probe) && !level.containsAnyLiquid(probe.getBoundingBox())) {
                        return new Vec3[]{new Vec3(c.x, y, c.z), new Vec3(carYaw, 0, 0)};
                    }
                    break;
                }
            }
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.papon.skyline_fob.tooltip").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.papon.skyline_fob.tooltip2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
