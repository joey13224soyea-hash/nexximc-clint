package com.nexxi.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pearl puller: send a hidden whisper to another Nexxi Client user, whose client turns it into an on-screen alert.
 * Works through ordinary /msg, so no extra server is needed.
 */
public class Pearl {
    public static final String TOKEN = "nxcpearl:";
    private static final Pattern MSG = Pattern.compile("nxcpearl:(?<from>\\w{3,16})");

    public static volatile String alertFrom = "";
    public static volatile long alertAt = 0;
    private static long lastSent = 0;

    /** ALLOW_GAME hook: returns false to hide the message from chat. */
    public static boolean allowMessage(String raw) {
        Matcher m = MSG.matcher(raw);
        if (!m.find()) return true;
        if (!Access.allowed()) return true;

        String from = m.group("from");
        if (from.equalsIgnoreCase(Access.meName())) {
            return false; // the echo of my own whisper
        }
        if (Access.hasClient(from)) {
            alertFrom = from;
            alertAt = System.currentTimeMillis();
            Tracker.say("§c[Nexxi] §f" + from + " needs a pearl pull!");
            return false;
        }
        return true; // not one of ours, show it like normal
    }

    /** Online players who have the client (everyone on the access list), excluding you. */
    public static List<String> candidates() {
        List<String> out = new ArrayList<>();
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn == null) return out;
        String me = Access.meName();
        for (PlayerInfo info : conn.getOnlinePlayers()) {
            String n = info.getProfile().name();
            if (n != null && !n.equalsIgnoreCase(me) && Access.hasClient(n)) out.add(n);
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /** Sends the alert to the chosen target. Returns a message to show the user. */
    public static String send() {
        String target = Config.get().pearlTarget;
        if (target == null || target.isBlank()) {
            return "§c[Nexxi] Pick someone first (open the pearl list with the [ key).";
        }
        long now = System.currentTimeMillis();
        if (now - lastSent < 3000) {
            return "§c[Nexxi] Wait a few seconds before sending another alert.";
        }
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn == null) return "§c[Nexxi] You're not connected to a server.";

        conn.sendCommand(Config.get().whisperCommand + " " + target + " " + TOKEN + Access.meName());
        lastSent = now;
        return "§d[Nexxi] §fPearl alert sent to §e" + target + "§f.";
    }
}
