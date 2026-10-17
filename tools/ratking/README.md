# tools/ratking: the Rat King and his rats

Generates the Rat King design candidates and his rats for the mod: GeckoLib models, textures and animations, the Java
design lists, the "Zzz" particle sprite, every preview picture, and (`sounds.py`) all their sounds. The Rat King is one
of the Plague Necromancer's three servants (CLAUDE.md, "The plan", step 9). **Only the looks (and sounds) are built**:
the user picks a design from the showcase; his fight comes later. For how a boss is made in general, see
`BOSS_GUIDE.md` at the project's root.

Needs Python 3 with numpy, scipy and Pillow, plus soundfile for the sounds (`pip install soundfile`). It reuses the
golem kit (`tools/golems/kit`) for the model maths, IK, noise, painting and rendering.

## Rounds

- **Round 1** (six designs, archived in `ratdesigns/round1/`, previews and in-game screenshots in
  `boss-previews/rat_king/round1/`): the user picked No. 1, **Gnawrick**, and two rats: R3 Plague Rat and E3 Plague
  Ravager.
- **Round 2** (seven builds of Gnawrick, archived in `ratdesigns/round2/`, previews in `boss-previews/rat_king/round2/`):
  the same look and height, different widths and depths: 1A the original, 1B skinny, 1C broad shoulders, 1D barrel
  chest, 1E potbelly, 1F hulking, 1G hunchback. Built in: he sleeps curled up on the ground; his ears and the staff
  never pass through each other, nor the staff through him or the floor; the burrow's dirt mound is where his head goes
  in. The user picked **1C**.
- **The pick** (`ratdesigns/gnawrick.py`, the only design built): Gnawrick 1C, with the user's notes on round 2: his
  rage pumps his free fist in the air (out to his side) and smashes the staff's foot into the ground; his fist grips the
  staff across its knuckles (`rig.grip_across`); the death's X's only on the front of his face; the staff's head all
  green. The rats: only the picks, the Plague Rat and the Plague Ravager (round 1's eight are archived in
  `ratdesigns/round1/rats_round1.py`); their spots are mirrored on both sides.

## Running it (from the project folder)

```
python tools/ratking/build.py                  # everything (about 10 minutes)
python tools/ratking/build.py --kings 1        # only the king (no rats, no Java lists)
python tools/ratking/build.py --rats           # only the rats (and RatDesigns.java)
python tools/ratking/build.py --previews-only  # previews only: the game files are left alone
python tools/ratking/build.py --clip 1 4       # only the clipping check, on those builds
python tools/ratking/build.py --quick <dir> 3  # quick still views of build 3 into <dir> (no animations)
python tools/ratking/build.py --sheet <dir> 3  # build 3's full sheet into <dir>
python tools/ratking/build.py --frames <dir> 3 rage 25 4 0.5 1.2   # big frames of one animation (yaw, scale, times)
python tools/ratking/build.py --ball           # only his poison ball (ratkit/ball.py)
python tools/ratking/build.py --charge <dir>   # only the spear charge's page (side views) into <dir>
python tools/ratking/build.py --bars           # only the boss bar options (same as python tools/ratking/bossbars.py)
python tools/ratking/build.py --trophies-only  # only his five trophies (about 7 minutes; his own files are left alone)
python tools/ratking/build.py --trophies-only --candidates   # the ten candidates' previews again (the record; no game files)
python tools/ratking/sounds.py                 # every sound (about 10 seconds)
```

A full build takes about 10 minutes (the clipping check and the staff guard run on every frame of every animation).

## What it writes

| Path | What |
|---|---|
| `assets/dross/geo/entity/rat_king/<id>.geo.json` | each Rat King build's model |
| `assets/dross/animations/entity/rat_king/<id>.animation.json` | `animation.rat_king.` sleep, wake, idle, walk, burrow, burrowed, popup, rage, death, dead, and the fight's run, sweep, cast, charge_start, charge, charge_skid |
| `assets/dross/{geo,animations,textures}/entity/plague_ball/plague_ball...` | his poison ball (`ratkit/ball.py`): model, `animation.plague_ball.spin`, texture and `_glow.png` |
| `assets/dross/textures/gui/rat_king_bar/option_<n>.png` | the eight boss bar options (`bossbars.py`) |
| `boss/client/RatKingBarStyles.java` | the generated list of the bar options (regions and offsets, below) |
| `assets/dross/textures/entity/rat_king/<id>.png`, `_glow.png`, `_rage.png` | texture; glowing parts (the orb, the gem); his red eyes |
| `assets/dross/{geo,animations,textures}/entity/rat/<id>...` | the rats (`animation.rat.` idle, run); `_glow.png` red eyes and boils |
| `assets/dross/textures/particle/zzz.png`, `particles/zzz.json` | the Zzz particle |
| `boss/ratking/RatKingDesigns.java`, `RatDesigns.java` | the generated design lists (names, sign text, every animation timing the mod's particles and sounds use) |
| `boss-previews/rat_king/` | `ratking_<label>_<id>_<front/side/back>.png`, `ratking_<label>_<id>_sheet.png` (four views, at night, enraged, and a filmstrip of every animation with the Zzz and steam sketched in), `ratking_<label>_<id>_charge.png` (the spear charge from the side, blended between its animations the way GeckoLib does), `ratking_all.png` |
| `boss-previews/rat_king/rats/` | `rat_<label>_<id>_<front/side/back>.png` and `rats_sheet.png` |
| `boss-previews/rat_king/plague_ball.png` | the poison ball from a few angles, at night, and its tumble |
| `boss-previews/rat_king/bossbar-previews/` | `option_<n>.png` (normal and Rage at full, half and low health, 3x) and `all_options.png` |
| `assets/dross/sounds/rat_king/`, `rat/`, `enraged_rat/` | the sounds (`sounds.py`) |
| `assets/dross/sounds.json`, `lang/en_us.json` | their events and subtitles (`sounds.py` adds its own and leaves the rest alone) |
| `boss/ratking/RatKingSounds.java` | the list of every sound event, for `/dross showcase boss_sounds ratking` |
| `boss-previews/rat_king/sounds/index.html`, `spectrograms.png` | a page that plays every sound (open it in a browser), and what each looks like |
| `assets/dross/geo/block/` and `geo/item/rat_king_trophy.geo.json` | his trophies' model (`ratkit/trophies.py`, below): his model plus the trophies' extra bones; two copies (GeckoLib shares a model's bones between everything drawn with it) |
| `assets/dross/animations/block/rat_king_trophy.animation.json` | `animation.rat_king_trophy.design_<1-5>`: each trophy's held pose |
| `assets/dross/textures/block/rat_king_trophy/rat_king_trophy.png`, `_glow.png`, `_rage.png` | his texels with the mound's under them (256 x 256); the glow; his red eyes |
| `boss-previews/rat_king/trophies/` | `trophies.png` (the five on their blocks, and at night) and `trophy_<nn>_<key>_<front/side>.png`; the ten candidates' previews (the record) in `candidates/` |
| `boss/ratking/trophy/RatKingTrophyModels.java` | generated: each trophy's scale, outline box, glow, red eyes, light level, sign look and particle spots |
| `assets/dross/lang/en_us.json` | the trophies' names (`block.dross.rat_king_trophy` and `.design_<n>`; only those keys are added or changed) |

## The designs

`ratdesigns/gnawrick.py` (round 2: one `Build` per width and depth) and `ratdesigns/rats.py` (all eight rats). A design
gives its numbers and sign text, its proportions and posture (`body()`, a `ratkit.rig.RatBody`), its boxes (`build`),
its staff (`build_staff`, sized after the posture so its foot reaches the ground), its materials (`materials()`,
painters from `ratkit/fur.py`) and its `STYLE` (walk, sleep and idle gesture). Every tuning number is a labelled
constant; change a design there and run the build again.

| Module | What it does |
|---|---|
| `ratkit/rig.py` | the upright skeleton, the posture and rest pose (IK legs and free hand), world-direction helpers, the cape drape solver |
| `ratkit/parts.py` | the body (head with snout, incisors, whiskers, beady eyes, hidden eyelids, X eyes and tongue), crowns, staffs, capes and extras, the hidden dirt hole (`HOLE_FORWARD` in front of him, sized to his belly, hidden deep enough that its tallest clod stays `HOLE_HIDE_GAP` under the ground) |
| `ratkit/fur.py` | material painters |
| `ratkit/king_anims.py` | the Rat King's animations and their secondary motion (cape, tail, censer, ribbons); `staff_bed` finds where his staff can lie beside him while he sleeps |
| `ratkit/guard.py` | the staff guard: every frame, tilts or slides the staff in his fist (or lifts and pushes it, lying loose) just enough to keep it out of his ears, head, crown, body, cape and the floor |
| `ratkit/clip.py` | the clipping check the build runs on every animation (staff, ears, crown, body, cape, floor) |
| `ratkit/quad.py`, `ratkit/rat_anims.py` | the rats' skeleton and animations |
| `ratkit/views.py` | previews (clipped at the ground, cut-out transparency, particle sketches) |
| `ratkit/javagen.py` | the Java design lists |
| `ratkit/ball.py` | his poison ball (the projectile his cast launches) |
| `ratkit/trophies.py` | his five trophies, and the ten candidates they were picked from (below) |
| `bossbars.py` | the boss bar options (pixel art, the textures, `RatKingBarStyles.java`, the previews) |
| `ratkit/synth.py`, `sounds.py` | the sound synthesizer (voices with vowels, squeaks, whistles, knocks, thuds, whooshes, reverb) and every sound made with it |

Parts that only show sometimes (eyelids, the X eyes, the tongue, the dirt hole) are built hidden inside the head or
under the ground, and the animations slide them out (`parts.HIDDEN`). The one exception: the mod's renderer also hides
the dirt hole's bone unless he's burrowing, under the ground or popping up (it would show under him when he's knocked
up into the air). The burrow's hole is `HOLE_FORWARD` in front of him (he digs and dives in head first); the pop-up
slides it back right under him (`king_anims.POPUP_HOLE_FORWARD`, 0): he springs straight up out of it and lands on the
spot. `king_anims.hole_z` tells the clipping check which hole each animation uses, and the Java list gets both spots
(`RatKingDesigns.HOLE_FORWARD`, `POPUP_HOLE_FORWARD`) for the dirt particles.

Watch out: `model.pose_matrices(pose, yaw_deg=0.0)` turns the model round to face the way it does in the game (the
renderer uses it), so his front is +z there; checks against model-space spots (the hole at `-HOLE_FORWARD`) must use
`pose_matrices(pose)`.

## The fight's animations

His fight has six animations of its own, in `ratkit/king_anims.py` (every number a labelled constant at the top of
each; `record()` runs every frame through the staff guard and the build's clipping check covers them like the others):

- **run** (loop, `RUN_*`): a fast, low scurry, leaning well in, on his toes, the staff carried forward in his fist
  with the orb leading, his cape streaming out behind (`Secondary`'s `"stream"` cape mode, `CAPE_STREAM_*`) and his tail
  out straight. Made in place: one loop would carry him `RUN_STRIDE` units in `RUN_T` seconds (6.6 blocks a second);
  the generated `runBlocks` / `runTicks` let the mod play it as fast as he really moves, so his feet don't slide.
- **sweep** (once, `SWEEP_*`): his close blow. He slides his fist down to the staff's foot, winds it back to his right
  and sweeps it flat round his front at chest height (his fist on a smaller arc than the staff's head, which whips
  through), follows through to his left and brings it back round and up to his hold. `sweepHit` is the moment it passes
  his front; `sweepReach` how far the orb reaches from his middle then.
- **cast** (once, `CAST_*`): he holds the staff out in front, aims (a squint, a tremble), draws it back and flicks it
  forward; the poison ball leaves the orb at `castRelease`, from `castForward` / `castUp` / `castRight` (blocks in front
  of him, above his feet and to his right: the orb at that moment). Then he eases back into his idle hold.

- **charge_start** (holds, `CHARGE_START_T`, `WINDUP_*`): the spear charge's wind-up (the user's request, October 16).
  The mod's wind-up is 0.4 s (`RatKingTuning.CHARGE_WINDUP_TICKS`): GeckoLib's 0.2 s blend carries him into this
  animation's first frame, already crouched low in a sprinter's stance with the staff levelled forward like a spear,
  and these 0.2 s coil him lower, drawing the staff back a touch, trembling.
- **charge** (loop, `CHARGE`, `LANCE_*`): the dash. The run's legs (`scurry`, shared with the run, which it leaves
  exactly as it was), faster and lower (one loop `CHARGE["stride"]` units in `CHARGE["T"]` seconds: 9 blocks a second),
  the staff slid through his fist (`LANCE_SLIDE`, held near its foot) and held out in front of him like a lance. Its
  meta gives `reach` (how far in front of his middle the orb leads: `chargeReach`, what the dash hits) and the stride,
  so the mod plays it as fast as he really dashes.
- **charge_skid** (holds, `CHARGE_SKID_T`, `SKID_*`): a miss (or a wall). The mod's 0.5 s recovery
  (`RatKingTuning.CHARGE_RECOVER_TICKS`) is the blend into the first frame (braced, leaning back, his left foot dug in
  on its heel) and these 0.3 s straightening up into his idle hold. A hit goes straight into the sweep instead.

The charge's sound is `sounds.py`'s `king_charge` (`entity.rat_king.charge`, two versions: a war-cry squeak and a
rising squeal over scurrying claws and rushing air).

Helpers they share: `staff_hold` (IK the staff arm so his fist sits on a spot and the shaft points a way, with the
staff slid through his fist), `orb_point`, `direction_at` (yaw 0 = straight ahead, + his left), `plant_feet_clear`
(plants the feet, then re-plants any that ended up in the floor). The sheet's strips sketch the sweep's orb trail and
the cast's ball.

## The trophies

`ratkit/trophies.py` (run by the full build, or `build.py --trophies-only`): 1x1x1 trophy blocks, like the Cinder
Colossus's (`tools/golems/kit/trophies.py`). Each is his own model and textures scaled down to fit one block (at most
`FIT_SIDE` / `FIT_HEIGHT` of it, and never bigger than `MAX_SCALE`, so standing and lying poses look the same size), no
base, holding one moment of his own animations (`TROPHIES`: the animation, the time, how far the figure is turned on its
block, and the showcase sign's `look`).

Phase 1 made ten candidates (kept in `TROPHIES` as a record; previews in `boss-previews/rat_king/trophies/candidates/`,
rendered again with `--trophies-only --candidates`): 1 Fast Asleep (sleep), 2 Rude Awakening (wake, snatching up his
staff), 3 Sly Schemer (idle, the drum), 4 Staff Sweep (sweep), 5 Plague Flick (cast, the flick), 6 Tantrum (rage, the
screech), 7 Ground Pound (rage, the slam), 8 Down the Hole (burrow, the dive), 9 Pop Goes the King (popup), 10 Belly Up
(death). **The user picked five** (`PICKED`), which are the ones in the game, numbered 1 to 5 in this order:
**1 Fast Asleep** (candidate 1), **2 Tantrum** (6), **3 Ground Pound** (7), **4 Pop Goes the King** (9), **5 Belly Up**
(10). Right-clicking a placed trophy cycles them; he gives No. 5 to every player who beat him (`RatKingTuning`).

A few change a detail of their moment (`tweak`, every number a labelled constant): 2 the startle's face (eyes wide,
jaw dropped, ears up), 3 a sly squint, head cocked and the drumming hand higher, 5 his poison ball leaving the orb, 8
his staff left planted in the dirt beside the hole, 9 both arms flung up with his staff held upright (in the pop-up
it's still deep in the hole), 10 his crown back on his head, knocked askew.

Bones a design doesn't show are scaled to 0 in its pose, and the root is moved so the figure stands in the middle of
the block, its lowest point on the floor. The trophy model adds only the extra bones the built designs use (built under
the floor, hidden unless used): `trophy_mound`, a small dirt mound (game design 4; candidates 8 and 9: his hole sits on
top; whatever of him is wholly under the ground is hidden, the rest is inside the dirt: the top tier is solid, the tiers
under it are rings of strips, which saves a lot of box-UV texture; it's sized to hold its designs and stays square to the
block when a design turns the figure); `trophy_staff`, a copy of his staff standing on its own (candidate 8 only); and
`trophy_ball`, ball.py's lumps with a glowing trail (candidate 5 only). Every pose goes through `ratkit/clip.py` (in his
animation's own frame; the mound's designs under the burrow's and pop-up's rules) and a check that nothing goes below the
block's floor. (Ground Pound reports one of his toes 0.3 units into the rage animation's floor at that moment; on the
trophy the figure stands on its lowest point, so nothing goes under the block.)

The mod's side is `boss/ratking/trophy/` (the block, its block entity and item, `RatKingTrophies`, the generated
`RatKingTrophyModels`; `client/`: the renderers, the glow and red-eye layer and the particles from the generated spots).

## The poison ball

`ratkit/ball.py`: a clumpy, brownish-green ball of poison, six lumps of different sizes (`LUMPS`: middle, size and
tilt each) stuck together, about 0.6 blocks across, with a couple of darker sickly spots and pale pustules (`SPOTS`),
and a faint glow (`_glow.png`: faint all over, brighter veins; alpha is the strength). Bones: `root` > `ball` (the
tumble, its pivot the ball's middle, `BALL_CENTRE_Y` = 4 units above the origin, so it fits a 0.5-block box) >
`lump_0`..`lump_5` (each swells and shrinks a little in turn). `animation.plague_ball.spin` loops every `SPIN_T` seconds.

## The boss bar options

`bossbars.py` draws eight options for his custom boss bar, each in a normal look (sickly green) and a Rage look (the
same shapes in reds, a little angrier), as Minecraft GUI pixel art: 1 Crown (its gem glows red in Rage), 2 Nose &
whiskers (teeth bared in Rage), 3 Curling tail, 4 Fur edge (bristling in Rage), 5 Gnawed, 6 Sewer pipe (drips under the
ooze), 7 Claw & orb, 8 Beady eyes (red, with a scowl, in Rage). The palettes are in `LOOKS`; every size is a labelled
constant; each option's art (both looks) must fit 200 x 20 pixels, which the script checks.

Each option's texture holds six parts, one under another: for the normal look, then the Rage look, the empty bar, the
full bar and the decorations. `RatKingBarStyles.java` lists, for each part, where it is in the texture (u, v, width,
height) and where it's drawn relative to Minecraft's boss bar position (x, y: the top-left of the vanilla 182 x 5 bar;
negative is above or to the left). To draw a bar: blit the empty bar, then the full bar cut off at the health (its
columns from `fillLeft` for `round(progress * fillWidth())` pixels; the fill is 182 wide like vanilla's), then the
decorations. The name goes centred on x + 91 at `nameY` (lower than vanilla's -9 where decorations stand over the
middle), and each bar takes `height` pixels before the next. The previews draw exactly that, so they match the game.

## The sounds

All original (nothing recorded, nothing from Minecraft): `sounds.py` builds each one out of tones and noise with
`ratkit/synth.py`. The events the mod plays now are registered in `registry/ModSounds`; the attack versions (claw
swipe, bite, staff strike, plague orb, summoning, sonic screech, tail whip, plague breath: two or three versions each)
wait for the fight. `RAGE_WHISTLE` in `sounds.py` picks which of the three train whistles his rage plays. Since the
author can't listen, every sound is also drawn as a spectrogram (`spectrograms.png`) to check its shape.
