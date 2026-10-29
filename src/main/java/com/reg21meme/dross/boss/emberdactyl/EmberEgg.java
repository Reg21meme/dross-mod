package com.reg21meme.dross.boss.emberdactyl;

import com.reg21meme.dross.boss.fire.FireCandidate;
import com.reg21meme.dross.registry.ModEntities;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Emberdactyl's egg in its nest (design E3B, obsidian with an ember glow at its foot). It can't be destroyed (the
 * user's rule; only {@code /kill}), pushed or moved. When the adult breaks apart in its death it {@link #hatch}es: it
 * splits open (design E3D, its lining all lava) with a burst of lava and flame, and the young reborn bursts out; the empty
 * shell stays in the nest.
 */
public class EmberEgg extends FireCandidate
{
    private static final String TAG_VOLCANO = "Volcano";
    private long volcano;

    public EmberEgg(EntityType<? extends EmberEgg> type, Level level)
    {
        super(type, level);
        this.setDesign(EmberBoss.design("E3B"));
    }

    /** True once it has split open. */
    public boolean isHatched()
    {
        return this.getDesign().number() == EmberBoss.design("E3D");
    }

    /** Splits open: the empty shell, lava and flames, the stone breaking. */
    public void hatch()
    {
        this.setDesign(EmberBoss.design("E3D"));
        if (this.level() instanceof ServerLevel level)
        {
            Vec3 c = this.position().add(0.0D, 6.0D, 0.0D);
            level.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 60, 2.5D, 4.0D, 2.5D, 0.0D);
            level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 80, 2.5D, 4.0D, 2.5D, 0.1D);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 4.0D, c.z, 50, 3.0D, 3.0D, 3.0D, 0.05D);
            level.playSound(null, c.x, c.y, c.z, EmberBoss.sound("entity.emberdactyl.break"), SoundSource.HOSTILE,
                    EmberTuning.VOLUME, 1.3F);
        }
    }

    public void setVolcano(long volcano)
    {
        this.volcano = volcano;
    }

    public long volcano()
    {
        return this.volcano;
    }

    /** It's no exhibit: right-clicking it does nothing. */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand)
    {
        return InteractionResult.PASS;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putLong(TAG_VOLCANO, this.volcano);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.volcano = tag.getLong(TAG_VOLCANO);
    }

    /** The egg standing in the nest at {@code nest} (within a few blocks), if one does. */
    @Nullable
    public static EmberEgg findNear(ServerLevel level, Vec3 nest)
    {
        List<EmberEgg> eggs = level.getEntitiesOfClass(EmberEgg.class, new AABB(nest, nest).inflate(6.0D, 8.0D, 6.0D));
        return eggs.isEmpty() ? null : eggs.get(0);
    }

    /** Lays a new egg at {@code feet} (unhatched), facing {@code yaw}. */
    @Nullable
    public static EmberEgg lay(ServerLevel level, Vec3 feet, float yaw, long volcano)
    {
        EmberEgg egg = ModEntities.EMBERDACTYL_EGG.get().create(level);
        if (egg == null)
        {
            return null;
        }
        egg.moveTo(feet.x, feet.y, feet.z, yaw, 0.0F);
        egg.setYHeadRot(yaw);
        egg.setYBodyRot(yaw);
        egg.setVolcano(volcano);
        level.addFreshEntity(egg);
        return egg;
    }
}
