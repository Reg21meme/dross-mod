package com.reg21meme.dross.boss;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.golem.CinderColossus;
import com.reg21meme.dross.boss.huntsman.Huntsman;
import com.reg21meme.dross.boss.huntsman.HuntsmanPair;
import com.reg21meme.dross.boss.rage.Rage;
import com.reg21meme.dross.boss.rage.RageTeam;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The boss area's test commands. The {@code /dross} command itself belongs to world-builder, which calls these:
 * <ul>
 *   <li>{@code /dross boss summon colossus}: {@link #summonColossus};</li>
 *   <li>{@code /dross boss arena colossus}: {@link #buildArena};</li>
 *   <li>{@code /dross boss summon ratking}: {@link #summonRatKing};</li>
 *   <li>{@code /dross boss arena ratking}: {@link #buildRatKingArena};</li>
 *   <li>{@code /dross boss arena bedrock [teams]}: {@link #buildBedrockArena} (any boss: a spawn-proof, roofed test arena,
 *       divided into a section for each team); {@code ... bedrock open|close}: {@link #bedrockWalls} (the walls between
 *       its sections); {@code ... bedrock team_up}: {@link #bedrockTeamUp} (everyone in a section onto its team).</li>
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

    /** The command that opens (or closes) the walls between the bedrock arena's sections, and the one that puts
     *  everyone in each section on its team (the arena's chat buttons run them). */
    public static final String BEDROCK_OPEN_COMMAND = "/dross boss arena bedrock open";
    public static final String BEDROCK_CLOSE_COMMAND = "/dross boss arena bedrock close";
    public static final String BEDROCK_TEAM_UP_COMMAND = "/dross boss arena bedrock team_up";
    /** The open, close and team-up buttons find the arena you're in, or the nearest within this many blocks. */
    private static final double BEDROCK_FIND_RANGE = 160.0D;

    /**
     * {@code /dross boss arena bedrock [teams]}: builds the bedrock test arena round you ({@link BedrockArena}: roofed,
     * nothing spawns in it), divided into {@code teams} sections (1: none) by walls, each with its team's glass in the
     * middle of its floor. Its chat message has a button for each section (it takes you to its middle), buttons to open
     * and close the walls between them and to put everyone in each section on its team, and a summon button for each
     * boss.
     *
     * @return how many blocks it changed (at least 1)
     */
    public static int buildBedrockArena(CommandSourceStack source, int teams)
    {
        ServerLevel level = source.getLevel();
        BlockPos floor = BossArena.floorUnder(level, BlockPos.containing(source.getPosition()));
        RatKingArena.Result result = BedrockArena.build(level, floor, teams);
        BedrockArenaData.get(level).add(new BedrockArenaData.Arena(floor, teams));
        LOGGER.info("Built the bedrock test arena for {} round {} in {} for {} team(s) ({} blocks changed, {} block entities "
                + "left alone)", source.getTextName(), floor.toShortString(), level.dimension().location(), teams,
                result.changed(), result.keptBlockEntities());
        MutableComponent message = Component.translatable("commands.dross.boss.arena_bedrock.success", BedrockArena.SIZE,
                BedrockArena.ROOF_HEIGHT);
        if (result.keptBlockEntities() > 0)
        {
            message.append(" ").append(Component.translatable("commands.dross.boss.arena.kept", result.keptBlockEntities()));
        }
        List<BedrockArena.Section> sections = BedrockArena.sections(teams);
        if (sections.size() > 1)
        {
            message.append("\n").append(Component.translatable("commands.dross.boss.arena_bedrock.sections", sections.size()));
            for (BedrockArena.Section s : sections)
            {
                String go = "/tp @s " + (floor.getX() + s.middle()) + " " + (floor.getY() + 1) + " " + floor.getZ();
                message.append(" ").append(Component.literal("[").append(s.team().displayName()).append("]")
                        .withStyle(style -> style.withColor(s.team().color())
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, go))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.translatable("commands.dross.boss.arena_bedrock.go_hover",
                                                s.team().displayName())))));
            }
            message.append("\n").append(summonButton("commands.dross.boss.arena_bedrock.team_up_button",
                    DrossColors.BEDROCK_ARENA_BUTTON, BEDROCK_TEAM_UP_COMMAND));
            message.append(" ").append(summonButton("commands.dross.boss.arena_bedrock.open_button",
                    DrossColors.BEDROCK_ARENA_BUTTON, BEDROCK_OPEN_COMMAND));
            message.append(" ").append(summonButton("commands.dross.boss.arena_bedrock.close_button",
                    DrossColors.BEDROCK_ARENA_BUTTON, BEDROCK_CLOSE_COMMAND));
        }
        message.append("\n").append(summonButton("commands.dross.boss.arena.summon_button", DrossColors.BOSS_COMMAND_BUTTON,
                SUMMON_COMMAND));
        message.append(" ").append(summonButton("commands.dross.boss.arena_ratking.summon_button",
                DrossColors.RAT_KING_COMMAND_BUTTON, SUMMON_RAT_KING_COMMAND));
        message.append(" ").append(summonButton("commands.dross.boss.arena_huntsman.summon_button",
                DrossColors.HUNTSMAN_COMMAND_BUTTON, SUMMON_HUNTSMAN_COMMAND));
        message.append(" ").append(summonButton("commands.dross.boss.arena_emberdactyl.summon_button",
                DrossColors.EMBERDACTYL_COMMAND_BUTTON, com.reg21meme.dross.boss.emberdactyl.EmberCommands.SUMMON_COMMAND));
        source.sendSuccess(() -> message, true);
        return Math.max(1, result.changed());
    }

    /** The bedrock arena the command is run in (or the nearest), or null with a failure message sent. */
    @Nullable
    private static BedrockArenaData.Arena bedrockArena(CommandSourceStack source)
    {
        BedrockArenaData.Arena arena = BedrockArenaData.get(source.getLevel()).find(BlockPos.containing(source.getPosition()),
                BEDROCK_FIND_RANGE);
        if (arena == null)
        {
            source.sendFailure(Component.translatable("commands.dross.boss.arena_bedrock.none"));
            return null;
        }
        if (arena.teams() < 2)
        {
            source.sendFailure(Component.translatable("commands.dross.boss.arena_bedrock.one_team"));
            return null;
        }
        return arena;
    }

    /**
     * {@code /dross boss arena bedrock open|close}: opens the walls between the bedrock arena's sections (the teams meet)
     * or puts them back.
     *
     * @return how many blocks changed (at least 1), or 0 if there's no arena with walls here
     */
    public static int bedrockWalls(CommandSourceStack source, boolean open)
    {
        BedrockArenaData.Arena arena = bedrockArena(source);
        if (arena == null)
        {
            return 0;
        }
        int changed = BedrockArena.setDividers(source.getLevel(), arena.floor(), arena.teams(), open);
        source.sendSuccess(() -> Component.translatable(open ? "commands.dross.boss.arena_bedrock.opened"
                : "commands.dross.boss.arena_bedrock.closed", arena.teams()), true);
        return Math.max(1, changed);
    }

    /**
     * {@code /dross boss arena bedrock team_up}: every mob and every player (not a spectator) in each of the bedrock
     * arena's sections goes onto that section's team (mobs raged as by the Rage Wand, {@link Rage#enrage}; players by
     * {@link Rage#setPlayerTeam}). Mobs the wand can't rage are left as they are.
     *
     * @return how many went onto a team, or 0
     */
    public static int bedrockTeamUp(CommandSourceStack source)
    {
        BedrockArenaData.Arena arena = bedrockArena(source);
        if (arena == null)
        {
            return 0;
        }
        ServerLevel level = source.getLevel();
        BlockPos floor = arena.floor();
        Map<RageTeam, Integer> counts = new EnumMap<>(RageTeam.class);
        int total = 0;
        for (BedrockArena.Section s : BedrockArena.sections(arena.teams()))
        {
            AABB box = new AABB(floor.getX() + s.from(), floor.getY() + 1, floor.getZ() - BedrockArena.RADIUS,
                    floor.getX() + s.to() + 1, floor.getY() + BedrockArena.ROOF_HEIGHT, floor.getZ() + BedrockArena.RADIUS + 1);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                    e -> e.isAlive() && !e.isSpectator() && box.contains(e.position())))
            {
                boolean joined = false;
                if (e instanceof Player player)
                {
                    Rage.setPlayerTeam(player, s.team());
                    joined = true;
                }
                else if (e instanceof Mob mob)
                {
                    joined = Rage.enrage(mob, s.team());
                }
                if (joined)
                {
                    counts.merge(s.team(), 1, Integer::sum);
                    total++;
                }
            }
        }
        if (total == 0)
        {
            source.sendFailure(Component.translatable("commands.dross.boss.arena_bedrock.nobody"));
            return 0;
        }
        MutableComponent list = Component.empty();
        boolean first = true;
        for (Map.Entry<RageTeam, Integer> e : counts.entrySet())
        {
            if (!first)
            {
                list.append(", ");
            }
            first = false;
            list.append(e.getKey().displayName()).append(" " + e.getValue());
        }
        int n = total;
        source.sendSuccess(() -> Component.translatable("commands.dross.boss.arena_bedrock.teamed", n, list), true);
        return total;
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
