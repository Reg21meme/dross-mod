package com.reg21meme.dross.boss.golem;

import com.reg21meme.dross.boss.rage.Rage;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

/**
 * Something a Cinder Colossus throws or spits (a rock, a lava blob). It flies on a plain arc (no air drag, its own
 * gravity), so the Colossus can aim it exactly with {@link #aim}: it lands where it was aimed after the given number
 * of ticks. It never hits its Colossus, the Colossus's minions or another of its projectiles, and it never interacts
 * with the blocks it hits (no buttons pressed, no TNT lit, no chorus flowers broken): what happens when it lands is
 * up to {@link #onHit}.
 */
public abstract class BossProjectile extends Projectile
{
    /** It's removed if it's still flying after this many ticks (it fell out of the world, or flew off somewhere). */
    private static final int MAX_LIFE = 200;

    /** Client: it has hit something and waits there to be removed. */
    private boolean landed;

    protected BossProjectile(EntityType<? extends BossProjectile> type, Level level)
    {
        super(type, level);
    }

    /** How fast it falls: blocks a tick, added each tick. */
    protected abstract double gravity();

    /**
     * Sets its speed so that, starting from where it is, it reaches {@code target} after {@code ticks} ticks.
     * (Each tick it moves by its speed and then falls a little faster, so after n ticks it has dropped
     * gravity x n(n-1)/2 below a straight line.)
     */
    public void aim(Vec3 target, int ticks)
    {
        int n = Math.max(1, ticks);
        Vec3 d = target.subtract(this.position());
        double vy = (d.y + this.gravity() * n * (n - 1) / 2.0D) / n;
        this.setDeltaMovement(d.x / n, vy, d.z / n);
    }

    @Override
    public void tick()
    {
        super.tick();
        if (this.landed)
        {
            return; // client: it has hit something; the server removes it in a moment
        }
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS)
        {
            if (this.level().isClientSide)
            {
                // What happens when it lands is the server's business; here it just stops where it hit.
                this.setPos(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z);
                this.landed = true;
                return;
            }
            if (!ForgeEventFactory.onProjectileImpact(this, hit))
            {
                this.onHit(hit);
                if (this.isRemoved())
                {
                    return;
                }
            }
        }
        Vec3 v = this.getDeltaMovement();
        this.setPos(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
        this.updateRotation();
        this.setDeltaMovement(v.x, v.y - this.gravity(), v.z);
        this.flyingEffects();
        if (!this.level().isClientSide && (this.tickCount > MAX_LIFE || this.getY() < this.level().getMinBuildHeight() - 16))
        {
            this.discard();
        }
    }

    /** Client: particles while it flies (none by default). */
    protected void flyingEffects()
    {
    }

    /**
     * Never another of the bosses' projectiles; never a Colossus or a golem, unless the Rage Wand has made it an enemy
     * of whoever threw this ({@link Rage#areEnemies}: both raged, on different sides).
     */
    @Override
    protected boolean canHitEntity(Entity entity)
    {
        if (entity instanceof BossProjectile
                || (entity instanceof CinderColossus || entity instanceof GolemMinion) && !Rage.areEnemies(this.getOwner(), entity))
        {
            return false;
        }
        return super.canHitEntity(entity);
    }

    /** Lands without touching the block it hit (unlike vanilla projectiles, which can press buttons or light TNT). */
    @Override
    protected void onHitBlock(net.minecraft.world.phys.BlockHitResult hit)
    {
    }

    @Override
    protected void defineSynchedData()
    {
    }

    @Override
    public boolean isPickable()
    {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance)
    {
        return distance < 128.0D * 128.0D;
    }
}
