package com.nexxi.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Decides whether this player may use the client, using an access list the owner keeps in a GitHub Gist.
 *
 * NOTE: this is a client-side check. It stops casual use, but anyone who edits the mod can remove it.
 */
public class Access {
    public enum State { LOADING, ALLOWED, DENIED, ERROR }

    private static volatile State state = State.LOADING;
    private static volatile String role = "";
    private static volatile String message = AccessList.DEFAULT_DENY;
    private static final AccessList list = new AccessList();
    private static String owner = "";
    private static String gistId = "";

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(6))
        .build();

    // ------------------------------------------------------------------ setup

    public static void init() {
        try (InputStream in = Access.class.getResourceAsStream("/nexxi-access.properties")) {
            if (in != null) {
                Properties p = new Properties();
                p.load(in);
                owner = p.getProperty("owner", "").trim();
                gistId = p.getProperty("gistId", "").trim();
            }
        } catch (IOException e) {
            System.err.println("[Nexxi] Could not read nexxi-access.properties: " + e);
        }
        refresh();
    }

    private static Path cachePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("nexxi-client-access-cache.json");
    }

    // ------------------------------------------------------------------ state

    public static boolean allowed() { return state == State.ALLOWED; }
    public static State state() { return state; }
    public static String role() { return role; }
    public static String message() { return message; }
    public static boolean isOwner() { return allowed() && "owner".equalsIgnoreCase(role); }

    public static String meName() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getUser() == null ? "" : mc.getUser().getName();
    }

    private static boolean meIsOwner() {
        return !owner.isBlank() && owner.equalsIgnoreCase(meName());
    }

    // ------------------------------------------------------------------ checking

    /** Re-reads the access list (in the background) and re-checks this player. */
    public static void refresh() {
        if (meIsOwner()) {
            role = "owner";
            state = State.ALLOWED;
        }
        if (gistId.isBlank()) {
            evaluate();
            return;
        }

        CompletableFuture.runAsync(() -> {
            String content = null;
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.github.com/gists/" + gistId))
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "nexxi-client")
                    .GET()
                    .build();
                HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() / 100 != 2) throw new IOException("HTTP " + res.statusCode());
                JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
                content = root.getAsJsonObject("files").getAsJsonObject("access.json").get("content").getAsString();
                Files.writeString(cachePath(), content);
            } catch (Exception e) {
                try {
                    if (Files.exists(cachePath())) content = Files.readString(cachePath());
                } catch (Exception ignored) {
                    // no usable cache either
                }
            }

            if (content == null) {
                if (!meIsOwner()) {
                    role = "";
                    message = "Couldn't verify access (no connection).";
                    state = State.ERROR;
                    announce();
                }
                return;
            }

            try {
                synchronized (list) {
                    AccessJson.load(list, content);
                }
            } catch (Exception e) {
                if (!meIsOwner()) {
                    role = "";
                    message = "The access list is broken. Tell the owner.";
                    state = State.ERROR;
                    announce();
                }
                return;
            }
            evaluate();
        });
    }

    private static void evaluate() {
        String me = meName();
        if (meIsOwner()) {
            role = "owner";
            state = State.ALLOWED;
            return;
        }
        if (gistId.isBlank()) {
            // Access control is off: everyone is a member.
            role = "member";
            state = State.ALLOWED;
            return;
        }

        AccessList.Result r;
        String r2;
        String deny;
        synchronized (list) {
            r = list.check(me);
            r2 = list.roleOf(me);
            deny = list.denyMessage;
        }

        if (r == AccessList.Result.ALLOWED) {
            role = r2 == null ? "member" : r2;
            state = State.ALLOWED;
        } else {
            role = "";
            message = deny;
            state = State.DENIED;
            announce();
        }
    }

    private static void announce() {
        Tracker.say("§c[Nexxi] §f" + message);
    }

    // ------------------------------------------------------------------ who has the client

    /** True if this name is the owner or is on the access list. */
    public static boolean hasClient(String name) {
        if (name == null) return false;
        if (!owner.isBlank() && owner.equalsIgnoreCase(name)) return true;
        synchronized (list) {
            return list.hasClient(name);
        }
    }

    /** name -> role for everyone known to have the client (including the owner). */
    public static Map<String, String> knownUsers() {
        TreeMap<String, String> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        synchronized (list) {
            out.putAll(list.users);
        }
        if (!owner.isBlank()) out.put(owner, "owner");
        return out;
    }

    public static List<String> describe() {
        List<String> out = new ArrayList<>();
        out.add("§d--- Access list ---");
        synchronized (list) {
            out.add("§7Owner: §f" + owner);
            if (list.users.isEmpty()) out.add("§7No users added yet.");
            list.users.forEach((n, r) -> out.add("§a" + n + " §7[" + r + "]"));
            if (!list.banned.isEmpty()) out.add("§cBanned: §f" + String.join(", ", list.banned));
        }
        return out;
    }

    // ------------------------------------------------------------------ owner admin

    public static void add(String name, String roleName, Consumer<String> done) {
        synchronized (list) {
            list.add(name, roleName);
        }
        publish(done);
    }

    public static void remove(String name, Consumer<String> done) {
        synchronized (list) {
            list.remove(name);
        }
        publish(done);
    }

    public static void ban(String name, Consumer<String> done) {
        synchronized (list) {
            list.ban(name);
        }
        publish(done);
    }

    public static void unban(String name, Consumer<String> done) {
        synchronized (list) {
            list.unban(name);
        }
        publish(done);
    }

    /** Pushes the current list to your Gist. Needs "gistToken" in config/nexxi-client.json. */
    private static void publish(Consumer<String> done) {
        String token = Config.get().gistToken;
        if (gistId.isBlank()) {
            done.accept("§c[Nexxi] No gistId is set in the mod, so there is nothing to publish to.");
            return;
        }
        if (token == null || token.isBlank()) {
            done.accept("§c[Nexxi] Put your GitHub token in \"gistToken\" in config/nexxi-client.json first.");
            return;
        }

        String content;
        synchronized (list) {
            content = AccessJson.toJson(list);
        }
        JsonObject file = new JsonObject();
        file.addProperty("content", content);
        JsonObject files = new JsonObject();
        files.add("access.json", file);
        JsonObject body = new JsonObject();
        body.add("files", files);

        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.github.com/gists/" + gistId))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + token)
                    .header("User-Agent", "nexxi-client")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
                HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() / 100 == 2) {
                    done.accept("§a[Nexxi] Access list updated.");
                } else {
                    done.accept("§c[Nexxi] GitHub answered HTTP " + res.statusCode() + " (check your token and gistId).");
                }
            } catch (Exception e) {
                done.accept("§c[Nexxi] Couldn't update the list: " + e.getMessage());
            }
        });
    }
}
