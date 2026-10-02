package com.groid.papon.registry;

import com.groid.papon.PaponMod;
import com.groid.papon.item.ChanclaItem;
import com.groid.papon.item.JarritoItem;
import com.groid.papon.item.TacoCouponItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

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

    public static final RegistryObject<Item> WANDERING_PAPON_SPAWN_EGG = REGISTRY.register("wandering_papon_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.WANDERING_PAPON, 0xD9AA51, 0x5C7840, new Item.Properties()));

    private static RegistryObject<Item> jarrito(String name, JarritoItem.Flavor flavor) {
        return REGISTRY.register("jarrito_" + name, () -> new JarritoItem(flavor, new Item.Properties()));
    }

    private ModItems() {}
}
