package com.groid.papon.event;

import com.groid.papon.PaponMod;
import com.groid.papon.command.PaponCommand;
import com.groid.papon.entity.WanderingPaponEntity;
import com.groid.papon.entity.npc.TacoNpc;
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
    /** Post Nut Clarity (effect/PostNutClarityEffect): mining at half speed. */
    @SubscribeEvent
    public static void onBreakSpeed(net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed event) {
        event.setNewSpeed(com.groid.papon.effect.PostNutClarityEffect.mining(event.getEntity(), event.getNewSpeed()));
    }

    /** Post Nut Clarity: jumps a quarter lower. */
    @SubscribeEvent
    public static void onJump(net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent event) {
        com.groid.papon.effect.PostNutClarityEffect.jump(event.getEntity());
    }

    /** Post Nut Clarity: you lie down and cannot sleep. */
    @SubscribeEvent
    public static void onSleep(net.minecraftforge.event.entity.player.PlayerSleepInBedEvent event) {
        if (com.groid.papon.effect.PostNutClarityEffect.sleep(event.getEntity())) event.setResult(Player.BedSleepingProblem.OTHER_PROBLEM);
    }

    /** A player with the body pillow on a bed: the camera where the body is drawn (kneeling, flat on the floor at her feet). */
    @SubscribeEvent
    public static void onSize(net.minecraftforge.event.entity.EntityEvent.Size event) {
        float eye = com.groid.papon.entity.BodyPillowEntity.eyeHeight(event.getEntity());
        if (!Float.isNaN(eye)) event.setNewEyeHeight(eye);
    }

    /**
     * The body pillow on a bed, before any other mod (Carry On picks up whatever you sneak + right-click with an empty hand):
     * right-clicks reach her whatever is in your hand.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPillowInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof com.groid.papon.entity.BodyPillowEntity pillow)) return;
        InteractionResult result = pillow.interact(event.getEntity(), event.getHand());
        if (!result.consumesAction()) return;
        event.setCanceled(true);
        event.setCancellationResult(result);
    }

    /** Sneak + right-click the bed she is on (her hitbox is only her middle): move her, as if you had clicked her. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPillowBedUse(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND || !player.isSecondaryUseActive()) return;
        var pillow = com.groid.papon.entity.BodyPillowEntity.at(event.getLevel(), event.getPos());
        if (pillow == null || pillow.isVehicle()) return;
        event.setCanceled(true);
        event.setCancellationResult(pillow.interact(player, event.getHand()));
    }

    /** Punching the bed she is on takes her back instead of breaking it (break it once she is off). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPillowBedHit(PlayerInteractEvent.LeftClickBlock event) {
        var pillow = com.groid.papon.entity.BodyPillowEntity.at(event.getLevel(), event.getPos());
        if (pillow == null) return;
        event.setCanceled(true);
        if (!event.getLevel().isClientSide && event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START) pillow.takeBack(event.getEntity());
    }

    /** Punching her: back into your arms, whatever you hit her with (no other mod's attack in the way). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPillowHit(AttackEntityEvent event) {
        if (!(event.getTarget() instanceof com.groid.papon.entity.BodyPillowEntity pillow)) return;
        event.setCanceled(true);
        if (!pillow.level().isClientSide && pillow.isAlive()) pillow.takeBack(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        ItemStack stack = event.getItemStack();
        Entity target = event.getTarget();
        if (!stack.is(ModItems.TACO_COUPON.get()) || !PAPON.equals(ForgeRegistries.ENTITY_TYPES.getKey(target.getType()))) { return; }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if (!(event.getLevel() instanceof ServerLevel level) || !(target instanceof Mob papon) || !papon.isAlive()) { return; }
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

    /** Nothing may take the Wandering Papon, or anyone at the Taco Truck, as a target. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof WanderingPaponEntity || event.getNewTarget() instanceof TacoNpc) { event.setCanceled(true); }
    }

    /** A totem in Chef Papon's fight: he calls it what it is. */
    @SubscribeEvent
    public static void onTotem(net.minecraftforge.event.entity.living.LivingUseTotemEvent event) {
        if (!(event.getEntity() instanceof Player p) || p.level().isClientSide) return;
        for (com.groid.papon.boss.PaponBossEntity b : p.level().getEntitiesOfClass(com.groid.papon.boss.PaponBossEntity.class,
                p.getBoundingBox().inflate(48))) {
            b.sawTotem(p);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        PaponCommand.register(event.getDispatcher());
    }
}
