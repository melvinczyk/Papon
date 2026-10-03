package com.groid.papon.registry;

import com.groid.papon.PaponMod;
import com.groid.papon.fx.FxOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The mod's particles. The boss fight's all take {@link FxOptions} (size, tint, life, facing) and are
 * drawn by client/FxParticle; their textures come from the Groid Pack's boss and monster mods, pixel art
 * throughout (tools/gen_particles.py). Spawn them through {@link com.groid.papon.fx.Fx}.
 */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> REGISTRY =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, PaponMod.MOD_ID);

    /** The "Z" that drifts up while he sleeps. */
    public static final RegistryObject<SimpleParticleType> SNOOZE =
            REGISTRY.register("snooze", () -> new SimpleParticleType(false));

    /** A lick of flame (animated). */
    public static final RegistryObject<ParticleType<FxOptions>> FIRE = fx("fire");
    /** A tall flame standing on the ground (animated): burning puddles. */
    public static final RegistryObject<ParticleType<FxOptions>> FLAME = fx("flame");
    /** A fireball blooming into smoke (animated): meteors, molotovs, explosions. */
    public static final RegistryObject<ParticleType<FxOptions>> BLAST = fx("blast");
    /** A small burst of fire (animated). */
    public static final RegistryObject<ParticleType<FxOptions>> BURST = fx("burst");
    /** A curling puff of smoke (animated, tinted). */
    public static final RegistryObject<ParticleType<FxOptions>> SMOKE = fx("smoke");
    /** A soft dust cloud (animated, tinted): landings, skids. */
    public static final RegistryObject<ParticleType<FxOptions>> DUST = fx("dust");
    /** A swing's arc, flat at its yaw (animated, tinted). */
    public static final RegistryObject<ParticleType<FxOptions>> SLASH = fx("slash");
    /** An impact cut where a hit lands (animated, tinted). */
    public static final RegistryObject<ParticleType<FxOptions>> HIT = fx("hit");
    /** A shockwave ring, flat on the ground, blown outward (animated). */
    public static final RegistryObject<ParticleType<FxOptions>> SHOCK = fx("shock");
    /** A ground marking: where something is about to land (variants: FxParticle.DECAL_*). */
    public static final RegistryObject<ParticleType<FxOptions>> DECAL = fx("decal");
    /** A glint (variants). */
    public static final RegistryObject<ParticleType<FxOptions>> SPARK = fx("spark");
    /** A chunk with gravity: mash, salt, grit (tinted). */
    public static final RegistryObject<ParticleType<FxOptions>> CHUNK = fx("chunk");
    /** A gold flare bursting into embers (animated, Cataclysm's flare). */
    public static final RegistryObject<ParticleType<FxOptions>> FLASH = fx("flash");

    /** A blue flame (animated, Dungeon Now Loading's soul flame): the blowtorch. */
    public static final RegistryObject<ParticleType<FxOptions>> TORCH = fx("torch");

    private static RegistryObject<ParticleType<FxOptions>> fx(String name) {
        return REGISTRY.register(name, FxOptions::newType);
    }

    private ModParticles() {}
}
