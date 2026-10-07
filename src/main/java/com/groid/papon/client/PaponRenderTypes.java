package com.groid.papon.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * Our render types. {@link #glow()} is vanilla's lightning (additive, coloured vertices, no texture) but it does not write
 * depth (2026-10-05). Lightning does, so every glow we drew with it (the spotlight's cone, the beams, the flames, El Gigante's
 * light) stamped its shape into the depth buffer: anything drawn after it and behind it failed the depth test and vanished,
 * the stands behind phase three's spotlight left with a hole in the shape of the beam (the user: "the crowd rendering behind
 * the spotlight"). A glow should only ever add light.
 */
public final class PaponRenderTypes extends RenderType {
    private static final RenderType GLOW = create("papon_glow", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, true,
            CompositeState.builder().setShaderState(RENDERTYPE_LIGHTNING_SHADER).setWriteMaskState(COLOR_WRITE)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY).setOutputState(WEATHER_TARGET).createCompositeState(false));

    private PaponRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort, Runnable setup,
                             Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }

    /** Additive light: adds to what is behind it, hides nothing. */
    public static RenderType glow() { return GLOW; }

    private static final java.util.Map<net.minecraft.resources.ResourceLocation, RenderType> MIRRORS = new java.util.HashMap<>();

    /**
     * A mirror's live picture (SkylineMirrors): unlit (the picture is already lit like the world it shows), smooth, opaque,
     * both sides; text's shader for its colour times the lightmap, and fog.
     */
    public static RenderType mirror(net.minecraft.resources.ResourceLocation picture) {
        return MIRRORS.computeIfAbsent(picture, id -> create("papon_mirror", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 256,
                false, false, CompositeState.builder().setShaderState(RENDERTYPE_TEXT_SHADER).setTextureState(new TextureStateShard(id, true, false))
                        .setLightmapState(LIGHTMAP).setCullState(NO_CULL).createCompositeState(false)));
    }
}
