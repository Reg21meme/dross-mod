package com.reg21meme.dross.boss.knight.ruin;

import com.reg21meme.dross.boss.knight.KnightKind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The knight ruins for other areas (the quest that will count them, the commands): where they are and who has cleared
 * them. A ruin is cleared when both its knights are beaten; each time, every player who cleared it gets a
 * {@link KnightRuinClearedEvent} on Forge's event bus. Every ruin is in the Overworld; nothing here generates a chunk.
 * The searches work regions out if they haven't been yet (a moment's work each): from the server thread use
 * {@link #nearestAsync}.
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

    /** The ruin with this id ({@link RuinSite#id}), or empty. */
    public static Optional<RuinSite> byId(MinecraftServer server, String id)
    {
        return Optional.ofNullable(KnightRuinSites.byId(server.overworld(), id));
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
