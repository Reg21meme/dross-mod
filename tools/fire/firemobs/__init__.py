"""The fire designs that move (tools/fire/mobs.py builds them): one module per mob."""

MOBS = ("emberdactyl", "emberyoung", "pallbearer", "hermit")

# Which gallery designs wear which mob's models in the game (FireDesigns' `mob`, written by build.py).
ANIMATED = {"E1A": "emberdactyl", "E2A": "emberyoung", "M1A": "pallbearer"}
ANIMATED.update({f"E1{c}": "emberdactyl" for c in "BCD"})    # (the adult with less lava on its body, October 31)
ANIMATED.update({f"M2{c}": "hermit" for c in "ABCDEFIJ"})     # (G and H were dropped, October 27)
