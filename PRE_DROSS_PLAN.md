# The way into the Dross: the plan (the quest revamp)

**Status:** agreed with the user November 22-23, 2026, on the branch `oracle-quests` (its own copy of the project,
`MyMinecraftMod-quests`). **T1, the trader's quests, built November 23-24, 2026, not yet tested in-game** (it builds and
its headless tests compile; they haven't been run yet). Where the build differs from this plan, or settles something it
left open, is listed at the end, under "As built (T1)". **T2, the cathedral's siege, built November 24, 2026, not yet
tested in-game** (it builds and its headless tests compile; not run yet): "As built (T2)" at the end. It replaces the trader's old hand-in quest (the tributes, the Dross Compass, the key materials
and the Rift Key; CLAUDE.md step 7). The Oracle's quests in the Dross (QUEST_PLAN.md) are unchanged. Each choice made
where the user didn't say is marked *(my choice)*.

## What the user asked for
- **The trader gets the Oracle's system**: the same markers over his head (red ?, yellow ..., green !), his quests in a
  list, a line or two a right-click.
- **His story is his own, never the Oracle's.** The Oracle tells the war from inside the Dross (trapped there, asking
  you to push it back). The trader tells the Overworld side: the Dross's knights left on this side when it was locked
  away, still guarding broken portals; their souls still carry its magic; he can use them.
- **First meeting:** "are you willing to help enter the Dross?" "Yes, of course." "Great..." He gives **3 golden apples**
  and **where the nearest knight ruin is** (rounded to the nearest 10).
- **Clear 3 different knight ruins** (both knights dead). Each one shows **"Knight ruins cleared: 1 / 3"**. The Nether's
  fire ruins count too (once they're in the world).
- **He makes the item you throw into the portal**: the user's pick of the designs, **O3, the Portal Orb** (a glass ball
  with a little Dross portal swirling in it; `tools/portal_item/portal_items.py`, `quest-previews/portal_item/`). It
  lights the cathedral's frame as the Rift Key did. Its name comes from the design.
- **He tells you where the cathedral is**, rounded to the nearest hundred (7531, 1234 → "around 7500, 1200"). No compass
  or pointing item. Then: "Go on, I believe in you."
- **Reminder:** asked again, "If you need a reminder, the portal should be around 7500, 1200."
- **Once you've been to the Dross** (any of the Oracle's quests): "I hope your battle in the Dross is going well."
- **A lost orb:** "If you need a new one, clear 3 more ruins"; 3 more ruins and he gives you a new one.
- **The cathedral under guard:** 5 knights roam round it, spread apart, a random mix of elements (at most 2 of one). A
  barrier (the locked lands' curtain) keeps you out while any lives. Kill all 5 and it falls, **for good, for everyone
  in the world**. The trader warns you about them.
- **Fire knight ruins in the Nether**, as rare as about one every 1,000 blocks; 20 designs to pick from first.
- **Removed:** Necromancy and Deathforged (done, 2365ac2); the tributes, the key materials, the Dross Compass, the Rift
  Key, "Proven Worthy" and "Keymaster"; the two join messages giving coordinates (the cathedral's and the trader's
  chapel's). His shop sells only the Guide Book.
- **The Weathered Letter:** 20% a chest (was 100% for testing).
- **Next update, not this one:** the Seeker's teleport device; your own Dross portal.

## The chain
Weathered Letter (20% of Nether Fortress and Bastion chests) → the trader (? over him): his story, "will you help?",
"Yes, of course.", 3 golden apples and the nearest ruin → **clear 3 knight ruins** (... over him; "Knight ruins cleared:
n / 3") → back to him (! over him): he makes the **Portal Orb**, says where the cathedral is, warns of its knights →
**kill the cathedral's 5 knights** → its barrier falls → throw the orb into the frame → **Enter the Dross** → the
Oracle's quests.

## His quests (`quest/trader/`, like `quest/oracle/`)
Saved per player in the quest area's `QuestProgressData` (the old tribute and key fields go). Markers: NEW (red ?) when
his offer is waiting; ACTIVE (yellow ...) while you clear ruins; DONE (green !) when you've cleared enough; NONE
otherwise. Sent with the Oracle's sync (one message carries both markers).

| Step | What happens | Marker |
|---|---|---|
| 1. His offer (talk) | his story, a line a click; the last asks if you'll help; your next click: "Yes, of course." (your own line, as you in chat); then his thanks, the 3 golden apples and the nearest ruin you haven't cleared | ? |
| 2. Three ruins (going) | each new ruin cleared counts (`KnightRuinClearedEvent`, first time at that ruin); "Knight ruins cleared: n / 3" over the hotbar; a click reminds you how many and where the nearest one you haven't cleared is | ... |
| 3. Three ruins (done) | his hand-in lines: he makes the Portal Orb (a puff, a sound), gives it, tells you the cathedral's spot (nearest 100), warns of its knights, "Go on. I believe in you." | ! |
| 4. Orb given | a click: "If you need a reminder, the portal should be around X, Z." | none |
| 4b. Orb lost | (orb given, none in your inventory, the cathedral's portal not lit yet) a click: "Clear three more ruins and I'll make another"; then 3 more new ruins count as in step 2, and the hand-in gives a new orb | ... then ! |
| 5. Been to the Dross | ("Enter the Dross" held) a click: "I hope your battle in the Dross is going well." (a few lines in turn) | none |

- **The nearest ruin** *(my choice)*: the nearest one you haven't cleared, worked out off the server thread
  (`KnightRuins`); his line comes when it's ready (no freeze). None within reach: a line saying he can't feel one near.
- **Different ruins:** only a ruin you clear for the first time counts (the knights come back after 10 minutes).
- **His shop** opens once he's given you an orb, and sells only the Guide Book (to players with "Enter the Dross").

## His lines (draft; the user may edit them)
- Offer: 1 "Another traveller with an old friend's letter. Come in, come in. You've come about the rift." 2 "When the
  Dross was locked away, not all of it went back through the door. Its knights were left on our side." 3 "They still
  guard the broken portals they came through, waiting for orders that will never come." 4 "But their souls still carry
  the Dross's magic. With enough of them, I could make something to wake the great portal." 5 "I remember roughly
  where it lies: a ruined cathedral, far from here. I'd find it for you." 6 "So tell me: are you willing to help me get
  you into the Dross?"
- Your answer: "Yes, of course."
- Accepted: 1 "Good. Here, take these: you'll need your strength." 2 "Bring down both knights at three of their ruins.
  Their souls will follow you back to me." 3 "The nearest ruin I know of is near X %s, Z %s." / "I can't feel any ruin
  near here. Try further out."
- Reminder: "Knight ruins cleared: %s of 3. The nearest one you haven't cleared is near X %s, Z %s."
- Done: 1 "Three ruins. I can feel their souls on you, thick as smoke." 2 "Hold still." 3 "There: a Portal Orb. Throw
  it into the cathedral's frame and the portal will wake." 4 "The cathedral should be around X %s, Z %s." 5 "But be
  careful: its knights never left it, and a spell keeps it shut while they stand." 6 "Go on. I believe in you."
- Orb given: "If you need a reminder, the portal should be around X %s, Z %s."
- Orb lost: "Lost the orb? I can make another, but I'll need more souls. Clear three more ruins and come back."
  Then the reminder ("...for a new orb"), and on the hand-in: "That's enough. Here, a new orb. Hold on to this one."
- Been to the Dross (in turn): "I hope your battle in the Dross is going well." / "Still standing? Good. The Dross
  doesn't keep many." / "Tell the old man by the fire the archivist sends his regards." *(my choice: the second and
  third)*

## Advancements
root → A Weathered Letter → **Knightfall** (3 knight ruins cleared; replaces Proven Worthy) → **The Portal Orb** (the
orb made for you; replaces Keymaster) → Enter the Dross → the Oracle's chain; and beside Enter the Dross, under The
Portal Orb, **Siege Broken** (the cathedral's 5 knights; stage T2), not in the chain, so a player who comes after
someone else broke the siege has no gap before Enter the Dross. Names and icons are placeholders until the user says.

## Stages
1. **T0, done:** Necromancy and Deathforged removed (2365ac2); the portal item's designs and the user's pick, O3
   (c4a3389).
2. **T1, the trader [built, not yet tested]:** his quest system and markers, the lines above, the Portal Orb (`dross:portal_orb`, the O3
   texture) in place of the Rift Key, the tributes, key materials, Dross Compass, Proven Worthy and Keymaster removed,
   the letter at 20%, the two join messages removed, `/dross quest status|reset|complete` updated, his shop the Guide
   Book only. Headless tests.
3. **T2, the cathedral's siege [built, not yet tested]:** its 5 roaming knights and its barrier (falls for good once
   they're dead); "Siege Broken"; the frame refusing the orb while it holds; `/dross siege status|reset|break`.
4. **T3, the Nether ruins:** the user's picks of the 20 designs placed in the Nether (fire knights, about one every
   1,000 blocks), counting for the trader.

## As built (T1)
Built November 23-24, 2026 (not yet run or tested in-game). The code: `quest/trader/` (`TraderState`, `TraderRecord`,
`TraderProgress`, the only way to change it; `TraderDialogue`, his talk; `TraderRuins`, the nearest ruin off the server
thread; `TraderQuestEvents`; the headless test `TraderQuestGameTest`), `portal/PortalOrbItem`,
`villager/client/QuestMarkerRenderer` (the Oracle's marker renderer made reusable, drawn over both), the trader's
right-click in `villager/DrossTrader`. What differs from the plan above, or settles what it left open (*my choice*):
- **Clicks.** His six offer lines a click each; the seventh click is your "Yes, of course." (sent only to you, as
  `<you> Yes, of course.`), his first thanks line and the 3 golden apples, and the quest goes (the yellow ...) at once;
  the next click is his second thanks line and, when it's worked out, the nearest ruin (third line). Then each click is
  the reminder. The hand-in is a line a click: he makes the orb on line 3 (a puff of the portal's blue and smoke, an
  enchanting sound and an amethyst chime) and names the cathedral on line 4.
- **Ruins count only while his quest is going** (like the Oracle's kills): a ruin cleared before you said yes, or while
  the orb is waiting (the green !), doesn't count, and since only a first clear counts, it never will. (Counting earlier
  clears would be a one-line change: tell me.)
- **The nearest ruin you haven't cleared**: searched round the player, within 1,200 blocks, then 2,400, then 4,800
  (`TraderRuins.SEARCH_RADII`); none: "I can't feel any ruin near here. Try further out." A click while his search is
  going starts no second one (it's forgotten after a minute, or when the server stops).
- **The reminder's count** is `%s of %s` (the count and `TraderProgress.RUINS_NEEDED`), not "of 3" written in; his other
  lines say "three" in words, so changing the number means editing them too.
- **A lost orb** is "none in your inventory and the cathedral's portal not lit yet, and not been to the Dross". The
  portal being lit is remembered for the whole world (`QuestProgressData`'s `CathedralLit`): set when an orb lights the
  cathedral's own frame (`DrossPortalActivation` tells `TraderProgress.portalLit`), or seen in the frame's chunk when
  it happens to be loaded (never loads one). A new orb's round that's no longer needed (you found yours, the portal was
  lit, you went through) ends quietly at your next click.
- **After the Dross** ("Enter the Dross" held) his three lines come in turn, whatever else: no new orb is offered then.
- **His shop** opens after his line, only once he's made you an orb and only when he has something for you (the Guide
  Book, with "Enter the Dross"); an empty trading screen never opens.
- **No cathedral in the world** (superflat, Generate Structures off): his hand-in line 4 and the reminder say he can't
  feel it (`dross.trader.done.4.none`, `dross.trader.orb_given.none`).
- **The markers** are the Oracle's (`OracleMarker`), sent in the Oracle's message (`OracleSyncMessage` has a second
  marker; `QuestNetwork`'s protocol 4 to 5).
- **The progress text** above the hotbar: "Knight ruins cleared: 1 / 3", and on the third "Knight ruins cleared: 3 / 3. Go
  back to the Dross Trader." (`DrossColors.TRADER_PROGRESS_TEXT`, `TRADER_DONE_TEXT`).
- **Advancements**: `dross:knightfall` ("Knightfall", "Clear three knight ruins for the Dross Trader.", icon an iron
  helmet) and `dross:portal_orb` ("The Portal Orb", "Have the Dross Trader make you a Portal Orb.", the orb); "Enter the
  Dross" now hangs from The Portal Orb. `/dross quest reset` takes both away; `complete` grants both.
- **The lines are the drafts above**, with "done.5" ("...a spell keeps it shut while they stand") said already though the
  cathedral's knights and barrier come with T2.
- **Removed:** `dross:rift_key` (its item, model, texture), the Dross Compass (`DrossCompass`, `DrossCompassTracker`),
  the tributes and key materials (`QuestRequirement`, their saved fields), "Proven Worthy" and "Keymaster", the two join
  messages (`SiteAnnouncer`, `TraderAnnouncer` and its test). `PortalSiteGameTest` now checks nobody is told the site on
  joining and that the trader's rounded spot is the frame's; `RiftChapelGameTest` checks his first click is his offer.
- **The letter's chance**: `config/dross-common.toml`'s `letterChance` default is 0.2; an existing config file keeps
  the value written in it (the dev runs' `run/config/dross-common.toml` still says 1.0 until it's changed or deleted).

## As built (T2)
Built November 24, 2026 (not yet run or tested in-game). The code: `world/CathedralSiege` (the plan from the seed, the
keeper, the knights' deaths, the fall, the frame's refusal, the test tools' logic), `world/CathedralWard` (the barrier's
push and send-back, its curtain, the fall's stretches), `world/CathedralSiegeData` (`data/dross_cathedral_siege.dat`),
`command/SiegeCommands` (`/dross siege status|reset|break`), the headless test `world/CathedralSiegeGameTest`, the
advancement `data/dross/advancements/siege_broken.json`. What settles what the user left open (*my choice*):
- **Which knights.** Five **different** kinds of the ten (the user's "5 different Dross knights"), from a seeded shuffle;
  with two kinds an element, that's never more than two of one element (the code still checks it, should kinds be
  added). The world's seed picks them, so every world has its own five.
- **Where.** Their directions from the footprint's middle are evenly spread (72 degrees apart) from a start the seed
  picks, each turned by up to 10 degrees. Each one's spot is about 12 blocks past the footprint's edge along its
  direction, on solid dry ground (not a log, leaves or water) with room for a knight and near the cathedral's ground
  level; where that's no good, up to 30 blocks out and turned up to 32 degrees; never within 24 blocks of another's.
  It's picked once, when all the ground it might be on is loaded, and saved, so it's the same whichever way a player
  comes. They use the knight ruins' guard behaviour as it is (they wander within 36 blocks of their spot, fight players
  who come there, walk back past 12): so you mostly face the one or two whose ground you're on (one that's hurt calls
  the knights within its follow range, which are usually none: they stand about 75 blocks apart).
- **When.** When a player (not a spectator) is within 160 blocks of the footprint, its land is loaded with its entities
  and its tree tidy-up (`SiteTidy`) is done; the keeper looks every 2 seconds. A knight that left without dying comes
  back; one that's missing but may only be in an unloaded chunk (part of the ground round its spot isn't loaded) is
  waited for, and an old copy that loads after a new one came is refused (as the ruins do).
- **The barrier's box.** The footprint (61 x 87), from 16 blocks under its lowest layer to 64 over its top. Pushed out
  through the nearest side; sent back (deeper than 4 blocks in) to the player's last spot outside within 64 blocks, else
  6 blocks in front of the great door (south), facing it. A line over the hotbar at most every 10 seconds: "A spell
  holds the cathedral shut while its knights stand." The curtain is sent from the server, to each player it keeps out
  within 28 blocks of the edge (the same particles as the land wards' curtain, the same number a block of edge), so
  their game needs no new message; players in creative or spectator mode see none (as with the land wards).
- **On Peaceful** the knights don't come, so the barrier stays up (as on Peaceful no ruin knights come either, the
  whole trader quest waits for another difficulty).
- **The count.** Each knight's death tells the players within 64 blocks of the cathedral (and the killer) "The
  cathedral's knights fallen: 2 / 5" over the hotbar.
- **The fall** goes to every player within 64 blocks of the footprint and the player credited with the last kill
  (their own blow, their pet's, or the last player to hurt it), wherever they are: each sees the straight stretch of
  curtain on the side nearest them (and the next side's when it's within 24 blocks: round a corner), the land barriers'
  own fall at their feet's height. They're told "The last of the cathedral's knights falls, and the spell holding it
  shut breaks. Its way in stands open." and get "Siege Broken": a **challenge**, its icon a netherite sword
  (placeholder), under "The Portal Orb" beside "Enter the Dross". Not for `/kill` (as with the bosses): the siege still
  breaks. `/dross quest reset` leaves it (it's a deed, like the bosses').
- **The frame's refusal**: the orb pops back out the way it came (out of the frame's plane, a little up), runes flare
  across the opening with a chime, and its thrower (or, with none, the players within 8 blocks) is told "A spell holds
  this frame shut while its knights stand." (at most every 2 s for one orb). Only the cathedral's own frame (the one
  holding its opening's middle), and only in a world where the cathedral was really made with the land.
- **`/dross goto cathedral`** while it holds adds a line: the barrier will bounce a survival player straight out (to the
  great door).
- **The test tools.** `siege status` (its state and each knight's), `siege reset` (it holds again: its knights about now
  go, and come back, their spots picked again, the next time a player is near; "Siege Broken" is taken from you so you
  can earn it again), `siege break` (broken at once, its knights about now gone, the players near see it fall; no
  advancement).
- **Test server.** Like the knight ruins' keeper, the keeper and the barrier stand down on the GameTest server only (its
  other tests' stand-in players visit the cathedral); the siege's own tests call them. Each of the three tests has its
  own batch (the siege is one per world). The siege test lights the real frame once the siege is broken, then puts the
  opening and the world's "cathedral lit" flag back as they were (the flag by reflection, test only) and resets the
  siege.
- **Shared files touched (added only):** `DrossColors` (the World site section: `SIEGE_BROKEN_TEXT`, `SIEGE_WARD_TEXT`,
  `SIEGE_PROGRESS_TEXT`), `en_us.json` (six keys), `quest/DrossAdvancements` (`SIEGE_BROKEN`), the portal area's
  `DrossPortalActivation` (the call to `CathedralSiege.refusesOrb` before a frame lights), `command/DrossCommand`
  (`siege`), `command/DrossCommandGameTest` (the three names).
