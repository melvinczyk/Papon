package com.groid.papon.event;

import com.groid.papon.PaponMod;
import com.groid.papon.command.PaponCommand;
import com.groid.papon.entity.WanderingPaponEntity;
import com.groid.papon.registry.ModEntities;
import com.groid.papon.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = PaponMod.MOD_ID)
public final class CommonEvents {
    /** The Walking Crops Papon. Looked up by name so this mod does not link against Walking Crops. */
    public static final ResourceLocation PAPON = new ResourceLocation("wkcr", "papon");

    private CommonEvents() {}

    /** Taco Coupon on a Papon: he becomes the Wandering Papon and sets off. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        ItemStack stack = event.getItemStack();
        Entity target = event.getTarget();
        if (!stack.is(ModItems.TACO_COUPON.get()) || !PAPON.equals(ForgeRegistries.ENTITY_TYPES.getKey(target.getType()))) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if (!(event.getLevel() instanceof ServerLevel level) || !(target instanceof Mob papon) || !papon.isAlive()) {
            return;
        }
        Player player = event.getEntity();
        if (!level.dimensionType().hasSkyLight()) {
            player.displayClientMessage(Component.translatable("message.papon.no_trucks_here").withStyle(ChatFormatting.RED), true);
            return;
        }

        WanderingPaponEntity wanderer = ModEntities.WANDERING_PAPON.get().create(level);
        if (wanderer == null) return;
        wanderer.moveTo(papon.getX(), papon.getY(), papon.getZ(), papon.getYRot(), 0);
        if (papon.hasCustomName()) wanderer.setCustomName(papon.getCustomName());
        level.addFreshEntity(wanderer);
        wanderer.beginJourney(player);
        papon.discard();

        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, wanderer.getX(), wanderer.getY() + 0.9, wanderer.getZ(),
                24, 0.5, 0.6, 0.5, 0.0);
        level.sendParticles(ParticleTypes.POOF, wanderer.getX(), wanderer.getY() + 0.6, wanderer.getZ(),
                20, 0.4, 0.5, 0.4, 0.03);
        level.playSound(null, wanderer.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.5F, 1.2F);
        level.playSound(null, wanderer.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.6F, 1.6F);
        if (!player.getAbilities().instabuild) stack.shrink(1);

        player.sendSystemMessage(Component.translatable("message.papon.coupon_accepted").withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.translatable("message.papon.coupon_rules").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    /** Whacking the Wandering Papon with a chancla. It never hurts him; it might wake him. */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getTarget() instanceof WanderingPaponEntity papon
                && event.getEntity().getMainHandItem().is(ModItems.CHANCLA.get())) {
            event.setCanceled(true);
            if (!event.getEntity().level().isClientSide) papon.onChancla(event.getEntity());
        }
    }

    /** Nothing may take the Wandering Papon as a target. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof WanderingPaponEntity) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        PaponCommand.register(event.getDispatcher());
    }
}
