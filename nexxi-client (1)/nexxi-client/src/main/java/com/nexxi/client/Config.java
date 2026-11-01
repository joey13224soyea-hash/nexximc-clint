package com.nexxi.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance = new Config();

    private static final String AMT = "[\\d,]*\\.?\\d+\\s*[KMBT]?(?![A-Za-z])";

    // ---- on/off ----
    public boolean enabled = true;
    public boolean showSession = true;
    public boolean showStats = true;
    public boolean showDonors = true;
    public boolean showAuction = true;
    public boolean showGoal = true;
    public boolean showAlerts = true;

    // ---- branding / layout ----
    public String brand = "Nexxi Client";
    public int sessionX = 8, sessionY = 8;
    public int statsX = 8, statsY = 76;
    public int goalX = 8, goalY = 170;
    public int donorsX = 180, donorsY = 8;
    public int auctionX = 180, auctionY = 100;

    // ---- pearl puller ----
    /** Who the pearl-pull alert goes to. */
    public String pearlTarget = "";
    /** Command used to whisper. Change to "/w" or "/tell" if the server uses another one. */
    public String whisperCommand = "msg";

    // ---- owner only: a GitHub token that can edit your access Gist ----
    public String gistToken = "";

    // ---- donors panel ----
    /** "all" (saved between sessions), "session" (since you joined) or "live" (since going live) */
    public String donorMode = "all";
    public int topCount = 5;

    // ---- parsing: tune these to the server's real wording (use /nexxi debug) ----
    public boolean debugChat = false;

    /** Needs named group "amt". Run against each sidebar line. */
    public String balanceRegex = "(?i)(?:money|balance)\\D{0,8}(?<amt>" + AMT + ")";

    /** Need named groups "name" and "amt". Matched from the START of a message (anti-spoof). */
    public List<String> receivedPatterns = new ArrayList<>(List.of(
        "(?i)\\W*(?<name>\\w{3,16})\\s+(?:has\\s+)?(?:paid|sent)\\s+you\\s+\\$?(?<amt>" + AMT + ")",
        "(?i)\\W*you\\s+(?:have\\s+)?received\\s+\\$?(?<amt>" + AMT + ")\\s+from\\s+(?<name>\\w{3,16})"
    ));

    /** You teleported to somebody. Optional named group "name". */
    public List<String> tpOutPatterns = new ArrayList<>(List.of(
        "(?i)\\W*teleporting\\s+to\\s+(?<name>\\w{3,16})",
        "(?i)\\W*you\\s+(?:have\\s+)?teleported\\s+to\\s+(?<name>\\w{3,16})",
        "(?i)\\W*(?<name>\\w{3,16})\\s+accepted\\s+your\\s+teleport\\s+request"
    ));

    /** Somebody teleported to you. Optional named group "name". */
    public List<String> tpInPatterns = new ArrayList<>(List.of(
        "(?i)\\W*(?<name>\\w{3,16})\\s+(?:has\\s+)?teleported\\s+to\\s+you",
        "(?i)\\W*you\\s+accepted\\s+(?<name>\\w{3,16})(?:'s)?\\s+teleport\\s+request"
    ));

    /** Need named groups "victim" and "killer". */
    public List<String> killPatterns = new ArrayList<>(List.of(
        "\\W*(?<victim>\\w{3,16})\\s+was\\s+[a-z ]{3,25}?\\s+by\\s+(?<killer>\\w{3,16})"
    ));

    /** Need named group "victim" (deaths with no killer: fall, lava, ...). */
    public List<String> deathPatterns = new ArrayList<>(List.of(
        "\\W*(?<victim>\\w{3,16})\\s+(?:died|fell|drowned|burned|blew\\s+up|hit\\s+the\\s+ground|tried\\s+to|walked\\s+into|suffocated|starved|withered|went\\s+up\\s+in|experienced|discovered|froze|was\\s+(?:squished|pricked|stung|killed))\\b.*"
    ));

    private transient Map<String, List<Pattern>> cache = new HashMap<>();
    private transient Pattern balancePattern;

    public static Config get() {
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("nexxi-client.json");
    }

    public static void load() {
        Path p = path();
        try {
            if (Files.exists(p)) {
                try (Reader r = Files.newBufferedReader(p)) {
                    Config c = GSON.fromJson(r, Config.class);
                    if (c != null) instance = c;
                }
            }
        } catch (Exception e) {
            System.err.println("[Nexxi] Could not read config, using defaults: " + e);
        }
        save();
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(path())) {
            GSON.toJson(instance, w);
        } catch (Exception e) {
            System.err.println("[Nexxi] Could not save config: " + e);
        }
    }

    private List<Pattern> compile(String key, List<String> src) {
        if (cache == null) cache = new HashMap<>();
        return cache.computeIfAbsent(key, k -> {
            List<Pattern> out = new ArrayList<>();
            if (src != null) {
                for (String s : src) {
                    try {
                        out.add(Pattern.compile(s));
                    } catch (PatternSyntaxException e) {
                        System.err.println("[Nexxi] Bad pattern in config: " + s);
                    }
                }
            }
            return out;
        });
    }

    public List<Pattern> received() { return compile("received", receivedPatterns); }
    public List<Pattern> tpOut() { return compile("tpOut", tpOutPatterns); }
    public List<Pattern> tpIn() { return compile("tpIn", tpInPatterns); }
    public List<Pattern> kills() { return compile("kills", killPatterns); }
    public List<Pattern> deaths() { return compile("deaths", deathPatterns); }

    public Pattern balancePattern() {
        if (balancePattern == null) {
            try {
                balancePattern = Pattern.compile(balanceRegex);
            } catch (PatternSyntaxException e) {
                balancePattern = Pattern.compile("(?i)money\\D{0,8}(?<amt>" + AMT + ")");
            }
        }
        return balancePattern;
    }
}
