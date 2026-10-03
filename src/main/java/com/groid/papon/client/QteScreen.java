package com.groid.papon.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Open (invisible, not pausing) while a QTE runs, so every key goes to the QTE and none to a keybind:
 * with a screen up Minecraft hands keys to it instead of the key mappings, so J does not open the map,
 * E not the inventory, no mod hotkey fires. The QTE itself is drawn by {@link QteClient} on the HUD. Esc
 * still gets you the pause menu.
 */
public class QteScreen extends Screen {
    public QteScreen() {
        super(Component.empty());
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (minecraft != null) minecraft.setScreen(new PauseScreen(true));
            return true;
        }
        QteClient.press(key);
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // nothing: the HUD under it shows the QTE
    }
}
