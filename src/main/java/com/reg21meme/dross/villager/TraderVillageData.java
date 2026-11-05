package com.reg21meme.dross.villager;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

/**
 * The rule that a village gives the Dross trader only once (the user's request of November 4, 2026: "once a trader spawns
 * in a random village, it is impossible for him to spawn back in again in that same village"), and that a world has only
 * one trader. Saved as data/dross_trader_village.dat in the Overworld's save folder.
 * <p>
 * Every village a chapel's trader has come to is remembered for good ({@link #villages}), and so is the world's trader
 * ({@link #trader}). On a chapel trader's first tick ({@link DrossTrader}) he's {@link #admit}ted: the world's own trader,
 * coming back with his land, always is; the first one ever (no trader yet), if his village never had one; any other (his
 * village already had its trader, or the world has one) is removed at once. World generation makes a chunk only once, so
 * normally nothing is ever turned away: this stops a second trader if a chapel's land is ever made again (a chunk
 * regenerated) or a mod change ever moves the chapel. If the world's trader dies (only {@code /kill} can do that), the
 * world has none until world generation makes another chapel, and never again at a village that already had him.
 * Spawn-egg and {@code /summon} traders aren't counted.
 */
public class TraderVillageData extends SavedData
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NAME = "dross_trader_village";

    /** The world's trader, or null while it has none. */
    @Nullable
    private UUID trader;
    /** Every village (its start chunk) a chapel trader has come to, in order. */
    private final LongLinkedOpenHashSet villages = new LongLinkedOpenHashSet();

    /** Must be called on the server thread with the Overworld. */
    public static TraderVillageData get(ServerLevel overworld)
    {
        return overworld.getDataStorage().computeIfAbsent(TraderVillageData::load, TraderVillageData::new, NAME);
    }

    private static TraderVillageData load(CompoundTag tag)
    {
        TraderVillageData data = new TraderVillageData();
        if (tag.hasUUID("Trader"))
        {
            data.trader = tag.getUUID("Trader");
        }
        for (long village : tag.getLongArray("Villages"))
        {
            data.villages.add(village);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        if (this.trader != null)
        {
            tag.putUUID("Trader", this.trader);
        }
        tag.put("Villages", new LongArrayTag(this.villages.toLongArray()));
        return tag;
    }

    /**
     * True if the chapel trader {@code id} from {@code village} may stay: he's the world's trader, or the world has none and
     * his village never had one (then he is, from now on). False: he's turned away.
     */
    boolean admit(UUID id, long village)
    {
        if (this.trader != null)
        {
            return this.trader.equals(id);
        }
        if (this.villages.contains(village))
        {
            return false;
        }
        this.trader = id;
        this.villages.add(village);
        this.setDirty();
        return true;
    }

    /** The world's trader has died: the world has none now (his village stays used). */
    void forget(UUID id)
    {
        if (id.equals(this.trader))
        {
            this.trader = null;
            this.setDirty();
            LOGGER.warn("[Dross] The world's Dross trader {} has died: it has no trader now", id);
        }
    }

    /** The world's trader, or null. */
    @Nullable
    public UUID trader()
    {
        return this.trader;
    }

    /** True if a chapel trader has ever come to this village. */
    public boolean hadTrader(long village)
    {
        return this.villages.contains(village);
    }

    /** How many villages have had the trader. */
    public int villageCount()
    {
        return this.villages.size();
    }
}
