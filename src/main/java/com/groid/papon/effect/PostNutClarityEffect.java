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
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Post Nut Clarity: what comes over you after humping the body pillow long enough ({@link #humped}, from the hump in hand
 * (item/BodyPillowItem) and every way on a bed (entity/BodyPillowEntity)): {@link #FINISH} ticks of it, a pause of more
 * than {@link #GRACE} starting you over. Slow (35% off your speed) and sick to your stomach (nausea, hidden behind this
 * icon) for {@link #DURATION}; a title, a sigh, a line. While it lasts you cannot bring yourself to do it again
 * ({@link #refractory}): hugging her is still allowed.
 */
public class PostNutClarityEffect extends MobEffect {
    /** Ticks of humping to finish (the dev tour shortens it: system property papon.clarity). */
    public static final int FINISH = 600;
    public static final int GRACE = 200, DURATION = 300;
    private static final String TICKS = "PaponHumpTicks", LAST = "PaponHumpLast";

    public PostNutClarityEffect() {
        super(MobEffectCategory.HARMFUL, 0xC9D6E3);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, "6e2f8a1c-4b7d-4c93-8e15-2d9a7b3c5f60", -0.35, AttributeModifier.Operation.MULTIPLY_TOTAL);
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
        p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, DURATION, 0, false, false, false));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.9F, 0.7F);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.8F, 0.6F);
        p.connection.send(new ClientboundSetTitlesAnimationPacket(8, 60, 25));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.papon.clarity.title").withStyle(ChatFormatting.GRAY)));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.papon.clarity." + p.getRandom().nextInt(6))
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
    }

    /** In the clarity: true (with a word to them) if they try it again. */
    public static boolean refractory(Player p) {
        if (!p.hasEffect(ModEffects.POST_NUT_CLARITY.get())) return false;
        if (!p.level().isClientSide) {
            p.displayClientMessage(Component.translatable("message.papon.clarity.not_now." + p.getRandom().nextInt(3))
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
        }
        return true;
    }
}
