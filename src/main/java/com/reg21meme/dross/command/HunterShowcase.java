package com.reg21meme.dross.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.boss.rimefall.RimefallCandidate;
import com.reg21meme.dross.boss.rimefall.RimefallDesigns;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.List;

/**
 * {@code /dross showcase hunters} (October 23, 2026; the user: "10-12 villagers that can spawn, 2 blocks tall, that are
 * similar to the Huntsman"): the boss area's hunter designs ({@link RimefallDesigns#HUNTERS}, Nos. 10A-10L) in two rows
 * of {@value #PER_ROW} ahead of you, facing you, on a snow floor, the back row a step up so both rows show; a packed ice
 * post and a sign in front of each ("No. 10A", "Hunter", what he is). They're the gallery's frozen statues
 * ({@link RimefallCandidate}): right-click one for its name and height. Running it again replaces them (old statues
 * within {@value #REMOVE_RADIUS} blocks removed).
 */
public final class HunterShowcase
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Statues in a row. */
    static final int PER_ROW = 6;
    /** Blocks between two statues in a row. */
    static final int SPACING = 3;
    /** The first row's distance ahead of you, and the second's. */
    static final int FIRST_ROW = 7;
    static final int SECOND_ROW = 12;
    /** Air cleared over the floor, and how deep a dip under it is filled. */
    static final int CLEAR_ABOVE = 5;
    static final int MAX_FILL = 6;
    /** Old statues this near (X and Z) are removed first. */
    static final int REMOVE_RADIUS = 48;

    private HunterShowcase() {}

    public static int run(CommandContext<CommandSourceStack> context)
    {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        Vec3 position = source.getPosition();
        Direction look = Direction.fromYRot(source.getRotation().y);
        Direction right = look.getClockWise();
        BlockPos origin = new BlockPos(Mth.floor(position.x), Showcases.groundY(level, position), Mth.floor(position.z));

        int removed = 0;
        AABB area = Showcases.columnsAround(level, position, REMOVE_RADIUS);
        for (RimefallCandidate old : level.getEntities(ModEntities.RIMEFALL_CANDIDATE.get(), area,
                c -> RimefallDesigns.HUNTERS.contains(c.getDesign())))
        {
            old.discard();
            removed++;
        }

        int half = (PER_ROW - 1) * SPACING / 2 + 2;
        Showcases.clearFloor(level, origin, look, right, -half, half, SECOND_ROW + 3, CLEAR_ABOVE + 1, MAX_FILL);
        BlockState snow = Blocks.SNOW_BLOCK.defaultBlockState();
        BlockState post = Blocks.PACKED_ICE.defaultBlockState();
        for (int ahead = 1; ahead <= SECOND_ROW + 3; ahead++)
        {
            for (int side = -half; side <= half; side++)
            {
                BlockPos ground = origin.relative(look, ahead).relative(right, side);
                level.setBlock(ground, snow, Block.UPDATE_CLIENTS);
                if (ahead >= SECOND_ROW - 2)
                {
                    level.setBlock(ground.above(), snow, Block.UPDATE_CLIENTS);     // the back row's step
                }
            }
        }

        List<RimefallDesigns.Design> hunters = RimefallDesigns.HUNTERS;
        int spawned = 0;
        for (int i = 0; i < hunters.size(); i++)
        {
            RimefallDesigns.Design design = hunters.get(i);
            int row = i / PER_ROW;
            int column = i % PER_ROW;
            int ahead = row == 0 ? FIRST_ROW : SECOND_ROW;
            int side = column * SPACING - (PER_ROW - 1) * SPACING / 2;
            BlockPos ground = origin.relative(look, ahead).relative(right, side).above(row);
            Vec3 feet = Vec3.atBottomCenterOf(ground.above());
            if (RimefallCandidate.spawnShowcase(level, feet, look.getOpposite().toYRot(), design) != null)
            {
                spawned++;
            }
            else
            {
                LOGGER.warn("Hunter showcase: couldn't spawn No. {}", design.label());
            }
            // the post and its sign, two blocks in front of him, the sign facing you
            BlockPos postPos = ground.relative(look.getOpposite(), 2).above();
            level.setBlock(postPos, post, Block.UPDATE_ALL);
            Showcases.placeWallSign(level, postPos.relative(look.getOpposite()), look.getOpposite(),
                    "No. " + design.label(), design.name(), design.mood());
        }
        int total = hunters.size();
        int made = spawned;
        int gone = removed;
        LOGGER.info("Hunter showcase: spawned {} of {} for {} at {} looking {}; removed {} old", made, total,
                source.getTextName(), origin.toShortString(), look.getName(), gone);
        source.sendSuccess(() -> Component.literal("Lined up " + made + " of " + total + " hunter designs ahead of you "
                + "(No. 10A-10L, two rows" + (gone == 0 ? "" : "; removed " + gone + " old") + "). Right-click one "
                + "for its name and height; tell me which you'd like in the villages."), true);
        return made;
    }
}
