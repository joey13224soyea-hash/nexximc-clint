package com.nexxi.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class NexxiClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Config.load();
        Tracker.loadDonors();
        Access.init();

        // Pearl alerts arrive as hidden whispers; this hides them from chat and shows the alert instead.
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) ->
            overlay || Pearl.allowMessage(message.getString()));

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay && Access.allowed()) Tracker.onChat(message.getString());
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (Access.allowed()) Tracker.tick(client);
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            Tracker.onJoin();
            Access.refresh(); // re-check the list every time you join a server
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> Commands.register(dispatcher));

        Keys.register();
        Hud.register();
    }
}
