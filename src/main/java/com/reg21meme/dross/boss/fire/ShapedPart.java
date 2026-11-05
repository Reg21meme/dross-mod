package com.reg21meme.dross.boss.fire;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.entity.PartEntity;

/**
 * One piece of a moving fire mob as a hitbox of its own (the user, November 5, 2026: "make more specific boxes around
 * them, not just 1 giant box"): any shape, put round its piece of the model every tick from {@link FireHitboxes} (its
 * box in the mob's own frame, turned with its body). Like the Ender Dragon's parts: hitting one hurts the mob; it never
 * bumps into anything and is never saved. A piece with nothing showing (tucked into the hermit's shell, a furled
 * membrane) can't be hit.
 */
public class ShapedPart<T extends Mob> extends PartEntity<T>
{
    private final String name;
    private AABB box = new AABB(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
    private boolean shown;

    public ShapedPart(T parent, String name)
    {
        super(parent);
        this.name = name;
        this.refreshDimensions();
    }

    public String name()
    {
        return this.name;
    }

    /** True if this piece shows now (it can be hit). */
    public boolean shown()
    {
        return this.shown;
    }

    /**
     * Puts each part round its piece: {@code boxes[i]} for part i ({@code {x0, y0, z0, x1, y1, z1}}: blocks, x the mob's
     * own left, y up from its feet, z its front at -z; null: nothing shows), turned by the mob's body yaw (the box round
     * the turned box). {@code null} boxes: every part hidden.
     */
    public static void place(Mob mob, ShapedPart<?>[] parts, @Nullable double[][] boxes)
    {
        float yaw = mob.yBodyRot * Mth.DEG_TO_RAD;
        double cos = Mth.cos(yaw);
        double sin = Mth.sin(yaw);
        for (int i = 0; i < parts.length; i++)
        {
            double[] b = boxes == null || i >= boxes.length ? null : boxes[i];
            parts[i].put(mob, b == null ? null : turned(mob.getX(), mob.getY(), mob.getZ(), b, cos, sin));
        }
    }

    /** The world box round a box of the mob's own frame turned by its yaw (cos, sin), at its feet (x, y, z). */
    public static AABB turned(double x, double y, double z, double[] b, double cos, double sin)
    {
        double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z0 = Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        for (double lx : new double[] {b[0], b[3]})
        {
            for (double lz : new double[] {b[2], b[5]})
            {
                double wx = lx * cos + lz * sin;
                double wz = lx * sin - lz * cos;
                x0 = Math.min(x0, wx);
                x1 = Math.max(x1, wx);
                z0 = Math.min(z0, wz);
                z1 = Math.max(z1, wz);
            }
        }
        return new AABB(x + x0, y + b[1], z + z0, x + x1, y + b[4], z + z1);
    }

    private void put(Mob mob, @Nullable AABB at)
    {
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        this.xOld = this.getX();
        this.yOld = this.getY();
        this.zOld = this.getZ();
        this.shown = at != null;
        this.box = at != null ? at : new AABB(mob.getX(), mob.getY(), mob.getZ(), mob.getX(), mob.getY(), mob.getZ());
        this.setPos((this.box.minX + this.box.maxX) / 2.0D, this.box.minY, (this.box.minZ + this.box.maxZ) / 2.0D);
        this.setBoundingBox(this.box);
    }

    @Override
    protected AABB makeBoundingBox()
    {
        return this.box == null ? super.makeBoundingBox() : this.box;
    }

    @Override
    protected void defineSynchedData()
    {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag)
    {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag)
    {
    }

    @Override
    public boolean isPickable()
    {
        return this.shown;
    }

    @Nullable
    @Override
    public ItemStack getPickResult()
    {
        return this.getParent().getPickResult();
    }

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        return this.shown && !this.isInvulnerableTo(source) && this.getParent().hurt(source, amount);
    }

    @Override
    public boolean is(Entity entity)
    {
        return this == entity || this.getParent() == entity;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose)
    {
        return this.box == null ? EntityDimensions.scalable(0.1F, 0.1F)
                : EntityDimensions.scalable((float) Math.max(this.box.getXsize(), this.box.getZsize()), (float) this.box.getYsize());
    }

    @Override
    public boolean shouldBeSaved()
    {
        return false;
    }
}
