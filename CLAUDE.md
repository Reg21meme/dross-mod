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
   - Registry classes in `com.reg21meme.dross.registry`: `ModBlocks`, `ModItems`, `ModEntities`, `ModParticles`, `ModCreativeTabs` (create one if it doesn't exist yet, holding only the `DeferredRegister` and your entries).
   - `src/main/resources/assets/dross/lang/en_us.json`: add keys only.
3. **Stay in your own area** (see the table below). If you need something from another area, don't build it. Report it as "Needs from <area>: ...".
4. Prefer your own `@Mod.EventBusSubscriber` classes inside your package over adding event code to `Dross.java`.
5. After changes, run `.\gradlew.bat build --console=plain` and fix any errors **in your own area**. If the error is in another area's code, report it instead.
6. Finish with a short, beginner-friendly explanation: what you added, which files, and how to test it in-game.
7. Don't commit.

## Areas and ownership
| Area | Agent | Owns |
|---|---|---|
| Dimension | `dimension-builder` | `com.reg21meme.dross.dimension.*`; `data/dross/dimension/`, `data/dross/dimension_type/`, `data/dross/worldgen/`; Dross mob equipment |
| Portal | `portal-builder` | `com.reg21meme.dross.portal.*`; the frame block `dross:dross_portal_frame` and the portal block `dross:dross_portal` (definitions, assets, tags); ingot activation; orange texture/particles/overlay; teleporting; return portal; the frame's creative-only block item and its entry in the "Dross" creative tab; the arrival sequence (title, piano notes) and the `dross:entered_the_dross` advancement (`data/dross/advancements/`) |
| World site | `world-builder` | `com.reg21meme.dross.world.*` (portal site location, placing the frame built from `portal-builder`'s frame block); `com.reg21meme.dross.command.*` (the `/dross` command) |
| Villager | `villager-builder` | `com.reg21meme.dross.villager.*`; trader entity, renderer, spawn logic, map trade, join message |
| Testing | `mod-tester` | Nothing. Builds, runs and reads logs only; never edits feature code |

Client-only code (renderers, particle providers) goes in a `client` subpackage of the area, for example `com.reg21meme.dross.villager.client`.

## The plan
1. **Dross dimension** (`dross:dross`): superflat, permanent night (`fixed_time` 18000), normal hostile mob spawn rates. Zombies spawn in full netherite armor + netherite sword. Skeletons spawn in full netherite armor and keep their bows. Nothing is enchanted (including the bow). None of this gear drops on death. Slimes are removed from the spawn list. Players see the dimension named **"The Dross"** (lang key `dimension.dross.dross`). Terrain is defined in data JSON with its own biome so it can be upgraded later without rewriting Java.
2. **Dross portal**: the frame is made of a custom block, `dross:dross_portal_frame`, which **can't be crafted or mined** (no recipe, no drops, unbreakable, blast-proof). For testing, it has a block item that is only available in the creative **"Dross"** tab (or `/give`); it still has no recipe, drops nothing and can't be broken in survival. It looks like obsidian with orange veins (Minecraft's obsidian texture copied and recolored, temporary, see Parked) so it matches the portal. Throwing a **netherite ingot** into the empty middle of that frame uses up the ingot and lights an **orange** portal, in frames facing either direction (along X or Z). Only this frame works: a netherite ingot does nothing in a normal obsidian frame, and flint and steel can't light the Dross frame. Normal obsidian frames stay normal purple nether portals. The orange look is Minecraft's nether portal texture copied and recolored orange (temporary, see Parked), plus orange particles and an orange in-portal screen swirl. The portal teleports players between the Overworld and the Dross dimension, both ways.
3. **Portal site** at (0, 0) in the Overworld, for testing: the frame is built from `dross:dross_portal_frame` and is **already there but unlit** when the world is created. It's the only Dross frame in the Overworld. Test command `/dross site` teleports the player there.
4. **Dross trader** (villager): spawns once in the plains or desert biome nearest world spawn. For testing, on join the chat shows his coordinates. Placeholder skin. Trades 1 nether star for a map that points to the portal site (up to 3 uses, no restock). He is unkillable, because he only ever spawns once.

5. **Arriving in the Dross** (through the portal only, not `/execute in`), in `portal/DrossArrival.java`. Times are in ticks after arrival (20 ticks = 1 second):
   - **Title**: at tick 20, "The Dross" (gold, from `dimension.dross.dross`) fades in like `/title` (0.5 s in, 3.5 s on screen, 1 s out). Shown every time. The 1-second delay keeps it from being hidden behind the "Loading terrain" screen.
   - **Four-note piano sequence** ("dun, dun, DUN, dunnn"): note block harp, every time, only the arriving player hears it, on the Master volume. Notes start with the title: E, D, C# 0.4 s apart, then a 0.5 s gap and low F# (pitch 0.5, the lowest note block note). Volumes 0.6, 0.6, 0.8, 1.0.
   - **Advancement "Entered the Dross"** (`dross:entered_the_dross`): challenge frame (purple), Dross Portal Frame icon, "Step through the orange portal.", in the Minecraft (story) tab. Granted from code (`minecraft:impossible` trigger) 2 seconds after the last note, so its fanfare doesn't clash with the piano. If the player leaves the Dross before then, it's granted on their next arrival.

## Build order
1. Dimension → 2. Portal → 3. World site → 4. Villager.
Run `mod-tester` after each step.

**Status:** steps 1–5 are built and tested in-game.

## Parked for later (do NOT build yet)
- Zombie guards at the portal site.
- Kill 3 iron golems to unlock the portal.
- Old-Minecraft-style terrain for the Dross dimension.
- A custom villager skin (replacing the placeholder).
- Moving the portal site 3,000–10,000 blocks out from (0, 0).
- Replace the recolored portal texture with an original one before publishing. The same goes for the recolored frame texture (orange-veined obsidian).
