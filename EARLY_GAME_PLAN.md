# Dross: Early Game Plan (from first steps to entering the Dross)

This document describes the full early-game journey: everything a player does before and during their first entry into the Dross. It replaces the current "test build" quest (nether star for compass, netherite ingot lights the frame at 0,0).

Items marked **PROPOSED DEFAULT** have not been confirmed yet. Build them as written unless told otherwise, but list them as open questions in the plan.

---

## Design goals

- The Dross is a **mid-to-late game experience**, not pure endgame. Players reach it after proving they have survived dangerous places, not after beating every vanilla boss.
- It should work well **alongside other mods** (modpack friendly).
- The first entry is a **quest**: memorable, one-time per player, with a clear story.
- The only way in is the castle portal and the only way out is the hub, so the Dross stays dangerous.
- The dimension's color identity is **electric blue** (deep cobalt / electric blue, distinct from vanilla's cyan soul fire). The Nether is red/orange/purple and the End is purple/yellow/black; blue belongs to the Dross.

---

## The journey, step by step

### Step 1: Normal survival
The player plays regular Minecraft. Nothing from the mod is required yet.

### Step 2: The quest begins
The player finds a **Weathered Letter** (custom item, readable like a written book) in **Nether Fortress or Bastion Remnant chests**. It says an old villager near a village close to spawn is seeking someone strong, and gives the rough direction or coordinates of his hut.

- Finding it means the player has started exploring the dangerous parts of the Nether, which marks the start of mid-game.
- Added to those chests with a Forge global loot modifier, so other mods' loot changes still work.
- **Spawn chance: 100% in every Nether Fortress and Bastion chest for testing.** Keep the chance as a labeled constant so it can be lowered later.
- Advancement: "A Weathered Letter", granted when the player first picks one up.

### Step 3: Meet the trader
- He lives in **his own hut at the edge of the nearest village** to world spawn (already built: village search, hut placement, dirt path, fallback to nearest plains or desert).
- All existing trader behavior stays: unkillable, 50-block home area, teleports home when lost, glows while a player is in his area, can't use portals.
- When a player first right-clicks him, he speaks in chat (with his name as the speaker), roughly: *"You've come about the rift? I don't believe you're strong enough. Show me. Bring me a skull from the Nether's fortresses, shards from the cities of the deep dark, and stone from the End."*
- Remove the testing chat message that announces his coordinates on join (keep `/dross trader` and `/dross trader home` for testing).

### Step 4: The three tributes
The player proves they have survived the three most dangerous places:

| Place | Tribute | Amount |
|---|---|---|
| Nether Fortress | Wither skeleton skull | 1 |
| Ancient City | Echo shard | 3 |
| The End | End stone | 15 |

**Important technical note:** vanilla villager trades only allow **two** cost items, so the tributes cannot be a single trade. Instead:
- The player **right-clicks the trader while holding a tribute item** to hand it over. He takes the required amount and confirms in chat.
- Progress is tracked **per player** (saved data). Tributes can be handed in in any order.
- The trader's normal trading screen should not open while the player is holding a tribute item he still needs.
- When all three are in, he gives the **Dross Compass** and says he knows how to forge a key, but needs materials (Step 5).
- Advancement: "Proven Worthy".

### Step 5: The key materials
The trader asks for:

| Material | Where it comes from | **PROPOSED DEFAULT** amount |
|---|---|---|
| Trident | Rare drop from drowned | 1 |
| Heart of the Sea | Buried treasure | 1 |
| Amethyst shards | Geodes | 8 |

- Same hand-in method as the tributes (right-click while holding, per-player progress, any order).
- When all are in, he forges the **Rift Key** (custom item, name can change): a crystal key with a blue heart. He gives it to the player with a line of dialogue.
- Advancement: "Keymaster".

### Step 6: Find the castle
- The **Dross Compass** points to the castle's sealed portal. It spins in other dimensions (existing behavior).
- Location: **0,0 for testing**. Later, a random spot 3,000 to 10,000 blocks from spawn, chosen when the world is created. Keep the location in one constant/config value. When it moves, already-made compasses must still point correctly (they should read the current location rather than store a fixed one).
- **The castle** replaces the bare frame. I will build it myself in creative and save it with a structure block as `dross:portal_castle`.
  - Until I provide it, use a **simple placeholder** (a small ruined shrine around the frame) so everything else can be built and tested.
  - The structure is placed once per world, at the site location, on flattened ground (reuse the existing site-placement logic).
- **The Dross is leaking out** around the castle:
  - Electric blue particles drifting near the portal.
  - Dead or withered plants and cracked blocks within about 16 blocks.
  - An ambient sound near the portal.
- The castle portal frame is **fully unbreakable** (existing frame protections).

### Step 7: Reopen the portal
- The player **throws the Rift Key into the sealed frame** (press Q while facing it), the same way the netherite ingot works today. This replaces the netherite-ingot activation.
- The portal lights in **electric blue** (texture, particles, screen swirl).
- **PROPOSED DEFAULT:** the **key is not consumed**. After the portal lights, the key pops back out to the player who threw it (it will matter for a later feature).
- Once reopened, the castle portal **stays open permanently** for everyone.
- Advancement: "The Rift Reopened".

### Step 8: Enter the Dross
- The castle portal leads to **one arrival point: the hub at Dross 0,0**.
- **The hub** is a safe zone:
  - No hostile mob spawning within about 48 blocks (**PROPOSED DEFAULT**).
  - Contains the exit portal, which returns players to the castle portal.
  - Portals cannot be built or lit anywhere inside the Dross.
  - A placeholder structure for now; I will design the real hub later with a structure block.
- On arrival, keep the existing sequence: the "The Dross" title and the four piano notes, then the "Entered the Dross" challenge advancement.
- **First arrival only:** the player receives the **Dross Guide Book** (a readable book explaining the dimension). Placeholder text is fine for now.
- **Advancements:** move all Dross advancements into **their own "Dross" tab** with its own root advancement and background, instead of the vanilla Minecraft tab. Early-game chain:
  1. A Weathered Letter
  2. Proven Worthy
  3. Keymaster
  4. The Rift Reopened
  5. Entered the Dross (challenge, purple)
  6. Rise! (challenge, purple, already exists; move it to the Dross tab)

---

## Visual changes

- **Electric blue replaces orange** across the mod: portal texture, portal particles, screen swirl, frame accents, teleport particles, risen undead puffs and wisps, the arrival title color, and glow outlines (Minecraft has real blue team colors, unlike orange).
- **New frame look:** an original texture, no longer based on obsidian. This also removes one recolored Mojang texture.
- Keep every color as a labeled constant so the exact shade is easy to tune.

---

## Trader shop after the quest

- Remove the current test trades: nether star for compass, the Admin Sword trade, and the Necromancy book trades.
- After the quest is complete, the trader sells:
  - **Necromancy I** book
  - **Deathforged I** book
  - **PROPOSED DEFAULT** prices: emeralds plus a book (suggest reasonable amounts in the plan).
- The Admin Sword and test-only books stay available in the creative tab.
- Note: Necromancy is being restructured into five levels (a new weaker level I, the old I to III shift up, admin becomes V). That restructure is a **separate task**; for this plan, the trader just sells "the lowest Necromancy level".

---

## What exists now and must change

| Currently built | Becomes |
|---|---|
| Testing chat message with the trader's coordinates | Weathered Letter found in Nether Fortress and Bastion chests |
| Trade: nether star for Dross Compass | Tribute hand-in quest, then compass |
| Netherite ingot lights the frame | Rift Key lights the frame (reusable) |
| Bare frame at 0,0 | Castle structure (placeholder until I build it) |
| Orange portal, particles, effects | Electric blue |
| Obsidian-with-orange-veins frame texture | Original electric blue frame texture |
| Arrival next to an auto-built return portal | Arrival at the hub at Dross 0,0 |
| Dross advancements in the vanilla tab | Their own Dross tab |
| Trader sells Necromancy I to IV and the Admin Sword | Sells Necromancy I and Deathforged I after the quest |

---

## Out of scope for this plan

These are planned for later and should not be built now:
- New terrain (gentle, old-Minecraft style), dense forests, silverwood trees, custom ores and crops.
- Boss towers, mini-bosses, the three elemental necromancers (Fire, Ice, Plague), the Earth General, the Storm King, and the dragon mount.
- The elemental relics (Magma Wand, Frostbreaker Charm, Cleansing Flask), the Great Mystic Bow, and special arrows.
- The Necromancy five-level restructure and Deathforged anvil changes.
- Boss exit portals.
- Player-built portals (Stormvine, a craftable frame, building portals anywhere in the Overworld, returning to the portal you came from, and protection for player-built frames).
- Config files for modpack makers (keep values as constants for now so a config can be added later).

---

## Open questions to include in the plan

1. Key material amounts (trident, Heart of the Sea, amethyst shards).
2. Is the Rift Key kept after use (proposed) or consumed?
3. Hub safe-zone radius.
4. Trader prices for Necromancy I and Deathforged I.