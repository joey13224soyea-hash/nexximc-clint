# Nexxi Client

A client-side Fabric mod for **Minecraft 26.3** with streaming overlays, an owner-controlled access list, and a pearl puller.

## Getting the .jar (the file you put in `mods`)

The zip is only the container for the source code. The mod itself is a `.jar`, and it has to be built once:

1. Make a free GitHub repo and upload everything in this folder.
2. Open the **Actions** tab, run **build**, and download **nexxi-client-jar**.
3. Put the `.jar` in your `mods` folder with **Fabric Loader** and **Fabric API** for 26.3.

(A Paper plugin can't do this. Paper runs on a server, so it can't draw on your screen, add menu buttons or show a 3D model.)

## Before you build: set yourself as owner

Edit `src/main/resources/nexxi-access.properties`:

    owner=YourMinecraftName
    gistId=

The owner is always allowed in and is the only one who can use `/nexxi admin`.

## Access list (who can use the client)

1. Go to https://gist.github.com and make a **secret** gist with one file named `access.json`:

       {
         "denyMessage": "Sorry, you have not been able to join.",
         "users": { "YourName": "owner", "Friend1": "member", "Friend2": "vip" },
         "banned": [ "SomeoneYouDontWant" ]
       }

2. Copy the gist's id (the long code at the end of its URL) into `gistId=` and build.
3. Anyone not in `users` (or in `banned`) sees "Sorry, you have not been able to join." and gets no overlay or features. Names are not case sensitive.
4. To manage people in game (owner only): `/nexxi admin add <name> [role]`, `remove`, `ban`, `unban`, `list`. To let these save to your gist, make a GitHub token that can edit gists and put it in `gistToken` in `config/nexxi-client.json`. Or just edit the gist on the website; the list is re-read each time someone joins a server or you run `/nexxi admin refresh`.

**Honest limit:** the check runs inside the mod on each person's computer, so it stops normal people but someone who edits the mod's code could remove it. Don't treat it as real security.

## Keys (rebind in Options > Controls > Key Binds > Nexxi Client)

| Key | Does |
|---|---|
| `=` / `+` | Open the Nexxi Client menu (name + 3D player top-left) |
| `[` | Pearl puller list with search bar |
| `]` | Send the "I need a pearl pull" alert to your chosen player |
| `O` | Show / hide overlay |
| `F9` | Start / stop LIVE |

There is also a **Nexxi Client** button on the Esc menu and the title menu.

## Pearl puller

Press `[`, type in the search bar, click a player who has the client to make them your target, then press `]` any time (for example while streaming). Their screen shows a flashing "NAME needs a PEARL PULL!" banner. It works through a hidden `/msg` whisper, which only other Nexxi Client users see as an alert. If the server uses another whisper command, change `whisperCommand` in the config.

## Overlay panels

Session profit/loss with a separate LIVE counter, stream stats (TPs, kills, deaths, per hour), top donors, donation goal bar, auction, and donation alerts. Run `/nexxi help` for commands.

## If something doesn't work

Payment, balance, TP and kill message formats are guesses at Donut SMP's wording. Use `/nexxi scoreboard` and `/nexxi debug`, then edit the patterns in `config/nexxi-client.json`. The 3D player is looked up at runtime; if the game version renamed it, it is skipped and the rest still works.

Check Donut SMP's rules on client mods first.
