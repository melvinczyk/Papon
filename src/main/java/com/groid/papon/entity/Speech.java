package com.groid.papon.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Lines spoken at the Taco Truck. A line is a lang key (plus optional arguments), so all the
 * dialogue lives in en_us.json and can be rewritten without touching code. It is shown two ways:
 *
 *   bubble  the speaker's synced speech string, drawn over its head by the renderer while it lasts
 *   chat    "<Name> line" to every player within {@link #HEAR_RADIUS}; a key with a ".gloss"
 *           sibling (the Spanish lines) also prints that translation underneath in grey
 *
 * No voices are recorded: speakers "babble" a pitched blip per syllable-ish (see {@link Voice}),
 * Animal Crossing style.
 */
public final class Speech {
    public static final double HEAR_RADIUS = 40;
    private static final char SEP = '\u001f';

    private Speech() {}

    public interface Speaker {
        String getSpeech();

        void setSpeech(String encoded, int ticks);
    }

    /** Says {@code key} as {@code speaker} for {@code ticks}: a bubble, and if {@code chat} a chat line too. */
    public static void say(Entity speaker, ChatFormatting nameColor, String key, boolean chat, boolean gloss, int ticks,
                           Object... args) {
        if (!(speaker.level() instanceof ServerLevel level)) return;
        StringBuilder enc = new StringBuilder(key);
        for (Object a : args) enc.append(SEP).append(a);
        if (speaker instanceof Speaker s) s.setSpeech(enc.toString(), ticks);
        if (!chat) return;

        MutableComponent line = Component.literal("<").append(speaker.getDisplayName().copy().withStyle(nameColor))
                .append("> ").append(Component.translatable(key, args).withStyle(ChatFormatting.WHITE));
        Component translation = gloss
                ? Component.literal("    ").append(Component.translatable(key + ".gloss", args))
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
                : null;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(speaker) > HEAR_RADIUS * HEAR_RADIUS) continue;
            p.sendSystemMessage(line);
            if (translation != null) p.sendSystemMessage(translation);
        }
    }

    /** The component an encoded speech string stands for, or null when nobody is talking. */
    public static Component decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) return null;
        String[] parts = encoded.split(String.valueOf(SEP));
        Object[] args = new Object[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        return Component.translatable(parts[0], args);
    }

    /** Client: how long the babble runs for this line, from its translated length. */
    public static int babbleLength(String encoded) {
        Component c = decode(encoded);
        return c == null ? 0 : Mth.clamp(c.getString().length(), 8, 70);
    }

    /** A character's talking sound: one blip every few ticks at a jittered pitch. */
    public record Voice(SoundEvent sound, float pitch, float volume, int every) {
        public Voice(SoundEvent sound, float pitch, float volume) {
            this(sound, pitch, volume, 3);
        }

        public void blip(Entity e, int ticksLeft) {
            if (ticksLeft % every != 0) return;
            float jitter = 0.88F + e.level().random.nextFloat() * 0.24F;
            e.level().playLocalSound(e.getX(), e.getEyeY(), e.getZ(), sound, SoundSource.NEUTRAL, volume,
                    pitch * jitter, false);
        }
    }
}
