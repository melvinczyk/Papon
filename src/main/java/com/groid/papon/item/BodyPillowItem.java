package com.groid.papon.item;

import com.groid.papon.entity.BodyPillowEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * The trophy for beating Chef Papon, handed over by Gerbin the dishwasher himself (see
 * GerbinEntity#reward): a body pillow, Makima on the front, "Woof" on the back.
 *
 *   hold use           hug it: arms wrapped round it, cheek on it, swaying (client/PillowClient); hearts
 *                      and a purr now and then, and a line of what Gerbin would say
 *   hold use, sneaking lick it (the tongue out, the head bobbing into it): slurps, drips
 *   hold use, looking down   hump it (folded over at the hips, squashed on every thrust): hearts; long enough and
 *                      you finish (effect/PostNutClarityEffect)
 *   use on a bed       lay it on the bed (entity/BodyPillowEntity): it slouches against the headboard; right-click
 *                      it to climb on with it; sneak + right-click to sit her on the edge of the bed, her feet down,
 *                      and right-click her then to get down on the floor and lick them (jump: lift them to your
 *                      face); punch her to take her back; sleep there and she lies beside you, you on your side facing her
 */
public class BodyPillowItem extends Item {
    public BodyPillowItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext ctx) {
        // before the bed gets the click (it would put you to bed instead)
        Level level = ctx.getLevel();
        if (!(level.getBlockState(ctx.getClickedPos()).getBlock() instanceof BedBlock) || ctx.getPlayer() == null || ctx.getPlayer().isCrouching()) {
            return InteractionResult.PASS;
        }
        // not in the clarity after (effect/PostNutClarityEffect): you cannot face laying her on a bed
        if (com.groid.papon.effect.PostNutClarityEffect.refractory(ctx.getPlayer())) return InteractionResult.FAIL;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BodyPillowEntity pillow = BodyPillowEntity.place(level, ctx.getClickedPos());
        if (pillow == null) {
            ctx.getPlayer().displayClientMessage(Component.translatable("message.papon.body_pillow.bed_taken").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return InteractionResult.FAIL;
        }
        level.playSound(null, pillow.getX(), pillow.getY(), pillow.getZ(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.0F, 0.8F);
        ctx.getPlayer().displayClientMessage(Component.translatable("message.papon.body_pillow.bed").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), true);
        if (!ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public int getUseDuration(ItemStack stack) { return 72000; }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }

    /** Holding it and looking down (not sneaking): the hump. */
    public static boolean humping(LivingEntity user) {
        return !user.isCrouching() && user.getXRot() > 40;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel server) || !(user instanceof Player player)) return;
        int held = getUseDuration(stack) - remaining;
        double x = player.getX(), y = player.getEyeY() - 0.3, z = player.getZ();
        // in the clarity after (effect/PostNutClarityEffect) you can only bring yourself to hug her
        if ((humping(player) || player.isCrouching()) && com.groid.papon.effect.PostNutClarityEffect.refractory(player)) {
            player.stopUsingItem();
            return;
        }
        if (humping(player)) {
            if (player instanceof net.minecraft.server.level.ServerPlayer sp && com.groid.papon.effect.PostNutClarityEffect.humped(sp)) {
                player.stopUsingItem();
                return;
            }
            // a stroke every 7 ticks (client/PillowClient.thrust)
            if (player.tickCount % 7 == 2) {
                server.playSound(null, x, y - 0.6, z, SoundEvents.WOOL_STEP, SoundSource.PLAYERS, 0.8F, 0.7F + level.random.nextFloat() * 0.2F);
                if (level.random.nextInt(3) == 0) server.playSound(null, x, y - 0.6, z, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.4F, 1.6F);
            }
            if (held % 20 == 6) server.sendParticles(ParticleTypes.HEART, x, y + 0.3, z, 1, 0.3, 0.2, 0.3, 0.02);
            if (held % 90 == 40) {
                player.displayClientMessage(Component.translatable("message.papon.body_pillow.hump." + level.random.nextInt(4))
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), true);
            }
            return;
        }
        if (player.isCrouching()) {
            // a lick every half a second or so
            if (held % 11 == 5) {
                server.playSound(null, x, y, z, SoundEvents.HONEY_DRINK, SoundSource.PLAYERS, 0.7F, 1.3F + level.random.nextFloat() * 0.3F);
                server.playSound(null, x, y, z, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.5F, 1.4F);
                var look = player.getLookAngle();
                server.sendParticles(ParticleTypes.DRIPPING_WATER, x + look.x * 0.45, y + 0.15, z + look.z * 0.45, 2, 0.12, 0.1, 0.12, 0);
            }
            if (held % 60 == 30) {
                player.displayClientMessage(Component.translatable("message.papon.body_pillow.lick." + level.random.nextInt(4))
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), true);
            }
            return;
        }
        if (held % 24 == 8) { server.sendParticles(ParticleTypes.HEART, x, y + 0.4, z, 2, 0.35, 0.2, 0.35, 0.02); }
        if (held % 48 == 8) {
            server.playSound(null, x, y, z, SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.8F, 0.7F);
            server.playSound(null, x, y, z, SoundEvents.CAT_PURR, SoundSource.PLAYERS, 0.5F, 1.1F);
        }
        if (held % 80 == 20) {
            player.displayClientMessage(Component.translatable("message.papon.body_pillow." + level.random.nextInt(4))
                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), true);
        }
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) { consumer.accept(com.groid.papon.client.PillowClient.EXTENSIONS); }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.papon.body_pillow.tooltip.0").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.papon.body_pillow.tooltip.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.papon.body_pillow.tooltip.2").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.papon.body_pillow.tooltip.3").withStyle(ChatFormatting.DARK_GRAY));
    }
}
