"""The hermit crab caves, one module each. Round 2 (the user's look at round 1, November 2, 2026: "I like number 7 ...
but I expected it to be like, there's a hill, and he builds inside the hill ... We can have some underground ones ...
some loot and stuff behind, some pots, pottery ... only 1 per cave"): every design in No. 7's spirit, hillside dens
built into a hill and shallow underground dens with a loot room. Round 1's seven are archived in round1/. The user's
pick of round 2 (November 3: "I like 4, 5, and for 2 make it a little bigger and less loot and stuff on the right"): the
Long Gallery (made bigger), the Ash Mound Den and the Twin Hump Den, now Nos. 1-3; the other two are archived in round2/.

Each module has ID, NAME, KIND (cavekit.HILLSIDE or UNDERGROUND), LORE and build() -> cavekit.Cave. hermit_caves.py
builds them in this order (their numbers). Their shared parts are burrows.py.
"""
from cavedesigns import h2_long_gallery, u1_ash_mound, u2_twin_hump  # noqa: F401

DESIGNS = (h2_long_gallery, u1_ash_mound, u2_twin_hump)
