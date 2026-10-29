"""No. E6 Emberdactyl (ash wyrm): the Fire Necromancer's volcanic pterosaur, adult and young reborn."""
from firekit import ptero

NUMBER = 6
ID = "emberash"
NAME = "Emberdactyl"
BLURB = ("The Emberdactyl, a volcanic pterosaur nesting on top of its own volcano, guarding its egg; it comes for you "
         "within about 60 blocks of the volcano, fights mostly in the air (dives, fly-bys, wing gusts that spin up fire "
         "tornadoes) and lands for melee. Beat it (300 health) and a younger, faster, weaker one (150) bursts out of "
         "the egg. This look: ash grey and sooty, a crest on its snout and another on its skull, a glowing throat pouch, smoke-dark wings with burning edges.")
VARIANTS = (
    dict(letter="A", mood="ash wyrm", young=False, note="A: the adult, about 19 blocks across its wings."),
    dict(letter="B", mood="young reborn", young=True,
         note="B: its young reborn form: smaller, sleeker, its wings swept back for speed, brighter and hotter."),
)
FREE = ()
DESIGN = dict(crest="double", tail="long")
THEME = "ash"


def build(s, v):
    ptero.build(s, DESIGN, young=v["young"])


def materials(v):
    return ptero.materials(THEME, young=v["young"])
