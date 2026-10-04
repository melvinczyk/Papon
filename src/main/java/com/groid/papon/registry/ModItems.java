package com.groid.papon.registry;

import com.groid.papon.PaponMod;
import com.groid.papon.item.CaravanKeysItem;
import com.groid.papon.item.ChanclaItem;
import com.groid.papon.item.TequilaItem;
import com.groid.papon.item.JarritoItem;
import com.groid.papon.item.TacoCouponItem;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.function.Supplier;

public final class ModItems {
    public static final DeferredRegister<Item> REGISTRY = DeferredRegister.create(ForgeRegistries.ITEMS, PaponMod.MOD_ID);

    public static final RegistryObject<Item> TACO_COUPON = REGISTRY.register("taco_coupon",
            () -> new TacoCouponItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    public static final RegistryObject<Item> JARRITO_RED = jarrito("red", JarritoItem.Flavor.RED);
    public static final RegistryObject<Item> JARRITO_ORANGE = jarrito("orange", JarritoItem.Flavor.ORANGE);
    public static final RegistryObject<Item> JARRITO_YELLOW = jarrito("yellow", JarritoItem.Flavor.YELLOW);
    public static final RegistryObject<Item> JARRITO_GREEN = jarrito("green", JarritoItem.Flavor.GREEN);

    public static final RegistryObject<Item> CHANCLA = REGISTRY.register("chancla",
            () -> new ChanclaItem(new Item.Properties()));

    public static final RegistryObject<Item> TEQUILA = REGISTRY.register("tequila",
            () -> new TequilaItem(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> CARAVAN_KEYS = REGISTRY.register("caravan_keys",
            () -> new CaravanKeysItem(new Item.Properties().rarity(Rarity.RARE).fireResistant()));

    /** Gerbin's present to whoever beats Chef Papon. */
    public static final RegistryObject<Item> BODY_PILLOW = REGISTRY.register("body_pillow",
            () -> new com.groid.papon.item.BodyPillowItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    /** Chef Papon's tacos, free from his menu once he is beaten and back at the window (TacoVendorEntity). */
    public static final RegistryObject<Item> TACO_AL_PASTOR = taco("al_pastor", 8, 0.8F);
    public static final RegistryObject<Item> TACO_CARNE_ASADA = taco("carne_asada", 8, 0.9F);
    public static final RegistryObject<Item> TACO_POLLO = taco("pollo", 7, 0.7F);
    public static final RegistryObject<Item> TACO_PAPA = REGISTRY.register("taco_papa", () -> new Item(new Item.Properties()
            .food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(6).saturationMod(0.75F)
                    .effect(() -> new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 100), 1F)
                    .build())));
    /** The menu, in order. */
    public static final List<RegistryObject<Item>> TACOS = List.of(TACO_AL_PASTOR, TACO_CARNE_ASADA, TACO_POLLO, TACO_PAPA);

    private static RegistryObject<Item> taco(String name, int nutrition, float saturation) {
        return REGISTRY.register("taco_" + name, () -> new Item(new Item.Properties()
                .food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(nutrition).saturationMod(saturation).build())));
    }

    // models for the boss fight's props (client/PropRenderer, TumbleRenderer); not in any tab
    public static final RegistryObject<Item> FX_SPUD = prop("fx_spud");
    public static final RegistryObject<Item> FX_METEOR = prop("fx_meteor");
    public static final RegistryObject<Item> FX_CLEAVER = prop("fx_cleaver");
    public static final RegistryObject<Item> FX_FRY = prop("fx_fry");
    public static final RegistryObject<Item> FX_TOT = prop("fx_tot");
    public static final RegistryObject<Item> FX_GUITAR = prop("fx_guitar");
    public static final RegistryObject<Item> FX_BURRITO = prop("fx_burrito");
    public static final RegistryObject<Item> FX_SPROUTS = prop("fx_sprouts");
    public static final RegistryObject<Item> FX_GIANT_SPUD = prop("fx_giant_spud");
    public static final RegistryObject<Item> FX_PINATA = prop("fx_pinata");
    public static final RegistryObject<Item> FX_CANDY = prop("fx_candy");

    public static final RegistryObject<Item> WANDERING_PAPON_SPAWN_EGG = egg("wandering_papon", ModEntities.WANDERING_PAPON, 0xD9AA51, 0x5C7840);
    public static final RegistryObject<Item> TACO_VENDOR_SPAWN_EGG = egg("taco_vendor", ModEntities.TACO_VENDOR, 0xF6F6F0, 0xD9AA51);
    public static final RegistryObject<Item> CUSTOMER_PAPON_SPAWN_EGG = egg("customer_papon", ModEntities.CUSTOMER_PAPON, 0xD9AA51, 0xBA2428);
    public static final RegistryObject<Item> DON_POLLO_SPAWN_EGG = egg("don_pollo", ModEntities.DON_POLLO, 0xEEC630, 0xB01A22);
    public static final RegistryObject<Item> EDP445_SPAWN_EGG = egg("edp445", ModEntities.EDP445, 0x004E56, 0xA8AEB2);
    public static final RegistryObject<Item> BADLANDS_CHUGS_SPAWN_EGG = egg("badlands_chugs", ModEntities.BADLANDS_CHUGS, 0xEEEEEA, 0xC42428);
    public static final RegistryObject<Item> REVIEWBRAH_SPAWN_EGG = egg("reviewbrah", ModEntities.REVIEWBRAH, 0x383A42, 0xA01E28);

    public static final RegistryObject<Item> GERBIN_SPAWN_EGG = egg("gerbin", ModEntities.GERBIN, 0x2E2E32, 0xF2F2EE);
    public static final RegistryObject<Item> PAPON_BOSS_SPAWN_EGG = egg("papon_boss", ModEntities.PAPON_BOSS, 0xD9AA51, 0x2C201A);

    public static final List<RegistryObject<Item>> SPAWN_EGGS = List.of(WANDERING_PAPON_SPAWN_EGG, TACO_VENDOR_SPAWN_EGG,
            CUSTOMER_PAPON_SPAWN_EGG, DON_POLLO_SPAWN_EGG, EDP445_SPAWN_EGG, BADLANDS_CHUGS_SPAWN_EGG, REVIEWBRAH_SPAWN_EGG,
            GERBIN_SPAWN_EGG, PAPON_BOSS_SPAWN_EGG);

    private static RegistryObject<Item> egg(String name, Supplier<? extends EntityType<? extends Mob>> type, int base, int spots) {
        return REGISTRY.register(name + "_spawn_egg", () -> new ForgeSpawnEggItem(type, base, spots, new Item.Properties()));
    }

    private static RegistryObject<Item> prop(String name) {
        return REGISTRY.register(name, () -> new Item(new Item.Properties()));
    }

    private static RegistryObject<Item> jarrito(String name, JarritoItem.Flavor flavor) {
        return REGISTRY.register("jarrito_" + name, () -> new JarritoItem(flavor, new Item.Properties()));
    }

    private ModItems() {}
}
