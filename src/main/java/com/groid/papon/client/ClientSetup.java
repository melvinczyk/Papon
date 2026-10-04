package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.entity.npc.Customer;
import com.groid.papon.entity.npc.TacoCustomerEntity;
import com.groid.papon.client.mesh.MeshCrowdRenderer;
import com.groid.papon.client.mesh.SkinnedMesh;
import com.groid.papon.registry.ModEntities;
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
        fx(event, ModParticles.FIRE.get(), FxParticle.Mode.FIRE);
        fx(event, ModParticles.TORCH.get(), FxParticle.Mode.FIRE);
        fx(event, ModParticles.FLAME.get(), FxParticle.Mode.FLAME);
        fx(event, ModParticles.BLAST.get(), FxParticle.Mode.BLAST);
        fx(event, ModParticles.BURST.get(), FxParticle.Mode.BURST);
        fx(event, ModParticles.SMOKE.get(), FxParticle.Mode.SMOKE);
        fx(event, ModParticles.DUST.get(), FxParticle.Mode.DUST);
        fx(event, ModParticles.SLASH.get(), FxParticle.Mode.SLASH);
        fx(event, ModParticles.HIT.get(), FxParticle.Mode.HIT);
        fx(event, ModParticles.SHOCK.get(), FxParticle.Mode.SHOCK);
        fx(event, ModParticles.DECAL.get(), FxParticle.Mode.DECAL);
        fx(event, ModParticles.SPARK.get(), FxParticle.Mode.SPARK);
        fx(event, ModParticles.CHUNK.get(), FxParticle.Mode.CHUNK);
        fx(event, ModParticles.FLASH.get(), FxParticle.Mode.FLASH);
    }

    private static void fx(RegisterParticleProvidersEvent event, net.minecraft.core.particles.ParticleType<com.groid.papon.fx.FxOptions> type,
                           FxParticle.Mode mode) {
        event.registerSpriteSet(type, sprites -> new FxParticle.Provider(sprites, mode));
    }

    /** The body pillow on players (hugged, licked, carried): client/PillowLayer. */
    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            var renderer = event.getSkin(skin);
            if (renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer pr) pr.addLayer(new PillowLayer(pr));
        }
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.WANDERING_PAPON.get(), WanderingPaponRenderer::new);
        // The Papon at the window is the boss to come: half again as big as any other.
        event.registerEntityRenderer(ModEntities.TACO_VENDOR.get(), VendorRenderer::new);
        customer(event, ModEntities.DON_POLLO.get(), Customer.DON_POLLO);
        // EDP445 and Badlands Chugs are MakeHuman bodies shaped to their builds, with their
        // photographed faces (tools/mh_human.py); their props are cubes painted into the same atlas.
        mesh(event, ModEntities.EDP445.get(), Customer.EDP445, 0.9F);
        mesh(event, ModEntities.BADLANDS_CHUGS.get(), Customer.BADLANDS_CHUGS, 0.95F);
        customer(event, ModEntities.REVIEWBRAH.get(), Customer.REVIEWBRAH);
        customer(event, ModEntities.CUSTOMER_PAPON.get(), Customer.PAPON);
        // Gerbin the dishwasher: a chort at 1.25x; his name tag is vanilla's, his bubble sits above it
        event.registerEntityRenderer(ModEntities.GERBIN.get(), GerbinRenderer::new);
        event.registerEntityRenderer(ModEntities.PAPON_BOSS.get(), BossRenderer::new);
        event.registerEntityRenderer(ModEntities.CARAVAN.get(), CaravanRenderer::new);
        event.registerEntityRenderer(ModEntities.FOIL_POTATO.get(), FoilPotatoRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_PORTAL.get(), ArenaPortalRenderer::new);
        event.registerEntityRenderer(ModEntities.MOLOTOV.get(),
                ctx -> new TumbleRenderer<>(ctx, () -> new net.minecraft.world.item.ItemStack(com.groid.papon.registry.ModItems.TEQUILA.get()), 1.1F, 24F));
        event.registerEntityRenderer(ModEntities.POTATO_SHOT.get(),
                ctx -> new TumbleRenderer<>(ctx, () -> new net.minecraft.world.item.ItemStack(com.groid.papon.registry.ModItems.FX_SPUD.get()), 0.7F, 40F));
        event.registerEntityRenderer(ModEntities.CROWD_POTATO.get(),
                ctx -> new TumbleRenderer<>(ctx, () -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.POTATO), 0.9F, 18F));
        event.registerEntityRenderer(ModEntities.BOSS_PROP.get(), PropRenderer::new);
        event.registerEntityRenderer(ModEntities.PAPON_ECHO.get(), EchoRenderer::new);
        event.registerEntityRenderer(ModEntities.BEAM.get(), BeamRenderer::new);
        event.registerEntityRenderer(ModEntities.SPIRIT_GIANT.get(), SpiritGiantRenderer::new);
        event.registerEntityRenderer(ModEntities.MARIACHI.get(), MariachiRenderer::new);
        event.registerEntityRenderer(ModEntities.COLISEUM_CROWD.get(), ColiseumCrowdRenderer::new);
        event.registerEntityRenderer(ModEntities.BODY_PILLOW.get(), BodyPillowRenderer::new);
        event.registerBlockEntityRenderer(com.groid.papon.registry.ModBlockEntities.ARENA_FLAG.get(), ArenaFlagRenderer::new);
        event.registerBlockEntityRenderer(com.groid.papon.registry.ModBlockEntities.STAGE_LAMP.get(), StageLampRenderer::new);
    }

    private static void mesh(EntityRenderersEvent.RegisterRenderers event, EntityType<TacoCustomerEntity> type,
                             Customer kind, float shadow) {
        event.registerEntityRenderer(type, ctx -> new MeshCrowdRenderer<>(ctx,
                new CrowdModel<>(kind.id + "_mesh", kind.id + "_mesh", "customer", true),
                kind.renderScale, shadow, 0.75F, kind.id));
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
