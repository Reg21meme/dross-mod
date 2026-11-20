package com.reg21meme.dross.boss.knight.ruin;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.world.shrine.TreeFelling;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;

/**
 * Once per ruin, as it comes into view and before its knights first come: Minecraft grows its trees, huge mushrooms and
 * ice spikes after the structures, so some grow into a ruin (through its fight floor, its frame), and its patches of
 * andesite, diorite, dirt, gravel and clay and its ores land in the ruin's stone. Every tree touching its footprint is
 * felled whole (world-builder's {@link TreeFelling}, as round the cathedral and the Rift Chapel), then its template is
 * placed again, so its own blocks come back (its designed trees among them: a felling takes those too). A step a server
 * tick, at most about 40 ms each.
 */
final class KnightRuinTidy
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** A tree touching the ruin is followed at most this far out of its footprint; this land must be loaded. */
    static final int REACH = 16;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private final ServerLevel level;
    private final RuinSite site;
    private final BoundingBox foot;
    private final TreeFelling.Felling felling;
    private final long started = System.nanoTime();

    KnightRuinTidy(ServerLevel level, RuinSite site)
    {
        this.level = level;
        this.site = site;
        this.foot = site.footprint();
        this.felling = new TreeFelling.Felling(level, new Area(this.foot, site.groundY()));
    }

    /** True once every chunk the felling may read (the footprint and {@value #REACH} blocks round it) is fully loaded. */
    static boolean landLoaded(ServerLevel level, RuinSite site)
    {
        BoundingBox foot = site.footprint();
        for (int cx = SectionPos.blockToSectionCoord(foot.minX() - REACH); cx <= SectionPos.blockToSectionCoord(foot.maxX() + REACH); cx++)
        {
            for (int cz = SectionPos.blockToSectionCoord(foot.minZ() - REACH); cz <= SectionPos.blockToSectionCoord(foot.maxZ() + REACH); cz++)
            {
                if (level.getChunkSource().getChunkNow(cx, cz) == null)
                {
                    return false;
                }
            }
        }
        return true;
    }

    /** At most about 40 ms of the felling, then (once it's done) the template placed again; true once it's all done. */
    boolean step()
    {
        if (!this.felling.step())
        {
            return false;
        }
        Optional<StructureTemplate> template = this.level.getStructureManager().get(this.site.design().template());
        if (template.isPresent())
        {
            StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(this.site.rotation())
                    .setRotationPivot(this.site.pivot()).setIgnoreEntities(true).setKnownShape(true);
            BlockPos origin = this.site.origin();
            template.get().placeInWorld(this.level, origin, origin, settings, RandomSource.create(origin.asLong()), FLAGS);
        }
        LOGGER.info("Knight ruin {}: felled what grew into it ({}) and put its own blocks back, in {} ms", this.site.describe(),
                this.felling.result().summary(), (System.nanoTime() - this.started) / 1_000_000L);
        return true;
    }

    /** The felling's area: the footprint's columns over its ground; a tree touching it is followed {@value #REACH} blocks out. */
    private record Area(BoundingBox foot, int ground) implements TreeFelling.Area
    {
        @Override public int minX() { return this.foot.minX(); }
        @Override public int minZ() { return this.foot.minZ(); }
        @Override public int sizeX() { return this.foot.getXSpan(); }
        @Override public int sizeZ() { return this.foot.getZSpan(); }
        @Override public boolean isTouched(int x, int z) { return true; }
        @Override public int scanFrom(int x, int z) { return this.ground; }
        @Override public int groundAt(int x, int z) { return this.ground; }

        @Override
        public boolean destroyed(BlockPos pos)
        {
            return pos.getY() > this.ground && pos.getX() >= this.foot.minX() && pos.getX() <= this.foot.maxX()
                    && pos.getZ() >= this.foot.minZ() && pos.getZ() <= this.foot.maxZ();
        }

        @Override
        public boolean reaches(BlockPos pos)
        {
            return pos.getX() >= this.foot.minX() - REACH && pos.getX() <= this.foot.maxX() + REACH
                    && pos.getZ() >= this.foot.minZ() - REACH && pos.getZ() <= this.foot.maxZ() + REACH;
        }
    }
}
