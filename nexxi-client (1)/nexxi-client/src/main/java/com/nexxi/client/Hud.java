package com.nexxi.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Hud {
    private static final int BG = 0xB0101018;
    private static final int ACCENT = 0xFFA78BFA;
    private static final int GREEN = 0xFF4ADE80;
    private static final int RED = 0xFFF87171;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFF9CA3AF;
    private static final int GOLD = 0xFFFBBF24;

    private record Line(String left, String right, int color) {
        Line(String left, int color) {
            this(left, null, color);
        }
    }

    public static void register() {
        HudElementRegistry.attachElementBefore(
            VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("nexxi-client", "overlay"),
            Hud::render
        );
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Config c = Config.get();
        if (!c.enabled || mc.player == null || !Access.allowed()) return;
        Font font = mc.font;
        pearlAlert(g, mc, font);

        if (c.showSession) panel(g, font, c.sessionX, c.sessionY, "SESSION", sessionLines(), -1);
        if (c.showStats) panel(g, font, c.statsX, c.statsY, "STREAM STATS", statLines(), -1);
        if (c.showGoal && Tracker.goalTarget > 0) {
            double frac = Math.min(1.0, Tracker.goalRaised / Tracker.goalTarget);
            panel(g, font, c.goalX, c.goalY, "GOAL", goalLines(), frac);
        }
        if (c.showDonors) panel(g, font, c.donorsX, c.donorsY, "TOP DONORS (" + c.donorMode.toUpperCase() + ")", donorLines(), -1);
        if (c.showAuction) panel(g, font, c.auctionX, c.auctionY, "AUCTION", auctionLines(), -1);
        if (c.showAlerts) alert(g, mc, font);
    }

    // ------------------------------------------------------------------ panel contents

    private static List<Line> sessionLines() {
        List<Line> l = new ArrayList<>();
        double bal = Tracker.balance;
        l.add(new Line("Balance", Double.isNaN(bal) ? "..." : MoneyUtil.format(bal), WHITE));

        if (!Double.isNaN(bal) && !Double.isNaN(Tracker.sessionStart)) {
            double d = bal - Tracker.sessionStart;
            l.add(new Line("Session (" + duration(Tracker.sessionStartMs) + ")", MoneyUtil.formatSigned(d), color(d)));
        } else {
            l.add(new Line("Waiting for balance...", GRAY));
        }

        if (Tracker.live) {
            if (!Double.isNaN(bal) && !Double.isNaN(Tracker.liveStart)) {
                double d = bal - Tracker.liveStart;
                l.add(new Line("LIVE (" + duration(Tracker.liveStartMs) + ")", MoneyUtil.formatSigned(d), color(d)));
            } else {
                l.add(new Line("LIVE (waiting...)", GRAY));
            }
        }
        return l;
    }

    private static List<Line> statLines() {
        List<Line> l = new ArrayList<>();
        l.add(new Line("TP'd to", Tracker.tpOut + " (" + Tracker.tpOutNames.size() + " unique)", WHITE));
        l.add(new Line("TP'd to you", Tracker.tpIn + " (" + Tracker.tpInNames.size() + " unique)", WHITE));
        l.add(new Line("Kills / Deaths", Tracker.kills + " / " + Tracker.deaths, WHITE));
        double kd = Tracker.deaths == 0 ? Tracker.kills : (double) Tracker.kills / Tracker.deaths;
        l.add(new Line("K/D", String.format("%.2f", kd) + "  streak " + Tracker.streak, kd >= 1 ? GREEN : RED));
        double ph = Tracker.perHour();
        if (Double.isNaN(ph)) {
            l.add(new Line("Per hour", "...", GRAY));
        } else {
            l.add(new Line("Per hour", MoneyUtil.formatSigned(ph), color(ph)));
        }
        return l;
    }

    private static List<Line> goalLines() {
        List<Line> l = new ArrayList<>();
        l.add(new Line(Tracker.goalTitle, GOLD));
        int pct = (int) Math.min(100, Math.round(100.0 * Tracker.goalRaised / Tracker.goalTarget));
        l.add(new Line(MoneyUtil.format(Tracker.goalRaised) + " / " + MoneyUtil.format(Tracker.goalTarget), pct + "%", WHITE));
        return l;
    }

    private static List<Line> donorLines() {
        List<Line> l = new ArrayList<>();
        List<Map.Entry<String, Double>> top = Tracker.top(Config.get().topCount);
        if (top.isEmpty()) {
            l.add(new Line("No donations yet", GRAY));
            return l;
        }
        int i = 1;
        for (Map.Entry<String, Double> e : top) {
            l.add(new Line(i + ". " + e.getKey(), MoneyUtil.format(e.getValue()), i == 1 ? GOLD : WHITE));
            i++;
        }
        return l;
    }

    private static List<Line> auctionLines() {
        List<Line> l = new ArrayList<>();
        switch (Tracker.auctionState) {
            case 1 -> {
                l.add(new Line(Tracker.auctionItem, GOLD));
                l.add(new Line("Min to buy", MoneyUtil.format(Tracker.auctionMin), WHITE));
                if (Tracker.auctionTop > 0) {
                    l.add(new Line("Top: " + Tracker.auctionTopName, MoneyUtil.format(Tracker.auctionTop), GREEN));
                } else {
                    l.add(new Line("No bids yet", GRAY));
                }
                if (Tracker.auctionEndsAt > 0) {
                    long left = Math.max(0, (Tracker.auctionEndsAt - System.currentTimeMillis()) / 1000);
                    l.add(new Line("Ends in", String.format("%d:%02d", left / 60, left % 60), RED));
                }
            }
            case 2 -> {
                l.add(new Line(Tracker.auctionItem, GOLD));
                if (Tracker.auctionTop > 0) {
                    l.add(new Line("SOLD to " + Tracker.auctionTopName, MoneyUtil.format(Tracker.auctionTop), GREEN));
                } else {
                    l.add(new Line("Ended - no bids", GRAY));
                }
            }
            default -> l.add(new Line("No auction running", GRAY));
        }
        return l;
    }

    // ------------------------------------------------------------------ drawing

    /** barFrac < 0 means no progress bar. */
    private static void panel(GuiGraphicsExtractor g, Font font, int x, int y, String title, List<Line> lines, double barFrac) {
        String brand = Config.get().brand;

        int w = Math.max(font.width(title), font.width(brand));
        for (Line ln : lines) {
            int lw = font.width(ln.left) + (ln.right == null ? 0 : 12 + font.width(ln.right));
            w = Math.max(w, lw);
        }
        w = Math.max(w + 14, 140);
        int barH = barFrac >= 0 ? 10 : 0;
        int h = 5 + 11 + lines.size() * 10 + barH + 4 + 12;

        g.fill(x, y, x + w, y + h, BG);
        g.fill(x, y, x + 2, y + h, ACCENT);

        int ty = y + 5;
        g.text(font, title, x + 8, ty, ACCENT, true);
        ty += 11;

        for (Line ln : lines) {
            g.text(font, ln.left, x + 8, ty, ln.right == null ? ln.color : WHITE, true);
            if (ln.right != null) {
                g.text(font, ln.right, x + w - 6 - font.width(ln.right), ty, ln.color, true);
            }
            ty += 10;
        }

        if (barFrac >= 0) {
            int bx1 = x + 8, bx2 = x + w - 6;
            g.fill(bx1, ty + 1, bx2, ty + 7, 0x80000000);
            int filled = (int) Math.round((bx2 - bx1) * barFrac);
            if (filled > 0) g.fill(bx1, ty + 1, bx1 + filled, ty + 7, GREEN);
            ty += 10;
        }

        g.fill(x + 8, ty + 1, x + w - 6, ty + 2, 0x30FFFFFF);
        int bw = font.width(brand);
        g.text(font, brand, x + (w - bw) / 2, ty + 4, ACCENT, true);
    }

    private static void alert(GuiGraphicsExtractor g, Minecraft mc, Font font) {
        if (Tracker.lastDonationAt == 0) return;
        long age = System.currentTimeMillis() - Tracker.lastDonationAt;
        if (age > 6000) return;

        String text = Tracker.lastDonorName + " donated " + MoneyUtil.format(Tracker.lastDonorAmt) + "!";
        int w = font.width(text) + 28;
        int x = (mc.getWindow().getGuiScaledWidth() - w) / 2;
        int y = 14;
        g.fill(x, y, x + w, y + 24, BG);
        g.fill(x, y, x + w, y + 2, GOLD);
        g.text(font, text, x + 14, y + 9, GOLD, true);
    }

    /** Big red banner for 8 seconds when someone asks you for a pearl pull. */
    private static void pearlAlert(GuiGraphicsExtractor g, Minecraft mc, Font font) {
        if (Pearl.alertAt == 0) return;
        long age = System.currentTimeMillis() - Pearl.alertAt;
        if (age > 8000) return;

        String text = Pearl.alertFrom + " needs a PEARL PULL!";
        int w = font.width(text) + 40;
        int x = (mc.getWindow().getGuiScaledWidth() - w) / 2;
        int y = 46;
        boolean flash = (age / 400) % 2 == 0;
        g.fill(x, y, x + w, y + 28, BG);
        g.fill(x, y, x + w, y + 3, flash ? RED : GOLD);
        g.text(font, text, x + 20, y + 11, flash ? RED : WHITE, true);
    }

    private static int color(double d) {
        if (d > 0) return GREEN;
        if (d < 0) return RED;
        return GRAY;
    }

    private static String duration(long startMs) {
        long s = Math.max(0, (System.currentTimeMillis() - startMs) / 1000);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + sec + "s";
        return sec + "s";
    }
}
