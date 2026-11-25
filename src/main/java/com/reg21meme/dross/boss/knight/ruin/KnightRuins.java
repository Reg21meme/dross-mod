package com.reg21meme.dross.boss.knight.ruin;

import com.reg21meme.dross.boss.knight.KnightKind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The knight ruins for other areas (the quest that will count them, the commands): where they are and who has cleared
 * them. A ruin is cleared when both its knights are beaten; each time, every player who cleared it gets a
 * {@link KnightRuinClearedEvent} on Forge's event bus. Ruins stand in the Overworld and (since PRE_DROSS_PLAN.md's stage
 * T3) in the Nether: the methods taking a server are the Overworld's alone (as they always were); those taking a level
 * are that level's dimension's (the Nether's for the Nether, else the Overworld's). Who has cleared a ruin is one record
 * for both. Nothing here generates a chunk. The searches work regions out if they haven't been yet (a moment's work
 * each): from the server thread use {@link #nearestAsync}.
 */
public final class KnightRuins
{
    /** True if the world has knight ruins (its Overworld generates them: not with Generate Structures off or superflat). */
    public static boolean available(MinecraftServer server)
    {
        return KnightRuinSites.available(server.overworld());
    }

    /** The few ruins near the world spawn (the settings' {@code nearSpawn}), nearest the spawn's own patch first. */
    public static List<RuinSite> nearSpawn(MinecraftServer server)
    {
        return KnightRuinSites.nearSpawn(server.overworld());
    }

    /** The nearest ruin to a spot (of an element, or any) within {@code maxRadius} blocks. May take a moment. */
    public static Optional<RuinSite> nearest(MinecraftServer server, BlockPos from, @Nullable KnightKind.Element element, int maxRadius)
    {
        return KnightRuinSites.nearest(server.overworld(), from.getX(), from.getZ(), element, maxRadius);
    }

    /** {@link #nearest}, worked out off the server thread (the answer comes on that other thread: hand it back with
     * {@code server.execute}). */
    public static CompletableFuture<Optional<RuinSite>> nearestAsync(MinecraftServer server, BlockPos from, @Nullable KnightKind.Element element,
                                                                    int maxRadius)
    {
        ServerLevel overworld = server.overworld();
        return CompletableFuture.supplyAsync(() -> KnightRuinSites.nearest(overworld, from.getX(), from.getZ(), element, maxRadius));
    }

    /** Every ruin within {@code radius} blocks of a spot, nearest first. May take a moment for a big radius. */
    public static List<RuinSite> within(MinecraftServer server, BlockPos from, int radius)
    {
        return KnightRuinSites.within(server.overworld(), from.getX(), from.getZ(), radius);
    }

    /** The ruin with this id ({@link RuinSite#id}: a Nether ruin's too), or empty. */
    public static Optional<RuinSite> byId(MinecraftServer server, String id)
    {
        return Optional.ofNullable(KnightRuinKeeper.siteById(server, id));
    }

    // ---------------------------------------------------------------- by dimension (the commands: the Nether's from the Nether)

    /** The level whose ruins a level's searches go to: the Nether itself, else the Overworld (the Dross and the End have none). */
    public static ServerLevel searchLevel(ServerLevel level)
    {
        return level.dimension() == Level.NETHER ? level : level.getServer().overworld();
    }

    /** True if that dimension has knight ruins (see {@link #searchLevel}). */
    public static boolean available(ServerLevel level)
    {
        ServerLevel search = searchLevel(level);
        return search.dimension() == Level.NETHER ? NetherRuinSites.available(search) : KnightRuinSites.available(search);
    }

    /** {@link #nearestAsync} in that dimension (see {@link #searchLevel}). */
    public static CompletableFuture<Optional<RuinSite>> nearestAsync(ServerLevel level, BlockPos from, @Nullable KnightKind.Element element,
                                                                    int maxRadius)
    {
        ServerLevel search = searchLevel(level);
        if (search.dimension() == Level.NETHER)
        {
            return CompletableFuture.supplyAsync(() -> NetherRuinSites.nearest(search, from.getX(), from.getZ(), element, maxRadius));
        }
        return CompletableFuture.supplyAsync(() -> KnightRuinSites.nearest(search, from.getX(), from.getZ(), element, maxRadius));
    }

    /** {@link #within} in that dimension (see {@link #searchLevel}). May take a moment for a big radius. */
    public static List<RuinSite> within(ServerLevel level, BlockPos from, int radius)
    {
        ServerLevel search = searchLevel(level);
        return search.dimension() == Level.NETHER ? NetherRuinSites.within(search, from.getX(), from.getZ(), radius)
                : KnightRuinSites.within(search, from.getX(), from.getZ(), radius);
    }

    /** The level a ruin stands in (null only if the server has no Nether). */
    @Nullable
    public static ServerLevel levelOf(MinecraftServer server, RuinSite ruin)
    {
        return server.getLevel(ruin.dimension());
    }

    /** Where a visitor lands at a Nether ruin (its chunks loaded): safe ground round its way in, read up and down from its
     * ground (a heightmap would find the Nether's roof); empty if there's none. */
    public static Optional<Vec3> netherLanding(ServerLevel nether, RuinSite ruin)
    {
        return NetherRuinSites.landing(nether, ruin);
    }

    /** True if this player has cleared this ruin (beaten both its knights, or been near when they fell) at least once. */
    public static boolean hasCleared(MinecraftServer server, UUID player, RuinSite ruin)
    {
        KnightRuinData.Entry e = KnightRuinData.get(server.overworld()).find(ruin.id());
        return e != null && e.clearedBy().contains(player);
    }

    /** The players who have cleared this ruin. */
    public static Set<UUID> clearedBy(MinecraftServer server, RuinSite ruin)
    {
        KnightRuinData.Entry e = KnightRuinData.get(server.overworld()).find(ruin.id());
        return e == null ? Set.of() : e.clearedBy();
    }

    /** True if the ruin's two knights are beaten now (until they come back). */
    public static boolean knightsBeaten(MinecraftServer server, RuinSite ruin)
    {
        KnightRuinData.Entry e = KnightRuinData.get(server.overworld()).find(ruin.id());
        return e != null && e.bothBeaten();
    }

    private KnightRuins() {}
}
