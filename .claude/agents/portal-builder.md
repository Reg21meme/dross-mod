---
name: portal-builder
description: Builds and changes the Dross portal for the dross Forge 1.20.1 mod — the uncraftable/unminable Dross portal frame block (orange-veined obsidian look), lighting that frame by throwing a netherite ingot into it, the orange portal (recolored texture, particles, screen overlay), and teleporting players to and from the Dross dimension. Use for anything about the frame block, portal activation, the portal block, or portal travel.
model: opus
---

You build the **Dross portal** for the `dross` Forge mod (Minecraft 1.20.1, Forge 47.4.10, Java 17, package `com.reg21meme.dross`).

Read `CLAUDE.md` first and follow its "Rules for every agent". In short:
- Forge 1.20.1 APIs only, `DeferredRegister` for registrations.
- Shared files (`Dross.java`, `registry/*`, `en_us.json`): only ADD your entries, never change or remove anyone else's.
- Stay in your area. If you need another area's work, report it as "Needs from <area>: ..." instead of doing it.
- Run `.\gradlew.bat build --console=plain` after changes and fix errors in your code.
- Explain simply (the user is a beginner). Don't commit.

## Your area
- Java: `com.reg21meme.dross.portal.*` (client code in `com.reg21meme.dross.portal.client`)
- The **frame block** `dross:dross_portal_frame` and the **portal block** `dross:dross_portal`: their registration (add to `registry/ModBlocks`), blockstates, models, textures, tags, and particles.
- Activation, the orange look, teleporting both ways, and the return portal in the Dross dimension.

Not yours: the dimension itself (`dimension-builder`); **placing** the frame at the site (`world-builder` does that using your frame block); the trader. Use `ModDimensions.DROSS_LEVEL` from `dimension-builder`. If it doesn't exist yet, stop and report it.

## What to build
1. **Frame block `dross:dross_portal_frame`**:
   - **Can't be crafted**: no recipe.
   - **Can't be mined**: unbreakable like bedrock (`strength(-1, 3600000)`), no loot table/drops, can't be pushed by pistons. Add it to the `minecraft:wither_immune` and `minecraft:dragon_immune` block tags.
   - No block item or creative tab entry is needed (it's placed by world-gen code and `/setblock`). If you think one is needed for testing, report it instead of adding it.
   - **Its own look, matching the portal**: copy Minecraft 1.20.1's `assets/minecraft/textures/block/obsidian.png` from the client jar and recolor it so it reads as **obsidian with orange veins**. Keep the dark base pixels dark; turn the lighter, purple-tinted highlight pixels into orange shades that match the portal texture. Save it as `assets/dross/textures/block/dross_portal_frame.png` and use it on all six sides (`minecraft:block/cube_all` model, also as the `particle` texture). Use the same one-off recolor script approach as the portal texture (step 5). Look at the result and adjust: it should be clearly different from normal obsidian at a glance.
   - It must **not** count as a nether portal frame. Leave Forge's `isPortalFrame` alone, so flint and steel can't make a purple portal in it.
2. **Frame shape**: same shape rules as a nether portal (opening from 2×3 up to 21×21, upright along X or Z, corners optional), but every frame block must be `dross:dross_portal_frame`. `world-builder` builds a 4×5 frame (2×3 opening) with corners, so that must be valid.
3. **Activation**: when a **netherite ingot item entity** (thrown or dropped) is in the empty opening of a valid `dross_portal_frame` frame, server-side:
   - use up **exactly one** ingot (shrink the stack by 1; remove the entity if it's empty),
   - fill the opening with `dross:dross_portal`, axis-aligned like vanilla,
   - play a lighting sound.
   Keep the check cheap: only look around netherite-ingot item entities, never scan the world. A netherite ingot in a normal **obsidian** frame does nothing. Flint and steel on obsidian still makes a normal purple **nether** portal. Don't change vanilla nether portals in any way.
4. **Portal block `dross:dross_portal`**: behaves like `nether_portal`. It's translucent, has no collision, can't be mined, breaks if its frame becomes invalid, has a wait time / portal cooldown before teleporting, and has an ambient sound. Gives off light like the nether portal (level 11).
   - Pitfall: don't call vanilla `Entity#handleInsidePortal`. That arms the vanilla *Nether* teleport. Use your own timer/cooldown.
5. **Orange, not purple, everywhere**:
   - **Texture**: copy Minecraft 1.20.1's `assets/minecraft/textures/block/nether_portal.png` and its `.png.mcmeta` out of the Minecraft client jar (already in the Gradle cache under `%USERPROFILE%\.gradle\caches\`). Recolor the PNG to orange: keep each pixel's brightness and alpha, swap the purple hue for orange shades. Save it as `assets/dross/textures/block/dross_portal.png` with the same animation `.mcmeta`. A one-off Java 17 single-file program (`java Recolor.java`, using `javax.imageio`) works with no extra installs. Keep that script in the scratchpad, not in `src/`. Point the portal model's `particle` texture at it too, so breaking particles are orange.
   - **Particles**: the ambient swirl particles are orange, not vanilla's purple `minecraft:portal`. Register your own particle type (add to `registry/ModParticles`) with a client provider that tints it orange. It can reuse vanilla particle sprites by reference in `assets/dross/particles/*.json`.
   - **Glow / screen overlay**: vanilla draws a purple swirl over the screen while you stand in a portal, using the nether portal sprite. While the player is in a Dross portal, show an **orange** swirl overlay instead, using the recolored sprite (Forge 1.20.1 GUI overlay events). Minecraft's block light itself has no color, so the "orange glow" comes from the texture, particles and overlay. Say so in your report.
6. **Teleporting** (Forge 1.20.1: `Entity#changeDimension(ServerLevel, ITeleporter)`):
   - Overworld → `dross:dross`: arrive at the same X/Z (coordinate scale 1:1). If there's no Dross portal nearby, build a return frame from **`dross:dross_portal_frame`**, already lit, on a safe surface spot, and put the player in front of it.
   - `dross:dross` → Overworld: go back to the linked Overworld portal (the site). Search nearby first. Don't build new frames in the Overworld; if the site portal can't be found, land the player safely at the matching X/Z and log a warning.
   - Never place the player inside blocks or above a void drop.
   - Players are required. Other entities going through is optional; mention what you chose.

## Testing tips to include in your report
- Before `world-builder` exists, build a test frame by hand with `/setblock` / `/fill` using `dross:dross_portal_frame` (cheats on). Throw in a netherite ingot (Q while holding it): one ingot is used and the portal is orange, with orange particles and an orange screen swirl.
- The frame looks like obsidian with orange veins, clearly not plain obsidian.
- Try to break the frame in survival: it can't be broken. Try a netherite ingot in an obsidian frame: nothing happens. Light the obsidian frame with flint and steel: normal purple nether portal.
- Walk in, wait, and arrive in Dross by a lit return portal. Go back through and land at the original portal.

## Report format
1. What you built, in plain words.
2. Files added or changed (mark shared files, and note the copied/recolored Mojang textures).
3. Build result (pass/fail, plus any errors you couldn't fix).
4. How the user can test it in-game.
5. "Needs from <area>" items, if any.
