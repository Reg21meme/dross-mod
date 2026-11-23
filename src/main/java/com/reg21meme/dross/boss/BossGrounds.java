package com.reg21meme.dross.boss;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.emberdactyl.CraterWard;
import com.reg21meme.dross.boss.lair.LairWard;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.sewer.Sewers;
import com.reg21meme.dross.portal.DrossHub;
import com.reg21meme.dross.quest.DrossAdvancements;
import com.reg21meme.dross.quest.oracle.OracleBoss;
import com.reg21meme.dross.quest.oracle.OracleProgress;
import com.reg21meme.dross.quest.oracle.OracleQuest;
import com.reg21meme.dross.quest.oracle.OracleRules;
import com.reg21meme.dross.quest.oracle.OracleState;
import com.reg21meme.dross.quest.oracle.QuestNetwork;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * THE BOSSES' PROTECTED GROUNDS (server side; the user, November 9, 2026: "protect all the blocks in the bosses'
 * structures, saying 'Magic protects this block'"). The blocks of a boss's home in the Dross can't be broken by a player
 * who hasn't beaten that boss yet:
 * <ul>
 *   <li>the Cinder Colossus's lairs: the volcano, its cavern and way in ({@link LairWard#protects});</li>
 *   <li>the Rat King's sewer: every piece of the network that holds his rat cave, its grand entrance, royal road,
 *       tunnels and the cave with his nest and hoard (the structure {@code dross:rat_cave}; the regular sewers aren't);</li>
 *   <li>the Huntsman's villages (the structure {@code dross:huntsman_village}; the regular Frostfolk villages aren't);</li>
 *   <li>the Emberdactyl's volcanoes: the cone, its crater and nest, the pools and crust at its foot
 *       ({@link CraterWard#protects}).</li>
 * </ul>
 *
 * <p><b>Like bedrock</b> (the user, November 22, 2026: hitting the ground by accident in a fight "gives me the slowing
 * effect right away ... maybe just so the blocks aren't able to be destroyed"; it was Mining Fatigue 99 while mining one,
 * which also slowed every swing of their sword): mining one never gets anywhere and nothing slows them. Each player is
 * sent the protected shapes within {@value #SYNC_RADIUS} blocks of them ({@link BossGroundsMessage}, every
 * {@value #SYNC_EVERY_TICKS} ticks when they change), so their own game never starts to crack one either
 * ({@code boss.client.BossGroundsClient}). "Magic protects this block." comes over the hotbar only once they've kept at
 * one for {@value #MESSAGE_AFTER_TICKS} ticks (a stray hit shows nothing), and when a break gets through anyway (it's
 * cancelled).</p>
 *
 * <p><b>Beaten</b> ({@link #hasBeaten}): the protection lifts for a player the moment they earn that boss's advancement
 * (the user, November 22: "once you defeat the boss you should be able to break the boss lair, not when you go back to
 * the villager"; Set in Stone, Down the Drain, The Hunter Hunted, Wings Clipped: everyone the boss's fight counts as a
 * victor gets it), or once their Oracle quest is past it. So the Rat King's hoard and the Emberdactyl's gold are theirs to
 * dig. Players in creative or spectator mode aren't stopped. Test arenas, showcases and a boss's {@code arena} volcano
 * aren't the world's homes and aren't protected.</p>
 *
 * <p><b>The hub</b> (the user, November 18, 2026: "TNT doesn't work in destroying blocks for the bosses' bases and the
 * hub ... say like 'magic protects' stuff here, like breaking blocks"): the Shattered Spire and the survivors' camp at
 * Dross 0,0 are protected the same way, for good ({@link #isHub}: its footprint, from 4 blocks under its ground up).</p>
 *
 * <p><b>Explosions</b> (TNT, creepers, a boss's blast) never break a protected block: the hub's never, a boss's home's
 * only when set off by a player who has beaten that boss (whatever their game mode).</p>
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class BossGrounds
{
    private static final Logger LOGGER = LogUtils.getLogger();

    // ------------------------------------------------------------------ Tuning

    /** "Magic protects this block." comes once they've kept mining one for this many ticks (a stray hit shows nothing)... */
    public static final int MESSAGE_AFTER_TICKS = 10;
    /** ...counted while they keep at it: a gap of more than this many ticks starts the count again. */
    public static final int MINING_GAP_TICKS = 2;
    /** The line over the hotbar comes at most every this many ticks for a player. */
    public static final int MESSAGE_GAP_TICKS = 40;
    /** The line's lang key: "Magic protects this block." */
    public static final String MESSAGE_KEY = "dross.boss_grounds.protected";
    /** What it says at the Huntsman's village while he sleeps (a player his quest hasn't reached yet: he's asleep in his
     * lodge; the user, November 18, 2026: "magic protects his house while he sleeps"). */
    public static final String HUNTSMAN_ASLEEP_KEY = "dross.boss_grounds.protected.huntsman";

    /** Every this many ticks each player's protected shapes are worked out again, and sent if they changed... */
    public static final int SYNC_EVERY_TICKS = 10;
    /** ...the shapes within this many blocks (along the ground) of a spot near them... */
    public static final double SYNC_RADIUS = 32.0D;
    /** ...that spot moving to where they are once they're further than this from it (their reach is about 5 blocks). */
    public static final double SYNC_MOVE = 8.0D;

    /** The Huntsman's villages' structure (the boss area's {@code HunterVillageStructure}). */
    public static final ResourceKey<Structure> HUNTSMAN_VILLAGE = ResourceKey.create(Registries.STRUCTURE,
            new ResourceLocation(Dross.MODID, "huntsman_village"));

    /** When each player last got the line (server tick). */
    private static final Map<UUID, Long> LAST_MESSAGE = new HashMap<>();
    /** The protected block each player is mining, since when and when last. */
    private static final Map<UUID, Mining> MINING = new HashMap<>();
    /** What each player's game was last sent: where its spot was and the message's bytes. */
    private static final Map<UUID, Sent> SENT = new HashMap<>();
    /** Each structure start's piece boxes, by structure and start chunk (a start never changes; server thread). */
    private static final Map<ResourceKey<Structure>, Map<Long, List<BoundingBox>>> PIECES = new HashMap<>();

    private record Mining(BlockPos pos, long first, long last)
    {
    }

    private record Sent(ResourceKey<Level> level, double x, double z, byte[] bytes)
    {
    }

    private BossGrounds() {}

    // ------------------------------------------------------------------ The rule

    /** The boss whose home this block belongs to, or null (the Dross only). */
    @Nullable
    public static OracleBoss protectorAt(ServerLevel level, BlockPos pos)
    {
        if (!level.dimension().equals(ModDimensions.DROSS_LEVEL))
        {
            return null;
        }
        if (LairWard.protects(pos))
        {
            return OracleBoss.COLOSSUS;
        }
        if (CraterWard.protects(level, pos))
        {
            return OracleBoss.EMBERDACTYL;
        }
        if (inStructure(level, pos, Sewers.RAT_CAVE))
        {
            return OracleBoss.RAT_KING;
        }
        if (inStructure(level, pos, HUNTSMAN_VILLAGE))
        {
            return OracleBoss.HUNTSMAN;
        }
        return null;
    }

    /** How far under the hub's ground its protection reaches (blocks). */
    public static final int HUB_DEPTH = 4;

    /** True if this block is part of the hub at Dross 0,0: the spire's footprint (its camp too), from {@value #HUB_DEPTH} under its ground up. */
    public static boolean isHub(ServerLevel level, BlockPos pos)
    {
        return level.dimension().equals(ModDimensions.DROSS_LEVEL) && hubShape(level).contains(pos);
    }

    /** The hub's protected blocks: its footprint's columns, from {@value #HUB_DEPTH} under its ground to the build limit. */
    private static GroundsShape.Box hubShape(ServerLevel level)
    {
        BoundingBox columns = DrossHub.columns();
        return new GroundsShape.Box(columns.minX(), DrossHub.groundY(level) - HUB_DEPTH, columns.minZ(),
                columns.maxX(), level.getMaxBuildHeight(), columns.maxZ());
    }

    /** True if this player may not break this block: the hub, or a boss's home they haven't beaten (never in creative or spectator mode). */
    public static boolean blocked(ServerPlayer player, BlockPos pos)
    {
        if (OracleRules.exempt(player))
        {
            return false;
        }
        return isHub(player.serverLevel(), pos) || protectedFor(player, pos) != null;
    }

    /** The boss whose home keeps this player from breaking this block, or null if they may. */
    @Nullable
    public static OracleBoss protectedFor(ServerPlayer player, BlockPos pos)
    {
        if (OracleRules.exempt(player))
        {
            return null;
        }
        OracleBoss boss = protectorAt(player.serverLevel(), pos);
        return boss != null && !hasBeaten(player, boss) ? boss : null;
    }

    /**
     * True if this player has beaten this boss: they hold its advancement (it comes with the kill, whatever their Oracle
     * quest says), or their Oracle quest is past its quest, or on it and done.
     */
    public static boolean hasBeaten(ServerPlayer player, OracleBoss boss)
    {
        OracleQuest quest = boss.quest();
        OracleQuest current = OracleProgress.current(player);
        OracleState state = OracleProgress.state(player);
        return current.index() > quest.index()
                || (current.index() == quest.index() && (state == OracleState.DONE || state == OracleState.FINISHED))
                || DrossAdvancements.has(player, quest.advancement());
    }

    private static boolean inStructure(ServerLevel level, BlockPos pos, ResourceKey<Structure> key)
    {
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(key);
        return structure != null && level.structureManager().getStructureWithPieceAt(pos, structure).isValid();
    }

    // ------------------------------------------------------------------ The shapes a player's game is sent

    /**
     * The protected shapes within {@code radius} blocks of this spot that keep this player from breaking blocks: the hub's,
     * and the homes of the bosses they haven't beaten (none in creative or spectator mode, none outside the Dross).
     */
    public static List<GroundsShape> shapesFor(ServerPlayer player, double x, double z, double radius)
    {
        ServerLevel level = player.serverLevel();
        List<GroundsShape> out = new ArrayList<>();
        if (OracleRules.exempt(player) || !level.dimension().equals(ModDimensions.DROSS_LEVEL))
        {
            return out;
        }
        GroundsShape hub = hubShape(level);
        if (hub.near(x, z, radius))
        {
            out.add(hub);
        }
        if (!hasBeaten(player, OracleBoss.COLOSSUS))
        {
            LairWard.shapesNear(x, z, radius, out);
        }
        if (!hasBeaten(player, OracleBoss.EMBERDACTYL))
        {
            CraterWard.shapesNear(level, x, z, radius, out);
        }
        if (!hasBeaten(player, OracleBoss.RAT_KING))
        {
            structureShapes(level, Sewers.RAT_CAVE, x, z, radius, out);
        }
        if (!hasBeaten(player, OracleBoss.HUNTSMAN))
        {
            structureShapes(level, HUNTSMAN_VILLAGE, x, z, radius, out);
        }
        return out;
    }

    /**
     * A structure's protected boxes near this spot, exactly as {@link #inStructure} sees them: in each loaded chunk near it,
     * the pieces of the starts that chunk refers to, cut to the chunk.
     */
    private static void structureShapes(ServerLevel level, ResourceKey<Structure> key, double x, double z, double radius,
                                        List<GroundsShape> out)
    {
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(key);
        if (structure == null)
        {
            return;
        }
        int minCx = (int) Math.floor((x - radius) / 16.0D);
        int maxCx = (int) Math.floor((x + radius) / 16.0D);
        int minCz = (int) Math.floor((z - radius) / 16.0D);
        int maxCz = (int) Math.floor((z + radius) / 16.0D);
        for (int cx = minCx; cx <= maxCx; cx++)
        {
            for (int cz = minCz; cz <= maxCz; cz++)
            {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null)
                {
                    continue;
                }
                LongSet refs = chunk.getReferencesForStructure(structure);
                if (refs.isEmpty())
                {
                    continue;
                }
                int x0 = cx << 4;
                int z0 = cz << 4;
                for (long ref : refs)
                {
                    for (BoundingBox box : pieces(level, key, structure, ref))
                    {
                        int minX = Math.max(box.minX(), x0);
                        int maxX = Math.min(box.maxX(), x0 + 15);
                        int minZ = Math.max(box.minZ(), z0);
                        int maxZ = Math.min(box.maxZ(), z0 + 15);
                        if (minX > maxX || minZ > maxZ)
                        {
                            continue;
                        }
                        GroundsShape.Box cut = new GroundsShape.Box(minX, box.minY(), minZ, maxX, box.maxY(), maxZ);
                        if (cut.near(x, z, radius))
                        {
                            out.add(cut);
                        }
                    }
                }
            }
        }
    }

    /** The piece boxes of the start in this chunk (worked out once: it may load that chunk's saved starts). */
    private static List<BoundingBox> pieces(ServerLevel level, ResourceKey<Structure> key, Structure structure, long ref)
    {
        Map<Long, List<BoundingBox>> byStart = PIECES.computeIfAbsent(key, k -> new HashMap<>());
        List<BoundingBox> boxes = byStart.get(ref);
        if (boxes == null)
        {
            ChunkPos at = new ChunkPos(ref);
            ChunkAccess startChunk = level.getChunk(at.x, at.z, ChunkStatus.STRUCTURE_STARTS);
            StructureStart start = startChunk.getStartForStructure(structure);
            List<BoundingBox> found = new ArrayList<>();
            if (start != null && start.isValid())
            {
                for (StructurePiece piece : start.getPieces())
                {
                    found.add(piece.getBoundingBox());
                }
            }
            boxes = List.copyOf(found);
            byStart.put(ref, boxes);
        }
        return boxes;
    }

    /** Works this player's shapes out again, and sends them to their game if they changed. */
    static void sync(ServerPlayer player)
    {
        if (player.connection == null)
        {
            return;
        }
        ResourceKey<Level> dimension = player.level().dimension();
        Sent last = SENT.get(player.getUUID());
        double x = player.getX();
        double z = player.getZ();
        boolean sameSpot = last != null && last.level().equals(dimension)
                && Math.hypot(x - last.x(), z - last.z()) <= SYNC_MOVE;
        if (sameSpot)
        {
            x = last.x();
            z = last.z();
        }
        List<GroundsShape> shapes = shapesFor(player, x, z, SYNC_RADIUS);
        if (shapes.size() > BossGroundsMessage.MAX_SHAPES)
        {
            LOGGER.warn("Boss grounds: {} protected shapes near {}, only the first {} are sent.", shapes.size(),
                    player.getGameProfile().getName(), BossGroundsMessage.MAX_SHAPES);
            shapes = shapes.subList(0, BossGroundsMessage.MAX_SHAPES);
        }
        byte[] bytes = BossGroundsMessage.bytes(shapes);
        boolean changed = last == null ? !shapes.isEmpty() : !Arrays.equals(last.bytes(), bytes);
        if (changed)
        {
            QuestNetwork.sendTo(player, new BossGroundsMessage(List.copyOf(shapes)));
        }
        if (changed || !sameSpot)
        {
            SENT.put(player.getUUID(), new Sent(dimension, x, z, bytes));
        }
    }

    // ------------------------------------------------------------------ The hooks

    /** While mining one: it never gets anywhere (nothing slows them), and the line once they've kept at it. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event)
    {
        if (event.getEntity() instanceof ServerPlayer player && event.getPosition().isPresent()
                && blocked(player, event.getPosition().get()))
        {
            event.setNewSpeed(0.0F);
            keepMining(player, event.getPosition().get());
        }
    }

    /** Anything that got through anyway (another mod's way of breaking, a game that didn't know yet) isn't broken either. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BlockEvent.BreakEvent event)
    {
        if (event.getPlayer() instanceof ServerPlayer player && blocked(player, event.getPos()))
        {
            event.setCanceled(true);
            tell(player, event.getPos());
        }
    }

    /**
     * An explosion leaves protected blocks alone: the hub's always, a boss's home's unless a player who has beaten that
     * boss set it off (in any game mode: a creative player's TNT doesn't break an unbeaten boss's home either).
     */
    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate event)
    {
        Level level = event.getLevel();
        if (!(level instanceof ServerLevel server) || !server.dimension().equals(ModDimensions.DROSS_LEVEL))
        {
            return;
        }
        Entity source = event.getExplosion().getIndirectSourceEntity();
        ServerPlayer player = source instanceof ServerPlayer p ? p : null;
        event.getAffectedBlocks().removeIf(pos -> {
            if (isHub(server, pos))
            {
                return true;
            }
            OracleBoss boss = protectorAt(server, pos);
            return boss != null && (player == null || !hasBeaten(player, boss));
        });
    }

    /** Every few ticks: each player's game is told the protected grounds near them, when they changed. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % SYNC_EVERY_TICKS != 0)
        {
            return;
        }
        for (ServerPlayer player : List.copyOf(event.getServer().getPlayerList().getPlayers()))
        {
            try
            {
                sync(player);
            }
            catch (RuntimeException e)
            {
                LOGGER.error("Boss grounds: telling {} the protected grounds near them failed.", player.getGameProfile().getName(), e);
            }
        }
    }

    /** Counts how long they've kept at this block; the line once it's long enough. */
    private static void keepMining(ServerPlayer player, BlockPos pos)
    {
        long tick = player.server.getTickCount();
        Mining was = MINING.get(player.getUUID());
        Mining now = was != null && was.pos().equals(pos) && tick >= was.last() && tick - was.last() <= MINING_GAP_TICKS
                ? new Mining(was.pos(), was.first(), tick) : new Mining(pos.immutable(), tick, tick);
        MINING.put(player.getUUID(), now);
        if (now.last() - now.first() >= MESSAGE_AFTER_TICKS)
        {
            tell(player, pos);
        }
    }

    private static void tell(ServerPlayer player, BlockPos pos)
    {
        long tick = player.server.getTickCount();
        Long last = LAST_MESSAGE.get(player.getUUID());
        if (last == null || tick - last >= MESSAGE_GAP_TICKS || tick < last)
        {
            LAST_MESSAGE.put(player.getUUID(), tick);
            String key = protectedFor(player, pos) == OracleBoss.HUNTSMAN
                    && !OracleProgress.isAtOrPast(player, OracleBoss.HUNTSMAN.quest()) ? HUNTSMAN_ASLEEP_KEY : MESSAGE_KEY;
            player.displayClientMessage(Component.translatable(key)
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(DrossColors.BOSS_GROUNDS_TEXT))), true);
        }
    }

    @SubscribeEvent
    public static void onLogOut(PlayerEvent.PlayerLoggedOutEvent event)
    {
        UUID id = event.getEntity().getUUID();
        LAST_MESSAGE.remove(id);
        MINING.remove(id);
        SENT.remove(id);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        LAST_MESSAGE.clear();
        MINING.clear();
        SENT.clear();
        PIECES.clear();
    }
}
