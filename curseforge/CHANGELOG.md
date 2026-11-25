# The Dross 0.4.0: the quest update

**Start a new world** for the full update: the knight ruins only appear in land that hasn't been made yet.

## A new way into the Dross

- **The Weathered Letter** now turns up in about one Nether Fortress or Bastion chest in five.
- **The Dross Trader** has his own quests now, with a marker over his head (a red **?** when he has one for you, a
  yellow **...** while you're on it, a green **!** when you can hand it in). He tells you what the Dross is and why its
  knights are still on our side, gives you 3 golden apples and points you to the nearest knight ruin.
- **Clear three knight ruins** (beat both knights at each), then he makes you a **Portal Orb** and tells you roughly
  where the cathedral is. Lose the orb and two more ruins get you a new one.
- **The Fallen Cathedral is under siege:** five knights roam round it and a spell keeps it shut until all five are
  dead. Break the siege (for everyone in the world), throw the orb into the frame, and the portal opens.
- Gone: the tributes and key materials, the Dross Compass, the Rift Key, and the chat messages that gave away the
  cathedral's and the trader's locations.

## The elemental knights

- **Ten new knights**, two of each element: the Thawed Axeman and Spearman (ice), the Blight Warden and the Sporebound
  (poison), the Cinder Knight and Flamehand (fire), the Wreck Knight and the Stormblade (thunder), the Geode Knight and
  the Rockfall Knight (earth). Each has its own look, sounds, two moves and spawn egg, and shrugs off its own element.
- **Their ruins:** small broken-portal fight spots in their element's biomes all over the Overworld (76 designs, each
  biome its own), and now in the **Nether** too (12 designs: fire knights in the Nether Wastes, Crimson Forest and Soul
  Sand Valley, poison knights in the Warped Forest, earth knights in the Basalt Deltas), about one every 1,000 blocks.
  Their knights come back 10 minutes after you beat them, once nobody's near.

## The Blind Oracle's quests (in the Dross)

- At the survivors' camp the **Blind Oracle** gives the Dross's main quests, one at a time: the forest's giants, a
  hunt in each new land, then each boss in turn (the Cinder Colossus, the Rat King, the Huntsman, the Emberdactyl),
  ending in *Defeat the Dross* with fireworks. The Ragged Seeker has jokes about every boss.
- **Locked lands:** the Dross's lands open as your quests go on. The Oracle breaks each land's barrier with a great
  spell you can watch, and you see the barrier shatter the first time you reach it. Inside a land that's still locked
  for you, you're slowed and weakened.
- **Locked bosses:** a boss at home only fights you once the Oracle has broken the ward on its home.
- **Protected boss homes:** you can't break the blocks of a boss's home until you've beaten that boss (like bedrock:
  no cracks and nothing slows you). The hub is always protected.

## New giants in the forest

- **The Silverwood Giants:** neutral giants that replace the Silverwood Forest's zombies and skeletons, in three looks
  (the Boulder-Hurler, the Brawler and the Tree-Bearer). Linger near one and it attacks: smashes, sweeps, stomps and
  hurled boulders.
- **A new icon:** the Boulder-Hurler's head.

## Removed

- **Necromancy and Deathforged** (the enchantments, their books and the risen undead). They may come back in another
  form later.

## Settings

- `knight_ruins` in `config/dross-common.toml`: how far apart the ruins are (Overworld and Nether), how many near spawn,
  and how long their knights take to come back.

## For testing (creative / cheats)

- `/dross locate ruins` and `/dross goto ruin [<element>]` (in the Overworld or the Nether), `/dross siege
  status|reset|break`, `/dross showcase knights`, `/dross showcase knight_ruins [<element>]`.

---

# The Dross 0.3.0: the Emberdactyl update

## New boss: the Emberdactyl

- A giant volcanic pterosaur, 33 blocks across its wings, the Fire Necromancer's second servant.
- It lives on its own **volcano** in the Cinder Wastes: one in every few Cinder Wastes areas, with a lava lake in its
  crater, its egg on a basalt pillar in the middle, lava rivers down its sides, a lit path up to a lookout, and a hoard of
  gold at the top.
- Come within about 60 blocks and it screeches and takes off (400 health). Its attacks:
  - dive bombs that blow everything back;
  - fire tornadoes that pull you in and throw you into the sky;
  - fiery strafing runs;
  - a talon grab that carries you off and drops you (never for more than half your health);
  - wing buffets and a screech that knock you away;
  - five beak pecks when it lands, and pecks in mid-air at anything flying.
- At half health it gets faster and its volcano rains lava bombs.
- **Beat it and its egg hatches.** Its young reborn (175 health) bursts out 2.5 times as fast, leaving a trail of fire
  wherever it flies.
- Beat the young for the challenge advancement **Wings Clipped** and a trophy (five poses: right-click to change).
- Its own boss bars, sounds (a kaiju-sized screech), spawn eggs (the adult and the young) and trophies.

## Tougher bosses

- **More health:** the Cinder Colossus 375 (from 300; its stages now change at 75% and 40%), the Rat King 250 (from
  200; still three burrows before his Rage and three after), the Wild Hunt 275 (from 250), and after the split the
  Huntsman 175 and the Alpha Moose 200 (from 150 each).
- **The Rat King's staff is poisoned:** his sweep and his spear charge give Poison II for 4 seconds (5 in his Rage).
- **No hit takes more than a fifteenth of a boss's health,** so a boss always takes at least 15 hits, whatever your
  weapon. (`/kill` and the creative Admin Sword aren't capped.)
- **More players, a stronger boss:** every extra player in its fight gives it 50% more health and 20% more damage. Someone
  joining mid-fight keeps its bar where it was, and the bonus stays until it resets.
- **Bosses reset:** with nobody within 64 blocks for 3 minutes, a boss is back to full health, its bonus gone, and back
  to resting (the Rat King asleep, the Colossus dormant, the Emberdactyl on its perch, the Huntsman calm in the saddle).

## New mobs in the Cinder Wastes

- **The Pallbearer**: a 9-block skeleton carrying a burning coffin. It's neutral: hit it and it roars, swipes and slams
  its coffin into the ground.
- **The Cinder Hermit**: a hermit crab in one of eight shells (a bronze bell, an iron brazier, a horned skull, an urn,
  a magma conch, a crystal geode, a giant lantern, a piece of a colossus). It's neutral: hit it and its neighbours join
  in. Every time it loses a quarter of its health it hides in its shell, where hits do half damage, then peeks out
  and comes back fighting.
- **Hermit crab caves**: one or two in every Cinder Wastes area, each with its hermit (who keeps to its den): dens dug into hillsides round
  a smoking vent, and shallow dens under mounds of ash with a loot room (pots, suspicious gravel, a barrel).
- Spawn eggs for both.

## No more freezes when a world starts

Players with big modpacks saw the game freeze (or crash) when a new world started, while the mod searched for its portal
site, built the hub and laid out villages for the trader. All of that is gone: the three are now made by world
generation, with the land, like a village.

- **The Fallen Cathedral** (the portal site): one per world, 3,500 to 4,500 blocks from spawn, preferring forests,
  taigas and snowy land, away from villages. It's worked out from the seed as the world loads (about a second, in the
  background) and built when its land is first made. The trees round it are cleared once its land is loaded.
- **The hub** (the Shattered Spire at Dross 0,0, with the survivors' camp) is made with the Dross land the first time
  anyone goes there. The land you'll arrive on starts being made the moment you step into the portal.
- **The trader**: still one per world, in his Rift Chapel at the edge of a village near spawn (the seed picks which
  one), with a path to the village's street. Once a village has had the trader, it never gets another.
- With **Generate Structures off** (or in a superflat world) there's no cathedral or chapel: the hub's portal brings you
  back near the world spawn.
- New worlds only.

## A settings file for modpacks: config/dross-common.toml

- `portal_site`: how far from spawn the cathedral can be (3,500 to 4,500 by default).
- `trader`: whether his chapel stands at a village (`atVillages`; turn it off if another mod's villages cause trouble,
  and it stands on its own instead) and how far from spawn his village can be.
- `quest`: how often a Nether Fortress or Bastion chest holds a Weathered Letter.
- `performance`: how many threads the Dross's boss-site planner uses, and how many milliseconds a tick may go on
  building a boss lair or volcano.
- The cathedral's and the trader's settings are saved in each world when it's made, so changing the file later never
  moves them in a world that already has them.

## Changes and fixes

- The hub's two survivors, the Blind Oracle and the Ragged Seeker, now live in a camp of their own at the foot of the
  Shattered Spire's front steps: a fallen slab of the spire's crown, a blue soul fire, his books and the Seeker's crates.
- Mobs no longer stutter as they speed up or slow down while walking.
- The Pallbearer and the Cinder Hermit find their way round walls and hills for their whole size (they could walk round
  in circles near one), and a hermit crab cave's hermit walks back to its den after a fight.
- New drawn advancement icons for every boss: the sword in the stone (Set in Stone), the Rat King's crown down the
  drain (Down the Drain), the Huntsman's mounted hood (The Hunter Hunted) and a clipped wing (Wings Clipped).
- The Rat King is immune to fire and lava, and throws his plague balls up at anything flying over him.
- The Huntsman and his Alpha Moose only go for mobs they can see (they no longer chase mobs underground), and leave
  players in creative mode alone.
- Moose have two white eyes (one eye looked covered).
- Killing a boss with the Admin Sword (creative) now gives its advancement and trophy.

## For testing (creative / cheats)

- **Rage Wand**: sneak + right-click rages every mob round where you look onto the wand's team. It now also works on
  the Wither, the Ender Dragon and the Dross trader, and shows every team's colour while you hold it. A raged Ender
  Dragon or Warden shows its health bar.
- **Rage Bow**: a hitscan gun, no arrows needed: it fires instantly where you look (hold to keep firing), and every
  mob round what it hits joins its team.
- `/dross rage team <targets> <team>` and `/dross rage clear <targets>` put players and mobs on teams.
- `/dross boss arena bedrock [teams]`: a roofed test arena, divided into 1-5 team sections with walls that open and
  close.
- `/dross goto lair emberdactyl`, `/dross goto hermit_cave`, `/dross boss summon emberdactyl|emberyoung`,
  `/dross boss arena emberdactyl`.
