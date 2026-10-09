package com.reg21meme.dross.portal;

import com.reg21meme.dross.DrossColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The Dross portal frame (dark stone with electric blue cracks).
 * <ul>
 *   <li>Can't be crafted: there is no recipe.</li>
 *   <li>Can't be mined or blown up: unbreakable like bedrock, and it drops nothing.</li>
 *   <li>Can't be pushed by pistons.</li>
 *   <li>Withers and the Ender Dragon can't break it (block tags wither_immune / dragon_immune).</li>
 *   <li>"The Dross is leaking out": every frame block, lit or not, now and then gives off drifting
 *       electric blue dust and a low hum (client side only, see {@link #animateTick}).</li>
 * </ul>
 * It deliberately does NOT override Forge's {@code isPortalFrame}, so flint and steel can't
 * make a purple nether portal inside it. Only the Rift Key lights it (see {@link DrossPortalActivation}).
 */
public class DrossPortalFrameBlock extends Block
{
    // How often the frame "leaks". Minecraft calls animateTick for a few random blocks near the player
    // every tick; each frame block gets roughly one call every 2-3 seconds. So "1 in N" below is per call.
    /** 1 in this many calls gives off a dust particle (a 4x5 frame: about 1-2 particles a second). */
    private static final int LEAK_PARTICLE_CHANCE = 4;
    /** 1 in this many calls plays the low hum (a 4x5 frame: about once a minute). */
    private static final int LEAK_SOUND_CHANCE = 400;
    /** Size of the dust particle (1.0 = redstone dust size; bigger dust also lasts a bit longer). */
    private static final float LEAK_PARTICLE_SIZE = 1.2F;
    /** How fast the dust drifts away from the frame. */
    private static final double LEAK_DRIFT_SPEED = 0.6D;
    /** How fast the dust drifts upwards. */
    private static final double LEAK_RISE_SPEED = 0.5D;
    /** The hum: quiet, and pitched down so it sounds low. */
    private static final float LEAK_SOUND_VOLUME = 0.25F;
    private static final float LEAK_SOUND_MIN_PITCH = 0.5F;
    private static final float LEAK_SOUND_PITCH_RANGE = 0.1F;

    private static final DustParticleOptions LEAK_DUST =
            new DustParticleOptions(DrossColors.vector(DrossColors.FRAME_LEAK_PARTICLE), LEAK_PARTICLE_SIZE);

    public DrossPortalFrameBlock()
    {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .sound(SoundType.STONE)
                .strength(-1.0F, 3600000.0F)
                .noLootTable()
                .isValidSpawn((state, level, pos, type) -> false)
                .pushReaction(PushReaction.BLOCK));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        if (random.nextInt(LEAK_SOUND_CHANCE) == 0)
        {
            level.playLocalSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, SoundEvents.PORTAL_AMBIENT,
                    SoundSource.BLOCKS, LEAK_SOUND_VOLUME, LEAK_SOUND_MIN_PITCH + random.nextFloat() * LEAK_SOUND_PITCH_RANGE, false);
        }

        if (random.nextInt(LEAK_PARTICLE_CHANCE) != 0)
        {
            return;
        }
        // Pick a random side; only leak out of a side that isn't covered by a full block.
        Direction side = Direction.getRandom(random);
        BlockPos next = pos.relative(side);
        if (level.getBlockState(next).isSolidRender(level, next))
        {
            return;
        }
        // Start just outside that face, at a random spot on it.
        double x = pos.getX() + 0.5D + side.getStepX() * 0.55D + (side.getStepX() == 0 ? random.nextDouble() - 0.5D : 0.0D);
        double y = pos.getY() + 0.5D + side.getStepY() * 0.55D + (side.getStepY() == 0 ? random.nextDouble() - 0.5D : 0.0D);
        double z = pos.getZ() + 0.5D + side.getStepZ() * 0.55D + (side.getStepZ() == 0 ? random.nextDouble() - 0.5D : 0.0D);
        // Dust particles slow their speed down a lot, so these numbers give a gentle drift away from the face and upwards.
        level.addParticle(LEAK_DUST, x, y, z,
                side.getStepX() * LEAK_DRIFT_SPEED, LEAK_RISE_SPEED + side.getStepY() * LEAK_DRIFT_SPEED, side.getStepZ() * LEAK_DRIFT_SPEED);
    }
}
