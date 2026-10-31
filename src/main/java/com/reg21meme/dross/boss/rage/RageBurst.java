package com.reg21meme.dross.boss.rage;

import com.reg21meme.dross.DrossColors;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Many mobs raged at once (the user, October 31: "think about ways how we can mass infect mobs with rage: the Rage Wand is
 * nice but it's slow, one by one; maybe a rage bow? or a spell?"): every mob within a radius of a spot onto a team, with
 * a ring of the team's colour rolling out over the ground and a roar. The Rage Wand's burst (sneak + right-click,
 * {@link RageWandItem}) and the Rage Bow's arrows ({@link RageBowItem}) use it. Server side.
 */
public final class RageBurst
{
    private RageBurst() {}

    /** Rages every mob that can be raged within {@code radius} of {@code at} onto {@code team} (ones already on it are
     *  left as they are) and tells {@code by} how many. Returns how many it raged. */
    public static int burst(ServerLevel level, Vec3 at, double radius, RageTeam team, @Nullable Player by)
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
        level.playSound(null, at.x, at.y, at.z, SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS,
                RageTuning.BURST_VOLUME, RageTuning.BURST_PITCH);
        if (by != null)
        {
            by.displayClientMessage(Component.translatable("item.dross.rage_wand.burst", raged, team.displayName()), true);
        }
        return raged;
    }
}
