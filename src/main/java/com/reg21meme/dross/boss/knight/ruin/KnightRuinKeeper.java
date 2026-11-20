package com.reg21meme.dross.boss.knight.ruin;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.DrossConfig;
import com.reg21meme.dross.boss.knight.ElementalKnight;
import com.reg21meme.dross.boss.knight.KnightKind;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Each knight ruin's two knights (CLAUDE.md step 18; the user, November 19, 2026):
 * <ul>
 *   <li>Once, as soon as a player is within {@link KnightRuinTuning#TIDY_RANGE} blocks and its land is loaded, it's tidied
 *       ({@link KnightRuinTidy}: what grew into it felled, its own blocks put back).</li>
 *   <li>They come when a player (not a spectator) is within {@link KnightRuinTuning#KNIGHTS_COME} blocks of the ruin and
 *       its land is loaded, on their guard spots (its design's), facing out of its way in; never on Peaceful, and only
 *       where the ruin was really built (its structure is there). They keep near it ({@link ElementalKnight#guard}).</li>
 *   <li>Once both are beaten, they come back {@code knightsReturnMinutes} (10) later, only while no player is within
 *       {@link KnightRuinTuning#CLEAR_RANGE} (64) blocks of it (the user: "if no one is within 64 blocks, that's fair").
 *       One that left without being beaten (Peaceful) comes again.</li>
 *   <li>When the second falls, every player who cleared it (the one credited with that death and every player within
 *       {@link KnightRuinTuning#CREDIT_RANGE}) is remembered ({@link KnightRuinData}) and gets a
 *       {@link KnightRuinClearedEvent} (for the quest, built separately).</li>
 * </ul>
 * Also starts the ruins' planning when the Overworld loads ({@link KnightRuinSites#register}) and keeps the regions round
 * the players worked out ahead of them in the background.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class KnightRuinKeeper
{
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceKey<Structure> STRUCTURE = ResourceKey.create(Registries.STRUCTURE, new ResourceLocation(Dross.MODID, "knight_ruin"));

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event)
    {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD)
        {
            KnightRuinSites.register(level);
        }
    }

    /** The tidy-ups going on, by ruin id (a step each server tick). */
    private static final java.util.Map<String, KnightRuinTidy> TIDYING = new java.util.LinkedHashMap<>();

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD)
        {
            return;
        }
        if (!TIDYING.isEmpty())
        {
            var it = TIDYING.entrySet().iterator();
            var next = it.next();
            if (next.getValue().step())
            {
                KnightRuinData.get(level).tidied(next.getKey());
                it.remove();
            }
        }
        if (net.minecraftforge.gametest.ForgeGameTestHooks.isGametestEnabled())
        {
            // (the test server: its tests drive the keeper and work their ruins out themselves; a near-spawn ruin's real
            // knights would come for the other tests' stand-in players and mobs, and working the regions out round every
            // stand-in player kept a thread busy all the run, other tests' land loading slower)
            return;
        }
        long t = level.getGameTime();
        boolean warm = t % KnightRuinTuning.WARM_EVERY == 0;
        boolean keep = t % KnightRuinTuning.KEEPER_EVERY == 0;
        if ((!warm && !keep) || level.players().isEmpty() || !KnightRuinSites.available(level))
        {
            return;
        }
        Set<String> done = new HashSet<>();
        for (ServerPlayer player : level.players())
        {
            if (warm)
            {
                KnightRuinSites.warmAround(level, player.getBlockX(), player.getBlockZ(), KnightRuinTuning.WARM_AHEAD);
            }
            if (keep && !player.isSpectator())
            {
                for (RuinSite site : KnightRuinSites.readyNear(level, player.getBlockX(), player.getBlockZ(), KnightRuinTuning.TIDY_RANGE + 32))
                {
                    if (done.add(site.id()))
                    {
                        keep(level, site);
                    }
                }
            }
        }
    }

    /** Looks after one ruin: its knights come (or come back) if they should. */
    static void keep(ServerLevel level, RuinSite site)
    {
        double near = nearestPlayer(level, site);
        if (near > KnightRuinTuning.TIDY_RANGE || !level.hasChunkAt(site.ground()) || !built(level, site))
        {
            return;
        }
        KnightRuinData data = KnightRuinData.get(level);
        if (!data.entry(site.id()).tidied())
        {
            // (once, as soon as its land is loaded: what grew into it felled, its blocks put back; its knights come after)
            if (!TIDYING.containsKey(site.id()) && KnightRuinTidy.landLoaded(level, site))
            {
                TIDYING.put(site.id(), new KnightRuinTidy(level, site));
            }
            return;
        }
        if (near > KnightRuinTuning.KNIGHTS_COME || !level.isPositionEntityTicking(site.ground())
                || !level.isPositionEntityTicking(site.guardCell(0)) || !level.isPositionEntityTicking(site.guardCell(1)))
        {
            return;
        }
        KnightRuinData.Entry entry = data.find(site.id());
        if (entry != null && entry.bothBeaten())
        {
            if (level.getGameTime() - entry.beatenAt() < returnTicks() || nearestPlayer(level, site) <= KnightRuinTuning.CLEAR_RANGE)
            {
                return;
            }
            data.reset(site.id());
            LOGGER.info("Knight ruin {}: its knights come back", site.describe());
        }
        entry = data.entry(site.id());
        if (level.getDifficulty() == Difficulty.PEACEFUL)
        {
            return;
        }
        List<KnightKind> kinds = kindsOf(site.element());
        for (int slot = 0; slot < 2 && slot < kinds.size(); slot++)
        {
            if (entry.beaten(slot))
            {
                continue;
            }
            UUID known = entry.knight(slot);
            if (known != null)
            {
                Entity there = level.getEntity(known);
                if (there != null && there.isAlive())
                {
                    continue;
                }
            }
            spawn(level, data, site, slot, kinds.get(slot));
        }
    }

    /** What the keeper sees at a ruin (for the tests' messages and the log). */
    static String state(ServerLevel level, RuinSite site)
    {
        KnightRuinData.Entry e = KnightRuinData.get(level).find(site.id());
        return "nearest player " + (int) nearestPlayer(level, site) + " blocks, land ticking " + level.isPositionEntityTicking(site.ground())
                + "/" + level.isPositionEntityTicking(site.guardCell(0)) + "/" + level.isPositionEntityTicking(site.guardCell(1))
                + ", built " + built(level, site) + ", " + level.getDifficulty() + ", "
                + (e == null ? "no record" : "tidied " + e.tidied() + ", beaten " + e.beaten(0) + "/" + e.beaten(1) + ", both beaten "
                + (e.bothBeaten() ? (level.getGameTime() - e.beatenAt()) + " ticks ago (back after " + returnTicks() + ")" : "no")
                + ", knights " + e.knight(0) + "/" + e.knight(1));
    }

    /** True if the ruin's structure is really there (its start was built with the land). */
    static boolean built(ServerLevel level, RuinSite site)
    {
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(STRUCTURE);
        return structure != null && level.structureManager().getStructureWithPieceAt(site.ground(), structure).isValid();
    }

    @Nullable
    private static ElementalKnight spawn(ServerLevel level, KnightRuinData data, RuinSite site, int slot, KnightKind kind)
    {
        ElementalKnight knight = ModEntities.KNIGHTS.get(kind).get().create(level);
        if (knight == null)
        {
            return null;
        }
        BlockPos cell = site.guardCell(slot);
        float yaw = site.guardYaw(slot);
        knight.moveTo(cell.getX() + 0.5D, cell.getY(), cell.getZ() + 0.5D, yaw, 0.0F);
        knight.setYBodyRot(yaw);
        knight.setYHeadRot(yaw);
        knight.guard(site.id(), cell, slot);
        knight.finalizeSpawn(level, level.getCurrentDifficultyAt(cell), MobSpawnType.STRUCTURE, null, null);
        level.addFreshEntity(knight);
        data.spawned(site.id(), slot, knight.getUUID());
        return knight;
    }

    /** Its element's two knights, in KnightKind's order (as the showcase stands them). */
    static List<KnightKind> kindsOf(KnightKind.Element element)
    {
        return Arrays.stream(KnightKind.values()).filter(k -> k.element == element).toList();
    }

    static long returnTicks()
    {
        return DrossConfig.ruinKnightsReturnMinutes() * 60L * 20L;
    }

    /** The distance to the nearest player (not a spectator) in the level, or {@link Double#MAX_VALUE}. */
    static double nearestPlayer(ServerLevel level, RuinSite site)
    {
        double best = Double.MAX_VALUE;
        for (ServerPlayer p : level.players())
        {
            if (!p.isSpectator())
            {
                best = Math.min(best, Math.sqrt(p.distanceToSqr(site.x() + 0.5D, site.groundY() + 1.0D, site.z() + 0.5D)));
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- the knights' deaths and comings

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event)
    {
        if (!(event.getEntity() instanceof ElementalKnight knight) || knight.ruin() == null || !(knight.level() instanceof ServerLevel level)
                || level.dimension() != Level.OVERWORLD)
        {
            return;
        }
        String id = knight.ruin();
        KnightRuinData data = KnightRuinData.get(level);
        KnightRuinData.Entry entry = data.find(id);
        int slot = knight.ruinSlot();
        if (entry == null || (entry.knight(slot) != null && !entry.knight(slot).equals(knight.getUUID())) || entry.beaten(slot))
        {
            return; // (not this ruin's knight now: a stray copy)
        }
        if (!data.fell(id, slot, level.getGameTime()))
        {
            return;
        }
        RuinSite site = KnightRuinSites.byId(level, id);
        Entity killer = event.getSource().getEntity();
        ServerPlayer credited = killer instanceof ServerPlayer sp ? sp
                : killer instanceof OwnableEntity pet && pet.getOwner() instanceof ServerPlayer owner ? owner : null;
        Set<ServerPlayer> players = new LinkedHashSet<>();
        if (credited != null)
        {
            players.add(credited);
        }
        BlockPos home = site != null ? site.ground() : knight.ruinHome();
        double range = KnightRuinTuning.CREDIT_RANGE;
        for (ServerPlayer p : level.players())
        {
            if (!p.isSpectator() && home != null && p.distanceToSqr(home.getX() + 0.5D, home.getY() + 1.0D, home.getZ() + 0.5D) <= range * range)
            {
                players.add(p);
            }
        }
        LOGGER.info("Knight ruin {}: both its knights are beaten (cleared by {})", site != null ? site.describe() : id,
                players.stream().map(p -> p.getGameProfile().getName()).toList());
        for (ServerPlayer p : players)
        {
            boolean first = !data.entry(id).clearedBy().contains(p.getUUID());
            data.cleared(id, p.getUUID());
            if (site != null)
            {
                MinecraftForge.EVENT_BUS.post(new KnightRuinClearedEvent(p, site, first, p == credited));
            }
        }
    }

    /** A copy of a ruin's knight that isn't its knight now (an old one loading after it came again) never joins. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event)
    {
        if (event.loadedFromDisk() && event.getEntity() instanceof ElementalKnight knight && knight.ruin() != null
                && event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD)
        {
            KnightRuinData.Entry entry = KnightRuinData.get(level).find(knight.ruin());
            int slot = knight.ruinSlot();
            if (entry != null && (entry.beaten(slot) || (entry.knight(slot) != null && !entry.knight(slot).equals(knight.getUUID()))))
            {
                event.setCanceled(true);
            }
        }
    }

    /** A knight that left without being beaten (Peaceful, or taken away) leaves its place empty: it comes again. */
    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event)
    {
        if (!(event.getEntity() instanceof ElementalKnight knight) || knight.ruin() == null || !(event.getLevel() instanceof ServerLevel level)
                || level.dimension() != Level.OVERWORLD)
        {
            return;
        }
        Entity.RemovalReason reason = knight.getRemovalReason();
        if (reason == null || reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.UNLOADED_TO_CHUNK
                || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER)
        {
            return;
        }
        KnightRuinData data = KnightRuinData.get(level);
        KnightRuinData.Entry entry = data.find(knight.ruin());
        int slot = knight.ruinSlot();
        if (entry != null && knight.getUUID().equals(entry.knight(slot)) && !entry.beaten(slot))
        {
            data.lost(knight.ruin(), slot);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event)
    {
        TIDYING.clear();
    }

    private KnightRuinKeeper() {}
}
