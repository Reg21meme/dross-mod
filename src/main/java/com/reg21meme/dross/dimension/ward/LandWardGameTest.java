package com.reg21meme.dross.dimension.ward;

import com.mojang.authlib.GameProfile;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.quest.oracle.OracleProgress;
import com.reg21meme.dross.quest.oracle.OracleQuests;
import com.reg21meme.dross.world.PortalSiteGameTest;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Headless tests (GameTest) for the land wards ({@link LandWard}; QUEST_PLAN.md, "Locked lands"), with stand-in players
 * in the Dross:
 * <ul>
 *   <li>a survival player on quest 0 standing in the Cinder Wastes gets Slowness V, Mining Fatigue V and Hunger II (particles hidden)
 *       and the chat line once, and again after 20 seconds; one in creative there, one in the Silverwood Forest and one
 *       in the Overworld get nothing;</li>
 *   <li>high over the land or deep under it, it's the same (the column decides); switching to creative lifts it at once
 *       and coming back in within 5 seconds doesn't say it again;</li>
 *   <li>once the quest opens the Cinder Wastes the ward lets go of them;</li>
 *   <li>the land of a column is its surface biome at every height (the sewers under Blightwood read as Blightwood).</li>
 * </ul>
 */
@GameTestHolder(LandWardGameTest.NAMESPACE)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class LandWardGameTest
{
    /**
     * The tests' GameTest namespace: the mod's, so the normal run has them. To run them alone, set it to one of their own
     * (say "dross_ward") and run {@code .\gradlew.bat runGameTestServer -PgameTestNamespaces=dross_ward
     * -PgameTestWorld=<a new folder>}: their empty template is then made under that namespace too ({@link #onServerStarted}).
     */
    static final String NAMESPACE = Dross.MODID;
    private static final String BATCH = "dross_land_ward";
    /** The tick limit: far past the waits (each a few checks), and the Dross's chunks loading round the stand-ins. */
    private static final int TIMEOUT_TICKS = 3000;
    /** A locked column is looked for this far round where the biome search landed (blocks), every few blocks. */
    private static final int COLUMN_SEARCH = 48;
    /** The Silverwood column is looked for back towards the hub from the Cinder Wastes, at most this far (blocks). */
    private static final int SILVERWOOD_SEARCH = 2000;
    /** How high over the land and how deep under it the "column" checks stand the player (Y). */
    private static final int HIGH_Y = 300;
    private static final int DEEP_Y = -40;

    private LandWardGameTest() {}

    /** The empty template, made under this class's own namespace when it isn't the mod's (the mod's is the site tests'). */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        if (!ForgeGameTestHooks.isGametestEnabled() || NAMESPACE.equals(Dross.MODID))
        {
            return;
        }
        StructureTemplate template = event.getServer().getStructureManager()
                .getOrCreate(new ResourceLocation(NAMESPACE, PortalSiteGameTest.EMPTY));
        CompoundTag tag = new CompoundTag();
        tag.put("size", ints(1, 1, 1));
        ListTag palette = new ListTag();
        palette.add(NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));
        tag.put("palette", palette);
        ListTag blocks = new ListTag();
        CompoundTag block = new CompoundTag();
        block.put("pos", ints(0, 0, 0));
        block.putInt("state", 0);
        blocks.add(block);
        tag.put("blocks", blocks);
        tag.put("entities", new ListTag());
        template.load(BuiltInRegistries.BLOCK.asLookup(), tag);
    }

    private static ListTag ints(int... values)
    {
        ListTag list = new ListTag();
        for (int value : values)
        {
            list.add(IntTag.valueOf(value));
        }
        return list;
    }

    // ------------------------------------------------------------------ Helpers

    private static void expect(boolean condition, String message)
    {
        if (!condition)
        {
            throw new GameTestAssertException(message);
        }
    }

    /** A column whose land (as the ward reads it) is {@code land}, near the biome search's find; null if none. */
    private static BlockPos columnOf(ServerLevel dross, ResourceKey<Biome> land)
    {
        Optional<BlockPos> found = DrossBiomes.findNearest(dross, new BlockPos(0, LandWard.COLUMN_BIOME_Y, 0), land);
        if (found.isEmpty())
        {
            return null;
        }
        BlockPos at = found.get();
        for (int r = 0; r <= COLUMN_SEARCH; r += 4)
        {
            for (int dx = -r; dx <= r; dx += 4)
            {
                for (int dz = -r; dz <= r; dz += 4)
                {
                    if ((Math.abs(dx) == r || Math.abs(dz) == r) && land.equals(LandWard.landAt(dross, at.getX() + dx, at.getZ() + dz)))
                    {
                        return new BlockPos(at.getX() + dx, 0, at.getZ() + dz);
                    }
                }
            }
        }
        return null;
    }

    /** A Silverwood column on the way from {@code from} back to the hub (so the stand-ins' land is near each other). */
    private static BlockPos silverwoodTowardsHub(ServerLevel dross, BlockPos from)
    {
        double length = Math.sqrt((double) from.getX() * from.getX() + (double) from.getZ() * from.getZ());
        for (int step = 16; step <= Math.min(SILVERWOOD_SEARCH, length); step += 8)
        {
            int x = (int) Math.round(from.getX() - from.getX() / length * step);
            int z = (int) Math.round(from.getZ() - from.getZ() / length * step);
            if (DrossBiomes.SILVERWOOD_FOREST.equals(LandWard.landAt(dross, x, z)))
            {
                return new BlockPos(x, 0, z);
            }
        }
        return new BlockPos(0, 0, 0); // the hub's own forest
    }

    /** Feet on the land at this column (from the generator's heights: no chunk is made for it). */
    private static Vec3 feetAt(ServerLevel dross, BlockPos column)
    {
        int y = dross.getChunkSource().getGenerator().getBaseHeight(column.getX(), column.getZ(), Heightmap.Types.WORLD_SURFACE_WG,
                dross, dross.getChunkSource().randomState());
        return new Vec3(column.getX() + 0.5D, y + 1.0D, column.getZ() + 0.5D);
    }

    /**
     * The stand-ins and the chat lines the ward has sent each of them. Each gets an in-memory network channel (like the
     * other areas' stand-ins); what the server sends him piles up in it and is read here. They aren't ticked, and
     * they're made invulnerable so no mob of the Dross gets in the way.
     */
    private static final class Run
    {
        final GameTestHelper helper;
        final MinecraftServer server;
        final ServerLevel dross;
        final List<ServerPlayer> players = new ArrayList<>();
        final Map<ServerPlayer, EmbeddedChannel> channels = new HashMap<>();
        final Map<ServerPlayer, Integer> lines = new HashMap<>();

        Run(GameTestHelper helper)
        {
            this.helper = helper;
            this.server = helper.getLevel().getServer();
            this.dross = server.getLevel(ModDimensions.DROSS_LEVEL);
            expect(dross != null, "there's no Dross level");
        }

        /** A stand-in joining at the test's spot in the Overworld, then (if {@code feet} isn't null) moved into the Dross. */
        ServerPlayer join(String name, GameType mode, Vec3 feet)
        {
            ServerLevel here = helper.getLevel();
            ServerPlayer player = new ServerPlayer(server, here, new GameProfile(UUID.randomUUID(), name));
            Vec3 start = helper.absoluteVec(new Vec3(0.5D, 1.0D, 0.5D));
            player.moveTo(start.x, start.y, start.z, 0.0F, 0.0F);
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            EmbeddedChannel channel = new EmbeddedChannel(connection);
            server.getPlayerList().placeNewPlayer(connection, player);
            players.add(player);
            channels.put(player, channel);
            lines.put(player, 0);
            setMode(player, mode);
            if (feet != null)
            {
                player.teleportTo(dross, feet.x, feet.y, feet.z, 0.0F, 0.0F);
            }
            readLines();
            return player;
        }

        void setMode(ServerPlayer player, GameType mode)
        {
            player.setGameMode(mode);
            player.getAbilities().invulnerable = true; // (setGameMode resets it)
        }

        /** Reads what each stand-in has been sent since the last read, counting the ward's chat lines. */
        void readLines()
        {
            for (ServerPlayer player : players)
            {
                EmbeddedChannel channel = channels.get(player);
                Object message;
                while ((message = channel.readOutbound()) != null)
                {
                    if (message instanceof ClientboundSystemChatPacket chat && chat.content().getContents() instanceof TranslatableContents text
                            && text.getKey().equals(LandWard.MESSAGE_KEY))
                    {
                        lines.merge(player, 1, Integer::sum);
                    }
                }
            }
        }

        int linesOf(ServerPlayer player)
        {
            readLines();
            return lines.get(player);
        }

        void leave()
        {
            for (ServerPlayer player : players)
            {
                if (server.getPlayerList().getPlayer(player.getUUID()) == player)
                {
                    server.getPlayerList().remove(player);
                }
            }
            for (EmbeddedChannel channel : channels.values())
            {
                channel.releaseOutbound();
            }
        }

        /** Runs a step; anything it throws logs the stand-ins out first. */
        Runnable step(Runnable body)
        {
            return () -> {
                try
                {
                    body.run();
                }
                catch (RuntimeException e)
                {
                    leave();
                    throw e;
                }
            };
        }

        /** A step to wait on: it's tried every tick until it passes; while it doesn't, the stand-ins' mail is still read. */
        Runnable until(Runnable check)
        {
            return () -> {
                readLines();
                check.run();
            };
        }
    }

    /** True if the player has all three of the ward's effects (its levels, no particles). */
    private static boolean hasWard(ServerPlayer player)
    {
        return LandWard.isWardEffect(player.getEffect(MobEffects.MOVEMENT_SLOWDOWN), LandWard.SLOWNESS_AMPLIFIER)
                && LandWard.isWardEffect(player.getEffect(MobEffects.DIG_SLOWDOWN), LandWard.MINING_FATIGUE_AMPLIFIER)
                && LandWard.isWardEffect(player.getEffect(MobEffects.HUNGER), LandWard.HUNGER_AMPLIFIER);
    }

    /** True if the player has none of Slowness, Mining Fatigue or Hunger at all. */
    private static boolean hasNoWard(ServerPlayer player)
    {
        return !player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && !player.hasEffect(MobEffects.DIG_SLOWDOWN)
                && !player.hasEffect(MobEffects.HUNGER) && !LandWard.isWarded(player);
    }

    private static String name(ServerPlayer player)
    {
        return player.getGameProfile().getName();
    }

    /** The test's stand-ins. */
    private static final class Cast
    {
        Vec3 cinder;
        ServerPlayer walker;
        ServerPlayer builder;
        ServerPlayer forester;
        ServerPlayer traveller;

        List<ServerPlayer> untouched()
        {
            return List.of(builder, forester, traveller);
        }
    }

    // ------------------------------------------------------------------ The tests

    /**
     * The ward over a day in the Cinder Wastes (locked on quest 0): it takes a survival player as he comes in (Slowness
     * V, Mining Fatigue V, Hunger II, the chat line once), says it again after 20 seconds, holds high over the land and deep under it,
     * lets go in creative and takes him back without another line so soon, and lets go for good once his quest opens
     * the land. A creative player beside him, a survival player in Silverwood and one in the Overworld get nothing.
     */
    @GameTest(template = PortalSiteGameTest.EMPTY, batch = BATCH, timeoutTicks = TIMEOUT_TICKS)
    public static void lockedLandSlowsAndWarns(GameTestHelper helper)
    {
        Run run = new Run(helper);
        Cast cast = new Cast();
        helper.startSequence()
                .thenExecute(run.step(() -> {
                    BlockPos cinder = columnOf(run.dross, DrossBiomes.CINDER_WASTES);
                    expect(cinder != null, "no Cinder Wastes column was found near the hub");
                    BlockPos forest = silverwoodTowardsHub(run.dross, cinder);
                    cast.cinder = feetAt(run.dross, cinder);
                    cast.walker = run.join("ward_walker", GameType.SURVIVAL, cast.cinder);
                    cast.builder = run.join("ward_builder", GameType.CREATIVE, cast.cinder.add(2.0D, 0.0D, 0.0D));
                    cast.forester = run.join("ward_forester", GameType.SURVIVAL, feetAt(run.dross, forest));
                    cast.traveller = run.join("ward_traveller", GameType.SURVIVAL, null);
                    expect(OracleProgress.current(cast.walker) == OracleQuests.first(), name(cast.walker) + " should start on the first quest");
                    expect(DrossBiomes.CINDER_WASTES.equals(LandWard.lockedLandOf(cast.walker)), name(cast.walker)
                            + " should stand in the Cinder Wastes, locked for him; the ward reads " + LandWard.lockedLandOf(cast.walker)
                            + " at " + cast.walker.blockPosition() + " in " + cast.walker.level().dimension().location());
                    expect(LandWard.lockedLandOf(cast.forester) == null, name(cast.forester) + " in Silverwood ("
                            + LandWard.landAt(run.dross, cast.forester.getBlockX(), cast.forester.getBlockZ()) + ") shouldn't be in a locked land");
                }))
                .thenWaitUntil(run.until(() -> expect(hasWard(cast.walker), name(cast.walker)
                        + " in the Cinder Wastes on quest 0 should get Slowness V, Mining Fatigue V and Hunger II with hidden particles")))
                .thenExecute(run.step(() -> {
                    expect(LandWard.isWarded(cast.walker), name(cast.walker) + " should be warded");
                    int told = run.linesOf(cast.walker);
                    expect(told == 1, name(cast.walker) + " should have been told once as he came in, but was told " + told + " times");
                    for (ServerPlayer other : cast.untouched())
                    {
                        expect(hasNoWard(other), name(other) + " shouldn't be slowed, hungry or warded");
                        expect(run.linesOf(other) == 0, name(other) + " shouldn't have been told anything");
                    }
                }))
                .thenIdle(LandWard.MESSAGE_EVERY_TICKS + 2 * LandWard.CHECK_EVERY_TICKS)
                .thenExecute(run.step(() -> {
                    int told = run.linesOf(cast.walker);
                    expect(told == 2, "after 20 seconds in the land he should have been told again (twice in all), but was told "
                            + told + " times");
                    expect(hasWard(cast.walker), name(cast.walker) + " should still be slowed and hungry");
                    for (ServerPlayer other : cast.untouched())
                    {
                        expect(hasNoWard(other) && run.linesOf(other) == 0, name(other) + " still shouldn't be warded or told anything");
                    }
                    expect(!LandWard.check(cast.traveller), "a player in the Overworld is never warded");
                    // The column decides: high over the land and deep under it, the same.
                    Vec3 feet = cast.cinder;
                    cast.walker.teleportTo(run.dross, feet.x, HIGH_Y, feet.z, 0.0F, 0.0F);
                    expect(LandWard.check(cast.walker), "high over the Cinder Wastes (y " + HIGH_Y + ") he should still be warded");
                    cast.walker.teleportTo(run.dross, feet.x, DEEP_Y, feet.z, 0.0F, 0.0F);
                    expect(LandWard.check(cast.walker), "deep under the Cinder Wastes (y " + DEEP_Y + ") he should still be warded");
                    cast.walker.teleportTo(run.dross, feet.x, feet.y, feet.z, 0.0F, 0.0F);
                    // Creative lifts it at once; back in survival it takes him again, without another line so soon.
                    run.setMode(cast.walker, GameType.CREATIVE);
                    expect(!LandWard.check(cast.walker) && hasNoWard(cast.walker), "in creative mode the ward should let go of him at once");
                    run.setMode(cast.walker, GameType.SURVIVAL);
                    expect(LandWard.check(cast.walker) && hasWard(cast.walker), "back in survival mode the ward should take him again");
                    int again = run.linesOf(cast.walker);
                    expect(again == 2, "coming back in within 5 seconds shouldn't say it again, but he's been told " + again + " times");
                    // His quest reaches the one that opens the Cinder Wastes.
                    OracleProgress.setQuest(cast.walker, OracleQuests.SET_IN_STONE);
                }))
                .thenWaitUntil(run.until(() -> expect(hasNoWard(cast.walker),
                        "once his quest opens the Cinder Wastes the ward should let go of him (no Slowness or Hunger)")))
                .thenExecute(run.step(() -> {
                    // To the Cinder Wastes' edge, a few blocks in from the forest: there its barrier falls.
                    Vec3 feet = cast.cinder;
                    double dx = -feet.x;
                    double dz = -feet.z;
                    double length = Math.sqrt(dx * dx + dz * dz);
                    for (int step = 0; step < 400; step++)
                    {
                        int x = (int) Math.floor(feet.x + dx / length * step);
                        int z = (int) Math.floor(feet.z + dz / length * step);
                        if (!DrossBiomes.CINDER_WASTES.equals(LandWard.landAt(run.dross, x, z)))
                        {
                            Vec3 edge = feetAt(run.dross, new BlockPos((int) Math.floor(feet.x + dx / length * Math.max(0, step - 4)), 0,
                                    (int) Math.floor(feet.z + dz / length * Math.max(0, step - 4))));
                            cast.walker.teleportTo(run.dross, edge.x, edge.y, edge.z, 0.0F, 0.0F);
                            break;
                        }
                    }
                }))
                .thenWaitUntil(run.until(() -> expect(OracleProgress.hasSeenFall(cast.walker, DrossBiomes.CINDER_WASTES),
                        "standing in the Cinder Wastes once they're open for him, their barrier should fall for him")))
                .thenExecute(run.step(() -> {
                    expect(!OracleProgress.hasSeenFall(cast.forester, DrossBiomes.CINDER_WASTES),
                            name(cast.forester) + "'s barrier is still up (the land isn't open for him)");
                    expect(!LandWard.check(cast.walker), "the Cinder Wastes are open for him now");
                    int told = run.linesOf(cast.walker);
                    expect(told == 2, "nothing more should be said once the land is open, but he's been told " + told + " times");
                    run.leave();
                }))
                .thenSucceed();
    }

    /**
     * The land of a column is its surface biome at every height from y 64 up (so flying over changes nothing), and under
     * Blightwood the sewers' own biome reads as Blightwood (they open together anyway). Every land a quest locks is one
     * the ward can read, or the sewers. No players; nothing is generated (the biomes come from the noise).
     */
    @GameTest(template = PortalSiteGameTest.EMPTY, batch = BATCH, timeoutTicks = TIMEOUT_TICKS)
    public static void theLandIsTheColumns(GameTestHelper helper)
    {
        ServerLevel dross = helper.getLevel().getServer().getLevel(ModDimensions.DROSS_LEVEL);
        expect(dross != null, "there's no Dross level");
        for (ResourceKey<Biome> land : DrossBiomes.ALL)
        {
            BlockPos column = columnOf(dross, land);
            expect(column != null, "no column of " + land.location() + " was found");
            for (int y : new int[] {72, LandWard.COLUMN_BIOME_Y, 200, HIGH_Y})
            {
                ResourceKey<Biome> there = dross.getBiome(new BlockPos(column.getX(), y, column.getZ())).unwrapKey().orElse(null);
                expect(land.equals(there), "the column at " + column.getX() + ", " + column.getZ() + " is " + land.location()
                        + " to the ward, but " + there + " at y " + y);
            }
        }
        BlockPos blight = columnOf(dross, DrossBiomes.BLIGHTWOOD);
        int ground = (int) feetAt(dross, blight).y;
        int deep = Math.min(ground - 20, 40);
        ResourceKey<Biome> under = dross.getBiome(new BlockPos(blight.getX(), deep, blight.getZ())).unwrapKey().orElse(null);
        expect(DrossBiomes.BLIGHTWOOD.equals(under) || DrossBiomes.SEWERS.equals(under), "under Blightwood at y " + deep
                + " the biome is " + under);
        expect(DrossBiomes.BLIGHTWOOD.equals(LandWard.landAt(dross, blight.getX(), blight.getZ())), "Blightwood's column "
                + "should read as Blightwood");
        expect(OracleQuests.openerOf(DrossBiomes.SEWERS) == OracleQuests.openerOf(DrossBiomes.BLIGHTWOOD),
                "the sewers should open with Blightwood (the ward reads the land over them)");
        for (ResourceKey<Biome> land : OracleQuests.lockableLands())
        {
            expect(DrossBiomes.ALL.contains(land) || DrossBiomes.SEWERS.equals(land), land.location()
                    + " is locked by a quest but isn't a land the ward reads");
        }
        helper.succeed();
    }
}
