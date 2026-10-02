package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.entity.npc.Customer;
import com.groid.papon.entity.npc.TacoCustomerEntity;
import com.groid.papon.registry.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = PaponMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.WANDERING_PAPON.get(), WanderingPaponRenderer::new);
        // The Papon at the window is the boss to come: half again as big as any other.
        event.registerEntityRenderer(ModEntities.TACO_VENDOR.get(),
                ctx -> new CrowdRenderer<>(ctx, new CrowdModel<>("taco_vendor", "papon_npc", false), 1.5F, 0.9F, 0.35F));
        customer(event, ModEntities.DON_POLLO.get(), Customer.DON_POLLO);
        customer(event, ModEntities.EDP445.get(), Customer.EDP445);
        customer(event, ModEntities.BADLANDS_CHUGS.get(), Customer.BADLANDS_CHUGS);
        customer(event, ModEntities.REVIEWBRAH.get(), Customer.REVIEWBRAH);
        customer(event, ModEntities.CUSTOMER_PAPON.get(), Customer.PAPON);
    }

    private static void customer(EntityRenderersEvent.RegisterRenderers event, EntityType<TacoCustomerEntity> type,
                                 Customer kind) {
        String anims = kind.human ? "customer" : "papon_npc";
        // the famous ones wear name tags, so their bubbles sit a little higher
        float lift = kind.human ? 0.75F : 0.35F;
        event.registerEntityRenderer(type, ctx -> new CrowdRenderer<>(ctx, new CrowdModel<>(kind.id, anims, kind.human),
                kind.renderScale, kind.human ? 0.5F : 0.6F, lift));
    }
}
