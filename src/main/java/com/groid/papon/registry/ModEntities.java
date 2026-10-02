package com.groid.papon.registry;

import com.groid.papon.PaponMod;
import com.groid.papon.entity.WanderingPaponEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> REGISTRY = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, PaponMod.MOD_ID);

    // MISC so he never counts against (or is culled by) any spawn category cap.
    public static final RegistryObject<EntityType<WanderingPaponEntity>> WANDERING_PAPON = REGISTRY.register("wandering_papon",
            () -> EntityType.Builder.of(WanderingPaponEntity::new, MobCategory.MISC)
                    .sized(0.9F, 1.5F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build(PaponMod.id("wandering_papon").toString()));

    private ModEntities() {}
}
