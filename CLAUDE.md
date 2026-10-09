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
   - Registry classes in `com.reg21meme.dross.registry`: `ModBlocks`, `ModItems`, `ModEntities`, `ModParticles`, `ModCreativeTabs`, `ModEnchantments`, `ModLootModifiers` (create one if it doesn't exist yet, holding only the `DeferredRegister` and your entries).
   - `src/main/resources/assets/dross/lang/en_us.json`: add keys only.
   - `DrossColors.java` (`com.reg21meme.dross.DrossColors`): add constants in your own area's section, and change only your own area's values.
3. **Stay in your own area** (see the table below). If you need something from another area, don't build it. Report it as "Needs from <area>: ...".
4. Prefer your own `@Mod.EventBusSubscriber` classes inside your package over adding event code to `Dross.java`.
5. After changes, run `.\gradlew.bat build --console=plain` and fix any errors **in your own area**. If the error is in another area's code, report it instead.
6. Finish with a short, beginner-friendly explanation: what you added, which files, and how to test it in-game.
7. Don't commit.
8. **Colors come from `DrossColors`.** Never hard-code a color in feature code (particles, glows, text styles, spawn eggs). If you need a new color, add a labeled constant in your area's section of `DrossColors`. Some older code still has hard-coded colors; move it onto `DrossColors` when you next change that code.

## Areas and ownership
| Area | Agent | Owns |
|---|---|---|
| Dimension | `dimension-builder` | `com.reg21meme.dross.dimension.*`; `data/dross/dimension/`, `data/dross/dimension_type/`, `data/dross/worldgen/`; Dross mob equipment (skips risen undead; marks geared mobs for `DrossMobGear.hasDrossGear`); the hub's safe zone (no natural hostile spawns near `DrossHub.CENTER`) |
| Portal | `portal-builder` | `com.reg21meme.dross.portal.*`; the frame block `dross:dross_portal_frame` and the portal block `dross:dross_portal` (definitions, assets, tags); the frame's ambient particles and sound; the **Rift Key** item and lighting a frame with it (replaces the netherite ingot); blocking portal lighting inside the Dross; the portal's look (texture, particles, screen overlay); teleporting; the **hub** at Dross 0,0 (`portal/DrossHub`: structure and exit portal); the frame's creative-only block item; the Rift Key and frame entries in the "Dross" creative tab; the arrival sequence (title, piano notes, and *when* `dross:entered_the_dross` is granted and the Guide Book is given, both through `quest-builder`'s API; the advancement JSON belongs to Quest) |
| World site | `world-builder` | `com.reg21meme.dross.world.*` (the site location `PortalSite` and its API; placing the **castle**, or its placeholder shrine, around a frame made of `portal-builder`'s frame block; the "leaking" dead plants and cracked blocks around it); `com.reg21meme.dross.command.*` (the `/dross` command root; its `trader` and `quest` subcommands call the villager and quest areas' logic) |
| Villager | `villager-builder` | `com.reg21meme.dross.villager.*`; trader entity, renderer, spawn logic, his hut and its path, his dialogue, tribute and key-material hand-ins (through Quest's progress API), giving the Dross Compass and the Rift Key, his shop, the Admin Sword (item and texture), the Dross Compass (and it setting its own target when in a player's inventory), the Admin Sword and Dross Compass entries in the "Dross" creative tab, spawn egg, `/dross trader` logic (`TraderCommands`), the test join message (being removed) |
| Enchantments | `enchant-builder` | `com.reg21meme.dross.enchant.*`; the Necromancy and Deathforged enchantments (`registry/ModEnchantments`); the soul system; summoning; the risen undead's behavior (and their entities/renderers if they need their own); Deathforged book drops; *when* `dross:rise` is granted (through `quest-builder`'s grant helper; the advancement JSON belongs to Quest); the enchanted books in the "Dross" creative tab. Design: see The plan, step 6 |
| Quest | `quest-builder` | `com.reg21meme.dross.quest.*`; player progression: the Weathered Letter item and its Nether chest loot (global loot modifier, `registry/ModLootModifiers`, `data/dross/loot_modifiers/`, `data/forge/loot_modifiers/`); per-player quest progress saving (`SavedData` by player UUID, with a public API other areas call); the Dross advancement tab and **all** Dross advancements (`data/dross/advancements/`, their lang keys, and the shared grant helper); the Dross Guide Book item (and the first-arrival flag); `/dross quest` logic. Later: boss progression. The trader's dialogue and hand-ins stay with Villager, which uses Quest's progress API |
| Testing | `mod-tester` | Nothing. Builds, runs and reads logs only; never edits feature code |

Client-only code (renderers, particle providers, book screens) goes in a `client` subpackage of the area, for example `com.reg21meme.dross.villager.client`.

## The plan
The full design, including the **early-game quest** from `EARLY_GAME_PLAN.md`, which replaces the old test quest (nether star for a compass, netherite ingot lights the bare frame at 0,0, arrival next to an auto-built return portal).
- **[built]** means it's in the game now.
- **[to build]** means it's part of the early-game build and isn't in the game yet (see "Build order"). Where something is marked "replaces", the old behavior stays in the game until that build step runs.

The Dross is a **mid-to-late game** place: players reach it after proving they survived dangerous places, not after beating every boss. It should work well alongside other mods. The only way in is the castle portal and the only way out is the hub, so the Dross stays dangerous.

### The look: electric blue [to build]
- The Dross's color is **electric blue** (deep cobalt / electric blue, clearly different from vanilla's cyan soul fire). It **replaces orange everywhere**: portal texture, portal particles, screen swirl, frame, the trader's teleport particles and glow, the spawn egg spots, the risen undead's puffs and wisps, the Deathforged X glow, and the arrival title.
- Every color is a labeled constant in `DrossColors`. The palette is main `0x2E6BFF`, deep cobalt `0x0B2A9E` and highlight `0x7FA8FF`, kept in a shared "Palette" section of `DrossColors` (added by `portal-builder`, the first step). Each area points its own constants at it.
- Scoreboard-team glows (Deathforged X) can only use chat colors, so they use `ChatFormatting.BLUE`. The trader's glow uses the exact RGB.
- **Not recolored:** the Admin Sword texture (parked for removal) and the soul tooltip text (stays gold).

### Steps
1. **Dross dimension** (`dross:dross`) [built]: superflat, permanent night (`fixed_time` 18000), normal hostile mob spawn rates.
   - Zombies spawn in full netherite armor + netherite sword. Skeletons spawn in full netherite armor and keep their bows. Nothing is enchanted (including the bow). None of this gear drops on death. Risen undead (step 6) are skipped by this rule: their gear comes only from Deathforged.
   - Slimes, creepers and spiders are removed from the spawn list.
   - Players see the dimension named **"The Dross"** (lang key `dimension.dross.dross`).
   - Terrain is defined in data JSON with its own biome so it can be upgraded later without rewriting Java.
   - [to build] The hub at Dross 0,0 is a safe zone (step 5).

2. **Dross portal**
   - **Frame** [built]: a custom block, `dross:dross_portal_frame`, which **can't be crafted or mined** (no recipe, no drops, unbreakable, blast-proof). For testing, it has a block item that is only available in the creative **"Dross"** tab (or `/give`); it still has no recipe, drops nothing and can't be broken in survival. Flint and steel can't light it, and normal obsidian frames stay normal purple nether portals.
   - **Frame look** [to build, replaces the recolored obsidian]: an **original** texture (dark stone with electric blue cracks), no longer based on Mojang's obsidian.
   - **"The Dross is leaking out"** [to build]: frame blocks give off drifting electric blue particles and a low ambient sound. This applies to every frame, including the unlit castle frame.
   - **Lighting** [to build, replaces the netherite ingot]: throwing the **Rift Key** (step 7) into the empty middle of a valid frame (along X or Z) **uses up the key** and lights the portal.
     - If that frame is already lit, the key isn't used: it pops back out, with a portal cooldown so it doesn't drift into the Dross.
     - A netherite ingot no longer lights anything.
   - **No portals inside the Dross** [to build]: the Rift Key does nothing there, and lighting a nether portal (flint and steel or fire on obsidian) is blocked there. Otherwise a nether portal would be a second way out.
   - **Portal look** [to build, replaces orange]: Minecraft's nether portal texture copied and recolored **electric blue** (still a recolor, see Parked), blue particles, and a blue in-portal screen swirl.
   - **Stays open**: once lit, the castle portal stays open permanently, for everyone.
   - **Travel** [to build, replaces "same X/Z, next to an auto-built return portal"]:
     - Every Dross portal outside the Dross leads to the **hub at Dross 0,0** (step 5).
     - The hub's exit portal always leads to the **castle portal** (`PortalSite`), wherever the player came in.
     - No more auto-built return portals, and no saved return-portal lookup.
     - All entities can travel, as now. Never place an entity inside blocks or above a void drop.

3. **The castle (portal site)**
   - [built today] A bare, unlit frame of `dross:dross_portal_frame` at (0, 0) in the Overworld, placed once per world, plus `/dross site`.
   - [to build, replaces the bare frame] **The castle**:
     - Placed once per world, at the site location, on flattened ground (reuse the existing site-placement logic). The frame is unlit and fully unbreakable.
     - If `data/dross/structures/portal_castle.nbt` exists, that template is placed. The user builds it in creative and saves it with a structure block as `dross:portal_castle`.
     - Otherwise a **placeholder**: a small ruined shrine around the frame.
     - The template must contain exactly **one** complete Dross frame with an empty opening (either direction). The code finds it and stores its bottom corner **and axis**.
     - Structure blocks save at most 48×48×48 in 1.20.1, so a bigger castle needs several pieces.
   - **Location**: one constant in `PortalSite`. It's (0, 0) for testing; a random spot 3,000–10,000 blocks out is parked.
   - **`PortalSite` API becomes axis-aware**: keep `getFramePos`, and add `getFrameAxis` and `getOpeningCenter`. The compass and `/dross site` use them.
   - **Leaking** [to build]: once, when the castle is placed, within about 16 blocks:
     - flowers and grass become dead bushes or are removed,
     - some grass blocks become coarse dirt,
     - stone bricks become cracked stone bricks.

     The particles and sound come from the frame blocks (step 2).
   - **`/dross site`**: teleports the player in front of the frame, facing it (axis-aware).

4. **Dross trader** (villager) [built, except where marked]: spawns once per world, in his own **hut at the edge of the village nearest world spawn**. Placeholder skin. He can be hurt and knocked back but **can never die**, because he only ever spawns once: at half health or less he teleports home and fully heals.
   - **When:** the village's chunks are generated in the background first (a chunk ticket), then the hut is built and he spawns, a few seconds after the world opens.
   - **Hut:** 5x5x5 outside (3x3x3 inside). Netherite walls, roof and floor edge (temporary, see Parked), a 3x3 gold block floor inside (not carpet: carpet in the doorway stopped him from walking out), an oak door, one glass pane window. It's on an open spot outside the village's bounds, so it never overlaps houses or paths. A dirt path follows the ground from his door to the nearest village path.
   - **No usable village** within 1,600 blocks: the hut goes in the plains/desert nearest world spawn, with no path.
   - **Behavior:** he wanders within 50 blocks of his hut (X and Z) and opens his door. If he's more than 50 blocks out, falls more than 4 blocks, or is more than 3 blocks below the surface (not counting his hut or village buildings), he teleports to the middle of his hut with the enderman sound and enderman-style particles (the portal's particles, so electric blue after the color switch). He can't use portals.
   - **Glow (finding aid):** while any player is within his 50-block area, he glows with an outline visible through walls (gold today, electric blue after the color switch).
   - **Quest and dialogue** [to build, replaces the test trades]: see step 7.
   - **Shop** [to build, replaces all current trades]:
     - Remove the nether star → compass trade, the Admin Sword trade and all Necromancy book trades.
     - After a player completes the quest he sells **Necromancy (its lowest level)** for 32 emeralds + book and **Deathforged I** for 24 emeralds + book. Unlimited uses, no restock, no XP.
     - The offers are the same for everyone, but the trading screen only opens for players who completed the quest (received the Rift Key).
     - **Dross Guide Book** [to build]: for players who have the "Entered the Dross" advancement, he also sells the Dross Guide Book for 3 books (no emeralds; a labeled constant). Unlimited uses, no restock, no XP. Players without that advancement don't see this offer.
     - Existing traders in old worlds get their old offers replaced when they load.
   - **Spawn egg:** "Dross Trader Spawn Egg" in the creative Dross tab (spots electric blue after the color switch). An egg trader treats the spot he was spawned at as his home, with the same 50-block area, glow and teleport-home rules.
   - **Test tools:** `/dross trader` teleports you to him (even if his area isn't loaded) and `/dross trader home` sends him home. His entity ID is `dross:dross_trader`; `@e` selectors only find him while his area is loaded.
   - [to build] **Remove** the test-only join message with his hut's coordinates. The Weathered Letter (step 7) replaces it.

5. **Arriving in the Dross** (through the portal only, not `/execute in`)
   - **The hub** [to build, replaces arriving next to an auto-built return portal]:
     - Built once per world at Dross 0,0. Its center is a constant, `DrossHub.CENTER`, in the portal area.
     - If `data/dross/structures/dross_hub.nbt` exists (the user designs it later with a structure block), that template is used. Otherwise a **placeholder** platform with the lit **exit portal**.
     - Players arrive at the hub, standing in front of the exit portal.
     - **Safe zone:** no **natural** hostile mob spawning within **48 blocks** (X and Z) of the hub center. Mobs can still walk in from outside.
   - **Arrival sequence**, in `portal/DrossArrival.java`. Times are in ticks after arrival (20 ticks = 1 second):
     - **Title** [built]: at tick 20, "The Dross" (from `dimension.dross.dross`) fades in like `/title` (0.5 s in, 3.5 s on screen, 1 s out). Shown every time. The 1-second delay keeps it from being hidden behind the "Loading terrain" screen. Its color is gold today and electric blue (`DrossColors`) after the color switch.
     - **Four-note piano sequence** [built] ("dun, dun, DUN, dunnn"): note block harp, every time, only the arriving player hears it, on the Master volume. Notes start with the title: E, D, C# 0.4 s apart, then a 0.5 s gap and low F# (pitch 0.5, the lowest note block note). Volumes 0.6, 0.6, 0.8, 1.0.
     - **Advancement "Entered the Dross"** (`dross:entered_the_dross`), **the main achievement**: challenge frame (purple), Dross Portal Frame icon.
       - [built] Granted from code (`minecraft:impossible` trigger) 2 seconds after the last note, so its fanfare doesn't clash with the piano. If the player leaves the Dross before then, it's granted on their next arrival.
       - [to build] Moves into the Dross tab (step 7) and is granted through Quest's grant helper. Description changes from "Step through the orange portal." to "Step through the blue portal."
     - **Dross Guide Book** [to build]: on the player's **first arrival only** (a flag that survives death), they get the **Dross Guide Book**: a custom item, readable like a written book, with its own original texture and placeholder text explaining the dimension. If their inventory is full, it drops at their feet.

6. **Necromancy and Deathforged** (enchantments, `com.reg21meme.dross.enchant`) [built, except where marked]. All tuning numbers are labeled constants at the top of their files.
   - **Necromancy** (swords only, I–IV): "Hits have a chance to raise the undead to fight for you."

     | Level | Chance per hit | Max alive | Soul capacity | Types |
     |---|---|---|---|---|
     | I | 5% | 2 | 25 | zombie, skeleton |
     | II | 10% | 4 | 100 | + husk, stray, drowned |
     | III | 15% | 6 | 250 | + chicken jockey, spider jockey, skeleton horseman |
     | IV (admin, testing only, see Parked) | 100% | 10 | infinite | same as III |

   - **Hits:** only a melee hit with the sword in the main hand on the **main target** of a swing (sweep hits never roll). Hitting a player, the Dross trader or your own risen undead never raises any.
   - **How many:** if none of yours are alive, a success raises the full max; otherwise half the max (I: 1, II: 2, III: 3, IV: 5), never over the max ("alive" counts per player). Each costs 1 soul; a jockey/horseman takes 1 slot but costs 2 souls (with only 1 soul left, a normal undead rises instead). Not enough souls: raise as many as the souls allow. Each one's type is random from the types the level unlocks.
   - **Souls:** killing any zombie, skeleton or variant (anything that is a kind of zombie or skeleton) with the sword stores 1 soul, up to the level's capacity; risen undead give none. A wisp (orange today, electric blue after the color switch) flies from the mob into the player's hand. Souls are stored on the sword itself (item tag), so each sword has its own count and it moves with the sword. Tooltip: "Souls: X / Y" ("Souls: ∞" at IV; IV never spends souls), in gold.
   - **Risen undead** (real vanilla mobs, owner stored in their persistent data): attack what their owner hits, mobs that hurt their owner, and mobs that hurt them; follow the owner when idle. They never target or hurt players (arrows included), the Dross trader, villagers, wandering traders, or their owner's other risen undead (even if the owner hits a villager or wandering trader). The owner's own hits (sweep included) never damage their risen undead. Whenever a risen undead damages a mob (arrows included), that mob switches its target to the risen undead that hit it, including zombies, skeletons and their variants, which normally ignore other undead. Risen drowned always fight on land, day or night. On Peaceful, Necromancy does nothing and no souls are spent. Deathforged X glow uses one shared team for all players, so different players' glowing undead don't fight each other. They crumble in a puff of particles (orange today, electric blue after the color switch) after 60 seconds, or early if the owner logs out, dies or changes dimension, or if their chunk is reloaded from a save. No drops, no XP, no item pickup, no portals, no conversions (zombie→drowned, skeleton→stray). Vanilla burning rules (helmets protect, husks don't burn).
   - **Deathforged** (swords only, I–X; does nothing without Necromancy on the same sword): "Your risen undead come armored. Higher levels forge stronger gear." I leather, II gold, III chainmail, IV iron, V diamond, VI netherite, VII netherite + netherite sword (zombie types; skeletons keep their bow), VIII + Protection II and Sharpness II / Power II, IX + Protection IV and Sharpness V / Power V, X + Strength I and Speed I for their whole life and a glow outline (gold today, `ChatFormatting.BLUE` after the color switch). Without Deathforged: no armor; skeletons always get a plain bow. Mounts get no gear.
   - **Rules for both:** treasure only (never from the enchanting table, loot chests, fishing or normal librarians). Books work at an anvil, but two items with the same level of either enchantment can't be combined (no anvil result). Necromancy can't be combined with Smite. Rarity keeps Deathforged X under the anvil's "Too Expensive!" limit.
   - **Getting them:**
     - [built today] The trader sells Necromancy I–IV.
     - [to build] After the quest he sells only Necromancy (lowest level) and Deathforged I (step 4).
     - Deathforged books drop only from the netherite-wearing zombies and skeletons of the Dross (`DrossMobGear.hasDrossGear`), when a player hit them recently: 5% per kill + 1% per Looting level; level weights I–X: 25, 20, 15, 12, 9, 7, 5, 4, 2, 1.
   - **Advancement "Rise!"** (`dross:rise`): "Raise the undead with Necromancy.", challenge frame (purple), zombie head icon, granted from code the first time a player raises undead. [to build] Moves into the Dross tab (step 7), parent "Entered the Dross", granted through Quest's grant helper.
   - **Creative "Dross" tab:** the Admin Sword, the Dross Compass (a hidden marker tag; once it's in a player's inventory its target is set to the portal site), and enchanted books for every level of both enchantments (these stay even after the trader stops selling them).

7. **The early-game quest** [to build]. Progress is **per player, per world**, saved by Quest (a `SavedData` keyed by player UUID). The trader's side (dialogue, hand-ins, gifts) is Villager's, through Quest's API.
   1. **Normal survival.** Nothing from the mod is needed yet.
   2. **The Weathered Letter** (Quest):
      - A custom item, readable like a written book. It opens `BookViewScreen` from client code, with its pages in the item's tag.
      - Found in **every Nether Fortress and Bastion Remnant chest**, 100% for testing (`LETTER_CHANCE = 1.0`, a labeled constant so it can be lowered later).
      - Added with a Forge **global loot modifier**, so other mods' loot changes still work. Tables: `minecraft:chests/nether_bridge`, `bastion_treasure`, `bastion_other`, `bastion_bridge`, `bastion_hoglin_stable`.
      - It says an old villager near a village close to spawn is seeking someone strong, and gives a **rough location**: the direction from world spawn plus coordinates rounded to 50 (for example "…north-east of where the world began, near X 250, Z -100").
      - The text is written when the letter is created. If the hut isn't built yet, it uses vaguer wording. Creative-tab copies get their text filled in once they're in a player's inventory.
      - Picking one up grants "A Weathered Letter".
   3. **Meeting the trader** (Villager):
      - The first time a player right-clicks him, he speaks in chat with his name as the speaker: `<Dross Trader> You've come about the rift? I don't believe you're strong enough. Show me. Bring me a skull from the Nether's fortresses, shards from the cities of the deep dark, and stone from the End.`
      - Each later right-click (when he isn't taking an item) repeats what's still needed.
      - Every line is a lang key in `en_us.json`, so the wording can be edited without code.
      - Only the main hand counts. His trading screen never opens before the quest is complete.
   4. **The three tributes** (Villager, any order): 1 **wither skeleton skull**, 3 **echo shards**, 15 **end stone**.
      - Vanilla trades allow only two cost items, so instead the player **right-clicks him while holding** a tribute.
      - **Full amount only**: if they're holding at least the required amount, he takes exactly that much and confirms in chat. If not, he says how many to bring.
      - When all three are in, he gives the **Dross Compass** (dropped at the player's feet if their inventory is full) and says he knows how to forge a key but needs materials. This grants "Proven Worthy".
   5. **The key materials** (Villager, only after all tributes, any order, same rules): 1 **trident** (any), 1 **Heart of the Sea**, 8 **amethyst shards**.
      - When all are in, he forges the **Rift Key** and gives it with a line of dialogue. This grants "Keymaster".
      - The quest is now complete, and his shop opens for that player.
   6. **Lost items:**
      - If a player earned the compass and has none in their inventory, he gives a new one for free.
      - A lost Rift Key means bringing the key materials again (no free duplicates).
      - The Rift Key is fireproof and never despawns.
   7. **The Rift Key** (Portal): a custom item with an original texture, a crystal key with a blue heart. Throwing it into the castle frame lights the portal and uses it up (step 2).
   8. **The Dross Compass** (Villager, built): a vanilla compass with lodestone tags, re-aimed every second while it's in a player's inventory. Moving the site later keeps compasses correct. It targets `PortalSite.getOpeningCenter` (to build) and spins in other dimensions.
   9. **The Dross advancement tab** (Quest): all Dross advancements move out of the vanilla Minecraft tab into their **own "Dross" tab**.
      - The tab has a root advancement with a background (the new frame texture). The root shows no toast and no chat message, and is granted silently when the player picks up a Weathered Letter, so **the tab stays hidden until then**.
      - Chain: root → **A Weathered Letter** → **Proven Worthy** → **Keymaster** → **Entered the Dross** (challenge, the main achievement) → **Rise!** (challenge).
      - There is **no** "The Rift Reopened" advancement.
      - Quest owns every advancement JSON and a grant helper. Other areas call the helper when their event happens.
      - Quest's progress API grants "Proven Worthy" and "Keymaster" itself when those stages complete.
   10. **Test commands** (cheats on):
       - `/dross hub` teleports you to the hub (World site hooks it up).
       - `/dross quest status|reset|complete` shows, clears or finishes your quest progress (Quest logic, World site hooks it up).
   11. **Creative "Dross" tab** additions: the Weathered Letter, the Rift Key, the Dross Guide Book.

## Build order
Original build: 1. Dimension → 2. Portal → 3. World site → 4. Villager → 5. Enchantments → 6. Quest. Run `mod-tester` after each step.

**Status:** steps 1–6 of the plan are built and tested in-game, as the old test quest (nether star compass, netherite ingot, bare frame, auto-built return portal, orange). This includes the reworked trader (hut, compass, Admin Sword, spawn egg, `/dross trader`) and Necromancy and Deathforged (the fix that makes hit mobs turn on the risen undead was added after that test). The **early-game build** below is **not started**.

### Early-game build (everything marked [to build])
Run the agents **one at a time** (never in parallel). They add to the same shared files and their Gradle builds would collide. Run `mod-tester` after each step.
1. **`portal-builder`**:
   - Add the shared "Palette" section to `DrossColors`, and move the portal's colors onto `DrossColors` (blue).
   - The Rift Key item, and key activation instead of the netherite ingot (used up; pops back if the frame is already lit; never inside the Dross).
   - Block nether-portal lighting in the Dross.
   - Blue portal texture recolor, particles, overlay and title.
   - The original frame texture, plus the frame's ambient particles and sound.
   - `DrossHub` (center constant, template or placeholder, lit exit portal, built once). Arrival at the hub, exit to the castle, and removing the auto-built return portal and the saved return-portal lookup.
   - The Rift Key and frame in the creative tab.
   - Until Quest exists, keep the current advancement grant in `DrossArrival`. Step 2's agent will need the hook points listed in your report.
2. **`quest-builder`**:
   - The progress `SavedData` and its public API (including the automatic "Proven Worthy" / "Keymaster" grants).
   - The grant helper.
   - The Dross tab: root plus all advancement JSON. That means adding `weathered_letter`, `proven_worthy` and `keymaster`, and moving `entered_the_dross` and `rise` into the tab.
   - The Weathered Letter, its loot modifier and `registry/ModLootModifiers`.
   - The Dross Guide Book item and the first-arrival flag.
   - The `/dross quest` logic.
   - Report the API for Portal (Guide Book and arrival grant), Enchantments ("Rise!") and Villager.
3. **`villager-builder`**:
   - Dialogue, tribute and key-material hand-ins through Quest's API, the compass gift and free replacement, and the Rift Key gift.
   - The new shop (quest-gated, replacing old offers on load), and removing all test trades.
   - Removing `TestingJoinMessage`.
   - Blue glow and spawn egg via `DrossColors`.
   - Aiming the compass at `PortalSite.getOpeningCenter` once World site adds it (until then keep the current target and report "Needs from world site").
4. **`world-builder`**:
   - The castle (template or placeholder shrine) on flattened ground, once per world, finding the frame and storing its corner and axis.
   - Making `PortalSite` axis-aware (`getFrameAxis`, `getOpeningCenter`).
   - The leak effects.
   - An axis-aware `/dross site`.
   - Hooking up `/dross hub` and `/dross quest`.
5. **`dimension-builder`**: no natural hostile spawns within 48 blocks of `DrossHub.CENTER`.
6. **`enchant-builder`**: blue wisps, puffs and Deathforged X glow via `DrossColors`. Grant "Rise!" through Quest's helper.
7. **Follow-ups**: any "Needs from <area>" items from the reports above (for example Portal switching the arrival grant and Guide Book to Quest's API, or Villager switching the compass to `getOpeningCenter`). Then a final `mod-tester` run (build plus a `runClient` load check).

### Technical notes for the early-game build
- **Ingot activation:** `portal/DrossPortalActivation.java` checks `Items.NETHERITE_INGOT` (around L45 and L60). The shrink-by-one step stays; add the dimension check and the "already lit" pop-back.
- **Teleport:** in `portal/DrossTeleporter.java`:
  - `toDross` should target the hub.
  - Remove `buildReturnPortal`.
  - `toOverworld` should always go to `PortalSite`.

  The `dross_return_portal` data in `DrossPortalTravel` is no longer needed.
- **Orange colors to move onto `DrossColors`:**
  - `portal/client/DrossPortalParticleProvider.java`
  - `portal/DrossArrival.java` (title)
  - `villager/DrossTrader.java` (`GLOW_COLOR`)
  - `registry/ModItems.java` (spawn egg)
  - `enchant/NecromancyEvents.java` (`WISP_DUST`)
  - `enchant/RisenUndead.java` (`ORANGE_DUST`, `GLOW_COLOR`)
  - The texture PNGs.
- **Compass:** `villager/DrossCompass.target` currently uses `offset(1,1,0)`, which assumes an X-axis frame. Switch it to `PortalSite.getOpeningCenter`.
- **Trader:** `DrossTrader.mobInteract` is where hand-ins and dialogue hook in. `buildOffers` and `addMissingOffers` are replaced by the new shop plus a one-time migration that clears old offers.
- **Saved data:** copy the `villager/TraderSpawnData` pattern (`computeIfAbsent` on the Overworld's data storage).
- **Readable items:** vanilla only opens the book screen for `Items.WRITTEN_BOOK`. The Weathered Letter and the Guide Book open `BookViewScreen` from Quest's client code.
- **Castle file:** a structure block saves to `run/saves/<world>/generated/dross/structures/portal_castle.nbt`. Copy it to `src/main/resources/data/dross/structures/`.
- **Old test worlds** keep their bare frame and old portals. Test the castle and hub in a **new world**.

### In-game test after the early-game build (new world, cheats ON)
1. `/loot give @s loot minecraft:chests/nether_bridge`: you get a Weathered Letter. Read it (rough hut location). The Dross tab appears with "A Weathered Letter".
2. `/dross trader`, then right-click him: the opening speech, then a reminder of what's still needed. The shop doesn't open.
3. Hand in 1 wither skeleton skull, 3 echo shards and 15 end stone, in any order.
   - Holding too few gets a "bring N" reply. Holding more takes exactly the amount needed.
   - You get the compass and "Proven Worthy".
   - Drop the compass and right-click him: you get a free replacement.
4. Hand in 1 trident, 1 Heart of the Sea and 8 amethyst shards. You get the Rift Key and "Keymaster". The shop now opens with Necromancy (32 emeralds + book) and Deathforged I (24 emeralds + book).
5. Follow the compass (`/dross site`): the placeholder shrine, dead plants and cracked blocks nearby, drifting blue particles and an ambient sound.
6. Throw the key in (Q): blue portal, blue swirl, and the key is used up.
   - Throw a second key (creative tab) into the lit portal: it pops back out.
   - A netherite ingot does nothing.
   - Leave and rejoin: the portal is still lit.
7. Walk in: you arrive at the hub at Dross 0,0, with the blue title, the four notes, "Entered the Dross" and the Guide Book. A second trip gives no book.
8. Stay near the hub at night: no hostile mobs spawn within 48 blocks. Flint and steel on an obsidian frame does nothing in the Dross, and neither does the Rift Key.
9. Take the hub's exit portal: you arrive at the castle portal.
10. Necromancy: wisps, puffs and the Deathforged X glow are blue. The trader glows blue. "Rise!" is in the Dross tab.
11. `/dross quest reset` lets you replay the quest.

## Parked for later (do NOT build yet)
- Zombie guards at the portal site.
- Moving the portal site 3,000–10,000 blocks out from (0, 0), chosen when the world is created.
- The real castle (`dross:portal_castle`) and the real hub (`dross:dross_hub`): the user builds them with structure blocks. Until then, the placeholders are used.
- A custom villager skin (replacing the placeholder).
- Replace the recolored portal texture (a recolored Mojang nether portal, blue after the color switch) with an original one before publishing.
- Remove the Admin Sword (`AdminSwordItem`, its `ModItems` entry, model, texture (a recolored Mojang netherite sword), lang key and creative-tab entry). Its trade is removed in the early-game build.
- Replace the trader hut's netherite blocks with a real building material.
- **Necromancy five-level restructure** (a new weaker level I, the old I–III shift up, the admin level becomes V) and Deathforged anvil changes. The trader keeps selling "the lowest Necromancy level".
- New terrain (gentle, old-Minecraft style), dense forests, silverwood trees, custom ores and crops.
- Boss towers (including the golem floor), mini-bosses, the three elemental necromancers (Fire, Ice, Plague), the Earth General, the Storm King, the dragon mount, and boss exit portals.
- The elemental relics (Magma Wand, Frostbreaker Charm, Cleansing Flask), the Great Mystic Bow, and special arrows.
- Player-built portals (Stormvine, a craftable frame, building portals anywhere in the Overworld, returning to the portal you came from, protection for player-built frames).
- Config files for modpack makers (keep values as constants so a config can be added later).
