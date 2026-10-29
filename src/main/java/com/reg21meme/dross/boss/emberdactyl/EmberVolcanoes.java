package com.reg21meme.dross.boss.emberdactyl;

import com.reg21meme.dross.dimension.sites.Site;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * The Emberdactyl's volcanoes in the Dross, for other areas ({@code /dross goto lair emberdactyl}, {@code goto biome}):
 * one in every group of 3 Cinder Wastes patches (the boss-site planner), built the first time a player comes near
 * ({@link VolcanoPlacer}).
 */
public final class EmberVolcanoes
{
    private EmberVolcanoes() {}

    /** Every volcano built so far. Server thread. */
    public static List<EmberVolcanoData.Volcano> all(MinecraftServer server)
    {
        return EmberVolcanoData.get(server).all();
    }

    /** The volcano of a planned site, if it's built. Server thread. */
    public static Optional<EmberVolcanoData.Volcano> get(MinecraftServer server, Site site)
    {
        return Optional.ofNullable(EmberVolcanoData.get(server).volcano(site.patch().id()));
    }

    /** The planned volcano site nearest {@code from} (built or not), worked out in the background. */
    public static CompletableFuture<Optional<Site>> findNearestSite(ServerLevel dross, BlockPos from)
    {
        return VolcanoPlacer.nearestSite(dross, from);
    }

    /** Runs {@code then} on the server thread once the site's volcano is built (at once if it is; otherwise it's built next). */
    public static void whenBuilt(MinecraftServer server, Site site, Consumer<EmberVolcanoData.Volcano> then)
    {
        VolcanoPlacer.whenBuilt(server, site, then);
    }

    /**
     * The site's volcano planned again, exactly as it was (or will be) built: on the generator's own land, from the seed
     * fixed by the world and its patch. Slow-ish (a second or so): for tests and tools.
     */
    public static EmberVolcano.Plan replan(ServerLevel dross, Site site)
    {
        return EmberVolcano.planOnLand(site.pos().getX(), site.pos().getZ(), VolcanoPlacer.generatorLand(dross),
                VolcanoPlacer.shapeSeed(dross, site));
    }

    /** True while the site's volcano is waiting to be built or being built. Server thread. */
    public static boolean isBeingBuilt(Site site)
    {
        return VolcanoPlacer.isPending(site.patch().id());
    }
}
