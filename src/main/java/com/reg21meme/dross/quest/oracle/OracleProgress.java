package com.reg21meme.dross.quest.oracle;

import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.quest.DrossAdvancements;
import com.reg21meme.dross.quest.QuestProgressData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import com.reg21meme.dross.villager.Survivor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Each player's Oracle quest progress: reading it, and the only way to change it (QUEST_PLAN.md). Every change is
 * saved ({@link QuestProgressData}, per player and per world) and sent to the player's game ({@link #sync}).
 *
 * <p>How a quest goes: AVAILABLE (his offer lines, a right-click each, {@link OracleDialogue}) → ACTIVE (kills count,
 * a boss's advancement finishes it) → DONE (his thanks) → the next quest, AVAILABLE. A TALK quest is finished by its last
 * line, the FINAL one too (then FINISHED). A boss already beaten when its quest goes ACTIVE is DONE at once.</p>
 */
public final class OracleProgress
{
    private OracleProgress() {}

    // ------------------------------------------------------------------ Reading

    /** The quest they're on (the last one once FINISHED). */
    public static OracleQuest current(ServerPlayer player)
    {
        return record(player).quest();
    }

    public static OracleState state(ServerPlayer player)
    {
        return record(player).state();
    }

    /** Kills counted for the current KILL quest. */
    public static int kills(ServerPlayer player)
    {
        return record(player).kills();
    }

    /** The marker over the Oracle for this player. */
    public static OracleMarker marker(ServerPlayer player)
    {
        return OracleMarker.of(state(player));
    }

    /**
     * True if they've reached this quest: they're past it, or on it and it's no longer AVAILABLE (they've heard his offer).
     * Creative and spectator players don't count as anything here: {@link OracleRules} exempts them.
     */
    public static boolean hasReached(ServerPlayer player, OracleQuest quest)
    {
        OracleRecord record = record(player);
        return record.index() > quest.index() || (record.index() == quest.index() && record.state() != OracleState.AVAILABLE);
    }

    /** True if they've handed this quest in: they're on a later one (or FINISHED past it). */
    public static boolean hasHandedIn(ServerPlayer player, OracleQuest quest)
    {
        OracleRecord record = record(player);
        return record.index() > quest.index() || (record.index() == quest.index() && record.state() == OracleState.FINISHED);
    }

    /** True if they're on this quest (in any state) or past it. */
    public static boolean isAtOrPast(ServerPlayer player, OracleQuest quest)
    {
        return record(player).index() >= quest.index();
    }

    /** True once every quest is done ("Defeat the Dross"). */
    public static boolean isFinished(ServerPlayer player)
    {
        return state(player) == OracleState.FINISHED;
    }

    /** Their open lockable lands, a bit each by {@link OracleQuests#lockableLands()} (by progress only). */
    public static int openLandsMask(ServerPlayer player)
    {
        List<ResourceKey<Biome>> lands = OracleQuests.lockableLands();
        int mask = 0;
        for (int i = 0; i < lands.size(); i++)
        {
            OracleQuest opener = OracleQuests.openerOf(lands.get(i));
            if (opener == null || hasHandedIn(player, opener))
            {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    /** The lands whose barrier they've seen fall, a bit each by {@link OracleQuests#lockableLands()}. */
    public static int fallenLandsMask(ServerPlayer player)
    {
        return record(player).fallen();
    }

    /** True if they've seen this land's barrier fall. */
    public static boolean hasSeenFall(ServerPlayer player, ResourceKey<Biome> land)
    {
        int bit = OracleQuests.lockableLands().indexOf(land);
        return bit < 0 || (record(player).fallen() & (1 << bit)) != 0;
    }

    /**
     * This land's barrier falls for them, at this column near them (the dimension area's {@code LandWardFalls}): saved,
     * and their game plays it ({@link LandFallMessage}).
     */
    public static void barrierFalls(ServerPlayer player, ResourceKey<Biome> land, int x, int z)
    {
        int bit = OracleQuests.lockableLands().indexOf(land);
        if (bit < 0)
        {
            return;
        }
        OracleRecord record = record(player);
        record.fallen |= 1 << bit;
        OracleQuest opener = OracleQuests.openerOf(land);
        if (opener != null)
        {
            for (ResourceKey<Biome> other : opener.opens())
            {
                if (!DrossBiomes.ALL.contains(other) && OracleQuests.lockableLands().contains(other))
                {
                    record.fallen |= 1 << OracleQuests.lockableLands().indexOf(other); // (the sewers fall with Blightwood)
                }
            }
        }
        changed(player);
        QuestNetwork.sendTo(player, new LandFallMessage(bit, x, z));
    }

    /** Sends the player's marker, open lands and fallen barriers to their game. */
    public static void sync(ServerPlayer player)
    {
        QuestNetwork.sendTo(player, new OracleSyncMessage(marker(player), openLandsMask(player), fallenLandsMask(player)));
    }

    // ------------------------------------------------------------------ Test commands

    /** Clears their Oracle progress (back to the first quest), takes away its own advancements (never the bosses'). */
    public static void reset(ServerPlayer player)
    {
        record(player).clear();
        for (String id : OracleQuests.ownAdvancements())
        {
            DrossAdvancements.revoke(player, id);
        }
        changed(player);
    }

    /**
     * Finishes the step they're on and moves to the next quest, AVAILABLE: an AVAILABLE or ACTIVE quest is completed
     * (its own advancement granted; a boss's is not, the boss area grants that one), a DONE one is handed in. The last
     * quest grants "Defeat the Dross" (no fireworks) and they're FINISHED.
     *
     * @return false if there was nothing to skip (already FINISHED)
     */
    public static boolean skip(ServerPlayer player)
    {
        OracleRecord record = record(player);
        OracleQuest quest = record.quest();
        switch (record.state())
        {
            case FINISHED ->
            {
                return false;
            }
            case AVAILABLE, ACTIVE ->
            {
                if (quest.grantsOwnAdvancement())
                {
                    DrossAdvancements.grant(player, quest.advancement());
                }
                if (quest.kind() == OracleQuest.Kind.FINAL)
                {
                    record.enter(OracleState.FINISHED);
                }
                else
                {
                    next(record);
                }
            }
            case DONE -> next(record);
        }
        changed(player);
        return true;
    }

    /** Jumps to this quest: AVAILABLE, nothing counted, his offer from its first line. No advancements change. */
    public static void setQuest(ServerPlayer player, OracleQuest quest)
    {
        record(player).moveTo(quest.index());
        changed(player);
    }

    // ------------------------------------------------------------------ This package

    static OracleRecord record(ServerPlayer player)
    {
        return QuestProgressData.get(player.server.overworld()).oracle(player.getUUID());
    }

    /** Saves their progress after a change their game needn't hear about (the line he's at). */
    static void saved(ServerPlayer player)
    {
        QuestProgressData.get(player.server.overworld()).setDirty();
    }

    /** Saves and sends their progress after a change. */
    static void changed(ServerPlayer player)
    {
        QuestProgressData.get(player.server.overworld()).setDirty();
        sync(player);
    }

    /**
     * Their last offer line was heard: the quest goes ACTIVE. A TALK quest is done there (its advancement, then the
     * next quest); a BOSS quest whose advancement they already have is DONE at once; the FINAL one grants
     * "Defeat the Dross", sets off the fireworks, and they're FINISHED.
     */
    static void accept(ServerPlayer player)
    {
        OracleRecord record = record(player);
        OracleQuest quest = record.quest();
        switch (quest.kind())
        {
            case TALK ->
            {
                DrossAdvancements.grant(player, quest.advancement());
                next(record);
            }
            case KILL ->
            {
                record.enter(OracleState.ACTIVE);
                record.kills = 0;
            }
            case BOSS ->
            {
                record.enter(DrossAdvancements.has(player, quest.advancement()) ? OracleState.DONE : OracleState.ACTIVE);
            }
            case FINAL ->
            {
                record.enter(OracleState.FINISHED);
                DrossAdvancements.grant(player, quest.advancement());
                OracleFireworks.launch(player);
            }
        }
        changed(player);
    }

    /**
     * One kill counted for their ACTIVE KILL quest (the caller checked it counts): progress above the hotbar, and the
     * last one makes it DONE with its advancement.
     */
    static void addKill(ServerPlayer player)
    {
        OracleRecord record = record(player);
        OracleQuest quest = record.quest();
        if (quest.kind() != OracleQuest.Kind.KILL || record.state() != OracleState.ACTIVE)
        {
            return;
        }
        record.kills = Math.min(quest.count(), record.kills + 1);
        if (record.kills >= quest.count())
        {
            record.enter(OracleState.DONE);
            DrossAdvancements.grant(player, quest.advancement());
            tellDone(player, quest);
        }
        else
        {
            player.displayClientMessage(Component.translatable("dross.oracle.progress", quest.displayName(), record.kills,
                    quest.count()).withStyle(style -> style.withColor(DrossColors.ORACLE_PROGRESS_TEXT)), true);
        }
        changed(player);
    }

    /** Their ACTIVE BOSS quest's advancement has been earned: DONE. */
    static void bossBeaten(ServerPlayer player)
    {
        OracleRecord record = record(player);
        OracleQuest quest = record.quest();
        if (quest.kind() != OracleQuest.Kind.BOSS || record.state() != OracleState.ACTIVE)
        {
            return;
        }
        record.enter(OracleState.DONE);
        tellDone(player, quest);
        changed(player);
    }

    /**
     * The thanks are over: the next quest, AVAILABLE. A hand-in that opens a land or a boss's home ends with the Oracle's
     * spell ({@link OracleSpell}), cast by {@code oracle} (null: none is cast, as from a command).
     */
    static void handIn(ServerPlayer player, @Nullable Survivor oracle)
    {
        OracleQuest quest = record(player).quest();
        next(record(player));
        changed(player);
        if (oracle != null && OracleQuests.castsSpell(quest))
        {
            OracleSpell.cast(player, oracle, quest);
        }
    }

    private static void next(OracleRecord record)
    {
        if (record.index + 1 >= OracleQuests.ALL.size())
        {
            record.enter(OracleState.FINISHED);
        }
        else
        {
            record.moveTo(record.index + 1);
        }
    }

    private static void tellDone(ServerPlayer player, OracleQuest quest)
    {
        player.displayClientMessage(Component.translatable("dross.oracle.return", quest.displayName())
                .withStyle(style -> style.withColor(DrossColors.ORACLE_DONE_TEXT)), true);
    }
}
