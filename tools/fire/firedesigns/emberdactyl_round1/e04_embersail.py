"""No. E4 Emberdactyl (ember sail): the Fire Necromancer's volcanic pterosaur, adult and young reborn."""
from firekit import ptero

NUMBER = 4
ID = "embersail"
NAME = "Emberdactyl"
BLURB = ("The Emberdactyl, a volcanic pterosaur nesting on top of its own volcano, guarding its egg; it comes for you "
         "within about 60 blocks of the volcano, fights mostly in the air (dives, fly-bys, wing gusts that spin up fire "
         "tornadoes) and lands for melee. Beat it (300 health) and a younger, faster, weaker one (150) bursts out of "
         "the egg. This look: a tall sail of glowing membrane on a bony frame over its head, and a long tail ending in a burning vane.")
VARIANTS = (
    dict(letter="A", mood="ember sail", young=False, note="A: the adult, about 19 blocks across its wings."),
    dict(letter="B", mood="young reborn", young=True,
         note="B: its young reborn form: smaller, sleeker, its wings swept back for speed, brighter and hotter."),
)
FREE = ()
DESIGN = dict(crest="sail", tail="long", tail_flame=True)
THEME = "sail"


def build(s, v):
    ptero.build(s, DESIGN, young=v["young"])


def materials(v):
    return ptero.materials(THEME, young=v["young"])
