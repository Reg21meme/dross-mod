package com.reg21meme.dross.boss.golem;

import com.reg21meme.dross.registry.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code dross:stone_golem}: the small golem of scorched stone a Cinder Colossus summons in the Cracking stage (three
 * at a time: in front of it, to its left and to its right). It climbs out of the ground and punches whoever is
 * nearest. See {@link GolemMinion}.
 */
public class StoneGolem extends GolemMinion
{
    public StoneGolem(EntityType<? extends StoneGolem> type, Level level)
    {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, ColossusTuning.STONE_GOLEM_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, ColossusTuning.STONE_GOLEM_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, ColossusTuning.STONE_GOLEM_SPEED)
                .add(Attributes.ARMOR, ColossusTuning.STONE_GOLEM_ARMOR)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    public GolemDesigns.Minion design()
    {
        return GolemDesigns.STONE_GOLEM;
    }

    @Override
    protected BlockState dust()
    {
        return Blocks.BLACKSTONE.defaultBlockState();
    }

    @Override
    protected double walkSpeed()
    {
        return ColossusTuning.STONE_GOLEM_SPEED;
    }

    @Override
    protected SoundEvent getAmbientSound()
    {
        return ModSounds.STONE_GOLEM_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return ModSounds.STONE_GOLEM_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return ModSounds.STONE_GOLEM_DEATH.get();
    }
}
