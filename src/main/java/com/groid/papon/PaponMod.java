package com.groid.papon;

import com.groid.papon.entity.WanderingPaponEntity;
import com.groid.papon.registry.ModEntities;
import com.groid.papon.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(PaponMod.MOD_ID)
public class PaponMod {
    public static final String MOD_ID = "papon";
    public static final Logger LOG = LoggerFactory.getLogger("Papon");

    public PaponMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.REGISTRY.register(bus);
        ModItems.REGISTRY.register(bus);
        bus.addListener(this::attributes);
        bus.addListener(this::creativeTabs);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, PaponConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    private void attributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.WANDERING_PAPON.get(), WanderingPaponEntity.createAttributes().build());
    }

    private void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModItems.TACO_COUPON);
        } else if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.WANDERING_PAPON_SPAWN_EGG);
        }
    }
}
