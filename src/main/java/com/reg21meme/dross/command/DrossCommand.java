package com.reg21meme.dross.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.BossCommands;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.portal.DrossHub;
import com.reg21meme.dross.quest.QuestCommands;
import com.reg21meme.dross.villager.TraderCommands;
import com.reg21meme.dross.world.PortalSite;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The {@code /dross} command. Owned by world-builder; other areas' logic lives in their own packages. All need cheats (level 2).
 * <p>
 * Names say what kind of thing a command is about and which one, so they stay clear as more bosses and places are
 * added: {@code goto <place>} (with {@code goto biome <name>} for the Dross's biomes and {@code goto lair <boss>} for
 * the bosses' lairs), {@code showcase <kind> [<boss>]}, {@code boss <action> <boss>}, {@code trader <action>},
 * {@code quest <action>}.
 * <ul>
 *   <li>{@code /dross goto cathedral}: teleports you in front of the castle's portal frame (the Fallen Cathedral, or
 *       the small fallback shrine), facing it. Before the site has been built in this world it fails
 *       ("hasn't been placed ... yet") and builds nothing: the site is built in the background a little while after a
 *       new world opens.</li>
 *   <li>{@code /dross goto hub}: teleports you to the hub in the Dross (portal area's {@link DrossHub}).</li>
 *   <li>{@code /dross goto biome silverwood_forest|cinder_wastes|blightwood|rimefall|gravecrags}: teleports
 *       you to the nearest place of that Dross biome, on safe ground and never on or right by the hub: searching from
 *       where you stand if you're in the Dross, otherwise from the hub ({@link GotoBiome}; finding the biome is the
 *       dimension area's {@code DrossBiomes}, safe ground the portal area's {@code DrossTeleporter.findSafeSpot}). It
 *       never lands in or by the Cinder Colossus's lair either.</li>
 *   <li>{@code /dross goto lair colossus}: teleports you to the entrance of the nearest Cinder Colossus lair (one in
 *       every group of 3 Cinder Wastes patches; nearest to you if you're in the Dross, otherwise to the hub), facing into
 *       its cave mouth, and says whether the Colossus will rise when you walk in, is awake, or has been beaten (for good)
 *       ({@link GotoLair}; the lairs are the boss area's {@code ColossusLair}). A lair is only built when someone first
 *       comes near: if the nearest isn't built yet, it says so, builds it, and takes you there once it's ready.</li>
 *   <li>{@code /dross goto lair ratking}: teleports you into the nearest rat cave, the Rat King's home at the end of a
 *       sewer under Blightwood (nearest to you if you're in the Dross, otherwise to the hub), onto its arrival spot at the
 *       end of a tunnel leading in, facing his nest, and says whether he's asleep there, awake, or beaten (for good)
 *       ({@link GotoLair#ratKing}; the caves are the dimension area's {@code RatCaves}, his home the boss area's
 *       {@code RatKingLair}). It fails if no rat cave is found.</li>
 *   <li>{@code /dross goto sewer}: teleports you to the foot of a pipe of the nearest regular sewer under Blightwood
 *       (nearest to you if you're in the Dross, otherwise to the hub), facing the pipe, on safe ground ({@link GotoSewer};
 *       the sewers are the dimension area's {@code Sewers}). It fails if no sewer is found.</li>
 *   <li>{@code /dross goto trader}: teleports you to the Dross trader. {@code /dross trader sendhome}: sends him home.
 *       From the villager area ({@link TraderCommands}).</li>
 *   <li>{@code /dross quest status|reset|complete}: shows, clears or finishes your quest progress.
 *       From the quest area ({@link QuestCommands}).</li>
 *   <li>{@code /dross showcase boss_designs colossus}: spawns one frozen Cinder Colossus per design in a row in front
 *       of you, dormant in their rock shells, each with a numbered sign;
 *       {@code /dross showcase boss_designs colossus dormant|erupt|erupted|break|core|die|walk|idle} switches them
 *       between their three stages and replays the eruption, the shell breaking and the death ({@link GolemShowcase};
 *       the designs are the boss area's {@code GolemDesigns}).</li>
 *   <li>{@code /dross showcase boss_designs ratking}: spawns one frozen Rat King per design in a row in front of
 *       you, each with a numbered sign; {@code /dross showcase boss_designs ratking
 *       sleep|wake|idle|walk|burrow|popup|rage|die} plays one of their animations ({@link RatKingShowcase}; the designs
 *       are the boss area's {@code RatKingDesigns}).</li>
 *   <li>{@code /dross showcase rats}: spawns the Rat King's rats in a row in front of you, each on a pedestal with a
 *       sign: the Plague Rat, then the Plague Ravager (the enraged breed); {@code /dross showcase rats idle|run}
 *       switches their animation ({@link RatShowcase}; the designs are the boss area's {@code RatDesigns}).</li>
 *   <li>{@code /dross showcase boss_sounds ratking}: lists every Rat King and rat sound in chat, each with a button
 *       that plays it ({@link RatSoundShowcase}; the list is the boss area's {@code RatKingSounds}).</li>
 *   <li>{@code /dross showcase trophies colossus}: places the 5 Cinder Colossus trophy designs in a row in front of
 *       you, each on a pedestal facing you with a sign giving its number and name ({@link TrophyShowcase}; the designs
 *       are the boss area's {@code ColossusTrophies}).</li>
 *   <li>{@code /dross showcase trophies ratking}: places the 5 Rat King trophy designs the same way
 *       ({@link TrophyShowcase#placeRatKingTrophies}; the designs are the boss area's {@code RatKingTrophies}).</li>
 *   <li>{@code /dross boss summon colossus}: summons a fighting Cinder Colossus in front of you, facing you (not on
 *       Peaceful). From the boss area ({@link BossCommands#summonColossus}).</li>
 *   <li>{@code /dross boss arena colossus}: builds the placeholder Colossus arena round you, with a chat button that
 *       summons the Colossus. From the boss area ({@link BossCommands#buildArena}; the arena itself is
 *       {@code BossArena}). The button runs {@link BossCommands#SUMMON_COMMAND}, so the {@code boss summon colossus}
 *       path must match it exactly.</li>
 *   <li>{@code /dross boss summon ratking}: summons a fighting Rat King in front of you, asleep, facing you (not on
 *       Peaceful); {@code /dross boss arena ratking} builds his placeholder arena (a round dirt and coarse dirt floor)
 *       round you, with a chat button that summons him. From the boss area ({@link BossCommands#summonRatKing},
 *       {@link BossCommands#buildRatKingArena}). The button runs {@link BossCommands#SUMMON_RAT_KING_COMMAND}, so the
 *       {@code boss summon ratking} path must match it exactly.</li>
 * </ul>
 * A future boss adds one literal under {@code showcase boss_designs}, {@code showcase boss_sounds},
 * {@code showcase trophies}, {@code boss summon}
 * and {@code boss arena} (and, once it has a lair, under {@code goto lair}); a future place adds one under
 * {@code goto}. A future Dross biome gets its {@code goto biome <name>} by itself: there's one literal per biome in
 * {@code DrossBiomes.ALL}, named by {@code DrossBiomes.name} (its id without {@code dross:}). Plain
 * {@code goto biome} and {@code goto lair} aren't commands.
 * <p>
 * The boss-design, rat and trophy showcases and the boss commands use the command's position, rotation and
 * dimension, so they also work from the server console and command blocks (for example {@code execute in
 * minecraft:overworld positioned 0 -60 0 rotated 0 0 run dross showcase trophies colossus}).
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class DrossCommand
{
    /** What {@code /dross goto cathedral} says (as a failure) before the site has been built in this world. */
    public static final String SITE_NOT_PLACED = "The Dross portal site hasn't been placed in this world yet.";
    /** How far in front of (or behind) the frame the player is put, best first. */
    private static final int[] STAND_DISTANCES = {3, 2, 4, 1, 5};
    /** Height steps tried at each spot (relative to the frame's bottom row), best first. */
    private static final int[] STAND_HEIGHTS = {0, 1, -1, 2, -2};

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event)
    {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(Commands.literal("dross")
                // Teleports to places: goto <place>.
                .then(Commands.literal("goto")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("cathedral").executes(DrossCommand::teleportToSite))
                        // Portal area's hub (the logic lives in DrossHub).
                        .then(Commands.literal("hub").executes(DrossCommand::teleportToHub))
                        // Villager area's trader (the logic lives in TraderCommands).
                        .then(Commands.literal("trader").executes(TraderCommands::teleportToTrader))
                        // The Dross's biomes: goto biome <name>, one literal per biome (the logic lives in GotoBiome).
                        .then(biomeGoto())
                        // The bosses' lairs: goto lair <boss>, one literal per boss with a lair (the logic lives in
                        // GotoLair; the lairs themselves are the boss area's ColossusLair and RatKingLair).
                        .then(Commands.literal("lair")
                                .then(Commands.literal("colossus").executes(context -> GotoLair.colossus(context.getSource())))
                                .then(Commands.literal("ratking").executes(context -> GotoLair.ratKing(context.getSource()))))
                        // The nearest regular sewer under Blightwood (the logic lives in GotoSewer; the sewers are the
                        // dimension area's).
                        .then(Commands.literal("sewer").executes(context -> GotoSewer.teleport(context.getSource()))))
                // Villager area's test commands: trader <action> (the logic lives in TraderCommands).
                .then(Commands.literal("trader")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("sendhome").executes(TraderCommands::sendTraderHome)))
                // Quest area's test commands: quest <action> (the logic lives in QuestCommands).
                .then(Commands.literal("quest")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("status").executes(QuestCommands::status))
                        .then(Commands.literal("reset").executes(QuestCommands::reset))
                        .then(Commands.literal("complete").executes(QuestCommands::complete)))
                // Test showcases that build things side by side to compare them: showcase <kind> [<boss>]
                // (the logic lives in GolemShowcase, RatKingShowcase, RatShowcase, RatSoundShowcase and TrophyShowcase).
                .then(Commands.literal("showcase")
                        .requires(source -> source.hasPermission(2))
                        // Boss area's designs, one literal per boss.
                        .then(Commands.literal("boss_designs")
                                .then(colossusDesignShowcase())
                                .then(ratKingDesignShowcase()))
                        // Boss area's Rat King's rats (regular and enraged), with their controls (RatShowcase).
                        .then(ratShowcase())
                        // Boss area's sounds, one literal per boss (RatSoundShowcase).
                        .then(Commands.literal("boss_sounds")
                                .then(Commands.literal("ratking").executes(RatSoundShowcase::list)))
                        // Boss area's trophies, one literal per boss.
                        .then(Commands.literal("trophies")
                                .then(Commands.literal("colossus").executes(TrophyShowcase::placeColossusTrophies))
                                .then(Commands.literal("ratking").executes(TrophyShowcase::placeRatKingTrophies))))
                // Boss area's test commands: boss <action> <boss> (the logic lives in BossCommands). The arena's chat
                // buttons run BossCommands.SUMMON_COMMAND ("/dross boss summon colossus") and SUMMON_RAT_KING_COMMAND
                // ("/dross boss summon ratking"), so keep those paths exactly as they are.
                .then(Commands.literal("boss")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("summon")
                                .then(Commands.literal("colossus")
                                        .executes(context -> BossCommands.summonColossus(context.getSource())))
                                .then(Commands.literal("ratking")
                                        .executes(context -> BossCommands.summonRatKing(context.getSource()))))
                        .then(Commands.literal("arena")
                                .then(Commands.literal("colossus")
                                        .executes(context -> BossCommands.buildArena(context.getSource())))
                                .then(Commands.literal("ratking")
                                        .executes(context -> BossCommands.buildRatKingArena(context.getSource()))))));
    }

    /**
     * {@code /dross goto biome <name>}: one literal per Dross biome ({@code silverwood_forest}, {@code cinder_wastes},
     * {@code blightwood}, {@code rimefall}, {@code gravecrags}), so they tab-complete. Plain
     * {@code /dross goto biome} isn't a command.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> biomeGoto()
    {
        LiteralArgumentBuilder<CommandSourceStack> biome = Commands.literal("biome");
        for (ResourceKey<Biome> key : DrossBiomes.ALL)
        {
            biome.then(Commands.literal(DrossBiomes.name(key)).executes(context -> GotoBiome.teleport(context.getSource(), key)));
        }
        return biome;
    }

    /**
     * {@code /dross showcase boss_designs colossus} and its controls ({@code dormant}, {@code erupt}, {@code break},
     * {@code die}...), one per {@link GolemShowcase.Control}.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> colossusDesignShowcase()
    {
        LiteralArgumentBuilder<CommandSourceStack> colossus = Commands.literal("colossus").executes(GolemShowcase::spawnGolems);
        for (GolemShowcase.Control control : GolemShowcase.Control.values())
        {
            colossus.then(Commands.literal(control.word()).executes(context -> GolemShowcase.control(context, control)));
        }
        return colossus;
    }

    /**
     * {@code /dross showcase boss_designs ratking} and its controls ({@code sleep}, {@code wake}, {@code rage},
     * {@code die}...), one per {@link RatKingShowcase.Control}.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> ratKingDesignShowcase()
    {
        LiteralArgumentBuilder<CommandSourceStack> ratking = Commands.literal("ratking").executes(RatKingShowcase::spawnKings);
        for (RatKingShowcase.Control control : RatKingShowcase.Control.values())
        {
            ratking.then(Commands.literal(control.word()).executes(context -> RatKingShowcase.control(context, control)));
        }
        return ratking;
    }

    /** {@code /dross showcase rats} and its controls ({@code idle}, {@code run}), one per {@link RatShowcase.Control}. */
    private static LiteralArgumentBuilder<CommandSourceStack> ratShowcase()
    {
        LiteralArgumentBuilder<CommandSourceStack> rats = Commands.literal("rats").executes(RatShowcase::spawnRats);
        for (RatShowcase.Control control : RatShowcase.Control.values())
        {
            rats.then(Commands.literal(control.word()).executes(context -> RatShowcase.control(context, control)));
        }
        return rats;
    }

    private static int teleportToHub(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        if (!DrossHub.teleportToHub(player))
        {
            source.sendFailure(Component.literal("The Dross dimension isn't loaded, so there's no hub to go to."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Teleported to the Dross hub"), true);
        return 1;
    }

    /**
     * Puts the player in front of the frame's opening, facing it. Tries the front side (+Z, or +X for a frame along Z)
     * first, then the back, at a few distances and heights, and uses the first spot with room to stand and solid
     * ground underneath. If there's none, the player stands inside the (unlit) opening.
     */
    private static int teleportToSite(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        CommandSourceStack source = context.getSource();
        ServerLevel overworld = source.getServer().overworld();
        if (!PortalSite.isPlaced(overworld))
        {
            // Never build it from here: it's built in the background a little while after a new world opens (or right
            // away when someone leaves the Dross), and everyone is told where it is then.
            source.sendFailure(Component.literal(SITE_NOT_PLACED));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();

        BlockPos frame = PortalSite.getFramePos(overworld);
        Direction.Axis axis = PortalSite.getFrameAxis(overworld);
        boolean alongX = axis == Direction.Axis.X;
        // The opening starts one block along the frame from the corner; its middle is half its width further.
        double along = (alongX ? frame.getX() : frame.getZ()) + 1 + PortalSite.getOpeningWidth(overworld) / 2.0D;
        double across = (alongX ? frame.getZ() : frame.getX()) + 0.5D;

        Vec3 spot = null;
        float yaw = 0.0F;
        search:
        for (int distance : STAND_DISTANCES)
        {
            for (int side : new int[] {1, -1})
            {
                for (int dy : STAND_HEIGHTS)
                {
                    double a = across + side * distance;
                    Vec3 pos = alongX ? new Vec3(along, frame.getY() + dy, a) : new Vec3(a, frame.getY() + dy, along);
                    if (canStand(overworld, player, pos))
                    {
                        spot = pos;
                        yaw = facingFrame(axis, side);
                        break search;
                    }
                }
            }
        }
        if (spot == null)
        {
            // No room around it: stand inside the opening, on the frame's bottom row.
            spot = alongX ? new Vec3(along, frame.getY() + 1, across) : new Vec3(across, frame.getY() + 1, along);
            yaw = facingFrame(axis, 1);
        }

        player.teleportTo(overworld, spot.x, spot.y, spot.z, yaw, 0.0F);
        source.sendSuccess(() -> Component.literal("Teleported to the Dross portal site (frame at "
                + frame.getX() + ", " + frame.getY() + ", " + frame.getZ() + ", along " + axis.getName().toUpperCase() + ")"), true);
        return 1;
    }

    /**
     * The yaw that looks at the frame from the given side.
     * Minecraft yaw: 0 = south (+Z), 90 = west (-X), 180 = north (-Z), -90 = east (+X).
     */
    private static float facingFrame(Direction.Axis axis, int side)
    {
        if (axis == Direction.Axis.X)
        {
            return side > 0 ? 180.0F : 0.0F; // on the +Z side look north, on the -Z side look south
        }
        return side > 0 ? 90.0F : -90.0F; // on the +X side look west, on the -X side look east
    }

    /** Room for a standing player, no liquid, and solid ground right under the feet. */
    private static boolean canStand(ServerLevel level, ServerPlayer player, Vec3 pos)
    {
        AABB box = player.getDimensions(Pose.STANDING).makeBoundingBox(pos);
        if (!level.noCollision(box) || level.containsAnyLiquid(box))
        {
            return false;
        }
        AABB floor = new AABB(box.minX, box.minY - 0.5D, box.minZ, box.maxX, box.minY, box.maxZ);
        return !level.noCollision(floor);
    }

    private DrossCommand() {}
}
