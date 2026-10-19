"""The hunter villages' building kit (tools/huntsman/villages.py builds the designs with it).

It reuses the shrine generator's voxel grid, block-state checks, template writer and renderer (tools/shrines) and adds:
  - Rimefall's own blocks (Hoarfrost, Frostbitten Log, Icicle, Frostbloom) to them (dross_blocks),
  - local coordinates turned to face a door (local.Frame),
  - igloos: snow domes and tunnels built as solids and hollowed out, so joined domes share one wall (igloo),
  - the camp's props: fire pits, racks, sleds, inuksuks, arches, palisades... (props),
  - the checks: no clear ice or snow layer bright enough to melt, beds and doors whole, nothing floating (checks),
  - a template writer that also stores block entity data (banner patterns) (writer).
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.normpath(os.path.join(HERE, "..", ".."))
SHRINES = os.path.join(TOOLS, "shrines")
if SHRINES not in sys.path:
    sys.path.insert(0, SHRINES)

from . import dross_blocks  # noqa: E402,F401  (teaches the shrine kit Rimefall's blocks)
