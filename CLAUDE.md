# Dross — Minecraft Forge mod

## Project summary
- Minecraft **1.20.1**, Forge **47.4.10** (recommended build), Java **17** (Temurin), Gradle 8.8 via the wrapper.
- Mod ID `dross`, display name `Dross`, base package `com.reg21meme.dross`, main class `Dross.java`.
- Mappings: `official` (Mojang names). Mod metadata: `src/main/resources/META-INF/mods.toml`, filled from `gradle.properties`.
- GitHub: https://github.com/Reg21meme/dross-mod (branch `main`).
- The user is a **beginner**. Explain changes in plain language. **Never commit or push unless the user asks.**

## Commands (Windows, run from the project folder)
- Build: `.\gradlew.bat build --console=plain` (output jar: `build/libs/dross-<version>.jar`)
- Run the game: `.\gradlew.bat runClient --console=plain`. This blocks until the game window closes. The user often closes the game themselves, which is normal.
- Game logs: `run/logs/latest.log` (and `debug.log`). Crash reports: `run/crash-reports/`.
- If a first-time setup fails with `NoSuchFileException` under `.gradle/caches/forge_gradle/maven_downloader`, that is a flaky parallel download. Re-run the same command.
- To test commands in-game, create the world with **Allow Cheats: ON**.

## Rules for every agent
1. Forge 1.20.1 / Java 17 APIs only. Use `DeferredRegister` for all registrations. Watch out for newer-version APIs that don't exist here: no `ResourceLocation.fromNamespaceAndPath` (use `new ResourceLocation(Dross.MODID, "path")`), no data components, no NeoForge classes, no `neoforge.mods.toml`.
2. **Shared files: only ADD your own entries.** Never rewrite, reorder, rename or remove another area's entries. Shared files are:
   - `Dross.java`: add one line per thing you need registered on the mod event bus (for example `ModBlocks.BLOCKS.register(modEventBus);`). If `modEventBus` doesn't exist yet, add `IEventBus modEventBus = context.getModEventBus();` once at the top of the constructor.
   - Registry classes in `com.reg21meme.dross.registry`: `ModBlocks`, `ModItems`, `ModEntities`, `ModParticles`, `ModCreativeTabs`, `ModEnchantments` (create one if it doesn't exist yet, holding only the `DeferredRegister` and your entries).
   - `src/main/resources/assets/dross/lang/en_us.json`: add keys only.
3. **Stay in your own area** (see the table below). If you need something from another area, don't build it. Report it as "Needs from <area>: ...".
4. Prefer your own `@Mod.EventBusSubscriber` classes inside your package over adding event code to `Dross.java`.
5. After changes, run `.\gradlew.bat build --console=plain` and fix any errors **in your own area**. If the error is in another area's code, report it instead.
6. Finish with a short, beginner-friendly explanation: what you added, which files, and how to test it in-game.
7. Don't commit.

## Areas and ownership
| Area | Agent | Owns |
|---|---|---|
| Dimension | `dimension-builder` | `com.reg21meme.dross.dimension.*`; `data/dross/dimension/`, `data/dross/dimension_type/`, `data/dross/worldgen/`; Dross mob equipment (skips risen undead; marks geared mobs for `DrossMobGear.hasDrossGear`) |
| Portal | `portal-builder` | `com.reg21meme.dross.portal.*`; the frame block `dross:dross_portal_frame` and the portal block `dross:dross_portal` (definitions, assets, tags); ingot activation; orange texture/particles/overlay; teleporting; return portal; the frame's creative-only block item and its entry in the "Dross" creative tab; the arrival sequence (title, piano notes) and the `dross:entered_the_dross` advancement (`data/dross/advancements/`) |
| World site | `world-builder` | `com.reg21meme.dross.world.*` (portal site location, placing the frame built from `portal-builder`'s frame block); `com.reg21meme.dross.command.*` (the `/dross` command; its `trader` subcommands call the villager area's `TraderCommands`) |
| Villager | `villager-builder` | `com.reg21meme.dross.villager.*`; trader entity, renderer, spawn logic, his hut and its path, his trades (Dross Compass, Admin Sword item and texture, Necromancy books), the compass setting its own target when in a player's inventory, the Admin Sword and Dross Compass entries in the "Dross" creative tab, spawn egg, `/dross trader` logic (`TraderCommands`), join message |
| Enchantments | `enchant-builder` | `com.reg21meme.dross.enchant.*`; the Necromancy and Deathforged enchantments (`registry/ModEnchantments`); the soul system; summoning; the risen undead's behavior (and their entities/renderers if they need their own); Deathforged book drops; the `dross:rise` advancement; the enchanted books in the "Dross" creative tab. Design: see The plan, step 6 |
| Testing | `mod-tester` | Nothing. Builds, runs and reads logs only; never edits feature code |

Client-only code (renderers, particle providers) goes in a `client` subpackage of the area, for example `com.reg21meme.dross.villager.client`.

## The plan
1. **Dross dimension** (`dross:dross`): superflat, permanent night (`fixed_time` 18000), normal hostile mob spawn rates. Zombies spawn in full netherite armor + netherite sword. Skeletons spawn in full netherite armor and keep their bows. Nothing is enchanted (including the bow). None of this gear drops on death. Risen undead (step 6) are skipped by this rule: their gear comes only from Deathforged. Slimes, creepers and spiders are removed from the spawn list. Players see the dimension named **"The Dross"** (lang key `dimension.dross.dross`). Terrain is defined in data JSON with its own biome so it can be upgraded later without rewriting Java.
2. **Dross portal**: the frame is made of a custom block, `dross:dross_portal_frame`, which **can't be crafted or mined** (no recipe, no drops, unbreakable, blast-proof). For testing, it has a block item that is only available in the creative **"Dross"** tab (or `/give`); it still has no recipe, drops nothing and can't be broken in survival. It looks like obsidian with orange veins (Minecraft's obsidian texture copied and recolored, temporary, see Parked) so it matches the portal. Throwing a **netherite ingot** into the empty middle of that frame uses up the ingot and lights an **orange** portal, in frames facing either direction (along X or Z). Only this frame works: a netherite ingot does nothing in a normal obsidian frame, and flint and steel can't light the Dross frame. Normal obsidian frames stay normal purple nether portals. The orange look is Minecraft's nether portal texture copied and recolored orange (temporary, see Parked), plus orange particles and an orange in-portal screen swirl. The portal teleports players between the Overworld and the Dross dimension, both ways.
3. **Portal site** at (0, 0) in the Overworld, for testing: the frame is built from `dross:dross_portal_frame` and is **already there but unlit** when the world is created. It's the only Dross frame in the Overworld. Test command `/dross site` teleports the player there.
4. **Dross trader** (villager): spawns once per world, in his own **hut at the edge of the village nearest world spawn**. Placeholder skin. He can be hurt and knocked back but **can never die**, because he only ever spawns once: at half health or less he teleports home and fully heals.
   - **When:** the village's chunks are generated in the background first (a chunk ticket), then the hut is built and he spawns, a few seconds after the world opens.
   - **Hut:** 5x5x5 outside (3x3x3 inside). Netherite walls, roof and floor edge (temporary, see Parked), a 3x3 gold block floor inside (not carpet: carpet in the doorway stopped him from walking out), an oak door, one glass pane window. It's on an open spot outside the village's bounds, so it never overlaps houses or paths. A dirt path follows the ground from his door to the nearest village path.
   - **No usable village** within 1,600 blocks: the hut goes in the plains/desert nearest world spawn, with no path.
   - **Behavior:** he wanders within 50 blocks of his hut (X and Z) and opens his door. If he's more than 50 blocks out, falls more than 4 blocks, or is more than 3 blocks below the surface (not counting his hut or village buildings), he teleports to the middle of his hut with the enderman sound and orange enderman-style particles. He can't use portals.
   - **Glow (finding aid):** while any player is within his 50-block area, he glows with a gold outline, visible through walls.
   - **Trades** (no restock): 1 nether star → **Dross Compass** that always points to the portal site (3 uses). 1 Dross Portal Frame → **Admin Sword** that kills anything in one hit, bosses included, except the trader (unlimited uses; testing only, see Parked).
   - **Spawn egg:** "Dross Trader Spawn Egg" in the creative Dross tab. An egg trader treats the spot he was spawned at as his home, with the same 50-block area, glow and teleport-home rules.
   - For testing, on join the chat shows his hut's coordinates. `/dross trader` teleports you to him (even if his area isn't loaded) and `/dross trader home` sends him home. His entity ID is `dross:dross_trader`; `@e` selectors only find him while his area is loaded.

5. **Arriving in the Dross** (through the portal only, not `/execute in`), in `portal/DrossArrival.java`. Times are in ticks after arrival (20 ticks = 1 second):
   - **Title**: at tick 20, "The Dross" (gold, from `dimension.dross.dross`) fades in like `/title` (0.5 s in, 3.5 s on screen, 1 s out). Shown every time. The 1-second delay keeps it from being hidden behind the "Loading terrain" screen.
   - **Four-note piano sequence** ("dun, dun, DUN, dunnn"): note block harp, every time, only the arriving player hears it, on the Master volume. Notes start with the title: E, D, C# 0.4 s apart, then a 0.5 s gap and low F# (pitch 0.5, the lowest note block note). Volumes 0.6, 0.6, 0.8, 1.0.
   - **Advancement "Entered the Dross"** (`dross:entered_the_dross`): challenge frame (purple), Dross Portal Frame icon, "Step through the orange portal.", in the Minecraft (story) tab. Granted from code (`minecraft:impossible` trigger) 2 seconds after the last note, so its fanfare doesn't clash with the piano. If the player leaves the Dross before then, it's granted on their next arrival.

6. **Necromancy and Deathforged** (enchantments, `com.reg21meme.dross.enchant`). All tuning numbers are labeled constants at the top of their files.
   - **Necromancy** (swords only, I–IV): "Hits have a chance to raise the undead to fight for you."

     | Level | Chance per hit | Max alive | Soul capacity | Types |
     |---|---|---|---|---|
     | I | 5% | 2 | 25 | zombie, skeleton |
     | II | 10% | 4 | 100 | + husk, stray, drowned |
     | III | 15% | 6 | 250 | + chicken jockey, spider jockey, skeleton horseman |
     | IV (admin, testing only, see Parked) | 100% | 10 | infinite | same as III |

   - **Hits:** only a melee hit with the sword in the main hand on the **main target** of a swing (sweep hits never roll). Hitting a player, the Dross trader or your own risen undead never raises any.
   - **How many:** if none of yours are alive, a success raises the full max; otherwise half the max (I: 1, II: 2, III: 3, IV: 5), never over the max ("alive" counts per player). Each costs 1 soul; a jockey/horseman takes 1 slot but costs 2 souls (with only 1 soul left, a normal undead rises instead). Not enough souls: raise as many as the souls allow. Each one's type is random from the types the level unlocks.
   - **Souls:** killing any zombie, skeleton or variant (anything that is a kind of zombie or skeleton) with the sword stores 1 soul, up to the level's capacity; risen undead give none. An orange wisp flies from the mob into the player's hand. Souls are stored on the sword itself (item tag), so each sword has its own count and it moves with the sword. Tooltip: "Souls: X / Y" ("Souls: ∞" at IV; IV never spends souls).
   - **Risen undead** (real vanilla mobs, owner stored in their persistent data): attack what their owner hits, mobs that hurt their owner, and mobs that hurt them; follow the owner when idle. They never target or hurt players (arrows included), the Dross trader, villagers, wandering traders, or their owner's other risen undead (even if the owner hits a villager or wandering trader). The owner's own hits (sweep included) never damage their risen undead. Whenever a risen undead damages a mob (arrows included), that mob switches its target to the risen undead that hit it, including zombies, skeletons and their variants, which normally ignore other undead. Risen drowned always fight on land, day or night. On Peaceful, Necromancy does nothing and no souls are spent. Deathforged X glow uses one shared team for all players, so different players' glowing undead don't fight each other. They crumble in a puff of orange particles after 60 seconds, or early if the owner logs out, dies or changes dimension, or if their chunk is reloaded from a save. No drops, no XP, no item pickup, no portals, no conversions (zombie→drowned, skeleton→stray). Vanilla burning rules (helmets protect, husks don't burn).
   - **Deathforged** (swords only, I–X; does nothing without Necromancy on the same sword): "Your risen undead come armored. Higher levels forge stronger gear." I leather, II gold, III chainmail, IV iron, V diamond, VI netherite, VII netherite + netherite sword (zombie types; skeletons keep their bow), VIII + Protection II and Sharpness II / Power II, IX + Protection IV and Sharpness V / Power V, X + Strength I and Speed I for their whole life and a gold ("orange") glow outline. Without Deathforged: no armor; skeletons always get a plain bow. Mounts get no gear.
   - **Rules for both:** treasure only (never from the enchanting table, loot chests, fishing or normal librarians). Books work at an anvil, but two items with the same level of either enchantment can't be combined (no anvil result). Necromancy can't be combined with Smite. Rarity keeps Deathforged X under the anvil's "Too Expensive!" limit.
   - **Getting them:** the Dross trader sells Necromancy I (16 emeralds + book), II (8 diamonds + book), III (1 nether star + book) and IV (1 Dross Portal Frame), all unlimited uses. Existing traders get missing trades added when they load. Deathforged books drop only from the netherite-wearing zombies and skeletons of the Dross (`DrossMobGear.hasDrossGear`), when a player hit them recently: 5% per kill + 1% per Looting level; level weights I–X: 25, 20, 15, 12, 9, 7, 5, 4, 2, 1.
   - **Advancement "Rise!"** (`dross:rise`): "Raise the undead with Necromancy.", challenge frame (purple), zombie head icon, Minecraft (story) tab, granted from code the first time a player raises undead.
   - **Creative "Dross" tab:** the Admin Sword, the Dross Compass (a hidden marker tag; once it's in a player's inventory its target is set to the portal site), and enchanted books for every level of both enchantments.

## Build order
1. Dimension → 2. Portal → 3. World site → 4. Villager → 5. Enchantments.
Run `mod-tester` after each step.

**Status:** steps 1–5 are built and tested in-game, including the reworked trader (hut, compass, Admin Sword, spawn egg, `/dross trader`). Step 6 (Necromancy and Deathforged) is built and tested in-game; the fix that makes hit mobs turn on the risen undead was added after that test.

## Parked for later (do NOT build yet)
- Zombie guards at the portal site.
- Kill 3 iron golems to unlock the portal.
- Old-Minecraft-style terrain for the Dross dimension.
- A custom villager skin (replacing the placeholder).
- Moving the portal site 3,000–10,000 blocks out from (0, 0).
- Replace the recolored portal texture with an original one before publishing. The same goes for the recolored frame texture (orange-veined obsidian).
- Remove the Admin Sword (the trade, `AdminSwordItem`, its `ModItems` entry, model, texture (a recolored Mojang netherite sword) and lang key).
- Replace the trader hut's netherite blocks with a real building material.
- Remove Necromancy IV (its trade, its creative-tab book, and the level itself: lower the max level to III).
