package com.nexxi.client;

import java.util.TreeMap;
import java.util.TreeSet;

/** Who may use the client. Pure data + rules, no Minecraft or JSON code, so it is easy to test. */
public class AccessList {
    public static final String DEFAULT_DENY = "Sorry, you have not been able to join.";

    public enum Result { ALLOWED, BANNED, NOT_LISTED }

    /** name -> role. Names are case-insensitive. */
    public final TreeMap<String, String> users = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    public final TreeSet<String> banned = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    public String denyMessage = DEFAULT_DENY;

    public Result check(String name) {
        if (name == null || name.isBlank()) return Result.NOT_LISTED;
        if (banned.contains(name)) return Result.BANNED;
        if (users.containsKey(name)) return Result.ALLOWED;
        return Result.NOT_LISTED;
    }

    public String roleOf(String name) {
        return name == null ? null : users.get(name);
    }

    public boolean hasClient(String name) {
        return check(name) == Result.ALLOWED;
    }

    public void add(String name, String role) {
        banned.remove(name);
        users.put(name, role == null || role.isBlank() ? "member" : role);
    }

    public void remove(String name) {
        users.remove(name);
    }

    public void ban(String name) {
        users.remove(name);
        banned.add(name);
    }

    public void unban(String name) {
        banned.remove(name);
    }
}
