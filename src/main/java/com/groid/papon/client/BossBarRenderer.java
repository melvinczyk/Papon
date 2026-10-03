package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.boss.PaponBossEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;

/**
 * Chef Papon's boss bar, drawn in place of the vanilla one (textures/gui/boss_bar.png, from
 * tools/gen_ui.py): a crisp taco-shell frame with lettuce along the top, filled with salsa (phase
 * two: flaming hot sauce), a taco at the empty end, and a little bald potato, Chef Papon himself,
 * riding the tip of the fill as it drains. The name sits above it in gold.
 *
 * Sheet layout (256x64): frame 0,0 220x24 | fill 0,24 204x10 | phase-two fill 0,34 204x10 |
 * potato 0,44 16x16 | taco 16,44 16x16 | phase-two potato 32,44 16x16.
 */
public final class BossBarRenderer {
    private static final ResourceLocation SHEET = PaponMod.id("textures/gui/boss_bar.png");
    private static final int FRAME_W = 220;
    private static final int FRAME_H = 24;
    private static final int FILL_W = 204;
    private static final int FILL_H = 10;

    private BossBarRenderer() {}

    public static void render(CustomizeGuiOverlayEvent.BossEventProgress event) {
        LerpingBossEvent bar = event.getBossEvent();
        if (!(bar.getName().getContents() instanceof TranslatableContents tc) || !tc.getKey().startsWith(PaponBossEntity.BAR_KEY)) return;
        event.setCanceled(true);
        event.setIncrement(40);
        boolean phase2 = tc.getKey().endsWith("phase2");
        GuiGraphics g = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        int cx = mc.getWindow().getGuiScaledWidth() / 2;
        int y = event.getY();
        float progress = Mth.clamp(bar.getProgress(), 0, 1);
        long time = mc.level == null ? 0 : mc.level.getGameTime();

        Component name = bar.getName().copy().withStyle(phase2 ? ChatFormatting.RED : ChatFormatting.GOLD, ChatFormatting.BOLD);
        g.drawString(mc.font, name, cx - mc.font.width(name) / 2, y - 1, 0xFFFFFF, true);

        int fx = cx - FRAME_W / 2;
        int fy = y + 9;
        g.blit(SHEET, fx, fy, 0, 0, FRAME_W, FRAME_H, 256, 64);
        int fillX = fx + 8;
        int fillY = fy + 7;
        int w = Math.round(FILL_W * progress);
        if (w > 0) {
            // phase two flickers between two rows of the fire strip
            int v = phase2 ? 34 : 24;
            int scroll = phase2 ? (int) (time % 8) : 0;
            g.blit(SHEET, fillX, fillY, scroll, v, w, FILL_H, 256, 64);
        }
        // the taco waits at the empty end; the potato rides the tip of the fill
        g.blit(SHEET, fx - 10, fy + 4, 16, 44, 16, 16, 256, 64);
        int bob = (int) (Math.sin(time * 0.3) * (phase2 ? 1.5 : 0.8));
        g.blit(SHEET, fillX + w - 8, fy + 3 + bob, phase2 ? 32 : 0, 44, 16, 16, 256, 64);
    }
}
