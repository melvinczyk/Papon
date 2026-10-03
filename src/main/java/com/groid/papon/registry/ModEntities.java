package com.groid.papon.registry;

import com.groid.papon.PaponMod;
import com.groid.papon.boss.MolotovEntity;
import com.groid.papon.boss.PaponBossEntity;
import com.groid.papon.entity.npc.GerbinEntity;
import com.groid.papon.vehicle.CaravanEntity;
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

    // Gerbin, the dishwasher in the back of the truck (a chort, at 1.25x).
    public static final RegistryObject<EntityType<GerbinEntity>> GERBIN = REGISTRY.register("gerbin",
            () -> EntityType.Builder.of(GerbinEntity::new, MobCategory.MISC)
                    .sized(0.75F, 1.9F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build(PaponMod.id("gerbin").toString()));

    // The boss: the taquero after the unmasking, 1.5x like before.
    public static final RegistryObject<EntityType<PaponBossEntity>> PAPON_BOSS = REGISTRY.register("papon_boss",
            () -> EntityType.Builder.of(PaponBossEntity::new, MobCategory.MONSTER)
                    .sized(1.35F, 2.4F)
                    .fireImmune()
                    .clientTrackingRange(12)
                    .build(PaponMod.id("papon_boss").toString()));

    // His 2001 Dodge Caravan. Square hitbox about the van's width; the length is handled in code.
    public static final RegistryObject<EntityType<CaravanEntity>> CARAVAN = REGISTRY.register("caravan",
            () -> EntityType.Builder.<CaravanEntity>of(CaravanEntity::new, MobCategory.MISC)
                    .sized(2.4F, 2.1F)
                    .fireImmune()
                    .clientTrackingRange(12)
                    .updateInterval(1)
                    .build(PaponMod.id("caravan").toString()));

    // Phase two: the foil-wrapped potatoes that circle him and soak up hits.
    public static final RegistryObject<EntityType<com.groid.papon.boss.FoilPotatoEntity>> FOIL_POTATO = REGISTRY.register("foil_potato",
            () -> EntityType.Builder.<com.groid.papon.boss.FoilPotatoEntity>of(com.groid.papon.boss.FoilPotatoEntity::new, MobCategory.MISC)
                    .sized(0.9F, 0.7F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build(PaponMod.id("foil_potato").toString()));

    public static final RegistryObject<EntityType<com.groid.papon.boss.PotatoShotEntity>> POTATO_SHOT = REGISTRY.register("potato_shot",
            () -> EntityType.Builder.<com.groid.papon.boss.PotatoShotEntity>of(com.groid.papon.boss.PotatoShotEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(6)
                    .updateInterval(2)
                    .build("potato_shot"));
    public static final RegistryObject<EntityType<MolotovEntity>> MOLOTOV = REGISTRY.register("molotov",
            () -> EntityType.Builder.<MolotovEntity>of(MolotovEntity::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F)
                    .clientTrackingRange(8)
                    .updateInterval(5)
                    .build(PaponMod.id("molotov").toString()));

    // Everything Chef Papon throws or summons: cleavers, fries, tots, meteors, the burrito, sprouts, the gravity spud, the mash wave
    public static final RegistryObject<EntityType<com.groid.papon.boss.BossProp>> BOSS_PROP = REGISTRY.register("boss_prop",
            () -> EntityType.Builder.<com.groid.papon.boss.BossProp>of(com.groid.papon.boss.BossProp::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build(PaponMod.id("boss_prop").toString()));

    private static RegistryObject<EntityType<TacoCustomerEntity>> customer(Customer kind, float width, float height) {
        return REGISTRY.register(kind.id, () -> EntityType.Builder.of(TacoCustomerEntity.factory(kind), MobCategory.MISC)
                .sized(width, height)
                .fireImmune()
                .clientTrackingRange(10)
                .build(PaponMod.id(kind.id).toString()));
    }

    private ModEntities() {}
}
