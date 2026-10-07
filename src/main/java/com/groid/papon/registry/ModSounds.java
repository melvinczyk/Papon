package com.groid.papon.registry;

import com.groid.papon.PaponMod;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The mod's own sounds (assets/papon/sounds.json; the .ogg files are synthesized by
 * tools/gen_sounds.py). Everything else the mod plays is a vanilla sound, picked in SoundCues.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> REGISTRY = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, PaponMod.MOD_ID);

    // the van: loops (idle, drive) are played by CaravanSounds on the client, pitched by speed
    public static final RegistryObject<SoundEvent> CARAVAN_IDLE = sound("caravan.idle");
    public static final RegistryObject<SoundEvent> CARAVAN_DRIVE = sound("caravan.drive");
    public static final RegistryObject<SoundEvent> CARAVAN_START = sound("caravan.start");
    public static final RegistryObject<SoundEvent> CARAVAN_REV = sound("caravan.rev");
    public static final RegistryObject<SoundEvent> CARAVAN_HORN = sound("caravan.horn");
    public static final RegistryObject<SoundEvent> CARAVAN_SCREECH = sound("caravan.screech");
    public static final RegistryObject<SoundEvent> CARAVAN_CRASH = sound("caravan.crash");
    public static final RegistryObject<SoundEvent> CARAVAN_DOOR_OPEN = sound("caravan.door_open");
    public static final RegistryObject<SoundEvent> CARAVAN_DOOR_CLOSE = sound("caravan.door_close");
    public static final RegistryObject<SoundEvent> CARAVAN_HIT = sound("caravan.hit");
    // the Skyline (tools/gen_skyline_sounds.py): an RB26, not the van's V6
    /** The Skyline's engine in layers (tools/gen_skyline_sounds.py; mixed by client/CaravanSounds), outside and from the cockpit. */
    /**
     * The Skyline's engine (2026-10-06, tools/gen_skyline_sounds.py): loops of the six at {@link #SKYLINE_RPM}, on the
     * throttle and off it (the overrun), heard outside and from the cockpit; client/CaravanSounds mixes the two nearest.
     */
    public static final int[] SKYLINE_RPM = {900, 1500, 2300, 3200, 4200, 5300, 6500, 7800};
    public static final RegistryObject<SoundEvent>[] SKYLINE_ON = layers("skyline.on_", ""), SKYLINE_OFF = layers("skyline.off_", ""),
            SKYLINE_ON_IN = layers("skyline.on_", "_in"), SKYLINE_OFF_IN = layers("skyline.off_", "_in");
    public static final RegistryObject<SoundEvent> SKYLINE_TURBO = sound("skyline.turbo");
    public static final RegistryObject<SoundEvent> SKYLINE_ROAD = sound("skyline.road");
    public static final RegistryObject<SoundEvent> SKYLINE_WIND = sound("skyline.wind");
    public static final RegistryObject<SoundEvent> SKYLINE_SHIFT = sound("skyline.shift");
    public static final RegistryObject<SoundEvent> SKYLINE_BLIP = sound("skyline.blip");
    public static final RegistryObject<SoundEvent> SKYLINE_POP = sound("skyline.pop");
    public static final RegistryObject<SoundEvent> SKYLINE_DOOR_OPEN = sound("skyline.door_open");
    public static final RegistryObject<SoundEvent> SKYLINE_DOOR_CLOSE = sound("skyline.door_close");
    public static final RegistryObject<SoundEvent> SKYLINE_START = sound("skyline.start");
    public static final RegistryObject<SoundEvent> SKYLINE_REV = sound("skyline.rev");
    public static final RegistryObject<SoundEvent> SKYLINE_BOV = sound("skyline.bov");
    public static final RegistryObject<SoundEvent> SKYLINE_BACKFIRE = sound("skyline.backfire");
    public static final RegistryObject<SoundEvent> SKYLINE_CHIRP = sound("skyline.chirp");
    public static final RegistryObject<SoundEvent> SKYLINE_GLOW = sound("skyline.glow");
    public static final RegistryObject<SoundEvent> SKYLINE_SUMMON = sound("skyline.summon");
    public static final RegistryObject<SoundEvent> SKYLINE_HORN = sound("skyline.horn");
    public static final RegistryObject<SoundEvent> SKYLINE_SCREECH = sound("skyline.screech");
    public static final RegistryObject<SoundEvent> SKYLINE_CRASH = sound("skyline.crash");

    // the fight
    public static final RegistryObject<SoundEvent> BOSS_MUSIC = sound("music.boss");
    public static final RegistryObject<SoundEvent> REVEAL = sound("boss.reveal");
    // phase four (tools/gen_p4_sounds.py): his panting on his back, the band stopping dead
    public static final RegistryObject<SoundEvent> PANT = sound("boss.pant");
    public static final RegistryObject<SoundEvent> CINE_SOULS = sound("boss.cine_souls");
    public static final RegistryObject<SoundEvent> CINE_SOUL = sound("boss.cine_soul");
    public static final RegistryObject<SoundEvent> CINE_ABSORB = sound("boss.cine_absorb");
    public static final RegistryObject<SoundEvent> CINE_MOON = sound("boss.cine_moon");
    public static final RegistryObject<SoundEvent> CINE_SWELL = sound("boss.cine_swell");
    public static final RegistryObject<SoundEvent> CINE_AWAKEN = sound("boss.cine_awaken");
    public static final RegistryObject<SoundEvent> CINE_EMERGE = sound("boss.cine_emerge");
    public static final RegistryObject<SoundEvent> SCRATCH = sound("boss.scratch");
    // El Gigante (tools/gen_giant_sounds.py): its laser, loud from anywhere in the arena, and a warning cue for each of its attacks
    public static final RegistryObject<SoundEvent> GIANT_LASER_CHARGE = sound("boss.laser_giant_charge");
    public static final RegistryObject<SoundEvent> GIANT_LASER_FIRE = sound("boss.laser_giant_fire");
    public static final RegistryObject<SoundEvent> GIANT_LASER_BURN = sound("boss.laser_giant_burn");
    public static final RegistryObject<SoundEvent> GIANT_LASER_END = sound("boss.laser_giant_end");
    public static final RegistryObject<SoundEvent> GIANT_LASER_SIZZLE = sound("boss.laser_giant_sizzle");
    public static final RegistryObject<SoundEvent> GIANT_TELL_GRAB = sound("boss.giant_tell_grab");
    public static final RegistryObject<SoundEvent> GIANT_TELL_SWIPE = sound("boss.giant_tell_swipe");
    public static final RegistryObject<SoundEvent> GIANT_TELL_SLAM = sound("boss.giant_tell_slam");
    public static final RegistryObject<SoundEvent> GIANT_TELL_LUNGE = sound("boss.giant_tell_lunge");

    @SuppressWarnings("unchecked")
    private static RegistryObject<SoundEvent>[] layers(String prefix, String suffix) {
        RegistryObject<SoundEvent>[] out = new RegistryObject[SKYLINE_RPM.length];
        for (int i = 0; i < out.length; i++) out[i] = sound(prefix + i + suffix);
        return out;
    }

    private static RegistryObject<SoundEvent> sound(String name) {
        return REGISTRY.register(name, () -> SoundEvent.createVariableRangeEvent(PaponMod.id(name)));
    }

    private ModSounds() {}
}
