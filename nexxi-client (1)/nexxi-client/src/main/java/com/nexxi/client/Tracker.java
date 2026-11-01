package com.nexxi.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Tracker {
    // ---- balance / session / live ----
    public static double balance = Double.NaN;
    public static double sessionStart = Double.NaN;
    public static long sessionStartMs = System.currentTimeMillis();
    public static boolean live = false;
    public static double liveStart = Double.NaN;
    public static long liveStartMs = 0;

    // ---- donors ----
    public static Map<String, Double> allDonors = new HashMap<>();
    public static final Map<String, Double> sessionDonors = new HashMap<>();
    public static final Map<String, Double> liveDonors = new HashMap<>();
    public static String lastDonorName = "";
    public static double lastDonorAmt = 0;
    public static long lastDonationAt = 0;

    // ---- goal ----
    public static double goalTarget = 0;
    public static double goalRaised = 0;
    public static String goalTitle = "Donation Goal";

    // ---- streamer stats ----
    public static int tpOut = 0;
    public static int tpIn = 0;
    public static final Set<String> tpOutNames = new HashSet<>();
    public static final Set<String> tpInNames = new HashSet<>();
    public static int kills = 0;
    public static int deaths = 0;
    public static int streak = 0;

    // ---- auction ----
    /** 0 = none, 1 = running, 2 = ended */
    public static int auctionState = 0;
    public static String auctionItem = "";
    public static double auctionMin = 0;
    public static double auctionTop = 0;
    public static String auctionTopName = "";
    public static long auctionEndsAt = 0;

    private static int ticks = 0;

    // ------------------------------------------------------------------ events

    public static void onJoin() {
        balance = Double.NaN;
        sessionStart = Double.NaN;
        sessionStartMs = System.currentTimeMillis();
        sessionDonors.clear();
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null) return;
        if (++ticks % 10 == 0) {
            double b = readBalance(mc);
            if (!Double.isNaN(b)) {
                balance = b;
                if (Double.isNaN(sessionStart)) sessionStart = b;
                if (live && Double.isNaN(liveStart)) liveStart = b;
            }
        }
        if (auctionState == 1 && auctionEndsAt > 0 && System.currentTimeMillis() >= auctionEndsAt) {
            endAuction();
        }
    }

    public static void onChat(String raw) {
        String s = MoneyUtil.normalize(raw);
        Config cfg = Config.get();
        if (cfg.debugChat) say("§7[raw] " + raw);

        Matcher m = first(cfg.received(), s);
        if (m != null) {
            String name = g(m, "name");
            double amt = MoneyUtil.parse(g(m, "amt"));
            if (name != null && !Double.isNaN(amt) && amt > 0) addDonation(name, amt);
            return;
        }

        m = first(cfg.tpOut(), s);
        if (m != null) {
            tpOut++;
            String n = g(m, "name");
            if (n != null) tpOutNames.add(n.toLowerCase());
            return;
        }

        m = first(cfg.tpIn(), s);
        if (m != null) {
            tpIn++;
            String n = g(m, "name");
            if (n != null) tpInNames.add(n.toLowerCase());
            return;
        }

        String me = myName();
        m = first(cfg.kills(), s);
        if (m != null) {
            if (me.equalsIgnoreCase(g(m, "killer"))) {
                kills++;
                streak++;
            }
            if (me.equalsIgnoreCase(g(m, "victim"))) {
                deaths++;
                streak = 0;
            }
            return;
        }

        m = first(cfg.deaths(), s);
        if (m != null && me.equalsIgnoreCase(g(m, "victim"))) {
            deaths++;
            streak = 0;
        }
    }

    private static Matcher first(List<Pattern> patterns, String s) {
        for (Pattern p : patterns) {
            Matcher m = p.matcher(s);
            if (m.lookingAt()) return m;
        }
        return null;
    }

    private static String g(Matcher m, String name) {
        try {
            return m.group(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String myName() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? "" : mc.player.getName().getString();
    }

    // ------------------------------------------------------------------ balance

    public static List<String> sidebarLines(Minecraft mc) {
        List<String> out = new ArrayList<>();
        if (mc.level == null) return out;
        Scoreboard sb = mc.level.getScoreboard();
        Objective obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (obj == null) return out;
        out.add(obj.getDisplayName().getString());
        for (PlayerScoreEntry e : sb.listPlayerScores(obj)) {
            PlayerTeam team = sb.getPlayersTeam(e.owner());
            Component line = PlayerTeam.formatNameForTeam(team, e.ownerName());
            out.add(line.getString());
        }
        return out;
    }

    private static double readBalance(Minecraft mc) {
        for (String line : sidebarLines(mc)) {
            Matcher m = Config.get().balancePattern().matcher(MoneyUtil.normalize(line));
            if (m.find()) {
                double v = MoneyUtil.parse(m.group("amt"));
                if (!Double.isNaN(v)) return v;
            }
        }
        return Double.NaN;
    }

    // ------------------------------------------------------------------ live

    public static void startLive() {
        live = true;
        liveStart = balance;
        liveStartMs = System.currentTimeMillis();
        liveDonors.clear();
    }

    /** Returns the final live delta, or NaN if unknown. */
    public static double stopLive() {
        double d = (Double.isNaN(liveStart) || Double.isNaN(balance)) ? Double.NaN : balance - liveStart;
        live = false;
        return d;
    }

    public static void resetSession() {
        sessionStart = balance;
        sessionStartMs = System.currentTimeMillis();
        sessionDonors.clear();
    }

    public static void resetStats() {
        tpOut = 0;
        tpIn = 0;
        tpOutNames.clear();
        tpInNames.clear();
        kills = 0;
        deaths = 0;
        streak = 0;
    }

    /** Profit/loss per hour for the live run if live, otherwise the session. NaN if not enough data. */
    public static double perHour() {
        double start = live ? liveStart : sessionStart;
        long startMs = live ? liveStartMs : sessionStartMs;
        if (Double.isNaN(balance) || Double.isNaN(start)) return Double.NaN;
        double hours = (System.currentTimeMillis() - startMs) / 3_600_000.0;
        if (hours < 1.0 / 60.0) return Double.NaN;
        return (balance - start) / hours;
    }

    // ------------------------------------------------------------------ donors / goal

    public static void addDonation(String name, double amt) {
        allDonors.merge(name, amt, Double::sum);
        sessionDonors.merge(name, amt, Double::sum);
        if (live) liveDonors.merge(name, amt, Double::sum);
        saveDonors();

        lastDonorName = name;
        lastDonorAmt = amt;
        lastDonationAt = System.currentTimeMillis();
        if (goalTarget > 0) goalRaised += amt;

        if (auctionState == 1 && amt >= auctionMin && amt > auctionTop) {
            auctionTop = amt;
            auctionTopName = name;
            say("§d[Nexxi] §fNew top bid: §a" + MoneyUtil.format(amt) + " §ffrom §e" + name);
        }
    }

    public static List<Map.Entry<String, Double>> top(int n) {
        Map<String, Double> src = switch (Config.get().donorMode.toLowerCase()) {
            case "session" -> sessionDonors;
            case "live" -> liveDonors;
            default -> allDonors;
        };
        List<Map.Entry<String, Double>> list = new ArrayList<>(src.entrySet());
        list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return list.size() > n ? list.subList(0, n) : list;
    }

    private static Path donorsPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("nexxi-client-donors.json");
    }

    public static void loadDonors() {
        Path p = donorsPath();
        if (!Files.exists(p)) return;
        try (Reader r = Files.newBufferedReader(p)) {
            Map<String, Double> m = new Gson().fromJson(r, new TypeToken<Map<String, Double>>() {}.getType());
            if (m != null) allDonors = new HashMap<>(m);
        } catch (Exception e) {
            System.err.println("[Nexxi] Could not read donors file: " + e);
        }
    }

    public static void saveDonors() {
        try (Writer w = Files.newBufferedWriter(donorsPath())) {
            new Gson().toJson(allDonors, w);
        } catch (Exception e) {
            System.err.println("[Nexxi] Could not save donors file: " + e);
        }
    }

    // ------------------------------------------------------------------ auction

    public static void startAuction(String item, double min) {
        auctionState = 1;
        auctionItem = item;
        auctionMin = min;
        auctionTop = 0;
        auctionTopName = "";
        auctionEndsAt = 0;
    }

    public static void manualBid(String name, double amt) {
        if (auctionState != 1) return;
        if (amt >= auctionMin && amt > auctionTop) {
            auctionTop = amt;
            auctionTopName = name;
        }
    }

    public static void endAuction() {
        if (auctionState != 1) return;
        auctionState = 2;
        auctionEndsAt = 0;
        if (auctionTop > 0) {
            say("§d[Nexxi] §fAuction over! §e" + auctionTopName + " §fwon §6" + auctionItem + " §ffor §a" + MoneyUtil.format(auctionTop));
        } else {
            say("§d[Nexxi] §fAuction over with no bids.");
        }
    }

    public static void clearAuction() {
        auctionState = 0;
        auctionEndsAt = 0;
    }

    // ------------------------------------------------------------------ util

    public static void say(String msg) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal(msg));
        });
    }
}
