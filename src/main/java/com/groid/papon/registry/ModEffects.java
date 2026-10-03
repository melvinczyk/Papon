package com.groid.papon.registry;

import com.groid.papon.PaponMod;
import com.groid.papon.effect.StunnedEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> REGISTRY = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, PaponMod.MOD_ID);

    /** Run over (or rear-ended) by the van: down on the ground, unable to move. */
    public static final RegistryObject<MobEffect> STUNNED = REGISTRY.register("stunned", StunnedEffect::new);

    private ModEffects() {}
}
