"""The decorations, each on its own for the decoration showcase (the user, October 20, 2026: "can we just do a
decoration showcase and come up with a bunch of ideas that are just not here ... I'll note the ones I don't like";
"a perimeter of lights around the base, but you give different light designs"). Every one is checked like an igloo:
nothing on a bottom slab, nothing floating, chests that open, nothing that would melt.

Each entry: (id, name, what it's for, draw(f)): draw puts it in the grid round a Frame at the plot's middle, its front
towards the south (where the showcase's walkway is).
"""
from villagekit import props
from villagekit.local import Frame

from . import decor_new as new

D = []


def add(id, name, note, draw, group="camp"):
    D.append((id, name, note, draw, group))


# ---------------------------------------------------------------- the ones the villages have now
add("fire_pit", "Fire pit", "A campfire in a ring of stones with log benches round it.",
    lambda f: props.fire_pit(f, r=1, benches=True))
add("roasting_fire", "Roasting fire", "The courtyard fire with a Roasted Meat Block turning on its spit.",
    lambda f: props.fire_pit(f, r=1, benches=True, spit=True))
add("stew_fire", "Stew fire", "A fire with a cauldron of stew over it.", lambda f: props.stew_fire(f))
add("long_fire", "Long fire", "A long trench fire for smoking meat.", lambda f: props.long_fire(f, length=5))
add("drying_rack", "Drying rack", "Bundles of dried meat hung on a rack.", lambda f: props.drying_rack(f, length=4))
add("meat_rack", "Meat rack", "Meat Blocks hung to dry on a frame.", lambda f: props.meat_rack(f, length=4))
add("smoking_rack", "Smoking rack", "Meat over a smouldering fire.", lambda f: props.smoking_rack(f, length=3))
add("meat_cache", "Meat cache", "Meat stacked on a raised platform, out of the wolves' reach.",
    lambda f: props.meat_cache(f))
add("butcher_block", "Butcher's block", "A stump and a joint of meat being cut.", lambda f: props.butcher_block(f))
add("hide_frame", "Hide frame", "A hide stretched on a frame to dry.", lambda f: props.hide_frame(f, colour="brown"))
add("hide_rack", "Hide rack", "Hides draped over a rail.", lambda f: props.hide_rack(f))
add("hide_line", "Hide line", "A line of hides hung out to dry.", lambda f: props.hide_line(f, length=5))
add("weapon_rack", "Weapon rack", "Spears standing in a rack.", lambda f: props.weapon_rack(f, n=3))
add("spear_stand", "Spear stand", "Spears leaning together.", lambda f: props.spear_stand(f))
add("archery_range", "Archery range", "Targets to practise on.", lambda f: props.archery_range(f, targets=3, gap=3,
                                                                                           distance=6))
add("woodpile", "Woodpile", "Logs stacked under a little roof.", lambda f: props.woodpile(f, length=3))
add("sled", "Sled", "A sled loaded with the hunt's take: a plank deck (no slabs), trapdoor runners, a stair prow.",
    lambda f: props.sled(f))
add("dog_sleds", "Sleds", "Two sleds side by side.", lambda f: props.dog_sleds(f))
add("kayak_rack", "Kayak rack", "A skin boat up on its rack for the winter.", lambda f: props.kayak_rack(f))
add("crate_stack", "Crates", "Barrels and bundles stacked up.", lambda f: props.crate_stack(f))
add("snow_block_stack", "Snow blocks", "Cut snow blocks stacked for building.", lambda f: props.snow_block_stack(f))
add("ice_block_stack", "Ice blocks", "Cut blocks of ice stacked up.", lambda f: props.ice_block_stack(f))
add("lean_to", "Lean-to", "A shelter of hides on poles, icicles at its eave.", lambda f: props.lean_to(f))
add("windbreak", "Windbreak", "A wall of snow blocks against the wind.", lambda f: props.windbreak(f, length=5))
add("fishing_hole", "Fishing hole", "A hole cut in the ice with a stool and a bucket.", lambda f: props.fishing_hole(f))
add("anvil_corner", "Anvil", "A smith's anvil on a stump.", lambda f: props.anvil_corner(f))
add("inuksuk", "Inuksuk", "A stone figure marking the way.", lambda f: props.inuksuk(f))
add("totem", "Totem", "A carved post of the hunt.", lambda f: props.totem(f, height=6))
add("banner_pole", "Banner pole", "The Huntsman's colours on a pole.", lambda f: props.banner_pole(f))
add("skull_post", "Skull post", "A skull on a post, facing out.", lambda f: props.skull_post(f, height=2))
add("antler_post", "Antler post", "Antlers on a post.", lambda f: props.antler_post(f, height=3))
add("rimehorn_skull", "Rimehorn skull", "A Rimehorn's skull and horn on a cairn.", lambda f: props.rimehorn_skull(f))
add("bone_pile", "Bone pile", "Bones of the hunt.", lambda f: props.bone_pile(f, big=True))
add("skull_pile", "Skull pile", "A heap of skulls.", lambda f: props.skull_pile(f))
add("bone_arch", "Bone arch", "An arch of whale bones over a path.", lambda f: props.bone_arch(f, width=5, height=6))
add("horn_arch", "Horn arch", "Two great ice horns crossed over a path.", lambda f: props.horn_arch(f, width=5, height=7))
add("antler_gate", "Antler gate", "A gateway crowned with antlers.", lambda f: props.antler_gate(f))
add("ice_sculpture", "Ice sculpture", "A Rimehorn carved from ice.", lambda f: props.ice_sculpture(f))
add("frostbloom_patch", "Frostblooms", "A patch of Frostblooms.", lambda f: props.frostbloom_patch(f))

# ---------------------------------------------------------------- lights for the ring round a village
add("lantern_post", "Lantern post", "A lantern hung from a post's arm.", lambda f: props.lantern_post(f), "light")
add("ice_lantern", "Ice lantern", "A lantern in a little house of ice.", lambda f: props.ice_lantern(f), "light")
for _id, _name, _note, _draw in new.LIGHTS:
    add(_id, _name, _note, _draw, "light")

# ---------------------------------------------------------------- new ideas
for _id, _name, _note, _draw in new.IDEAS:
    add(_id, _name, _note, _draw, "new")

DECORATIONS = D
