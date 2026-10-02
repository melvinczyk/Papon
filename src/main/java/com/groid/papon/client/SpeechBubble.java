package com.groid.papon.client;

import com.groid.papon.entity.Speech;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Draws a speaker's current line over its head: dark text on a pale card, wrapped, facing the
 * camera like a name tag but sitting above it. Hidden past {@link #MAX_DISTANCE}.
 */
public final class SpeechBubble {
    private static final double MAX_DISTANCE = 24;
    private static final int WRAP = 150;
    private static final int TEXT = 0xFF1E1E1E;
    private static final int CARD = 0xE8FFF8E6;

    private SpeechBubble() {}

    public static void render(Entity entity, String encoded, float lift, PoseStack pose, MultiBufferSource buffers,
                              int light, EntityRenderDispatcher dispatcher) {
        Component line = Speech.decode(encoded);
        if (line == null || dispatcher.distanceToSqr(entity) > MAX_DISTANCE * MAX_DISTANCE) return;
        Font font = Minecraft.getInstance().font;
        List<FormattedCharSequence> rows = font.split(line, WRAP);
        pose.pushPose();
        pose.translate(0, entity.getBbHeight() + lift + rows.size() * 0.25F, 0);
        pose.mulPose(dispatcher.cameraOrientation());
        pose.scale(-0.025F, -0.025F, 0.025F);
        Matrix4f m = pose.last().pose();
        for (int i = 0; i < rows.size(); i++) {
            FormattedCharSequence row = rows.get(i);
            float x = -font.width(row) / 2F;
            font.drawInBatch(row, x, i * 10, TEXT, false, m, buffers, Font.DisplayMode.NORMAL, CARD, light);
        }
        pose.popPose();
    }
}
