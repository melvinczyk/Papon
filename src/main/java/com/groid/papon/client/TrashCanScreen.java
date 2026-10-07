package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.menu.TrashCanMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** The trash can's window (menu/TrashCanMenu): textures/gui/trash_can.png from tools/gen_trash_can.py. */
public class TrashCanScreen extends AbstractContainerScreen<TrashCanMenu> {
    private static final ResourceLocation BG = PaponMod.id("textures/gui/trash_can.png");
    private static final Component HINT = Component.translatable("container.papon.trash_can.hint");

    public TrashCanScreen(TrashCanMenu menu, Inventory inv, Component title) { super(menu, inv, title); }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        g.blit(BG, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        g.drawString(font, HINT, (imageWidth - font.width(HINT)) / 2, 60, 0x707070, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partial);
        renderTooltip(g, mouseX, mouseY);
    }
}
