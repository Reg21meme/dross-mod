"""The hermit crab caves, one module each (the user's idea, October 31, 2026: "a new structure called a hermit crab cave
... nothing huge, maybe 10 blocks or so deep"; all of them in a showcase, 1-2 caves in every Cinder Wastes area).

Each module has ID, NAME, LORE and build() -> cavekit.Cave. hermit_caves.py builds them in this order (their numbers).
"""
from cavedesigns import (c1_sinkhole_den, c2_tide_pools, c3_shell_midden, c4_lava_tube, c5_basalt_geode,  # noqa: F401
                         c6_bone_reef, c7_steam_burrows)

DESIGNS = (c1_sinkhole_den, c2_tide_pools, c3_shell_midden, c4_lava_tube, c5_basalt_geode, c6_bone_reef,
           c7_steam_burrows)
