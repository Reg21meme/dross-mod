package com.reg21meme.dross.boss;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.golem.CinderColossus;
import com.reg21meme.dross.boss.huntsman.Huntsman;
import com.reg21meme.dross.boss.huntsman.HuntsmanPair;
import com.reg21meme.dross.boss.ratking.RatKing;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import javax.annotation.Nullable;

/**
 * The boss area's test commands. The {@code /dross} command itself belongs to world-builder, which calls these:
 * <ul>
 *   <li>{@code /dross boss summon colossus}: {@link #summonColossus};</li>
 *   <li>{@code /dross boss arena colossus}: {@link #buildArena};</li>
 *   <li>{@code /dross boss summon ratking}: {@link #summonRatKing};</li>
 *   <li>{@code /dross boss arena ratking}: {@link #buildRatKingArena};</li>
 *   <li>{@code /dross boss arena bedrock}: {@link #buildBedrockArena} (any boss: a spawn-proof test arena).</li>
 * </ul>
 * Both use the command's position, rotation and dimension, so they also work from command blocks and the console
 * ({@code execute positioned ... rotated ... run dross boss arena colossus}). They need cheats, like the rest of /dross.
 */
public final class BossCommands
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The command that summons the Colossus (the arena's chat button runs it). */
    public static final String SUMMON_COMMAND = "/dross boss summon colossus";
    /** The command that summons the Rat King (his arena's chat button runs it). */
    public static final String SUMMON_RAT_KING_COMMAND = "/dross boss summon ratking";
    /** The command the Huntsman's arena's button runs (it must match {@code /dross boss summon huntsman}). */
    public static final String SUMMON_HUNTSMAN_COMMAND = "/dross boss summon huntsman";
    /** The Huntsman rides in this far ahead (or as close to it as there's room). */
    private static final int HUNTSMAN_SUMMON_AHEAD = 16;
    /** The Colossus is summoned this many blocks in front of you (closer if there's no room there)... */
    private static final int SUMMON_AHEAD = 10;
    /** ...but never closer than this. */
    private static final int SUMMON_NEAREST = 4;
    /**
     * The Rat King appears this far ahead (or as close to it as there's room, never nearer than RAT_KING_SUMMON_NEAREST):
     * beyond his 12-block wake range, so you see him asleep and walk up to him (or sneak).
     */
    private static final int RAT_KING_SUMMON_AHEAD = 14;
    private static final int RAT_KING_SUMMON_NEAREST = 13;
    /** How far up or down from your feet it looks for ground to stand the Colossus on. */
    private static final int GROUND_SEARCH_UP = 4;
    private static final int GROUND_SEARCH_DOWN = 8;

    private BossCommands() {}

    /**
     * {@code /dross boss summon colossus}: a fighting Cinder Colossus rises {@link #SUMMON_AHEAD} blocks in front of
     * you (or as close to that as there's room), facing you. It notices players it can see and the fight begins.
     * Not on Peaceful (monsters can't exist there).
     *
     * @return 1 if it was summoned, 0 if not (the reason is sent as a failure message)
     */
    public static int summonColossus(CommandSourceStack source)
    {
        ServerLevel level = source.getLevel();
        if (level.getDifficulty() == Difficulty.PEACEFUL)
        {
            source.sendFailure(Component.translatable("commands.dross.boss.summon.peaceful"));
            return 0;
        }
        Vec3 from = source.getPosition();
        float lookYaw = source.getRotation().y;
        Vec3 look = new Vec3(-Mth.sin(lookYaw * Mth.DEG_TO_RAD), 0.0D, Mth.cos(lookYaw * Mth.DEG_TO_RAD));
        EntityType<CinderColossus> type = ModEntities.CINDER_COLOSSUS.get();
        Vec3 feet = null;
        int distance = 0;
        for (int d = SUMMON_AHEAD; d >= SUMMON_NEAREST && feet == null; d--)
        {
            feet = standingSpot(level, type, from.add(look.scale(d)));
            distance = d;
        }
        if (feet == null)
        {
            feet = from;
            distance = 0;
        }
        CinderColossus golem = CinderColossus.spawnBoss(level, feet, lookYaw + 180.0F);
        if (golem == null)
        {
            source.sendFailure(Component.translatable("commands.dross.boss.summon.failed"));
            return 0;
        }
        int ahead = distance;
        LOGGER.info("Summoned a Cinder Colossus for {} at {} in {}", source.getTextName(), BlockPos.containing(feet).toShortString(),
                level.dimension().location());
        source.sendSuccess(() -> Component.translatable("commands.dross.boss.summon.success", ahead), true);
        return 1;
    }

    /**
     * {@code /dross boss arena colossus}: builds the placeholder boss arena ({@link BossArena}) round you, its floor
     * where you stand. You stay where you are (in its middle), and the chat message has a button that summons the Colossus.
     *
     * @return how many blocks were changed (at least 1)
     */
    public static int buildArena(CommandSourceStack source)
    {
        ServerLevel level = source.getLevel();
        BlockPos floor = BossArena.floorUnder(level, BlockPos.containing(source.getPosition()));
        BossArena.Result result = BossArena.build(level, floor);
        LOGGER.info("Built the placeholder boss arena for {} round {} in {} ({} blocks changed, {} block entities left alone)",
                source.getTextName(), floor.toShortString(), level.dimension().location(), result.changed(),
                result.keptBlockEntities());
        int across = 2 * BossArena.RADIUS + 1;
        MutableComponent message = Component.translatable("commands.dross.boss.arena.success", across);
        if (result.keptBlockEntities() > 0)
        {
            message.append(" ").append(Component.translatable("commands.dross.boss.arena.kept", result.keptBlockEntities()));
        }
        message.append(" ").append(Component.translatable("commands.dross.boss.arena.summon_button").withStyle(style -> style
                .withColor(DrossColors.BOSS_COMMAND_BUTTON)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, SUMMON_COMMAND))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("commands.dross.boss.arena.summon_hover", SUMMON_COMMAND)))));
        source.sendSuccess(() -> message, true);
        return Math.max(1, result.changed());
    }

    /**
     * {@code /dross boss summon ratking}: a fighting Rat King appears {@link #RAT_KING_SUMMON_AHEAD} blocks in front of
     * you (or as close to that as there's room), facing you, fast asleep. Coming within 12 blocks (8 if you sneak) or hitting him
     * wakes him, and the fight begins. Not on Peaceful (monsters can't exist there).
     *
     * @return 1 if he was summoned, 0 if not (the reason is sent as a failure message)
     */
    public static int summonRatKing(CommandSourceStack source)
    {
        ServerLevel level = source.getLevel();
        if (level.getDifficulty() == Difficulty.PEACEFUL)
        {
            source.sendFailure(Component.translatable("commands.dross.boss.summon_ratking.peaceful"));
            return 0;
        }
        Vec3 from = source.getPosition();
        float lookYaw = source.getRotation().y;
        Vec3 look = new Vec3(-Mth.sin(lookYaw * Mth.DEG_TO_RAD), 0.0D, Mth.cos(lookYaw * Mth.DEG_TO_RAD));
        EntityType<RatKing> type = ModEntities.RAT_KING.get();
        Vec3 feet = null;
        int distance = 0;
        for (int d = RAT_KING_SUMMON_AHEAD; d >= RAT_KING_SUMMON_NEAREST && feet == null; d--)
        {
            feet = standingSpot(level, type, from.add(look.scale(d)));
            distance = d;
        }
        for (int d = RAT_KING_SUMMON_NEAREST - 1; d >= SUMMON_NEAREST && feet == null; d--)
        {
            // No room that far: closer (he may wake at once).
            feet = standingSpot(level, type, from.add(look.scale(d)));
            distance = d;
        }
        if (feet == null)
        {
            feet = from;
            distance = 0;
        }
        RatKing king = RatKing.spawnBoss(level, feet, lookYaw + 180.0F);
        if (king == null)
        {
            source.sendFailure(Component.translatable("commands.dross.boss.summon_ratking.failed"));
            return 0;
        }
        int ahead = distance;
        LOGGER.info("Summoned a Rat King for {} at {} in {}", source.getTextName(), BlockPos.containing(feet).toShortString(),
                level.dimension().location());
        source.sendSuccess(() -> Component.translatable("commands.dross.boss.summon_ratking.success", ahead), true);
        return 1;
    }

    /**
     * {@code /dross boss arena ratking}: builds the Rat King's placeholder arena ({@link RatKingArena}) round you, its
     * floor where you stand: a round floor of dirt and coarse dirt. You stay where you are (in its middle), and the chat
     * message has a button that summons the Rat King.
     *
     * @return how many blocks were changed (at least 1)
     */
    public static int buildRatKingArena(CommandSourceStack source)
    {
        ServerLevel level = source.getLevel();
        BlockPos floor = BossArena.floorUnder(level, BlockPos.containing(source.getPosition()));
        RatKingArena.Result result = RatKingArena.build(level, floor);
        LOGGER.info("Built the Rat King's placeholder arena for {} round {} in {} ({} blocks changed, {} block entities left alone)",
                source.getTextName(), floor.toShortString(), level.dimension().location(), result.changed(),
                result.keptBlockEntities());
        int across = 2 * RatKingArena.RADIUS + 1;
        MutableComponent message = Component.translatable("commands.dross.boss.arena_ratking.success", across);
        if (result.keptBlockEntities() > 0)
        {
            message.append(" ").append(Component.translatable("commands.dross.boss.arena.kept", result.keptBlockEntities()));
        }
        message.append(" ").append(Component.translatable("commands.dross.boss.arena_ratking.summon_button").withStyle(style -> style
                .withColor(DrossColors.RAT_KING_COMMAND_BUTTON)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, SUMMON_RAT_KING_COMMAND))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("commands.dross.boss.arena.summon_hover", SUMMON_RAT_KING_COMMAND)))));
        source.sendSuccess(() -> message, true);
        return Math.max(1, result.changed());
    }

    /**
     * {@code /dross boss summon huntsman}: the Huntsman appears riding his Alpha Moose {@link #HUNTSMAN_SUMMON_AHEAD}
     * blocks in front of you (or as close as there's room), facing you, calm: hitting either of them starts the hunt (and
     * his horn). Not on Peaceful.
     *
     * @return 1 if he was summoned, 0 if not (the reason is sent as a failure message)
     */
    public static int summonHuntsman(CommandSourceStack source)
    {
        ServerLevel level = source.getLevel();
        if (level.getDifficulty() == Difficulty.PEACEFUL)
        {
            source.sendFailure(Component.translatable("commands.dross.boss.summon_huntsman.peaceful"));
            return 0;
        }
        Vec3 from = source.getPosition();
        float lookYaw = source.getRotation().y;
        Vec3 look = new Vec3(-Mth.sin(lookYaw * Mth.DEG_TO_RAD), 0.0D, Mth.cos(lookYaw * Mth.DEG_TO_RAD));
        Vec3 feet = null;
        int distance = 0;
        for (int d = HUNTSMAN_SUMMON_AHEAD; d >= SUMMON_NEAREST && feet == null; d--)
        {
            feet = standingSpot(level, ModEntities.ALPHA_MOOSE.get(), from.add(look.scale(d)));
            distance = d;
        }
        if (feet == null)
        {
            feet = from;
            distance = 0;
        }
        Huntsman huntsman = HuntsmanPair.spawn(level, feet, lookYaw + 180.0F);
        if (huntsman == null)
        {
            source.sendFailure(Component.translatable("commands.dross.boss.summon_huntsman.failed"));
            return 0;
        }
        int ahead = distance;
        LOGGER.info("Summoned the Huntsman for {} at {} in {}", source.getTextName(), BlockPos.containing(feet).toShortString(),
                level.dimension().location());
        source.sendSuccess(() -> Component.translatable("commands.dross.boss.summon_huntsman.success", ahead), true);
        return 1;
    }

    /**
     * {@code /dross boss arena huntsman}: builds his basic arena ({@link HuntsmanArena}) round you, its floor where you
     * stand: a round floor of snow and packed ice with lantern posts round its edge. You stay in its middle; the chat
     * message has a button that summons him.
     *
     * @return how many blocks were changed (at least 1)
     */
    public static int buildHuntsmanArena(CommandSourceStack source)
    {
        ServerLevel level = source.getLevel();
        BlockPos floor = BossArena.floorUnder(level, BlockPos.containing(source.getPosition()));
        RatKingArena.Result result = HuntsmanArena.build(level, floor);
        LOGGER.info("Built the Huntsman's arena for {} round {} in {} ({} blocks changed, {} block entities left alone)",
                source.getTextName(), floor.toShortString(), level.dimension().location(), result.changed(),
                result.keptBlockEntities());
        int across = 2 * HuntsmanArena.RADIUS + 1;
        MutableComponent message = Component.translatable("commands.dross.boss.arena_huntsman.success", across);
        if (result.keptBlockEntities() > 0)
        {
            message.append(" ").append(Component.translatable("commands.dross.boss.arena.kept", result.keptBlockEntities()));
        }
        message.append(" ").append(Component.translatable("commands.dross.boss.arena_huntsman.summon_button").withStyle(style -> style
                .withColor(DrossColors.HUNTSMAN_COMMAND_BUTTON)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, SUMMON_HUNTSMAN_COMMAND))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("commands.dross.boss.arena.summon_hover", SUMMON_HUNTSMAN_COMMAND)))));
        source.sendSuccess(() -> message, true);
        return Math.max(1, result.changed());
    }

    /**
     * {@code /dross boss arena bedrock}: builds the bedrock test arena ({@link BedrockArena}) round you, its floor where
     * you stand: bedrock walled in and lit with glowstone, where nothing spawns by itself. You stay in its middle; the
     * chat message has a button for each boss.
     *
     * @return how many blocks were changed (at least 1)
     */
    public static int buildBedrockArena(CommandSourceStack source)
    {
        ServerLevel level = source.getLevel();
        BlockPos floor = BossArena.floorUnder(level, BlockPos.containing(source.getPosition()));
        RatKingArena.Result result = BedrockArena.build(level, floor);
        LOGGER.info("Built the bedrock test arena for {} round {} in {} ({} blocks changed, {} block entities left alone)",
                source.getTextName(), floor.toShortString(), level.dimension().location(), result.changed(),
                result.keptBlockEntities());
        MutableComponent message = Component.translatable("commands.dross.boss.arena_bedrock.success", BedrockArena.SIZE);
        if (result.keptBlockEntities() > 0)
        {
            message.append(" ").append(Component.translatable("commands.dross.boss.arena.kept", result.keptBlockEntities()));
        }
        message.append(" ").append(summonButton("commands.dross.boss.arena.summon_button", DrossColors.BOSS_COMMAND_BUTTON,
                SUMMON_COMMAND));
        message.append(" ").append(summonButton("commands.dross.boss.arena_ratking.summon_button",
                DrossColors.RAT_KING_COMMAND_BUTTON, SUMMON_RAT_KING_COMMAND));
        message.append(" ").append(summonButton("commands.dross.boss.arena_huntsman.summon_button",
                DrossColors.HUNTSMAN_COMMAND_BUTTON, SUMMON_HUNTSMAN_COMMAND));
        source.sendSuccess(() -> message, true);
        return Math.max(1, result.changed());
    }

    /** A chat button ({@code key}'s text in {@code color}) that runs {@code command}. */
    private static MutableComponent summonButton(String key, int color, String command)
    {
        return Component.translatable(key).withStyle(style -> style
                .withColor(color)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("commands.dross.boss.arena.summon_hover", command))));
    }

    /** A spot near {@code at} where the Colossus fits, standing on solid ground; null if there's none. */
    @Nullable
    private static Vec3 standingSpot(ServerLevel level, EntityType<?> type, Vec3 at)
    {
        int x = Mth.floor(at.x);
        int z = Mth.floor(at.z);
        int y0 = Mth.floor(at.y);
        for (int y = y0 + GROUND_SEARCH_UP; y >= y0 - GROUND_SEARCH_DOWN; y--)
        {
            BlockPos below = new BlockPos(x, y - 1, z);
            if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP))
            {
                continue;
            }
            Vec3 feet = new Vec3(x + 0.5D, y, z + 0.5D);
            if (level.noCollision(type.getAABB(feet.x, feet.y, feet.z)) && !level.containsAnyLiquid(type.getAABB(feet.x, feet.y, feet.z)))
            {
                return feet;
            }
        }
        return null;
    }
}
