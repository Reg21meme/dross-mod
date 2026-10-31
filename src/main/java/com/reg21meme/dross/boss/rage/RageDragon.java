package com.reg21meme.dross.boss.rage;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A raged Ender Dragon's fight (the user, October 30: "can't use the rage wand on ... the Ender Dragon, change that"; it
 * was refused). The dragon has no goals (it flies by its own phases, which only ever go for players, round the End's
 * middle), so a raged one with an enemy of another team near is flown at it here, with its own moves: it charges
 * through it (its head and neck hurt what they hit, its wings knock it aside, as in the End), flies on past and up, turns
 * and charges again, and between passes spits its fireball (its lingering cloud) at it when it can see it. With no enemy
 * near it circles over where it was raged (its own phases would take it away to the world's middle). It finds its
 * enemies through walls (it flies through them). Server side.
 */
final class RageDragon
{
    /** Each dragon's fight: whom it's after, whether its next flight is a charge (else a pass on and up), its next
     *  fireball's tick, where it was raged (it keeps round there) and how far round that spot it has circled. */
    private static final class Fight
    {
        @Nullable
        LivingEntity target;
        boolean charge = true;
        int nextFireball;
        final Vec3 home;
        double circle;

        Fight(Vec3 home)
        {
            this.home = home;
        }
    }

    private static final Map<EnderDragon, Fight> FIGHTS = new WeakHashMap<>();

    private RageDragon()
    {
    }

    static void tick(EnderDragon dragon)
    {
        if (dragon.dragonDeathTime > 0 || dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.DYING)
        {
            FIGHTS.remove(dragon);
            return;
        }
        Fight fight = FIGHTS.computeIfAbsent(dragon, d -> new Fight(d.position()));
        if (!Rage.isTargetable(dragon, fight.target))
        {
            fight.target = Rage.nearestTarget(dragon, null);
            fight.charge = true;
        }
        LivingEntity target = fight.target;
        if (target == null)
        {
            // nobody to fight: it circles over where it was raged, waiting (its own phases, away from the End, take it
            // off round the world's middle, 0 0, and far from its enemies: the user, October 30, "the Ender Dragon
            // doesn't attack anyone")
            if (dragon.getPhaseManager().getCurrentPhase().getPhase() != EnderDragonPhase.CHARGING_PLAYER)
            {
                fight.circle += RageTuning.DRAGON_CIRCLE_STEP;
                Vec3 to = fight.home.add(Math.cos(fight.circle) * RageTuning.DRAGON_CIRCLE_RADIUS, RageTuning.DRAGON_CIRCLE_UP,
                        Math.sin(fight.circle) * RageTuning.DRAGON_CIRCLE_RADIUS);
                dragon.getPhaseManager().setPhase(EnderDragonPhase.CHARGING_PLAYER);
                dragon.getPhaseManager().getPhase(EnderDragonPhase.CHARGING_PLAYER).setTarget(to);
            }
            return;
        }
        // a charge (or a pass) ends by itself (its phase goes back to holding its pattern): the next one
        if (dragon.getPhaseManager().getCurrentPhase().getPhase() != EnderDragonPhase.CHARGING_PLAYER)
        {
            Vec3 to;
            if (fight.charge)
            {
                to = target.position();      // (its feet, as the dragon's own charge at a player)
            }
            else
            {
                // on past it and up, ready to turn and come back
                Vec3 along = new Vec3(target.getX() - dragon.getX(), 0.0D, target.getZ() - dragon.getZ());
                along = along.lengthSqr() < 1.0D ? new Vec3(1.0D, 0.0D, 0.0D) : along.normalize();
                to = target.position().add(along.scale(RageTuning.DRAGON_PASS_PAST)).add(0.0D, RageTuning.DRAGON_PASS_UP, 0.0D);
            }
            dragon.getPhaseManager().setPhase(EnderDragonPhase.CHARGING_PLAYER);
            dragon.getPhaseManager().getPhase(EnderDragonPhase.CHARGING_PLAYER).setTarget(to);
            fight.charge = !fight.charge;
        }
        // its fireball, now and then, when it can see it
        if (dragon.tickCount >= fight.nextFireball && target.distanceToSqr(dragon) <= Mth.square(RageTuning.DRAGON_FIREBALL_RANGE)
                && dragon.hasLineOfSight(target))
        {
            fight.nextFireball = dragon.tickCount + RageTuning.DRAGON_FIREBALL_EVERY;
            Vec3 look = dragon.getViewVector(1.0F);
            double x = dragon.head.getX() - look.x;
            double y = dragon.head.getY(0.5D) + 0.5D;
            double z = dragon.head.getZ() - look.z;
            if (!dragon.isSilent())
            {
                dragon.level().levelEvent(null, 1017, dragon.blockPosition(), 0);   // (its fireball's sound)
            }
            DragonFireball ball = new DragonFireball(dragon.level(), dragon, target.getX() - x, target.getY(0.5D) - y,
                    target.getZ() - z);
            ball.moveTo(x, y, z, 0.0F, 0.0F);
            dragon.level().addFreshEntity(ball);
        }
    }
}
