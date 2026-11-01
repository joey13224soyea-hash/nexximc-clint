package com.nexxi.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class Commands {
    private static int fb(CommandContext<FabricClientCommandSource> c, String msg) {
        c.getSource().sendFeedback(Component.literal("§d[Nexxi] §f" + msg));
        return 1;
    }

    private static int help(CommandContext<FabricClientCommandSource> c) {
        String[] lines = {
            "§d--- Nexxi Client ---",
            "§7Most things are in the §fNexxi Client§7 button on the pause/title menu (or press Right Shift).",
            "§e/nexxi live §7| §elive stop §7| §esession reset",
            "§e/nexxi auction start <min> <item...> §7| §etimer <sec> §7| §ebid <name> <amt> §7| §eend §7| §eclear",
            "§e/nexxi goal <amount> <title...> §7| §egoal clear",
            "§e/nexxi tp out §7| §etp in §7(add one by hand) | §e/nexxi stats reset",
            "§e/nexxi donors <all|session|live|clear>",
            "§e/nexxi toggle <overlay|session|stats|donors|auction|goal|alerts>",
            "§e/nexxi brand <text> §7| §e/nexxi pos <session|stats|goal|donors|auction> <x> <y>",
            "§e/nexxi pearl target <name> §7| §epearl send §7(or press [ to search, ] to alert)",
            "§e/nexxi admin add <name> [role] §7| §eremove <name> §7| §eban <name> §7| §eunban <name> §7| §elist §7(owner only)",
            "§e/nexxi debug §7(echo raw chat) | §e/nexxi scoreboard §7(print sidebar lines)",
            "§7Amounts accept 5k, 2.5m, 1b etc."
        };
        for (String s : lines) c.getSource().sendFeedback(Component.literal(s));
        return 1;
    }

    private static int toggle(CommandContext<FabricClientCommandSource> c, String which) {
        Config cfg = Config.get();
        switch (which) {
            case "overlay" -> cfg.enabled = !cfg.enabled;
            case "session" -> cfg.showSession = !cfg.showSession;
            case "stats" -> cfg.showStats = !cfg.showStats;
            case "donors" -> cfg.showDonors = !cfg.showDonors;
            case "goal" -> cfg.showGoal = !cfg.showGoal;
            case "alerts" -> cfg.showAlerts = !cfg.showAlerts;
            default -> cfg.showAuction = !cfg.showAuction;
        }
        Config.save();
        return fb(c, "Toggled " + which + ".");
    }

    private static int setMode(CommandContext<FabricClientCommandSource> c, String mode) {
        Config.get().donorMode = mode;
        Config.save();
        return fb(c, "Top donors now showing: " + mode);
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> d) {
        d.register(literal("nexxi")
            .executes(Commands::help)
            .then(literal("help").executes(Commands::help))

            .then(literal("live")
                .executes(c -> {
                    Tracker.startLive();
                    return fb(c, "LIVE tracking started. Live profit/loss and live donors reset.");
                })
                .then(literal("stop").executes(c -> {
                    double dlt = Tracker.stopLive();
                    return fb(c, "LIVE ended. Final: " + (Double.isNaN(dlt) ? "unknown" : MoneyUtil.formatSigned(dlt)));
                })))

            .then(literal("session").then(literal("reset").executes(c -> {
                Tracker.resetSession();
                return fb(c, "Session counter reset.");
            })))

            .then(literal("stats").then(literal("reset").executes(c -> {
                Tracker.resetStats();
                return fb(c, "Stream stats reset.");
            })))

            .then(literal("tp")
                .then(literal("out").executes(c -> {
                    Tracker.tpOut++;
                    return fb(c, "Added a TP to someone.");
                }))
                .then(literal("in").executes(c -> {
                    Tracker.tpIn++;
                    return fb(c, "Added a TP to you.");
                })))

            .then(literal("goal")
                .then(literal("clear").executes(c -> {
                    Tracker.goalTarget = 0;
                    Tracker.goalRaised = 0;
                    return fb(c, "Goal cleared.");
                }))
                .then(argument("amount", StringArgumentType.word())
                    .then(argument("title", StringArgumentType.greedyString()).executes(c -> {
                        double amt = MoneyUtil.parse(StringArgumentType.getString(c, "amount"));
                        if (Double.isNaN(amt) || amt <= 0) return fb(c, "§cCouldn't read that amount.");
                        Tracker.goalTarget = amt;
                        Tracker.goalRaised = 0;
                        Tracker.goalTitle = StringArgumentType.getString(c, "title");
                        return fb(c, "Goal set: " + MoneyUtil.format(amt));
                    }))))

            .then(literal("auction")
                .then(literal("start").then(argument("min", StringArgumentType.word())
                    .then(argument("item", StringArgumentType.greedyString()).executes(c -> {
                        double min = MoneyUtil.parse(StringArgumentType.getString(c, "min"));
                        if (Double.isNaN(min)) return fb(c, "§cCouldn't read that amount.");
                        Tracker.startAuction(StringArgumentType.getString(c, "item"), min);
                        return fb(c, "Auction started. Min to buy: " + MoneyUtil.format(min));
                    }))))
                .then(literal("timer").then(argument("seconds", IntegerArgumentType.integer(1)).executes(c -> {
                    if (Tracker.auctionState != 1) return fb(c, "§cNo running auction.");
                    int s = IntegerArgumentType.getInteger(c, "seconds");
                    Tracker.auctionEndsAt = System.currentTimeMillis() + s * 1000L;
                    return fb(c, "Auction ends in " + s + "s.");
                })))
                .then(literal("bid").then(argument("name", StringArgumentType.word())
                    .then(argument("amount", StringArgumentType.word()).executes(c -> {
                        double amt = MoneyUtil.parse(StringArgumentType.getString(c, "amount"));
                        if (Double.isNaN(amt)) return fb(c, "§cCouldn't read that amount.");
                        Tracker.manualBid(StringArgumentType.getString(c, "name"), amt);
                        return fb(c, "Bid recorded (if it beat the minimum and the top bid).");
                    }))))
                .then(literal("end").executes(c -> {
                    Tracker.endAuction();
                    return 1;
                }))
                .then(literal("clear").executes(c -> {
                    Tracker.clearAuction();
                    return fb(c, "Auction cleared.");
                })))

            .then(literal("donors")
                .then(literal("all").executes(c -> setMode(c, "all")))
                .then(literal("session").executes(c -> setMode(c, "session")))
                .then(literal("live").executes(c -> setMode(c, "live")))
                .then(literal("clear").executes(c -> {
                    Tracker.allDonors.clear();
                    Tracker.sessionDonors.clear();
                    Tracker.liveDonors.clear();
                    Tracker.saveDonors();
                    return fb(c, "All donor totals cleared.");
                })))

            .then(literal("toggle")
                .then(literal("overlay").executes(c -> toggle(c, "overlay")))
                .then(literal("session").executes(c -> toggle(c, "session")))
                .then(literal("stats").executes(c -> toggle(c, "stats")))
                .then(literal("donors").executes(c -> toggle(c, "donors")))
                .then(literal("auction").executes(c -> toggle(c, "auction")))
                .then(literal("goal").executes(c -> toggle(c, "goal")))
                .then(literal("alerts").executes(c -> toggle(c, "alerts"))))

            .then(literal("brand").then(argument("text", StringArgumentType.greedyString()).executes(c -> {
                Config.get().brand = StringArgumentType.getString(c, "text");
                Config.save();
                return fb(c, "Footer and menu button set to: " + Config.get().brand);
            })))

            .then(literal("pos").then(argument("panel", StringArgumentType.word())
                .then(argument("x", IntegerArgumentType.integer(0))
                    .then(argument("y", IntegerArgumentType.integer(0)).executes(c -> {
                        Config cfg = Config.get();
                        String p = StringArgumentType.getString(c, "panel");
                        int x = IntegerArgumentType.getInteger(c, "x");
                        int y = IntegerArgumentType.getInteger(c, "y");
                        switch (p) {
                            case "session" -> { cfg.sessionX = x; cfg.sessionY = y; }
                            case "stats" -> { cfg.statsX = x; cfg.statsY = y; }
                            case "goal" -> { cfg.goalX = x; cfg.goalY = y; }
                            case "donors" -> { cfg.donorsX = x; cfg.donorsY = y; }
                            case "auction" -> { cfg.auctionX = x; cfg.auctionY = y; }
                            default -> { return fb(c, "§cPanel must be session, stats, goal, donors or auction."); }
                        }
                        Config.save();
                        return fb(c, "Moved " + p + " panel.");
                    })))))

            .then(literal("pearl")
                .then(literal("target").then(argument("name", StringArgumentType.word()).executes(c -> {
                    Config.get().pearlTarget = StringArgumentType.getString(c, "name");
                    Config.save();
                    return fb(c, "Pearl target: " + Config.get().pearlTarget);
                })))
                .then(literal("send").executes(c -> {
                    Tracker.say(Pearl.send());
                    return 1;
                })))

            .then(literal("admin")
                .requires(src -> Access.isOwner())
                .then(literal("list").executes(c -> {
                    for (String s : Access.describe()) c.getSource().sendFeedback(Component.literal(s));
                    return 1;
                }))
                .then(literal("refresh").executes(c -> {
                    Access.refresh();
                    return fb(c, "Re-reading the access list...");
                }))
                .then(literal("add").then(argument("name", StringArgumentType.word())
                    .executes(c -> {
                        Access.add(StringArgumentType.getString(c, "name"), "member", Tracker::say);
                        return 1;
                    })
                    .then(argument("role", StringArgumentType.word()).executes(c -> {
                        Access.add(StringArgumentType.getString(c, "name"), StringArgumentType.getString(c, "role"), Tracker::say);
                        return 1;
                    }))))
                .then(literal("remove").then(argument("name", StringArgumentType.word()).executes(c -> {
                    Access.remove(StringArgumentType.getString(c, "name"), Tracker::say);
                    return 1;
                })))
                .then(literal("ban").then(argument("name", StringArgumentType.word()).executes(c -> {
                    Access.ban(StringArgumentType.getString(c, "name"), Tracker::say);
                    return 1;
                })))
                .then(literal("unban").then(argument("name", StringArgumentType.word()).executes(c -> {
                    Access.unban(StringArgumentType.getString(c, "name"), Tracker::say);
                    return 1;
                }))))

            .then(literal("debug").executes(c -> {
                Config.get().debugChat = !Config.get().debugChat;
                Config.save();
                return fb(c, "Raw chat echo: " + (Config.get().debugChat ? "ON" : "OFF"));
            }))

            .then(literal("scoreboard").executes(c -> {
                var lines = Tracker.sidebarLines(Minecraft.getInstance());
                if (lines.isEmpty()) return fb(c, "No sidebar scoreboard found.");
                for (String s : lines) c.getSource().sendFeedback(Component.literal("§7[sidebar] §f" + s));
                return 1;
            }))
        );
    }
}
