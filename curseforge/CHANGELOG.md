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
