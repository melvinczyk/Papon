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
}
