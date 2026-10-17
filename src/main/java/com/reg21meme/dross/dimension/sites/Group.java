package com.reg21meme.dross.dimension.sites;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/**
 * A group of a boss biome's patches: the 3 nearest the hub (group 0), the next 3 (group 1), and so on (the last group
 * before {@link BossSitesTuning#MAX_DISTANCE} may have fewer). Each patch has a <b>slot</b>:
 * <ul>
 *   <li>slot 0: the biome's current boss (the Cinder Colossus, the Rat King): one patch picked by the world seed among the
 *       patches where its lair fits ({@link #bossSite});</li>
 *   <li>slots 1 and 2: kept free for the biome's two future bosses (the nearer of the two patches is slot 1). In Blightwood
 *       each may hold a regular sewer ({@link #filler}).</li>
 * </ul>
 * If no patch of the group fits the boss, the roomiest one gets the boss's smallest form ({@link Site#relaxed}); if not
 * even that fits, the group has no boss ({@link #bossSlotPatch} empty) and its patches take slots 1, 2 and 3 (none 0).
 *
 * @param biome    the boss biome
 * @param index    0 = the nearest group
 * @param patches  its patches, nearest the hub first
 * @param slots    each patch's slot (in the order of {@code patches})
 * @param bossSite where the current boss goes (empty: no patch could hold it)
 * @param rolled   for each patch, whether its filler roll came up ({@link BossSitesTuning#REGULAR_SEWER_CHANCE}); always
 *                 false for the boss's patch and in a biome without a filler
 * @param fillers  for each patch, its filler's site (empty: none rolled, none fits there, or the boss is there)
 */
public record Group(ResourceKey<Biome> biome, int index, List<Patch> patches, List<Integer> slots, Optional<Site> bossSite, List<Boolean> rolled,
                    List<Optional<Site>> fillers)
{
    /** The patch with this slot (0: the current boss; 1 and 2: kept for future bosses), if the group has it. */
    public Optional<Patch> slotPatch(int slot)
    {
        for (int i = 0; i < this.patches.size(); i++)
        {
            if (this.slots.get(i) == slot)
            {
                return Optional.of(this.patches.get(i));
            }
        }
        return Optional.empty();
    }

    /** The patch the current boss is in (empty if no patch could hold it). */
    public Optional<Patch> bossSlotPatch()
    {
        return this.bossSite.map(Site::patch);
    }

    /** The slot of a patch of this group, or -1 if it isn't one. */
    public int slotOf(Patch patch)
    {
        for (int i = 0; i < this.patches.size(); i++)
        {
            if (this.patches.get(i).id() == patch.id())
            {
                return this.slots.get(i);
            }
        }
        return -1;
    }

    /** The filler's site in this patch of the group (a regular sewer), if it has one. */
    public Optional<Site> filler(Patch patch)
    {
        for (int i = 0; i < this.patches.size(); i++)
        {
            if (this.patches.get(i).id() == patch.id())
            {
                return this.fillers.get(i);
            }
        }
        return Optional.empty();
    }

    /** Every site of the group (the boss's first, then the fillers'). */
    public List<Site> sites()
    {
        List<Site> all = new ArrayList<>();
        this.bossSite.ifPresent(all::add);
        this.fillers.forEach(f -> f.ifPresent(all::add));
        return all;
    }

    /** The site of {@code use} in this patch of the group, or null. */
    @Nullable
    Site siteFor(SiteUse use, Patch patch)
    {
        for (Site s : sites())
        {
            if (s.use() == use && s.patch().id() == patch.id())
            {
                return s;
            }
        }
        return null;
    }
}
