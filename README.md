# Dross

A Minecraft Forge mod that adds **the Dross**: a dangerous dimension of endless night, with its own forests, wastes and mountains, reached through an electric blue rift portal. Getting there takes a quest. You find an old letter in the Nether, prove yourself to a strange villager, and have him forge the key that opens the rift. The mod also adds two necromancy enchantments that raise the undead to fight for you.

The Dross is a **mid-to-late game** place. You reach it after surviving the Nether, the deep dark, the ocean and the End, not after beating every boss. There is one way in (the portal at the Fallen Cathedral) and one way out (the hub's portal).

| | |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.4.10 |
| Java | 17 |
| Mod ID | `dross` |
| Version | 0.1.0 |
| Needs | [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib) 4.8.4 or newer (for Forge 1.20.1) |

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
7. **The Dross.** Stepping through takes you to the **Shattered Spire**, the hub at Dross 0,0. The words "The Dross" fade in over four low piano notes. *Advancement: Enter the Dross*, the main achievement. On your first arrival you also get the **Dross Guide Book**. Two survivors, the Blind Oracle and the Ragged Seeker, keep watch at the spire.
8. **Exploring.** Round the hub lies the Silverwood Forest; further out are the Cinder Wastes, Blightwood, Rimefall, the Nether Scar and the Gravecrags' peaks. Somewhere in the Cinder Wastes a volcano holds the **Cinder Colossus**. *Advancement: Set in Stone.*
9. **Necromancy.** The trader sells the Necromancy and Deathforged enchanted books, and Deathforged books also drop in the Dross. Raising your first undead earns *Rise!*

**Lost items:**
- If you lose the compass, the trader gives you a new one for free.
- If you lose the Rift Key, you bring the key materials again. Until you've entered the Dross, he reminds you that he can forge another.
- The Rift Key is fireproof and never despawns.

---

## The Dross (the dimension)

- **Dimension ID:** `dross:dross`, shown in game as **"The Dross"**.
- **Terrain:** real land, surface only (no caves, except Blightwood's sewers, below): rolling hills, cliff steps and rocky knolls, mesas in the Cinder Wastes, and snow-capped mountain peaks (about y 230-260) in the Gravecrags. It's Minecraft's own terrain generator, set up in data files (`data/dross/dimension/`, `dimension_type/`, `worldgen/`) written by `tools/terrain/worldgen.py`, where every tuning number lives.
- **Six biomes**, regions about 600-670 blocks across:
  - **Silverwood Forest** (the main biome, always round the hub): a dense, dark forest of silverwood trees with glowing Stormvine, Glimmer Ferns, Wispblooms, mossy boulders, fallen logs and dark ponds.
  - **Cinder Wastes**: ash, basalt and magma, enclosed lava pools, basalt spires, charred trees, Emberweed and Cinder Vents. Home of the Cinder Colossus's volcano.
  - **Blightwood**: poison-green and maroon giant mushrooms on bone-beige stems, Gloomcaps, Sporepods, murky ponds and thick green fog. No zombies, skeletons or endermen: the mobs here are rats (Plague Rats, and about 1 in 5 Plague Ravagers) and the odd witch (7 in 100). Underneath are **the sewers** (below).
  - **Rimefall**: frozen ground, frost-covered dead trees with icicles, ice spikes, Frostblooms and frozen ponds.
  - **Nether Scar** (smaller patches, about 240 blocks across): netherrack with clusters of blue-cracked Scarred Netherrack, crimson and warped fungi, Soulwicks and Scar Vents.
  - **The Gravecrags**: the mountains, Cragstone cliffs with glowing Storm Lichen and Crag Thistles, snow on the peaks. Never near the hub.
- **Ores** under every biome, in Duskstone (the Dross's stone): coal, gold (drops nuggets), **Moonsilver** (Raw Moonsilver, smelted into Moonsilver Ingots) and **Stormite** (glowing Stormite Shards; needs an iron pickaxe). Moonsilver and Stormite are for gear that comes later.
- **Sky and fog:** each biome has its own night fog, sky and water colours, particles and ambient sounds, with a blue glow over the night sky and no clouds.
- **Old worlds:** chunks that were already generated flat stay flat; new chunks get the terrain, so there's a wall where they meet. Start a new world for the real thing.
- **Time:** permanent night (fixed at time 18000). The sun never rises.
- **Mobs:** normal hostile spawn rates, but no slimes, creepers or spiders. The **Cinder Wastes** are different: only the Cinder Colossus's **stone golems** spawn there, with **lava golems** (about 1 in 5, always next to lava). Wild golems don't crumble, and a wild lava golem only lights its fuse when you're close.
  - **Zombies** spawn in full netherite armor with a netherite sword.
  - **Skeletons** spawn in full netherite armor and keep their bows.
  - None of it is enchanted, and none of it drops when they die.
  - These geared mobs are the only source of dropped Deathforged books (see Enchantments).
- **No other way out:** no portal can be lit inside the Dross. The Rift Key does nothing there, and flint and steel or fire can't light a nether portal there either. The hub's portal is the only exit.
- **Safe zone:** no hostile mobs spawn naturally within 48 blocks of the hub center. Mobs can still walk in from the dark.

### The sewers under Blightwood

The Dross's only underground places. **Where they go:** Blightwood's patches are taken nearest the hub first, in
groups of 3. In every group, one patch has **the Rat King's sewer**, and each of the other two has a **regular sewer**
half the time. That repeats outwards forever, so there are many of both, never two in one patch.

**A regular sewer** has:

- **3 pipe entrances:** big green copper pipes, like Mario pipes, standing out of the ground with steps up to the
  rim. Jump in: you drop 34-58 blocks down a copper shaft into a deep pool of **murky green sewer water** that breaks
  your fall. A ladder up the inside wall takes you back out.
- **A maze of tunnels:** wide brick tunnels with a water channel down the middle, narrow side tunnels with cobwebs,
  pillared rooms, rough natural caves, loops and dead ends. It's dark down there, with a little glow lichen on the
  walls.
- **Sewer Sludge** in patches on the floors: a murky brownish-green mud that slows you down (less than soul sand).
- **Only rats spawn** in the sewers, 3 enraged Plague Ravagers to every 2 Plague Rats. The green water, thick fog and
  dripping come from the sewers' own underground biome, "The Sewers".

**The Rat King's sewer** looks different:

- **One grand entrance:** a huge copper pipe whose rim is shaped like his crown (bright copper points tipped with
  glowing green Blight Vents, bright copper bands, tattered red banners; no gold blocks). Round it the ground is poisoned: dead plants, sludge puddles, bones, rat nests of old
  carpet, cobwebs and gnawed fences. **Blight Vents** in and round the pipe glow poison green and puff spores, so it
  stands out at night.
- **The royal road:** the tunnel from the entrance to his cave has red banners, green candles, bones and scattered
  gold. There's no carpet, so it doesn't show you the way through the maze.
- **The rat cave:** a domed chamber about 40 blocks across, lit by green candles and glow lichen, copper pipes on its
  walls and ceiling pouring into drains, sludge patches all over its floor, and in the middle the **Rat King's nest**
  of piled carpets, surrounded by his hoard of gold (about 70% Dross gold ore, 30% gold blocks). He's **asleep there
  when you arrive** and wakes as you come near, like in his fight. Plague Ravagers that spawn in the rat cave are
  tougher than usual (1.5 times the health and bite).

`/dross goto lair ratking` takes you to the nearest rat cave and `/dross goto sewer` to the nearest regular sewer
(Minecraft's `/locate` can't find them). Sewers only appear in newly generated chunks.

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

The site's position and the frame's direction are available to the rest of the mod through `world/PortalSite` (frame corner, axis, opening center and size). The compass, `/dross goto cathedral` and the hub's exit portal all use it.

---

## The hub: the Shattered Spire

- Built once per world at **Dross 0,0**: a tall, broken spire, levelled into the forest. The ground round it steps back to the natural land at most 1 block at a time (no cliffs or dirt walls), and only trees touching it are removed, each one whole.
- Its portal frame is lit as the **exit portal**, facing south.
- You arrive inside the spire's hall, on the top step in front of the exit portal, facing the gate.
- **Arrival sequence**, every time you come through the portal:
  - After 1 second, the title **"The Dross"** fades in, in electric blue.
  - Four piano notes play, "dun, dun, DUN, dunnn" (E, D, C#, then a low F#). Only you hear them.
  - Two seconds after the last note, you get the *Enter the Dross* advancement.
  - On your **first arrival only**, you get the **Dross Guide Book** (dropped at your feet if your inventory is full).
- **Fallback:** if the spire can't be built, an 11 × 11 platform with a lit 4 × 5 exit portal is built instead.

### The survivors
Two survivors of the Dross live at the spire (`dross:survivor`): **the Blind Oracle**, the mentor, and **the Ragged Seeker**, his student. They wear two of the Dross skins drawn for the trader's villager model (8 and 3).
- They start on the dais beside the arrival step and walk together in and around the spire and out of its gate, never more than 20 blocks outside its walls.
- Right-click one and he says his next line in chat.
- Like the trader, they can be hit and knocked back but never die: at half health they heal and pop back to their spots. Hostile mobs ignore them, and Necromancy never raises undead from hitting them.
- No trades.

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
| **Rage Wand** | A creative-only testing wand with five **teams** (blue, red, green, yellow, purple: right-click to switch; the crystal shows the colour). Hit a mob with it (no damage) to give it **Rage** and the wand's team: mobs of different teams fight each other, never their own team, and ignore players while an enemy team is near. Teams and Rage last forever, not even milk cures them. Mobs use their own attacks; animals with none headbutt (1 heart). Bosses work too, and their minions join their team. See [The Rage Wand](#the-rage-wand). |
| **Cinder Colossus Trophy** | A one-block miniature of the Cinder Colossus, in one of five poses (the tooltip names it). Place it on the floor and it faces you; right-click it to change its pose (1 to 5, then round again). Its lava glows and keeps flowing down it. The boss drops one for every player who helped beat it. Fireproof, never despawns (see [The Cinder Colossus](#the-cinder-colossus-mini-boss)). |
| **Rat King Trophy** | A one-block miniature of the Rat King in one of five poses (the tooltip names it). Right-click a placed one to change its pose. The Rat King gives one to every player who helped beat him. Fireproof, never despawns (see [The Rat King](#the-rat-king)). |
| **Stone Golem / Lava Golem Spawn Eggs** | Spawn a wild stone or lava golem (like the ones in the Cinder Wastes): it doesn't crumble, and a lava golem only blows up when you get close. |
| **Dross terrain blocks** | The 38 blocks of the Dross's land: Duskstone and its four ores; the silverwood wood set (log, wood, stripped log and wood, planks, leaves, sapling; four saplings in a square grow a great silverwood) and Stormvine; and each biome's ground covers and plants (Silvermoss, Glimmer Fern, Wispbloom; Ash, Ash Layer, Charred Log, Emberweed, Cinder Vent; Sporeloam, Blightcap blocks, Gloomcap, Sporepod; Hoarfrost, Frostbitten Log, Frostbloom, Icicle; Scarred Netherrack, Scar Roots, Soulwick, Scar Vent; Cragstone, Storm Lichen, Crag Thistle); and from Blightwood's sewers **Sewer Sludge** (it slows you down, less than soul sand) and the **Blight Vent** (glows poison green and puffs spores). |
| **Raw Moonsilver, Moonsilver Ingot, Stormite Shard** | Materials from the Dross's ores, for gear that comes later. Raw Moonsilver smelts into an ingot. |

**The creative "Dross" tab** holds every item above, plus enchanted books for every level of Necromancy and Deathforged. It's grouped by kind, in this order: **blocks** (the portal frame, then the terrain blocks by biome), **quest items** (Weathered Letter, Dross Compass, Rift Key, Guide Book), **trophies** (all five Colossus trophy designs, then the Rat King's five), **spawn eggs** (the trader, the Cinder Colossus, the stone golem, the lava golem, the Rat King, the Plague Rat, the Plague Ravager), **weapons and tools** (the Admin Sword, the Rage Wand), the **enchanted books** (Necromancy I-V, then Deathforged I-X), then the **materials** (Raw Moonsilver, Moonsilver Ingot, Stormite Shard). Everything from the mod is only in this tab: the Dross enchanted books are kept out of vanilla's Ingredients and Combat tabs.

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

- **Which hits count:** only a melee hit with the sword in your main hand, on the main target of the swing. Sweep hits never count. Hitting a player, the trader, the hub's survivors or your own risen undead never raises any.
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
- **Who they leave alone:** they never hurt players, the trader, the hub's survivors, villagers, wandering traders or each other. Your own hits never hurt them.
- **Mobs fight back:** any mob they damage turns on them, even other undead.
- **Risen drowned** fight on land, day or night.
- **How long they last:** they crumble into blue particles after **60 seconds**, or early if you log out, die or change dimension.
- **What they never do:** drop items, give XP, pick things up, use portals or convert into other mobs. Normal burning rules apply.
- **Peaceful:** on Peaceful, Necromancy does nothing.

### Deathforged (I–X)
*"Your risen undead come armored. Higher levels forge stronger gear."* Does nothing without Necromancy on the same sword.

| Level | Armor (all types) | Sword (zombie types) |
|---|---|---|
| — (none) | none | none |
| I | Leather | none |
| II | Leather | Wooden |
| III | Gold | Gold |
| IV | Chainmail | Stone |
| V | Iron | Stone |
| VI | Iron | Iron |
| VII | Diamond | Iron |
| VIII | Diamond | Diamond |
| IX | Netherite | Netherite |
| X | Netherite | Netherite, plus Strength I and Speed I for their whole life |

- **Nothing is enchanted**, and none of the gear drops.
- **Skeleton types** (skeletons, strays, the riders of spider jockeys and horsemen) always hold a plain bow instead of a sword.
- **Mounts** (chickens, spiders, horses) get no gear.
- Level X's effects show no swirling particles, and they don't glow.

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

## The Cinder Colossus (mini-boss)

A golem mini-boss that serves the Fire Necromancer: a huge, gorilla-like brute about 5.5 blocks tall, hunched over and
walking on its knuckles, with fists bigger than its legs, a cracked face, a layered jaw (an outer jaw and an inner
one behind it), horns and spikes, and **volcanoes on its back**. After three rounds of designs, the user picked
**3G Saddleback**, a variation of Old Calderon (No. 3 of the first five designs): two great volcanoes grow out of its
back and merge into one saddle of rock, big obsidian spikes stand in the saddle and down its lower back, it has horns,
spikes pointing up off its shoulders and a beard of obsidian shards, all in dark scorched stone. Its volcanoes erupt
out along their own angle, and its craters always hold glowing lava. Previews are in `boss-previews/cinder_colossus/`
(`all_golems.png`, `01_saddleback_sheet.png`, the fight page `01_saddleback_fight.png` and `minions.png`; the other
candidates are in `boss-previews/cinder_colossus/round1/` to `round3/`).

It lives in its **lair**, a volcano in the Cinder Wastes (below). For testing you can also get one from the
**Cinder Colossus Spawn Egg** (Dross creative tab) or `/dross boss summon colossus`, and `/dross boss arena colossus` builds a
placeholder arena to fight it in: a round floor of red nether bricks, 31 blocks across, with a low wall (four gaps)
and lights set in the floor.

### The lair
- **Where:** there are many lairs, one in **every group of 3 Cinder Wastes patches**: the 3 patches nearest the hub
  hold one lair, the next 3 nearest another, and so on outwards forever (never two in one patch; the other two patches
  of each group are kept for future fire bosses). A lair is built in the background the first time you come within
  about 384 blocks of it. `/dross goto lair colossus` takes you to the nearest one's entrance (building it first if
  it isn't yet).
- **Outside:** a volcano rising about 40 blocks over its arena, with a jagged crater that smokes and throws embers, lava
  streams and pools round its foot (they never spread), and a big cave mouth with obsidian teeth. The way in never
  steps more than 1 block: on steep land a rock ramp leads to the mouth.
- **Inside:** a domed cavern about 41 blocks across under the crater, with an obsidian and magma floor, lava glowing
  through sealed fissures below it, and obsidian stalactites. No lava can be reached from the floor, and nothing
  spawns inside by itself.
- **The boss:** the Colossus **rises when a player steps onto the floor** (not on Peaceful). Once it's beaten, that
  lair stays empty for good: no respawn.
- Previews: `boss-previews/cinder_colossus/lair_outside.png` and `lair_cutaway.png`. Numbers: `boss/lair/LairTuning.java`.

### The fight
It has **300 health** and a boss bar of its own (drawn by the mod: Minecraft's bars have no orange), shown to players
within 40 blocks. The fight has three stages:
1. **Shell** (down to 70% health; the bar is obsidian purple): heavy, but not slow to follow you (about 1.5 blocks a
   second; 2 in the Cracking stage and 2.8 in the Lava Core stage). It swipes at you up close, tears rocks
   out of the ground and throws them (they shatter; they never place a block), raises both fists for a moment and
   slams the ground (the shockwave hurts and throws you about 20 blocks), and charges on all fours, head first, from up
   to 32 blocks away, running all the way to where you stood half a second before it set off.
2. **Cracking** (70% to 35%; purple with glowing orange cracks): its volcanoes erupt (it can't be hurt while they do)
   and its shell starts breaking up, plates knocked loose and lava glowing through, more with every hit. A bit faster
   and more aggressive. Its volcanoes also spit **lava blobs** that land near you (never on you) and leave small pools
   of lava for 20-40 seconds, and it pounds the ground to raise **three stone golems** (left, right, in front of it). As it enters this stage,
   **four stone golems** climb out at once, and from now on its charge leaves a short **trail of fire**.
3. **Lava Core** (35% to 0%; lava red): its shell bursts off (it can't be hurt while it does). Faster and angrier:
   **every hit sets you on fire**, its charge leaves a longer **trail of fire**, it stops throwing rocks, and it raises
   **lava golems**, which blow up a few seconds later unless you kill them first. Its lobs now spit **four** lava
   blobs instead of two. As it enters this stage its volcanoes spew **four lava blobs** round you.

**Its damage** (in hearts, Shell / Cracking / Lava Core, on Normal with no armour): swipe 6.5 / 7.5 / 8, charge
6 / 6.5 / 7, slam 3.5 / 4 / 5, thrown rock 4.5 / 5.2 / none, a lava blob landing right on you 3.5 / 3.9 (it lands
near you, never aimed at you). In the Lava Core every hit also sets you on fire for 5 seconds.

**The roar:** the first time it's hurt, and at both stage changes, it roars and a ring of dust throws every player
within 15 blocks back about 15 blocks. The first one does no damage; the eruption's roar takes 1 heart and the
shell bursting's 2.

**How it picks a move:** by how far away you are (close, mid, far, very far) and its stage, with the chances in
`ColossusTuning` (the user's table): up close it swipes and slams, further out it charges, throws and lobs, and very
far away it mostly charges.

**Against Necromancy:** with your risen undead nearby it raises one golem for every two of them (never more than 6),
its golems go for the risen undead first, and when three or more of them crowd it, it slams more often.

It's a slow stone brute, so it can't be beaten easily from afar: **arrows and other projectiles** (tridents,
fireballs, snowballs, your risen skeletons' arrows) deal only **50%** of their damage in the Shell stage, **35%** in
Cracking and **15%** in the Lava Core. You can tell: they clink off the stone with a few sparks, and in the Lava Core
they hiss and burn up in a puff of smoke. Melee hits are never reduced, and its minions take full damage from
anything. The minions are quick on their feet: stone golems walk about 3 blocks a second and lava golems about 4.6
(a player walks 4.3 and sprints 5.6). **Pickaxes crack the shell:** in the Shell and Cracking stages a pickaxe hit
(any pickaxe, modded ones too) does **twice** its damage; in the Lava Core it's a normal hit. The Colossus keeps facing
the player it's fighting nearly all the time, except while it gallops.

A big hit never skips a stage: its health stops at 70% and at 35%. When it dies it screams, sinks down and **cools
into an obsidian statue**, which stands for 2 minutes and then crumbles; its minions crumble with it. It drops 50
experience. The player who killed it, and everyone within 40 blocks, get the challenge advancement **"Set in Stone"**
and **a trophy each** (below). Removed with `/kill`, it gives neither.

Its lava pools and fire trail are the mod's own blocks, not real lava and fire: they never spread, can't be picked up
with a bucket, never make cobblestone or obsidian, never replace anything but air (or, with the mob griefing rule on,
grass, flowers and thin snow), never touch chests or other blocks with contents, and clear themselves away, even if
the area is unloaded and loaded again. Nothing in the fight breaks blocks. Its sounds are vanilla stone, iron golem,
ravager and lava sounds pitched down: deep rumbles and grinding stone.

All its numbers (health, damage, speeds, cooldowns, ranges, move chances, the roar, minion counts, pool size and time,
the stage thresholds, the trophy design it drops, the wild golems' spawning) are labeled constants in `boss/golem/ColossusTuning.java` and `ColossusStage.java`.

### The trophies
The **Cinder Colossus Trophy** (`dross:colossus_trophy`) is a decorative block: a miniature of the boss, its own model
scaled down to fit one block (about a sixth of its size), with no base or pedestal. It has five designs, each a
different pose and look (previews: `boss-previews/cinder_colossus/trophies.png`), and every trophy can show any of them:

| No. | Name | Pose | Look |
|---|---|---|---|
| 1 | Volcano's Wrath | the volcano lob, lava spurting from both craters | erupted (lava over the shell) |
| 2 | Molten Charge | mid-gallop on all fours, head first | lava core |
| 3 | The Roar | reared up roaring, both fists raised high | erupted |
| 4 | Earthshaker | the ground slam, as its fists hit | cracking (plates loose, bright cracks) |
| 5 | Dormant Shell | crouched in its shell, head bowed | dormant (dim) |

- Its lava cracks glow softly, and the lava keeps **flowing down its body**: bright bands of fresh lava slide down the
  streams over and over. Now and then **lava drips** off it and its craters smoke (just particles: nothing catches
  fire). It gives off a little light (more for the lava ones).
- Place it **on the floor** (it needs a block under it) and it **faces you**.
- **Right-click it to change its pose:** each click turns it to the next design (1, 2, 3, 4, 5, then 1 again), still
  facing the same way, with a quiet stone click and a little puff of ember dust. An empty hand or any item works (main
  hand); holding a block, you change the pose instead of placing the block, so **sneak** to place a block against it.
  In adventure mode it can't be changed. The tooltip says "Right-click to change its pose".
- Any tool, or a bare hand, breaks it, and it drops itself with the design it's showing. It never burns and explosions can't break it.
- **The drop:** when the Colossus is beaten, every player within its boss bar's range (40 blocks), plus the player who
  killed it, gets **their own trophy**, straight into their inventory. If their inventory is full, it's dropped one
  block in front of them instead (at their feet if a wall is there), and a player who's dead at that moment finds it
  where they died. A dropped trophy can only be picked up by its player, never despawns, and fire, lava, explosions
  and cactus can't destroy it. It drops as **design 1** (`DROP_DESIGN` in
  `ColossusTuning`); since any trophy can be turned to any pose, there's no single final design to pick.
- **`/dross showcase trophies colossus`** puts all five side by side on pedestals, each with a sign (they can be right-clicked
  too). The creative Dross tab has all five too.
- The figures are made by `tools/golems` (`kit/trophies.py`) from the boss's own model, poses and textures; the mod's
  code is in `boss/trophy`.

### The showcase
`/dross showcase boss_designs colossus` spawns it frozen (it never fights) so you can look at it and play its
animations: the dormant shell, the eruption, the shell breaking off, the lava core's endless eruption, the walk and the death.

---

## The Rat King

The Rat King is one of the Plague Necromancer's three servants: a lean, sly giant rat standing upright, 3 to 4 blocks
tall, with a staff, a crown and a red cape. **His look, sounds, fight and trophies are built** and play-tested. After two rounds
of designs the user picked **Gnawrick, build 1C**: sewer grey-green, broad shoulders and a wide chest, a cocked
three-point crown, a ragged moth-eaten cape, and a twisted staff whose green claw holds a glowing green orb.

He sleeps curled up on the ground (with floating Zzz and snores), wakes up and picks up his staff, stands, walks on
tiptoe, burrows into the ground (the dirt mound is where his head went in) and pops back up out of it, flies into a
rage (stomping, pumping his fist in the air, red eyes, steam out of his ears with a choo-choo train whistle, smashing
his staff into the ground) and dies a silly death (flat on his back, tongue out, X eyes). His staff never goes through
him, his ears or the floor. His rats: the **Plague Rat** and the bigger, enraged **Plague Ravager**, each with an idle
and a run. All three have spawn eggs in the Dross tab.

All their sounds are original (made by `tools/ratking/sounds.py`, nothing from Minecraft), including several versions of
each attack sound for the fight, to pick from later.

**The fight** (every number is in `RatKingTuning`):
- **Asleep:** he starts curled up asleep. Come within 12 blocks (8 if you sneak) or hit him and he wakes (that hit does
  no damage, and he can't be hurt while he gets up). Then his boss bar appears: a rusty sewer pipe with a rat peeking
  over it (the user's pick of nine designs), shown within 40 blocks.
- **200 health**, armour 6, quick on his feet (about 4.7 blocks a second, 6.2 in his Rage; you walk at 4.3). He keeps
  facing you.
- **Staff sweep** up close: 4.5 blocks in front of him, 9 damage (11 in his Rage), with a strong knockback, about
  every second.
- **Spear charge** from 5 to 24 blocks: he crouches for 0.4 s with his staff levelled like a spear, then dashes at
  9 blocks a second (11 in his Rage) to where you were standing. If he catches you it's 6 damage (8) and straight into
  a sweep; if you dodge, he skids and needs half a second to turn round.
- **Poison shot** from 5 to 24 blocks, now and then: a clumpy plague ball that bursts into a small poison cloud (a
  steady Poison I while you stand in it); in his Rage, a fan of three.
- **How often:** at 5-16 blocks he charges 60%, throws poison 25% and runs in 15% (Rage: 60 / 35 / 5); at 16-24
  blocks charge 40, poison 30, run in 30 (Rage: 35 / 35 / 30); up close always the sweep.
- **Burrow:** every 25 health he loses (at 175, 150 and 125 of his 200), he digs and dives into the ground. While he
  tunnels towards you (a dirt trail moves over the ground) he can't be hurt, and his rats burst out of the ground round
  you: **10, then 12, then 14** Plague Rats, however many of his rats are already about. Then he pops straight up
  4 blocks from you, facing you, without knocking you back.
- **Rage** at half health, 100 (he can't be hurt while it plays): stomping, red eyes, steam and the train whistle.
  Then he's faster and hits harder, throws three balls at once, and burrows three more times (at 75, 50 and 25),
  calling **8, then 9, then 10 Plague Ravagers**, whose bites poison for 2.5 seconds.
- **Death:** a silly death; his rats crumble, he vanishes after 10 seconds; no drops except a trophy (below). The advancement **Down the Drain**
  ("End the Rat King's reign beneath Blightwood.") goes to whoever killed him and everyone within 40 blocks.
- His rats: summoned ones attack you and crumble when he dies. A wild Plague Rat (spawn egg) never attacks; a wild
  Plague Ravager does.
- `/dross boss arena ratking` builds a round dirt arena round you with a chat button to summon him;
  `/dross boss summon ratking` summons him asleep 14 blocks in front of you. His spawn egg makes a sleeping one too.
- **Trophies:** beating him gives everyone who helped a **Rat King Trophy**, a one-block miniature of him (his orb
  glows). It comes as *Belly Up*; right-click a placed one to change its pose: 1 Fast Asleep, 2 Tantrum, 3 Ground
  Pound, 4 Pop Goes the King, 5 Belly Up (the user's five picks from ten). It works like the Colossus trophy: it goes
  straight into your inventory (or drops in front of you, kept for you), and it's fireproof and never despawns.
  `/dross showcase trophies ratking` places all five in a row.

- `/dross showcase boss_designs ratking` spawns him frozen with a sign; the chat buttons (or
  `/dross showcase boss_designs ratking sleep|wake|idle|walk|burrow|popup|rage|charge|die`) play an animation, and
  right-clicking him plays his next one (sneak + right-click: he dies).
- `/dross showcase rats` puts the two rats on pedestals; `idle` / `run` switch them.
- `/dross showcase boss_sounds ratking` lists every sound with a button that plays it.
- Previews: `boss-previews/rat_king/` (the rats in `rats/`, the sounds in `sounds/`: open `index.html` to listen;
  the earlier rounds in `round1/` and `round2/`). They're made by `tools/ratking/`.

---

## The Rage Wand

A creative-only testing tool in the Dross tab (after the Admin Sword), with five **teams**: **right-click** to switch
the wand between Blue, Red, Green, Yellow and Purple (its crystal changes colour and the team name shows above your
hotbar). Hitting a mob with it does no damage; it gives the mob **Rage** and the wand's team (dust in the team's
colour floats over it). Hit it again with another colour and it switches team. Rage and teams never wear off and
can't be cured, not even with milk.

- Raged mobs **never fight their own team**. Each goes for the nearest raged mob of another team within 40 blocks,
  and while an enemy team is near they **ignore players**, even ones who hit them. With no enemy near they behave
  normally but keep their team, so the winners stay ready for the next fight.
- No new animations: every mob uses its own attacks (zombies swing, skeletons shoot, creepers explode, golems smash).
  Animals with no attack of their own (cows, pigs, sheep, chickens, villagers...) **headbutt** for 1 heart.
- **Bosses** work too: raging the Cinder Colossus makes it fight; raging the sleeping Rat King wakes him. Their golems
  or rats (the ones already out and every one they summon) **join their team**, never fighting them or each other.
- It won't rage players, showcase mobs, the Ender Dragon, the trader or the hub's survivors.

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
| Set in Stone | Defeat the Cinder Colossus | Challenge, under *Enter the Dross*. Goes to the player who killed it (their arrows, pets and risen undead count) and everyone within 40 blocks when it dies |
| Down the Drain | End the Rat King's reign beneath Blightwood | Challenge, under *Enter the Dross*. Goes to the player who killed him and everyone within 40 blocks when he dies |

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

All need cheats (operator level 2). Each name says what kind of thing it's about and which one, so the list stays
clear as more bosses and places are added: `goto <place>`, `showcase <kind> [<boss>]`, `boss <action> <boss>`,
`trader <action>` and `quest <action>`.

| Command | What it does |
|---|---|
| `/dross goto cathedral` | Teleports you in front of the Fallen Cathedral's frame, facing it. Before the site is built, it says so instead. |
| `/dross goto hub` | Teleports you to the hub (the Shattered Spire) in the Dross. |
| `/dross goto trader` | Teleports you to the trader, even if his area isn't loaded. |
| `/dross goto biome <name>` | Teleports you to safe ground in the nearest example of a Dross biome: `silverwood_forest`, `cinder_wastes`, `blightwood`, `rimefall`, `nether_scar` or `gravecrags`. |
| `/dross goto lair colossus` | Teleports you to the nearest Cinder Colossus lair's entrance. If it isn't built yet, it says so, builds it, and teleports you once it's ready. |
| `/dross goto sewer` | Teleports you to the foot of the nearest regular sewer's pipe, facing it. |
| `/dross goto lair ratking` | Teleports you into the nearest rat cave under Blightwood, at a tunnel mouth facing the Rat King's nest, and tells you whether he's asleep there, awake, or already beaten (he never comes back). |
| `/dross trader sendhome` | Sends the trader home. |
| `/dross quest status` | Shows your quest progress. |
| `/dross quest reset` | Clears your quest progress and the Guide Book flag, and revokes the Dross advancements (except *Rise!*, *Set in Stone* and *Down the Drain*). |
| `/dross quest complete` | Finishes the quest for you. |
| `/dross showcase boss_designs colossus` | Spawns the Cinder Colossus (3G Saddleback), frozen (dormant), with its sign and clickable controls in chat. Right-click it to move it on a stage; sneak + right-click to make it die. |
| `/dross showcase boss_designs colossus dormant` / `erupted` / `core` | Puts every showcase golem nearby into that stage. |
| `/dross showcase boss_designs colossus erupt` / `break` / `die` | Replays the volcanoes erupting / the shell breaking off / the death (cooling into an obsidian statue). |
| `/dross showcase boss_designs colossus walk` / `idle` | Knuckle-walking on the spot / standing. |
| `/dross showcase trophies colossus` | Places the five Cinder Colossus trophy designs in a row in front of you, each on a pedestal facing you with a sign giving its number and name. Running it again replaces the row. |
| `/dross showcase trophies ratking` | Places the Rat King's five trophy designs in a row in front of you, each on a pedestal facing you with a sign. Running it again replaces the row. |
| `/dross showcase boss_designs ratking` | Spawns the Rat King (Gnawrick 1C) frozen, with signs and clickable animation buttons in chat. Right-click one to play his next animation; sneak + right-click: he dies. |
| `/dross showcase boss_designs ratking sleep` / `wake` / `idle` / `walk` / `burrow` / `popup` / `rage` / `charge` / `die` | Plays that animation on every showcase Rat King nearby (rage turns their eyes red until they sleep). |
| `/dross showcase rats` | Lines up the Rat King's two rats on pedestals with signs: the Plague Rat and the Plague Ravager. |
| `/dross showcase boss_sounds ratking` | Lists every Rat King and rat sound in chat, each with a [play] button. |
| `/dross showcase rats idle` / `run` | Every showcase rat nearby idles / runs on the spot. |
| `/dross boss summon colossus` | Summons a fighting Cinder Colossus 10 blocks in front of you, facing you (not on Peaceful). |
| `/dross boss arena colossus` | Builds a placeholder boss arena round you (a 31-block round floor of nether bricks, with a low wall and lights), with a chat button to summon the Colossus. |
| `/dross boss summon ratking` | Summons a fighting Rat King, asleep, 14 blocks in front of you, facing you (not on Peaceful). |
| `/dross boss arena ratking` | Builds the Rat King's placeholder arena round you (a 31-block round floor of dirt and coarse dirt), with a chat button to summon him. |

---

## Building and running

From the project folder, on Windows:

| Task | Command |
|---|---|
| Build the mod jar (`build/libs/dross-<version>.jar`) | `.\gradlew.bat build --console=plain` |
| Run the game with the mod | `.\gradlew.bat runClient --console=plain` |
| Run the automated in-game checks | `.\gradlew.bat runGameTestServer --console=plain` |
| The same checks in a brand-new world | `.\gradlew.bat runGameTestServer -PgameTestWorld=world_check_1 --console=plain` (pick a folder name under `run/` that doesn't exist yet) |

- **Logs:** `run/logs/latest.log`. **Crash reports:** `run/crash-reports/`.
- **Commands in-game:** create the world with **Allow Cheats: ON** to use the commands.
- **First-time setup:** if it fails with `NoSuchFileException` under `.gradle/caches/forge_gradle/maven_downloader`, re-run the same command.

### Tools (Python)
- **`tools/shrines/`:** generates the two grand shrine designs, the Fallen Cathedral and the Shattered Spire (the user's picks from ten; the other eight were removed).
  - It writes their structure templates (`data/dross/structures/shrine/*.nbt`), renders the previews in `shrine-previews/`, and `gen_java.py` writes `ShrineDesigns.java`.
  - See `tools/shrines/README.md`.
- **`tools/skins/`:** draws the 4 villager-model skins (`textures/entity/trader/`: 1 Hooded Watcher, kept for later; 2 The Archivist, the trader; 3 Ragged Seeker and 8 Blind Oracle, the survivors) and renders the front and back previews in `skin-previews/`.
- **`tools/terrain/`:** the Dross's terrain and blocks.
  - `worldgen.py` writes all the terrain data files (noise, biomes, ground cover, features, ores, spawn lists); every tuning number is a labeled constant at its top.
  - `draw_blocks.py` draws the terrain blocks' textures and `block_data.py` writes their models, block states, loot, tags and recipes; previews go to `block-previews/`.
  - Top-down maps of a generated area are in `terrain-previews/`.
  - See `tools/terrain/README.md` (and `TERRAIN_DESIGN.md` for the design).
- **`tools/golems/`:** generates the Cinder Colossus designs and its trophies.
  - It writes their GeckoLib models, animations and textures (`geo/`, `animations/`, `textures/entity/cinder_colossus/`), renders the previews in `boss-previews/cinder_colossus/`, and writes `GolemDesigns.java`.
  - The trophies: `geo/block/` and `geo/item/colossus_trophy.geo.json`, `animations/block/colossus_trophy.animation.json`, `textures/block/colossus_trophy/`, `boss-previews/cinder_colossus/trophies.png` and `TrophyModels.java` (`python tools/golems/build.py --trophies-only` rebuilds just them).
  - `lair_preview.py` renders the lair previews (`boss-previews/cinder_colossus/lair_*.png`).
  - See `tools/golems/README.md`.
- **`tools/ratking/`:** generates the Rat King design candidates and his rats (models, animations, textures, the Zzz
  particle, `RatKingDesigns.java` and `RatDesigns.java`) and renders the previews in `boss-previews/rat_king/`; its
  `sounds.py` makes all their sounds from scratch. It reuses the golem kit. See `tools/ratking/README.md`.
- **Making a new boss:** follow `BOSS_GUIDE.md` (the whole process, from design candidates to the user's pick).
  Every boss's previews go in its own folder under `boss-previews/`.

---

## Project layout

Base package: `com.reg21meme.dross`. Client-only code is in each area's `client` subpackage.

| Package | What's in it |
|---|---|
| `dimension` | The Dross dimension: its terrain blocks and items (`dimension/block`), world generation features, tree decorators and the hub falloff (`dimension/worldgen`), the biome finder, the sky and fog (`dimension/client`), mob gear, the hub's safe zone |
| `portal` | Frame and portal blocks, the Rift Key, lighting, teleporting, the hub (fitted onto the terrain), the arrival sequence |
| `world` | The portal site: location search, the Fallen Cathedral, ground fitting, the leak (`world/shrine` holds the shrine designs) |
| `villager` | The trader, his chapel and path, dialogue, hand-ins, shop, skins, the Dross Compass, the Admin Sword (`villager/hut` draws the chapel), the hub's survivors |
| `enchant` | Necromancy, Deathforged, souls, the risen undead, anvil rules, book drops |
| `quest` | The Weathered Letter and its loot, quest progress, advancements, the Guide Book |
| `boss` | The bosses: so far the Cinder Colossus golem (`boss/golem`): its fight, minions, projectiles, temporary lava and fire, its GeckoLib renderers and the showcase golems; its trophies (`boss/trophy`: the block, its block entity and item, their GeckoLib renderers); the custom boss bar (`boss/client`); the lair (`boss/lair`); the wild golems; the logic of `/dross boss` |
| `command` | The `/dross` command and its showcases |
| `registry` | Every `DeferredRegister`: blocks, block entities, items, entities, particles, enchantments, loot modifiers, sounds, world generation features, tree decorators, density functions, placement modifiers, the creative tab |

Mod metadata is in `src/main/resources/META-INF/mods.toml`, filled from `gradle.properties`. All player-facing text is in `assets/dross/lang/en_us.json`.
