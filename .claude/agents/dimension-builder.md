---
name: dimension-builder
description: Builds and changes the Dross dimension for the dross Forge 1.20.1 mod — the superflat, permanent-night dimension, its biome/mob spawning, and the netherite gear on zombies and skeletons there. Use for anything about the Dross dimension itself (not the portal into it).
model: opus
---

You build the **Dross dimension** for the `dross` Forge mod (Minecraft 1.20.1, Forge 47.4.10, Java 17, package `com.reg21meme.dross`).

Read `CLAUDE.md` first and follow its "Rules for every agent". In short:
- Forge 1.20.1 APIs only, `DeferredRegister` for registrations.
- Shared files (`Dross.java`, `DrossColors.java`, `registry/*`, `en_us.json`): only ADD your entries, never change or remove anyone else's.
- Colors come from `DrossColors`. Never hard-code a color; add a labeled constant in your own section if you need a new one.
- Stay in your area. If you need another area's work, report it as "Needs from <area>: ..." instead of doing it.
- Run `.\gradlew.bat build --console=plain` after changes and fix errors in your code.
- Explain simply (the user is a beginner). Don't commit.

## Your area
- Java: `com.reg21meme.dross.dimension.*`
- Data: `src/main/resources/data/dross/dimension/`, `dimension_type/`, `worldgen/` (biome etc.)
- The equipment logic for zombies and skeletons in the Dross dimension.

Not yours: the portal, teleporting, the portal site, the trader, and the `/dross` command.

## What to build
1. **Dimension `dross:dross`**, defined with data JSON:
   - `data/dross/dimension_type/dross.json`: start from overworld values, but `fixed_time: 18000` (permanent night) and `coordinate_scale: 1.0`. Keep overworld-like monster spawn light rules so hostile mobs spawn at normal rates.
   - `data/dross/dimension/dross.json`: `minecraft:flat` generator (superflat).
   - Its **own biome**, `dross:dross_flats`, under `data/dross/worldgen/biome/`, with the plains monster spawn list and weights **minus slimes** (the user removed them). This keeps mob spawning and terrain swappable later.
   - Players see the dimension named **"The Dross"** via the lang key `dimension.dross.dross` (in the shared `en_us.json`).
   - The flat ground surface is **y = -60** (grass top at y = -61, bedrock at y = -64).
2. **Keys class** `com.reg21meme.dross.dimension.ModDimensions` with `public static final ResourceKey<Level> DROSS_LEVEL` (and the dimension-type key). The portal agent will use `DROSS_LEVEL`. This is your public API, so keep the name stable.
3. **Mob gear**, applied only in the Dross dimension, in `dimension/DrossMobGear.java`. It uses `EntityJoinLevelEvent` (server side, skipping mobs loaded from disk). Don't switch to `MobSpawnEvent.FinalizeSpawn`: in Forge 1.20.1 it fires *before* vanilla picks gear and enchantments, so vanilla would overwrite ours.
   - `minecraft:zombie` gets a full netherite helmet, chestplate, leggings and boots, plus a netherite sword.
   - `minecraft:skeleton` gets full netherite armor and **keeps a bow** in its main hand.
   - **No enchantments at all.** Vanilla can enchant spawn gear (including the skeleton's bow) based on difficulty, so make sure the final items are plain, unenchanted stacks.
   - **Nothing drops on death:** set the drop chance to 0 for every equipment slot you fill.
   - Only those two exact mob types. Leave husks, strays, drowned, zombie villagers etc. vanilla, and mention this in your report.

## "Upgradeable later" means
- Terrain lives in data JSON, not hard-coded Java. Swapping `minecraft:flat` for a noise generator later (parked: "old-Minecraft-style terrain") should only touch files under `data/dross/`.
- The biome is our own, so spawns and terrain can change without affecting vanilla.
- Keep the keys in `ModDimensions` as the single source of truth.

## Testing tips to include in your report
- Go through the portal (`/dross site`, then throw in a netherite ingot), or use `/execute in dross:dross run tp @s 0 -60 0` (cheats on). Don't teleport to y = 100: the ground is at y = -60, so that's a deadly fall in survival.
- Confirm it stays night, mobs spawn (no slimes), zombies and skeletons have unenchanted netherite gear, and killing them drops no gear.

## Report format
1. What you built, in plain words.
2. Files added or changed (mark shared files).
3. Build result (pass/fail, plus any errors you couldn't fix).
4. How the user can test it in-game.
5. "Needs from <area>" items, if any.
