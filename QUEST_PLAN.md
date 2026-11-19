# The Oracle's quest line: the plan

**Status:** agreed November 7, 2026. **Built: stages 1-6, November 9, 2026, not yet tested in-game**, on the branch
`oracle-quests`, in its own copy of the project (`MyMinecraftMod-quests`), so it doesn't mix with the uncommitted
Blightwood candidates. It's made from the user's notes of October 28 and November 7. Each choice I made where the notes
didn't say is marked *(my choice)*. Where the build differs from this plan, or settles a number it left open, is listed
at the end, under "As built". The docs: CLAUDE.md, The plan, step 19; the README, "The Blind Oracle's quests" and "The
Silverwood Giant".

## What the user asked for
- **The Blind Oracle gives the main quests**, from the survivors' camp at the foot of the spire's steps.
- This build covers the first points of the October 28 plan:
  - the Oracle gives the quests;
  - markers over his head;
  - short lines, one or two sentences a right-click;
  - a quest chain ending in "Defeat the Dross";
  - locked lands;
  - locked boss arenas.
- Left out for now: the trophies unlocking a final boss, and the sleeping king's lore.
- **Bosses in this order:** the Cinder Colossus, then the Rat King, then the Huntsman, then the Emberdactyl. **Every boss
  that gets built gets a quest.**
- **Zombies and skeletons leave the Silverwood Forest.** A new forest mob takes their place: a giant warrior like the
  Odyssey's Laestrygonians (not the Cyclops).
  - It's **neutral**. Walk or run past it and it does nothing; hang about near it too long and it attacks; hit it and
    it fights back.
  - It's strong. It shouldn't be common, but there should be enough of them for the quest. Players can still cross the
    forest without trouble.
  - The Oracle asks you to kill some, and you get a regular advancement (not a purple one) for it.
- **No boss before its quest.** The Oracle first sends you to kill a mob or two in the boss's land. You come back, and
  then he sends you after the boss.
- **"Defeat the Dross"** comes after the Emberdactyl, once you've told the Oracle. You go back to him, he talks a little,
  and then he gives it. Its description: you've beaten everything the Dross has to offer; come back later to face more
  dangerous foes.
- Not in this build:
  - the pre-Dross quests (the trader's), which get their own revamp later;
  - the Ragged Seeker's side quests (he keeps his lore lines);
  - rewards;
  - Deathforged books and Necromancy souls (the user deals with them later). Until then they come only from the
    Gravecrags' netherite zombies and skeletons.
  - Parked idea: a passive Dross animal for food.

## The quest line
One quest at a time, always in this order. The list lives in one place in the code, so adding a quest for a new boss
means adding one entry and its lines.

| # | Quest | The Oracle asks you to | Done when | Advancement | Handing it in, his spell opens |
|---|---|---|---|---|---|
| 0 | The Oracle's Plea | listen to him | his story ends | **The Oracle's Plea** (regular) | |
| 1 | The Forest Giants | kill **3** Silverwood Giants | the 3rd kill | **Giant Slayer** (regular) | the forest's barrier: **the Cinder Wastes** and **the Gravecrags** |
| 2 | Into the Ashes | kill **3** of the Cinder Wastes' mobs (any golem, hermit or Pallbearer) | the 3rd kill | **Into the Ashes** (regular) | the **Colossus's volcanoes** |
| 3 | The Cinder Colossus | beat it | "Set in Stone" | Set in Stone (purple, as now) | **Blightwood** and its sewers |
| 4 | Beneath the Rot | kill **3** of Blightwood's creatures (Plague Rats, Ravagers, witches) | the 3rd kill | **Beneath the Rot** (regular) | the **Rat King's cave** |
| 5 | The Rat King | beat him | "Down the Drain" | Down the Drain (purple) | **Rimefall** |
| 6 | First Frost | hunt **3** of Rimefall's beasts (a Pond Skater, a grown moose or a grown Rimehorn) | the 3rd kill | **First Frost** (regular) | the **Huntsman's villages** |
| 7 | The Huntsman | beat him and his Alpha Moose | "The Hunter Hunted" | The Hunter Hunted (purple) | the **Emberdactyl's volcanoes** |
| 8 | The Emberdactyl | beat it and its young | "Wings Clipped" | Wings Clipped (purple) | |
| 9 | Defeat the Dross | tell him | his last words | **Defeat the Dross** (purple, blue fireworks) | |

- **A kill quest comes only the first time you go into a land** (the user, November 18, 2026: not before every boss).
  So "The Burning Mountain" (2 Pallbearers before the Emberdactyl) is gone, and after First Frost there are no more kill
  quests for now. Beneath the Rot counts any of Blightwood's creatures, so the two mobs coming to Blightwood will count
  as soon as they're added to its tag (`oracle/blightwood_mobs`).
- The counts and the names of the regular advancements are first drafts *(my choice)*. They're easy to change.
- **A quest is done the moment you've done it.** For a kill quest that's the last kill. For a boss quest it's the
  boss's advancement, which still comes from the kill as now.
- **A boss you beat earlier counts at once** *(my choice)*. Say a friend fought it with you while your quest wasn't
  there yet: you already have its advancement, so that quest is ready to hand in as soon as he gives it.
- **"Defeat the Dross" always stays last.** A new boss's two quests slot in before it.

## Talking to the Oracle
- **Each right-click gives his next line**, one or two sentences in chat as `<Blind Oracle> ...`. Right-click again to
  hear the next one.
- **How a quest goes:**
  1. **Offer:** 2-4 lines, then the quest starts.
  2. **While it's going:** each right-click is one reminder line with your progress ("Two of the three...").
  3. **Done:** his thanks, 1-3 lines, and then the next quest is ready.
- **His story** (quest 0), from the October 28 notes:
  - he welcomes you;
  - he and the Ragged Seeker are master and student;
  - long ago the Dross went to war on the other worlds, and they helped beat it and lock it away;
  - remnants of it are still scattered through the Overworld;
  - they've been trapped inside since;
  - the Dross has grown too strong to fight from in here, which is why someone had to open the portal from your side;
  - will you help?
- **All the words are lang keys** (`dross.oracle.<quest>.<part>.<n>`), so the wording can be changed without code. I
  write first drafts, and you change them as you like.
- After "Defeat the Dross", a right-click gives his lines for an old friend, 7 in turn (`dross.oracle.after.<n>`; the
  user, November 9: "he knows you by then"), never his lore lines for a stranger again.
- The Ragged Seeker jokes about the big boss whose quest you're on (3 lines in turn, `dross.seeker.<quest>.<n>`), and has
  one line once it's beaten (`.done`); otherwise his lore lines (`quest/oracle/SeekerDialogue`).

## Markers over his head
- **Red ?** means a new quest is waiting: talk to him to start it.
- **Yellow …** means your quest is in progress.
- **Green !** means it's done: go back to him.
- Only you see yours (each player has their own), and only over the Oracle.
- Drawn as a small picture that floats and bobs over his head, seen from about 48 blocks.

## Advancements
```
Enter the Dross
 ├─ Rise!
 └─ The Oracle's Plea                      (regular)
     └─ Giant Slayer                       (regular)
         └─ Into the Ashes                 (regular)
             └─ Set in Stone               (purple)
                 └─ Beneath the Rot        (regular)
                     └─ Down the Drain     (purple)
                         └─ First Frost            (regular)
                             └─ The Hunter Hunted  (purple)
                                 └─ Wings Clipped      (purple)
                                     └─ Defeat the Dross   (purple, fireworks)
```
- Regular advancements show green in chat; the purple ones are challenges.
- `/dross quest reset` clears the Oracle's quests and their regular advancements and "Defeat the Dross". As now, it
  leaves the boss advancements alone.

## Kills that count
- These count: your own kills (a blow, an arrow, a thrown spear), your tamed wolves' and your Necromancy risen
  undead's.
- They count only while that quest is the one you're on.
- Each kill shows your progress above the hotbar ("Into the Ashes: 2 / 3").

## The Silverwood Giant (the new forest mob)
Its working name (entity `dross:silverwood_giant`); the name in the game can change any time.
- **What it is:** a giant warrior of the forest, about 4.5 blocks tall, its eyes glowing electric blue: the Odyssey's
  giant warriors (the Laestrygonians, who hurled boulders at Odysseus's ships and speared his men), in Dross dress. Not
  the Cyclops (the user's correction).
  - **Looks:** I'll make three candidate looks, like members of one tribe, and use the one I think fits best until you
    pick:
    - **G1, the Boulder-Hurler:** wild and rough, a boulder on its shoulder;
    - **G2, the Brawler** (was the Hoplite): no weapon, his bare fists;
    - **G3, the Tree-Bearer** (was the Old Warrior): grown over by the forest, a whole uprooted tree over his shoulder.
    - (The user, November 18, 2026: "no armor on the Silverwood giants ... a giant tree instead of a spear, a rock, and
      then no armor, just hands"; all three in the game, each giant one at random.)
  - Then its animations and its own original sounds.
- **Neutral, with patience** *(my choice of numbers)*:
  - Walking or running past it does nothing.
  - **Stay within about 7 blocks of it for about 4 seconds** and it warns you: it turns to stare at you, its eye
    flares, and it huffs and stamps. **Stay 8 seconds** and it attacks.
  - Moving off calms it down again.
  - Hit it and it fights back.
  - It's slow, so you can always get away. It gives up after about 24 blocks or 30 seconds, like the moose.
- **Strong** *(my choice of numbers)*:
  - about 100 health and heavy armour;
  - a club smash of about 5 hearts;
  - a sweep that knocks you away;
  - a stomp that throws whoever's round it into the air.
  - Like the Odyssey's giants, it hurls a boulder, but only at someone it's angry with. A giant you never bothered
    never throws one. It still gives up after its leash, so running away works.
- **How many** *(my choice)*:
  - Only in the Silverwood Forest, and never within about 96 blocks of another.
  - Never near the hub.
  - Only where there's room for its size.
  - So you meet about one every hundred blocks or so: not common, but easy to find three for the quest.
- **Its spawn egg** goes in the Dross tab.
- **Silverwood's monster list:** zombies, zombie villagers and skeletons leave it; the Silverwood Giant comes in; the endermen and
  the odd witch stay *(my choice)*. So the forest is calm at night, apart from the giants you choose to bother.
  Then (the user, November 18, 2026: "remove witches and like Minecraft regular mobs and cows and stuff from the
  Silverwood forests"): the endermen and witches go too, and so do its vanilla animals and bats; only the giants and the
  odd moose spawn there.

## Locked lands (like the Twilight Forest)
- **Each player's open lands follow their quest** (the user, November 18, 2026: barriers were raised across the Dross
  after the war, one ringing the forest round the hub "so nothing could leave or enter", and the Oracle breaks them):
  - **Silverwood** is always open, and you can't leave it until the giants are dead.
  - Handing in **The Forest Giants** opens **the Cinder Wastes** and **the Gravecrags**; **the Cinder Colossus** opens
    **Blightwood** (its sewers too); **the Rat King** opens **Rimefall** (the table above).
- **Inside a land that's locked for you**, you get Slowness and Hunger as long as you stay. Every 20 seconds a line
  in chat says: "This land rejects you. You cannot enter yet." It goes by the column you're in, so digging under or
  flying over doesn't get round it.
- **Its edge** (the user: "be creative"): a shimmering curtain at the border.
  - Thin electric-blue streaks of light rise from the ground and fade high up.
  - Glowing glyphs of the Dross's own drift up them: angular runes drawn for the mod, like old carvings.
  - Only the players it's locked for see it.
- Players in creative or spectator mode are never affected.

## Locked bosses
- **A boss's home opens when you hand in the quest before its own** (the land's kill quest; the Emberdactyl's with the
  Huntsman's), with the Oracle's spell.
- **A boss at home doesn't rise, wake or come home for a player whose quest isn't up to it**, and they can't hurt it.
- **Its arena is warded:** a player it's locked for who walks in is bounced back out (a push and a flash of the runes, no
  message). The arenas are the Colossus's cavern, the Rat King's cave and the Emberdactyl's volcano.
- **The Huntsman has no arena: he sleeps at home** (the user, November 18, 2026: "before he's unlocked he should be
  sleeping inside his home ... you can hear him snoring ... the moose sleeps and snores too ... magic protects his house
  while he sleeps ... his spear, bow and arrows ... in front of his door ... gone once he wakes"; `boss/huntsman/village/
  HuntsmanAsleep`): with no player near whose quest has reached him (creative alone doesn't count), he lies asleep on the
  rug at his triple bed's foot in his lodge and the Alpha Moose lies in its stall in the barn, its eyes shut, both snoring with every slow breath (Zzz
  rising), nothing hurting them ("Magic protects the Huntsman while he sleeps."); a window of clear ice in his bedroom's
  roof (soul lanterns by his bed so it never melts) lets you look down on him; his spear leans on his lodge's front and
  his bow, bola and arrows (3D, his own at his size) lie on the snow before his door. Breaking a block of his village says "Magic protects the
  Huntsman's home while he sleeps." **Once a player near has handed in First Frost** they wake onto their patrol round the
  village, his gear gone, and they never sleep again; so once you've reached him, every Huntsman you come near is out on
  patrol. Rimefall is locked for a player until the Rat King is beaten anyway.
- Spawn eggs, `/dross boss summon` and the test arenas are test tools, so they're never locked.

## The Oracle's spell
The user, November 18, 2026: "every time you defeat an area ... 'all right, let me remove the barrier, so you can go
defeat the boss now', and then he does a cool animation: lifting his hand, a giant spell circle being cast, a giant laser
going in the air, and then you hear an explosion in the background ... make it cool, a cool destruction thing ...
everyone should be able to see it"; after a boss, "I can feel the energy of [it] dying throughout the land; I can use
some of that energy to open the lair of another boss so you can kill it before it gets its full strength".
- **When**: his last thanks line of every quest in the table above that opens something says he'll break the barrier
  (after a boss, with its dying energy), and he casts the spell as he says it. The opening itself happens at that
  hand-in, whoever is watching.
- **A pause first** (the user: "there needs to be a break for the people to read the message he says before he uses the
  spell"): after that line he stands still for 3 seconds (`OracleSpellTuning.PAUSE`), saying nothing to a click, then
  casts it; he stays still and quiet until it's over.
- **What everyone near sees** (about 12 seconds since November 18, 2026, the user: "make it last a little longer and more
  grand with the sounds"; `quest/client/OracleSpellEffects`, every number in
  `quest/oracle/OracleSpellTuning`): he stands still, raises his arms over his head and tips his head back; a rune circle
  about 13 blocks across opens on the ground round him, a small ring of runes turning the other way inside it and a great
  faint ring outside it, light rising thick off its rim while shards of light spiral in to him and the ground starts to
  tremble (`tools/oracle/spell_textures.py`); a beam bursts up out of his hands into the sky with a shock ring rolling out
  over the ground, pulsing, knots of light running up it, copies of the inner ring floating up over the circle, then three
  thinner beams rise off the circle's rim and wind round into it; it bends out of the sky as a lance towards the barrier
  (the nearest place of the land it opens, or the nearest home of the boss), and far off over the land a flash, two shock
  rings and a pillar of light burst over it, then the boom and its echo; the beams and the lance shatter into falling
  shards of light, the circle fades and his arms come down. Its
  light isn't dimmed by the Dross's fog. Sounds: vanilla's respawn anchor, beacon, warden and thunder pitched down (their
  own subtitles): a charge as he raises his arms and twice more as it gathers, a surge, the beam's burst with a strike and
  a rumble, surges up the beam, the lance's strike, the far boom and rumble and their echo, and the shattering.
- **On show**: `/dross showcase oracle_spell` (the nearest Blind Oracle within 64 blocks casts it at once towards where
  you look, else it's cast from the ground in front of you) and `/dross showcase barrier_fall` (a stretch of curtain in
  front of you falls). Neither changes anyone's quest.
- **The barrier falling**: after the spell, an opened land's curtain still stands for you until you first come within
  24 blocks of its edge (in the Dross, in survival or adventure; standing deep inside it doesn't, and it waits for the
  land there to be loaded); then the stretch round you flares up, cracks and breaks into shards of its runes that tumble
  down and fade, once (`dimension/ward/LandWardFalls`, `client/LandWardFall`; saved per player). Only the land edges, not
  the boss arenas.

## Test commands
- `/dross quest status` shows your Oracle quest too, and `reset` clears it.
- `/dross quest skip` finishes the step you're on.
- `/dross quest set <quest>` jumps you to a quest.

## Who builds what
- **Quest area:**
  - the quest list, saving each player's progress, and counting kills;
  - the new advancements and the chain;
  - the Oracle's lines and dialogue;
  - the messages that tell your game which marker to show and which lands are open;
  - the rules other areas ask ("is this land open for this player?", "may this boss fight this player?");
  - the fireworks;
  - `/dross quest`;
  - and an up-to-date `quest-builder` agent, so "add a quest for boss X" is one request.
- **Villager area:** the Oracle's right-click goes to the quest dialogue; the marker drawn over him.
- **Dimension area:**
  - Silverwood's spawn list;
  - the land wards: the Slowness and Hunger, the chat line, and the edge's streaks and runes.
- **Boss area:**
  - the Silverwood Giant;
  - each boss's lock;
  - the arena wards.
- **World site:** wiring `/dross quest skip` and `set`.

## Stages
Each stage is built and checked headlessly before the next. Nothing is committed until the user says so.

1. **The quest core:** the list, saving, kill counting, advancements, the dialogue lines, the network messages and
   the commands' logic.
2. **The Oracle:** his right-click and his markers; the commands wired up.
3. **The Silverwood Giant:**
   - its candidate looks;
   - its model, animations and sounds;
   - its behaviour and spawn egg;
   - Silverwood's spawns.
4. **Locked lands.**
5. **Locked bosses and arena wards.**
6. **The docs** (CLAUDE.md, the README) and an in-game checklist.

## As built (November 9, 2026)
Where the build differs from the plan above, or settles what it left open. Every number is a labeled constant (the
class named with it).

- **The quests** are as planned (the list, counts and advancement names unchanged; `quest/oracle/OracleQuests`). His
  lines: 7 for his story, 3 to offer each quest, 2 of thanks each, 4 last words (first drafts in `en_us.json`,
  `dross.oracle.*`). When a kill quest is done, a green line above the hotbar says so ("Into the Ashes: done. Go back to
  the Blind Oracle."), besides the progress line on each kill.
- **What opens what** (since November 18, 2026; before, a land opened when you heard its kill quest's offer and a boss
  when you heard its own): a land opens once the quest that opens it is handed in (`OracleProgress.hasHandedIn`), a boss
  once you're on its own quest (`isAtOrPast`), its offer heard or not. So `/dross quest set set_in_stone` opens the
  Colossus at once, and the lands before it. `/dross quest skip` completes a
  waiting or going quest with its own advancement (never a boss's: only beating the boss grants that) and hands in a
  done one; on the last quest it grants "Defeat the Dross" without the fireworks.
- **The fireworks**: 6 rockets round you over about 2 seconds, in the Dross's blues, harmless (`OracleFireworks`).
- **The marker**: about a block over his head, bobbing, full-bright, seen from 48 blocks and growing up to 1.75 times
  its size between 12 and 40 blocks off, never drawn through walls (`villager/client/OracleMarkerRenderer`; drawn by
  `tools/skins/quest_markers.py`).
- **Locked lands** (`dimension/ward/LandWard`): **Slowness V, Mining Fatigue V and Hunger II** (Slowness II until the user's "slowness 5 and mining fatigue 5", November 9), their particles hidden. Rather than
  lasting "as long as you stay", each is given for **11 seconds** and topped up every half second while you stay, and
  taken off the moment you step back into open land: Minecraft makes an effect's icon blink when it has under 10
  seconds left, and 11 keeps it steady. The chat line comes as you come in and every 20 seconds, but never again within
  5 seconds of the last (walking along the border doesn't fill the chat). The column's land is read at y 100, so the
  sewers under Blightwood count as Blightwood (and "Beneath the Rot" opens both).
- **The edge** (`client/LandWardEdge`): streaks rising 14-24 blocks, with 10 runes drawn for the mod drifting up among
  them; your game looks for the edge within 32 blocks, on a grid 4 blocks apart, once a second; at most 10 streaks and
  1 rune a tick, half on Decreased particles, none on Minimal; underground they rise from round your feet. Drawn by
  `tools/terrain/ward_particles.py`.
- **Locked bosses** (`boss/BossLocks`): besides not rising, waking or coming home for a locked player, never targeting
  them and refusing their hits (theirs, their pets', their risen undead's), a boss at home doesn't count them for its
  more-players bonus and they don't keep it from its reset. Its own blows still hurt whoever they hit. A creative player
  still makes a boss come home or rise (so testers see it). Showcase bosses are never locked either.
- **The arena wards** (`boss/BossWards`): looked at every 4 ticks; within 4 blocks of the edge a push back out, deeper
  in back to your last spot outside (if within 64 blocks), else to the ward's way out; the runes, streaks and a soft
  amethyst chime only for you. **The Emberdactyl's ward is its whole volcano from its foot** (since the user's "make the
  barrier for the Emberdactyl further out", November 9; it was only its crater), up to 48 blocks over the rim; the land
  round it stays open (the Cinder Wastes open earlier than the Emberdactyl). The Colossus's: its cavern, the crater's throat and its passage (the gully, flanks and rim open). The
  Rat King's: the rat cave itself, not the tunnels (and he appears asleep on his nest for a locked player too, so
  they can see him from the tunnel: the user, November 9, "for the Rat King I should be able [to see] it sleeping"); `/dross goto lair ratking` lands inside it, so a locked survival
  player is bounced straight into the tunnel behind the arrival spot. The Emberdactyl's test volcano (`/dross boss
  arena emberdactyl`) has no ward.
- **The Silverwood Giant** (`boss/giant/`, every number in `GiantTuning`): 100 health, armour 12, toughness 4,
  knockback resistance 0.85, speed 0.2 (about 1.3 blocks a second wandering, 3.5 running: slower than a walking
  player), 18 XP, no drops; its hitbox 1.8 x 4.4. **Patience**: within 7 blocks, warned at 4 s, attacked at 8 s, the
  count draining twice as fast as it builds. **Its blows**: smash 10 (5 hearts, within 2.2 blocks), sweep 7 (out to 4.5
  blocks, knocked away to its left), stomp 6 (within 4 blocks, thrown about 3.5 up), boulder 8 (at 8-28 blocks, only
  at the one it's angry with). Anger 25-35 s, leash 24 blocks. **Spawning**: weight 40 in Silverwood's monsters
  (endermen 10, witch 5), always alone, never within 96 blocks of another giant, never within 128 of the hub, and only
  with 5 blocks of open air over the 3 x 3 round it. **Its looks** (no armour): G1 the Boulder-Hurler (a rock), G2 the
  Brawler (bare hands), G3 the Tree-Bearer (an uprooted tree), each giant one at random;
  `/summon dross:silverwood_giant ~ ~ ~ {Look:"brawler"}` (or `"treebearer"`, `"hurler"`). It dies the vanilla way.
  Its animations and sounds are its own (`tools/forest_giant/`). Its spawn egg comes after the Cinder Hermit's;
  "Giant Slayer"'s icon is that egg. `/dross showcase silverwood_giants` lines up the three looks.
- **Older worlds** lose Silverwood's zombies and skeletons too (the spawn lists are read as a world loads), so in the
  Dross the Deathforged books and Necromancy souls now come only from the Gravecrags' netherite zombies and skeletons.
