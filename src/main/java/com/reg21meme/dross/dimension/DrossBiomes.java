package com.reg21meme.dross.dimension;

import com.mojang.datafixers.util.Pair;
import com.reg21meme.dross.Dross;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;

/**
 * The Dross's six biomes (data/dross/worldgen/biome/, written by {@code tools/terrain/worldgen.py}), and finding the
 * nearest place of each one (for {@code /dross goto biome <name>}, which world-builder wires up).
 * <ul>
 *   <li>{@link #SILVERWOOD_FOREST} Silverwood Forest: the main biome, dense and dark; it always surrounds the hub.</li>
 *   <li>{@link #CINDER_WASTES} Cinder Wastes: volcanic; ash, basalt, magma and mesas.</li>
 *   <li>{@link #BLIGHTWOOD} Blightwood: rotten and fungal; low, lumpy ground.</li>
 *   <li>{@link #RIMEFALL} Rimefall: frozen; hoarfrost, snow and packed ice; gentle.</li>
 *   <li>{@link #NETHER_SCAR} Nether Scar: smaller patches where the Nether bleeds through.</li>
 *   <li>{@link #GRAVECRAGS} The Gravecrags: the mountains, peaks about y 230-265; never within about 650 blocks of the hub.</li>
 * </ul>
 */
public final class DrossBiomes
{
    public static final ResourceKey<Biome> SILVERWOOD_FOREST = key("silverwood_forest");
    public static final ResourceKey<Biome> CINDER_WASTES = key("cinder_wastes");
    public static final ResourceKey<Biome> BLIGHTWOOD = key("blightwood");
    public static final ResourceKey<Biome> RIMEFALL = key("rimefall");
    public static final ResourceKey<Biome> NETHER_SCAR = key("nether_scar");
    public static final ResourceKey<Biome> GRAVECRAGS = key("gravecrags");

    /**
     * The sewers under Blightwood (biome {@code dross:sewers}, "The Sewers"): not a surface biome, but the Blightwood
     * underground from {@code SEWER_BIOME_DEPTH} blocks under the ground down (tools/terrain/worldgen.py, SEWERS). It's solid
     * rock except for the sewer networks, so it only shows in them: their murky green water and fog, and their spawns
     * (rats only). Not in {@link #ALL} ({@code /dross goto biome} is for the six surface biomes): the surface map is as before.
     */
    public static final ResourceKey<Biome> SEWERS = key("sewers");

    /** All six, Silverwood Forest first (the order the command lists them in). */
    public static final List<ResourceKey<Biome>> ALL = List.of(SILVERWOOD_FOREST, CINDER_WASTES, BLIGHTWOOD, RIMEFALL, NETHER_SCAR,
            GRAVECRAGS);

    // Finding the nearest place of a biome (Minecraft's own search, like /locate biome)
    /** How far the search looks (blocks, measured along X and Z from the start). Every biome is within about 1,500 of the hub. */
    public static final int SEARCH_RADIUS = 6400;
    /** The search samples every this many blocks along X and Z (a biome region is hundreds of blocks across). */
    public static final int SEARCH_HORIZONTAL_STEP = 32;
    /**
     * ...and every this many blocks up and down (the Dross's surface biomes are the same at every height, so few are needed;
     * only the sewers lie under Blightwood).
     */
    public static final int SEARCH_VERTICAL_STEP = 64;
    /**
     * The search stops at the first place it finds, at the region's near edge; the place given is moved up to this many
     * blocks further in (along the line from the start), as far as the biome still reaches, so a teleport lands inside it.
     */
    public static final int STEP_INSIDE = 32;
    /** ...checked every this many blocks. */
    private static final int STEP_INSIDE_STEP = 8;

    private DrossBiomes() {}

    /** The name a command uses for a biome: its id's path ({@code silverwood_forest}, {@code gravecrags}...). */
    public static String name(ResourceKey<Biome> biome)
    {
        return biome.location().getPath();
    }

    /** The biome with this command name ({@code silverwood_forest}, {@code cinder_wastes}, {@code blightwood}, {@code rimefall},
     * {@code nether_scar}, {@code gravecrags}; case doesn't matter), or empty. */
    public static Optional<ResourceKey<Biome>> byName(String name)
    {
        String wanted = name.toLowerCase(Locale.ROOT);
        for (ResourceKey<Biome> biome : ALL)
        {
            if (name(biome).equals(wanted))
            {
                return Optional.of(biome);
            }
        }
        return Optional.empty();
    }

    /** All the command names, in {@link #ALL}'s order (for command suggestions). */
    public static List<String> names()
    {
        return ALL.stream().map(DrossBiomes::name).toList();
    }

    /**
     * The nearest place of {@code biome} in the Dross, searching outwards from {@code from} (Minecraft's own biome search,
     * like {@code /locate biome}: about {@value #SEARCH_RADIUS} blocks out, every {@value #SEARCH_HORIZONTAL_STEP} blocks).
     * Only the biome noise is read: no chunks are loaded or generated. The search runs on the calling thread and is
     * quick for every Dross biome (they're all within about 1,500 blocks of the hub), up to about a second for one
     * that isn't there at all.
     *
     * @param dross the Dross level
     * @param from  where to start looking (its Y is where the search starts up and down; the biomes don't change with height)
     * @param biome one of {@link #ALL} (any biome key works; one the Dross never places gives empty)
     * @return the place found, up to {@value #STEP_INSIDE} blocks inside the region: its X and Z are in that biome; its Y
     *         is just where the biome was sampled, not the ground (find the surface there before teleporting anyone).
     *         Empty if none is within the search radius.
     */
    public static Optional<BlockPos> findNearest(ServerLevel dross, BlockPos from, ResourceKey<Biome> biome)
    {
        @Nullable Pair<BlockPos, Holder<Biome>> found = dross.findClosestBiome3d(holder -> holder.is(biome), from, SEARCH_RADIUS,
                SEARCH_HORIZONTAL_STEP, SEARCH_VERTICAL_STEP);
        if (found == null)
        {
            return Optional.empty();
        }
        BlockPos edge = found.getFirst();
        double dx = edge.getX() - from.getX();
        double dz = edge.getZ() - from.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0D)
        {
            return Optional.of(edge);
        }
        BlockPos inside = edge;
        for (int step = STEP_INSIDE_STEP; step <= STEP_INSIDE; step += STEP_INSIDE_STEP)
        {
            BlockPos next = BlockPos.containing(edge.getX() + dx / length * step, edge.getY(), edge.getZ() + dz / length * step);
            if (!isBiome(dross, next, biome))
            {
                break;
            }
            inside = next;
        }
        return Optional.of(inside);
    }

    /** True if the biome noise puts {@code biome} at this column (what world generation uses, before Minecraft's slight blurring of borders). */
    public static boolean isBiome(ServerLevel dross, BlockPos pos, ResourceKey<Biome> biome)
    {
        Holder<Biome> holder = dross.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(pos.getX()),
                QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ()), dross.getChunkSource().randomState().sampler());
        return holder.is(biome);
    }

    private static ResourceKey<Biome> key(String name)
    {
        return ResourceKey.create(Registries.BIOME, new ResourceLocation(Dross.MODID, name));
    }
}
