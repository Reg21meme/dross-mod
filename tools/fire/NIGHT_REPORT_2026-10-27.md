# Night report, October 27, 2026: the Emberdactyl round 3, the fire mobs' animations, sounds, the volcano's hoard

Written for you to read in the morning. Everything below is on the `fire-designs` branch, **not committed** (CLAUDE.md:
never commit unless you ask). I didn't open the game; everything was checked headlessly and in previews.

## What you asked for, and what I did

| Your note | What I did |
|---|---|
| "I like the 2nd design" (round 2's B, the half-moon crest) | It's now the only adult: **E1A**. Rounds 1 and 2 are archived (`tools/fire/firedesigns/emberdactyl_round1/`, `_round2/`; previews in `boss-previews/cinder_wastes/emberdactyl/round1/`, `round2/`). |
| "not all of the semicircle thing is on his beak" | The half moon now stands on the bill all the way along it: its foot follows the bill's top line down to the tip. |
| "his wing is not good" | Rebuilt: a thinner, smoother membrane (four rows, each stretch between two bones cut in two), its trailing edge scalloped between the bones, ribs running across it, small thorns swept back along the forearm, a big hooked thumb claw at the wrist. |
| "he seems so chubby with the bear belly, slim that down" | Slimmer all over (80% of round 2's girth), a deep keel of a chest and no pot belly. |
| "a wingspan of 33 best fits" | 33 blocks tip to tip. |
| "the current baby sizes also work" | The young stays at round 2's size (22.5 across). |
| "a little more lava amount on the kid" | Much more lava in the young's veins (hide, horns, wings). |
| "a little less lava on the scales" (the adult) | Less lava in the adult's hide (about a third of round 2's). |
| "shrink the egg shell amount" | A thinner shell (2 units) and a snugger egg round the curled-up young: about 13.4 tall, 10.4 wide. Four looks: A a little magma, B more, C lots, D cut open. The nest shows B. |
| "for his screech I want to copy Rodan" | The film's recording belongs to the studio, so I made an **original** screech in the same spirit: a huge, high, piercing shriek with a fast warble and a metallic, ringing edge, echoing as if across a valley. Three versions to pick from (see "Sounds"). |
| "work on the animations" | The adult and the young have 11 animations each (below), the young's quicker (your stage 2 rule: "bam bam", no rest). |
| volcano: "add chests or gold at the top or ores and stuff" | Two loot chests at the lookout (a new loot table: gold, Moonsilver, Stormite, blaze rods, diamonds, a rare netherite scrap or enchanted golden apple), heaps of gold blocks, raw gold and gilded blackstone round the lookout, gold in the nest's ring of bones round the egg, and Dross ores (gold, Moonsilver, Stormite) glinting in the crater wall. |
| "make the path up a little more obvious" | Paved with polished blackstone bricks, a **border of pale bone blocks along both edges** (two pale lines up the dark cone by day), a **shroomlight set into its middle every 3 blocks** (a dotted line of light winding up the dark cone, plain from far away at night), and lanterns on bone posts every 6 blocks (was 9). |
| "make animations ... for the other mobs, the 2 mobs we decided on, and showcases for all of that" | The Pallbearer (6 animations) and the Cinder Hermit (7, in all ten shells) move; both galleries have a button per animation, right-click plays the next one, and every animation makes its sounds. |

## The animations

Made by the new `tools/fire/mobs.py` (it reuses the Rimefall animals' kit: legs and wrists planted with the golem IK,
a clipping and under-the-floor check on every frame). Previews: `boss-previews/cinder_wastes/emberdactyl/animations/`
and `boss-previews/cinder_wastes/mobs/animations/` (a GIF of each, a filmstrip page, `index.html`).

- **Emberdactyl** (and the young, 1.6 times as quick): hover (slow heavy wingbeats over its nest: its idle), fly
  (body level, legs tucked), glide (banking), dive (wings swept back, a plunge, a flare out of it), **screech** (it
  rears, wings up, then throws its head forward, jaw wide, head and wings shaking), gust (wings drawn back, one huge
  beat forward: the fire tornadoes' moment), grab (a swoop, legs forward, talons closing), snap (on the ground, a beak
  lunge), perch (standing on its legs and wrists, wings folded up its sides like a pterosaur's), walk (on legs and
  wrists), death (a last scream, wings flailing, it crashes onto its belly, wings spread on the ground).
- **Pallbearer**: idle (bowed under the coffin, censers swinging), walk (a slow funeral march), swipe (a backhand),
  slam (it heaves the coffin up and brings its weight down), roar, death (it sinks to its knees and slumps).
- **Cinder Hermit**: idle, walk (scuttling on its four legs), snap (the magma claw raised and slammed shut), pinch (two
  nips of the small claw), hide (it pulls into its shell, its big claw plugging the opening, the shell settling), peek
  (eyes first, then it climbs out), death.

## Sounds

`tools/fire/sounds.py`, all synthesized from scratch (nothing recorded, nothing from Minecraft or a film). Listen in a
browser: `boss-previews/cinder_wastes/sounds/index.html`; in the game `/dross showcase boss_sounds emberdactyl` and
`/dross showcase mob_sounds cinder_wastes`. The showcase models play them at the right moments of their animations
(the screech at its scream, a wingbeat each beat, the gust, the bell's clang...).

- **The screech, three versions to pick from**: 1 a long shriek, rising, held and warbling, falling away; 2 a harsh
  "kraa" bark then the long "eeeaah"; 3 a trilling, stuttering shriek. The young's are higher and shorter.
- The Emberdactyl's rattles and squawks, wingbeats, the gust's whoosh and roar of flame, the dive's rising scream of
  wind, its obsidian beak snapping, talons closing, hurt, death (a last falling scream, a croak, the crash), steps.
- The Pallbearer's groans, its funeral drum, its rattling roar, swing, slam, hurt, death, steps.
- The Cinder Hermit's clicking, its bronze bell's clang, its claw's snap, hiding, hurt, death, steps.

## Choices I made on my own

- **Not committed** (CLAUDE.md's rule beats the overnight habit of committing as I go).
- The young keeps the adult's moves but each plays 1.6 times as fast (the "no rest" feel); its fight will add more
  hits and faster flight on top.
- In the galleries the moving designs wear their animated models; the statue previews are still drawn by `build.py`.
  Their idle animation plays quietly; sounds only start once you press a control or right-click one.
- On the ground the Emberdactyl stands on its legs and its wing wrists with its long wing fingers folded up its sides
  (real pterosaurs walked like that), rather than standing on two legs.
- The hoard is at the lookout (reachable), not on the nest (that's across the lava lake); the nest gets gold in its
  ring so the egg sits on a hoard.

## Checks (all headless; no game window opened)

- `.\gradlew.bat build`: builds.
- **All 241 GameTests pass** in a brand-new world (`run/night_oct27_r3`), including the new
  `FireGalleriesGameTest.fireAnimationsAreInTheMod` (every moving design's model, texture and animations are in the mod
  at the lengths the game expects; every sound cue points at a real sound) and the mobs' controls (all 11 walk, the 10
  hermits hide). The Emberdactyl gallery test: its 6 models, the volcano's 4,646 lava blocks all still and walled in,
  a path of 143 blocks to the lookout, the egg in the nest and the adult hovering over it.
- Every animation checked frame by frame for clipping and the floor. Clean: the adult and the hermit. Small leftovers
  (a few model units, under a third of a block): the young's wing tip resting into the ground after its death, its
  hands brushing its head in the dive, its membrane grazing the ground at one moment of its walk; the Pallbearer's
  shoulders brushing his neck in his swipe and roar.
- The volcano previews were redrawn from the blocks the game placed: `boss-previews/cinder_wastes/emberdactyl/
  volcano_front.png` (the bone-lined path is easy to see now), `volcano_crater.png` (the hoard and the gold in the nest).

## What to look at when you're back

Your game window from last night is still open on the **old** build: close it and start it again
(`.\gradlew.bat runClient --console=plain`) to see all this. In a flat world with cheats on:

1. `/dross showcase boss_designs emberdactyl`: the adult, the young and the four eggs; press the buttons in chat to play
   each animation (with its sounds), or right-click one for its next. Wait about a minute for the volcano, then
   [path] and [crater] for the bone-lined road, the chests and the gold.
2. `/dross showcase boss_sounds emberdactyl`: **pick a screech** (1, 2 or 3).
3. `/dross showcase cinder_mobs` and its buttons; `/dross showcase mob_sounds cinder_wastes`.
4. Or in a browser: `boss-previews/cinder_wastes/emberdactyl/animations/index.html`, `boss-previews/cinder_wastes/mobs/
   animations/index.html`, `boss-previews/cinder_wastes/sounds/index.html`.

## Not done (left for when you've looked)

- The fight itself (the AI, its hitboxes, the tornadoes, the rebirth), the volcano on the boss-site planner, and the
  mobs' real behaviour and spawning: those come after your picks, as planned.
- The sounds aren't registered as game sound events yet (the showcase plays them by name); the fight will add them.
- Four designs still have a few flickering faces or a loose piece the build flags (11D, 13B, E3D, M2B: small).
