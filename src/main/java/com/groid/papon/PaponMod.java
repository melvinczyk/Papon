package com.groid.papon;

import com.groid.papon.boss.PaponBossEntity;
import com.groid.papon.entity.WanderingPaponEntity;
import com.groid.papon.network.PaponNetwork;
import com.groid.papon.registry.ModSounds;
import com.groid.papon.vehicle.CaravanEntity;
import com.groid.papon.entity.npc.TacoNpc;
import com.groid.papon.registry.ModBlockEntities;
import com.groid.papon.registry.ModBlocks;
import com.groid.papon.registry.ModEffects;
import com.groid.papon.registry.ModEntities;
import com.groid.papon.registry.ModItems;
import com.groid.papon.registry.ModParticles;
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
        ModBlocks.REGISTRY.register(bus);
        ModBlockEntities.REGISTRY.register(bus);
        com.groid.papon.registry.ModMenus.REGISTRY.register(bus);
        ModEntities.REGISTRY.register(bus);
        ModItems.REGISTRY.register(bus);
        ModParticles.REGISTRY.register(bus);
        ModSounds.REGISTRY.register(bus);
        ModEffects.REGISTRY.register(bus);
        PaponNetwork.register();
        bus.addListener(this::attributes);
        bus.addListener(this::creativeTabs);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, PaponConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    private void attributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.WANDERING_PAPON.get(), WanderingPaponEntity.createAttributes().build());
        event.put(ModEntities.TACO_VENDOR.get(), TacoNpc.createAttributes().build());
        event.put(ModEntities.DON_POLLO.get(), TacoNpc.createAttributes().build());
        event.put(ModEntities.EDP445.get(), TacoNpc.createAttributes().build());
        event.put(ModEntities.BADLANDS_CHUGS.get(), TacoNpc.createAttributes().build());
        event.put(ModEntities.REVIEWBRAH.get(), TacoNpc.createAttributes().build());
        event.put(ModEntities.CUSTOMER_PAPON.get(), TacoNpc.createAttributes().build());
        event.put(ModEntities.GERBIN.get(), TacoNpc.createAttributes().build());
        event.put(ModEntities.MARIACHI.get(), TacoNpc.createAttributes().build());
        event.put(ModEntities.SALSA_DANCER.get(), TacoNpc.createAttributes().build());
        event.put(ModEntities.PAPON_BOSS.get(), PaponBossEntity.createAttributes().build());
        event.put(ModEntities.CARAVAN.get(), CaravanEntity.createAttributes().build());
        event.put(ModEntities.MAKIMA.get(), com.groid.papon.entity.MakimaEntity.createAttributes().build());
        event.put(ModEntities.FOIL_POTATO.get(), com.groid.papon.boss.FoilPotatoEntity.createAttributes().build());
    }

    private void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModItems.TACO_COUPON);
        } else if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(ModItems.JARRITO_RED);
            event.accept(ModItems.JARRITO_ORANGE);
            event.accept(ModItems.JARRITO_YELLOW);
            event.accept(ModItems.JARRITO_GREEN);
            event.accept(ModItems.TEQUILA);
            ModItems.TACOS.forEach(event::accept);
        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.CHANCLA);
            event.accept(ModItems.CARAVAN_KEYS);
            event.accept(ModItems.SKYLINE_FOB);
            event.accept(ModItems.BODY_PILLOW);
        } else if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModItems.TRASH_CAN);
        } else if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) { ModItems.SPAWN_EGGS.forEach(event::accept); }
    }
}
