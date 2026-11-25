package com.reg21meme.dross.quest.oracle;

import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.quest.DrossAdvancements;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * THE ORACLE'S QUESTS, in order: the only place they're defined (QUEST_PLAN.md, "The quest line"). One quest at a
 * time, always in this order; {@code defeat_the_dross} always stays last.
 *
 * <h2>What opens what (the user, November 18, 2026)</h2>
 * A land opens when the quest listing it in {@code opens} is <b>handed in</b> (its last thanks line); a boss may fight a
 * player once the quest before its own is handed in. Each of those hand-ins ends with the Oracle's spell
 * ({@link OracleSpell}): the giants break the forest's barrier, a land's kill quest the ward on its boss's home, a boss's
 * dying energy the next barrier. A KILL quest comes only the first time a land is entered (the user: not before every
 * boss).
 *
 * <h2>Adding a quest for a new boss</h2>
 * One entry before {@link #DEFEAT_THE_DROSS}: a BOSS quest named after the boss's advancement, with a new
 * {@link OracleBoss}; and, if the boss lives in a land no quest has opened yet, a KILL quest before it that scouts the
 * land (its tag {@code data/dross/tags/entity_types/oracle/<name>.json}, {@code "required": false} entries), the land in
 * the {@code opens} of the quest whose hand-in should open it. Then their lines in {@code en_us.json}
 * ({@code dross.oracle.<id>.name}, {@code .offer.<n>}, {@code .remind}, {@code .done.<n>}; a hand-in that casts the
 * spell says so in its last done line), the kill quest's advancement JSON, and the chain's parents. Nothing else uses
 * numbers: the rules ask the quests.
 */
public final class OracleQuests
{
    // How many lines each says is part of its entry (offer lines, then done lines). Counts are the plan's first drafts.

    /** 0: his story. */
    public static final OracleQuest ORACLE_PLEA = OracleQuest.talk(DrossAdvancements.ORACLE_PLEA, 7);
    /** 1: three Silverwood Giants. Handing it in breaks the forest's barrier (his spell): the Cinder Wastes and the Gravecrags open. */
    public static final OracleQuest GIANT_SLAYER = OracleQuest.kill(DrossAdvancements.GIANT_SLAYER, 3, 2,
            "silverwood_giants", 3, false, DrossBiomes.CINDER_WASTES, DrossBiomes.GRAVECRAGS);
    /** 2: three of the Cinder Wastes' mobs. */
    public static final OracleQuest INTO_THE_ASHES = OracleQuest.kill(DrossAdvancements.INTO_THE_ASHES, 3, 1,
            "cinder_wastes_mobs", 3, false);
    /** 3: the Cinder Colossus. His offer tells of it, then breaks its volcanoes' ward (line 4). */
    public static final OracleQuest SET_IN_STONE = OracleQuest.boss(DrossAdvancements.SET_IN_STONE, 5, 2, OracleBoss.COLOSSUS)
            .spell(4);
    /** 4: three of Blightwood's creatures (its rats and witches, and its mobs to come). His offer breaks Blightwood's barrier (line 2). */
    public static final OracleQuest BENEATH_THE_ROT = OracleQuest.kill(DrossAdvancements.BENEATH_THE_ROT, 4, 1,
            "blightwood_mobs", 3, false, DrossBiomes.BLIGHTWOOD, DrossBiomes.SEWERS).spell(2);
    /** 5: the Rat King. His offer tells of him, then breaks his cave's ward (line 3). */
    public static final OracleQuest DOWN_THE_DRAIN = OracleQuest.boss(DrossAdvancements.DOWN_THE_DRAIN, 4, 1, OracleBoss.RAT_KING)
            .spell(3);
    /** 6: three of Rimefall's grown beasts. His offer breaks Rimefall's barrier (line 3). */
    public static final OracleQuest FIRST_FROST = OracleQuest.kill(DrossAdvancements.FIRST_FROST, 5, 1,
            "rimefall_beasts", 3, true, DrossBiomes.RIMEFALL).spell(3);
    /** 7: the Huntsman and his Alpha Moose. His offer tells of him, then breaks his village's ward (line 3). */
    public static final OracleQuest THE_HUNTER_HUNTED = OracleQuest.boss(DrossAdvancements.THE_HUNTER_HUNTED, 4, 1, OracleBoss.HUNTSMAN)
            .spell(3);
    /** 8: the Emberdactyl and its young. His offer tells of it, then breaks its volcano's ward (line 3). */
    public static final OracleQuest WINGS_CLIPPED = OracleQuest.boss(DrossAdvancements.WINGS_CLIPPED, 4, 2, OracleBoss.EMBERDACTYL)
            .spell(3);
    /** 9: his last words, "Defeat the Dross". Always last. */
    public static final OracleQuest DEFEAT_THE_DROSS = OracleQuest.finale(DrossAdvancements.DEFEAT_THE_DROSS, 4);

    /** Every quest, in order. */
    public static final List<OracleQuest> ALL = List.of(
            ORACLE_PLEA,
            GIANT_SLAYER,
            INTO_THE_ASHES,
            SET_IN_STONE,
            BENEATH_THE_ROT,
            DOWN_THE_DRAIN,
            FIRST_FROST,
            THE_HUNTER_HUNTED,
            WINGS_CLIPPED,
            DEFEAT_THE_DROSS);

    /** The lands a quest can open, in a fixed order (their bits in {@link OracleSyncMessage}). */
    private static final List<ResourceKey<Biome>> LOCKABLE_LANDS;

    static
    {
        List<ResourceKey<Biome>> lands = new ArrayList<>();
        for (int i = 0; i < ALL.size(); i++)
        {
            OracleQuest quest = ALL.get(i);
            quest.setIndex(i);
            check(quest, i);
            for (ResourceKey<Biome> land : quest.opens())
            {
                if (lands.contains(land))
                {
                    throw new IllegalStateException("Oracle quest " + quest.id() + " opens " + land.location()
                            + ", which an earlier quest already opens");
                }
                lands.add(land);
            }
        }
        if (lands.size() > 31)
        {
            throw new IllegalStateException("too many lockable lands for the Oracle's sync message (31 at most)");
        }
        LOCKABLE_LANDS = List.copyOf(lands);
    }

    private OracleQuests() {}

    private static void check(OracleQuest quest, int index)
    {
        for (int j = 0; j < index; j++)
        {
            if (ALL.get(j).id().equals(quest.id()))
            {
                throw new IllegalStateException("two Oracle quests are called " + quest.id());
            }
        }
        boolean lines = switch (quest.kind())
        {
            case TALK, FINAL -> quest.offerLines() >= 1;
            case KILL -> quest.offerLines() >= 1 && quest.doneLines() >= 1 && quest.count() >= 1 && quest.targets() != null;
            case BOSS -> quest.offerLines() >= 1 && quest.doneLines() >= 1 && quest.boss() != null;
        };
        if (!lines)
        {
            throw new IllegalStateException("Oracle quest " + quest.id() + " is missing its lines, count or target");
        }
        if (quest.spellLine() < 0 || quest.spellLine() > quest.offerLines()
                || (quest.kind() == OracleQuest.Kind.BOSS && quest.spellLine() < 1))
        {
            throw new IllegalStateException("Oracle quest " + quest.id() + "'s spell line " + quest.spellLine()
                    + " isn't one of its offer lines (a boss's quest must break its ward in its offer)");
        }
        if (quest.kind() == OracleQuest.Kind.FINAL && index != ALL.size() - 1)
        {
            throw new IllegalStateException("the Oracle's final quest must be the last one");
        }
    }

    /** The first quest. */
    public static OracleQuest first()
    {
        return ALL.get(0);
    }

    /** The quest at this place in the list (clamped to the list). */
    public static OracleQuest get(int index)
    {
        return ALL.get(Math.max(0, Math.min(ALL.size() - 1, index)));
    }

    /** The quest with this id, or null. */
    @Nullable
    public static OracleQuest byId(String id)
    {
        for (OracleQuest quest : ALL)
        {
            if (quest.id().equals(id))
            {
                return quest;
            }
        }
        return null;
    }

    /** Every quest's id, in order (for {@code /dross quest set}'s tab completion). */
    public static List<String> ids()
    {
        return ALL.stream().map(OracleQuest::id).toList();
    }

    /** The quest whose spell opens this land, or null if no quest locks it (it's always open). */
    @Nullable
    public static OracleQuest openerOf(ResourceKey<Biome> land)
    {
        for (OracleQuest quest : ALL)
        {
            if (quest.opens().contains(land))
            {
                return quest;
            }
        }
        return null;
    }

    /** The BOSS quest about this boss (every {@link OracleBoss} has one). */
    public static OracleQuest questFor(OracleBoss boss)
    {
        for (OracleQuest quest : ALL)
        {
            if (quest.boss() == boss)
            {
                return quest;
            }
        }
        throw new IllegalStateException("no Oracle quest for the boss " + boss);
    }

    /** Every land some quest locks until it's handed in, in a fixed order. */
    public static List<ResourceKey<Biome>> lockableLands()
    {
        return LOCKABLE_LANDS;
    }

    /**
     * True if handing in this quest ends with the Oracle's spell: it opens lands and has no spell in its offer (the
     * forest giants'). Every other spell goes off on its quest's {@link OracleQuest#spellLine} instead.
     */
    public static boolean castsSpell(OracleQuest quest)
    {
        return quest.spellLine() == 0 && !quest.opens().isEmpty();
    }

    /** The quest after this one, or null for the last. */
    @Nullable
    public static OracleQuest next(OracleQuest quest)
    {
        int next = quest.index() + 1;
        return next < ALL.size() ? ALL.get(next) : null;
    }

    /** Every advancement the quest area grants for these quests (the bosses' aren't: the boss area grants those). */
    public static List<String> ownAdvancements()
    {
        return ALL.stream().filter(OracleQuest::grantsOwnAdvancement).map(OracleQuest::advancement).toList();
    }
}
