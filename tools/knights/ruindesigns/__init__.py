"""The knight ruins' designs: one module per biome area, tools/knights/ruindesigns/<element>/<area>.py (see the kit,
tools/knights/ruinkit). AREAS gives each element's areas in the order they're shown (and numbered)."""

ELEMENTS = ("ice", "poison", "fire", "thunder", "earth")

AREAS = {
    "ice": ["snowy_plains", "taiga", "frozen_shore"],
    "poison": ["swamp", "mushroom_fields", "dark_forest", "jungle", "forest", "warped_forest"],
    "fire": ["desert", "savanna", "badlands", "nether_wastes", "crimson_forest", "soul_sand_valley"],
    "thunder": ["warm_beach", "cold_beach", "stony_shore", "riverbank", "mountains"],
    "earth": ["plains", "meadow", "windswept_hills", "peaks", "caverns", "basalt_deltas"],
}
