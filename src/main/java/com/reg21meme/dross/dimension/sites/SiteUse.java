package com.reg21meme.dross.dimension.sites;

import java.util.Objects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

/**
 * Something the planner places in the patches of a boss biome: a boss's lair (the biome's current boss gets one patch of
 * every group) or a filler (a regular sewer in a Blightwood patch kept for a future boss). Its {@link SiteFinder} says
 * where in a patch it fits. The uses are constants of {@link BossSites}.
 */
public final class SiteUse
{
    private final ResourceLocation id;
    private final ResourceKey<Biome> biome;
    private volatile SiteFinder finder;

    SiteUse(ResourceLocation id, ResourceKey<Biome> biome, SiteFinder finder)
    {
        this(id, biome, finder, false);
    }

    SiteUse(ResourceLocation id, ResourceKey<Biome> biome, SiteFinder finder, boolean perPatch)
    {
        this.id = id;
        this.biome = biome;
        this.finder = finder;
        this.perPatch = perPatch;
    }

    private final boolean perPatch;

    /**
     * True for a use every patch of its biome gets (where its finder finds a spot), whatever its group's slots: Rimefall's
     * areas (a lake and herds in each). False for a boss or a filler, which the groups place.
     */
    public boolean perPatch()
    {
        return this.perPatch;
    }

    /** Its name ({@code dross:rat_king_sewer}...), as the structure sets' placement JSON names it. */
    public ResourceLocation id()
    {
        return this.id;
    }

    /** The boss biome whose patches it goes in. */
    public ResourceKey<Biome> biome()
    {
        return this.biome;
    }

    /** Its fit test. */
    public SiteFinder finder()
    {
        return this.finder;
    }

    void setFinder(SiteFinder finder)
    {
        this.finder = Objects.requireNonNull(finder);
    }

    @Override
    public String toString()
    {
        return this.id.toString();
    }
}
