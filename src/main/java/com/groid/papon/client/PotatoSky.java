package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.boss.PaponBossEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;

/**
 * The sky of Chef Papon's fight: while one is on near you (the fight is long, so you will see both),
 * the sun is a grinning potato with french fries for rays and the moon a silver potato asleep, in all
 * its phases (tools/gen_sky.py). Done without touching the sky renderer: the vanilla sun and moon
 * texture slots are pointed at the potato images for the fight, and back at their own afterwards.
 */
public final class PotatoSky {
    private static final ResourceLocation SUN = new ResourceLocation("textures/environment/sun.png");
    private static final ResourceLocation MOON = new ResourceLocation("textures/environment/moon_phases.png");
    private static final ResourceLocation POTATO_SUN = PaponMod.id("textures/environment/potato_sun.png");
    private static final ResourceLocation POTATO_MOON = PaponMod.id("textures/environment/potato_moon_phases.png");
    private static final double RANGE = 160;
    /** It stays a while after he falls (or you step away), so it does not flicker at the edge. */
    private static final int LINGER = 100;

    private static boolean on;
    private static int sinceSeen = LINGER;

    private PotatoSky() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean fight = mc.level != null && mc.player != null && !mc.level.getEntitiesOfClass(PaponBossEntity.class,
                mc.player.getBoundingBox().inflate(RANGE), PaponBossEntity::isAlive).isEmpty();
        sinceSeen = fight ? 0 : Math.min(LINGER, sinceSeen + 1);
        boolean want = mc.level != null && sinceSeen < LINGER;
        if (want != on) set(mc.getTextureManager(), want);
    }

    private static void set(TextureManager textures, boolean potato) {
        on = potato;
        textures.register(SUN, new SimpleTexture(potato ? POTATO_SUN : SUN));
        textures.register(MOON, new SimpleTexture(potato ? POTATO_MOON : MOON));
    }
}
