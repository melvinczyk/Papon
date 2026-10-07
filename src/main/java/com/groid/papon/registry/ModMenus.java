package com.groid.papon.registry;

import com.groid.papon.PaponMod;
import com.groid.papon.menu.TrashCanMenu;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The mod's container windows: the trash can's (client/TrashCanScreen). */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> REGISTRY = DeferredRegister.create(ForgeRegistries.MENU_TYPES, PaponMod.MOD_ID);

    public static final RegistryObject<MenuType<TrashCanMenu>> TRASH_CAN = REGISTRY.register("trash_can",
            () -> new MenuType<>(TrashCanMenu::new, FeatureFlags.VANILLA_SET));

    private ModMenus() {}
}
