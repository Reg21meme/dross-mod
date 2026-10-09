package com.reg21meme.dross.quest;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Everything the Dross trader asks for, with how many of each.
 * <ul>
 *   <li>{@link Group#TRIBUTE}: the three tributes (any order). All three earn the Dross Compass.</li>
 *   <li>{@link Group#KEY_MATERIAL}: the three key materials (any order, only after the tributes).
 *       All three forge a Rift Key. They can be brought again later to forge a replacement key.</li>
 * </ul>
 * Change the amounts with the constants below.
 */
public enum QuestRequirement
{
    WITHER_SKELETON_SKULL(Group.TRIBUTE, Items.WITHER_SKELETON_SKULL, QuestRequirement.WITHER_SKELETON_SKULLS_NEEDED),
    ECHO_SHARD(Group.TRIBUTE, Items.ECHO_SHARD, QuestRequirement.ECHO_SHARDS_NEEDED),
    END_STONE(Group.TRIBUTE, Items.END_STONE, QuestRequirement.END_STONE_NEEDED),
    TRIDENT(Group.KEY_MATERIAL, Items.TRIDENT, QuestRequirement.TRIDENTS_NEEDED),
    HEART_OF_THE_SEA(Group.KEY_MATERIAL, Items.HEART_OF_THE_SEA, QuestRequirement.HEARTS_OF_THE_SEA_NEEDED),
    AMETHYST_SHARD(Group.KEY_MATERIAL, Items.AMETHYST_SHARD, QuestRequirement.AMETHYST_SHARDS_NEEDED);

    // ---- Tributes (Step 4 of the quest) ----
    /** Wither skeleton skulls (Nether fortresses). */
    public static final int WITHER_SKELETON_SKULLS_NEEDED = 1;
    /** Echo shards (Ancient Cities in the deep dark). */
    public static final int ECHO_SHARDS_NEEDED = 3;
    /** End stone (the End). */
    public static final int END_STONE_NEEDED = 15;

    // ---- Key materials (Step 5 of the quest) ----
    /** Tridents (any trident counts, enchanted or damaged). */
    public static final int TRIDENTS_NEEDED = 1;
    /** Hearts of the Sea (buried treasure). */
    public static final int HEARTS_OF_THE_SEA_NEEDED = 1;
    /** Amethyst shards (geodes). */
    public static final int AMETHYST_SHARDS_NEEDED = 8;

    /** Which part of the quest a requirement belongs to. */
    public enum Group
    {
        TRIBUTE,
        KEY_MATERIAL
    }

    private final Group group;
    private final Item item;
    private final int amount;

    QuestRequirement(Group group, Item item, int amount)
    {
        this.group = group;
        this.item = item;
        this.amount = amount;
    }

    public Group getGroup()
    {
        return group;
    }

    /** The item the trader wants. */
    public Item getItem()
    {
        return item;
    }

    /** How many he takes in one hand-in (the full amount, never part of it). */
    public int getAmount()
    {
        return amount;
    }

    /** True if this stack is the right item (the amount isn't checked). */
    public boolean matches(ItemStack stack)
    {
        return !stack.isEmpty() && stack.is(item);
    }

    /** For chat lines, for example "3 Echo Shard" (amount, then the item's own translated name). */
    public Component describe()
    {
        return Component.translatable("quest.dross.requirement", amount, item.getDescription());
    }

    /** Name used in the save file. Never change it, or saved progress for this requirement is lost. */
    String saveName()
    {
        return name().toLowerCase(Locale.ROOT);
    }

    @Nullable
    static QuestRequirement fromSaveName(String name)
    {
        for (QuestRequirement requirement : values())
        {
            if (requirement.saveName().equals(name))
            {
                return requirement;
            }
        }
        return null;
    }

    /** The requirement this item counts towards, or null if the trader never wants it. */
    @Nullable
    public static QuestRequirement forItem(ItemStack stack)
    {
        for (QuestRequirement requirement : values())
        {
            if (requirement.matches(stack))
            {
                return requirement;
            }
        }
        return null;
    }

    /** All requirements in one group, in the order above. */
    public static List<QuestRequirement> inGroup(Group group)
    {
        return Arrays.stream(values()).filter(r -> r.group == group).toList();
    }
}
