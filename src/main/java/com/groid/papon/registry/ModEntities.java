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

    // The way back into his coliseum: a portal in the Taco Truck's window while it stands.
    public static final RegistryObject<EntityType<com.groid.papon.boss.ArenaPortalEntity>> ARENA_PORTAL = REGISTRY.register("arena_portal",
            () -> EntityType.Builder.<com.groid.papon.boss.ArenaPortalEntity>of(com.groid.papon.boss.ArenaPortalEntity::new, MobCategory.MISC)
                    .sized(1.0F, 2.2F)
                    .fireImmune()
                    .noSummon()
                    .clientTrackingRange(8)
                    .updateInterval(20)
                    .build(PaponMod.id("arena_portal").toString()));

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
    /** A potato thrown from the stands of his coliseum. */
    public static final RegistryObject<EntityType<com.groid.papon.boss.CrowdPotatoEntity>> CROWD_POTATO = REGISTRY.register("crowd_potato",
            () -> EntityType.Builder.<com.groid.papon.boss.CrowdPotatoEntity>of(com.groid.papon.boss.CrowdPotatoEntity::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F)
                    .clientTrackingRange(6)
                    .updateInterval(2)
                    .build(PaponMod.id("crowd_potato").toString()));
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

    // Phase three's beams of light: his eyes, his statues' eyes, the Aztec sun (only a picture: the boss burns what they touch)
    public static final RegistryObject<EntityType<com.groid.papon.boss.BeamEntity>> BEAM = REGISTRY.register("beam",
            () -> EntityType.Builder.<com.groid.papon.boss.BeamEntity>of(com.groid.papon.boss.BeamEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .fireImmune()
                    .noSummon()
                    .clientTrackingRange(12)
                    .updateInterval(1)
                    .build(PaponMod.id("beam").toString()));

    // Phase four's El Gigante: the hologram skeleton under the arena (its skull and hands over the wall)
    public static final RegistryObject<EntityType<com.groid.papon.boss.SpiritGiantEntity>> SPIRIT_GIANT = REGISTRY.register("spirit_giant",
            () -> EntityType.Builder.<com.groid.papon.boss.SpiritGiantEntity>of(com.groid.papon.boss.SpiritGiantEntity::new, MobCategory.MISC)
                    .sized(2.0F, 2.0F)
                    .fireImmune()
                    .noSummon()
                    .clientTrackingRange(16)
                    .updateInterval(2)
                    .build(PaponMod.id("spirit_giant").toString()));

    // Phase four: the muchachas, skeleton salsa dancers by the stage
    public static final RegistryObject<EntityType<com.groid.papon.boss.SalsaDancerEntity>> SALSA_DANCER = REGISTRY.register("salsa_dancer",
            () -> EntityType.Builder.<com.groid.papon.boss.SalsaDancerEntity>of(com.groid.papon.boss.SalsaDancerEntity::new, MobCategory.MISC)
                    .sized(1.3F, 2.4F)
                    .fireImmune()
                    .clientTrackingRange(12)
                    .build(PaponMod.id("salsa_dancer").toString()));

    // Phase four: Ouros and Boros, the feathered serpents circling the arena (the entity sits in the middle; the serpent is a path)
    public static final RegistryObject<EntityType<com.groid.papon.boss.SkySerpentEntity>> SKY_SERPENT = REGISTRY.register("sky_serpent",
            () -> EntityType.Builder.<com.groid.papon.boss.SkySerpentEntity>of(com.groid.papon.boss.SkySerpentEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .fireImmune()
                    .noSummon()
                    .clientTrackingRange(32)
                    .updateInterval(20)
                    .build(PaponMod.id("sky_serpent").toString()));

    // His echoes (Los Tres Chefs): see-through copies of him in the air that throw cleavers
    public static final RegistryObject<EntityType<com.groid.papon.boss.PaponEchoEntity>> PAPON_ECHO = REGISTRY.register("papon_echo",
            () -> EntityType.Builder.<com.groid.papon.boss.PaponEchoEntity>of(com.groid.papon.boss.PaponEchoEntity::new, MobCategory.MISC)
                    .sized(1.2F, 2.4F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build(PaponMod.id("papon_echo").toString()));

    // El Coliseo Papa (phase two): his mariachi band on the stand, and one entity for the whole crowd in the stands
    public static final RegistryObject<EntityType<com.groid.papon.boss.MariachiEntity>> MARIACHI = REGISTRY.register("mariachi",
            () -> EntityType.Builder.<com.groid.papon.boss.MariachiEntity>of(com.groid.papon.boss.MariachiEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.9F)
                    .fireImmune()
                    .clientTrackingRange(12)
                    .build(PaponMod.id("mariachi").toString()));

    public static final RegistryObject<EntityType<com.groid.papon.boss.ColiseumCrowdEntity>> COLISEUM_CROWD = REGISTRY.register("coliseum_crowd",
            () -> EntityType.Builder.<com.groid.papon.boss.ColiseumCrowdEntity>of(com.groid.papon.boss.ColiseumCrowdEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .fireImmune()
                    .clientTrackingRange(14)
                    .updateInterval(4)
                    .build(PaponMod.id("coliseum_crowd").toString()));

    // Gerbin's present, laid on a bed.
    public static final RegistryObject<EntityType<com.groid.papon.entity.BodyPillowEntity>> BODY_PILLOW = REGISTRY.register("body_pillow",
            () -> EntityType.Builder.<com.groid.papon.entity.BodyPillowEntity>of(com.groid.papon.entity.BodyPillowEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.0F)
                    .clientTrackingRange(8)
                    .updateInterval(40)
                    .build(PaponMod.id("body_pillow").toString()));

    private static RegistryObject<EntityType<TacoCustomerEntity>> customer(Customer kind, float width, float height) {
        return REGISTRY.register(kind.id, () -> EntityType.Builder.of(TacoCustomerEntity.factory(kind), MobCategory.MISC)
                .sized(width, height)
                .fireImmune()
                .clientTrackingRange(10)
                .build(PaponMod.id(kind.id).toString()));
    }

    private ModEntities() {}
}
