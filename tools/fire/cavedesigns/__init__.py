"""The hermit crab caves, one module each. Round 2 (the user's look at round 1, November 2, 2026: "I like number 7 ...
but I expected it to be like, there's a hill, and he builds inside the hill ... We can have some underground ones ...
some loot and stuff behind, some pots, pottery ... only 1 per cave"): every design in No. 7's spirit, hillside dens
built into a hill and shallow underground dens with a loot room. Round 1's seven are archived in round1/.

Each module has ID, NAME, KIND (cavekit.HILLSIDE or UNDERGROUND), LORE and build() -> cavekit.Cave. hermit_caves.py
builds them in this order (their numbers). Their shared parts are burrows.py.
"""
from cavedesigns import (h1_hillside_burrows, h2_long_gallery, h3_claw_burrows, u1_ash_mound,  # noqa: F401
                         u2_twin_hump)

DESIGNS = (h1_hillside_burrows, h2_long_gallery, h3_claw_burrows, u1_ash_mound, u2_twin_hump)
