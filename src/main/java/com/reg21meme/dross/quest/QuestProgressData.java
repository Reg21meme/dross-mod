package com.reg21meme.dross.quest;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The quest progress of every player, saved with the world (in the Overworld's
 * {@code data/dross_quest.dat}), so it's per player and per world, and survives death and rejoining.
 *
 * <p>Other areas never use this class directly: they go through {@link QuestProgress},
 * which is the only way to change progress. That's why everything that changes data here is package-private.</p>
 */
public class QuestProgressData extends SavedData
{
    private static final String NAME = "dross_quest";

    private final Map<UUID, PlayerQuest> players = new HashMap<>();

    /** One player's progress. */
    static final class PlayerQuest
    {
        /** A Weathered Letter has been in their inventory. */
        boolean letterFound;
        /** The trader has said his opening line to them. */
        boolean introSpoken;
        /** Tributes handed in so far. */
        final Set<QuestRequirement> tributes = EnumSet.noneOf(QuestRequirement.class);
        /** All tributes are in, so they earned the Dross Compass ("Proven Worthy"). */
        boolean compassEarned;
        /** Key materials handed in towards the NEXT key (cleared each time a key is forged). */
        final Set<QuestRequirement> keyMaterials = EnumSet.noneOf(QuestRequirement.class);
        /** They were given a Rift Key at least once: the quest is complete. */
        boolean keyGiven;
        /** How many Rift Keys were forged for them (replacements included). */
        int keysForged;
        /** They already got the Dross Guide Book on their first arrival. */
        boolean guideBookGiven;
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
            quest.introSpoken = entry.getBoolean("IntroSpoken");
            readSet(entry.getList("Tributes", Tag.TAG_STRING), quest.tributes);
            quest.compassEarned = entry.getBoolean("CompassEarned");
            readSet(entry.getList("KeyMaterials", Tag.TAG_STRING), quest.keyMaterials);
            quest.keyGiven = entry.getBoolean("KeyGiven");
            quest.keysForged = entry.getInt("KeysForged");
            quest.guideBookGiven = entry.getBoolean("GuideBookGiven");
            data.players.put(entry.getUUID("Id"), quest);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, PlayerQuest> e : players.entrySet())
        {
            PlayerQuest quest = e.getValue();
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", e.getKey());
            entry.putBoolean("LetterFound", quest.letterFound);
            entry.putBoolean("IntroSpoken", quest.introSpoken);
            entry.put("Tributes", writeSet(quest.tributes));
            entry.putBoolean("CompassEarned", quest.compassEarned);
            entry.put("KeyMaterials", writeSet(quest.keyMaterials));
            entry.putBoolean("KeyGiven", quest.keyGiven);
            entry.putInt("KeysForged", quest.keysForged);
            entry.putBoolean("GuideBookGiven", quest.guideBookGiven);
            list.add(entry);
        }
        tag.put("Players", list);
        return tag;
    }

    private static void readSet(ListTag list, Set<QuestRequirement> into)
    {
        for (int i = 0; i < list.size(); i++)
        {
            QuestRequirement requirement = QuestRequirement.fromSaveName(list.getString(i));
            if (requirement != null)
            {
                into.add(requirement);
            }
        }
    }

    private static ListTag writeSet(Set<QuestRequirement> set)
    {
        ListTag list = new ListTag();
        for (QuestRequirement requirement : set)
        {
            list.add(StringTag.valueOf(requirement.saveName()));
        }
        return list;
    }
}
