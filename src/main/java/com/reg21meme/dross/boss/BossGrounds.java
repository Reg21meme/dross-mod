package com.reg21meme.dross.boss;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.emberdactyl.CraterWard;
import com.reg21meme.dross.boss.lair.LairWard;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.sewer.Sewers;
import com.reg21meme.dross.portal.DrossHub;
import com.reg21meme.dross.quest.oracle.OracleBoss;
import com.reg21meme.dross.quest.oracle.OracleProgress;
import com.reg21meme.dross.quest.oracle.OracleQuest;
import com.reg21meme.dross.quest.oracle.OracleRules;
import com.reg21meme.dross.quest.oracle.OracleState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * THE BOSSES' PROTECTED GROUNDS (server side; the user, November 9, 2026: "protect all the blocks in the bosses'
 * structures, saying 'Magic protects this block', and putting extreme mining fatigue while you mine it, like mining
 * fatigue 99"). The blocks of a boss's home in the Dross can't be broken by a player who hasn't beaten that boss yet:
 * <ul>
 *   <li>the Cinder Colossus's lairs: the volcano, its cavern and way in ({@link LairWard#protects});</li>
 *   <li>the Rat King's sewer: every piece of the network that holds his rat cave, its grand entrance, royal road,
 *       tunnels and the cave with his nest and hoard (the structure {@code dross:rat_cave}; the regular sewers aren't);</li>
 *   <li>the Huntsman's villages (the structure {@code dross:huntsman_village}; the regular Frostfolk villages aren't);</li>
 *   <li>the Emberdactyl's volcanoes: the cone, its crater and nest, the pools and crust at its foot
 *       ({@link CraterWard#protects}).</li>
 * </ul>
 * Mining one gives Mining Fatigue level {@value #FATIGUE_LEVEL} while they keep at it (topped up, its particles hidden) and
 * "Magic protects this block." over the hotbar; the block never breaks (the break is cancelled too, for anything the
 * fatigue doesn't stop: a block that breaks in one hit). Players in creative or spectator mode, and players who have
 * beaten that boss (their Oracle quest for it is done: the protection lifts for them, so the Rat King's hoard and the
 * Emberdactyl's gold are theirs to dig), aren't stopped. Test arenas, showcases and a boss's {@code arena} volcano aren't
 * the world's homes and aren't protected.
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
    // ------------------------------------------------------------------ Tuning

    /** Mining Fatigue's level while mining a protected block (the user: "mining fatigue 99")... */
    public static final int FATIGUE_LEVEL = 99;
    /** ...given for this many ticks, topped up while they keep mining (so it's gone a moment after they stop)... */
    public static final int FATIGUE_TICKS = 30;
    /** ...topped up whenever less than this many ticks of it are left. */
    public static final int FATIGUE_TOP_UP_BELOW = 20;
    /** The line over the hotbar comes at most every this many ticks for a player. */
    public static final int MESSAGE_GAP_TICKS = 40;
    /** The line's lang key: "Magic protects this block." */
    public static final String MESSAGE_KEY = "dross.boss_grounds.protected";
    /** What it says at the Huntsman's village while he sleeps (a player his quest hasn't reached yet: he's asleep in his
     * lodge; the user, November 18, 2026: "magic protects his house while he sleeps"). */
    public static final String HUNTSMAN_ASLEEP_KEY = "dross.boss_grounds.protected.huntsman";

    /** The Huntsman's villages' structure (the boss area's {@code HunterVillageStructure}). */
    public static final ResourceKey<Structure> HUNTSMAN_VILLAGE = ResourceKey.create(Registries.STRUCTURE,
            new ResourceLocation(Dross.MODID, "huntsman_village"));

    /** When each player last got the line (server tick). */
    private static final Map<UUID, Long> LAST_MESSAGE = new HashMap<>();

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
        return level.dimension().equals(ModDimensions.DROSS_LEVEL) && DrossHub.isOnHub(pos.getX(), pos.getZ(), 0)
                && pos.getY() >= DrossHub.groundY(level) - HUB_DEPTH;
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

    /** True if this player has beaten this boss: their Oracle quest is past its quest, or on it and done. */
    public static boolean hasBeaten(ServerPlayer player, OracleBoss boss)
    {
        OracleQuest quest = boss.quest();
        OracleQuest current = OracleProgress.current(player);
        OracleState state = OracleProgress.state(player);
        return current.index() > quest.index()
                || (current.index() == quest.index() && (state == OracleState.DONE || state == OracleState.FINISHED));
    }

    private static boolean inStructure(ServerLevel level, BlockPos pos, ResourceKey<Structure> key)
    {
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(key);
        return structure != null && level.structureManager().getStructureWithPieceAt(pos, structure).isValid();
    }

    // ------------------------------------------------------------------ The hooks

    /** Starting to mine a protected block: the line, and the fatigue. */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event)
    {
        if (event.getEntity() instanceof ServerPlayer player && blocked(player, event.getPos()))
        {
            tire(player);
            tell(player, event.getPos());
        }
    }

    /** While mining one: it never gets anywhere, and the fatigue is kept up. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event)
    {
        if (event.getEntity() instanceof ServerPlayer player && event.getPosition().isPresent()
                && blocked(player, event.getPosition().get()))
        {
            event.setNewSpeed(0.0F);
            tire(player);
        }
    }

    /** Anything the fatigue didn't stop (a block that breaks in one hit) isn't broken either. */
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

    private static void tire(ServerPlayer player)
    {
        MobEffectInstance now = player.getEffect(MobEffects.DIG_SLOWDOWN);
        if (now == null || now.getAmplifier() < FATIGUE_LEVEL - 1 || now.getDuration() < FATIGUE_TOP_UP_BELOW)
        {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, FATIGUE_TICKS, FATIGUE_LEVEL - 1, false, false, true));
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
        LAST_MESSAGE.remove(event.getEntity().getUUID());
    }
}
