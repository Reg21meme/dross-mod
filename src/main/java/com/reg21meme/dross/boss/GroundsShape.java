package com.reg21meme.dross.boss;

import com.reg21meme.dross.boss.emberdactyl.EmberVolcano;
import com.reg21meme.dross.boss.emberdactyl.EmberVolcanoTuning;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * One piece of a boss's protected home ({@link BossGrounds}) as a shape both sides can test a block against. The server
 * sends each player the shapes near them that are protected for them ({@link BossGroundsMessage}), so their own game
 * never starts to break one: no cracks and no block flickering back, like bedrock. The Colossus's lairs and the
 * Emberdactyl's volcanoes are tested with these very shapes on the server too ({@code LairWard.protects},
 * {@code CraterWard.covers}), so both sides always agree.
 */
public sealed interface GroundsShape permits GroundsShape.Box, GroundsShape.Round
{
    /** True if this block is inside it. */
    boolean contains(BlockPos pos);

    /** True if any of its columns may lie within {@code radius} blocks of this spot (along the ground). */
    boolean near(double x, double z, double radius);

    void write(FriendlyByteBuf buf);

    static GroundsShape read(FriendlyByteBuf buf)
    {
        byte kind = buf.readByte();
        return switch (kind)
        {
            case Box.KIND -> Box.read(buf);
            case Round.KIND -> Round.read(buf);
            default -> throw new DecoderException("not a protected ground's shape: " + kind);
        };
    }

    /** A box of blocks, both corners inside it (a structure piece's, cut to a chunk; the hub's). */
    record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) implements GroundsShape
    {
        static final byte KIND = 0;

        public static Box of(BoundingBox box)
        {
            return new Box(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
        }

        @Override
        public boolean contains(BlockPos pos)
        {
            return pos.getX() >= minX && pos.getX() <= maxX && pos.getY() >= minY && pos.getY() <= maxY
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }

        @Override
        public boolean near(double x, double z, double radius)
        {
            double dx = Math.max(0.0D, Math.max(minX - x, x - (maxX + 1)));
            double dz = Math.max(0.0D, Math.max(minZ - z, z - (maxZ + 1)));
            return dx * dx + dz * dz <= radius * radius;
        }

        @Override
        public void write(FriendlyByteBuf buf)
        {
            buf.writeByte(KIND);
            buf.writeInt(minX);
            buf.writeInt(minY);
            buf.writeInt(minZ);
            buf.writeInt(maxX);
            buf.writeInt(maxY);
            buf.writeInt(maxZ);
        }

        static Box read(FriendlyByteBuf buf)
        {
            return new Box(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
        }
    }

    /**
     * The columns round a middle ({@code cx}, {@code cz}: block middles are measured from it) out to {@code radius}, from
     * {@code minY} up to {@code maxY}: a Colossus lair's. With {@code lobes} (a volcano's three phases,
     * {@link EmberVolcano#lobePhases}) its edge is pushed in and out like the Emberdactyl's volcano's foot
     * ({@link EmberVolcano#lobes}); with none it's round.
     */
    record Round(double cx, double cz, double radius, int minY, int maxY, double[] lobes) implements GroundsShape
    {
        static final byte KIND = 1;
        /** No lobes: round. */
        public static final double[] ROUND = new double[0];
        /** The widest a lobed edge is pushed out (its lobes at their most). */
        private static final double WIDEST_LOBES = 1.0D + EmberVolcanoTuning.LOBE_3 + EmberVolcanoTuning.LOBE_5
                + EmberVolcanoTuning.LOBE_2;

        public Round
        {
            if (lobes.length != 0 && lobes.length != 3)
            {
                throw new IllegalArgumentException("a volcano's foot has 3 lobe phases, not " + lobes.length);
            }
        }

        @Override
        public boolean contains(BlockPos pos)
        {
            if (pos.getY() < minY || pos.getY() > maxY)
            {
                return false;
            }
            double dx = pos.getX() + 0.5D - cx;
            double dz = pos.getZ() + 0.5D - cz;
            double r = Math.sqrt(dx * dx + dz * dz);
            return r <= (lobes.length == 0 ? radius : radius * EmberVolcano.lobes(lobes, Math.atan2(dz, dx)));
        }

        @Override
        public boolean near(double x, double z, double radius)
        {
            double dx = x - cx;
            double dz = z - cz;
            return Math.sqrt(dx * dx + dz * dz) <= widest() + radius;
        }

        /** The furthest any of its columns reaches from its middle. */
        public double widest()
        {
            return lobes.length == 0 ? radius : radius * WIDEST_LOBES;
        }

        @Override
        public void write(FriendlyByteBuf buf)
        {
            buf.writeByte(KIND);
            buf.writeDouble(cx);
            buf.writeDouble(cz);
            buf.writeDouble(radius);
            buf.writeInt(minY);
            buf.writeInt(maxY);
            buf.writeByte(lobes.length);
            for (double lobe : lobes)
            {
                buf.writeDouble(lobe);
            }
        }

        static Round read(FriendlyByteBuf buf)
        {
            double cx = buf.readDouble();
            double cz = buf.readDouble();
            double radius = buf.readDouble();
            int minY = buf.readInt();
            int maxY = buf.readInt();
            int n = buf.readByte();
            if (n != 0 && n != 3)
            {
                throw new DecoderException("a volcano's foot has 3 lobe phases, not " + n);
            }
            double[] lobes = n == 0 ? ROUND : new double[n];
            for (int i = 0; i < n; i++)
            {
                lobes[i] = buf.readDouble();
            }
            return new Round(cx, cz, radius, minY, maxY, lobes);
        }
    }
}
