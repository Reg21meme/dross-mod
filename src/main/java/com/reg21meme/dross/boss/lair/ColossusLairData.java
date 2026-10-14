package com.reg21meme.dross.boss.lair;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * Remembers (in the world save) the Colossus's lair: where it was planned, whether it's built and how it's laid out, and
 * its Colossus (which one, when it was last beaten). Saved as {@code data/dross_colossus_lair.dat} in the Overworld's save
 * folder (the Overworld is always loaded), like the hub's and the trader's data.
 * <ul>
 *   <li>{@code Planned}, {@code PlannedX/Z}: the spot the search picked (kept if the world closes before it's built).</li>
 *   <li>{@code Placed}, {@code CenterX/Z}, {@code FloorY}, {@code Tunnel}, {@code EntranceX/Y/Z}, {@code EntranceYaw},
 *       {@code BuildMillis}: the built lair.</li>
 *   <li>{@code Version}: what was built: 1 (or missing) the R2c cavern with its sloping tunnel ({@code Descent}: how far
 *       the tunnel went down), 2 the volcano ({@code Mouth}: the cave mouth's distance from the middle, {@code RimY}: the
 *       crater rim's crest, {@code GullyEnd}: where the gully in front of the mouth ends, {@code ApproachEnd}: the last row
 *       of the land shaped in front of it; missing in volcanoes built before R6b, whose blend always ended
 *       {@link LairTuning#APRON} + {@link LairTuning#BLEND} past the gully). Old worlds keep the lair they have.</li>
 *   <li>{@code FailedSearches}: how many times no spot was found (it tries again at every start).</li>
 *   <li>{@code Boss} (UUID), {@code Beaten} (true once it's beaten, until the next one rises), {@code DefeatedAt} (the game
 *       time it was beaten), {@code Rises}.</li>
 * </ul>
 * Server thread only.
 */
public final class ColossusLairData extends SavedData
{
    private static final String NAME = "dross_colossus_lair";

    private boolean planned;
    private int plannedX;
    private int plannedZ;
    private boolean placed;
    private int centerX;
    private int centerZ;
    private int floorY;
    private Direction tunnel = Direction.NORTH;
    private int descent;
    private int version = 1;
    private int mouth;
    private int rimY;
    private int gullyEnd;
    /** The last row of the land shaped in front of the mouth; -1 where it wasn't saved (built before R6b). */
    private int approachEnd = -1;
    private Vec3 entrance = Vec3.ZERO;
    private float entranceYaw;
    private long buildMillis = -1L;
    private int failedSearches;
    @Nullable
    private UUID boss;
    private boolean beaten;
    private long defeatedAt;
    private int rises;

    static ColossusLairData get(MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(ColossusLairData::load, ColossusLairData::new, NAME);
    }

    private static ColossusLairData load(CompoundTag tag)
    {
        ColossusLairData data = new ColossusLairData();
        data.planned = tag.getBoolean("Planned");
        data.plannedX = tag.getInt("PlannedX");
        data.plannedZ = tag.getInt("PlannedZ");
        data.placed = tag.getBoolean("Placed");
        data.centerX = tag.getInt("CenterX");
        data.centerZ = tag.getInt("CenterZ");
        data.floorY = tag.getInt("FloorY");
        Direction tunnel = Direction.byName(tag.getString("Tunnel"));
        data.tunnel = tunnel != null && tunnel.getAxis().isHorizontal() ? tunnel : Direction.NORTH;
        data.descent = tag.getInt("Descent");
        data.version = tag.contains("Version") ? tag.getInt("Version") : 1;
        data.mouth = tag.getInt("Mouth");
        data.rimY = tag.getInt("RimY");
        data.gullyEnd = tag.getInt("GullyEnd");
        data.approachEnd = tag.contains("ApproachEnd") ? tag.getInt("ApproachEnd") : -1;
        data.entrance = new Vec3(tag.getDouble("EntranceX"), tag.getDouble("EntranceY"), tag.getDouble("EntranceZ"));
        data.entranceYaw = tag.getFloat("EntranceYaw");
        data.buildMillis = tag.contains("BuildMillis") ? tag.getLong("BuildMillis") : -1L;
        data.failedSearches = tag.getInt("FailedSearches");
        data.boss = tag.hasUUID("Boss") ? tag.getUUID("Boss") : null;
        data.beaten = tag.getBoolean("Beaten");
        data.defeatedAt = tag.getLong("DefeatedAt");
        data.rises = tag.getInt("Rises");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        tag.putBoolean("Planned", planned);
        tag.putInt("PlannedX", plannedX);
        tag.putInt("PlannedZ", plannedZ);
        tag.putBoolean("Placed", placed);
        tag.putInt("CenterX", centerX);
        tag.putInt("CenterZ", centerZ);
        tag.putInt("FloorY", floorY);
        tag.putString("Tunnel", tunnel.getName());
        tag.putInt("Descent", descent);
        tag.putInt("Version", version);
        tag.putInt("Mouth", mouth);
        tag.putInt("RimY", rimY);
        tag.putInt("GullyEnd", gullyEnd);
        if (approachEnd >= 0)
        {
            tag.putInt("ApproachEnd", approachEnd);
        }
        tag.putDouble("EntranceX", entrance.x);
        tag.putDouble("EntranceY", entrance.y);
        tag.putDouble("EntranceZ", entrance.z);
        tag.putFloat("EntranceYaw", entranceYaw);
        tag.putLong("BuildMillis", buildMillis);
        tag.putInt("FailedSearches", failedSearches);
        if (boss != null)
        {
            tag.putUUID("Boss", boss);
        }
        tag.putBoolean("Beaten", beaten);
        tag.putLong("DefeatedAt", defeatedAt);
        tag.putInt("Rises", rises);
        return tag;
    }

    // ---------------------------------------------------------------- the site

    boolean isPlanned()
    {
        return planned;
    }

    int plannedX()
    {
        return plannedX;
    }

    int plannedZ()
    {
        return plannedZ;
    }

    void setPlanned(int x, int z)
    {
        planned = true;
        plannedX = x;
        plannedZ = z;
        setDirty();
    }

    void noteFailedSearch()
    {
        failedSearches++;
        setDirty();
    }

    int failedSearches()
    {
        return failedSearches;
    }

    boolean isPlaced()
    {
        return placed;
    }

    void markPlaced(LairBuilder.Layout layout, long millis)
    {
        placed = true;
        planned = true;
        plannedX = layout.centerX();
        plannedZ = layout.centerZ();
        centerX = layout.centerX();
        centerZ = layout.centerZ();
        floorY = layout.floorY();
        tunnel = layout.tunnel();
        descent = 0;
        version = LairBuilder.VERSION;
        mouth = layout.mouth();
        rimY = layout.rimY();
        gullyEnd = layout.gullyEnd();
        approachEnd = layout.approachEnd();
        entrance = layout.entrance();
        entranceYaw = layout.entranceYaw();
        buildMillis = millis;
        setDirty();
    }

    int centerX()
    {
        return centerX;
    }

    int centerZ()
    {
        return centerZ;
    }

    int floorY()
    {
        return floorY;
    }

    Direction tunnel()
    {
        return tunnel;
    }

    int descent()
    {
        return descent;
    }

    /** 1: the R2c cavern and tunnel (old worlds); 2: the volcano. */
    int version()
    {
        return version;
    }

    /** The volcano's cave mouth: its distance from the middle (version 2). */
    int mouth()
    {
        return mouth;
    }

    /** Where the volcano's gully in front of the mouth ends: its distance from the middle (version 2). */
    int gullyEnd()
    {
        return gullyEnd;
    }

    /**
     * The last row of the land shaped in front of the volcano's mouth (its distance from the middle, version 2): the way
     * in meets the land just beyond it. For a volcano built before R6b (not saved), where its blend ended.
     */
    int approachEnd()
    {
        return approachEnd >= 0 ? approachEnd : gullyEnd + LairTuning.APRON + LairTuning.BLEND;
    }

    /** True if this volcano's way in was planned to meet the land (built since R6b; see {@link #approachEnd}). */
    boolean hasPlannedApproach()
    {
        return approachEnd >= 0;
    }

    /** The volcano's crater rim (Y, version 2). */
    int rimY()
    {
        return rimY;
    }

    Vec3 entrance()
    {
        return entrance;
    }

    float entranceYaw()
    {
        return entranceYaw;
    }

    long buildMillis()
    {
        return buildMillis;
    }

    // ---------------------------------------------------------------- the boss

    @Nullable
    UUID boss()
    {
        return boss;
    }

    /** True once the lair's Colossus has been beaten, until the next one rises. */
    boolean beaten()
    {
        return beaten;
    }

    /** The game time it was last beaten (only meaningful while {@link #beaten}). */
    long defeatedAt()
    {
        return defeatedAt;
    }

    int rises()
    {
        return rises;
    }

    /** A new Colossus has risen: it's the lair's now, and not beaten. */
    void setRisen(UUID id)
    {
        boss = id;
        beaten = false;
        rises++;
        setDirty();
    }

    /** The lair's Colossus was beaten at {@code gameTime}. */
    void recordDefeat(long gameTime)
    {
        beaten = true;
        defeatedAt = gameTime;
        setDirty();
    }

    /** The lair's Colossus is gone (its statue crumbled, or it was removed): only the time it was beaten is kept. */
    void forgetBoss()
    {
        boss = null;
        setDirty();
    }

    /** True if a new Colossus may rise at {@code gameTime}: not beaten, or beaten {@link LairTuning#LAIR_RESPAWN_TICKS} ago. */
    boolean respawnReady(long gameTime)
    {
        return !beaten || gameTime - defeatedAt >= LairTuning.LAIR_RESPAWN_TICKS;
    }

    /** Tests: moves the time it was beaten (to skip the wait). It may be before the world began (a negative time). */
    void setDefeatedAtForTests(long gameTime)
    {
        beaten = true;
        defeatedAt = gameTime;
        setDirty();
    }

    /** Tests: no Colossus, never beaten (as freshly built). */
    void resetBossForTests()
    {
        boss = null;
        beaten = false;
        defeatedAt = 0L;
        setDirty();
    }
}
