# Golem generator (Cinder Colossus)

Generates the Cinder Colossus (the golem mini-boss that serves the Fire Necromancer): its GeckoLib model, animations
and textures, the preview images in `boss-previews/`, and the Java design list `boss/golem/GolemDesigns.java`. The mod
itself only reads the finished files; Python is needed only to change the design.

**The pick:** the user picked **3G Saddleback** (`designs/d3g_saddleback.py`) from round 3, the only design built
now. Its parts are shared with the other round-3 designs in `designs/calderon.py`. **Rounds 1 to 3**' other
candidates are kept for reference in `designs/round1/` to `round3/` (not built; their previews are in
`boss-previews/round1/` to `round3/`). Rounds 1 and 2 were written for older versions of the kit (round 2's volcano
stood upright, with a `tilt` argument) and need changes to build again; round 3's still build if added back to
`DESIGN_MODULES` in `build.py` (with today's kit: eruptions along the volcanoes, longer forearms).

Needs Python 3 with numpy, scipy and Pillow.

## Run it

```
python tools/golems/build.py               # all designs: game files, previews, GolemDesigns.java
python tools/golems/build.py 2 4           # only designs 2 and 4 (GolemDesigns.java and all_golems.png are left alone)
python tools/golems/build.py --previews-only 3          # previews only, the game files are left alone
python tools/golems/build.py --scratch <folder> 1       # everything into <folder>, to try things out
```

After changing a design: run `build.py` (all designs, so `GolemDesigns.java` and `all_golems.png` stay complete),
then rebuild the mod. A full build takes a few minutes (the animations solve the limbs frame by frame). It also
deletes the generated game files of designs that are no longer in the list.

## What it writes

| File | What it is |
|---|---|
| `assets/dross/geo/entity/cinder_colossus/<id>.geo.json` | the model (Bedrock geometry 1.12.0, box UV, 16 units = 1 block) |
| `assets/dross/animations/entity/cinder_colossus/<id>.animation.json` | `idle`, `walk`, `erupt`, `break_shell`, `death`, `statue`, and the volcano's own `volcano_burst` and `volcano_erupting` |
| `assets/dross/textures/entity/cinder_colossus/<id>.png` | the look (rock shell with dim cracks; lava painted as cooled crust) |
| `.../<id>_glow.png` | the parts that glow, drawn full-bright on top (cracks, eyes, the whole lava body) |
| `.../<id>_flow.png` | the lava poured over the shell when the volcano erupts; its alpha says how soon each pixel is reached |
| `.../<id>_statue.png` | the golem cooled into obsidian (the death fades it in) |
| `boss-previews/NN_<id>_<dormant/erupted/core>_<front/side/back>.png`, `NN_<id>_sheet.png`, `all_golems.png` | previews |
| `src/main/java/.../boss/golem/GolemDesigns.java` | the design list and timings (generated: don't edit it by hand) |

## The three stages

- **Dormant** (start of the fight): the rock shell (`shell_*` bones), a few dim cracks, the volcano only smoking.
  Its crater pools always hold bright lava: they're the only fully opaque pixels of the flow texture, so the mod's
  dissolve pass shows them even before any lava has poured.
- **Erupted** (halfway through the first stage): the `erupt` animation plays while the volcano's `burst_*` bones blast
  out of the crater (`volcano_burst`), and the flow texture pours the lava down over the shell (the mod reveals it
  over `FLOW_REVEAL` in `kit/previews.py`).
- **Lava core** (second stage): the shell breaks off (`break_shell`); a bigger lava volcano (`molten_*` bones) grows
  out and its fountain and lava bombs (`vent_*` bones) erupt all the time (`volcano_erupting`, its own controller).

The death cools it into obsidian from any stage.

## How a design is put together

- **Bones** (`designs/common.py`): every design shares one skeleton: `root`, `pelvis`, `torso`, `head` with
  `jaw` (the outer jaw) and `jaw_inner`, three-part arms (`arm_*`, `forearm_*`, `fist_*`) and legs (`thigh_*`,
  `shin_*`, `foot_*`). The lava body hangs on these. Shell plates (`plate()`) are each their own bone, so each
  can fly off. `volcano()` builds a stepped cone that grows out of the back along a direction (`back_dir()`: straight
  out of the back, raked towards the tail or the head, splayed to a side), its lowest level sunk into the body so
  nothing hangs off: rock levels and rim, the crater's lava pool, the bigger lava cone of the core form, and a
  `*_crater` marker the mod uses for particles (with `*_crater_aim` on the cone's axis above it, so the mod knows which
  way the crater points). The fountain, bombs and blast hang on frames (`*_spout`, `*_blast`) lined up with the cone,
  so the lava shoots out the way the volcano leans (the user's wish); the bombs and the blast's rocks still fall with
  real gravity. (`jet="upright"` would point them straight up instead: `upright()` cancels the body's tilt, and
  `volcano_burst` then turns the blast frame back against the body's lean during `erupt`.)
  `spike()` builds a tapering spike pointing along a direction, its foot sunk into what it grows from (`embed`).
  `designs/calderon.py` holds the round-3 body, shell, head (horns, beard), limbs and spine ridges. Its forearms are
  2 units longer than round 2's, so the elbows rest a little bent and the arms have room to reach in the animations.
  The shoulder pads, their spikes and the lava shoulder caps hang on `pauldron_mount_*` bones at the shoulder joints,
  marked as followers (`bone.follow = (leader, share)`): every animation turns them back against all but `PAD_FOLLOW`
  of the arm's turn (`Rig.follow`), so they move like armour strapped to the body instead of sliding through the chest.
- **Pose**: the design gives proportions and where the knuckles and feet stand; `kit/ik.py` bends the limbs so they
  stand flat on the ground (the rest pose is written into the model's bone rotations).
- **Textures** (`kit/paint.py`, `kit/materials.py`): every texel is painted from where it sits on the model in 3D,
  so rock chunks, cracks and lava flow run on across box edges. Each box names a material (rock, lava, basalt,
  crater pool...); each material paints the base, glow and statue textures at once. `paint_flows` paints the
  poured lava: streams running downhill from the crater over the posed body (with finer drips and flecks of cooling
  crust), keeping the face clear. The crater pools and the blast column are fully opaque in it, so they're always
  drawn bright.
- **Animations** (`kit/anims.py`): generated, not hand-keyed. Fists and feet are planted with the IK on every
  frame; shell plates fly on real arcs (gravity, one bounce, slide, crumble); the blast's rocks and the lava bombs
  fly on arcs too. Keyframes are thinned to what GeckoLib's straight-line interpolation needs.
  - Smooth limbs: each frame's IK starts from the last frame's angles and is pulled gently towards them
    (`ik_smooth`), so an arm with spare joints doesn't flip from one way of bending to another; if that pull leaves a
    limb missing its spot by more than `RETRY_MISS`, it's solved again freely. The walk is solved for one lap first and
    then recorded, so its loop has no seam. Targets that the arms can't reach (a body rearing up or slumping away from
    planted fists) make limbs lock straight and then snap bent: the animations move the fists with the body instead
    (off the ground while it roars or rears back, wider while it leans in).
  - The walk goes a little lower than it stands (`walk_crouch`), its fists step a little ahead (`arm_reach`) and
    swing out as they pass the body, easing out and back in (`arm_bow`).
- **Checks** (`kit/checks.py`): boxes whose faces would z-fight are grown by 0.1 texel; the size in blocks is printed.
- **Clipping** (`kit/clipping.py`, run by every build; also `python tools/golems/kit/clipping.py <design module>`):
  every part that grows out of the body (spikes, horns, volcano levels, rim teeth) must have at least 60% of its foot
  on the body (round 3's all have 95% or more), and parts mustn't pass through each other (forearms and fists through
  the torso, legs, head or volcanoes; shoulder spikes through the head or volcanoes; the jaw through the chest), in
  the rest pose and through the idle, walk, erupt and death animations. Anything it finds is printed.

## Matching the game

`kit/geo.py` and `kit/render.py` copy GeckoLib 4's own maths (x flipped, Z-Y-X rotation order, box-UV layout,
the entity turned by 180 - yaw) and Minecraft's entity lighting, so the previews show what the game shows. The
glow and flow layers' softened lighting (`GLOW_NORMAL_LIFT`), the flow reveal (`FLOW_REVEAL`) and the death's fades
(`GLOW_FADE`, `STATUE_FADE`, in `kit/previews.py`, written into `GolemDesigns.java`) are the numbers the mod uses.

## Files

| File | What it does |
|---|---|
| `build.py` | builds everything (see above) |
| `designs/d3e_twinpeak.py` ... `d3h_spined.py` | one design each: volcano and spikes, materials, face, flows, animation style |
| `designs/calderon.py` | round 3's shared parts: body, lava core, shell, head and beard, limbs, spine ridges |
| `designs/common.py` | the shared skeleton, limbs, rest-pose solving, plates, the volcano and spikes |
| `designs/round1/`, `designs/round2/` | earlier rounds' designs (reference only) |
| `kit/geo.py` | bones and boxes, box-UV packing, `.geo.json` writer, GeckoLib-exact forward kinematics |
| `kit/ik.py` | limb solver (puts a fist or foot flat on a target) |
| `kit/anims.py` | the animations and the `.animation.json` writer |
| `kit/paint.py`, `kit/materials.py`, `kit/noise.py` | texture painting (and the lava flows) |
| `kit/render.py`, `kit/previews.py` | the preview renderer and the preview pages |
| `kit/checks.py`, `kit/clipping.py`, `kit/java.py` | z-fighting fix and size check; the clipping checks; the Java list writer |
