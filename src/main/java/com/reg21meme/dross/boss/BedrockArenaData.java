package com.reg21meme.dross.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The bedrock test arenas built in a dimension ({@code /dross boss arena bedrock}), so their chat buttons (open and close
 * the walls between the sections, team up by section) find the arena they're about: where its floor's middle is and how
 * many teams it was built for. Saved per dimension ({@code data/dross_bedrock_arenas.dat}); an arena built over an older
 * one replaces it.
 */
public final class BedrockArenaData extends SavedData
{
    private static final String NAME = "dross_bedrock_arenas";

    /** One arena: the block under its floor's middle (its floor), and how many teams (sections) it has. */
    public record Arena(BlockPos floor, int teams)
    {
        /** True if a spot is in it (inside its walls, from its floor to its roof). */
        public boolean contains(BlockPos pos)
        {
            return Math.abs(pos.getX() - this.floor.getX()) <= BedrockArena.RADIUS + 1
                    && Math.abs(pos.getZ() - this.floor.getZ()) <= BedrockArena.RADIUS + 1
                    && pos.getY() >= this.floor.getY() - 1 && pos.getY() <= this.floor.getY() + BedrockArena.ROOF_HEIGHT + 1;
        }
    }

    private final List<Arena> arenas = new ArrayList<>();

    public static BedrockArenaData get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(BedrockArenaData::load, BedrockArenaData::new, NAME);
    }

    /** Records an arena (any older one it was built over is forgotten). */
    public void add(Arena arena)
    {
        int reach = BedrockArena.SIZE + 2;
        this.arenas.removeIf(a -> Math.abs(a.floor().getX() - arena.floor().getX()) < reach
                && Math.abs(a.floor().getZ() - arena.floor().getZ()) < reach
                && Math.abs(a.floor().getY() - arena.floor().getY()) <= BedrockArena.ROOF_HEIGHT);
        this.arenas.add(arena);
        this.setDirty();
    }

    /** Forgets the arena with its floor at {@code floor} (a test's, taken away again). */
    public void remove(BlockPos floor)
    {
        if (this.arenas.removeIf(a -> a.floor().equals(floor)))
        {
            this.setDirty();
        }
    }

    /** The arena {@code pos} is in, else the nearest within {@code range} blocks of it, else null. */
    @Nullable
    public Arena find(BlockPos pos, double range)
    {
        Arena best = null;
        double bestD = range * range;
        for (Arena a : this.arenas)
        {
            if (a.contains(pos))
            {
                return a;
            }
            double d = a.floor().distSqr(pos);
            if (d <= bestD)
            {
                bestD = d;
                best = a;
            }
        }
        return best;
    }

    private static BedrockArenaData load(CompoundTag tag)
    {
        BedrockArenaData data = new BedrockArenaData();
        for (Tag t : tag.getList("Arenas", Tag.TAG_COMPOUND))
        {
            CompoundTag a = (CompoundTag) t;
            data.arenas.add(new Arena(new BlockPos(a.getInt("X"), a.getInt("Y"), a.getInt("Z")), Math.max(1, a.getInt("Teams"))));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        ListTag list = new ListTag();
        for (Arena a : this.arenas)
        {
            CompoundTag t = new CompoundTag();
            t.putInt("X", a.floor().getX());
            t.putInt("Y", a.floor().getY());
            t.putInt("Z", a.floor().getZ());
            t.putInt("Teams", a.teams());
            list.add(t);
        }
        tag.put("Arenas", list);
        return tag;
    }
}
