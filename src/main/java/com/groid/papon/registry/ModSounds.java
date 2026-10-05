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
    public static final RegistryObject<SoundEvent> SKYLINE_IDLE = sound("skyline.idle");
    public static final RegistryObject<SoundEvent> SKYLINE_DRIVE = sound("skyline.drive");
    public static final RegistryObject<SoundEvent> SKYLINE_START = sound("skyline.start");
    public static final RegistryObject<SoundEvent> SKYLINE_REV = sound("skyline.rev");
    public static final RegistryObject<SoundEvent> SKYLINE_BOV = sound("skyline.bov");
    public static final RegistryObject<SoundEvent> SKYLINE_BACKFIRE = sound("skyline.backfire");
    public static final RegistryObject<SoundEvent> SKYLINE_CHIRP = sound("skyline.chirp");
    public static final RegistryObject<SoundEvent> SKYLINE_GLOW = sound("skyline.glow");

    // the fight
    public static final RegistryObject<SoundEvent> BOSS_MUSIC = sound("music.boss");
    public static final RegistryObject<SoundEvent> REVEAL = sound("boss.reveal");
    // phase four (tools/gen_p4_sounds.py): his panting on his back, the band stopping dead
    public static final RegistryObject<SoundEvent> PANT = sound("boss.pant");
    public static final RegistryObject<SoundEvent> SCRATCH = sound("boss.scratch");

    private static RegistryObject<SoundEvent> sound(String name) {
        return REGISTRY.register(name, () -> SoundEvent.createVariableRangeEvent(PaponMod.id(name)));
    }

    private ModSounds() {}
}
