package com.groid.papon.registry;

import com.groid.papon.PaponMod;
import com.groid.papon.entity.WanderingPaponEntity;
import com.groid.papon.entity.npc.Customer;
import com.groid.papon.entity.npc.TacoCustomerEntity;
import com.groid.papon.entity.npc.TacoVendorEntity;
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

    // The Taco Truck crowd. MISC for the same reason; never spawned naturally.
    public static final RegistryObject<EntityType<TacoVendorEntity>> TACO_VENDOR = REGISTRY.register("taco_vendor",
            () -> EntityType.Builder.of(TacoVendorEntity::new, MobCategory.MISC)
                    .sized(1.35F, 2.4F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build(PaponMod.id("taco_vendor").toString()));

    public static final RegistryObject<EntityType<TacoCustomerEntity>> DON_POLLO = customer(Customer.DON_POLLO, 0.7F, 1.95F);
    public static final RegistryObject<EntityType<TacoCustomerEntity>> EDP445 = customer(Customer.EDP445, 1.1F, 2.2F);
    public static final RegistryObject<EntityType<TacoCustomerEntity>> BADLANDS_CHUGS = customer(Customer.BADLANDS_CHUGS, 1.15F, 2.4F);
    public static final RegistryObject<EntityType<TacoCustomerEntity>> REVIEWBRAH = customer(Customer.REVIEWBRAH, 0.6F, 1.95F);
    public static final RegistryObject<EntityType<TacoCustomerEntity>> CUSTOMER_PAPON = customer(Customer.PAPON, 0.9F, 1.5F);

    private static RegistryObject<EntityType<TacoCustomerEntity>> customer(Customer kind, float width, float height) {
        return REGISTRY.register(kind.id, () -> EntityType.Builder.of(TacoCustomerEntity.factory(kind), MobCategory.MISC)
                .sized(width, height)
                .fireImmune()
                .clientTrackingRange(10)
                .build(PaponMod.id(kind.id).toString()));
    }

    private ModEntities() {}
}
