package com.reg21meme.dross.quest.oracle;

import com.reg21meme.dross.Dross;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One quest the Blind Oracle gives. They're all made in {@link OracleQuests#ALL}, the only list of them; each knows its
 * place in it ({@link #index()}), so inserting a quest never breaks the numbers the rules use.
 *
 * <p>Its words are lang keys under {@code dross.oracle.<id>}: {@code .name} (above the hotbar), {@code .offer.1} ...
 * {@code .offer.<offerLines>}, {@code .remind} (kill quests get the count and the total as {@code %1$s} and
 * {@code %2$s}) and {@code .done.1} ... {@code .done.<doneLines>}.</p>
 */
public final class OracleQuest
{
    /** What finishes a quest. */
    public enum Kind
    {
        /** Listening: the last offer line finishes it and grants its advancement. */
        TALK,
        /** Killing a number of the mobs in an entity-type tag. The last kill grants its own (regular) advancement. */
        KILL,
        /** Beating a boss: the boss's advancement, which the boss area grants on the kill, finishes it. */
        BOSS,
        /** The last words: they grant "Defeat the Dross" and set off blue fireworks. */
        FINAL
    }

    private final String id;
    private final Kind kind;
    private final int offerLines;
    private final int doneLines;
    @Nullable
    private final TagKey<EntityType<?>> targets;
    private final int count;
    private final boolean adultsOnly;
    private final String advancement;
    @Nullable
    private final OracleBoss boss;
    private final List<ResourceKey<Biome>> opens;
    private int index = -1;

    private OracleQuest(String id, Kind kind, int offerLines, int doneLines, @Nullable TagKey<EntityType<?>> targets,
                        int count, boolean adultsOnly, String advancement, @Nullable OracleBoss boss,
                        List<ResourceKey<Biome>> opens)
    {
        this.id = id;
        this.kind = kind;
        this.offerLines = offerLines;
        this.doneLines = doneLines;
        this.targets = targets;
        this.count = count;
        this.adultsOnly = adultsOnly;
        this.advancement = advancement;
        this.boss = boss;
        this.opens = List.copyOf(opens);
    }

    // ------------------------------------------------------------------ Making them (OracleQuests only)

    /** A quest that's only listening: {@code offerLines} lines, then its advancement {@code dross:<id>}. */
    static OracleQuest talk(String id, int offerLines)
    {
        return new OracleQuest(id, Kind.TALK, offerLines, 0, null, 0, false, id, null, List.of());
    }

    /**
     * A kill quest: {@code count} kills of the mobs in the tag {@code dross:oracle/<tagName>} (adults only, if asked),
     * then its advancement {@code dross:<id>}. Handing it in opens the lands in {@code opens} (see {@link OracleRules}).
     */
    @SafeVarargs
    static OracleQuest kill(String id, int offerLines, int doneLines, String tagName, int count, boolean adultsOnly,
                            ResourceKey<Biome>... opens)
    {
        TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(Dross.MODID, "oracle/" + tagName));
        return new OracleQuest(id, Kind.KILL, offerLines, doneLines, tag, count, adultsOnly, id, null, List.of(opens));
    }

    /**
     * A boss quest: done by the boss's advancement {@code dross:<id>} (the boss area grants it on the kill). Reaching
     * it (the quest before it handed in) is what lets the player fight {@code boss} (see
     * {@link OracleRules#mayFightBoss}); handing it in opens the lands in {@code opens}.
     */
    @SafeVarargs
    static OracleQuest boss(String id, int offerLines, int doneLines, OracleBoss boss, ResourceKey<Biome>... opens)
    {
        return new OracleQuest(id, Kind.BOSS, offerLines, doneLines, null, 0, false, id, boss, List.of(opens));
    }

    /** The last words: {@code lines} lines, then its advancement {@code dross:<id>} and the fireworks. */
    static OracleQuest finale(String id, int lines)
    {
        return new OracleQuest(id, Kind.FINAL, lines, 0, null, 0, false, id, null, List.of());
    }

    void setIndex(int index)
    {
        this.index = index;
    }

    // ------------------------------------------------------------------ Reading

    /** Its id, also its lang prefix ({@code dross.oracle.<id>}) and the {@code /dross quest set} argument. */
    public String id()
    {
        return id;
    }

    public Kind kind()
    {
        return kind;
    }

    /** Its place in {@link OracleQuests#ALL} (0 is the first). */
    public int index()
    {
        return index;
    }

    /** How many lines he says to offer it (for TALK and FINAL quests, all their lines). */
    public int offerLines()
    {
        return offerLines;
    }

    /** How many lines he says when it's done (0 for TALK and FINAL). */
    public int doneLines()
    {
        return doneLines;
    }

    /** What counts for a KILL quest (null for the others). */
    @Nullable
    public TagKey<EntityType<?>> targets()
    {
        return targets;
    }

    /** How many kills a KILL quest wants (0 for the others). */
    public int count()
    {
        return count;
    }

    /** True if a KILL quest counts only grown mobs (never babies). */
    public boolean adultsOnly()
    {
        return adultsOnly;
    }

    /**
     * The advancement that goes with it, {@code dross:<advancement>}: for TALK, KILL and FINAL quests granted by the quest
     * area when it's done; for a BOSS quest the boss's own, which the boss area grants and which finishes it.
     */
    public String advancement()
    {
        return advancement;
    }

    /** The boss a BOSS quest is about (null for the others). */
    @Nullable
    public OracleBoss boss()
    {
        return boss;
    }

    /** The lands handing in this quest opens (empty for most). */
    public List<ResourceKey<Biome>> opens()
    {
        return opens;
    }

    /** True if the quest area grants its advancement itself (TALK, KILL, FINAL), so {@code /dross quest reset} takes it away. */
    public boolean grantsOwnAdvancement()
    {
        return kind != Kind.BOSS;
    }

    /** Its name, above the hotbar ("Into the Ashes"). */
    public Component displayName()
    {
        return Component.translatable(key("name"));
    }

    /** {@code dross.oracle.<id>.<part>}. */
    public String key(String part)
    {
        return "dross.oracle." + id + "." + part;
    }

    @Override
    public String toString()
    {
        return index + " " + id + " (" + kind + ")";
    }
}
