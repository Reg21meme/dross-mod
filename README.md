# Dross

A Minecraft Forge mod that adds **the Dross**: a dangerous dimension of endless night, reached through an electric blue rift portal. Getting there takes a quest. You find an old letter in the Nether, prove yourself to a strange villager, and have him forge the key that opens the rift. The mod also adds two necromancy enchantments that raise the undead to fight for you.

The Dross is a **mid-to-late game** place. You reach it after surviving the Nether, the deep dark, the ocean and the End, not after beating every boss. There is one way in (the portal at the Fallen Cathedral) and one way out (the hub's portal).

| | |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.4.10 |
| Java | 17 |
| Mod ID | `dross` |
| Version | 0.1.0 |

---

## The story

How a player moves through the mod, from a normal survival world to the Dross.

1. **Normal survival.** Nothing from the mod is needed at first. Somewhere 3,000–10,000 blocks from spawn, the **Fallen Cathedral** already stands in ruins around an unlit blue portal frame, and the land around it is dying. An old villager, the **Dross Trader**, lives in a small stone chapel at the edge of a village near spawn.
2. **The Weathered Letter.** Every Nether Fortress and Bastion Remnant chest holds a Weathered Letter. It's a 4-page letter from an unnamed friend:
   - Blue portals from the Dross have been spilling into the world. "We" closed them, but the Dross is pressing to break through again.
   - An old villager, the writer's friend, is looking for someone strong enough to fight back.
   - It gives a rough location for him: a direction from where the world began, and coordinates rounded to 50 blocks.
   - It tells the reader to say an old friend sent them about the rift.

   Picking up the letter opens the **Dross** advancement tab.
3. **Meeting the trader.** The first time you right-click him, he doubts you're strong enough and sends you for three tributes, one each from the Nether's fortresses, the deep dark's cities and the End. After that, each right-click reminds you what's still missing. His trading screen stays closed until the quest is done.
4. **The three tributes** (any order). Right-click him while holding the full amount: **1 wither skeleton skull**, **3 echo shards** and **15 end stone**. If you hold too few, he tells you how many to bring. If you hold more, he takes exactly what he needs. Once all three are in, he gives you the **Dross Compass**, which points at the cathedral's portal. He says he knows how to forge a key but needs materials. *Advancement: Proven Worthy.*
5. **The key materials** (any order, same rules): **1 trident**, **1 Heart of the Sea** and **8 amethyst shards**. He forges the **Rift Key**, tells you to be careful what you let out, and mentions he has books that can help in there. *Advancement: Keymaster.* His shop opens for you.
6. **Opening the rift.** Follow the compass to the Fallen Cathedral and throw the Rift Key into the frame's opening. The key is used up and the portal lights, staying open for everyone, permanently.
7. **The Dross.** Stepping through takes you to the **Shattered Spire**, the hub at Dross 0,0. The words "The Dross" fade in over four low piano notes. *Advancement: Enter the Dross*, the main achievement. On your first arrival you also get the **Dross Guide Book**.
8. **Necromancy.** The trader sells the Necromancy and Deathforged enchanted books, and Deathforged books also drop in the Dross. Raising your first undead earns *Rise!*

**Lost items:**
- If you lose the compass, the trader gives you a new one for free.
- If you lose the Rift Key, you bring the key materials again. Until you've entered the Dross, he reminds you that he can forge another.
- The Rift Key is fireproof and never despawns.

---

## The Dross (the dimension)

- **Dimension ID:** `dross:dross`, shown in game as **"The Dross"**.
- **Terrain:** a flat world with its own biome, defined in data files (`data/dross/dimension/`, `dimension_type/`, `worldgen/`).
- **Time:** permanent night (fixed at time 18000). The sun never rises.
- **Mobs:** normal hostile spawn rates, but no slimes, creepers or spiders.
  - **Zombies** spawn in full netherite armor with a netherite sword.
  - **Skeletons** spawn in full netherite armor and keep their bows.
  - None of it is enchanted, and none of it drops when they die.
  - These geared mobs are the only source of dropped Deathforged books (see Enchantments).
- **No other way out:** no portal can be lit inside the Dross. The Rift Key does nothing there, and flint and steel or fire can't light a nether portal there either. The hub's portal is the only exit.
- **Safe zone:** no hostile mobs spawn naturally within 48 blocks of the hub center. Mobs can still walk in from the dark.

---

## The portal

### The frame
- **Dross Portal Frame** (`dross:dross_portal_frame`): dark stone with glowing electric blue cracks.
- It can't be crafted, mined or blown up, and it drops nothing. It has a block item for testing, only in the creative Dross tab (or with `/give`).
- Every frame block "leaks" the Dross: drifting blue particles and a low ambient hum, even when the frame is unlit.
- Flint and steel does nothing to it, and normal obsidian portals stay purple nether portals.

### Lighting it with the Rift Key
- Throw (Q) a **Rift Key** into the empty middle of a complete frame, along X or Z. The key is used up and the frame fills with the **Dross Portal** (`dross:dross_portal`).
- If the frame is already lit, the key pops back out instead of being used, and it won't drift into the portal.
- A netherite ingot doesn't light anything.

### Look
Electric blue portal texture, blue particles, and a blue swirl over your screen while you stand inside.

### Travel
- Every Dross portal in the Overworld leads to the **hub** (the Shattered Spire at Dross 0,0).
- The hub's exit portal always leads back to the **Fallen Cathedral's** portal, wherever you came in.
- All entities can use it. Nothing is ever placed inside blocks or above a drop.

---

## The Fallen Cathedral (the portal site)

The cathedral is the only way into the Dross, and it's built once per world.

- **Where:** a random spot **3,000–10,000 blocks from world spawn**, chosen when a new world starts and saved with the world.
  - **Allowed biomes:** plains, sunflower plains, desert, taiga (including old growth and snowy taiga), forest, flower forest, birch forest, dark forest, snowy plains and ice spikes.
  - **Avoided:** ocean, river, beach, swamp, water near the footprint, and villages.
  - The natural ground under it must be nearly flat to begin with: 5 blocks of variation or less, with a few small dips allowed.
  - The search runs in the background using the world generator's own maths, then the best spot is checked in the real world. If nothing passes, the rules relax step by step, so every world gets a site. It takes about a minute after a new world starts.
  - When it's built, a chat message gives its location. You see the message again each time you join the world.
- **What:** a huge ruined cathedral, 61 × 87 blocks. Its heart is an unlit, unbreakable portal frame with a 3 × 5 opening, facing south. Glowing blue cracks, broken frame shards and dead ground show the Dross leaking out.
- **Fitting into the land:**
  - The footprint is levelled by cutting or filling a few blocks, with no big foundations.
  - Around it the ground steps back down to the natural terrain **one block at a time**, so there are no cliffs or dirt walls.
  - New ground keeps the local top layer (grass, sand, snow...).
  - Only trees that touch the cathedral or the reshaped ground are removed, always whole, so there are no floating leaves. The rest of the forest is left alone.
- **The leak:** within about 16 blocks, flowers and grass die or disappear, some grass turns to coarse dirt, and stone bricks crack.
- **Fallback:** if the cathedral can't be built, a small ruined stone-brick shrine around a 4 × 5 frame is built instead.

The site's position and the frame's direction are available to the rest of the mod through `world/PortalSite` (frame corner, axis, opening center and size). The compass, `/dross site` and the hub's exit portal all use it.

---

## The hub: the Shattered Spire

- Built once per world at **Dross 0,0**: a tall, broken spire.
- Its portal frame is lit as the **exit portal**, facing south.
- You arrive inside the spire's hall, on the top step in front of the exit portal, facing the gate.
- **Arrival sequence**, every time you come through the portal:
  - After 1 second, the title **"The Dross"** fades in, in electric blue.
  - Four piano notes play, "dun, dun, DUN, dunnn" (E, D, C#, then a low F#). Only you hear them.
  - Two seconds after the last note, you get the *Enter the Dross* advancement.
  - On your **first arrival only**, you get the **Dross Guide Book** (dropped at your feet if your inventory is full).
- **Fallback:** if the spire can't be built, an 11 × 11 platform with a lit 4 × 5 exit portal is built instead.

---

## The Dross Trader

An old villager (`dross:dross_trader`) who knows about the rift. He wears an original electric blue skin, **The Archivist**: a bald scholar with glowing spectacles and a blue book.

### Where he lives
- He spawns **once per world**, in his own **Rift Chapel** at the edge of the nearest village to world spawn that has room for it.
- **Finding a village:** he checks the 5 nearest villages within 1,000 blocks first. If none has room, he tries the next 5, widening the search 1,000 blocks at a time up to 5,000. The log says which village was picked and why.
- **No usable village:** if none is found (for example with Generate Structures off), the chapel goes on the nearest flat spot near spawn, with its door facing spawn.
- When he spawns, a chat message gives his chapel's location. You see the message again each time you join the world.
- **The Rift Chapel** (9 wide, 13 deep, 18 tall):
  - **Outside:** deepslate and stone brick walls with lapis, buttresses, tall blue windows, a blue rose window over the dark oak door, and a steep roof. On top are an open belfry with a soul lantern and a spire with a copper lightning rod.
  - **Inside:** pews, blue banners and soul lanterns. Behind the altar is a broken piece of a real Dross portal frame, which leaks and hums but can't be lit.
- **Placement:**
  - The chapel sits right next to the village's houses without touching any of them. Its door faces the nearest street.
  - A 3-wide path in the village's own path material (dirt path, or smooth sandstone in desert villages) leads from the door to that street.
  - Small bumps under it are levelled, up to 3 blocks each way.
  - The lightning rod draws natural lightning within 128 blocks to the spire, which keeps it away from the village.

### Behavior
- **Wandering:** he wanders within 50 blocks of his chapel and opens his door.
- **Teleporting home:** he teleports back to the chapel aisle (with blue enderman-style particles and sound) if any of these happen:
  - he gets more than 50 blocks away;
  - he falls more than 4 blocks;
  - he ends up underground.

  If he's riding something, he gets off first.
- **Can't die:** he can be hurt and knocked back, but at half health he teleports home and heals fully. He can't use portals.
- **Glow:** while any player is within his 50-block area, he glows electric blue, visible through walls.
- **Spawn egg:** "Dross Trader Spawn Egg" (creative tab). An egg trader treats the spot he was placed as his home, with the same rules.

### Shop
His shop opens only for players who have completed the quest.

| Offer | Price |
|---|---|
| Necromancy book (lowest level) | 32 emeralds + 1 book |
| Deathforged I book | 24 emeralds + 1 book |
| Dross Guide Book (only for players with *Enter the Dross*) | 3 books |

Unlimited uses, no restocking, no XP.

---

## Items

| Item | What it does |
|---|---|
| **Weathered Letter** | Starts the quest. A readable 4-page letter, found in every Nether Fortress and Bastion Remnant chest (added with a global loot modifier, so other mods' loot still works). It points to the trader with a rough location and rewrites itself with his real location once he has spawned. |
| **Dross Compass** | Given by the trader after the three tributes. Points at the Fallen Cathedral's portal and re-aims itself every second, so it stays correct. Spins in other dimensions. |
| **Rift Key** | Forged by the trader from the key materials. A crystal key with a blue heart. Throw it into a Dross frame to light the portal (it's used up). Fireproof, never despawns. |
| **Dross Guide Book** | Given on your first arrival in the Dross, and sold by the trader. A readable 5-page guide to the dimension: endless night, the safe hub, the netherite-clad dead, and no way out but the hub. |
| **Dross Portal Frame** | The frame block (creative tab or `/give` only). Can't be broken in survival and drops nothing. |
| **Dross Trader Spawn Egg** | Spawns a trader who treats that spot as home. |
| **Admin Sword** | A testing sword (creative tab). Kills anything in one hit (including the Ender Dragon and the Wither) while still counting as your kill. Unbreakable and fireproof. It never hurts your own risen undead, and it hits the trader like a normal sword. |

**The creative "Dross" tab** holds every item above, plus enchanted books for every level of Necromancy and Deathforged. Everything from the mod is only in this tab: the Dross enchanted books are kept out of vanilla's Ingredients and Combat tabs.

---

## Enchantments

Both are **swords only** and **treasure enchantments**. They never come from the enchanting table, loot chests, fishing or normal librarians.

### Necromancy (I–V)
*"Hits have a chance to raise the undead to fight for you."*

| Level | Chance per hit | Max alive | Soul capacity | Undead it can raise |
|---|---|---|---|---|
| I | 15% | 1 | 25 | zombie, skeleton |
| II | 20% | 2 | 50 | zombie, skeleton |
| III | 25% | 4 | 100 | + husk, stray, drowned |
| IV | 30% | 6 | 250 | + chicken jockey, spider jockey, skeleton horseman |
| V (admin, testing) | 100% | 10 | infinite | same as IV |

- **Which hits count:** only a melee hit with the sword in your main hand, on the main target of the swing. Sweep hits never count. Hitting a player, the trader or your own risen undead never raises any.
- **How many rise:**
  - If none of yours are alive, a successful hit raises the full max. Otherwise it raises half the max, rounded up, never going over the max.
  - Each one costs **1 soul**. Jockeys and horsemen cost 2.
  - The type is random from what your level unlocks.
- **Souls:**
  - Killing any zombie or skeleton (or a variant) with the sword stores 1 soul, up to the level's capacity, and a blue wisp flies from the mob into your hand. Risen undead give no souls.
  - Souls are stored **on the sword**, so each sword has its own count. The tooltip shows "Souls: X / Y" in gold ("Souls: ∞" at level V, which never spends souls).

### The risen undead
- **Real vanilla mobs:** they're normal zombies, skeletons and so on, owned by you, and they fight as a **pack**.
- **Choosing a target**, in order:
  1. anything attacking or targeting you;
  2. whatever you hit in the last 5 seconds;
  3. anything that hurt them;
  4. any fight another of your risen undead is in.

  Otherwise they follow you. Once fighting, they stick with their target until it dies, then help allies before coming back to you.
- **Who they leave alone:** they never hurt players, the trader, villagers, wandering traders or each other. Your own hits never hurt them.
- **Mobs fight back:** any mob they damage turns on them, even other undead.
- **Risen drowned** fight on land, day or night.
- **How long they last:** they crumble into blue particles after **60 seconds**, or early if you log out, die or change dimension.
- **What they never do:** drop items, give XP, pick things up, use portals or convert into other mobs. Normal burning rules apply.
- **Peaceful:** on Peaceful, Necromancy does nothing.

### Deathforged (I–X)
*"Your risen undead come armored. Higher levels forge stronger gear."* Does nothing without Necromancy on the same sword.

| Level | Gear on your risen undead |
|---|---|
| — (none) | No armor. Skeletons get a plain bow. |
| I | Leather armor |
| II | Gold armor |
| III | Chainmail armor |
| IV | Iron armor |
| V | Diamond armor |
| VI | Netherite armor |
| VII | Netherite armor + netherite sword (skeletons keep their bow) |
| VIII | + Protection II, Sharpness II / Power II |
| IX | + Protection IV, Sharpness V / Power V |
| X | + Strength I and Speed I for life, and a blue glow outline |

Mounts (chickens, spiders, horses) get no gear.

### Anvil rules
- **Deathforged I–V** combine like vanilla enchantments (I + I → II … IV + IV → V), up to V. Levels VI–X can't be combined, but a single VI–X book can go onto a sword without Deathforged.
- **Necromancy never levels up at an anvil.** Two Necromancy swords can be combined: the result keeps the higher level, and the souls from both swords add up, capped at its capacity.
- **Not allowed:**
  - Necromancy with Smite.
  - A combine that changes nothing.

### Getting them
- **From the trader**, after the quest: Necromancy (lowest level) and Deathforged I.
- **As drops in the Dross:** Deathforged books drop only from the netherite-clad zombies and skeletons of the Dross, when a player hit them recently. The chance is 5% per kill, plus 1% per Looting level. Higher levels are rarer (weights I–X: 25, 20, 15, 12, 9, 7, 5, 4, 2, 1).

---

## Advancements

All Dross advancements live in their own **Dross** tab, which stays hidden until you pick up a Weathered Letter.

| Advancement | How | Notes |
|---|---|---|
| Dross (tab root) | Pick up a Weathered Letter | Silent, opens the tab |
| A Weathered Letter | Pick up a Weathered Letter | |
| Proven Worthy | Bring the trader his three tributes | |
| Keymaster | Have the trader forge the Rift Key | |
| **Enter the Dross** | Step through the blue portal | Challenge, the main achievement |
| Rise! | Raise the undead with Necromancy | Challenge |

---

## The look

The Dross's color is **electric blue**: main `#2E6BFF`, deep cobalt `#0B2A9E`, highlight `#7FA8FF`. It's used for:
- the portal, its particles and screen swirl;
- the frame's cracks;
- the trader's glow and particles;
- the risen undead's wisps and puffs;
- the arrival title.

Every color lives in one place, `DrossColors.java`.

---

## Commands

All need cheats (operator level 2).

| Command | What it does |
|---|---|
| `/dross site` | Teleports you in front of the Fallen Cathedral's frame, facing it. Before the site is built, it says so instead. |
| `/dross hub` | Teleports you to the hub (the Shattered Spire) in the Dross. |
| `/dross trader` | Teleports you to the trader, even if his area isn't loaded. |
| `/dross trader home` | Sends the trader home. |
| `/dross quest status` | Shows your quest progress. |
| `/dross quest reset` | Clears your quest progress and the Guide Book flag, and revokes the Dross advancements (except *Rise!*). |
| `/dross quest complete` | Finishes the quest for you. |
| `/dross showcase huts` | Builds the 10 candidate hut designs in a row, with numbered signs. |
| `/dross showcase skins` | Lines up 10 frozen traders wearing the 10 candidate skins. Right-click one for its name. |
| `/dross showcase shrines` | Builds the 10 grand shrine designs in a row, with signs and clickable teleports in chat. |

---

## Building and running

From the project folder, on Windows:

| Task | Command |
|---|---|
| Build the mod jar (`build/libs/dross-<version>.jar`) | `.\gradlew.bat build --console=plain` |
| Run the game with the mod | `.\gradlew.bat runClient --console=plain` |
| Run the automated in-game checks | `.\gradlew.bat runGameTestServer --console=plain` |

- **Logs:** `run/logs/latest.log`. **Crash reports:** `run/crash-reports/`.
- **Commands in-game:** create the world with **Allow Cheats: ON** to use the commands.
- **First-time setup:** if it fails with `NoSuchFileException` under `.gradle/caches/forge_gradle/maven_downloader`, re-run the same command.

### Tools (Python)
- **`tools/shrines/`:** generates the 10 grand shrine designs, including the Fallen Cathedral and the Shattered Spire.
  - It writes their structure templates (`data/dross/structures/shrine/*.nbt`), renders the previews in `shrine-previews/`, and `gen_java.py` writes `ShrineDesigns.java`.
  - See `tools/shrines/README.md`.
- **`tools/skins/`:** draws the 10 trader skins (`textures/entity/trader/`) and renders the front and back previews in `skin-previews/`.

---

## Project layout

Base package: `com.reg21meme.dross`. Client-only code is in each area's `client` subpackage.

| Package | What's in it |
|---|---|
| `dimension` | The Dross dimension, mob gear, the hub's safe zone |
| `portal` | Frame and portal blocks, the Rift Key, lighting, teleporting, the hub, the arrival sequence |
| `world` | The portal site: location search, the Fallen Cathedral, ground fitting, the leak (`world/shrine` holds the shrine designs) |
| `villager` | The trader, his chapel and path, dialogue, hand-ins, shop, skins, the Dross Compass, the Admin Sword (`villager/hut` holds the hut designs) |
| `enchant` | Necromancy, Deathforged, souls, the risen undead, anvil rules, book drops |
| `quest` | The Weathered Letter and its loot, quest progress, advancements, the Guide Book |
| `command` | The `/dross` command and its showcases |
| `registry` | Every `DeferredRegister`: blocks, items, entities, particles, enchantments, loot modifiers, the creative tab |

Mod metadata is in `src/main/resources/META-INF/mods.toml`, filled from `gradle.properties`. All player-facing text is in `assets/dross/lang/en_us.json`.
