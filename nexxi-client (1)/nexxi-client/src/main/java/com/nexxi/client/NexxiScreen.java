package com.nexxi.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** The Nexxi Client screen: your name + 3D player top-left, then all the settings. */
public class NexxiScreen extends Screen {
    private final Screen parent;

    public NexxiScreen(Screen parent) {
        super(Component.literal("Nexxi Client"));
        this.parent = parent;
    }

    private static Component label(String name, boolean on) {
        return Component.literal(name + ": " + (on ? "ON" : "OFF"));
    }

    private Button toggle(int x, int y, String name, Supplier<Boolean> get, Consumer<Boolean> set) {
        return Button.builder(label(name, get.get()), b -> {
            set.accept(!get.get());
            Config.save();
            b.setMessage(label(name, get.get()));
        }).bounds(x, y, 150, 20).build();
    }

    @Override
    protected void init() {
        // Not allowed? Only show a Done button and the "sorry" message.
        if (!Access.allowed()) {
            this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(this.width / 2 - 100, this.height / 2 + 20, 200, 20).build());
            return;
        }

        Config c = Config.get();
        int lx = this.width / 2 - 155;
        int rx = this.width / 2 + 5;
        int y = 40;
        int step = 24;

        // left column: what is shown
        this.addRenderableWidget(toggle(lx, y, "Overlay", () -> c.enabled, v -> c.enabled = v));
        this.addRenderableWidget(toggle(lx, y + step, "Session panel", () -> c.showSession, v -> c.showSession = v));
        this.addRenderableWidget(toggle(lx, y + step * 2, "Stats panel", () -> c.showStats, v -> c.showStats = v));
        this.addRenderableWidget(toggle(lx, y + step * 3, "Donors panel", () -> c.showDonors, v -> c.showDonors = v));
        this.addRenderableWidget(toggle(lx, y + step * 4, "Auction panel", () -> c.showAuction, v -> c.showAuction = v));
        this.addRenderableWidget(toggle(lx, y + step * 5, "Goal bar", () -> c.showGoal, v -> c.showGoal = v));
        this.addRenderableWidget(toggle(lx, y + step * 6, "Donation alerts", () -> c.showAlerts, v -> c.showAlerts = v));

        // right column: actions
        this.addRenderableWidget(Button.builder(liveLabel(), b -> {
            if (Tracker.live) {
                Tracker.stopLive();
            } else {
                Tracker.startLive();
            }
            b.setMessage(liveLabel());
        }).bounds(rx, y, 150, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Reset session"), b -> Tracker.resetSession())
            .bounds(rx, y + step, 150, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Reset stream stats"), b -> Tracker.resetStats())
            .bounds(rx, y + step * 2, 150, 20).build());

        this.addRenderableWidget(Button.builder(donorLabel(), b -> {
            String next = switch (c.donorMode.toLowerCase()) {
                case "all" -> "session";
                case "session" -> "live";
                default -> "all";
            };
            c.donorMode = next;
            Config.save();
            b.setMessage(donorLabel());
        }).bounds(rx, y + step * 3, 150, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("End / clear auction"), b -> {
            if (Tracker.auctionState == 1) {
                Tracker.endAuction();
            } else {
                Tracker.clearAuction();
            }
        }).bounds(rx, y + step * 4, 150, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Clear goal"), b -> {
            Tracker.goalTarget = 0;
            Tracker.goalRaised = 0;
        }).bounds(rx, y + step * 5, 150, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Pearl puller..."),
            b -> Minecraft.getInstance().gui.setScreen(new PearlScreen(this)))
            .bounds(rx, y + step * 6, 150, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
            .bounds(this.width / 2 - 100, this.height - 30, 200, 20).build());
    }

    private static Component liveLabel() {
        return Component.literal(Tracker.live ? "Stop LIVE" : "Go LIVE");
    }

    private static Component donorLabel() {
        return Component.literal("Donors list: " + Config.get().donorMode);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        if (!Access.allowed()) {
            String msg = Access.message();
            graphics.text(this.font, msg, this.width / 2 - this.font.width(msg) / 2, this.height / 2 - 10, 0xFFF87171, true);
            return;
        }

        // top-left badge: 3D player + name + role
        PlayerModel.draw(graphics, 8, 8, 72, 104, 42, mouseX, mouseY);
        String name = Access.meName();
        graphics.text(this.font, name, 8, 108, 0xFFFFFFFF, true);
        String role = Access.role();
        if (!role.isEmpty()) {
            graphics.text(this.font, role, 8, 119, 0xFFA78BFA, true);
        }

        String title = "Nexxi Client";
        graphics.text(this.font, title, this.width / 2 - this.font.width(title) / 2, 16, 0xFFA78BFA, true);
        String hint = "Keybinds: Options > Controls > Key Binds > Nexxi Client";
        graphics.text(this.font, hint, this.width / 2 - this.font.width(hint) / 2, this.height - 46, 0xFF9CA3AF, true);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
