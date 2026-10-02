package com.groid.papon.registry;

import com.groid.papon.PaponMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> REGISTRY =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, PaponMod.MOD_ID);

    /** The "Z" that drifts up while he sleeps. */
    public static final RegistryObject<SimpleParticleType> SNOOZE =
            REGISTRY.register("snooze", () -> new SimpleParticleType(false));

    private ModParticles() {}
}
