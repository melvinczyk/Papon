package com.groid.papon.item;

import com.groid.papon.registry.ModSounds;
import com.groid.papon.vehicle.CaravanEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The key fob to Ascended Papon's Skyline (the van he crushed and reformed in phase three): he drops it when he falls,
 * tied to the car he leaves behind. Like the van's keys, used on the ground it parks the car there and sneak-used on the
 * car it pockets it; pressed in the air it is a remote: the fob chirps, the car's lamps blink and the engine catches, the
 * underglow coming on, from up to 64 blocks away (press again to shut it down).
 */
public class SkylineFobItem extends CaravanKeysItem {
    private static final double RANGE = 64;

    public SkylineFobItem(Properties properties) { super(properties); }

    @Override
    protected void dress(CaravanEntity van) { van.setSkyline(true); }

    @Override
    protected void parked(ServerLevel level, CaravanEntity van) {
        van.setUnderglow(true);
        level.playSound(null, van.getX(), van.getY(), van.getZ(), ModSounds.SKYLINE_CHIRP.get(), SoundSource.PLAYERS, 1.2F, 1.0F);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack fob = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server)) return InteractionResultHolder.success(fob);
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.SKYLINE_CHIRP.get(), SoundSource.PLAYERS, 0.6F, 1.3F);
        player.getCooldowns().addCooldown(this, 20);
        Entity e = vanOf(server, fob);
        if (!(e instanceof CaravanEntity car) || car.distanceTo(player) > RANGE) {
            player.displayClientMessage(Component.translatable(e == null ? "message.papon.fob_no_car" : "message.papon.fob_out_of_range")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return InteractionResultHolder.consume(fob);
        }
        if (car.isVehicle()) {
            player.displayClientMessage(Component.translatable("message.papon.van_occupied").withStyle(ChatFormatting.RED), true);
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

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.papon.skyline_fob.tooltip").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.papon.skyline_fob.tooltip2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
