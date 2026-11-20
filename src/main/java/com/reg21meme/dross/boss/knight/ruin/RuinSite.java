package com.reg21meme.dross.boss.knight.ruin;

import com.reg21meme.dross.boss.knight.KnightKind;
import com.reg21meme.dross.boss.knight.KnightRuinDesigns;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * One knight ruin in the Overworld, as the seed plans it ({@link KnightRuinSites}): its middle column and ground level,
 * the way its way in faces, its design (its biome area's layout) and whether it's one of the few near the world spawn.
 * The same whenever and on whatever thread it's asked for. Its template is placed the showcase's way
 * ({@code command/KnightRuinShowcase}): turned about its middle so its way in (the template's south) faces {@link #front}.
 */
public record RuinSite(int x, int z, int groundY, Direction front, KnightRuinDesigns.Design design, boolean nearSpawn)
{
    /** Its name in saves and in the quest's signal: its middle column. */
    public String id()
    {
        return this.x + "," + this.z;
    }

    public KnightKind.Element element()
    {
        return this.design.element();
    }

    /** Its middle ground block. */
    public BlockPos ground()
    {
        return new BlockPos(this.x, this.groundY, this.z);
    }

    /** The chunk its structure starts in (its middle column's). */
    public ChunkPos startChunk()
    {
        return new ChunkPos(this.x >> 4, this.z >> 4);
    }

    /** Where a visitor stands: on the ground just out of its way in. */
    public BlockPos doorstep()
    {
        int out = Math.max(this.design.width(), this.design.depth()) / 2 + 4;
        return this.ground().relative(this.front, out).above();
    }

    public String describe()
    {
        return this.design.id() + " at " + this.x + " " + this.groundY + " " + this.z + ", facing " + this.front.getName()
                + (this.nearSpawn ? " (near spawn)" : "");
    }

    // ---------------------------------------------------------------- where its template's cells go

    public Rotation rotation()
    {
        return switch (this.front)
        {
            case WEST -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    public BlockPos pivot()
    {
        return new BlockPos(this.design.width() / 2, 0, this.design.depth() / 2);
    }

    /** The template's corner (before it's turned). */
    public BlockPos origin()
    {
        return this.ground().offset(-this.design.width() / 2, -this.design.ground(), -this.design.depth() / 2);
    }

    /** Where a cell of the template (its own coordinates, layer 0 at its bottom; may lie outside it) ends up. */
    public BlockPos toWorld(BlockPos local)
    {
        return StructureTemplate.transform(local, Mirror.NONE, this.rotation(), this.pivot()).offset(this.origin());
    }

    /** Its template's box in the world. */
    public BoundingBox footprint()
    {
        BlockPos a = this.toWorld(BlockPos.ZERO);
        BlockPos b = this.toWorld(new BlockPos(this.design.width() - 1, this.design.layers() - 1, this.design.depth() - 1));
        return BoundingBox.fromCorners(a, b);
    }

    /** True if a column is on its sea side (a shore ruin's back, the template's z under its shore line). */
    public boolean seaSide(int worldX, int worldZ)
    {
        if (this.design.area().setting() != KnightRuinDesigns.Setting.SHORE)
        {
            return false;
        }
        // (the template's z of a world column: how far it is from the back, along the way in)
        int back = this.design.depth() / 2;
        int along = (worldX - this.x) * this.front.getStepX() + (worldZ - this.z) * this.front.getStepZ();
        return along + back < KnightRuinDesigns.shoreLine(this.design.depth());
    }

    /** The two knights' spots in the world (feet's cells) and their yaws, as the showcase stands them. */
    public BlockPos guardCell(int i)
    {
        KnightRuinDesigns.Guard g = this.design.guards().get(i);
        return this.toWorld(new BlockPos(g.x(), this.design.ground() + g.y(), g.z()));
    }

    public float guardYaw(int i)
    {
        return this.front.toYRot() + this.design.guards().get(i).yaw();
    }
}
