"""No. E1 Emberdactyl (cinder crest): the Fire Necromancer's volcanic pterosaur, adult and young reborn."""
from firekit import ptero

NUMBER = 1
ID = "embercrest"
NAME = "Emberdactyl"
BLURB = ("The Emberdactyl, a volcanic pterosaur nesting on top of its own volcano, guarding its egg; it comes for you "
         "within about 60 blocks of the volcano, fights mostly in the air (dives, fly-bys, wing gusts that spin up fire "
         "tornadoes) and lands for melee. Beat it (300 health) and a younger, faster, weaker one (150) bursts out of "
         "the egg. This look: the classic: a long crest swept back off its skull, a charcoal hide cracked with magma, dark red wings veined with glowing lines.")
VARIANTS = (
    dict(letter="A", mood="cinder crest", young=False, note="A: the adult, about 19 blocks across its wings."),
    dict(letter="B", mood="young reborn", young=True,
         note="B: its young reborn form: smaller, sleeker, its wings swept back for speed, brighter and hotter."),
)
FREE = ()
DESIGN = dict(crest="swept", tail="short")
THEME = "cinder"


def build(s, v):
    ptero.build(s, DESIGN, young=v["young"])


def materials(v):
    return ptero.materials(THEME, young=v["young"])
