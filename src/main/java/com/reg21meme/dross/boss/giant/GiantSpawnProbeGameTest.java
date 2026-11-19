package com.reg21meme.dross.boss.giant;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.boss.rimefall.Moose;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.ward.LandWard;
import com.reg21meme.dross.registry.ModEntities;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * A probe (namespace {@code dross_spawn_probe}, not in the normal run): a survival player standing in the Silverwood Forest,
 * well away from the hub, with mob spawning on; every 30 s of game time how many Silverwood Giants and moose are within
 * 128 blocks, logged; and, first, why the giant's natural spawn is refused at 3,000 spots of the ground round him.
 * The user (November 19, 2026): "I don't see any mooses or giants at all in the forests".
 * <pre>.\gradlew.bat runGameTestServer -PgameTestNamespaces=dross_spawn_probe -PgameTestWorld=&lt;new folder&gt;</pre>
 */
@GameTestHolder("dross_spawn_probe")
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = "dross", bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.FORGE)
public final class GiantSpawnProbeGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MINUTES = 10;
    private static final int EVERY = 600;
    private static final int SAMPLES = 3000;

    private GiantSpawnProbeGameTest() {}

    @GameTest(template = "empty", batch = "dross_probe_spawns", timeoutTicks = MINUTES * 1200 + 2000)
    public static void silverwoodSpawnProbe(GameTestHelper helper)
    {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel dross = server.getLevel(ModDimensions.DROSS_LEVEL);
        BlockPos spot = findForest(dross);
        LOGGER.info("[spawn probe] standing at {} (Silverwood all round)", spot);
        int y = dross.getChunkSource().getGenerator().getBaseHeight(spot.getX(), spot.getZ(),
                Heightmap.Types.WORLD_SURFACE_WG, dross, dross.getChunkSource().randomState());
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), new GameProfile(UUID.randomUUID(), "spawn_probe"));
        net.minecraft.world.phys.Vec3 start = helper.absoluteVec(new net.minecraft.world.phys.Vec3(0.5D, 1.0D, 0.5D));
        player.moveTo(start.x, start.y, start.z, 0.0F, 0.0F);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player);
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().invulnerable = true;
        player.teleportTo(dross, spot.getX() + 0.5D, y + 1.0D, spot.getZ() + 0.5D, 0.0F, 0.0F);
        GameRules.BooleanValue spawning = helper.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING);
        boolean was = spawning.get();
        spawning.set(true, server);
        helper.runAfterDelay(200, () -> sample(dross, player));
        for (int t = EVERY; t <= MINUTES * 1200; t += EVERY)
        {
            final int at = t;
            helper.runAfterDelay(200 + t, () -> count(dross, player, at));
        }
        helper.runAfterDelay(200 + MINUTES * 1200 + 20, () ->
        {
            spawning.set(was, server);
            player.connection.disconnect(net.minecraft.network.chat.Component.literal("done"));
            server.getPlayerList().remove(player);
            helper.succeed();
        });
    }

    private static BlockPos findForest(ServerLevel dross)
    {
        for (int r = 400; r < 3000; r += 100)
        {
            for (int a = 0; a < 16; a++)
            {
                double ang = a * Math.PI / 8.0D;
                int x = (int) (Math.cos(ang) * r);
                int z = (int) (Math.sin(ang) * r);
                boolean all = true;
                for (int dx = -128; dx <= 128 && all; dx += 64)
                {
                    for (int dz = -128; dz <= 128 && all; dz += 64)
                    {
                        all = DrossBiomes.SILVERWOOD_FOREST.equals(LandWard.landAt(dross, x + dx, z + dz));
                    }
                }
                if (all)
                {
                    return new BlockPos(x, 0, z);
                }
            }
        }
        return new BlockPos(400, 0, 0);
    }

    private static void sample(ServerLevel dross, ServerPlayer player)
    {
        RandomSource random = RandomSource.create(7L);
        Map<String, Integer> why = new TreeMap<>();
        SilverwoodGiant giant = ModEntities.SILVERWOOD_GIANT.get().create(dross);
        Moose moose = ModEntities.MOOSE.get().create(dross);
        int mooseOk = 0;
        Map<String, Integer> mooseWhy = new TreeMap<>();
        for (int i = 0; i < SAMPLES; i++)
        {
            double ang = random.nextDouble() * Math.PI * 2.0D;
            double d = 24.0D + random.nextDouble() * 104.0D;
            int x = (int) Math.floor(player.getX() + Math.cos(ang) * d);
            int z = (int) Math.floor(player.getZ() + Math.sin(ang) * d);
            if (!dross.hasChunk(x >> 4, z >> 4))
            {
                why.merge("chunk not loaded", 1, Integer::sum);
                continue;
            }
            int top = dross.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, top, z);
            String reason;
            if (!SpawnPlacements.checkSpawnRules(ModEntities.SILVERWOOD_GIANT.get(), dross, MobSpawnType.NATURAL, pos, random))
            {
                reason = SilverwoodGiants.refusal(dross, pos, dross.getBiome(pos).is(DrossBiomes.SILVERWOOD_FOREST), true);
                if (reason == null)
                {
                    reason = !Mob.checkMobSpawnRules(ModEntities.SILVERWOOD_GIANT.get(), dross, MobSpawnType.NATURAL, pos, random)
                            ? "checkMobSpawnRules (ground block " + dross.getBlockState(pos.below()).getBlock() + ")" : "other";
                }
            }
            else
            {
                giant.moveTo(x + 0.5D, top, z + 0.5D, 0.0F, 0.0F);
                if (!giant.checkSpawnRules(dross, MobSpawnType.NATURAL))
                {
                    reason = "checkSpawnRules (walk target " + giant.getWalkTargetValue(pos, dross) + ")";
                }
                else if (!giant.checkSpawnObstruction(dross))
                {
                    reason = "obstructed";
                }
                else
                {
                    reason = "OK";
                }
            }
            why.merge(reason.startsWith("walk") || reason.startsWith("checkSpawnRules") ? "checkSpawnRules (light)" : reason, 1, Integer::sum);
            moose.moveTo(x + 0.5D, top, z + 0.5D, 0.0F, 0.0F);
            String m;
            if (!SpawnPlacements.checkSpawnRules(ModEntities.MOOSE.get(), dross, MobSpawnType.NATURAL, pos, random))
            {
                m = "placement (ground " + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(dross.getBlockState(pos.below()).getBlock()) + ")";
            }
            else if (!moose.checkSpawnRules(dross, MobSpawnType.NATURAL))
            {
                m = "checkSpawnRules";
            }
            else if (!moose.checkSpawnObstruction(dross))
            {
                m = "obstructed";
            }
            else
            {
                m = "OK";
                mooseOk++;
            }
            mooseWhy.merge(m, 1, Integer::sum);
        }
        LOGGER.info("[spawn probe] giant natural spawn at {} ground spots 24-128 blocks out: {}", SAMPLES, why);
        LOGGER.info("[spawn probe] moose could spawn at {} of {}: {}", mooseOk, SAMPLES, mooseWhy);
        giant.discard();
        moose.discard();
    }

    private static void count(ServerLevel dross, ServerPlayer player, int at)
    {
        AABB box = player.getBoundingBox().inflate(128.0D, 128.0D, 128.0D);
        int giants = dross.getEntitiesOfClass(SilverwoodGiant.class, box).size();
        int moose = dross.getEntitiesOfClass(Moose.class, box).size();
        int mobs = dross.getEntitiesOfClass(Mob.class, box).size();
        LOGGER.info("[spawn probe] after {} s: {} giants, {} moose, {} mobs in all within 128 blocks", at / 20, giants, moose, mobs);
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event)
    {
        if (!net.minecraftforge.gametest.ForgeGameTestHooks.isGametestEnabled())
        {
            return;
        }
        net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate template = event.getServer()
                .getStructureManager().getOrCreate(new net.minecraft.resources.ResourceLocation("dross_spawn_probe", "empty"));
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.put("size", ints(1, 1, 1));
        net.minecraft.nbt.ListTag palette = new net.minecraft.nbt.ListTag();
        palette.add(net.minecraft.nbt.NbtUtils.writeBlockState(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()));
        tag.put("palette", palette);
        net.minecraft.nbt.ListTag blocks = new net.minecraft.nbt.ListTag();
        net.minecraft.nbt.CompoundTag block = new net.minecraft.nbt.CompoundTag();
        block.put("pos", ints(0, 0, 0));
        block.putInt("state", 0);
        blocks.add(block);
        tag.put("blocks", blocks);
        tag.put("entities", new net.minecraft.nbt.ListTag());
        template.load(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), tag);
    }

    private static net.minecraft.nbt.ListTag ints(int... values)
    {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (int v : values)
        {
            list.add(net.minecraft.nbt.IntTag.valueOf(v));
        }
        return list;
    }
}
