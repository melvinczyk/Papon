package com.groid.papon.effect;

import com.groid.papon.registry.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Post Nut Clarity: what comes over you after humping the body pillow long enough ({@link #humped}, from the hump in hand
 * (item/BodyPillowItem) and every way on a bed (entity/BodyPillowEntity)): {@link #FINISH} ticks of it, a pause of more
 * than {@link #GRACE} starting you over. Three minutes ({@link #DURATION}) of:
 *   slow        35% off your speed, lower jumps ({@link #jump})
 *   limp        attacks 30% weaker and 25% slower, mining at half speed ({@link #mining})
 *   sick        nausea at the start, then in waves now and then
 *   sleepless   you lie down and cannot sleep ({@link #sleep})
 *   ashamed     nothing with her but a hug ({@link #refractory}); grey thoughts now and then, a pool of them
 *               ({@code message.papon.clarity.N}): you killed the final boss for this pillow
 */
public class PostNutClarityEffect extends MobEffect {
    /** Ticks of humping to finish (the dev tour shortens it: system property papon.clarity). */
    public static final int FINISH = 600;
    public static final int GRACE = 200, DURATION = 3600;
    /** How many of each line there are in the lang file: the thoughts, the refusals, the sleepless nights. */
    public static final int THOUGHTS = 40, NOT_NOW = 12, SLEEPLESS = 4;
    private static final String TICKS = "PaponHumpTicks", LAST = "PaponHumpLast";

    public PostNutClarityEffect() {
        super(MobEffectCategory.HARMFUL, 0xC9D6E3);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, "6e2f8a1c-4b7d-4c93-8e15-2d9a7b3c5f60", -0.35, AttributeModifier.Operation.MULTIPLY_TOTAL);
        addAttributeModifier(Attributes.ATTACK_DAMAGE, "1b7c3e94-5d2a-4f60-9a8e-7c4f2b1d6e38", -0.3, AttributeModifier.Operation.MULTIPLY_TOTAL);
        addAttributeModifier(Attributes.ATTACK_SPEED, "a3d58f17-2c6e-4b49-8d71-5e9b0c3a7f24", -0.25, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    /** A tick of humping; true when that was the last one (the effect is on them now, and they should stop). */
    public static boolean humped(ServerPlayer p) {
        var data = p.getPersistentData();
        long now = p.level().getGameTime();
        int ticks = now - data.getLong(LAST) > GRACE ? 0 : data.getInt(TICKS);
        data.putLong(LAST, now);
        if (++ticks < Integer.getInteger("papon.clarity", FINISH)) {
            data.putInt(TICKS, ticks);
            return false;
        }
        data.putInt(TICKS, 0);
        finish(p);
        return true;
    }

    private static void finish(ServerPlayer p) {
        p.addEffect(new MobEffectInstance(ModEffects.POST_NUT_CLARITY.get(), DURATION, 0, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 300, 0, false, false, false));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.9F, 0.7F);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.8F, 0.6F);
        p.connection.send(new ClientboundSetTitlesAnimationPacket(8, 60, 25));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.papon.clarity.title").withStyle(ChatFormatting.GRAY)));
        p.connection.send(new ClientboundSetSubtitleTextPacket(line("clarity.", THOUGHTS, p).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
    }

    private static net.minecraft.network.chat.MutableComponent line(String key, int count, Player p) {
        return Component.translatable("message.papon." + key + p.getRandom().nextInt(count));
    }

    private static void think(Player p, String key, int count) {
        p.displayClientMessage(line(key, count, p).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) { return duration % 20 == 0; }

    /** Once a second: a grey thought now and then (about every 15 s), a wave of nausea about every 40 s. */
    @Override
    public void applyEffectTick(LivingEntity e, int amplifier) {
        if (e.level().isClientSide || !(e instanceof Player p)) return;
        var effect = p.getEffect(ModEffects.POST_NUT_CLARITY.get());
        if (effect == null || effect.getDuration() > DURATION - 100) return;      // the title is still up
        if (p.getRandom().nextInt(15) == 0) think(p, "clarity.", THOUGHTS);
        if (!p.hasEffect(MobEffects.CONFUSION) && p.getRandom().nextInt(40) == 0) {
            p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0, false, false, false));
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.5F, 0.6F);
        }
    }

    public static boolean has(LivingEntity e) { return e.hasEffect(ModEffects.POST_NUT_CLARITY.get()); }

    /** In the clarity: true (with a word to them) if they try anything with her but a hug. */
    public static boolean refractory(Player p) {
        if (!has(p)) return false;
        if (!p.level().isClientSide) think(p, "clarity.not_now.", NOT_NOW);
        return true;
    }

    /** Mining at half speed (Forge's BreakSpeed, event/CommonEvents). */
    public static float mining(Player p, float speed) { return has(p) ? speed * 0.5F : speed; }

    /** No spring in your step: jumps a quarter lower (Forge's LivingJumpEvent). */
    public static void jump(LivingEntity e) {
        if (!has(e)) return;
        Vec3 v = e.getDeltaMovement();
        e.setDeltaMovement(v.x * 0.8, v.y * 0.78, v.z * 0.8);
    }

    /** You lie down and cannot sleep (Forge's PlayerSleepInBedEvent): true to refuse, with a word to them. */
    public static boolean sleep(Player p) {
        if (!has(p)) return false;
        if (!p.level().isClientSide) think(p, "clarity.sleepless.", SLEEPLESS);
        return true;
    }
}
