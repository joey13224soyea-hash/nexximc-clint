package com.nexxi.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Builds the "Nexxi Client" button that sits under the vanilla buttons on the pause and title menus. */
public class NexxiMenu {
    public static Button makeButton(Screen screen) {
        int minX = Integer.MAX_VALUE;
        int maxRight = Integer.MIN_VALUE;
        int maxBottom = Integer.MIN_VALUE;

        // Only look at the wide "real" menu buttons, not the little icon buttons.
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget w && w.getWidth() >= 98) {
                minX = Math.min(minX, w.getX());
                maxRight = Math.max(maxRight, w.getX() + w.getWidth());
                maxBottom = Math.max(maxBottom, w.getY() + w.getHeight());
            }
        }

        if (minX == Integer.MAX_VALUE) {
            minX = screen.width / 2 - 102;
            maxRight = minX + 204;
            maxBottom = screen.height / 4 + 120;
        }

        int y = Math.min(maxBottom + 4, screen.height - 24);
        return Button.builder(
            Component.literal(Config.get().brand),
            b -> Minecraft.getInstance().gui.setScreen(new NexxiScreen(screen))
        ).bounds(minX, y, maxRight - minX, 20).build();
    }
}
