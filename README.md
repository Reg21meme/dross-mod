# Dross

A Minecraft Forge mod that adds **the Dross**: a dangerous dimension of endless night, with its own forests, wastes and mountains, reached through an electric blue rift portal. Getting there takes a quest. You find an old letter in the Nether, prove yourself to a strange villager, and have him forge the key that opens the rift.

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

1. **Normal survival.** Nothing from the mod is needed at first. Somewhere 3,500–4,500 blocks from spawn, usually in a forest, taiga or snowy land, the **Fallen Cathedral** stands in ruins around an unlit blue portal frame, and the land around it is dying. At the edge of one of the villages near spawn stands a small stone **Rift Chapel** with an old villager inside: the **Dross Trader**.
2. **The Weathered Letter.** One Nether Fortress or Bastion Remnant chest in five holds a Weathered Letter. It's a 4-page letter from an unnamed friend:
   - Blue portals from the Dross have been spilling into the world. "We" closed them, but the Dross is pressing to break through again.
   - An old villager, the writer's friend, is looking for someone strong enough to fight back.
   - It gives a rough location for his chapel at the edge of a village: a direction from where the world began, and coordinates rounded to 50 blocks.
   - It tells the reader to say an old friend sent them about the rift.

   Picking up the letter opens the **Dross** advancement tab.
3. **Meeting the trader.** There's one per world; your quest progress is your own. Like the Blind Oracle, he has a marker over his head that only you see: a red **?** while his offer waits for you, a yellow **...** while you're on his quest, a green **!** when it's done. Each right-click is his next line or two. He tells you his side of the story: when the Dross was locked away, its knights were left on our side, still guarding the broken portals they came through, and their souls still carry the Dross's magic. With enough of them he could make something to wake the great portal. Will you help? On your next click you answer "Yes, of course." (in chat, as you), and he gives you **3 golden apples** and tells you where the nearest **knight ruin** you haven't cleared is (rounded to 10 blocks; he takes a moment to feel for it, so that line may come a second later).
4. **Three knight ruins.** Clear three different knight ruins (both their knights beaten: see [The elemental knights](#the-elemental-knights)). Only a ruin you clear for the first time counts, so beating the same ruin's knights again when they come back doesn't. Each one shows **"Knight ruins cleared: 1 / 3"** over your hotbar. Asked meanwhile, he tells you your count and the nearest ruin you haven't cleared. The third one: *Advancement: Knightfall.*
5. **The Portal Orb.** Go back to him (the green **!**). Over a few lines he feels the souls on you, makes the **Portal Orb** (a glass ball with a little Dross portal swirling in it) in a puff of blue, tells you where the cathedral is (rounded to 100 blocks, for example "around X 7500, Z 1200") and warns you about its knights, then: "Go on. I believe in you." *Advancement: The Portal Orb.* Asked again, he reminds you where the portal should be. His shop opens for you (it sells the Dross Guide Book once you've been to the Dross).
6. **The cathedral under siege.** Go to the Fallen Cathedral (there's no compass: his directions are all you get). Five of the Dross's elemental knights roam round it, each on its own spot, spread apart, a random mix of elements, and a spell holds it shut: a shimmering curtain of blue light along its edge, and walking in, you're pushed straight back out. Bring down all five (as they fall, "The cathedral's knights fallen: 2 / 5" over your hotbar) and the barrier cracks and shatters, **for good and for everyone in the world**. *Advancement: Siege Broken* (for whoever brings the last one down and everyone near). Someone else got there first? Then it's already open for you. See [The siege](#the-siege).
7. **Opening the rift.** Throw the Portal Orb into the cathedral's frame. The orb is used up and the portal lights, staying open for everyone, permanently. (While the siege holds, the frame refuses it: the orb pops back out.)
8. **The Dross.** Stepping through takes you to the **Shattered Spire**, the hub at Dross 0,0. The words "The Dross" fade in over four low piano notes. *Advancement: Enter the Dross*, the main achievement. On your first arrival you also get the **Dross Guide Book**. Two survivors, the Blind Oracle and the Ragged Seeker, keep watch at the spire.
9. **The Blind Oracle's quests.** At the survivors' camp the Blind Oracle tells you his story and asks for your help, then sends you out one quest at a time: three of the Silverwood Forest's giants first, then for each boss a hunt in its land and then the boss itself (the Cinder Colossus, the Rat King, the Huntsman, the Emberdactyl), ending in *Defeat the Dross*. See [The Blind Oracle's quests](#the-blind-oracles-quests).
10. **Exploring.** Round the hub lies the Silverwood Forest; further out are the Cinder Wastes, Blightwood, Rimefall and the Gravecrags' peaks. The Cinder Wastes, Blightwood and Rimefall stay locked until the Oracle's quests reach them. Somewhere in the Cinder Wastes a volcano holds the **Cinder Colossus**. *Advancement: Set in Stone.*

**A lost orb:**
- If you've lost your Portal Orb (none in your inventory) and the cathedral's portal isn't lit yet, he offers to make another: clear **three more** knight ruins you've never cleared before (the yellow **...** again), then go back for a new orb. Once the portal is lit (by anyone) nobody needs another.
- The Portal Orb is fireproof and never despawns.
- Once you've been to the Dross (*Enter the Dross*), he has a few lines for you in turn ("I hope your battle in the Dross is going well.").

---

## The Dross (the dimension)

- **Dimension ID:** `dross:dross`, shown in game as **"The Dross"**.
- **Terrain:** real land, surface only (no caves, except Blightwood's sewers, below): rolling hills, cliff steps and rocky knolls, mesas in the Cinder Wastes, and snow-capped mountain peaks (about y 230-260) in the Gravecrags. It's Minecraft's own terrain generator, set up in data files (`data/dross/dimension/`, `dimension_type/`, `worldgen/`) written by `tools/terrain/worldgen.py`, where every tuning number lives.
- **Five biomes**, regions about 600-670 blocks across:
  - **Silverwood Forest** (the main biome, always round the hub): a dense, dark forest of silverwood trees with glowing Stormvine, Glimmer Ferns, Wispblooms, mossy boulders, fallen logs and dark ponds. No zombies or skeletons: its only monsters are the **Silverwood Giants** (below), and its only animal the odd moose: no witches, endermen,
    farm animals or bats.
  - **Cinder Wastes**: ash, basalt and magma, enclosed lava pools, basalt spires, charred trees, Emberweed and Cinder Vents. Home of the Cinder Colossus's volcano.
  - **Blightwood**: poison-green and maroon giant mushrooms on bone-beige stems, Gloomcaps, Sporepods, murky ponds and thick green fog. No zombies, skeletons or endermen: the mobs here are rats (Plague Rats, and about 1 in 5 Plague Ravagers) and the odd witch (7 in 100). Underneath are **the sewers** (below).
  - **Rimefall**: frozen ground, frost-covered dead trees with icicles, ice spikes, Frostblooms and frozen ponds, and one **big frozen lake** in every Rimefall area. Its only mobs are its own animals: Rimehorn herds, moose and Pond Skaters (below).
  - **The Gravecrags**: the mountains, Cragstone cliffs with glowing Storm Lichen and Crag Thistles, snow on the peaks. Never near the hub.
  - (There was a sixth, the **Nether Scar**, until October 17, 2026; it was removed and its land went to the biomes round it. Its four blocks are kept as building blocks: see the blocks below.)
- **Ores** under every biome, in Duskstone (the Dross's stone): coal, gold (drops nuggets), **Moonsilver** (Raw Moonsilver, smelted into Moonsilver Ingots) and **Stormite** (glowing Stormite Shards; needs an iron pickaxe). Moonsilver and Stormite are for gear that comes later.
- **Sky and fog:** each biome has its own night fog, sky and water colours, particles and ambient sounds, with a blue glow over the night sky and no clouds.
- **Old worlds:** not supported while the mod is unpublished (the old flat Dross and its biome are gone). Start a new world.
- **Time:** permanent night (fixed at time 18000). The sun never rises.
- **Mobs:** normal hostile spawn rates, but no slimes, creepers or spiders. The **Cinder Wastes** are different: only their own mobs spawn there: the Cinder Colossus's **stone golems**, **lava golems** (always next to lava), the **Cinder Hermit** and the **Pallbearer**. Wild golems don't crumble, and a wild lava golem only lights its fuse when you're close.
- **The Cinder Hermit** is a 6-block hermit crab in one of eight shells (a bronze bell, an iron brazier, a horned skull, a funerary urn, a magma conch, a crystal geode, a giant lantern or a piece of the Colossus). It leaves you alone until you hit it, then snaps and pinches, and its neighbours join in; every time it loses a quarter of its health it hides in its shell (hits there do half damage), peeks out and comes back out. Each Cinder Wastes area has one or two **hermit crab caves**, each with its own hermit, who keeps to its den: a long gallery dug into a hillside with smoking vents and a pool of lava, or shallow dens under a mound of ash with a loot room behind a crawl (decorated pots, suspicious gravel to brush, a barrel) (`/dross goto hermit_cave`).
- **The Pallbearer** is a towering skeleton (nearly 10 blocks) carrying a burning coffin. It also leaves you alone until hit; then it roars (slowing you), swipes and slams its coffin down.
- **The netherite dead of the Gravecrags.** Since the Silverwood Giants came (November 9, 2026), the Gravecrags are the only part of the Dross with zombies and skeletons (the Silverwood Forest has its giants instead, and Blightwood, the Cinder Wastes and Rimefall have their own mobs). Older worlds' Silverwood Forest loses its zombies and skeletons too.
  - **Zombies** spawn in full netherite armor with a netherite sword.
  - **Skeletons** spawn in full netherite armor and keep their bows.
  - None of it is enchanted, and none of it drops when they die.
- **No other way out:** no portal can be lit inside the Dross. The Portal Orb does nothing there, and flint and steel or fire can't light a nether portal there either. The hub's portal is the only exit.
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
  tougher than usual (1.5 times the health and bite). Until your Blind Oracle quest reaches him, the cave is warded:
  you can see him asleep on his nest from the tunnel, but he doesn't wake for you (see
  [Locked bosses](#locked-bosses)). While he sleeps you hear his snoring all through his sewer, faint far off and
  louder as you get closer to his cave.

`/dross goto lair ratking` takes you to the nearest rat cave and `/dross goto sewer` to the nearest regular sewer
(Minecraft's `/locate` can't find them). Sewers only appear in newly generated chunks.

### Rimefall's animals and lakes

- **The big lakes:** every Rimefall area has one big frozen lake, about 100 blocks across (a smaller one, about 64,
  where there's no room), in its flattest spot: a sheet of ice over dark water, deeper in the middle, with 4-6
  **open holes** you can fall into (they never freeze over), and a gentle snowy shore. Nothing grows on the ice.
  `/dross goto lake` takes you to the nearest one's shore. They only appear in newly generated land.
- **Rimehorns:** woolly rhinos about 4.5 blocks tall and 8 long, in herds of 6-8 (2 frosted males, the rest dark
  brown females). Each Rimefall area has 2 herds, sometimes 3 (a small area 1, sometimes 2), placed when the land is made, away from
  the lake. They're **neutral**: hurt one and its herd comes for you, head on, but gives up after about half a minute
  or 24 blocks (a hurt calf runs off). Up close it **flicks** you up into the air with its horn; further off (or when
  you shoot it from afar) it **rushes** you in a quick, low dash. Its face, snout and rump can be hit, not just its
  middle (so can a moose's head). Feed **Frostbloom**
  to a male and a female and they make a calf, which grows up in 35 minutes and **never attacks you**, even when its
  herd is angry. No drops yet.
- **Moose:** about 3.8 blocks tall, alone, in Rimefall and now and then in the Silverwood Forest (never round the
  hub). Neutral: hit one and it charges and rams you, throwing you back (a bull hits harder than a cow). A **mother**
  with her calf goes for you if you come within about 6 blocks of her or the calf, but never chases you more than 10
  blocks from her calf. No drops yet.
- **Pond Skaters:** hostile long-legged skaters that spawn on ice at night: at most 1 or 2 on a big lake (lakes differ), at
  most 1 on any other patch of ice at least 20 blocks across, none on smaller ice. As fast as a spider off the ice and very fast on it;
  they rear up and stab with their beak. Their legs have hitboxes of their own. Besides players they go for the
  Frostfolk, the Huntsman, his Alpha Moose, moose and Rimehorns (which fight back).
- Every one of their sounds is original (`/dross showcase mob_sounds rimefall`). Spawn eggs for all three are in the
  Dross tab, and `/dross showcase rimefall_mobs` lines them up with animation buttons.

### The Silverwood Giant

A giant warrior of the Silverwood Forest, about 4.5 blocks tall with glowing blue eyes: the giants of the Odyssey (the
Laestrygonians, who hurled boulders at Odysseus's ships), in the Dross's dress. It took the place of the forest's
zombies and skeletons (November 9, 2026). It wears no armour, and comes in three looks, like members of one tribe, each
giant one of them at random: **G1 the Boulder-Hurler** (wild hair and beard, woad on his bare chest, a boulder on his
shoulder), **G2 the Brawler** (shaved sides and a long braid, woad down his chest, nothing in his bare fists) and **G3
the Tree-Bearer** (the oldest, grown over by the forest: a cloak of moss, glowing Stormvine, a whole uprooted silverwood
tree carried over his shoulder and swung overhead).

- **Neutral, with patience.** Walk or run past one and it does nothing. Stay within about 7 blocks of it: after about
  4 seconds it turns to stare at you, its eyes flare and it huffs and stamps (a warning); after about 8 seconds it
  attacks. Moving off calms it again. Hit it and it fights back.
- **Strong:** 100 health and heavy armour. It **smashes** (5 hearts), **sweeps** you away to its left (3.5 hearts),
  **stomps** and throws everyone round it into the air (3 hearts), and **hurls a boulder** (4 hearts, from 8 to 28
  blocks) but only at someone it's angry with, and only G1, the Boulder-Hurler (the other two fight up close).
- **Slow:** it runs slower than you walk, its anger runs out after about 30 seconds, and it gives up a chase 24 blocks
  from where it began and walks back. So you can always get away.
- **Where:** only in the Silverwood Forest, never within about 62 blocks of another giant (and at most 4 within 128), never within 96 blocks of the hub,
  and only where there's room for its size: a few roam within sight of you. No drops; 18 XP. It dies the way
  Minecraft's mobs do (it tips over, flashing red).
- Its sounds are all original (`tools/forest_giant/sounds.py`): a deep, monstrous voice that carries. Its spawn egg is
  in the Dross tab (a random look each time; `/summon dross:silverwood_giant ~ ~ ~ {Look:"brawler"}` for G2,
  `"treebearer"` for G3, `"hurler"` for G1), and `/dross showcase silverwood_giants` lines up the three looks with
  animation buttons. Previews: `boss-previews/silverwood/mobs/forest_giant/`.

---

## The portal

### The frame
- **Dross Portal Frame** (`dross:dross_portal_frame`): dark stone with glowing electric blue cracks.
- It can't be crafted, mined or blown up, and it drops nothing. It has a block item for testing, only in the creative Dross tab (or with `/give`).
- Every frame block "leaks" the Dross: drifting blue particles and a low ambient hum, even when the frame is unlit.
- Flint and steel does nothing to it, and normal obsidian portals stay purple nether portals.

### Lighting it with the Portal Orb
- Throw (Q) a **Portal Orb** into the empty middle of a complete frame, along X or Z. The orb is used up (glass shatters) and the frame fills with the **Dross Portal** (`dross:dross_portal`).
- If the frame is already lit, the orb pops back out instead of being used, and it won't drift into the portal.
- Lighting the Fallen Cathedral's frame is remembered for the whole world (the trader stops offering new orbs).
- While the cathedral's siege holds (its five knights still stand), its own frame won't light: the orb pops back out, unused, runes flare across the opening, and you're told "A spell holds this frame shut while its knights stand."
- A netherite ingot doesn't light anything.

### Look
Electric blue portal texture, blue particles, and a blue swirl over your screen while you stand inside.

### Travel
- Every Dross portal in the Overworld leads to the **hub** (the Shattered Spire at Dross 0,0).
- The hub's exit portal always leads back to the **Fallen Cathedral's** portal, wherever you came in.
- All entities can use it. Nothing is ever placed inside blocks or above a drop.

---

## The Fallen Cathedral (the portal site)

The cathedral is the only way into the Dross. There's one per world, and it's made by world generation (the structure `dross:portal_site`): it appears with the land the first time its chunks are generated, like a village. Nothing is built when a new world starts.

- **Where:** worked out from the world's seed alone (in about a second, without loading or generating anything), so it's known from the start and is the same every time.
  - **3,500–4,500 blocks from spawn**, in a direction the seed picks, then nudged to the nearest good spot: forest, dark forest, taiga, snowy plains or taiga, ice spikes or grove.
  - **Avoided:** ocean, river, beach, swamp, mountains, deep water, and villages and pillager outposts.
  - The land under it must be nearly flat to begin with: 5 blocks of variation or less.
  - If nothing passes, the rules relax step by step (more variation, further out, any dry land), so every world gets a site.
  - Nothing tells you where it is when you join any more: the Dross Trader tells you, rounded to 100 blocks, once he's made you a Portal Orb.
- **What:** a huge ruined cathedral, 61 × 87 blocks. Its heart is an unlit, unbreakable portal frame with a 3 × 5 opening, facing south. Glowing blue cracks, broken frame shards and dead ground show the Dross leaking out.
- **Fitting into the land:**
  - Minecraft's own terrain shaping (as for village houses) draws the land to its floor, and the footprint is levelled exactly, with no big foundations.
  - Around it the ground steps back to the natural terrain **one block at a time**, out to 16 blocks, so there are no cliffs or dirt walls. New ground keeps the local top layer (grass, sand, snow...).
  - Minecraft grows its trees after the structures, so once its land is loaded, every tree touching the cathedral is removed, always whole, so there are no floating leaves. The rest of the forest is left alone. (Arriving there, you may see them vanish a moment after the land appears.)
- **The leak:** within about 16 blocks, flowers and grass die or disappear, some grass turns to coarse dirt, and stone bricks crack.
- **No site** in a superflat world, or one made with **Generate Structures** off (no structures at all, like vanilla's strongholds; a warning in the log): there, the hub's exit portal brings you back near the world spawn.

### The siege
The cathedral doesn't stand empty (`world/CathedralSiege`, `CathedralWard`, `CathedralSiegeData`):
- **Its five knights.** Once per world, when a player (not a spectator) comes within 160 blocks of it, its land is loaded and its trees are tidied, five [elemental knights](#the-elemental-knights) appear round it: five different kinds of the ten, never more than two of one element, picked from the world's seed. Each has its own spot about 12 blocks outside the cathedral's footprint, on solid dry ground, the five spread evenly round it (at least 24 blocks apart), and keeps near it as a ruin's knights do: it wanders there, fights players who come near and walks back. They never despawn. Not on Peaceful. One that leaves without dying (Peaceful, say) comes back the next time; a dead one never does.
- **Its barrier.** While any of them stands, the cathedral (its footprint, from 16 blocks under its lowest layer to 64 over its top) is warded, like the bosses' homes in the Dross: a player in survival or adventure who walks in is pushed back out with a flash of runes and a soft chime (now and then "A spell holds the cathedral shut while its knights stand." over the hotbar); one deeper in (an ender pearl, a teleport) is sent back to where they last stood outside, or to the cathedral's great door. Near its edge they see the locked lands' curtain: blue streaks rising off the ground, runes drifting up among them. Players in creative or spectator mode pass and see nothing. The knights walk through it freely.
- **The fall.** As each knight dies, the players near are told how many have fallen. When the last one dies, the barrier stands up bright, cracks and shatters in front of every player within 64 blocks (the land barriers' own fall), and is gone **for good, for everyone in the world** (saved). Those players and whoever brought the last knight down get *Siege Broken* and the line "The last of the cathedral's knights falls, and the spell holding it shut breaks. Its way in stands open." (Not for `/kill`.)
- **Its frame** refuses the Portal Orb while the siege holds (see [Lighting it with the Portal Orb](#lighting-it-with-the-portal-orb)).
- Test tools: `/dross siege status|reset|break` (see [Commands](#commands)).

The site's position and the frame's direction are available to the rest of the mod through `world/PortalSite` (frame corner, axis, opening center and size), worked out from the seed. The trader's directions, `/dross goto cathedral` and the hub's exit portal all use it; the last two load the frame's land first (the first time, generating it), as a nether portal trip does.

(Until November 4, 2026 the site was found by a long search at world start, 3,000–10,000 blocks out, and built a step at a time; on some modded worlds that froze the game, so it became a world-generation structure.)

---

## The hub: the Shattered Spire

- One per world at **Dross 0,0**: a tall, broken spire, levelled into the forest. It's made by world generation (the structure `dross:dross_hub`), with the land, the first time someone enters the Dross: nothing happens when a new world starts, and that first trip takes about a second longer, like a first trip into new Nether land. Its ground level comes from the seed. The ground round it steps back to the natural land at most 1 block at a time (no cliffs or dirt walls), and trees keep about 24 blocks off it, plants and ores 8.
- Its portal frame is lit as the **exit portal**, facing south.
- You arrive inside the spire's hall, on the top step in front of the exit portal, facing the gate.
- **Arrival sequence**, every time you come through the portal:
  - After 1 second, the title **"The Dross"** fades in, in electric blue.
  - Four piano notes play, "dun, dun, DUN, dunnn" (E, D, C#, then a low F#). Only you hear them.
  - Two seconds after the last note, you get the *Enter the Dross* advancement.
  - On your **first arrival only**, you get the **Dross Guide Book** (dropped at your feet if your inventory is full).
- If the exit portal has gone out but its frame is whole, arriving lights it again.
- With **Generate Structures** off there's no hub (nor cathedral or chapel): you'd land on safe ground near Dross 0,0.

### The survivors
Two survivors of the Dross live at the spire (`dross:survivor`): **the Blind Oracle**, the mentor, and **the Ragged Seeker**, his student. They wear two of the Dross skins drawn for the trader's villager model (8 and 3).
- They live in their **camp** at the foot of the spire's front steps, west of the road: a slab of the spire's broken crown on four broken pillars behind a windbreak of masonry, the Oracle's seat by a humming shard of a Dross frame among his books and candles, the Seeker's bed among his crates and tools, and a blue soul fire with benches in front. They walk together round the camp (the fire, the books, the tools, the road), never more than 6 blocks outside it. They come with the hub's land, once per world.
- Right-click the **Blind Oracle** and he gives you his quests, a line a click, with a marker over his head (see [The Blind Oracle's quests](#the-blind-oracles-quests)); once they're all done he talks to you as an old friend (7 new lines, in turn). Right-click the **Ragged Seeker** and he says his next line in chat; while you're after one of the big bosses (the Colossus, the Rat King, the Huntsman, the Emberdactyl) he jokes about it instead, and has a line for you once it's beaten.
- Like the trader, they can be hit and knocked back but never die: at half health they heal and pop back to their spots. Hostile mobs ignore them.
- No trades.

---

## The Blind Oracle's quests

The Dross's main quests. **The Blind Oracle**, at the survivors' camp at the foot of the spire's steps, gives them one
at a time, always in the same order. Each player has their own quests.

| # | Quest | He asks you to | Advancement |
|---|---|---|---|
| 0 | The Oracle's Plea | hear his story | The Oracle's Plea |
| 1 | The Forest Giants | kill 3 Silverwood Giants | Giant Slayer |
| 2 | Into the Ashes | kill 3 of the Cinder Wastes' mobs (golems, Cinder Hermits, Pallbearers) | Into the Ashes |
| 3 | The Cinder Colossus | beat it | *Set in Stone* (challenge) |
| 4 | Beneath the Rot | kill 3 of Blightwood's creatures (rats or witches) | Beneath the Rot |
| 5 | The Rat King | beat him | *Down the Drain* (challenge) |
| 6 | First Frost | hunt 3 of Rimefall's grown beasts (a Pond Skater, a moose or a Rimehorn; calves don't count) | First Frost |
| 7 | The Huntsman | beat him and his Alpha Moose | *The Hunter Hunted* (challenge) |
| 8 | The Emberdactyl | beat it and its young | *Wings Clipped* (challenge) |
| 9 | Defeat the Dross | hear his last words | *Defeat the Dross* (challenge, with blue fireworks) |

There's a kill quest only the first time you go into a land; after that he sends you straight after its next boss.

- **Talking to him:** each right-click is his next line in chat (`<Blind Oracle> ...`, only you see it). A new quest is
  a few lines; after the last one it's yours. While it's going, a right-click reminds you how far you've got. When
  it's done, go back to him: he thanks you, and the next quest is waiting. If you walk off mid-talk, he carries on where
  he stopped.
- **The marker over his head** (only you see yours): a red **?** (a new quest is waiting), a yellow **...** (it's
  going), a green **!** (it's done: go back to him). Seen from about 48 blocks.
- **Kills that count:** only while that quest is going: your own kills (a blow, an arrow, a thrown spear) and your tamed
  wolf's. Each one shows your progress above the hotbar ("Into the Ashes: 2 / 3").
- **Bosses:** a boss quest is done when you get the boss's advancement. If you already beat that boss (say with a
  friend before your quest got there), the quest is done as soon as he gives it.
- After "Defeat the Dross" ("You've beaten everything the Dross has to offer. Come back later to face more dangerous
  foes.") he has no more quests, and says his old lines again.

### Locked lands

Like the Twilight Forest, the Dross's lands open as your quests go on. After the war barriers were raised across the
Dross, one ringing the forest round the hub, and the Blind Oracle breaks them for you: **the Cinder Wastes** and **the
Gravecrags** when you hand in the giants, **Blightwood** (and its sewers) when you've beaten the Cinder Colossus,
**Rimefall** when you've beaten the Rat King. So you can't leave the Silverwood Forest until the giants are dead. Each
boss's home opens the same way: when you hand in the land's kill quest (the Emberdactyl's when you hand in the
Huntsman).

- **His spell:** each time, he says his last thanks line, waits a few seconds so you can read it, then raises his arms:
  a great rune circle opens on the ground round him and light gathers into his hands, a beam of blue light bursts up into
  the sky with three thinner beams winding up into it, then it bends out towards the barrier, and far off over the land a
  flash, shock rings and a pillar of light burst, then the boom and its echo. It lasts about 12 seconds, and anyone near
  sees it. After a boss, he uses its dying energy. That's his big spell, for a land; to open only a boss's home he casts
  a quick medium one: one circle, a small ring of barrier round it shattering, and a beam shot straight up into the
  sky. In the big one eight beams leave the top of his great one, arc over the sky and come down far off, shattering a ring of barrier round him. (`/dross showcase oracle_spell` shows the big one,
  `/dross showcase oracle_spell medium` the medium one.)
- **The barrier falling:** a land he has opened still shows its curtain until you first come to its edge; then it glows up,
  cracks and shatters in front of you, once (arriving inside the land, its nearest border falls, or a stretch ahead of you if none is near). (`/dross
  showcase barrier_fall` shows it.)

- **Inside a land that's locked for you:** Slowness V, Mining Fatigue V and Hunger II for as long as you stay (gone the moment you step
  out), and "This land rejects you. You cannot enter yet." in chat as you come in and every 20 seconds. It goes by the
  ground you're over, so flying over or digging under a land doesn't get round it.
- **Its edge:** a shimmering curtain at the border, only for the players it's locked for: thin electric-blue streaks of
  light rising from the ground and fading high up, with the Dross's glowing runes drifting up them (none with particles
  on Minimal).
- Creative and spectator players are never affected.

### Locked bosses

A boss **at home** (the Colossus in its lair, the Rat King in his rat cave, the Huntsman at his village, the
Emberdactyl on its volcano) only fights players whose quest has reached it (they've heard its quest from the Oracle).
To anyone else it acts as if they weren't there: it doesn't rise, wake or come home for them, never attacks them,
their hits (and their pets') do nothing to it, and they don't make it stronger or keep it from
resetting. Its arena is **warded**: walk into the Colossus's cavern, the Rat King's cave or onto the Emberdactyl's
volcano (anywhere from its foot up) too early and you're bounced back out, with a flash of runes and a soft chime (an
ender pearl in: you're sent back to where you were). The Rat King still lies asleep on his nest where you can see him
from the tunnel. The Huntsman has no arena: until your quest reaches him he **sleeps at home**: him on his bed of furs
in his lodge (look down at him through the clear ice window in its roof), his Alpha Moose lying in its barn
with its eyes shut, both snoring,
his spear, bow, bola and arrows lying before his door, and magic keeps them from harm. Once your quest reaches him (First
Frost handed in) they wake and ride their patrol round his village, and they never sleep again. Spawn eggs, `/dross boss summon`, the test
arenas and the showcases are never locked, and neither are creative players.

**Magic protects** the bosses' homes: until you've beaten a boss you can't break the blocks of its home (the
Colossus's lair volcanoes, the Rat King's whole sewer with his cave and gold, the Huntsman's villages, the
Emberdactyl's volcanoes). Mining one is like mining bedrock: no cracks and nothing slows you (a stray hit in a fight
does nothing); keep at it and it says "Magic protects this block.", and explosions leave them alone. The **hub** at Dross
0,0 is protected the same way, for good. The moment you beat that boss (its advancement: everyone in the fight gets it)
you can dig there freely (its gold included).
Creative players are never stopped.

---

## The Dross Trader

An old villager (`dross:dross_trader`) who knows about the rift. He wears an original electric blue skin, **The Archivist**: a bald scholar with glowing spectacles and a blue book.

### Where he lives
- In the **Rift Chapel**: a small stone chapel made by world generation (the structure `dross:rift_chapel`) at the edge of a village. A world has **one** trader and one chapel. The village is one of the 5 nearest to spawn, picked at random by the seed (only one with room at its edge for the chapel). Nothing of him happens when a new world starts: the chapel and the trader come with the village's land, the first time it's made.
- **At the village:** the chapel stands just clear of the village's houses and streets, its door facing the village, with a 3-wide path (dirt, or smooth sandstone at a desert village) to the nearest street. It needs nearly flat dry ground (6 blocks of variation or less). Which village and where is worked out from the seed while the world loads (about a second or two, in the background), the village laid out exactly as Minecraft will make it.
- **Once a village:** every village that has had the trader is remembered in the save. A trader can never come to the same village twice, and a world never has a second one. If a chapel's land were ever made again, the extra trader would be removed at once.
- **Fitting into the land:** Minecraft's own terrain shaping draws the land to its floor, and the ground round it steps back to the land a block at a time over 6 blocks. Trees that grew into it are felled, whole, by the trader the first time its land is loaded.
- **No village with room** near spawn (a world of oceans, a modpack without villages): the chapel stands on its own on the nearest flat dry land at least 384 blocks from spawn, and the letter says so.
- Nothing tells you where it is when you join: the Weathered Letter does.
- In a superflat world, or one made with **Generate Structures** off, there's no chapel (only spawn-egg traders).
- (Until November 4, 2026 his chapel was built at the edge of the nearest village to spawn, found by a search at world start that could freeze modded worlds; for a few hours that day there were many chapels in the countryside, each with a trader.)
- **The Rift Chapel** (9 wide, 13 deep, 18 tall):
  - **Outside:** deepslate and stone brick walls with lapis, buttresses, tall blue windows, a blue rose window over the dark oak door, and a steep roof. On top are an open belfry with a soul lantern and a spire with a copper lightning rod.
  - **Inside:** pews, blue banners and soul lanterns. Behind the altar is a broken piece of a real Dross portal frame, which leaks and hums but can't be lit.
  - The lightning rod draws natural lightning within 128 blocks to the spire (harmless).

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

### His quest
See [The story](#the-story), steps 3 to 6: his offer, three knight ruins, the Portal Orb and where the cathedral is. Its
words are lang keys under `dross.trader.` in `en_us.json` (first drafts), and its progress is saved per player and per
world (`/dross quest status`). The marker over him works like the Blind Oracle's.

### Shop
His shop opens only for players he has made a Portal Orb, and only when he has something for them (right-clicking him
gives his line, then opens it).

| Offer | Price |
|---|---|
| Dross Guide Book (only for players with *Enter the Dross*) | 3 books |

Unlimited uses, no restocking, no XP.

---

## Items

| Item | What it does |
|---|---|
| **Weathered Letter** | Starts the quest. A readable 4-page letter, found in one Nether Fortress or Bastion Remnant chest in five (added with a global loot modifier, so other mods' loot still works). It comes out of its chest blank and is written the first time it's in your inventory: a rough location of the trader's Rift Chapel, at the edge of a village. |
| **Portal Orb** | Made by the trader from the souls of three knight ruins. A glass ball with a little Dross portal swirling in it. Throw it into a Dross frame to light the portal (it's used up). Fireproof, never despawns. |
| **Dross Guide Book** | Given on your first arrival in the Dross, and sold by the trader. A readable 5-page guide to the dimension: endless night, the safe hub, the netherite-clad dead, and no way out but the hub. |
| **Dross Portal Frame** | The frame block (creative tab or `/give` only). Can't be broken in survival and drops nothing. |
| **Dross Trader Spawn Egg** | Spawns a trader who treats that spot as home. |
| **Admin Sword** | A testing sword (creative tab). Kills anything in one hit (including the Ender Dragon, the Wither and every Dross boss, whatever stage it's in) while still counting as your kill (a boss's advancement and trophy too). Unbreakable and fireproof. It hits the trader like a normal sword. |
| **Rage Wand** | A creative-only testing wand with five **teams** (blue, red, green, yellow, purple: right-click to switch; the crystal shows the colour). Hit a mob with it (no damage) to give it **Rage** and the wand's team: mobs of different teams fight each other, never their own team, and ignore players while an enemy team is near. Teams and Rage last forever, not even milk cures them. Mobs use their own attacks; animals with none headbutt (1 heart). Bosses work too, and their minions join their team. See [The Rage Wand](#the-rage-wand). |
| **Cinder Colossus Trophy** | A one-block miniature of the Cinder Colossus, in one of five poses (the tooltip names it). Place it on the floor and it faces you; right-click it to change its pose (1 to 5, then round again). Its lava glows and keeps flowing down it. The boss drops one for every player who helped beat it. Fireproof, never despawns (see [The Cinder Colossus](#the-cinder-colossus-mini-boss)). |
| **Rat King Trophy** | A one-block miniature of the Rat King in one of five poses (the tooltip names it). Right-click a placed one to change its pose. The Rat King gives one to every player who helped beat him. Fireproof, never despawns (see [The Rat King](#the-rat-king)). |
| **Huntsman Trophy** | A one-block miniature of the Huntsman and his Alpha Moose in one of five poses: Riding Down, Rain of Arrows, The Rearing, The Rearing Shot and The Parting Shot (the tooltip names it). Right-click a placed one to change its pose. Beating the pair gives one to every player who helped. Fireproof, never despawns (see [The Huntsman](#the-huntsman)). |
| **Stone Golem / Lava Golem Spawn Eggs** | Spawn a wild stone or lava golem (like the ones in the Cinder Wastes): it doesn't crumble, and a lava golem only blows up when you get close. |
| **Silverwood Giant Spawn Egg** | Spawns a Silverwood Giant (look G1, the Boulder-Hurler), neutral like a wild one (see [The Silverwood Giant](#the-silverwood-giant)). |
| **Dross terrain blocks** | The 38 blocks of the Dross's land: Duskstone and its four ores; the silverwood wood set (log, wood, stripped log and wood, planks, leaves, sapling; four saplings in a square grow a great silverwood) and Stormvine; and each biome's ground covers and plants (Silvermoss, Glimmer Fern, Wispbloom; Ash, Ash Layer, Charred Log, Emberweed, Cinder Vent; Sporeloam, Blightcap blocks, Gloomcap, Sporepod; Hoarfrost, Frostbitten Log, Frostbloom, Icicle; Scarred Netherrack, Scar Roots, Soulwick, Scar Vent (from the old Nether Scar: building blocks now, they no longer generate); Cragstone, Storm Lichen, Crag Thistle); and from Blightwood's sewers **Sewer Sludge** (it slows you down, less than soul sand) and the **Blight Vent** (glows poison green and puffs spores). |
| **Raw Moonsilver, Moonsilver Ingot, Stormite Shard** | Materials from the Dross's ores. Raw Moonsilver smelts into an ingot, and the ingot makes the Moonsilver Spear; Stormite is for gear that comes later. |
| **Moonsilver Spear** | The Huntsman's spear, for players: two sticks on a diagonal with a Moonsilver Ingot at the top right. It stabs for 8, and held back and let go it's thrown like a trident (8) and flies back to you. Whatever it hits is frozen for 4 seconds (frost, shaking, slowed). 500 uses, repaired with Moonsilver Ingots. Enchantments: Sharpness or Smite, Impaling (for its throw, on anything), Looting, Knockback, Channeling, Unbreaking, Mending. |
| **Planted Moonsilver Spear** | A Moonsilver Spear standing upright, its butt in the ground: the hunter villages' spears, a decoration (it drops itself). |
| **Alpha Moose Skull, Moose Skull, Rimehorn Skull** | Mounted bare skulls (no lower jaw or teeth) with their antlers or the Rimehorn's two ice horns, in 3D and life size (the Alpha Moose's antlers about 5 blocks across, a bull's nearly 4, a Rimehorn's skull 2.8 tall with its horn). Place one on top of a block (a post) and it stands there facing you, or against a wall and it hangs there. The Alpha Moose drops its own every time it dies; a bull moose or a Rimehorn sometimes (1 in 10, more with Looting) when a player kills it. They're in the hunter villages too. |

**The creative "Dross" tab** holds every item above. It's grouped by kind, in this order: **blocks** (the portal frame, then the terrain blocks by biome), **quest items** (Weathered Letter, Portal Orb, Guide Book), **trophies** (all five Colossus trophy designs, then the Rat King's five, then the three mounted skulls, then the Huntsman's five), **spawn eggs** (the trader, the Cinder Colossus, the stone golem, the lava golem, the Rat King, the Plague Rat, the Plague Ravager, the Rimehorn, the moose, the Pond Skater, the Huntsman, then the Frostfolk's three: warrior, worker, elder, the Emberdactyl and its young, the Pallbearer, the Cinder Hermit, then the Silverwood Giant), **weapons and tools** (the Admin Sword, the Rage Wand, the Moonsilver Spear), the **materials** (Raw Moonsilver, Moonsilver Ingot, Stormite Shard), then the **crops** (Frostroot, Snow Lichen, Rimeberries). Everything from the mod is only in this tab: the Dross enchanted books are kept out of vanilla's Ingredients and Combat tabs.

---

## Every boss: the rules

Every boss (the Cinder Colossus, the Rat King, the Huntsman and his Alpha Moose, the Emberdactyl and its young)
follows the same three rules (`boss/BossRules.java`, the numbers there):

- **No hit takes more than a fifteenth of its health.** So it takes at least 15 hits, however strong your weapon.
  `/kill` and the Admin Sword aren't capped.
- **More players, a stronger boss.** Every extra player in its fight (within its boss bar's range, not in creative or
  spectator mode) gives it **50% more health** and **20% more damage**. Someone joining mid-fight raises its health
  but keeps its bar where it was (half full stays half full), and the bonus never goes down until it resets.
- **It resets** when nobody has been within 64 blocks for 3 minutes (and no foe it's fighting is that near): back to
  full health, its bonus gone, and back to resting. The Rat King goes back to sleep, the Colossus goes dormant in its
  Shell, the Emberdactyl back to its perch, and the Huntsman calm in the saddle.
- **Locked until your quest reaches it.** At home, a boss ignores a player whose Blind Oracle quest hasn't reached it,
  and its arena is warded against them (see [Locked bosses](#locked-bosses)): they don't make it stronger and don't keep
  it from resetting either.

## The Cinder Colossus (mini-boss)

A golem mini-boss that serves the Fire Necromancer: a huge, gorilla-like brute about 5.5 blocks tall, hunched over and
walking on its knuckles, with fists bigger than its legs, a cracked face, a layered jaw (an outer jaw and an inner
one behind it), horns and spikes, and **volcanoes on its back**. After three rounds of designs, the user picked
**3G Saddleback**, a variation of Old Calderon (No. 3 of the first five designs): two great volcanoes grow out of its
back and merge into one saddle of rock, big obsidian spikes stand in the saddle and down its lower back, it has horns,
spikes pointing up off its shoulders and a beard of obsidian shards, all in dark scorched stone. Its volcanoes erupt
out along their own angle, and its craters always hold glowing lava. Previews are in `boss-previews/cinder_wastes/cinder_colossus/`
(`all_golems.png`, `01_saddleback_sheet.png`, the fight page `01_saddleback_fight.png` and `minions.png`; the other
candidates are in `boss-previews/cinder_wastes/cinder_colossus/round1/` to `round3/`).

It lives in its **lair**, a volcano in the Cinder Wastes (below). For testing you can also get one from the
**Cinder Colossus Spawn Egg** (Dross creative tab) or `/dross boss summon colossus`, and `/dross boss arena colossus` builds a
placeholder arena to fight it in: a round floor of red nether bricks, 31 blocks across, with a low wall (four gaps)
and lights set in the floor.

### The lair
- **Where:** there are many lairs, one in **every group of 3 Cinder Wastes patches**: the 3 patches nearest the hub
  hold one lair, the next 3 nearest another, and so on outwards forever (never two in one patch; the other two patches
  of each group are kept for future fire bosses). A lair is built in the background the first time you come within
  about 384 blocks of it, a little at a time (at most about 40 ms of work each game tick, so the game never stutters).
  `/dross goto lair colossus` takes you to the nearest one's entrance (building it first if it isn't yet; never onto a
  half-built volcano).
- **Outside:** a volcano rising about 40 blocks over its arena, with a jagged crater that smokes and throws embers, lava
  streams and pools round its foot (they never spread, and neither do natural lava pools on its flanks), and a big cave
  mouth with obsidian teeth. The way in never steps more than 1 block: on steep land a rock ramp leads to the mouth.
- **Inside:** a domed cavern about 41 blocks across under the crater, with an obsidian and magma floor, lava glowing
  through sealed fissures below it, and obsidian stalactites. No lava can be reached from the floor, and nothing
  spawns inside by itself.
- **The boss:** the Colossus **rises when a player steps onto the floor** (not on Peaceful), a player whose Blind Oracle
  quest has reached it: until then its cavern is warded against them (see [Locked bosses](#locked-bosses)). Once it's
  beaten, that lair stays empty for good: no respawn.
- Previews: `boss-previews/cinder_wastes/cinder_colossus/lair_outside.png` and `lair_cutaway.png`. Numbers: `boss/lair/LairTuning.java`.

### The fight
It has **375 health** and a boss bar of its own (drawn by the mod: Minecraft's bars have no orange), shown to players
within 40 blocks. The fight has three stages:
1. **Shell** (down to 75% health; the bar is obsidian purple): heavy, but not slow to follow you (about 1.5 blocks a
   second; 2 in the Cracking stage and 2.8 in the Lava Core stage). It swipes at you up close, tears rocks
   out of the ground and throws them (they shatter; they never place a block), raises both fists for a moment and
   slams the ground (the shockwave hurts and throws you about 20 blocks), and charges on all fours, head first, from up
   to 32 blocks away, running all the way to where you stood half a second before it set off.
2. **Cracking** (75% to 40%; purple with glowing orange cracks): its volcanoes erupt (it can't be hurt while they do)
   and its shell starts breaking up, plates knocked loose and lava glowing through, more with every hit. A bit faster
   and more aggressive. Its volcanoes also spit **lava blobs** that land near you (never on you) and leave small pools
   of lava for 20-40 seconds, and it pounds the ground to raise **three stone golems** (left, right, in front of it). As it enters this stage,
   **four stone golems** climb out at once, and from now on its charge leaves a short **trail of fire**.
3. **Lava Core** (40% to 0%; lava red): its shell bursts off (it can't be hurt while it does). Faster and angrier:
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

It's a slow stone brute, so it can't be beaten easily from afar: **arrows and other projectiles** (tridents,
fireballs, snowballs) deal only **50%** of their damage in the Shell stage, **35%** in
Cracking and **15%** in the Lava Core. You can tell: they clink off the stone with a few sparks, and in the Lava Core
they hiss and burn up in a puff of smoke. Melee hits are never reduced, and its minions take full damage from
anything. The minions are quick on their feet: stone golems walk about 3 blocks a second and lava golems about 4.6
(a player walks 4.3 and sprints 5.6). **Pickaxes crack the shell:** in the Shell and Cracking stages a pickaxe hit
(any pickaxe, modded ones too) does **twice** its damage; in the Lava Core it's a normal hit. The Colossus keeps facing
the player it's fighting nearly all the time, except while it gallops.

A big hit never skips a stage: its health stops at 75% and at 40%. When it dies it screams, sinks down and **cools
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
different pose and look (previews: `boss-previews/cinder_wastes/cinder_colossus/trophies.png`), and every trophy can show any of them:

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
three-point crown, a ragged moth-eaten cape, and a twisted staff whose green claw holds a glowing green orb. Fire and
lava never hurt him.

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
- **250 health**, armour 6, quick on his feet (about 4.7 blocks a second, 6.2 in his Rage; you walk at 4.3). He keeps
  facing you.
- **Staff sweep** up close: 4.5 blocks in front of him, 9 damage (11 in his Rage), with a strong knockback, about
  every second. **His staff is poisoned**: the sweep and the spear charge give Poison II for 4 seconds (5 in his Rage),
  about 3 to 4 hearts more.
- **Spear charge** from 5 to 24 blocks: he crouches for 0.4 s with his staff levelled like a spear, then dashes at
  9 blocks a second (11 in his Rage) to where you were standing. If he catches you it's 6 damage (8) and straight into
  a sweep; if you dodge, he skids and needs half a second to turn round.
- **Poison shot** from 5 to 24 blocks, now and then: a clumpy plague ball that bursts into a small poison cloud (a
  steady Poison I while you stand in it); in his Rage, a fan of three.
- **How often:** at 5-16 blocks he charges 60%, throws poison 25% and runs in 15% (Rage: 60 / 35 / 5); at 16-24
  blocks charge 40, poison 30, run in 30 (Rage: 35 / 35 / 30); up close always the sweep.
- **Burrow:** every eighth of his health he loses (at about 219, 188 and 156 of his 250), he digs and dives into the ground. While he
  tunnels towards you (a dirt trail moves over the ground) he can't be hurt, and his rats burst out of the ground round
  you: **10, then 12, then 14** Plague Rats, however many of his rats are already about. Then he pops straight up
  4 blocks from you, facing you, without knocking you back.
- **Rage** at half health, 125 (he can't be hurt while it plays): stomping, red eyes, steam and the train whistle.
  Then he's faster and hits harder, throws three balls at once, and burrows three more times (at about 94, 63 and 31),
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
- Previews: `boss-previews/blightwood/rat_king/` (the rats in `rats/`, the sounds in `sounds/`: open `index.html` to listen;
  the earlier rounds in `round1/` and `round2/`). They're made by `tools/ratking/`.

---

## The Huntsman

One of the Ice Necromancer's three servants: a hunter in a wolf-head hood with a stag's antlers, riding his **Alpha
Moose**. He lives in **his own hunter village** in Rimefall (below): walk near it and he and the moose are already riding
their patrol round it, a loop just outside its lights, calm (only for a player whose Blind Oracle quest has reached him;
before that he and the moose sleep at home: see [Locked bosses](#locked-bosses)). He also comes from the **Huntsman Spawn Egg** or `/dross boss summon huntsman`, and
`/dross boss arena huntsman` builds a round snowy arena (41 blocks across, patches of packed ice, lantern posts round
the edge) with a chat button to summon him.

- **His villages.** Rimefall's areas come in groups of three, nearest the hub first and on out forever: in every group
  one area has the Huntsman's village (his lodge, the Alpha Moose's barn, 26 or 29 igloos), and each of the other two
  has a regular hunter village half the time (15-20 igloos round sunken courtyards). A village sits on the flattest
  ground of its area, never on or next to the big lake, and each part of it on its own ground: each courtyard with its
  igloos at its own height, the lanes between them climbing with a stair at each step, every decoration on solid
  ground, and the land between the parts left as it was (its trees and hills). Only the animals in its pens are tame
  (one that gets out is wild), and no moose or Pond Skaters spawn by themselves inside a village. A moose skull with
  its antlers stands on a tall post either side of every village's way in. Beat
  him (both of them) and his village stays without him for good; on Peaceful he never comes. They only appear in newly
  generated land.

- **He spots you.** The fight starts, and he lets out a wolf's howl and then blows his hunting horn, when he or the
  moose sees a foe within 30
  blocks: a player (not in creative or spectator), a hostile mob (he hunts those too) or, raged with the Rage Wand, a
  mob of another team. A hit starts it too.
- **Stage 1, The Wild Hunt** (one bar of 275 for both; hitting either of them takes it down). The two attack at the
  same time, each on its own timer. From the saddle he uses whatever fits where you are: right next to the moose mostly
  his spear, two bow shots wherever you are (ahead past the moose's head, to either side, or back over his shoulder;
  his arrows lead you as you run, and every one frosts and slows you), the Horn of the Hunt ahead (a cone of icy wind
  that pushes you away and slows you), a **bola** (held still 2 seconds, then slowed; at once if you run away from him)
  and the **arrow rain** anywhere (his arrows shoot up out of his bow and vanish high up, then 48 streak down out of
  the sky close round you with no warning, steering after you as they fall: half a heart each, and you take several). He hunts Pond Skaters, grown Rimehorns and grown moose too (never their calves). Meanwhile the moose rams, tosses you, stomps, bellows and stamps
  cracks across the snow up close, charges (6.5 hearts) and circles you further out, and kicks anyone behind it (4.5).
- **The split:** at 0 the moose rears with a neigh, and once it's up on its hind legs he leaps off and blows his horn;
  neither can be hurt for 2 seconds. Then each has his own bar: **The Huntsman** (175) and **Alpha Moose** (200). (While he
  rides, the two are drawn as one model, so he moves exactly with the moose; they become two at the split.)
- **Stage 2:** on foot (about 4 blocks to the top of his hood) he uses spear combos, a leaping strike, a thrown spear
  (it comes back to his hand), an aimed shot after a frost glint (it freezes you for a moment), a backstep and shot (he
  leaps back three blocks, shooting at the top of the leap, and is ready again as he lands), the arrow rain, the bola,
  the horn, and whenever those are all cooling down, a quick shot (2 hearts), so at range he keeps shooting.
  The moose rams, tosses you high, stomps, bellows, charges, kicks, and stamps cracks across the snow: where they end a
  clump of small spikes of clear ice bursts up, then a big one 1.5 blocks tall under you that throws you up, slows and
  frosts you. The moose goes for the closest player, he goes for the furthest. He likes to keep his
  distance: when you come close he backs off, at range he circles you, far off he closes in, and he keeps out of the
  moose's way.
- **Kill one** and the other gets angry: its bar turns blood red, and it's faster and hits harder.
- **Kill both:** the advancement **The Hunter Hunted** for whoever got the last kill and everyone within 40 blocks,
  and each of them gets a **Huntsman Trophy** (No. 1, Riding Down), straight into their inventory. Removed with `/kill`,
  they give neither.
- **Trophies:** a one-block miniature of the Wild Hunt in five poses (the user's picks): 1 Riding Down, 2 Rain of Arrows,
  3 The Rearing, 4 The Rearing Shot (the moose rearing, him turned in the saddle with his bow drawn level back over its
  rump), 5 The Parting Shot (the
  moose's ice slam, the clear ice bursting up, as he leaps off its back shooting). It works like the other trophies:
  right-click a placed one to change its pose. Made by `tools/huntsman/trophies.py` (previews in
  `boss-previews/rimefall/huntsman/trophies/`); `/dross showcase trophies huntsman` places all five in a row.
  The Alpha Moose drops its skull (a block you can mount: see the items below) every time it dies.
- **The Rage Wand:** raging either of them rages both, on the same team.
- **His hunting ground**: a village's Huntsman hunts animals and monsters only inside his patrol loop or just past it,
  and only in Rimefall: he never runs off into the land round his village or another biome to kill things.
- **Running away** from his village's pair: they chase you up to about 24 blocks past their patrol loop, and no further:
  there they stand and shoot (the arrow rain too) while you're within about 40 blocks. Get further than that out there,
  or leave Rimefall, and they let you go and walk back home, still hurt: they never vanish, so you can come back and
  finish the fight. They don't start a fight with a player standing outside Rimefall.
- **Stay away** (no one within 64 blocks for 3 minutes) and the fight is broken off: both are back at full health (even
  one you'd killed), him in the saddle, calm (a village's pair back home on their patrol).
- His thrown spear is drawn with the Moonsilver Spear's model, made big.
- **His sounds** are original (made from scratch, like the Rat King's): the user picked which of a few versions each
  one plays. His horn is a goat horn, and his grunts, hurt and death sounds are being picked.
- `/dross showcase boss_designs huntsman` shows him riding, on foot and the moose alone, with a button for every
  animation; `/dross showcase bossbars huntsman` shows his boss bars; `/dross showcase boss_sounds huntsman` lists
  every version of his and the moose's sounds with a button that plays it.

### The Frostfolk

Rimefall's villagers: eleven callings, about 2 blocks tall, built like a pillager, each in a blue hood of its own style
with no face under it, only darkness and two glowing blue eyes, gloved, in simple clothes, carrying its own tools in
its fist (long ones stood upright, short ones held out ahead like a Minecraft tool), with its own voice. Three spawn eggs (Frostfolk Warrior, Worker and Elder) each bring a calling
of that kind at random. They live in Rimefall's villages, 1.2 to 1.4 for every bed (a regular village 15 to 35, the
Huntsman's 64 and 65): the elder in the meeting hall, a worker at each workplace (the butcher in the Cookhouse, the tanner in the
Tannery, the fisher in the Ice-Fishing Hut, a farmer in each farm, a herder in the Storehouse and the barn, the archer
in the Workshop), at most 2 warriors in each Barracks and the rest on guard in the courtyards, one or two horn-blowers, the rest in their homes and round the courtyards'
fires. Each wanders within 24 blocks of where it started and walks back there after a fight (angry, it chases you
anywhere). The Huntsman's blows never hurt them. They also come from their spawn eggs.

- **Warriors:** the spearman (a peaked hood, a wolf pelt, spear and round shield, 30 health), the axeman (a fur-trimmed
  hood, an axe of ice, 28), the pikeman (a tall pointed hood, a long spear, 28), the archer (a deep cowl, his bow carried level in his fist and a brown quiver with ice fletchings on his back, 25;
  he keeps his distance and shoots) and the horn-blower (a hood with wolf's ears, a spear and the village's flag, 26; when
  his village turns on someone he blows his horn and everyone near gets Speed and Strength for 30 seconds).
- **Workers** (20 health, fighting with their tools): the farmer (a Rimehorn's horns on his hood, a hoe and a basket of
  Frostroot), the butcher (a cleaver and an apron), the herder (fur ear flaps, a crook), the fisher (a wide-brimmed
  oilskin hood, a rod with a fish on its hook) and the tanner (a fringed capelet and a roll of hide on his back; nothing in his hands).
- **The elder** (50 health): a small white scar over one eye (that eye gone), a long white beard, a sword of ice in its
  sheath on his back, drawn when he fights. He's the one to talk to.
- **Calm** they wander and hum. Right-click one and it talks to you in chat (the elder has the most to say).
- **Hit one** and it fights you back until you're about 16 blocks from it. **Kill one** (it tips over like any mob):
  every horn-blower blows his horn and every one of them within 96 blocks rushes you, until you've left their village
  (80 blocks from where each lives). Hit the elder and he stays angry until you've been away (48 blocks off) for 10
  minutes.
- They hunt Pond Skaters, and Pond Skaters hunt them; hurt by one, a villager fights back and the others near come to
  help.
- To keep a village smooth, they're drawn only within 40 blocks, and the villages' fires (Village Fire, a campfire to
  look at) give only a wisp of smoke now and then.
- `/dross showcase frostfolk` lines up one of each (the warriors in front, the workers and the elder behind) with signs;
  right-click one to hear it talk, and the chat's buttons play their animations. Previews:
  `boss-previews/rimefall/frostfolk/` (made by `tools/rimefall/mobs.py frostfolk` and `tools/huntsman/folk_preview.py`).

---

## The Emberdactyl

A giant volcanic pterosaur, 33 blocks across its wings, one of the Fire Necromancer's servants. It lives on its own
**volcano** in the Cinder Wastes: one in every group of three Cinder Wastes areas, never where a Cinder Colossus lair is,
built (in the background) the first time you come within about 384 blocks. The volcano is 70 blocks tall, with a lava lake
in its crater, the **egg** on a basalt pillar in the lake, rivers and pockets of lava down its flanks (all still lava that
never spills), and a lit path winding up to a lookout over the crater. `/dross goto lair emberdactyl` takes you to the
foot of its path.

**The fight** (400 health): it perches on a rock ledge on the crater's rim, facing out, guarding its egg. Come within 60
blocks of the volcano's foot (once your Blind Oracle quest has reached it: until then it ignores you and its crater is
warded, see [Locked bosses](#locked-bosses)) and it screeches (you hear it from far off), takes off and its boss bar appears. It fights
mostly in the air, circling you, and never strays more than 100 blocks from its nest:
- **Glide in**: from far off it glides at you low, fire dropping off it onto the ground, and dives once it's close.
- **Dive bomb** (7 hearts within 7 blocks of where it crashes, a ring of blasts and fire, everything thrown out and up):
  then it lies stuck on the ground for 3 seconds, your chance to hit it up close. On the ground it may peck you with its
  obsidian beak, five pecks in a row (4 hearts each), each one hitting everything within 5 blocks of where its beak
  strikes the ground (about 14 blocks in front of its feet) and everything between there and under its chest.
- **Fire tornadoes**: its wings whip up two (three at half health) that chase you, pull you in, burn you and throw you
  up into the sky.
- **Strafing run**: low over you in a line, breathing fire (4 hearts and fire), leaving fire on the ground.
- **Talon grab**: it flies in close, then swoops onto you and catches you (it never swoops when it can't reach you).
  You hang from its talons (look round as you like; sneak to struggle free) as it carries you up, always through open
  air, holds you a moment and drops you close to where it caught you (the young drops you higher). However high it
  drops you, the fall never takes more than half your health.
- **Wing buffet** (it swoops down to you first): a blast that throws you about 25 blocks. **Screech**: about 20 blocks.
- **Peck in the air**: against anything flying (another Emberdactyl, a Wither, a phantom, you on elytra) it bites in
  mid-air, three quick pecks of 4 hearts, its body surging forward with each.

It can't fly through blocks: walls, hills and roofs stop it (its wings too), and it rises over what's in its way.

At half health it gets faster and its volcano starts raining lava bombs round the crater. When it dies its body breaks
apart, its egg bursts open and its **young reborn** (175 health) bursts out and comes straight for you: the same moves,
just as hard, but 2.5 times as fast with almost no rest, a dash straight through you, and a solid trail of fire 2
blocks wide on the ground wherever it flies. Its egg lies broken apart in the nest, a bowl of lava with its pieces round it. Dying, either one glides down to
flat ground and lies there. Beat the young (it hardens into obsidian) and everyone near gets the challenge advancement
**Wings Clipped** and a trophy (five poses, right-click to change). It never comes back.

It also comes from its spawn eggs (the adult alone; the young has its own) and `/dross boss summon emberdactyl` /
`emberyoung`: those fight from the moment they appear; `/dross boss arena emberdactyl` builds a volcano with both in front of
you. Its boss bar is a volcano between two burning wings (`/dross showcase bossbars emberdactyl`); its trophies:
`/dross showcase trophies emberdactyl`.

## The Rage Wand

A creative-only testing tool in the Dross tab (after the Admin Sword), with five **teams**: **right-click** to switch
the wand between Blue, Red, Green, Yellow and Purple (its crystal changes colour and the team name shows above your
hotbar). Hitting a mob with it does no damage; it gives the mob **Rage** and the wand's team (dust in the team's
colour floats over it). Hit it again with another colour and it switches team. Rage and teams never wear off and
can't be cured, not even with milk.

- Raged mobs **never fight their own team**: they never target, chase or swing at a teammate, and the team beats
  natural enmity (a blue iron golem and a blue zombie, or a blue pillager and a blue iron golem, leave each other
  alone). If two mobs were already fighting when the second one joins the first's team, both stop at once. Each goes
  for the nearest raged mob of another team within 40 blocks that it can see (never one behind a wall it hasn't seen),
  and while an enemy team is near they **ignore players**,
  even ones who hit them. With no enemy near they behave normally towards everything but their own team, and keep
  their team, so the winners stay ready for the next fight.
- No new animations: every mob uses its own attacks (zombies swing, skeletons shoot, creepers explode, golems smash).
  Animals with no attack of their own (cows, pigs, sheep, chickens, villagers...) **headbutt** for 1 heart.
- **Bosses** work too, each with its own moves: raging the Cinder Colossus makes it fight; raging the sleeping Rat
  King wakes him; raging the Huntsman or his moose rages both, and the Wild Hunt fights with its spear, arrows, horn
  and the moose's charges; raging an Emberdactyl or its young wakes it, and the adult and the young raged on two
  teams fight each other in the air (talons, pecks, buffets, screeches, tornadoes). Their golems
  or rats (the ones already out and every one they summon) **join their team**, never fighting them or each other.
- The **Wither** fights with its skulls, and the **Ender Dragon** charges through its enemies, swings round and spits
  its fireballs at them; with nobody to fight it circles over where you raged it.
- A raged **Ender Dragon** or **warden** shows a plain health bar at the top of the screen to players within 96
  blocks.
- **Many at once:** sneak + right-click with the wand casts a **Rage Burst**: every mob within 8 blocks of what you're
  looking at joins the wand's team. The **Rage Bow** (creative only) is a hitscan gun: right-click fires instantly
  where you look (hold it to keep firing), and every mob within 5 blocks of what it hits joins its team; sneak +
  right-click changes its team.
- In the **bedrock test arena**, raged mobs find raged enemies anywhere in it (once they can see them).
- It won't rage showcase mobs or the hub's survivors. The **trader** can be raged: he headbutts,
  and beaten down to half his health he teleports home and heals as always.
- **Players on teams:** `/dross rage team <targets> <colour>` puts players and mobs on a team (`@s` for yourself, a
  player's name, `@a`, or `@e[type=zombie,distance=..10]`...; mobs get the Rage as from the wand), and
  `/dross rage clear <targets>` takes them off. Hitting another player with the wand puts them on its team. A player
  on a team fights with it: the other teams' raged mobs come for you (in survival or adventure: in creative or
  spectator mode you're nobody's enemy, whatever your team), your own team's never do, and nothing you and your teammates do hurts each other. You keep your team when
  you die.
- **Seeing the teams:** while you hold the wand, every raged mob and every player on a team round you gives off
  sparks of its team's colour.

---

## The elemental knights

Two knights of each of the five elements; they'll guard broken Dross portals in their element's Overworld biomes (the
ruins aren't in the world yet). For now they come from their **spawn eggs** (the end of the Dross tab's spawn eggs).
Five of them, a mix the world's seed picks, also besiege the Fallen Cathedral (see [The siege](#the-siege)).
Each is hostile, never despawns (no boss bar), calls the other knights near
when hurt, shrugs off its own element (fire, cold, poison, lightning or falls), and fights with two moves:

| Knight | Health | Armour | Move 1 | Move 2 |
|---|---|---|---|---|
| Thawed Axeman (ice) | 90 | 8 | Swing: 4.5 hearts, chills | Smash: ice spikes burst up in a line to you, a big one under you (4.5 hearts, thrown up, frozen) |
| Thawed Spearman (ice) | 90 | 10 | Thrust: 4 hearts, long reach | Shield wall: he takes 30% damage while poking you five times (2 hearts each) |
| Blight Warden (poison) | 80 | 6 | A poison ball from his staff (a cloud of poison) | Blight rain: poison rains 7 blocks round him for 4 s |
| Sporebound (poison) | 90 | 6 | Reap: 4.5 hearts all round him, poisons | Spore burst: sickness, poison, a moment blind, pushed away; poison clouds left |
| Cinder Knight (fire) | 200 | 10 | Hammer swing: 9 hearts, sets you burning | Slam: a shockwave of fire (up to 9 hearts), a ring of fire |
| Flamehand (fire) | 70 | 6 | Fireballs from his fists (4.5 hearts, burning) | The great fireball: bursts round where it lands (up to 12 hearts) |
| Wreck Knight (thunder) | 110 | 12 | Anchor throw: hooks you on its chain and hauls you to him | Anchor slam: 5.5 hearts with a crack of lightning, stuns |
| Stormblade (thunder) | 70 | 6 | Two quick cuts (6 hearts each) | Lightning dash: through you and past, lightning left behind that stings |
| Geode Knight (earth) | 100 | 10 | Pick: 5 hearts | Crystal burst: rings of amethyst burst up round him, one under you |
| Rockfall Knight (earth) | 110 | 12 | Boulder throw: a lumpy mossy boulder, 4.5 hearts | Rockfall: a shockwave, then mossy rocks rain down round you |

None of their moves breaks a block; the only fire they leave is temporary and goes out by itself. Every number is in
`boss/knight/KnightTuning` (and `KnightKind`). Their looks and animations come from `tools/knights/` (`mobs.py`; the
statues on show from `build.py`), their sounds from `tools/knights/sounds.py` (all made from scratch: each element its
own, voices that ring inside a helm rather than a person's, the heavy Wreck Knight deep and the quick Stormblade light),
their ruin candidates from `tools/knights/ruins.py`. See the ten with `/dross showcase knights` (with buttons that make
them play their moves), hear them with `/dross showcase boss_sounds knights`.

You can see each big move coming: its weapon gathers its element as the knight winds up, and the biggest moves show
where they'll land (a poison cloud gathering, dust falling where rocks will drop, sparks where lightning will strike).
Every slam sends a ring of the ground's blocks rolling out, a blow that lands bursts its element on you, and a knight
bursts into its element as it dies. Up close they're alive with it: the Cinder Knight sheds embers, Flamehand's fists
burn, the Thawed Soldiers' breath frosts, spores drift off the Sporebound. `tools/knights/audit.py` checks their
animations for parts passing through each other or the floor, snaps and spins.

**Their ruins** (candidates, not in the world yet): every area of each element's biomes has its own 4-5 small fight
spots round a broken portal frame, built from that area's blocks: 76 in all (the user's picks). Ice: snowy plains, taiga, frozen shore.
Poison: swamp, mushroom fields, dark forest, jungle, forest. Fire: desert, savanna, badlands. Thunder: warm shore, cold
shore, stony shore, riverbank, and the storm peaks (mountain tops). Earth: plains, meadow and cherry grove, windswept
hills, peaks, and big caverns underground. The shore ruins stand on the beach with the sea behind them, some reaching
out into it. Build them all, the five elements side by side, with `/dross showcase knight_ruins`, or one element's
with `/dross showcase knight_ruins <element>` (pictures in
`boss-previews/knights/ruins/index.html`).

## Advancements

All Dross advancements live in their own **Dross** tab, which stays hidden until you pick up a Weathered Letter.

| Advancement | How | Notes |
|---|---|---|
| Dross (tab root) | Pick up a Weathered Letter | Silent, opens the tab |
| A Weathered Letter | Pick up a Weathered Letter | |
| Knightfall | Clear three knight ruins for the Dross Trader | Only ruins you clear for the first time, while his quest is going |
| The Portal Orb | Have the Dross Trader make you a Portal Orb | |
| Siege Broken | Bring down the five knights guarding the Fallen Cathedral | Challenge, under *The Portal Orb* beside *Enter the Dross* (not in the chain: if someone else broke the siege first, nothing's missing before *Enter the Dross*). Goes to the player who killed the last knight and everyone within 64 blocks of the cathedral; never for `/kill` |
| **Enter the Dross** | Step through the blue portal | Challenge, the main achievement |
| The Oracle's Plea | Hear the Blind Oracle's story | Under *Enter the Dross*: the start of the Oracle's chain |
| Giant Slayer | Bring down three of the Silverwood Giants | The Oracle's quest (the kills count only while it's going) |
| Into the Ashes | Slay three creatures of the Cinder Wastes | The Oracle's quest |
| Set in Stone | Defeat the Cinder Colossus | Challenge, under *Into the Ashes*. Goes to the player who killed it (their arrows and pets count) and everyone within 40 blocks when it dies |
| Beneath the Rot | Kill three of Blightwood's creatures | The Oracle's quest |
| Down the Drain | End the Rat King's reign beneath Blightwood | Challenge, under *Beneath the Rot*. Goes to the player who killed him and everyone within 40 blocks when he dies |
| First Frost | Hunt three of Rimefall's grown beasts | The Oracle's quest |
| The Hunter Hunted | Bring down the Huntsman and his Alpha Moose | Challenge, under *First Frost*. Goes to the player with the last kill and everyone within 40 blocks |
| Wings Clipped | Bring down the Emberdactyl and the young reborn from its egg | Challenge, under *The Hunter Hunted*. Goes to the player who kills the young and everyone within 64 blocks |
| **Defeat the Dross** | Hear the Blind Oracle's last words, after the Emberdactyl | Challenge, with blue fireworks: "You've beaten everything the Dross has to offer. Come back later to face more dangerous foes." |

The Oracle's quests make one chain under *Enter the Dross*, from *The Oracle's Plea* to *Defeat the Dross*, with the
four bosses' challenges in it (until November 9, 2026 those hung straight off *Enter the Dross*).

---

## The look

The Dross's color is **electric blue**: main `#2E6BFF`, deep cobalt `#0B2A9E`, highlight `#7FA8FF`. It's used for:
- the portal, its particles and screen swirl;
- the frame's cracks;
- the trader's glow and particles;
- the arrival title.

Every color lives in one place, `DrossColors.java`.

---

## Commands

All need cheats (operator level 2). Each name says what kind of thing it's about and which one, so the list stays
clear as more bosses and places are added: `goto <place>`, `showcase <kind> [<boss>]`, `boss <action> <boss>`,
`trader <action>` and `quest <action>`.

| Command | What it does |
|---|---|
| `/dross goto cathedral` | Teleports you in front of the Fallen Cathedral's frame, facing it, generating its land first if needed. In a world with no site (superflat, or Generate Structures off), it says so instead. While its siege holds, a player in survival or adventure is bounced straight back out to its great door (it says so). |
| `/dross goto hub` | Teleports you to the hub (the Shattered Spire) in the Dross, onto the arrival step (the first time, its land is generated). |
| `/dross goto trader` | Teleports you onto the doorstep of the trader's Rift Chapel, facing its door, generating its land (and the village's) first if needed. |
| `/dross goto biome <name>` | Teleports you to safe ground in the nearest example of a Dross biome: `silverwood_forest`, `cinder_wastes`, `blightwood`, `rimefall` or `gravecrags`. |
| `/dross goto lair colossus` | Teleports you to the nearest Cinder Colossus lair's entrance. If it isn't built yet, it says so, builds it, and teleports you once it's ready. |
| `/dross goto sewer` | Teleports you to the foot of the nearest regular sewer's pipe, facing it. |
| `/dross goto lake` | Teleports you to the shore of the nearest of Rimefall's big frozen lakes, facing it. |
| `/dross goto lair huntsman` | Teleports you to the way into the nearest Huntsman's village, facing in, and says whether he's at home or beaten. |
| `/dross goto lair emberdactyl` | Teleports you to the foot of the path up the nearest Emberdactyl volcano (one in every group of 3 Cinder Wastes areas), building it first if it isn't yet. Within 60 blocks of its foot, the Emberdactyl wakes. |
| `/dross goto village` | Teleports you to the way into the nearest hunter village (either kind). `/dross goto village <x> <z>`: the one nearest that spot. |
| `/dross locate villages` | A test tool for now: lists the 5 nearest hunter villages (which kind, where, how far, how many igloos), each with a [go] button. |
| `/dross goto lair ratking` | Teleports you into the nearest rat cave under Blightwood, at a tunnel mouth facing the Rat King's nest, and tells you whether he's asleep there, awake, or already beaten (he never comes back). |
| `/dross trader sendhome` | Sends the nearest trader whose land is loaded home. |
| `/dross quest status` | Shows your quest progress: the letter, the Dross Trader's quest (his offer, the ruins cleared, the orb, the marker over him, whether the cathedral's portal is lit), the Guide Book, and the Blind Oracle's quest (which one, how far, the marker over him). |
| `/dross quest reset` | Clears your quest progress, the Guide Book flag, the trader's and the Blind Oracle's quests, and revokes the Dross advancements (except the four bosses': *Set in Stone*, *Down the Drain*, *The Hunter Hunted*, *Wings Clipped*). |
| `/dross quest complete` | Finishes the trader's quest for you, as if he'd just made you the Portal Orb (*Knightfall* and *The Portal Orb*; no items given). |
| `/dross siege status` | Shows the Fallen Cathedral's siege: holding (how many of its five knights have fallen) or broken (when, by whom), its footprint, and each knight (its kind and element: on guard where, not come yet, or fallen). |
| `/dross siege reset` | The siege holds again: its knights about now go, and they come back (their spots picked again) the next time a player is near. Takes *Siege Broken* from you, so you can earn it again. |
| `/dross siege break` | Breaks the siege at once (its knights about now go); the players near see the barrier fall. Gives nobody *Siege Broken*. |
| `/dross quest skip` | Finishes the Blind Oracle's quest step you're on (with its own advancement; a boss's only comes from beating it) and makes the next quest wait for you. |
| `/dross quest set <quest>` | Jumps you to one of the Blind Oracle's quests, by its advancement's name (`oracle_plea`, `giant_slayer`, `into_the_ashes`, `set_in_stone`, `beneath_the_rot`, `down_the_drain`, `first_frost`, `the_hunter_hunted`, `wings_clipped`, `defeat_the_dross`), waiting to be given: its land or boss opens once you've heard him offer it. |
| `/dross showcase boss_designs colossus` | Spawns the Cinder Colossus (3G Saddleback), frozen (dormant), with its sign and clickable controls in chat. Right-click it to move it on a stage; sneak + right-click to make it die. |
| `/dross showcase boss_designs colossus dormant` / `erupted` / `core` | Puts every showcase golem nearby into that stage. |
| `/dross showcase boss_designs colossus erupt` / `break` / `die` | Replays the volcanoes erupting / the shell breaking off / the death (cooling into an obsidian statue). |
| `/dross showcase boss_designs colossus walk` / `idle` | Knuckle-walking on the spot / standing. |
| `/dross showcase trophies colossus` | Places the five Cinder Colossus trophy designs in a row in front of you, each on a pedestal facing you with a sign giving its number and name. Running it again replaces the row. |
| `/dross showcase trophies ratking` | Places the Rat King's five trophy designs in a row in front of you, each on a pedestal facing you with a sign. Running it again replaces the row. |
| `/dross showcase trophies huntsman` | Places the Huntsman's five trophy designs in a row in front of you the same way. |
| `/dross showcase boss_designs ratking` | Spawns the Rat King (Gnawrick 1C) frozen, with signs and clickable animation buttons in chat. Right-click one to play his next animation; sneak + right-click: he dies. |
| `/dross showcase boss_designs ratking sleep` / `wake` / `idle` / `walk` / `burrow` / `popup` / `rage` / `charge` / `die` | Plays that animation on every showcase Rat King nearby (rage turns their eyes red until they sleep). |
| `/dross showcase knight_ruins` | Builds all the knight ruin candidates ahead of you, the five elements side by side (ice on the left), each element's two knights frozen on guard in its ruins, with [go] buttons. |
| `/dross showcase knight_ruins <element>` | Builds that element's knight ruin candidates ahead of you (`ice`, `poison`, `fire`, `thunder`, `earth`): a row for each of its biome areas, each ruin on its area's ground with the element's two knights frozen on guard, with [go] buttons. |
| `/dross showcase boss_sounds knights` | Lists every knight's sounds with play buttons. |
| `/dross showcase knight_ruins idle` / `walk` / `move1` / `move2` | Every knight on show near you plays that (its first or its second move). |
| `/dross showcase knights` | The ten knights, frozen, element by element in front of broken portals, with buttons to stand by each. |
| `/dross showcase knights idle` / `walk` / `move1` / `move2` | Every knight on show near you plays that. |
| `/dross showcase boss_designs knights` | The knights' design candidates (the statues they were picked from). |
| `/dross showcase rats` | Lines up the Rat King's two rats on pedestals with signs: the Plague Rat and the Plague Ravager. |
| `/dross showcase boss_sounds ratking` | Lists every Rat King and rat sound in chat, each with a [play] button. |
| `/dross showcase boss_sounds huntsman` | Lists every Huntsman and Alpha Moose sound in chat, with a numbered button for each version (hover to see what it is). |
| `/dross showcase rats idle` / `run` | Every showcase rat nearby idles / runs on the spot. |
| `/dross showcase rimefall_mobs` | Lines up Rimefall's animals, frozen, on ice pedestals with signs: a male, a female and a calf Rimehorn, a bull, a cow and a calf moose, and a Pond Skater. Right-click one to play its next animation. |
| `/dross showcase rimefall_mobs idle` / `walk` / `run` / `charge` / `eat` / `attack` | Plays that animation on every showcase animal nearby (attack: the Rimehorn's horn flick, the moose's ram, the Pond Skater's stab). |
| `/dross showcase frostfolk` | Lines up the eleven Frostfolk (the warriors in front, the workers and the elder behind), frozen, with signs. Right-click one and it talks to you. |
| `/dross showcase frostfolk idle` / `walk` / `run` / `attack` / `horn` / `talk` | Plays that animation on every showcase Frostfolk nearby (attack: each one's own; horn: the horn-blower's). |
| `/dross showcase mob_sounds rimefall` | Lists every Rimehorn, moose and Pond Skater sound in chat, each with a [play] button. |
| `/dross showcase boss_designs cinder_wastes` | Builds a gallery of the fire servant candidates (fifteen possible bosses, each with its variations behind it), frozen, with signs and [go] buttons in chat. Use a flat world. |
| `/dross showcase boss_designs emberdactyl` | Builds a gallery of the Emberdactyl (33 blocks across its wings), its young and its egg's five looks (the last one hatched), then its volcano behind them (about a minute): lava lake, nest, lava cascades, a lit path up to a lookout with its hoard (empty chests and gold). The adult and the young move, fire falling from their wings as they fly: chat buttons play each of their animations (hover, fly, glide, dive, screech, gust, grab, snap, perch, walk, death); right-click one for its next. Use a flat world. |
| `/dross showcase boss_designs emberdactyl <animation>` | Plays one animation on the Emberdactyl and its young near you (with its sounds). |
| `/dross showcase cinder_mobs` | Lines up the Cinder Wastes' mobs with signs: the Pallbearer and the Cinder Hermit in eight shells; chat buttons play their animations (idle, walk, swipe, slam, roar, snap, pinch, hide, peek, come_out, death). |
| `/dross showcase cinder_mobs <animation>` | Plays one animation on the Cinder Wastes' mobs near you (with its sounds). |
| `/dross showcase oracle_spell [big\|medium]` | The Blind Oracle's spell: the nearest Blind Oracle casts it at once towards where you look (with none near, it's cast from the ground in front of you): the big one (a land opening) or the medium one (a boss's home). Nobody's quest changes. |
| `/dross showcase barrier_fall` | A stretch of a land's barrier falls in front of you, as it does the first time you come to a land the Oracle has opened. |
| `/dross showcase silverwood_giants` | Lines up the Silverwood Giant's three looks (G1, G2, G3), frozen, on Silvermoss with signs; right-click one to play its next animation. Running it again replaces the row. |
| `/dross showcase silverwood_giants <animation>` | Plays that animation on the three (idle, walk, run, warn, smash, sweep, stomp, throw, hurt, death), with its sounds. |
| `/dross showcase boss_sounds emberdactyl` | Lists the Emberdactyl's and its young's sounds (its screech, wingbeats, gust, dive...) with play buttons. |
| `/dross showcase bossbars emberdactyl` | Shows the Emberdactyl's boss bar (a volcano between two burning wings) for a minute, the adult's over its young's, draining and filling. |
| `/dross showcase trophies emberdactyl` | Places the Emberdactyl's five trophies in a row on pedestals with signs (right-click one to turn it through the poses). |
| `/dross showcase mob_sounds cinder_wastes` | Lists the Pallbearer's and the Cinder Hermit's sounds with play buttons. |
| `/dross boss summon colossus` | Summons a fighting Cinder Colossus 10 blocks in front of you, facing you (not on Peaceful). |
| `/dross boss arena colossus` | Builds a placeholder boss arena round you (a 31-block round floor of nether bricks, with a low wall and lights), with a chat button to summon the Colossus. |
| `/dross boss summon ratking` | Summons a fighting Rat King, asleep, 14 blocks in front of you, facing you (not on Peaceful). |
| `/dross boss arena ratking` | Builds the Rat King's placeholder arena round you (a 31-block round floor of dirt and coarse dirt), with a chat button to summon him. |
| `/dross boss summon huntsman` | Summons the Huntsman riding his Alpha Moose 16 blocks in front of you, calm until hit (not on Peaceful). |
| `/dross boss summon emberdactyl` / `emberyoung` | Summons the Emberdactyl (or its young) 30 (18) blocks in front of you, hovering over that spot, alone (not on Peaceful). |
| `/dross boss arena emberdactyl` | Builds an Emberdactyl volcano on the land in front of you, with its egg in the nest and the Emberdactyl perched on the rim (a few seconds' freeze). |
| `/dross boss arena huntsman` | Builds the Huntsman's placeholder arena round you (a 41-block round floor of snow and packed ice, with lantern posts), with a chat button to summon him. |
| `/dross boss arena bedrock [teams]` | Builds a test arena for any boss round you: an 89-block square bedrock floor with glowstone set in it, bedrock walls up to a bedrock roof 48 blocks up (room for the Emberdactyl), where nothing spawns by itself. With a number from 2 to 5 it's divided into that many equal sections by walls up to the roof, one for each Rage Wand team, its colour of glass in the middle of each floor. Chat buttons take you to each section, put everyone in each section on its team, open and close the walls between them, and summon each boss. |
| `/dross boss arena bedrock open` / `close` | Opens (or closes again) the walls between the bedrock arena's sections, so the teams meet. |
| `/dross boss arena bedrock team_up` | Puts every mob and player in each of the bedrock arena's sections on that section's team (the Rage Wand's Rage). |
| `/dross rage team <targets> <team>` | Puts players and mobs on one of the Rage Wand's teams (blue, red, green, yellow or purple): `@s`, a player's name, `@e[...]`. Players on a team fight alongside their team's raged mobs and against the others. |
| `/dross rage clear <targets>` | Takes players and mobs off their teams (a mob's Rage goes too). |
| `/dross goto ruin [<element>]` | Teleports in front of the nearest elemental knights' ruin in the Overworld (of that element: `fire`, `ice`, `poison`, `thunder`, `earth`), building it first if need be. |
| `/dross goto ruin <x> <z>` | The ruin nearest that spot (the locate list's [go] buttons). |
| `/dross locate ruins` | Lists the 8 nearest knight ruins (within 2,400 blocks) with their element, area, layout and distance, each with a [go] button. |

---

## Settings (config/dross-common.toml)

A settings file for modpack makers and server owners, made the first time the game starts. Every value defaults to the
mod's own tuning.

| Section | Setting | Default | What it does |
|---|---|---|---|
| `portal_site` | `minDistance`, `maxDistance` | 3500, 4500 | How far from the world spawn the Fallen Cathedral can be. |
| `trader` | `atVillages` | true | His Rift Chapel at the edge of a village; `false`: on its own, 384-1,536 blocks from spawn, no village laid out for it. |
| `trader` | `villageSearchRadius` | 3000 | How far from spawn his village can be. |
| `quest` | `letterChance` | 0.2 | The chance a Nether Fortress or Bastion Remnant chest holds a Weathered Letter (1.0: every chest). |
| `knight_ruins` | `spacing` | 800 | About how far apart the elemental knights' ruins are, on average (away from the ones near spawn). |
| `knight_ruins` | `nearSpawn` | 5 | How many ruins go near the world spawn, each in its own big biome (0: none). |
| `knight_ruins` | `nearSpawnRadius` | 1000 | The furthest those ruins can be from the spawn. |
| `knight_ruins` | `nearSpawnMinDistance` | 200 | The nearest any ruin can be to the spawn. |
| `knight_ruins` | `knightsReturnMinutes` | 10 | Minutes after both of a ruin's knights are beaten until they come back (only while no player is within 64 blocks; this one counts at once in every world). |
| `performance` | `plannerThreads` | 0 (automatic) | Helper threads for the Dross's boss-site planner (0: half the processors, 1 to 8; read at start). |
| `performance` | `buildMillisPerTick` | 40 | How many milliseconds a server tick may spend building a boss lair or volcano. |

The `portal_site`, `trader` and `knight_ruins` values (all but `knightsReturnMinutes`) are saved in each world the first time it loads
(`data/dross_world_settings.dat`, `DrossWorldSettings`), so changing the file never moves an existing world's cathedral
or trader; the log says when a world keeps settings that differ from the file. The code is `DrossConfig` (each area
reads its own values through it).

## Building and running

From the project folder, on Windows:

| Task | Command |
|---|---|
| Build the mod jar (`build/libs/dross-<version>.jar`; the tests and the design galleries' models stay out of it) | `.\gradlew.bat build --console=plain` |
| Run the game with the mod | `.\gradlew.bat runClient --console=plain` |
| Run the automated in-game checks | `.\gradlew.bat runGameTestServer --console=plain` |
| The same checks in a brand-new world | `.\gradlew.bat runGameTestServer -PgameTestWorld=world_check_1 --console=plain` (pick a folder name under `run/` that doesn't exist yet) |
| The performance test (the mod's mobs and bosses fighting, every server tick timed) | `.\gradlew.bat runGameTestServer -PgameTestNamespaces=dross_perf -PgameTestWorld=perf_check_1 --console=plain` (the numbers in `run/perf/server_perf.txt`) |

- **Logs:** `run/logs/latest.log`. **Crash reports:** `run/crash-reports/`.
- **Commands in-game:** create the world with **Allow Cheats: ON** to use the commands.
- **First-time setup:** if it fails with `NoSuchFileException` under `.gradle/caches/forge_gradle/maven_downloader`, re-run the same command.

### Tools (Python)
- **`tools/shrines/`:** generates the two grand shrine designs, the Fallen Cathedral and the Shattered Spire (the user's picks from ten; the other eight were removed).
  - It writes their structure templates (`data/dross/structures/shrine/*.nbt`), renders the previews in `shrine-previews/`, and `gen_java.py` writes `ShrineDesigns.java`.
  - See `tools/shrines/README.md`.
- **`tools/skins/`:** draws the 4 villager-model skins (`textures/entity/trader/`: 1 Hooded Watcher, kept for later; 2 The Archivist, the trader; 3 Ragged Seeker and 8 Blind Oracle, the survivors) and renders the front and back previews in `skin-previews/`; `quest_markers.py` draws the markers over the Blind Oracle's head (`textures/gui/quest_markers.png`, preview `skin-previews/quest_markers.png`).
- **`tools/terrain/`:** the Dross's terrain and blocks.
  - `worldgen.py` writes all the terrain data files (noise, biomes, ground cover, features, ores, spawn lists), and the structure files of the hub, the Fallen Cathedral and the Rift Chapels; every tuning number is a labeled constant at its top (among them how far trees, plants and ores keep off the hub).
  - `ward_particles.py` draws the locked lands' edge, its streaks and runes (`textures/particle/ward_*`), with a preview in `terrain-previews/ward_edge.png`.
  - `draw_blocks.py` draws the terrain blocks' textures and `block_data.py` writes their models, block states, loot, tags and recipes; previews go to `block-previews/`.
  - Top-down maps of a generated area are in `terrain-previews/`.
  - See `tools/terrain/README.md` (and `TERRAIN_DESIGN.md` for the design).
- **`tools/golems/`:** generates the Cinder Colossus designs and its trophies.
  - It writes their GeckoLib models, animations and textures (`geo/`, `animations/`, `textures/entity/cinder_colossus/`), renders the previews in `boss-previews/cinder_wastes/cinder_colossus/`, and writes `GolemDesigns.java`.
  - The trophies: `geo/block/` and `geo/item/colossus_trophy.geo.json`, `animations/block/colossus_trophy.animation.json`, `textures/block/colossus_trophy/`, `boss-previews/cinder_wastes/cinder_colossus/trophies.png` and `TrophyModels.java` (`python tools/golems/build.py --trophies-only` rebuilds just them).
  - `lair_preview.py` renders the lair previews (`boss-previews/cinder_wastes/cinder_colossus/lair_*.png`).
  - See `tools/golems/README.md`.
- **`tools/ratking/`:** generates the Rat King design candidates and his rats (models, animations, textures, the Zzz
  particle, `RatKingDesigns.java` and `RatDesigns.java`) and renders the previews in `boss-previews/blightwood/rat_king/`; its
  `sounds.py` makes all their sounds from scratch. It reuses the golem kit. See `tools/ratking/README.md`.
- **`tools/forest_giant/`:** the Silverwood Giant: `build.py` its three looks (models, textures, checks and previews in
  `boss-previews/silverwood/mobs/forest_giant/`), `mobs.py` its animations (and the moments the game reads), `sounds.py`
  its sounds, all made from scratch. It reuses the golem, Rimefall and fire kits.
- **Making a new boss:** follow `BOSS_GUIDE.md` (the whole process, from design candidates to the user's pick).
  Every boss's previews go in its own folder under `boss-previews/`.

---

## Project layout

Base package: `com.reg21meme.dross`. Client-only code is in each area's `client` subpackage.

| Package | What's in it |
|---|---|
| `dimension` | The Dross dimension: its terrain blocks and items (`dimension/block`), world generation features, tree decorators, the hub falloff and the filter that keeps features off the hub (`dimension/worldgen`), the biome finder, the sky and fog (`dimension/client`), mob gear, the hub's safe zone, the locked lands (`dimension/ward`) |
| `portal` | Frame and portal blocks, the Portal Orb, lighting, teleporting, the hub (a world-generation structure fitted onto the terrain), the arrival sequence |
| `world` | The portal site: its spot from the seed, the Fallen Cathedral as a world-generation structure, the tree tidy-up, ground fitting, the leak, its siege (its five knights and its barrier) (`world/shrine` holds the shrine designs) |
| `villager` | The trader, his Rift Chapel (a world-generation structure at a village the seed picks; the "once a village" rule), his shop, skins, the Admin Sword (`villager/hut` draws the chapel), the hub's survivors, the quest markers over the Oracle and the trader (`villager/client/QuestMarkerRenderer`) |
| `quest` | The Weathered Letter and its loot, quest progress, advancements, the Guide Book; the Dross Trader's quest (`quest/trader`: his talk, the knight ruins counted, the Portal Orb); the Blind Oracle's quest line (`quest/oracle`: the quests, their progress, kill counting, the rules for the locked lands and bosses) |
| `boss` | The bosses: so far the Cinder Colossus golem (`boss/golem`): its fight, minions, projectiles, temporary lava and fire, its GeckoLib renderers and the showcase golems; its trophies (`boss/trophy`: the block, its block entity and item, their GeckoLib renderers); the custom boss bar (`boss/client`); the lair (`boss/lair`); the wild golems; the logic of `/dross boss`; the locked bosses and their warded arenas (`BossLocks`, `BossWards`); the Silverwood Giant (`boss/giant`) |
| `command` | The `/dross` command and its showcases |
| `registry` | Every `DeferredRegister`: blocks, block entities, items, entities, particles, enchantments, loot modifiers, sounds, world generation features, tree decorators, density functions, placement modifiers, structures, the creative tab |

Mod metadata is in `src/main/resources/META-INF/mods.toml`, filled from `gradle.properties`. All player-facing text is in `assets/dross/lang/en_us.json`.
