package com.groid.papon.registry;

import com.groid.papon.PaponMod;
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

    public static final RegistryObject<Item> WANDERING_PAPON_SPAWN_EGG = REGISTRY.register("wandering_papon_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.WANDERING_PAPON, 0xD9AA51, 0x5C7840, new Item.Properties()));

    private ModItems() {}
}
