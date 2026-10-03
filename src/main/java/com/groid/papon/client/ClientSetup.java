package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.entity.npc.Customer;
import com.groid.papon.entity.npc.TacoCustomerEntity;
import com.groid.papon.client.mesh.MeshCrowdRenderer;
import com.groid.papon.client.mesh.SkinnedMesh;
import com.groid.papon.registry.ModEntities;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.entity.EntityType;
import com.groid.papon.registry.ModParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = PaponMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> SkinnedMesh.clear());
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SNOOZE.get(), SnoozeParticle.Provider::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.WANDERING_PAPON.get(), WanderingPaponRenderer::new);
        // The Papon at the window is the boss to come: half again as big as any other.
        event.registerEntityRenderer(ModEntities.TACO_VENDOR.get(),
                ctx -> new CrowdRenderer<>(ctx, new CrowdModel<>("taco_vendor", "papon_npc", false), 1.5F, 0.9F, 0.35F));
        customer(event, ModEntities.DON_POLLO.get(), Customer.DON_POLLO);
        // EDP445's body is the EDP SIM character, skinned onto the customer rig (gen_edp_mesh.py);
        // his beanie, taco and phone are still cubes, textured from the blocky model.
        event.registerEntityRenderer(ModEntities.EDP445.get(), ctx -> new MeshCrowdRenderer<>(ctx,
                new CrowdModel<>("edp445_mesh", "edp445", "customer", true),
                Customer.EDP445.renderScale, 0.5F, 0.75F, "edp445"));
        // Badlands Chugs is a MakeHuman body shaped to his build, with his photographed face
        // (gen_chugs_mesh.py); his jug and taco are cubes, textured from the blocky model.
        event.registerEntityRenderer(ModEntities.BADLANDS_CHUGS.get(), ctx -> new MeshCrowdRenderer<>(ctx,
                new CrowdModel<>("badlands_chugs_mesh", "badlands_chugs", "customer", true),
                Customer.BADLANDS_CHUGS.renderScale, 0.6F, 0.75F, "badlands_chugs"));
        customer(event, ModEntities.REVIEWBRAH.get(), Customer.REVIEWBRAH);
        customer(event, ModEntities.CUSTOMER_PAPON.get(), Customer.PAPON);
        // Gerbin the dishwasher: a chort at 1.25x; his name tag is vanilla's, his bubble sits above it
        event.registerEntityRenderer(ModEntities.GERBIN.get(),
                ctx -> new CrowdRenderer<>(ctx, new CrowdModel<>("gerbin", "gerbin", true), 1.25F, 0.4F, 0.6F));
        event.registerEntityRenderer(ModEntities.PAPON_BOSS.get(), BossRenderer::new);
        event.registerEntityRenderer(ModEntities.CARAVAN.get(), CaravanRenderer::new);
        event.registerEntityRenderer(ModEntities.MOLOTOV.get(), ThrownItemRenderer::new);
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
