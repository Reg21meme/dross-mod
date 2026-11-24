package com.reg21meme.dross.quest;

import com.reg21meme.dross.quest.oracle.OracleRecord;
import com.reg21meme.dross.quest.trader.TraderRecord;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The quest progress of every player, saved with the world (in the Overworld's {@code data/dross_quest.dat}), so it's
 * per player and per world, and survives death and rejoining: the Weathered Letter and Guide Book flags, the Dross
 * Trader's quest ({@link TraderRecord}) and the Blind Oracle's ({@link OracleRecord}). And one flag for the whole world:
 * the cathedral's portal has been lit ({@link #isCathedralLit}).
 *
 * <p>Other areas never change this class directly: they go through {@link QuestProgress}, {@code quest.trader.TraderProgress}
 * and {@code quest.oracle.OracleProgress}, the only ways to change progress.</p>
 */
public class QuestProgressData extends SavedData
{
    private static final String NAME = "dross_quest";

    private final Map<UUID, PlayerQuest> players = new HashMap<>();
    /** The Fallen Cathedral's portal has been lit (by anyone): it stays open for everyone. */
    private boolean cathedralLit;

    /** One player's progress. */
    static final class PlayerQuest
    {
        /** A Weathered Letter has been in their inventory. */
        boolean letterFound;
        /** They already got the Dross Guide Book on their first arrival. */
        boolean guideBookGiven;
        /** Their Dross Trader quest (changed only by {@code quest.trader.TraderProgress}). */
        TraderRecord trader = new TraderRecord();
        /** Their Oracle quest line (changed only by {@code quest.oracle.OracleProgress}). */
        OracleRecord oracle = new OracleRecord();
    }

    public static QuestProgressData get(ServerLevel overworld)
    {
        return overworld.getDataStorage().computeIfAbsent(QuestProgressData::load, QuestProgressData::new, NAME);
    }

    /** This player's progress (an empty one if they haven't started). Call {@link #setDirty()} after changing it. */
    PlayerQuest get(UUID player)
    {
        return players.computeIfAbsent(player, id -> new PlayerQuest());
    }

    /**
     * This player's Dross Trader quest progress (a fresh one if they haven't started). Read freely; only
     * {@code quest.trader.TraderProgress} changes it, and calls {@link #setDirty()} after.
     */
    public TraderRecord trader(UUID player)
    {
        return get(player).trader;
    }

    /**
     * This player's Oracle quest progress (a fresh one if they haven't started). Read freely; only
     * {@code quest.oracle.OracleProgress} changes it, and calls {@link #setDirty()} after.
     */
    public OracleRecord oracle(UUID player)
    {
        return get(player).oracle;
    }

    /** True once the Fallen Cathedral's portal has been lit (by anyone in this world). */
    public boolean isCathedralLit()
    {
        return cathedralLit;
    }

    /** The cathedral's portal is lit (only {@code quest.trader.TraderProgress} calls this). */
    public void markCathedralLit()
    {
        if (!cathedralLit)
        {
            cathedralLit = true;
            setDirty();
        }
    }

    /**
     * Sets the "cathedral's portal is lit" flag either way. For the tests only (in the game it never goes back): a test
     * that lights the cathedral's frame puts it back as it was, so the trader's tests still see an unlit cathedral.
     */
    public void setCathedralLitForTests(boolean lit)
    {
        if (cathedralLit != lit)
        {
            cathedralLit = lit;
            setDirty();
        }
    }

    /** Forgets everything about this player. */
    void remove(UUID player)
    {
        if (players.remove(player) != null)
        {
            setDirty();
        }
    }

    public static QuestProgressData load(CompoundTag tag)
    {
        QuestProgressData data = new QuestProgressData();
        data.cathedralLit = tag.getBoolean("CathedralLit");
        ListTag list = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("Id"))
            {
                continue;
            }
            PlayerQuest quest = new PlayerQuest();
            quest.letterFound = entry.getBoolean("LetterFound");
            quest.guideBookGiven = entry.getBoolean("GuideBookGiven");
            quest.trader = TraderRecord.read(entry);
            quest.oracle = OracleRecord.read(entry);
            data.players.put(entry.getUUID("Id"), quest);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        tag.putBoolean("CathedralLit", cathedralLit);
        ListTag list = new ListTag();
        for (Map.Entry<UUID, PlayerQuest> e : players.entrySet())
        {
            PlayerQuest quest = e.getValue();
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", e.getKey());
            entry.putBoolean("LetterFound", quest.letterFound);
            entry.putBoolean("GuideBookGiven", quest.guideBookGiven);
            quest.trader.write(entry);
            quest.oracle.write(entry);
            list.add(entry);
        }
        tag.put("Players", list);
        return tag;
    }
}
