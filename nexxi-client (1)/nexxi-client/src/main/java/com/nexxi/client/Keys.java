package com.nexxi.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Keybinds. Rebind them in Options > Controls > Key Binds > Nexxi Client. */
public class Keys {
    private static KeyMapping openMenu;
    private static KeyMapping toggleOverlay;
    private static KeyMapping toggleLive;
    private static KeyMapping pearlList;
    private static KeyMapping pearlSend;

    private static KeyMapping make(String id, int key, KeyMapping.Category cat) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(id, InputConstants.Type.KEYSYM, key, cat));
    }

    public static void register() {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("nexxi-client", "main"));

        openMenu = make("key.nexxi-client.open_menu", InputConstants.KEY_EQUAL, category);          // the + / = key
        toggleOverlay = make("key.nexxi-client.toggle_overlay", InputConstants.KEY_O, category);
        toggleLive = make("key.nexxi-client.toggle_live", InputConstants.KEY_F9, category);
        pearlList = make("key.nexxi-client.pearl_list", InputConstants.KEY_LBRACKET, category);     // [
        pearlSend = make("key.nexxi-client.pearl_send", InputConstants.KEY_RBRACKET, category);     // ]

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean ok = Access.allowed();

            // The menu always opens, so people who aren't allowed see the "sorry" message.
            while (openMenu.consumeClick()) {
                client.gui.setScreen(new NexxiScreen(null));
            }

            while (toggleOverlay.consumeClick()) {
                if (!ok) continue;
                Config.get().enabled = !Config.get().enabled;
                Config.save();
                if (client.player != null) {
                    client.player.sendSystemMessage(Component.literal(
                        "§d[Nexxi] §fOverlay " + (Config.get().enabled ? "§aON" : "§cOFF")));
                }
            }

            while (toggleLive.consumeClick()) {
                if (!ok || client.player == null) continue;
                if (Tracker.live) {
                    double d = Tracker.stopLive();
                    client.player.sendSystemMessage(Component.literal("§d[Nexxi] §fLIVE ended. Final: "
                        + (Double.isNaN(d) ? "unknown" : MoneyUtil.formatSigned(d))));
                } else {
                    Tracker.startLive();
                    client.player.sendSystemMessage(Component.literal("§d[Nexxi] §fLIVE started."));
                }
            }

            while (pearlList.consumeClick()) {
                if (!ok) continue;
                client.gui.setScreen(new PearlScreen(null));
            }

            while (pearlSend.consumeClick()) {
                if (!ok) continue;
                Tracker.say(Pearl.send());
            }
        });
    }
}
