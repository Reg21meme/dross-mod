package com.reg21meme.dross.boss.huntsman;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.boss.rage.Rage;
import com.reg21meme.dross.boss.rage.RageTeam;
import com.reg21meme.dross.boss.rimefall.PondSkater;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Measuring probes for the Huntsman's fight: in their own GameTest namespace so the normal run leaves them out
 * (<pre>.\gradlew.bat runGameTestServer -PgameTestNamespaces=dross_probe -PgameTestWorld=&lt;a folder&gt;</pre>; a world
 * the probe has run in before is fine), each on a floor of barrier blocks high in the sky (the normal tests' arenas are
 * underground, too small for a shot from 20 blocks).
 * <p>
 * {@link #saddleShotsProbe}: him in the saddle, the moose standing still, a target (a villager that can't be killed)
 * at 6, 12 and 20 blocks in each of the four directions, standing or running across his line at a sprint; each of his
 * shots from the saddle that fits the direction is played at it three times and the hits counted (the user, October
 * 23, 2026: his arrows from the saddle didn't hit them; a running target was missed: 0 of 6 at 12 and 20 blocks, until
 * he led his shots). {@link #shots} is also the normal run's check, {@code HuntsmanFightGameTest.saddleShotsHit}.
 * <p>
 * {@link #ragedHuntVersusRatKing} and {@link #ragedHuntVersusColossus}: the Wild Hunt raged against another raged boss,
 * logged every second (it only headbutted them until October 24, 2026). {@link #skatersHuntBigGame}: a Pond Skater by a
 * moose, a Rimehorn and the Wild Hunt, logged.
 */
@GameTestHolder(HuntProbeGameTest.NAMESPACE)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = "dross", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HuntProbeGameTest
{
    static final String NAMESPACE = "dross_probe";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String EMPTY = "empty";
    private static final ResourceLocation EMPTY_ID = new ResourceLocation(NAMESPACE, EMPTY);
    /** The floor: this high (an absolute height), this far each way from its middle. */
    private static final int SKY_Y = 250;
    private static final int FLOOR_HALF = 26;
    private static final float TARGET_HEALTH = 1000.0F;
    private static final int AFTER_TICKS = 30;
    /** A running target: this many blocks a tick, across the line he shoots along (a sprinting player: 0.28). */
    private static final double RUN = 0.28D;
    /** Directions round the moose (it faces +z): its left is +x. */
    private static final String[] DIRS = {"left", "front", "right", "back"};

    private HuntProbeGameTest() {}

    private record Case(String dir, int distance, boolean running)
    {
    }

    @GameTest(template = EMPTY, batch = "dross_probe_shots", timeoutTicks = 60_000)
    public static void saddleShotsProbe(GameTestHelper helper)
    {
        shots(helper, new int[] {6, 12, 20}, 3, 0.0D);
    }

    /**
     * Plays each of his saddle shots at a target in its direction ({@code distances} out, standing and running across,
     * {@code reps} times each), logs the hits, and fails if a case's share of hits is under {@code mustHit}.
     */
    static void shots(GameTestHelper helper, int[] distances, int reps, double mustHit)
    {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos base = new BlockPos(origin.getX(), SKY_Y, origin.getZ());
        List<ChunkPos> forced = new ArrayList<>();
        for (int cx = (base.getX() - FLOOR_HALF) >> 4; cx <= (base.getX() + FLOOR_HALF) >> 4; cx++)
        {
            for (int cz = (base.getZ() - FLOOR_HALF) >> 4; cz <= (base.getZ() + FLOOR_HALF) >> 4; cz++)
            {
                level.setChunkForced(cx, cz, true);
                forced.add(new ChunkPos(cx, cz));
            }
        }
        floor(level, base, Blocks.BARRIER);
        // (villagers left over from an earlier run in this world would take the arrows)
        for (Villager old : level.getEntitiesOfClass(Villager.class, new AABB(base).inflate(40.0D)))
        {
            old.discard();
        }
        Huntsman h = HuntsmanPair.spawn(level, Vec3.atBottomCenterOf(base), 0.0F);
        if (h == null || !(h.getVehicle() instanceof AlphaMoose m))
        {
            throw new GameTestAssertException("couldn't make the Huntsman in his saddle");
        }
        h.setNoAi(true);
        m.setNoAi(true);
        Villager v = EntityType.VILLAGER.create(level);
        if (v == null)
        {
            throw new GameTestAssertException("couldn't make a villager");
        }
        v.setNoAi(true);
        v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(TARGET_HEALTH);
        v.setHealth(TARGET_HEALTH);
        v.moveTo(base.getX() + 0.5D, base.getY(), base.getZ() + 10.5D, 0.0F, 0.0F);
        level.addFreshEntity(v);
        List<Case> cases = new ArrayList<>();
        for (String dir : DIRS)
        {
            for (int d : distances)
            {
                for (int r = 0; r < reps; r++)
                {
                    cases.add(new Case(dir, d, false));
                    cases.add(new Case(dir, d, true));
                }
            }
        }
        int[] hits = new int[DIRS.length * distances.length * 2];
        int[] fired = new int[DIRS.length * distances.length * 2];
        helper.runAfterDelay(5, () -> next(helper, h, v, base, distances, cases, 0, hits, fired, () ->
        {
            List<String> out = new ArrayList<>();
            String failed = null;
            for (int i = 0; i < DIRS.length; i++)
            {
                for (int j = 0; j < distances.length; j++)
                {
                    for (int run = 0; run < 2; run++)
                    {
                        int k = (i * distances.length + j) * 2 + run;
                        String line = String.format(Locale.ROOT, "%-5s %2d blocks, %s: %d of %d hit", DIRS[i], distances[j],
                                run == 1 ? "running across" : "standing", hits[k], fired[k]);
                        out.add(line);
                        if (fired[k] > 0 && hits[k] < mustHit * fired[k] && failed == null)
                        {
                            failed = line;
                        }
                    }
                }
            }
            out.forEach(line -> LOGGER.info("[saddle shots] {}", line));
            h.discard();
            m.discard();
            v.discard();
            floor(level, base, Blocks.AIR);
            forced.forEach(c -> level.setChunkForced(c.x, c.z, false));
            if (failed != null)
            {
                helper.fail("his arrows from the saddle should hit: " + failed);
                return;
            }
            helper.succeed();
        }));
    }

    private static void floor(ServerLevel level, BlockPos base, Block block)
    {
        floor(level, base, block, FLOOR_HALF);
    }

    private static void floor(ServerLevel level, BlockPos base, Block block, int half)
    {
        for (int x = -half; x <= half; x++)
        {
            for (int z = -half; z <= half; z++)
            {
                level.setBlock(base.offset(x, -1, z), block.defaultBlockState(), 2);
            }
        }
    }

    static String shotAnim(String dir)
    {
        return dir.equals("left") ? "ride_shoot" : "ride_shoot_" + dir;
    }

    private static void next(GameTestHelper helper, Huntsman h, Villager v, BlockPos base, int[] distances,
                             List<Case> cases, int i, int[] hits, int[] fired, Runnable done)
    {
        if (i >= cases.size())
        {
            done.run();
            return;
        }
        Case c = cases.get(i);
        String anim = shotAnim(c.dir());
        double dx = switch (c.dir()) { case "left" -> c.distance(); case "right" -> -c.distance(); default -> 0.0D; };
        double dz = switch (c.dir()) { case "front" -> c.distance(); case "back" -> -c.distance(); default -> 0.0D; };
        // running across his line of fire (along z for left and right, along x for front and back), passing the middle
        // as he looses his first arrow
        List<HuntCues.Shot> arrows = HuntCues.shots(HuntAnims.RIDER, anim);
        int firstShot = arrows.isEmpty() ? 0 : arrows.get(0).tick();
        boolean along = c.dir().equals("left") || c.dir().equals("right");
        int[] t = {0};
        Runnable place = () ->
        {
            double run = c.running() ? RUN * (t[0] - firstShot) : 0.0D;
            v.moveTo(base.getX() + 0.5D + dx + (along ? 0.0D : run), base.getY(),
                    base.getZ() + 0.5D + dz + (along ? run : 0.0D), 0.0F, 0.0F);
            v.setDeltaMovement(Vec3.ZERO);
        };
        place.run();
        v.xo = v.getX();
        v.zo = v.getZ();
        v.setHealth(TARGET_HEALTH);
        v.invulnerableTime = 0;
        h.shotTarget = v;
        h.shotBase = 1.0D;
        h.shotFrost = HuntFight.Frost.NONE;
        h.play(anim);
        int length = HuntAnims.named(HuntAnims.RIDING, anim).ticks();
        int[] hurts = {0};
        float[] last = {TARGET_HEALTH};
        Runnable[] watch = new Runnable[1];
        // each drop in its health is a hit (an arrow at 3 blocks a tick does 3 at a base of 1)
        watch[0] = () ->
        {
            float now = v.getHealth();
            if (now < last[0] - 0.5F)
            {
                hurts[0]++;
                v.invulnerableTime = 0;
            }
            last[0] = now;
            place.run();
            if (++t[0] < length + AFTER_TICKS)
            {
                // (a new task each time: the test's scheduler drops a task re-added from inside itself)
                helper.runAfterDelay(1, () -> watch[0].run());
                return;
            }
            int k = (indexOf(c.dir()) * distances.length + indexOf(distances, c.distance())) * 2 + (c.running() ? 1 : 0);
            hits[k] += hurts[0];
            fired[k] += arrows.size();
            h.shotTarget = null;
            h.play("ride_idle");
            helper.runAfterDelay(2, () -> next(helper, h, v, base, distances, cases, i + 1, hits, fired, done));
        };
        helper.runAfterDelay(1, () -> watch[0].run());
    }

    private static int indexOf(String dir)
    {
        for (int i = 0; i < DIRS.length; i++)
        {
            if (DIRS[i].equals(dir))
            {
                return i;
            }
        }
        return -1;
    }

    private static int indexOf(int[] distances, int distance)
    {
        for (int i = 0; i < distances.length; i++)
        {
            if (distances[i] == distance)
            {
                return i;
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------- raged against the other bosses

    private static final int RAGE_FLOOR_HALF = 40;
    private static final int RAGE_PROBE_TICKS = 900;

    /** (the user, October 24, 2026: "the Huntsman doesn't seem to attack when I use the Rage Wand on him against the other
     * bosses") A raged Wild Hunt (red) against a raged Rat King (blue), on a floor in the sky; logs every second what
     * each of them does and how much health the king has left. */
    @GameTest(template = EMPTY, batch = "dross_probe_rage_king", timeoutTicks = RAGE_PROBE_TICKS + 200)
    public static void ragedHuntVersusRatKing(GameTestHelper helper)
    {
        ragedVersus(helper, ModEntities.RAT_KING.get());
    }

    /** The same against a raged Cinder Colossus. */
    @GameTest(template = EMPTY, batch = "dross_probe_rage_colossus", timeoutTicks = RAGE_PROBE_TICKS + 200)
    public static void ragedHuntVersusColossus(GameTestHelper helper)
    {
        ragedVersus(helper, ModEntities.CINDER_COLOSSUS.get());
    }

    private static void ragedVersus(GameTestHelper helper, EntityType<? extends Mob> type)
    {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos base = new BlockPos(origin.getX(), SKY_Y, origin.getZ());
        List<ChunkPos> forced = forceChunks(level, base, RAGE_FLOOR_HALF);
        floor(level, base, Blocks.BARRIER, RAGE_FLOOR_HALF);
        Huntsman h = HuntsmanPair.spawn(level, Vec3.atBottomCenterOf(base.offset(-12, 0, 0)), 270.0F);
        if (h == null || !(h.getVehicle() instanceof AlphaMoose m))
        {
            throw new GameTestAssertException("couldn't make the Huntsman in his saddle");
        }
        Mob boss = type.create(level);
        if (boss == null)
        {
            throw new GameTestAssertException("couldn't make the boss");
        }
        boss.moveTo(base.getX() + 12.5D, base.getY(), base.getZ() + 0.5D, 90.0F, 0.0F);
        level.addFreshEntity(boss);
        boolean raged = Rage.enrage(h, RageTeam.RED) && Rage.enrage(boss, RageTeam.BLUE);
        LOGGER.info("[rage probe] {} vs the Wild Hunt: raged {}, the moose {} {}", boss.getType().getDescriptionId(),
                raged, Rage.isRaged(m), Rage.teamOf(m));
        float[] start = {boss.getHealth(), m.wildHunt};
        for (int at = 20; at <= RAGE_PROBE_TICKS; at += 20)
        {
            int when = at;
            helper.runAfterDelay(at, () -> LOGGER.info(String.format(Locale.ROOT,
                    "[rage probe] %3ds %s; him %s at %s, %s, held %s; the moose %s at %s, %s; Wild Hunt %.0f/%.0f;"
                            + " the boss %.0f/%.0f at %s, its target %s; apart %.1f",
                    when / 20, m.stage(), h.current().name(), name(h.getTarget()), h.isPassenger() ? "riding" : "on foot",
                    h.held, m.current().name(), name(m.getTarget()), m.held, m.wildHunt, start[1], boss.getHealth(),
                    start[0], name(boss), name(boss.getTarget()), boss.distanceTo(m))));
        }
        helper.runAfterDelay(RAGE_PROBE_TICKS + 5, () ->
        {
            h.discard();
            m.discard();
            boss.discard();
            floor(level, base, Blocks.AIR, RAGE_FLOOR_HALF);
            forced.forEach(c -> level.setChunkForced(c.x, c.z, false));
            helper.succeed();
        });
    }

    private static String name(@javax.annotation.Nullable net.minecraft.world.entity.Entity e)
    {
        return e == null ? "nothing" : e.getType().getDescriptionId().replace("entity.dross.", "").replace("entity.minecraft.", "")
                + (e.isAlive() ? "" : " (dead)");
    }

    private static List<ChunkPos> forceChunks(ServerLevel level, BlockPos base, int half)
    {
        List<ChunkPos> forced = new ArrayList<>();
        for (int cx = (base.getX() - half) >> 4; cx <= (base.getX() + half) >> 4; cx++)
        {
            for (int cz = (base.getZ() - half) >> 4; cz <= (base.getZ() + half) >> 4; cz++)
            {
                level.setChunkForced(cx, cz, true);
                forced.add(new ChunkPos(cx, cz));
            }
        }
        return forced;
    }

    // ---------------------------------------------------------------- Pond Skaters against Rimefall's big game

    private static final int SKATER_PROBE_TICKS = 400;

    /** (the user, October 24, 2026: "make sure Pond Skaters are hostile to the Frostfolk, the Huntsman and the moose and
     * Rimehorns as well") A Pond Skater 6 blocks from a moose, a Rimehorn and the Wild Hunt in turn (each on its own
     * floor in the sky); logs whom it goes for and their health. */
    @GameTest(template = EMPTY, batch = "dross_probe_skaters", timeoutTicks = SKATER_PROBE_TICKS + 200)
    public static void skatersHuntBigGame(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos base = new BlockPos(origin.getX(), SKY_Y, origin.getZ());
        List<ChunkPos> forced = forceChunks(level, base, RAGE_FLOOR_HALF);
        floor(level, base, Blocks.BARRIER, RAGE_FLOOR_HALF);
        List<Mob> all = new ArrayList<>();
        List<Mob[]> pairs = new ArrayList<>();
        int row = -24;
        for (EntityType<? extends Mob> type : List.<EntityType<? extends Mob>>of(ModEntities.MOOSE.get(),
                ModEntities.RIMEHORN.get(), ModEntities.ALPHA_MOOSE.get()))
        {
            Mob prey;
            if (type == ModEntities.ALPHA_MOOSE.get())
            {
                Huntsman h = HuntsmanPair.spawn(level, Vec3.atBottomCenterOf(base.offset(0, 0, row)), 0.0F);
                prey = h == null ? null : (Mob) h.getVehicle();
                if (h != null)
                {
                    all.add(h);
                }
            }
            else
            {
                prey = type.create(level);
                if (prey != null)
                {
                    prey.moveTo(base.getX() + 0.5D, base.getY(), base.getZ() + row + 0.5D, 0.0F, 0.0F);
                    level.addFreshEntity(prey);
                }
            }
            PondSkater skater = ModEntities.POND_SKATER.get().create(level);
            if (prey == null || skater == null)
            {
                throw new GameTestAssertException("couldn't make " + type.getDescriptionId() + " or a skater");
            }
            skater.moveTo(base.getX() + 6.5D, base.getY(), base.getZ() + row + 0.5D, 90.0F, 0.0F);
            level.addFreshEntity(skater);
            all.add(prey);
            all.add(skater);
            pairs.add(new Mob[] {prey, skater});
            row += 24;
        }
        for (int at = 40; at <= SKATER_PROBE_TICKS; at += 40)
        {
            int when = at;
            helper.runAfterDelay(at, () ->
            {
                for (Mob[] pair : pairs)
                {
                    LOGGER.info(String.format(Locale.ROOT, "[skater probe] %3ds %s %.0f/%.0f, its target %s; the skater"
                                    + " %.0f, its target %s",
                            when / 20, name(pair[0]), pair[0].getHealth(), pair[0].getMaxHealth(), name(pair[0].getTarget()),
                            pair[1].getHealth(), name(pair[1].getTarget())));
                }
            });
        }
        helper.runAfterDelay(SKATER_PROBE_TICKS + 5, () ->
        {
            all.forEach(Mob::discard);
            floor(level, base, Blocks.AIR, RAGE_FLOOR_HALF);
            forced.forEach(c -> level.setChunkForced(c.x, c.z, false));
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------- the probes' empty template

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        if (!ForgeGameTestHooks.isGametestEnabled())
        {
            return;
        }
        StructureTemplate template = event.getServer().getStructureManager().getOrCreate(EMPTY_ID);
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
        for (int v : values)
        {
            list.add(IntTag.valueOf(v));
        }
        return list;
    }
}
