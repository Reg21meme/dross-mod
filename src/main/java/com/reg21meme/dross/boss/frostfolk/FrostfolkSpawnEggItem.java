package com.reg21meme.dross.boss.frostfolk;

import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.common.ForgeSpawnEggItem;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

/**
 * A Frostfolk spawn egg for one kind of them (the user, October 24, 2026: "don't make a spawn egg for all of them, just
 * one for the elder, one for warriors and one for workers"): each use brings one of that kind's callings at random.
 */
public class FrostfolkSpawnEggItem extends ForgeSpawnEggItem
{
    private static final RandomSource RANDOM = RandomSource.create();
    private final List<FolkRole> roles;

    public FrostfolkSpawnEggItem(FolkRole.Kind kind, Properties properties)
    {
        super(ModEntities.FROSTFOLK.get(first(kind)), kind.eggBase, kind.eggSpots, properties);
        this.roles = Arrays.stream(FolkRole.values()).filter(r -> r.kind == kind).toList();
    }

    private static FolkRole first(FolkRole.Kind kind)
    {
        return Arrays.stream(FolkRole.values()).filter(r -> r.kind == kind).findFirst().orElseThrow();
    }

    /** The callings it may bring. */
    public List<FolkRole> roles()
    {
        return this.roles;
    }

    /** One of its kind's callings at random (unless the egg's own tag names one). */
    @Override
    public EntityType<?> getType(@Nullable CompoundTag tag)
    {
        if (tag != null && tag.contains("EntityTag", Tag.TAG_COMPOUND) && tag.getCompound("EntityTag").contains("id"))
        {
            return super.getType(tag);
        }
        return ModEntities.FROSTFOLK.get(this.roles.get(RANDOM.nextInt(this.roles.size()))).get();
    }
}
