package com.reg21meme.dross.dimension.ward;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.quest.oracle.OracleRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * THE LAND WARDS (QUEST_PLAN.md, "Locked lands"; server side). A land of the Dross the player's Oracle quest hasn't
 * opened yet rejects them: while they're in it they're slowed to a crawl (Slowness V), can barely mine (Mining Fatigue V)
 * and hungry (Hunger II), and a line in chat
 * says "This land rejects you. You cannot enter yet." as they come in and then every 20 seconds while they stay.
 *
 * <p>Which land they're in goes by the <b>column</b> they stand in, its surface biome ({@link #landAt}), so digging under
 * a land or flying over it changes nothing (and the sewers under Blightwood count as Blightwood). Which lands are open
 * is the quest area's rule ({@link OracleRules#isLandOpen}): today the Cinder Wastes open with "Into the Ashes",
 * Blightwood with "Beneath the Rot", Rimefall with "First Frost". Players in creative or spectator mode are never
 * warded. The edge players see is client side: {@code client/LandWardEdge}.</p>
 *
 * <p>The three effects are short and topped up every {@value #CHECK_EVERY_TICKS} ticks while the player stays, their
 * particles hidden; the moment they step back into open land (or the land opens, or they leave the Dross, or switch
 * to creative) the ward's own effects are taken off again (only the ward's: {@link #isWardEffect}).</p>
 */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LandWard
{
    // ------------------------------------------------------------------ Tuning

    /** The players in the Dross are checked every this many server ticks (20 a second). */
    public static final int CHECK_EVERY_TICKS = 10;
    /**
     * A column's land is read at this height: the surface biome. The Dross's biomes are the same at every height but
     * for the sewers, which lie only under y 64 under Blightwood (tools/terrain/worldgen.py, SEWERS).
     */
    public static final int COLUMN_BIOME_Y = 100;
    /** Slowness V (an amplifier of 4 is level V; the user, November 9, 2026: "slowness 5 and mining fatigue 5"). */
    public static final int SLOWNESS_AMPLIFIER = 4;
    /** Mining Fatigue V. */
    public static final int MINING_FATIGUE_AMPLIFIER = 4;
    /** Hunger II. */
    public static final int HUNGER_AMPLIFIER = 1;
    /**
     * How long each effect is given for (ticks), topped up at every check. It's taken off at once when the player leaves
     * the land, so this only needs to be long enough not to run out between checks; it's kept just over 10 seconds
     * because Minecraft makes an effect's icon blink when it has less than that left.
     */
    public static final int EFFECT_TICKS = 220;
    /** The chat line comes again every this many ticks while the player stays (20 seconds). */
    public static final int MESSAGE_EVERY_TICKS = 400;
    /**
     * Coming back in sooner than this many ticks after the last line (5 seconds) doesn't say it again, so walking along
     * the border in and out doesn't fill the chat.
     */
    public static final int MESSAGE_MIN_GAP_TICKS = 100;
    /** The chat line's lang key. */
    public static final String MESSAGE_KEY = "dross.land_ward.rejected";

    /** Each warded player's state (only while they're in a locked land; forgotten when they log out). */
    private static final Map<UUID, Warded> WARDED = new HashMap<>();
    /** When each player last got the chat line (server tick), kept after they step out for the gap above. */
    private static final Map<UUID, Long> LAST_MESSAGE = new HashMap<>();

    private record Warded(ResourceKey<Biome> land) {}

    private LandWard() {}

    // ------------------------------------------------------------------ The land of a column

    /**
     * The land of the column at x, z: its surface biome, read at {@value #COLUMN_BIOME_Y} whatever height anyone is at.
     * The server and a player's game read it the same way (from the chunk's biomes, with Minecraft's own blur at the
     * borders), so the edge a player sees lies where the ward starts. Null if the biome has no key (never in the Dross).
     */
    @Nullable
    public static ResourceKey<Biome> landAt(Level level, int x, int z)
    {
        Holder<Biome> biome = level.getBiome(new BlockPos(x, COLUMN_BIOME_Y, z));
        return biome.unwrapKey().orElse(null);
    }

    /**
     * The locked land this player is in, or null: they must be in the Dross, alive, not in creative or spectator mode,
     * and the land of their column not open for them yet.
     */
    @Nullable
    public static ResourceKey<Biome> lockedLandOf(ServerPlayer player)
    {
        if (!player.level().dimension().equals(ModDimensions.DROSS_LEVEL) || !player.isAlive() || OracleRules.exempt(player))
        {
            return null;
        }
        ResourceKey<Biome> land = landAt(player.level(), player.getBlockX(), player.getBlockZ());
        return land != null && !OracleRules.isLandOpen(player, land) ? land : null;
    }

    // ------------------------------------------------------------------ The check

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % CHECK_EVERY_TICKS != 0)
        {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel dross = server.getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null || dross.players().isEmpty())
        {
            return;
        }
        for (ServerPlayer player : List.copyOf(dross.players()))
        {
            check(player);
        }
    }

    /**
     * Checks one player now (the server does it for everyone in the Dross every {@value #CHECK_EVERY_TICKS} ticks): in a
     * land that's locked for them, the two effects are topped up and the chat line sent when it's due (as they come in,
     * then every {@value #MESSAGE_EVERY_TICKS} ticks); anywhere else the ward's own effects are taken off.
     *
     * @return true if they're warded now
     */
    public static boolean check(ServerPlayer player)
    {
        UUID id = player.getUUID();
        ResourceKey<Biome> land = lockedLandOf(player);
        if (land == null)
        {
            if (WARDED.remove(id) != null || hasWardEffect(player))
            {
                lift(player);
            }
            return false;
        }
        long now = player.server.getTickCount();
        boolean entering = WARDED.put(id, new Warded(land)) == null;
        Long last = LAST_MESSAGE.get(id);
        boolean due = last == null || now - last >= (entering ? MESSAGE_MIN_GAP_TICKS : MESSAGE_EVERY_TICKS);
        give(player);
        if (due)
        {
            player.sendSystemMessage(Component.translatable(MESSAGE_KEY)
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(DrossColors.LAND_WARD_TEXT))));
            LAST_MESSAGE.put(id, now);
        }
        return true;
    }

    /** True if the ward has this player now (as of the last check). */
    public static boolean isWarded(ServerPlayer player)
    {
        return WARDED.containsKey(player.getUUID());
    }

    /** Slowness V, Mining Fatigue V and Hunger II, short, their particles hidden (their icons still show). */
    private static void give(ServerPlayer player)
    {
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EFFECT_TICKS, SLOWNESS_AMPLIFIER, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, EFFECT_TICKS, MINING_FATIGUE_AMPLIFIER, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.HUNGER, EFFECT_TICKS, HUNGER_AMPLIFIER, false, false, true));
    }

    /** Takes off the ward's own effects (never a potion's or a beacon's, which look different: {@link #isWardEffect}). */
    public static void lift(ServerPlayer player)
    {
        removeIfWard(player, MobEffects.MOVEMENT_SLOWDOWN, SLOWNESS_AMPLIFIER);
        removeIfWard(player, MobEffects.DIG_SLOWDOWN, MINING_FATIGUE_AMPLIFIER);
        removeIfWard(player, MobEffects.HUNGER, HUNGER_AMPLIFIER);
    }

    private static boolean hasWardEffect(ServerPlayer player)
    {
        return isWardEffect(player.getEffect(MobEffects.MOVEMENT_SLOWDOWN), SLOWNESS_AMPLIFIER)
                || isWardEffect(player.getEffect(MobEffects.DIG_SLOWDOWN), MINING_FATIGUE_AMPLIFIER)
                || isWardEffect(player.getEffect(MobEffects.HUNGER), HUNGER_AMPLIFIER);
    }

    private static void removeIfWard(ServerPlayer player, MobEffect effect, int amplifier)
    {
        if (isWardEffect(player.getEffect(effect), amplifier))
        {
            player.removeEffect(effect);
        }
    }

    /**
     * True if this looks like the ward's own effect: its level, no particles, not a beacon's, and no longer than the
     * ward gives (not endless).
     */
    public static boolean isWardEffect(@Nullable MobEffectInstance instance, int amplifier)
    {
        return instance != null && instance.getAmplifier() == amplifier && !instance.isVisible() && !instance.isAmbient()
                && !instance.isInfiniteDuration() && instance.getDuration() <= EFFECT_TICKS;
    }

    // ------------------------------------------------------------------ Leaving

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        if (event.getFrom().equals(ModDimensions.DROSS_LEVEL) && event.getEntity() instanceof ServerPlayer player)
        {
            WARDED.remove(player.getUUID());
            lift(player);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event)
    {
        WARDED.remove(event.getEntity().getUUID());
        LAST_MESSAGE.remove(event.getEntity().getUUID());
    }
}
