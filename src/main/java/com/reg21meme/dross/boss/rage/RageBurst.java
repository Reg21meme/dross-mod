package com.reg21meme.dross.boss.rage;

import com.reg21meme.dross.DrossColors;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;

import java.util.Optional;

import javax.annotation.Nullable;

/**
 * Many mobs raged at once (the user, October 31: "think about ways how we can mass infect mobs with rage: the Rage Wand is
 * nice but it's slow, one by one; maybe a rage bow? or a spell?"): every mob within a radius of a spot onto a team, with
 * a ring of the team's colour rolling out over the ground and a roar. The Rage Wand's burst (sneak + right-click,
 * {@link RageWandItem}) and the Rage Bow's shots ({@link RageBowItem}) use it. Server side.
 */
public final class RageBurst
{
    private RageBurst() {}

    /** Where a shot from {@code player}'s eyes lands: the first living thing on the line (or the first block), and the
     *  living thing it hit, if any. */
    public record Hit(Vec3 at, @Nullable LivingEntity entity)
    {
    }

    /**
     * A hitscan shot (the user, November 2: the Rage Bow's arrows dropped and were slow, "make it a gun ... instantly ... a
     * hitscan"): a straight line from {@code player}'s eyes the way they look, up to {@code reach} blocks or the first
     * block; the nearest living thing whose box, grown by {@link RageTuning#AIM_SLACK}, the line passes through is hit
     * (a little slack, so it's easy to aim). Nothing hit: the block, or the air at the end of its reach.
     */
    public static Hit aim(ServerLevel level, Player player, double reach)
    {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(reach));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        LivingEntity best = null;
        Vec3 bestAt = stop;
        double bestD = eye.distanceToSqr(stop);
        // (the hitboxes of a mob made of pieces count as it: the Emberdactyl's wings, the hermit's claws; November 5)
        for (Entity hit : level.getEntities((Entity) null, new AABB(eye, stop).inflate(RageTuning.AIM_SLACK + 1.0D),
                e -> e != player && e.isAlive() && !e.isSpectator()))
        {
            LivingEntity e = hit instanceof LivingEntity living ? living
                    : hit instanceof PartEntity<?> part && part.getParent() instanceof LivingEntity parent ? parent : null;
            if (e == null || e == player || !e.isAlive())
            {
                continue;
            }
            AABB box = hit.getBoundingBox().inflate(RageTuning.AIM_SLACK);
            Optional<Vec3> on = box.contains(eye) ? Optional.of(eye) : box.clip(eye, stop);
            if (on.isPresent() && eye.distanceToSqr(on.get()) < bestD)
            {
                best = e;
                bestAt = on.get();
                bestD = eye.distanceToSqr(bestAt);
            }
        }
        return new Hit(best != null ? best.position().add(0.0D, best.getBbHeight() * 0.5D, 0.0D) : bestAt, best);
    }

    /** A line of the team's dust from {@code player}'s hand out to {@code to}. */
    public static void beam(ServerLevel level, Player player, Vec3 to, RageTeam team)
    {
        DustParticleOptions dust = new DustParticleOptions(DrossColors.vector(team.color()), RageTuning.ARROW_TRAIL_SIZE);
        Vec3 from = player.getEyePosition().add(0.0D, -0.2D, 0.0D);
        Vec3 along = to.subtract(from);
        double length = along.length();
        if (length < 0.01D)
        {
            return;
        }
        Vec3 step = along.scale(1.0D / length);
        for (double d = 1.0D; d < length; d += RageTuning.BEAM_STEP)
        {
            Vec3 p = from.add(step.scale(d));
            level.sendParticles(dust, p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /** Rages every mob that can be raged within {@code radius} of {@code at} onto {@code team} (ones already on it are
     *  left as they are) and tells {@code by} how many. Returns how many it raged. */
    public static int burst(ServerLevel level, Vec3 at, double radius, RageTeam team, @Nullable Player by)
    {
        return burst(level, at, radius, team, by, true);
    }

    /** {@link #burst}, with or without its roar (the Rage Bow fires too fast for one each shot). */
    public static int burst(ServerLevel level, Vec3 at, double radius, RageTeam team, @Nullable Player by, boolean roar)
    {
        int raged = 0;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(radius),
                m -> m.distanceToSqr(at) <= radius * radius && Rage.canBeRaged(m)))
        {
            if (Rage.isRaged(mob) && Rage.teamOf(mob) == team)
            {
                continue;
            }
            if (Rage.enrage(mob, team))
            {
                raged++;
            }
        }
        DustParticleOptions dust = new DustParticleOptions(DrossColors.vector(team.color()), RageTuning.BURST_DUST_SIZE);
        for (double share : new double[] {0.35D, 0.7D, 1.0D})
        {
            int points = Math.max(8, (int) Math.round(RageTuning.BURST_RING_POINTS * share));
            for (int i = 0; i < points; i++)
            {
                double a = Math.PI * 2.0D * i / points;
                level.sendParticles(dust, at.x + Math.cos(a) * radius * share, at.y + 0.3D, at.z + Math.sin(a) * radius * share,
                        1, 0.0D, 0.15D, 0.0D, 0.0D);
            }
        }
        level.sendParticles(dust, at.x, at.y + 1.0D, at.z, RageTuning.BURST_CENTRE_PARTICLES, 0.8D, 0.8D, 0.8D, 0.0D);
        if (roar)
        {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS,
                    RageTuning.BURST_VOLUME, RageTuning.BURST_PITCH);
        }
        if (by != null)
        {
            by.displayClientMessage(Component.translatable("item.dross.rage_wand.burst", raged, team.displayName()), true);
        }
        return raged;
    }
}
