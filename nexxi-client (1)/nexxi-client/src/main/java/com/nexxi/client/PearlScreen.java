package com.nexxi.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Searchable list of online players who have the client. Click one to make them your pearl target. */
public class PearlScreen extends Screen {
    private static final int MAX_ROWS = 7;

    private final Screen parent;
    private EditBox search;
    private final List<Button> rows = new ArrayList<>();
    private List<String> names = new ArrayList<>();
    private String query = "";

    public PearlScreen(Screen parent) {
        super(Component.literal("Pearl Puller"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rows.clear();
        names = Pearl.candidates();
        int cx = this.width / 2;

        search = new EditBox(this.font, cx - 100, 44, 200, 20, Component.literal("Search"));
        search.setResponder(s -> {
            query = s;
            layoutRows();
        });
        this.addRenderableWidget(search);
        this.setInitialFocus(search);

        Map<String, String> roles = Access.knownUsers();
        for (String n : names) {
            String r = roles.getOrDefault(n, "");
            Component label = Component.literal(r.isEmpty() ? n : n + "  [" + r + "]");
            Button b = Button.builder(label, btn -> {
                Config.get().pearlTarget = n;
                Config.save();
                this.onClose();
            }).bounds(cx - 100, 74, 200, 20).build();
            rows.add(b);
            this.addRenderableWidget(b);
        }
        layoutRows();

        this.addRenderableWidget(Button.builder(Component.literal("Send pull alert now"), b -> Tracker.say(Pearl.send()))
            .bounds(cx - 100, this.height - 54, 200, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
            .bounds(cx - 100, this.height - 30, 200, 20).build());
    }

    private void layoutRows() {
        String q = query.toLowerCase();
        int y = 74;
        int shown = 0;
        for (int i = 0; i < rows.size(); i++) {
            Button b = rows.get(i);
            boolean match = names.get(i).toLowerCase().contains(q);
            if (match && shown < MAX_ROWS) {
                b.visible = true;
                b.setY(y);
                y += 22;
                shown++;
            } else {
                b.visible = false;
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int cx = this.width / 2;

        String title = "Pearl Puller";
        graphics.text(this.font, title, cx - this.font.width(title) / 2, 16, 0xFFA78BFA, true);

        String target = Config.get().pearlTarget;
        String t = "Target: " + (target == null || target.isBlank() ? "nobody picked" : target);
        graphics.text(this.font, t, cx - this.font.width(t) / 2, 30, 0xFFFBBF24, true);

        if (names.isEmpty()) {
            String none = "Nobody with the client is online right now.";
            graphics.text(this.font, none, cx - this.font.width(none) / 2, 80, 0xFF9CA3AF, true);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
