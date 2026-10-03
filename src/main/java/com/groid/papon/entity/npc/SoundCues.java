package com.groid.papon.entity.npc;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Sound keyframes in customer.animation.json and papon_npc.animation.json name a cue; this picks
 * the vanilla sound for it, so cues can be retuned here without regenerating the animations.
 * Runs on the client (GeckoLib fires sound keyframes there), hence playLocalSound.
 */
public final class SoundCues {
    private SoundCues() {}

    /** Client: when each entity last played each cue, so overlapping animations never stack one sound. */
    private static final Map<Entity, Map<String, Long>> LAST = new WeakHashMap<>();
    /** Ticks before the same entity may play the same cue again. */
    private static final int REPEAT_TICKS = 8;

    public static void play(Entity e, String cue) {
        long now = e.level().getGameTime();
        Map<String, Long> last = LAST.computeIfAbsent(e, k -> new HashMap<>());
        Long prev = last.get(cue);
        if (prev != null && now - prev < REPEAT_TICKS && now >= prev) return;
        last.put(cue, now);
        SoundEvent sound;
        float volume = 0.7F;
        float pitch = 1.0F;
        switch (cue) {
            // eating and drinking
            case "bite" -> { sound = SoundEvents.GENERIC_EAT; volume = 0.6F; }
            case "glug" -> { sound = SoundEvents.GENERIC_DRINK; volume = 0.4F; pitch = 0.8F; }
            case "sip" -> { sound = SoundEvents.GENERIC_DRINK; volume = 0.35F; pitch = 1.5F; }
            case "burp" -> { sound = SoundEvents.PLAYER_BURP; volume = 0.8F; pitch = 0.5F; }
            case "gulp" -> { sound = SoundEvents.PLAYER_BURP; volume = 0.5F; pitch = 1.4F; }
            // Don Pollo's phone, which always rings at the worst moment
            case "ring_hi" -> { sound = SoundEvents.NOTE_BLOCK_BELL.value(); volume = 0.9F; pitch = 1.6F; }
            case "ring_lo" -> { sound = SoundEvents.NOTE_BLOCK_BELL.value(); volume = 0.9F; pitch = 1.2F; }
            case "ring_mid" -> { sound = SoundEvents.NOTE_BLOCK_BELL.value(); volume = 0.9F; pitch = 1.42F; }
            case "fan" -> { sound = SoundEvents.WOOL_PLACE; volume = 0.5F; pitch = 1.6F; }
            case "hot" -> { sound = SoundEvents.FIRE_EXTINGUISH; volume = 0.35F; pitch = 1.8F; }
            // crowd noises
            case "hype" -> { sound = SoundEvents.VILLAGER_CELEBRATE; volume = 0.4F; pitch = 0.7F; }
            case "cheer" -> { sound = SoundEvents.VILLAGER_CELEBRATE; volume = 0.35F; pitch = 1.45F; }
            case "hmm" -> { sound = SoundEvents.VILLAGER_AMBIENT; volume = 0.3F; pitch = 0.75F; }
            case "flex" -> { sound = SoundEvents.ARMOR_EQUIP_LEATHER; volume = 0.5F; pitch = 0.7F; }
            case "step" -> { sound = SoundEvents.WOOL_STEP; volume = 0.4F; }
            case "tap" -> { sound = SoundEvents.WOOD_STEP; volume = 0.3F; pitch = 1.4F; }
            case "phone" -> { sound = SoundEvents.UI_BUTTON_CLICK.value(); volume = 0.25F; pitch = 1.8F; }
            case "tie" -> { sound = SoundEvents.ARMOR_EQUIP_LEATHER; volume = 0.4F; pitch = 1.4F; }
            // the window
            case "scribble" -> { sound = SoundEvents.VILLAGER_WORK_CARTOGRAPHER; volume = 0.7F; pitch = 1.3F; }
            case "sizzle" -> { sound = SoundEvents.FIRE_EXTINGUISH; volume = 0.25F; pitch = 1.9F; }
            case "flip" -> { sound = SoundEvents.ITEM_PICKUP; volume = 0.5F; pitch = 0.75F; }
            case "clank" -> { sound = SoundEvents.LANTERN_PLACE; volume = 0.8F; pitch = 0.9F; }
            case "serve" -> { sound = SoundEvents.ITEM_PICKUP; volume = 0.6F; pitch = 1.2F; }
            case "ding" -> { sound = SoundEvents.NOTE_BLOCK_BELL.value(); volume = 0.7F; pitch = 1.9F; }
            case "paper" -> { sound = SoundEvents.BOOK_PAGE_TURN; volume = 1.0F; }
            case "squint" -> { sound = SoundEvents.WANDERING_TRADER_AMBIENT; volume = 0.6F; pitch = 0.75F; }
            case "gasp" -> { sound = SoundEvents.VILLAGER_TRADE; volume = 0.45F; pitch = 1.6F; }
            case "rumble" -> { sound = SoundEvents.RAVAGER_STUNNED; volume = 0.6F; pitch = 1.6F; }
            case "slam" -> { sound = SoundEvents.ANVIL_LAND; volume = 0.7F; pitch = 0.6F; }
            case "roar" -> { sound = SoundEvents.RAVAGER_ROAR; volume = 1.2F; pitch = 1.5F; }
            case "crumple" -> { sound = SoundEvents.BOOK_PUT; volume = 1.0F; pitch = 0.7F; }
            case "pop" -> { sound = SoundEvents.ITEM_PICKUP; volume = 0.5F; pitch = 1.6F; }
            case "jump" -> { sound = SoundEvents.SLIME_JUMP_SMALL; volume = 0.6F; }
            case "plop" -> { sound = SoundEvents.SLIME_SQUISH; volume = 0.6F; pitch = 0.8F; }
            // the unmasking
            case "nervous" -> { sound = SoundEvents.VILLAGER_NO; volume = 0.8F; pitch = 0.6F; }
            case "rip" -> { sound = SoundEvents.SHEEP_SHEAR; volume = 1.4F; pitch = 0.7F; }
            case "yelp" -> { sound = SoundEvents.FOX_HURT; volume = 0.9F; pitch = 0.6F; }
            case "whoosh" -> { sound = SoundEvents.PLAYER_ATTACK_SWEEP; volume = 0.8F; pitch = 1.3F; }
            case "cloth" -> { sound = SoundEvents.ARMOR_EQUIP_LEATHER; volume = 0.9F; pitch = 0.9F; }
            case "reveal" -> { sound = SoundEvents.AMETHYST_BLOCK_CHIME; volume = 1.5F; pitch = 1.6F; }
            case "breeze" -> { sound = SoundEvents.ELYTRA_FLYING; volume = 0.15F; pitch = 1.6F; }
            case "pat" -> { sound = SoundEvents.SLIME_BLOCK_STEP; volume = 0.8F; pitch = 1.3F; }
            case "crouch" -> { sound = SoundEvents.ARMOR_EQUIP_LEATHER; volume = 0.7F; pitch = 0.6F; }
            // Gerbin at work
            case "chop" -> { sound = SoundEvents.WOOD_HIT; volume = 0.35F; pitch = 1.5F; }
            case "leaf" -> { sound = SoundEvents.AZALEA_LEAVES_HIT; volume = 0.6F; pitch = 1.2F; }
            case "swish" -> { sound = SoundEvents.AZALEA_LEAVES_BREAK; volume = 0.5F; pitch = 1.4F; }
            case "sigh" -> { sound = SoundEvents.FOX_AMBIENT; volume = 0.4F; pitch = 0.8F; }
            case "pick" -> { sound = SoundEvents.ITEM_PICKUP; volume = 0.4F; pitch = 1.1F; }
            case "splash" -> { sound = SoundEvents.GENERIC_SPLASH; volume = 0.35F; pitch = 1.4F; }
            case "scrub" -> { sound = SoundEvents.BRUSH_GENERIC; volume = 0.6F; pitch = 1.2F; }
            case "squeak" -> { sound = SoundEvents.GLOW_ITEM_FRAME_ROTATE_ITEM; volume = 0.6F; pitch = 1.8F; }
            case "drip" -> { sound = SoundEvents.POINTED_DRIPSTONE_DRIP_WATER; volume = 0.6F; }
            case "hey" -> { sound = SoundEvents.FOX_AMBIENT; volume = 0.6F; pitch = 1.6F; }
            // Chef Papon, the boss
            case "smash" -> { sound = SoundEvents.GLASS_BREAK; volume = 1.2F; pitch = 0.9F; }
            case "hiccup" -> { sound = SoundEvents.PLAYER_BURP; volume = 0.9F; pitch = 1.7F; }
            case "keys" -> { sound = SoundEvents.CHAIN_STEP; volume = 0.5F; pitch = 1.9F; }
            case "grunt" -> { sound = SoundEvents.RAVAGER_STEP; volume = 0.7F; pitch = 1.4F; }
            case "swing" -> { sound = SoundEvents.PLAYER_ATTACK_SWEEP; volume = 1.2F; pitch = 0.6F; }
            case "roar_small" -> { sound = SoundEvents.RAVAGER_ROAR; volume = 0.8F; pitch = 1.3F; }
            case "flick" -> { sound = SoundEvents.FLINTANDSTEEL_USE; volume = 1.0F; }
            case "flame" -> { sound = SoundEvents.FIRECHARGE_USE; volume = 0.6F; pitch = 0.8F; }
            case "thud" -> { sound = SoundEvents.WOOL_FALL; volume = 1.0F; pitch = 0.6F; }
            case "sob" -> { sound = SoundEvents.VILLAGER_HURT; volume = 0.9F; pitch = 0.5F; }
            default -> { return; }
        }
        e.level().playLocalSound(e.getX(), e.getY(), e.getZ(), sound, SoundSource.NEUTRAL, volume,
                pitch * (0.95F + e.level().random.nextFloat() * 0.1F), false);
    }
}
