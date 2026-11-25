package com.reg21meme.dross.quest.trader;

import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.quest.DrossAdvancements;
import com.reg21meme.dross.quest.QuestProgressData;
import com.reg21meme.dross.quest.oracle.OracleMarker;
import com.reg21meme.dross.quest.oracle.OracleProgress;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.registry.ModItems;
import com.reg21meme.dross.world.PortalSite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Optional;

/**
 * THE DROSS TRADER'S QUEST: each player's progress, reading it, and the only way to change it (PRE_DROSS_PLAN.md, "His
 * quests"). Every change is saved ({@link QuestProgressData}, per player and per world) and sent to the player's game
 * with the Blind Oracle's ({@link OracleProgress#sync}: one message carries both markers).
 *
 * <p>How it goes: {@link TraderState#OFFER} (his story, then your "Yes, of course.": 3 golden apples) →
 * {@link TraderState#RUINS} (clear {@value #RUINS_NEEDED} knight ruins you've never cleared before) →
 * {@link TraderState#READY} (go back to him: he makes the Portal Orb and tells you where the cathedral is) →
 * {@link TraderState#ORB_GIVEN}. A lost orb (none in your inventory, the cathedral's portal not lit yet) starts another
 * round of {@value #RUINS_NEEDED} new ruins for a new one. The talk is {@link TraderDialogue}.</p>
 */
public final class TraderProgress
{
    /**
     * Knight ruins to clear for an orb (the first, and each new one). His lines say "three" in words: change them in
     * {@code en_us.json} with it.
     */
    public static final int RUINS_NEEDED = 3;
    /**
     * Knight ruins to clear for a new orb after a lost one (the user, November 25, 2026: "make him clear 2 instead of 3,
     * say I have some left over souls"). His lost-orb line says "two" in words.
     */
    public static final int NEW_ORB_RUINS = 2;
    /** Golden apples he gives when you agree to help. */
    public static final int GOLDEN_APPLES = 3;
    /** A ruin's spot in his lines is rounded to this many blocks. */
    public static final int RUIN_ROUNDING = 10;
    /** The cathedral's spot in his lines is rounded to this many blocks ("around 7500, 1200"). */
    public static final int CATHEDRAL_ROUNDING = 100;

    /** His offer's lines ({@code dross.trader.offer.<n>}), a click each; the click after the last is your answer. */
    public static final int OFFER_LINES = 6;
    /** His hand-in lines for the first orb ({@code dross.trader.done.<n>}), a click each. */
    public static final int DONE_LINES = 4;
    /** The hand-in line he makes the orb on (1 is the first)... */
    public static final int ORB_LINE = 2;
    /**
     * ...and the one that tells you where the cathedral is: the same line (the user, November 25, 2026: "combine some of
     * the dialogue ... like the portal orb and the cathedral words").
     */
    public static final int CATHEDRAL_LINE = 2;
    /** His lines for a player who has been to the Dross ({@code dross.trader.after.<n>}), in turn. */
    public static final int AFTER_LINES = 3;

    private TraderProgress() {}

    // ------------------------------------------------------------------ Reading

    public static TraderState state(ServerPlayer player)
    {
        return record(player).state();
    }

    /** Ruins cleared for the first time in this round. */
    public static int ruinsCleared(ServerPlayer player)
    {
        return record(player).ruins();
    }

    /** The round of ruins (1 for the first orb, 2 and on after a lost one; 0 before his offer). */
    public static int round(ServerPlayer player)
    {
        return record(player).round();
    }

    /** How many Portal Orbs he's made for them. */
    public static int orbsMade(ServerPlayer player)
    {
        return record(player).orbs();
    }

    /** The marker over the trader for this player. */
    public static OracleMarker marker(ServerPlayer player)
    {
        return state(player).marker();
    }

    /** True once he's made them a Portal Orb: his shop is open to them (it sells only the Guide Book). */
    public static boolean isShopOpen(ServerPlayer player)
    {
        return record(player).orbs() > 0;
    }

    /** True if a Portal Orb is anywhere in their inventory (armour and off hand included). */
    public static boolean hasOrb(ServerPlayer player)
    {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
        {
            if (player.getInventory().getItem(i).is(ModItems.PORTAL_ORB.get()))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * True once the Fallen Cathedral's portal is lit (by anyone in this world): saved when an orb lights it
     * ({@link #portalLit}), or seen in its loaded chunk (never loads one).
     */
    public static boolean isCathedralLit(MinecraftServer server)
    {
        ServerLevel overworld = server.overworld();
        QuestProgressData data = QuestProgressData.get(overworld);
        if (data.isCathedralLit())
        {
            return true;
        }
        if (!PortalSite.isPlaced(overworld))
        {
            return false;
        }
        BlockPos center = PortalSite.getOpeningCenter(overworld);
        LevelChunk chunk = overworld.getChunkSource().getChunkNow(center.getX() >> 4, center.getZ() >> 4);
        if (chunk != null && chunk.getBlockState(center).is(ModBlocks.DROSS_PORTAL.get()))
        {
            data.markCathedralLit();
            return true;
        }
        return false;
    }

    /** The cathedral's spot (its frame opening's middle), or empty in a world with no cathedral. Arithmetic: loads nothing. */
    public static Optional<BlockPos> cathedral(MinecraftServer server)
    {
        ServerLevel overworld = server.overworld();
        return PortalSite.isPlaced(overworld) ? Optional.of(PortalSite.getOpeningCenter(overworld)) : Optional.empty();
    }

    /** A coordinate rounded to the nearest {@code step} blocks (7531 to the nearest 100: 7500; -1250: -1200). */
    public static int rounded(int coordinate, int step)
    {
        return (int) Math.round(coordinate / (double) step) * step;
    }

    /** His line naming the cathedral's spot, rounded to {@value #CATHEDRAL_ROUNDING}: {@code key} (X, Z), or {@code key.none}. */
    static Component cathedralLine(MinecraftServer server, String key)
    {
        Optional<BlockPos> spot = cathedral(server);
        return spot.<Component>map(pos -> Component.translatable(key, rounded(pos.getX(), CATHEDRAL_ROUNDING),
                rounded(pos.getZ(), CATHEDRAL_ROUNDING))).orElseGet(() -> Component.translatable(key + ".none"));
    }

    // ------------------------------------------------------------------ The world (the portal area)

    /**
     * A Dross frame's opening (its bottom corner, the way it runs, its size) has just been lit in this level (the portal
     * area calls this): if it's the Fallen Cathedral's, it's saved as lit for the whole world.
     */
    public static void portalLit(ServerLevel level, BlockPos minCorner, Direction.Axis axis, int width, int height)
    {
        if (level.dimension() != Level.OVERWORLD || !PortalSite.isPlaced(level))
        {
            return;
        }
        BlockPos center = PortalSite.getOpeningCenter(level);
        boolean alongX = axis == Direction.Axis.X;
        int along = alongX ? center.getX() - minCorner.getX() : center.getZ() - minCorner.getZ();
        int across = alongX ? center.getZ() - minCorner.getZ() : center.getX() - minCorner.getX();
        int up = center.getY() - minCorner.getY();
        if (across == 0 && along >= 0 && along < width && up >= 0 && up < height)
        {
            QuestProgressData.get(level.getServer().overworld()).markCathedralLit();
        }
    }

    // ------------------------------------------------------------------ Ruins (TraderQuestEvents)

    /**
     * They cleared a knight ruin (both its knights beaten). It counts only the first time they clear that ruin, and only
     * while they're clearing ruins for him: progress above the hotbar, and the last one makes it READY ("Knightfall").
     */
    public static void ruinCleared(ServerPlayer player, boolean firstTime)
    {
        TraderRecord record = record(player);
        if (!firstTime || record.state() != TraderState.RUINS)
        {
            return;
        }
        int needed = ruinsNeeded(record);
        record.ruins = Math.min(needed, record.ruins + 1);
        if (record.ruins >= needed)
        {
            record.enter(TraderState.READY);
            DrossAdvancements.grant(player, DrossAdvancements.KNIGHTFALL);
            player.displayClientMessage(Component.translatable("dross.trader.return", record.ruins, needed)
                    .withStyle(style -> style.withColor(DrossColors.TRADER_DONE_TEXT)), true);
        }
        else
        {
            player.displayClientMessage(Component.translatable("dross.trader.progress", record.ruins, needed)
                    .withStyle(style -> style.withColor(DrossColors.TRADER_PROGRESS_TEXT)), true);
        }
        changed(player);
    }

    /** Ruins this round wants: {@value #RUINS_NEEDED} for the first orb, {@value #NEW_ORB_RUINS} for a new one. */
    public static int ruinsNeeded(TraderRecord record)
    {
        return record.isReplacementRound() ? NEW_ORB_RUINS : RUINS_NEEDED;
    }

    /** Ruins this player's round wants. */
    public static int ruinsNeeded(ServerPlayer player)
    {
        return ruinsNeeded(record(player));
    }

    // ------------------------------------------------------------------ Test commands

    /** Clears their trader quest (back to his offer). Advancements and items are left as they are. */
    public static void reset(ServerPlayer player)
    {
        record(player).clear();
        changed(player);
    }

    /**
     * Marks his quest done, as if he'd just given them an orb (no items given): ruins cleared, "Knightfall" and "The
     * Portal Orb" granted.
     */
    public static void complete(ServerPlayer player)
    {
        TraderRecord record = record(player);
        record.round = Math.max(1, record.round);
        record.ruins = RUINS_NEEDED;
        record.orbs = Math.max(1, record.orbs);
        record.enter(TraderState.ORB_GIVEN);
        DrossAdvancements.grant(player, DrossAdvancements.KNIGHTFALL);
        DrossAdvancements.grant(player, DrossAdvancements.PORTAL_ORB);
        changed(player);
    }

    // ------------------------------------------------------------------ This package (TraderDialogue)

    static TraderRecord record(ServerPlayer player)
    {
        return QuestProgressData.get(player.server.overworld()).trader(player.getUUID());
    }

    /** Saves their progress after a change their game needn't hear about (the line he's at). */
    static void saved(ServerPlayer player)
    {
        QuestProgressData.get(player.server.overworld()).setDirty();
    }

    /** Saves and sends their progress (both quest markers) after a change. */
    static void changed(ServerPlayer player)
    {
        QuestProgressData.get(player.server.overworld()).setDirty();
        OracleProgress.sync(player);
    }

    /** They said yes: the golden apples, and the first round of ruins begins (his two last thanks lines still to come). */
    static void accept(ServerPlayer player)
    {
        TraderRecord record = record(player);
        give(player, new ItemStack(Items.GOLDEN_APPLE, GOLDEN_APPLES));
        record.round = 1;
        record.ruins = 0;
        record.enter(TraderState.RUINS);
        changed(player);
    }

    /** He's made them a Portal Orb: it's theirs ("The Portal Orb"). */
    static void makeOrb(ServerPlayer player)
    {
        TraderRecord record = record(player);
        give(player, new ItemStack(ModItems.PORTAL_ORB.get()));
        record.orbs++;
        DrossAdvancements.grant(player, DrossAdvancements.PORTAL_ORB);
        changed(player);
    }

    /** His hand-in lines are over: the orb's given. */
    static void handedIn(ServerPlayer player)
    {
        record(player).enter(TraderState.ORB_GIVEN);
        changed(player);
    }

    /** They've lost their orb: another round of new ruins for a new one (his reminder at once, no thanks lines). */
    static void orbLost(ServerPlayer player)
    {
        TraderRecord record = record(player);
        record.round = Math.max(1, record.round) + 1;
        record.ruins = 0;
        record.enter(TraderState.RUINS);
        record.line = 1;
        changed(player);
    }

    /** A round for a new orb that's no longer needed (they found it, the portal's lit, they've been through): back to ORB_GIVEN. */
    static void cancelRound(ServerPlayer player)
    {
        record(player).enter(TraderState.ORB_GIVEN);
        changed(player);
    }

    /** Puts the item in the player's inventory, or drops it at their feet (kept for them) if it's full. */
    static void give(ServerPlayer player, ItemStack stack)
    {
        if (!player.getInventory().add(stack) && !stack.isEmpty())
        {
            ItemEntity entity = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack);
            entity.setDeltaMovement(0, 0, 0);
            entity.setNoPickUpDelay();
            entity.setTarget(player.getUUID());
            player.level().addFreshEntity(entity);
        }
    }
}
