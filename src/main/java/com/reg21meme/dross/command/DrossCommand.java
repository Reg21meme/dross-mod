package com.reg21meme.dross.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.portal.DrossHub;
import com.reg21meme.dross.quest.QuestCommands;
import com.reg21meme.dross.villager.TraderCommands;
import com.reg21meme.dross.world.PortalSite;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The {@code /dross} command. Owned by world-builder; other areas' logic lives in their own packages. All need cheats (level 2).
 * <ul>
 *   <li>{@code /dross site}: teleports you in front of the castle's portal frame, facing it. Before the site has been
 *       built in this world it fails ("hasn't been placed ... yet") and builds nothing: the site is built in the
 *       background a little while after a new world opens (old worlds' bare frame or small shrine count as built).</li>
 *   <li>{@code /dross hub}: teleports you to the hub in the Dross (portal area's {@link DrossHub}).</li>
 *   <li>{@code /dross trader}: teleports you to the Dross trader. {@code /dross trader home}: sends him home.
 *       From the villager area ({@link TraderCommands}).</li>
 *   <li>{@code /dross quest status|reset|complete}: shows, clears or finishes your quest progress.
 *       From the quest area ({@link QuestCommands}).</li>
 *   <li>{@code /dross showcase huts}: builds every candidate design for the trader's hut in a row in front of you,
 *       each with a numbered sign, to compare them ({@link HutShowcase}; the designs are the villager area's).</li>
 *   <li>{@code /dross showcase shrines}: builds the 10 candidate designs for a grander portal shrine in a row to your
 *       right (about 1,000 blocks long, so use a superflat world), one at a time from the server tick, each with a
 *       numbered sign. A chat line appears as each one is finished; click it to teleport in front of that shrine
 *       ({@link ShrineShowcase}; the designs are in {@code world.shrine}).</li>
 *   <li>{@code /dross showcase skins}: spawns one frozen trader per candidate skin in a row in front of you, each on
 *       a pedestal with a numbered sign; old showcase traders nearby are removed first, never the real trader
 *       ({@link SkinShowcase}; the skins are the villager area's {@code TraderSkins}).</li>
 * </ul>
 * The shrines and skins showcases use the command's position, rotation and dimension, so they also work from the server
 * console and command blocks (for example {@code execute in minecraft:overworld positioned 0 -60 0 rotated 0 0 run dross
 * showcase skins}). The huts showcase needs a player.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class DrossCommand
{
    /** What {@code /dross site} says (as a failure) before the site has been built in this world. */
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
                .then(Commands.literal("site")
                        .requires(source -> source.hasPermission(2))
                        .executes(DrossCommand::teleportToSite))
                // Portal area's hub (the logic lives in DrossHub).
                .then(Commands.literal("hub")
                        .requires(source -> source.hasPermission(2))
                        .executes(DrossCommand::teleportToHub))
                // Villager area's test commands (the logic lives in TraderCommands).
                .then(Commands.literal("trader")
                        .requires(source -> source.hasPermission(2))
                        .executes(TraderCommands::teleportToTrader)
                        .then(Commands.literal("home")
                                .executes(TraderCommands::sendTraderHome)))
                // Quest area's test commands (the logic lives in QuestCommands).
                .then(Commands.literal("quest")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("status").executes(QuestCommands::status))
                        .then(Commands.literal("reset").executes(QuestCommands::reset))
                        .then(Commands.literal("complete").executes(QuestCommands::complete)))
                // Test showcases that build things side by side to compare them
                // (the logic lives in HutShowcase, ShrineShowcase and SkinShowcase).
                .then(Commands.literal("showcase")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("huts").executes(HutShowcase::buildHuts))
                        .then(Commands.literal("shrines").executes(ShrineShowcase::buildShrines))
                        .then(Commands.literal("skins").executes(SkinShowcase::spawnSkins))));
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
