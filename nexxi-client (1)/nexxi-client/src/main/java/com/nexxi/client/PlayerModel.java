package com.nexxi.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Draws the local player's 3D model (the same one the inventory screen shows).
 *
 * Mojang renamed this helper between versions, so it is looked up at runtime. If it can't be found
 * or fails, the model is skipped and everything else keeps working.
 */
public class PlayerModel {
    private static Method method;
    private static boolean searched = false;
    private static boolean broken = false;

    public static void draw(Object graphics, int x1, int y1, int x2, int y2, int scale, float mouseX, float mouseY) {
        if (broken) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        try {
            if (!searched) {
                searched = true;
                for (Method m : InventoryScreen.class.getDeclaredMethods()) {
                    if (Modifier.isStatic(m.getModifiers())
                        && m.getName().toLowerCase().contains("entityininventoryfollowsmouse")) {
                        m.setAccessible(true);
                        method = m;
                        break;
                    }
                }
            }
            if (method == null) {
                broken = true;
                return;
            }

            Class<?>[] types = method.getParameterTypes();
            Object[] args = new Object[types.length];
            int[] ints = {x1, y1, x2, y2, scale};
            float[] floats = {0.0625f, mouseX, mouseY};
            int ii = 0;
            int fi = 0;
            for (int i = 0; i < types.length; i++) {
                Class<?> t = types[i];
                if (t == int.class && ii < ints.length) {
                    args[i] = ints[ii++];
                } else if (t == float.class && fi < floats.length) {
                    args[i] = floats[fi++];
                } else if (t.isInstance(graphics)) {
                    args[i] = graphics;
                } else if (t.isInstance(mc.player)) {
                    args[i] = mc.player;
                } else {
                    broken = true;
                    return;
                }
            }
            method.invoke(null, args);
        } catch (Throwable t) {
            broken = true;
            System.err.println("[Nexxi] Player model unavailable: " + t);
        }
    }
}
