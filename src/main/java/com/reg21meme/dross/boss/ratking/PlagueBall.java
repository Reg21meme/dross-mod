package com.reg21meme.dross.boss.ratking;

import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.golem.BossProjectile;
import com.reg21meme.dross.boss.rage.Rage;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A ball of poison the Rat King's cast throws from his staff's orb ({@code dross:plague_ball}): a clumpy, brownish-green
 * lump (its model and spin are made by {@code tools/ratking/ratkit/ball.py}) that flies on a gentle arc, leaving a trail
 * of sickly dust. Wherever it hits (the ground, a wall, someone) it bursts into a {@link PoisonCloud}. It never hits
 * the Rat King or a rat, and never touches the blocks it hits.
 */
public class PlagueBall extends BossProjectile implements GeoEntity
{
    private static final RawAnimation SPIN = RawAnimation.begin().thenLoop("animation.plague_ball.spin");
    /** The trail: dust particles each tick, their size. */
    private static final float TRAIL_SIZE = 1.1F;
    private static final double TRAIL_SPREAD = 0.15D;
    /** The burst: dust flung out where it hits, how many, how far, and how loud. */
    private static final int BURST_DUST = 24;
    private static final double BURST_SPREAD = 0.8D;
    private static final float BURST_SIZE = 1.4F;
    private static final float BURST_VOLUME = 1.0F;
    /** Its cloud settles on the ground at most this far below where it burst. */
    private static final int CLOUD_DROP = 3;
    /** The middle of the ball is this far above its position (the bottom of its box). */
    private static final double MIDDLE = 0.25D;

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public PlagueBall(EntityType<? extends PlagueBall> type, Level level)
    {
        super(type, level);
    }

    /** A ball cast by {@code owner} from {@code from} (aim it with {@link #aim}, then add it to the world). */
    public static PlagueBall cast(Level level, LivingEntity owner, Vec3 from)
    {
        PlagueBall ball = new PlagueBall(ModEntities.PLAGUE_BALL.get(), level);
        ball.setOwner(owner);
        ball.setPos(from.x, from.y, from.z);
        return ball;
    }

    @Override
    protected double gravity()
    {
        return RatKingTuning.BALL_GRAVITY;
    }

    @Override
    protected void flyingEffects()
    {
        if (!this.level().isClientSide)
        {
            return;
        }
        int color = this.random.nextInt(3) == 0 ? DrossColors.PLAGUE_BALL_TRAIL_DARK : DrossColors.PLAGUE_BALL_TRAIL;
        this.level().addParticle(new DustParticleOptions(DrossColors.vector(color), TRAIL_SIZE),
                this.getX() + (this.random.nextDouble() - 0.5D) * TRAIL_SPREAD, this.getY() + MIDDLE,
                this.getZ() + (this.random.nextDouble() - 0.5D) * TRAIL_SPREAD, 0.0D, 0.0D, 0.0D);
    }

    /**
     * Never the Rat King or his rats (nor another of the bosses' projectiles), unless the Rage Wand has made that one
     * an enemy of whoever cast it ({@link Rage#areEnemies}).
     */
    @Override
    protected boolean canHitEntity(Entity entity)
    {
        if (entity instanceof PoisonCloud
                || (entity instanceof RatKing || entity instanceof Rat) && !Rage.areEnemies(this.getOwner(), entity))
        {
            return false;
        }
        return super.canHitEntity(entity);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit)
    {
        if (!this.level().isClientSide)
        {
            Entity target = hit.getEntity();
            this.burst(new Vec3(target.getX(), target.getY(), target.getZ()));
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult hit)
    {
        if (!this.level().isClientSide)
        {
            Vec3 at = hit.getLocation();
            // Hitting the ground from above, it settles there; hitting a wall, a little out from it.
            Vec3 out = Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.3D);
            this.burst(hit.getDirection() == Direction.UP ? at : at.add(out));
        }
    }

    /** Bursts: a cloud of poison where it hit (settled on the ground below, if there's ground near), dust and a hiss. */
    private void burst(Vec3 at)
    {
        if (this.level() instanceof ServerLevel level)
        {
            Vec3 ground = settle(level, at);
            Entity owner = this.getOwner();
            level.addFreshEntity(PoisonCloud.at(level, ground, owner instanceof LivingEntity living ? living : null));
            level.sendParticles(new DustParticleOptions(DrossColors.vector(DrossColors.PLAGUE_BALL_TRAIL), BURST_SIZE),
                    at.x, at.y + MIDDLE, at.z, BURST_DUST, BURST_SPREAD, 0.3D, BURST_SPREAD, 0.0D);
            level.playSound(null, at.x, at.y, at.z, ModSounds.RAT_KING_ATTACK_PLAGUE_BREATH.get(), SoundSource.HOSTILE,
                    BURST_VOLUME, 0.9F + this.random.nextFloat() * 0.2F);
        }
        this.discard();
    }

    /** The top of the ground at most {@link #CLOUD_DROP} blocks below a spot (or the spot itself if there's none). */
    private static Vec3 settle(ServerLevel level, Vec3 at)
    {
        BlockPos.MutableBlockPos pos = BlockPos.containing(at.x, at.y + 0.01D, at.z).mutable();
        for (int i = 0; i <= CLOUD_DROP; i++)
        {
            var shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (!shape.isEmpty())
            {
                double top = pos.getY() + shape.max(Direction.Axis.Y);
                return new Vec3(at.x, Math.min(Math.max(top, at.y - CLOUD_DROP), at.y + 1.0D), at.z);
            }
            pos.move(Direction.DOWN);
        }
        return at;
    }

    // ---------------------------------------------------------------- animation (GeckoLib)

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers)
    {
        controllers.add(new AnimationController<>(this, "spin", 0, state -> state.setAndContinue(SPIN)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache()
    {
        return this.animationCache;
    }
}
