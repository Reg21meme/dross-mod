package com.reg21meme.dross.dimension.block;

import com.reg21meme.dross.Dross;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.PlantType;

/**
 * Which ground each of the Dross's biome plants grows on. Every plant's ground is a block tag in
 * {@code data/dross/tags/blocks/<plant>_grows_on.json} (written by {@code tools/terrain/block_data.py}), so the
 * terrain stages (or a modpack) can let a plant grow on more blocks without touching Java.
 *
 * <p>Why the plants also report {@link #PLANT_TYPE}: once a plant is in the world, Forge asks the block under it
 * ({@code Block.canSustainPlant}) whether it may stay. That check first asks the plant's own ground rule, and when the
 * answer is no it falls back on the plant's Forge "plant type": the default type, PLAINS, accepts any
 * {@code #minecraft:dirt}. So without their own type, Emberweed or Soulwick would refuse to be placed on dirt but stay
 * on it if the ground under them was swapped for dirt later. Their own type matches no vanilla ground, so only the tag
 * decides.
 */
public final class PlantGround
{
    /** The Forge plant type every tag-ruled Dross plant reports (no vanilla ground accepts it by itself). */
    public static final PlantType PLANT_TYPE = PlantType.get("dross_plant");

    // Cinder Wastes: ash, basalt, blackstone, magma and Duskstone (full blocks only)
    public static final TagKey<Block> EMBERWEED = tag("emberweed_grows_on");
    // Blightwood: #minecraft:dirt, #minecraft:mushroom_grow_block, mycelium and logs
    public static final TagKey<Block> GLOOMCAP = tag("gloomcap_grows_on");
    // Blightwood: the same ground as the Gloomcap (the tag includes #dross:gloomcap_grows_on)
    public static final TagKey<Block> SPOREPOD = tag("sporepod_grows_on");
    // Rimefall: Hoarfrost, snow blocks and #minecraft:dirt
    public static final TagKey<Block> FROSTBLOOM = tag("frostbloom_grows_on");
    // Scar Roots (from the old Nether Scar): nylium, netherrack, Scarred Netherrack and soul soil
    public static final TagKey<Block> SCAR_ROOTS = tag("scar_roots_grow_on");
    // Soulwick (from the old Nether Scar): soul soil and soul sand
    public static final TagKey<Block> SOULWICK = tag("soulwick_grows_on");
    // The Gravecrags: Cragstone, gravel, Duskstone, snow blocks and #minecraft:dirt
    public static final TagKey<Block> CRAG_THISTLE = tag("crag_thistle_grows_on");

    private PlantGround() {}

    private static TagKey<Block> tag(String name)
    {
        return TagKey.create(Registries.BLOCK, new ResourceLocation(Dross.MODID, name));
    }
}
