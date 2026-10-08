package com.reg21meme.dross.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.villager.TraderCommands;
import com.reg21meme.dross.world.PortalSite;
import com.reg21meme.dross.world.PortalSiteBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The {@code /dross} command. Owned by world-builder; other areas ask for new subcommands.
 * <ul>
 *   <li>{@code /dross site}: teleports you in front of the Overworld portal site frame, facing it. Needs cheats (level 2).</li>
 *   <li>{@code /dross trader}: teleports you to the Dross trader. {@code /dross trader home}: sends him home.
 *       From the villager area ({@link TraderCommands}). Needs cheats (level 2).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class DrossCommand
{
    /** How far in front of the frame (+Z) the player is put. Must stay inside the cleared area. */
    private static final int STAND_DISTANCE = 3;
    /** Yaw that faces north (towards -Z), i.e. towards the frame. */
    private static final float FACE_NORTH = 180.0F;

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
                // Villager area's test commands (the logic lives in TraderCommands).
                .then(Commands.literal("trader")
                        .requires(source -> source.hasPermission(2))
                        .executes(TraderCommands::teleportToTrader)
                        .then(Commands.literal("home")
                                .executes(TraderCommands::sendTraderHome))));
    }

    private static int teleportToSite(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel overworld = source.getServer().overworld();

        BlockPos frame = PortalSiteBuilder.ensurePlaced(overworld);

        // The opening is the middle two columns (frame.x+1 and frame.x+2), so its centre line is frame.x + 2.
        double x = frame.getX() + PortalSite.FRAME_WIDTH / 2.0;
        double y = frame.getY();
        double z = frame.getZ() + STAND_DISTANCE + 0.5;

        player.teleportTo(overworld, x, y, z, FACE_NORTH, 0.0F);
        source.sendSuccess(() -> Component.literal("Teleported to the Dross portal site (frame at "
                + frame.getX() + ", " + frame.getY() + ", " + frame.getZ() + ")"), true);
        return 1;
    }

    private DrossCommand() {}
}
