package com.reg21meme.dross.boss.emberdactyl.trophy;

import com.reg21meme.dross.boss.trophy.TrophyRewards;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.registry.ModItems;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.core.animation.RawAnimation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The Emberdactyl trophies: the fifteen designs of a 1x1x1 decorative block ({@code dross:emberdactyl_trophy}), each a
 * miniature of the Emberdactyl (its own models, scaled down, no base), for the user to pick from (October 29, 2026:
 * "the only young trophy I want is of him bursting out of the egg, and then both the adult and the young together in a
 * combo thing; give me 15 different trophies but mostly adult ones; maybe include the volcano as well"):
 * <ol>
 *   <li>The Sky That Burns: reared up on a spire of rock, wings flung up, screaming</li>
 *   <li>The Downbeat: hovering over a spire, its wings at the bottom of a beat</li>
 *   <li>Wide Glide: gliding off a spire on its full wingspan</li>
 *   <li>Into the Fire: plunging head first into a pool of lava</li>
 *   <li>Talons Out: landing on a spire, talons thrown forward</li>
 *   <li>Gathering Storm: its wings closed over its back before the gust</li>
 *   <li>Obsidian Beak: on the ground, its head drawn back to strike</li>
 *   <li>At Rest: perched on its legs and folded wings</li>
 *   <li>The Last Scream: its death, magma cracks splitting its hide</li>
 *   <li>Lord of the Caldera: alighting on the rim of its volcano</li>
 *   <li>Guardian of the Nest: hovering over its volcano, its egg in the nest</li>
 *   <li>Reborn: its young bursting out of the egg</li>
 *   <li>Under Its Wing: the adult reared up, its young perched before it</li>
 *   <li>Flight of Embers: the two taking flight together</li>
 *   <li>Twin Screech: the two screaming side by side</li>
 * </ol>
 * Right-clicking a placed trophy turns it to the next design ({@link #next}), so any trophy can show any of them. The
 * names are lang keys ({@link Design#nameKey}); the shapes come from their models ({@link EmberTrophyModels}, generated
 * by {@code tools/fire/trophies.py}). This class is the API the trophy showcase uses: {@link #DESIGNS}, {@link #get},
 * {@link Design#blockState} and {@link Design#stack}; beating the young gives its reward with {@link #giveTo} (the shared
 * {@link TrophyRewards}).
 */
public final class EmberdactylTrophies
{
    // ---------------------------------------------------------------- the block

    /** How long it takes to break (wool is 0.8): any tool, or a bare hand, breaks it in about a second and it drops itself. */
    public static final float HARDNESS = 0.8F;
    /** Explosions can't break it (obsidian is 1200). */
    public static final float BLAST_RESISTANCE = 1200.0F;

    // ---------------------------------------------------------------- changing its pose (right-click)

    /**
     * Right-clicking a placed trophy turns it to the next design (1, 2 ... 15, then 1 again), with a quiet click and a
     * soft crackle of embers. Players who can't build there (adventure mode) can't change it, so a map maker's display
     * stays as it is. This is the volume of the click (vanilla's stone button).
     */
    public static final float CYCLE_CLICK_VOLUME = 0.4F;
    /** Pitch of the click (1 is normal; a little lower, softer). */
    public static final float CYCLE_CLICK_PITCH = 0.7F;
    /** Volume of the crackle of embers under the click (a campfire's). */
    public static final float CYCLE_CRACKLE_VOLUME = 0.6F;
    /** Pitch of that crackle. */
    public static final float CYCLE_CRACKLE_PITCH = 1.1F;
    /** Specks of ember in the little puff it gives off. */
    public static final int CYCLE_DUST_COUNT = 8;
    /** Size of each speck (vanilla redstone dust is 1). */
    public static final float CYCLE_DUST_SIZE = 0.7F;
    /** How far the puff spreads round the figure (blocks, each way). */
    public static final double CYCLE_PUFF_SPREAD = 0.25D;
    /** How high in the block the puff's middle is (blocks above its floor). */
    public static final double CYCLE_PUFF_HEIGHT = 0.4D;

    // ---------------------------------------------------------------- its particles (client, now and then)
    // Each is the chance that it happens each time the game picks the block for a random effect: the client does that
    // for blocks near the player, a few times a second right next to it and about once a second a few blocks away.

    /** An ember drifting down from a wing tip. */
    public static final float EMBER_CHANCE = 0.2F;
    /** A wisp of smoke from the volcano's crater, the burst egg or the breaking body. */
    public static final float SMOKE_CHANCE = 0.2F;
    /** A lava pop out of the volcano's crater. */
    public static final float LAVA_CHANCE = 0.05F;

    /** Lang keys: a design's name is this plus its number; the tooltip line is "No. 1: The Sky That Burns"; and the hint. */
    public static final String NAME_KEY = "block.dross.emberdactyl_trophy.design_";
    public static final String TOOLTIP_KEY = "block.dross.emberdactyl_trophy.tooltip";
    public static final String HINT_KEY = "block.dross.emberdactyl_trophy.hint";
    /** The item tag vanilla uses to give a placed block its properties (and loot tables' copy_state fills in). */
    private static final String BLOCK_STATE_TAG = BlockItem.BLOCK_STATE_TAG;

    /**
     * One trophy design.
     *
     * @param number its number (1 to {@link #count()})
     * @param model  its generated shape: scale, outline, glow, light, particle spots
     */
    public record Design(int number, EmberTrophyModels.Model model)
    {
        /** Its lang key (the name: "The Sky That Burns"). */
        public String nameKey()
        {
            return NAME_KEY + this.number;
        }

        /** Its name, for signs and chat. */
        public Component displayName()
        {
            return Component.translatable(this.nameKey());
        }

        /** A word or two about it, for the showcase sign ("Screech", "Volcano"...). */
        public String look()
        {
            return this.model.look();
        }

        /** The block placed with this design, the figure facing {@code facing} (a horizontal direction). */
        public BlockState blockState(Direction facing)
        {
            Direction horizontal = facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
            return ModBlocks.EMBERDACTYL_TROPHY.get().defaultBlockState()
                    .setValue(EmberTrophyBlock.FACING, horizontal)
                    .setValue(EmberTrophyBlock.DESIGN, this.number);
        }

        /** One trophy item of this design. */
        public ItemStack stack()
        {
            ItemStack stack = new ItemStack(ModItems.EMBERDACTYL_TROPHY.get());
            CompoundTag state = new CompoundTag();
            state.putString(EmberTrophyBlock.DESIGN.getName(), Integer.toString(this.number));
            stack.getOrCreateTag().put(BLOCK_STATE_TAG, state);
            return stack;
        }

        /** Its held pose (GeckoLib animation). */
        public RawAnimation animation()
        {
            return ANIMATIONS.get(this.number - 1);
        }
    }

    /** Every design, in order (design 1 first). */
    public static final List<Design> DESIGNS;
    private static final List<RawAnimation> ANIMATIONS;

    static
    {
        List<Design> designs = new ArrayList<>();
        List<RawAnimation> animations = new ArrayList<>();
        for (EmberTrophyModels.Model model : EmberTrophyModels.ALL)
        {
            designs.add(new Design(model.number(), model));
            animations.add(RawAnimation.begin().thenLoop(EmberTrophyModels.ANIMATION_PREFIX + model.number()));
        }
        DESIGNS = List.copyOf(designs);
        ANIMATIONS = List.copyOf(animations);
    }

    private EmberdactylTrophies()
    {
    }

    /** How many designs there are (15). */
    public static int count()
    {
        return DESIGNS.size();
    }

    /** The design with this number (an unknown number gives design 1). */
    public static Design get(int number)
    {
        return number >= 1 && number <= DESIGNS.size() ? DESIGNS.get(number - 1) : DESIGNS.get(0);
    }

    /** The design after {@code number} when a trophy is right-clicked: 1, 2 ... 15, then 1 again. */
    public static int next(int number)
    {
        return get(number).number() % count() + 1;
    }

    /** The design of a trophy item (design 1 if it doesn't say, for example from {@code /give}). */
    public static int designOf(ItemStack stack)
    {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(BLOCK_STATE_TAG))
        {
            return 1;
        }
        String value = tag.getCompound(BLOCK_STATE_TAG).getString(EmberTrophyBlock.DESIGN.getName());
        try
        {
            return get(Integer.parseInt(value)).number();
        }
        catch (NumberFormatException e)
        {
            return 1;
        }
    }

    /** One trophy item of design {@code number}. */
    public static ItemStack stack(int number)
    {
        return get(number).stack();
    }

    /**
     * The fight's reward: one trophy of design {@code number} for this player, straight into their inventory; with a full
     * inventory dropped a block in front of them, and for a dead player where they died; a dropped one is theirs alone
     * and can't be destroyed ({@link TrophyRewards#giveTo}, shared with the other bosses).
     *
     * @return the dropped item, or null when it went into their inventory
     */
    @Nullable
    public static ItemEntity giveTo(ServerPlayer player, int number)
    {
        return TrophyRewards.giveTo(player, stack(number));
    }
}
