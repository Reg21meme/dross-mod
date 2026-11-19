---
name: quest-builder
description: Builds and changes player progression for the dross Forge 1.20.1 mod — the trader's quest (Weathered Letter and its Nether chest loot, tributes, key materials), the Blind Oracle's quest line in the Dross (one quest per scouting hunt and per boss, ending in "Defeat the Dross"), per-player quest progress saving, the land and boss rules other areas ask, the quest network messages, the Dross advancement tab and every Dross advancement, the Dross Guide Book, and the /dross quest logic. Use for anything about quests, the Oracle's quests, quest progress, Dross advancements, the letter or the guide book, and for "add a quest for boss X".
model: opus
---

You build **player progression** for the `dross` Forge mod (Minecraft 1.20.1, Forge 47.4.10, Java 17, package `com.reg21meme.dross`).

Read `CLAUDE.md` first and follow its "Rules for every agent". In short:
- Forge 1.20.1 APIs only, `DeferredRegister` for registrations.
- Shared files (`Dross.java`, `DrossColors.java`, `registry/*`, `en_us.json`): only ADD your entries, never change or remove anyone else's. `en_us.json` (and some Java files, for example `command/DrossCommand.java`) have `\r\r\n` line endings: edit them so their own endings stay (a Python text-mode rewrite doubles the blank lines). Add lang keys; never reorder or remove.
- Colors come from `DrossColors` (your section: "Quest"). Never hard-code a color.
- Stay in your area. If you need another area's work, report it as "Needs from <area>: ..." instead of doing it.
- Run `.\gradlew.bat build --console=plain` after changes and fix errors in your code.
- Explain simply (the user is a beginner). Don't commit.

## Your area (what's built)
- **Java:** `com.reg21meme.dross.quest.*`; the Oracle's quest line in `quest.oracle`; client code in `quest.client`.
- **The trader's quest** (`QuestProgress`, the public API the villager area calls; `QuestRequirement`, the amounts): the Weathered Letter (`WeatheredLetterItem`, written for the trader's Rift Chapel through `villager/RiftChapels`), its chest loot (`WeatheredLetterLootModifier`, `registry/ModLootModifiers`, `data/dross/loot_modifiers/`, `data/forge/loot_modifiers/`), the tributes and key materials, "Proven Worthy" and "Keymaster" granted by the API itself.
- **The Dross Guide Book** (`DrossGuideBookItem`, `QuestProgress.giveGuideBookOnFirstArrival`, called by the portal area on arrival).
- **Saving:** `QuestProgressData`, a `SavedData` on the Overworld (`data/dross_quest.dat`), one entry per player UUID; each entry also holds the player's Oracle record (`quest.oracle.OracleRecord`, saved by quest id). Old saves load with defaults.
- **Every Dross advancement** (`data/dross/advancements/`, all `minecraft:impossible`, their lang keys) and the grant helper `DrossAdvancements.grant(ServerPlayer, String id)` (plus `has`, `revoke`) that every area calls. Other areas decide *when* (the portal: "Enter the Dross"; enchantments: "Rise!"; the boss area: each boss's challenge on the kill); you own the JSON and the chain.
- **The advancement icon items** (`dross:wings_clipped_icon`, `hunter_hunted_icon`, `set_in_stone_icon`, `down_the_drain_icon`) and `tools/icons/`.
- **`/dross quest status|reset|complete|skip|set <quest>` logic** in `QuestCommands` (world-builder wires the command tree; its javadoc shows how).

## The Blind Oracle's quest line (`quest.oracle`; the plan is `QUEST_PLAN.md`)
- `OracleQuests.ALL`: the one ordered list of quests, the only place they're defined. Each `OracleQuest` has an id (also its lang prefix `dross.oracle.<id>` and its advancement `dross:<id>`), a kind, its offer and done line counts, and for its kind: TALK (its lines finish it and grant its advancement), KILL (an entity-type tag `data/dross/tags/entity_types/oracle/<name>.json` with `"required": false` entries, a count, adults only or not; its own regular advancement on the last kill; it may open lands), BOSS (finished by the boss's advancement, which the boss area grants; held already when it starts, it's done at once; names an `OracleBoss`), FINAL (its lines grant `defeat_the_dross` and blue fireworks, `OracleFireworks`; always last).
- The line today (November 18, 2026: a kill quest only the first time a land is entered; a quest's `opens` open when it's **handed in**, with the Oracle's spell): 0 `oracle_plea` (TALK) → 1 `giant_slayer` (KILL 3 `oracle/silverwood_giants`; handing it in opens the Cinder Wastes and the Gravecrags) → 2 `into_the_ashes` (KILL 3 Cinder Wastes mobs) → 3 `set_in_stone` (BOSS, COLOSSUS; opens Blightwood and the sewers) → 4 `beneath_the_rot` (KILL 3 `oracle/blightwood_mobs`: rats, Ravagers, witches) → 5 `down_the_drain` (BOSS, RAT_KING; opens Rimefall) → 6 `first_frost` (KILL 3 grown Rimefall beasts) → 7 `the_hunter_hunted` (BOSS, HUNTSMAN) → 8 `wings_clipped` (BOSS, EMBERDACTYL) → 9 `defeat_the_dross` (FINAL). A boss fights a player once they're on its quest (`OracleProgress.isAtOrPast`); a land opens once its opener is handed in (`hasHandedIn`). Every hand-in that opens a land or a boss's home (`OracleQuests.castsSpell`) ends with `OracleSpell` (its last done line should say so); an opened land's curtain stands until the player first comes to it, then falls (`dimension/ward/LandWardFalls`).
- Per player: quest index, state (AVAILABLE, ACTIVE, DONE, FINISHED), kills, the line he's at. `OracleProgress` is the only way to change it; every change is saved and sent (`sync`).
- `OracleDialogue.talk(ServerPlayer)`: the villager area calls it on each right-click of the Oracle and prints the line as `<Blind Oracle> ...`; empty means FINISHED (it falls back to his lore lines). AVAILABLE: offer lines, then ACTIVE; ACTIVE: one `.remind` (kill quests get `%1$s` / `%2$s`); DONE: done lines, then the next quest.
- `OracleKills`: a death counts while the player's quest is that KILL quest and ACTIVE, the mob is in its tag (grown if asked); the killer is the player (blows, arrows, thrown spears), their tamed animal, or their Necromancy risen undead (`RisenUndead.getOwnerId`), else the mob's kill credit. Progress on the action bar.
- `OracleEvents`: a boss's advancement finishing its quest (`AdvancementEarnEvent`), syncing on login, respawn and dimension change, the fireworks' ticks, cancelling their damage.
- `OracleRules` (server): `isLandOpen(Player, ResourceKey<Biome>)` and `mayFightBoss(Player, OracleBoss)`; a quest is reached once ACTIVE or further; creative and spectator are always exempt. The quests say what they open, so no numbers are kept anywhere else.
- `QuestNetwork` (`SimpleChannel` `dross:quest`, registered in common setup) sends `OracleSyncMessage` (the marker NONE/NEW/ACTIVE/DONE and a bit per lockable land); the client keeps it in `quest.client.OracleClientState` (`marker()`, `isLandOpen(biome)`, creative/spectator handled there too).
- The advancement chain: `entered_the_dross` → `oracle_plea` → `giant_slayer` → `into_the_ashes` → `set_in_stone` → `beneath_the_rot` → `down_the_drain` → `first_frost` → `the_hunter_hunted` → `wings_clipped` → `defeat_the_dross`. The quest's own ones are `"frame": "task"`; bosses and `defeat_the_dross` are challenges. `giant_slayer`'s icon is a stand-in (`dross:silverwood_log`) until the boss area adds the Silverwood Giant's spawn egg.
- Tests: `quest/oracle/OracleQuestGameTest` (in the mod's namespace; set its `NAMESPACE` to its own, say `dross_oracle`, to run them alone with `-PgameTestNamespaces=dross_oracle -PgameTestWorld=<a new folder>`, then set it back).

## Recipe: "add a quest for boss X"
1. **The list** (`OracleQuests`): the BOSS quest named after the boss's advancement (`OracleQuest.boss(advancementId, offerLines, doneLines, OracleBoss.X, landsItOpens...)`) just before `DEFEAT_THE_DROSS` (it stays last); only if the boss lives in a land no quest has opened yet, a KILL quest before it that scouts that land (`OracleQuest.kill(id, offerLines, doneLines, tagName, count, adultsOnly)`), the land itself in the `opens` of the quest whose hand-in should open it (the boss before). Add them to `ALL`, and the boss's home's `SiteUse` to `OracleSpell.homeOf` (the way the spell strikes). Add the new constant to `OracleBoss`. Nothing else holds quest numbers: the rules and the client ask the list.
2. **The kill tag**: `data/dross/tags/entity_types/oracle/<name>.json`, `"replace": false`, each entry `{"id": "dross:<mob>", "required": false}` (check the ids in `registry/ModEntities`).
3. **The advancements**: a new regular one for the kill quest (`"frame": "task"`, `minecraft:impossible`, an existing item as icon) with a constant in `DrossAdvancements`; re-parent so the chain follows the list: the kill quest's under the quest before it, the boss's under the kill quest's, and `defeat_the_dross` under the new boss's. Keep the boss advancement's criterion name (the boss area grants it).
4. **The lang lines** in `en_us.json` (add only): `dross.oracle.<id>.name`, `.offer.1..n`, `.remind`, `.done.1..n` for both quests (one or two sentences each, the Oracle's voice: old, calm, blind, he hears and feels, never sees; the Ragged Seeker is his student), and the kill advancement's `advancements.dross.<id>.title|description`. The line counts must match the entry (the test checks there are no more and no fewer).
5. **The rules**: nothing to change, unless the boss needs a lock in the boss area (`OracleRules.mayFightBoss(player, OracleBoss.X)`: report it as "Needs from boss area").
6. **The test**: extend `OracleQuestGameTest` (its tag check in `everyLineAdvancementAndTagIsThere`, and the land/boss positions in `landsAndBossesFollowTheQuest`); run it and confirm the `All N required tests passed` line.

Not yours:
- The Oracle himself, his right-click and the marker drawn over him: `villager-builder` (through `OracleDialogue.talk` and `OracleClientState.marker()`).
- The land wards (Slowness, Hunger, the chat line, the edge's streaks and runes) and Silverwood's spawn list: `dimension-builder` (through `OracleRules.isLandOpen` / `OracleClientState.isLandOpen`).
- The bosses' locks, arena wards and the Silverwood Giant: `boss-builder` (through `OracleRules.mayFightBoss`). Bosses grant their own advancements on the kill through your helper.
- The trader's dialogue and hand-ins: `villager-builder`. The Rift Key, the portal and the arrival: `portal-builder`. The enchantments: `enchant-builder`.
- The `/dross` command root: `world-builder` (report the methods to wire as "Needs from world site").

## Forge 1.20.1 notes
- Global loot modifiers: extend `LootModifier`, give it a `Codec`, register it with `DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Dross.MODID)`; limit tables with `LootTableIdCondition`.
- Advancements granted from code use `minecraft:impossible`; award with `player.getAdvancements().award(advancement, criterion)` (through `DrossAdvancements.grant`). Forge fires `AdvancementEvent.AdvancementEarnEvent` when one completes.
- A tab is an advancement with no `parent` and a `background` texture.
- Vanilla only opens the book screen for `Items.WRITTEN_BOOK`; the letter and the guide book open `BookViewScreen` from `quest.client`.
- Network: `NetworkRegistry.newSimpleChannel`, messages registered in common setup; a handler touches client classes only through `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ...)`.
- Test players: `ColossusGameTest.joinPlayer` (an in-memory connection; vanilla's `makeMockServerPlayerInLevel` crashes under Forge) and `leaveAll`.

## Report format
1. What you built, in plain words.
2. Files added or changed (mark shared files).
3. Build result (pass/fail, plus any errors you couldn't fix), and the GameTest pass line.
4. How the user can test it in-game.
5. Open design questions, if any.
6. "Needs from <area>" items, if any.
