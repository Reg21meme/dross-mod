package com.reg21meme.dross;

import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

/**
 * The settings from {@link DrossConfig} that decide where a world's things go (the portal site's distances, the
 * trader's village), saved in the world the first time it loads (data/dross_world_settings.dat in the Overworld's save
 * folder) and read from there ever after, so a world keeps them whatever the file says later. Taken on the server thread
 * when the Overworld loads ({@link #of} from the portal site's and the trader's level-load events, before anything is
 * generated); any other thread gets the server's saved copy.
 */
public final class DrossWorldSettings extends SavedData
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NAME = "dross_world_settings";

    /** What's in use, per server (weak, so a closed world's goes). Guarded by itself. */
    private static final Map<MinecraftServer, Values> IN_USE = new WeakHashMap<>();

    /** The values a world uses. */
    public record Values(int siteMinDistance, int siteMaxDistance, boolean traderAtVillages, int traderSearchRadius)
    {
        static Values fromConfig()
        {
            return new Values(DrossConfig.siteMinDistance(), DrossConfig.siteMaxDistance(), DrossConfig.traderAtVillages(),
                    DrossConfig.traderSearchRadius());
        }

        static Values defaults()
        {
            return new Values(DrossConfig.DEFAULT_SITE_MIN_DISTANCE, DrossConfig.DEFAULT_SITE_MAX_DISTANCE,
                    DrossConfig.DEFAULT_TRADER_AT_VILLAGES, DrossConfig.DEFAULT_TRADER_SEARCH_RADIUS);
        }

        String describe()
        {
            return "portal site " + this.siteMinDistance + " to " + this.siteMaxDistance + " blocks from spawn, trader "
                    + (this.traderAtVillages ? "at a village within " + this.traderSearchRadius + " blocks" : "on his own (no village)");
        }
    }

    private Values values;

    private DrossWorldSettings(Values values)
    {
        this.values = values;
    }

    public Values values()
    {
        return this.values;
    }

    /**
     * The world's values: its saved ones, or (a new world) the file's, saved now. On the server thread this reads (and the
     * first time writes) the save; on any other it gives the server's copy taken then, or the file's values if none was.
     */
    public static Values of(ServerLevel level)
    {
        MinecraftServer server = level.getServer();
        synchronized (IN_USE)
        {
            Values known = IN_USE.get(server);
            if (known != null)
            {
                return known;
            }
        }
        if (!server.isSameThread())
        {
            Values file = Values.fromConfig();
            LOGGER.warn("[Dross] This world's settings were asked for off the server thread before it loaded; using the config's: {}",
                    file.describe());
            return file;
        }
        ServerLevel overworld = level.dimension() == Level.OVERWORLD ? level : server.overworld();
        boolean[] made = {false};
        DrossWorldSettings data = overworld.getDataStorage().computeIfAbsent(DrossWorldSettings::load, () -> {
            made[0] = true;
            DrossWorldSettings fresh = new DrossWorldSettings(Values.fromConfig());
            fresh.setDirty();
            return fresh;
        }, NAME);
        Values values = data.values;
        if (made[0])
        {
            LOGGER.info("[Dross] A new world: its settings from config/dross-common.toml, now saved in it: {}", values.describe());
        }
        else
        {
            Values file = Values.fromConfig();
            if (!file.equals(values))
            {
                LOGGER.info("[Dross] This world keeps the settings it was made with ({}); config/dross-common.toml now says {}, which only "
                        + "new worlds use", values.describe(), file.describe());
            }
        }
        synchronized (IN_USE)
        {
            IN_USE.put(server, values);
        }
        return values;
    }

    private static DrossWorldSettings load(CompoundTag tag)
    {
        Values d = Values.defaults();
        int min = tag.contains("SiteMinDistance") ? tag.getInt("SiteMinDistance") : d.siteMinDistance();
        int max = tag.contains("SiteMaxDistance") ? tag.getInt("SiteMaxDistance") : d.siteMaxDistance();
        boolean atVillages = tag.contains("TraderAtVillages") ? tag.getBoolean("TraderAtVillages") : d.traderAtVillages();
        int radius = tag.contains("TraderSearchRadius") ? tag.getInt("TraderSearchRadius") : d.traderSearchRadius();
        return new DrossWorldSettings(new Values(min, Math.max(min, max), atVillages, radius));
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        tag.putInt("SiteMinDistance", this.values.siteMinDistance());
        tag.putInt("SiteMaxDistance", this.values.siteMaxDistance());
        tag.putBoolean("TraderAtVillages", this.values.traderAtVillages());
        tag.putInt("TraderSearchRadius", this.values.traderSearchRadius());
        return tag;
    }
}
