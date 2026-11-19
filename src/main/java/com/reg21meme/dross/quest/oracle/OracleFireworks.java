package com.reg21meme.dross.quest.oracle;

import com.reg21meme.dross.DrossColors;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The blue fireworks round a player when the Oracle gives "Defeat the Dross": {@link #ROCKETS} rockets over about two
 * seconds, in the Dross's palette ({@code DrossColors}, Quest section). They never hurt anyone
 * ({@link OracleEvents} cancels their blasts' damage: a rocket hitting the camp's roof would burst right over the player).
 */
public final class OracleFireworks
{
    /** How many rockets. */
    public static final int ROCKETS = 6;
    /** Ticks between rockets (6 rockets: the last about 1.75 s after the first). */
    public static final int INTERVAL_TICKS = 7;
    /** How far from the player (x and z, blocks) a rocket may start. */
    public static final double SPREAD = 3.5D;
    /** How many columns to try for one with open sky over the player's feet. */
    private static final int SKY_TRIES = 8;
    /** Firework flight (1: about 1-1.5 s up). */
    private static final byte FLIGHT = 1;
    /** Their bursts, by turns: large ball, burst, small ball (firework explosion types). */
    private static final byte[] SHAPES = {1, 4, 0};
    /** Marks our rockets so their blasts do no damage. */
    static final String ROCKET_TAG = "DrossOracleFirework";

    private static final List<Show> SHOWS = new ArrayList<>();

    private OracleFireworks() {}

    /** Starts a show round the player (the first rocket at once). */
    public static void launch(ServerPlayer player)
    {
        Show show = new Show(player.level().dimension(), player.position());
        synchronized (SHOWS)
        {
            SHOWS.add(show);
        }
        show.fire(player.serverLevel());
    }

    /** Every server tick: the next rocket of each show. */
    static void tick(MinecraftServer server)
    {
        List<Show> due = new ArrayList<>();
        synchronized (SHOWS)
        {
            Iterator<Show> it = SHOWS.iterator();
            while (it.hasNext())
            {
                Show show = it.next();
                if (show.left <= 0)
                {
                    it.remove();
                }
                else if (--show.wait <= 0)
                {
                    due.add(show);
                }
            }
        }
        for (Show show : due)
        {
            ServerLevel level = server.getLevel(show.dimension);
            if (level == null)
            {
                show.left = 0;
                continue;
            }
            show.fire(level);
        }
    }

    /** Forgets every show (the server stopped). */
    static void clear()
    {
        synchronized (SHOWS)
        {
            SHOWS.clear();
        }
    }

    /** True if this entity is one of the Oracle's rockets. */
    static boolean isOurs(Entity entity)
    {
        return entity instanceof FireworkRocketEntity && entity.getPersistentData().getBoolean(ROCKET_TAG);
    }

    private static final class Show
    {
        final ResourceKey<Level> dimension;
        final Vec3 centre;
        int left = ROCKETS;
        int wait;
        int fired;

        Show(ResourceKey<Level> dimension, Vec3 centre)
        {
            this.dimension = dimension;
            this.centre = centre;
        }

        void fire(ServerLevel level)
        {
            if (left <= 0)
            {
                return;
            }
            left--;
            wait = INTERVAL_TICKS;
            RandomSource random = level.getRandom();
            Vec3 start = startSpot(level, random);
            FireworkRocketEntity rocket = new FireworkRocketEntity(level, start.x, start.y, start.z, rocket(fired++));
            rocket.getPersistentData().putBoolean(ROCKET_TAG, true);
            level.addFreshEntity(rocket);
        }

        /** A spot near the player with open sky over it (else the top of the player's own column). */
        private Vec3 startSpot(ServerLevel level, RandomSource random)
        {
            int feet = (int) Math.floor(centre.y);
            for (int i = 0; i < SKY_TRIES; i++)
            {
                double x = centre.x + (random.nextDouble() * 2.0D - 1.0D) * SPREAD;
                double z = centre.z + (random.nextDouble() * 2.0D - 1.0D) * SPREAD;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
                if (top <= feet + 1)
                {
                    return new Vec3(x, Math.max(centre.y, top) + 0.5D, z);
                }
            }
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(centre.x), (int) Math.floor(centre.z));
            return new Vec3(centre.x, Math.max(centre.y, top) + 0.5D, centre.z);
        }
    }

    /** One firework rocket item: the palette's blue, fading to deep cobalt, with a trail and twinkle. */
    private static ItemStack rocket(int number)
    {
        CompoundTag explosion = new CompoundTag();
        explosion.putByte("Type", SHAPES[number % SHAPES.length]);
        explosion.putIntArray("Colors", number % 2 == 0
                ? new int[] {DrossColors.ORACLE_FIREWORK_MAIN, DrossColors.ORACLE_FIREWORK_SPARK}
                : new int[] {DrossColors.ORACLE_FIREWORK_SPARK, DrossColors.ORACLE_FIREWORK_MAIN});
        explosion.putIntArray("FadeColors", new int[] {DrossColors.ORACLE_FIREWORK_FADE});
        explosion.putBoolean("Trail", true);
        explosion.putBoolean("Flicker", number % 2 == 1);
        ListTag explosions = new ListTag();
        explosions.add(explosion);
        CompoundTag fireworks = new CompoundTag();
        fireworks.putByte("Flight", FLIGHT);
        fireworks.put("Explosions", explosions);
        ItemStack stack = new ItemStack(Items.FIREWORK_ROCKET);
        stack.getOrCreateTag().put("Fireworks", fireworks);
        return stack;
    }
}
