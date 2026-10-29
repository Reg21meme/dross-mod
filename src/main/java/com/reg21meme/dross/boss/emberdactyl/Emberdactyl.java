package com.reg21meme.dross.boss.emberdactyl;

import com.reg21meme.dross.boss.golem.LavaBlob;
import com.reg21meme.dross.registry.ModEntities;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import static com.reg21meme.dross.boss.emberdactyl.EmberTuning.*;

/**
 * The Emberdactyl (design E1A, 33 blocks across its wings): 400 health. At half health it flies and moves faster,
 * sends out a third tornado and, at its volcano, the crater erupts every few seconds, raining lava bombs round the players
 * near its nest (the user: no new look, just faster, and those extras). When it dies it breaks apart, and as it does its
 * egg in the nest cracks open and its young reborn bursts out and fights on ({@link EmberYoung}); one with no egg (a
 * spawn egg's, the user: "it lies alone") just dies. Its trophy and "Wings Clipped" come with the young.
 */
public class Emberdactyl extends EmberBoss
{
    /** Its parts' sizes (width, height): head, neck, left wing, right wing, left wing tip, right wing tip, tail. */
    private static final double[][] SIZES = {{4.5D, 4.5D}, {3.5D, 4.0D}, {7.0D, 7.0D}, {7.0D, 7.0D}, {6.0D, 8.0D},
            {6.0D, 8.0D}, {3.0D, 3.0D}};
    /** ...where they are flying (its own left +x, up from its feet, its front -z; measured from its hover and flight)... */
    private static final double[][] FLY = {{0.0D, 11.0D, -12.5D}, {0.0D, 10.0D, -6.5D}, {6.6D, 9.0D, 0.9D},
            {-6.6D, 9.0D, 0.9D}, {11.5D, 7.5D, 2.7D}, {-11.5D, 7.5D, 2.7D}, {0.0D, 5.0D, 7.0D}};
    /** ...and standing (its wings folded up its sides). */
    private static final double[][] GROUND = {{0.0D, 9.0D, -12.0D}, {0.0D, 6.5D, -6.0D}, {3.8D, 0.0D, 0.6D},
            {-3.8D, 0.0D, 0.6D}, {4.7D, 4.0D, 2.1D}, {-4.7D, 4.0D, 2.1D}, {0.0D, 1.0D, 6.5D}};

    public Emberdactyl(EntityType<? extends Emberdactyl> type, Level level)
    {
        super(type, level, ADULT, design("E1A"), SIZES, FLY, GROUND);
        this.xpReward = ADULT_XP;
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, ADULT.health())
                .add(Attributes.ARMOR, ADULT.armor())
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 128.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FLYING_SPEED, 0.6D);
    }

    @Override
    protected Component barName()
    {
        return Component.translatable("boss.dross.emberdactyl.bar.adult");
    }

    @Override
    protected String soundPrefix()
    {
        return "entity.emberdactyl";
    }

    @Override
    protected int designNumber()
    {
        return design("E1A");
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound()
    {
        return sound("entity.emberdactyl.ambient");
    }

    @Override
    public int getAmbientSoundInterval()
    {
        return 200;
    }

    /** At half health: faster, a third tornado, its volcano erupting. */
    @Override
    protected void checkEnrage()
    {
        if (!this.enraged && this.getHealth() <= this.getMaxHealth() * ENRAGE_AT)
        {
            this.enraged = true;
        }
    }

    @Override
    protected void customServerAiStep()
    {
        super.customServerAiStep();
        if (this.enraged && this.phase == Phase.FIGHT && this.nest != null && this.tickCount % ERUPT_EVERY == 0
                && this.level() instanceof ServerLevel level)
        {
            this.erupt(level);
        }
    }

    /** Its crater erupts: lava bombs out of the lava lake onto spots near the players by its nest. */
    private void erupt(ServerLevel level)
    {
        List<Player> near = level.getEntitiesOfClass(Player.class, new AABB(this.nest, this.nest).inflate(ERUPT_RANGE, 80.0D, ERUPT_RANGE),
                p -> !p.isCreative() && !p.isSpectator() && p.isAlive());
        if (near.isEmpty())
        {
            return;
        }
        Vec3 lake = this.nest.add(0.0D, -EmberVolcanoTuning.NEST_HEIGHT + 1.0D, 0.0D);
        level.playSound(null, lake.x, lake.y, lake.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, VOLUME, 0.5F);
        level.sendParticles(ParticleTypes.LAVA, lake.x, lake.y, lake.z, 40, 6.0D, 1.0D, 6.0D, 0.0D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, lake.x, lake.y + 2.0D, lake.z, 40, 6.0D, 2.0D, 6.0D, 0.05D);
        for (int i = 0; i < ERUPT_BOMBS; i++)
        {
            Player p = near.get(this.random.nextInt(near.size()));
            double a = this.random.nextDouble() * Math.PI * 2.0D;
            double r = this.random.nextDouble() * ERUPT_SPREAD;
            Vec3 spot = p.position().add(Math.cos(a) * r, 0.0D, Math.sin(a) * r);
            Vec3 from = lake.add((this.random.nextDouble() - 0.5D) * 10.0D, 0.0D, (this.random.nextDouble() - 0.5D) * 10.0D);
            LavaBlob blob = LavaBlob.spat(level, this, from, ERUPT_DAMAGE);
            blob.aim(spot, 40 + this.random.nextInt(20));
            level.addFreshEntity(blob);
        }
    }

    /** As its body breaks apart (its death, 4.2 s in), its egg cracks open and its young bursts out; then it vanishes. */
    @Override
    protected boolean deathTick(int ticks)
    {
        if (ticks == HATCH_TICKS)
        {
            this.hatch();
        }
        return ticks >= ADULT_REMOVE_TICKS;
    }

    private void hatch()
    {
        if (this.nest == null || !(this.level() instanceof ServerLevel level))
        {
            return;
        }
        EmberEgg egg = EmberEgg.findNear(level, this.nest);
        if (egg == null || egg.isHatched())
        {
            return;
        }
        egg.hatch();
        EmberYoung young = ModEntities.EMBERYOUNG.get().create(level);
        if (young == null)
        {
            return;
        }
        Vec3 out = this.nest.add(0.0D, 4.0D, 0.0D);
        young.moveTo(out.x, out.y, out.z, this.getYRot(), 0.0F);
        young.setHome(this.nest, this.nest, null, 0.0F, this.volcano);
        young.finalizeSpawn(level, level.getCurrentDifficultyAt(young.blockPosition()), MobSpawnType.EVENT, null, null);
        level.addFreshEntity(young);
        // it goes straight for whoever beat its parent (the user: "the young one should be coming over to attack me"),
        // else the nearest player
        LivingEntity foe = this.getLastHurtByMob() instanceof Player p && !p.isCreative() && !p.isSpectator() ? p
                : level.getNearestPlayer(this.getX(), this.getY(), this.getZ(), YOUNG.leash(), e -> e instanceof Player q
                        && !q.isCreative() && !q.isSpectator());
        young.startFighting(foe);
        if (this.volcano != 0L)
        {
            EmberVolcanoData.get(level.getServer()).setState(this.volcano, EmberVolcanoData.State.YOUNG);
        }
    }

    @Override
    protected void onDeathStart(DamageSource source)
    {
        if (this.volcano != 0L && this.level() instanceof ServerLevel level)
        {
            EmberVolcanoData.get(level.getServer()).setState(this.volcano, EmberVolcanoData.State.YOUNG);
        }
    }
}
