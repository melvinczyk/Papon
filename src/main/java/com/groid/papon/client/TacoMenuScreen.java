package com.groid.papon.client;

import com.groid.papon.network.PaponNetwork;
import com.groid.papon.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The reformed Chef Papon's menu board, chalk on a wooden board: four tacos, their prices crossed out,
 * GRATIS under every one. Pick one and he makes it (TacoVendorEntity#order); the board closes.
 */
public class TacoMenuScreen extends Screen {
    private static final String[] PRICES = {"$45", "$55", "$40", "$35"};
    private static final int W = 236, ROW = 30;
    private final int vendorId;
    private int left, top;

    public TacoMenuScreen(int vendorId) {
        super(Component.translatable("menu.papon.tacos.title"));
        this.vendorId = vendorId;
    }

    @Override
    protected void init() {
        int h = 64 + ModItems.TACOS.size() * ROW;
        left = (width - W) / 2;
        top = (height - h) / 2;
        for (int i = 0; i < ModItems.TACOS.size(); i++) {
            int taco = i;
            addRenderableWidget(Button.builder(Component.translatable("menu.papon.tacos.order"), b -> {
                PaponNetwork.orderTaco(vendorId, taco);
                onClose();
            }).bounds(left + W - 70, top + 44 + i * ROW + 4, 58, 18).build());
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        int h = 64 + ModItems.TACOS.size() * ROW;
        // the board: a wooden frame round a slate of chalkboard
        g.fill(left - 6, top - 6, left + W + 6, top + h + 6, 0xFF6B4423);
        g.fill(left - 4, top - 4, left + W + 4, top + h + 4, 0xFF8A5A2F);
        g.fill(left, top, left + W, top + h, 0xFF23302A);
        g.fill(left + 4, top + 34, left + W - 4, top + 35, 0x66FFFFFF);
        g.drawCenteredString(font, title.copy().withStyle(ChatFormatting.BOLD), left + W / 2, top + 8, 0xFFF4D35E);
        g.drawCenteredString(font, Component.translatable("menu.papon.tacos.subtitle"), left + W / 2, top + 20, 0xFFE8E4D8);
        for (int i = 0; i < ModItems.TACOS.size(); i++) {
            int y = top + 44 + i * ROW;
            ItemStack stack = new ItemStack(ModItems.TACOS.get(i).get());
            g.renderItem(stack, left + 10, y + 5);
            g.drawString(font, stack.getHoverName(), left + 32, y + 3, 0xFFFFFFFF);
            // the old price, chalked out, and the new one
            int px = left + 32;
            g.drawString(font, PRICES[i], px, y + 15, 0xFFB0A898);
            int pw = font.width(PRICES[i]);
            g.fill(px - 1, y + 18, px + pw + 1, y + 19, 0xFFE05A4E);
            g.drawString(font, Component.translatable("menu.papon.tacos.free").withStyle(ChatFormatting.BOLD), px + pw + 6, y + 15, 0xFF7BE06A);
        }
        g.drawCenteredString(font, Component.translatable("menu.papon.tacos.footer").withStyle(ChatFormatting.ITALIC), left + W / 2, top + h - 12,
                0xFFB8C4BC);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
