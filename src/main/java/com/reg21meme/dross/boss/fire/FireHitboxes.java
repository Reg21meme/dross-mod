package com.reg21meme.dross.boss.fire;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import org.slf4j.Logger;

/**
 * The boxes round each piece of a moving fire mob's model through its animations (the user, November 5, 2026: "make
 * more specific boxes around them, not just 1 giant box: a box around each leg, then their giant claw, their shell; and
 * the same with the Emberdactyl, its body and wings as it swings"): the Emberdactyl's bill, skull, horns, neck, body,
 * tail, legs and wings in pieces; the Cinder Hermit's shell, body, eyes, claws and legs, every 0.1 s of every animation
 * but its death. They're each mob's hitboxes ({@link ShapedPart}), and the Emberdactyl's are also what blocks stop.
 * <p>
 * Read from {@code assets/dross/hitboxes/<model>.bin} in the mod's own files (on both sides), made by
 * {@code tools/fire/hitboxes.py} from the game's model and animation files (its frame: blocks, x its own left, y up from
 * its feet, z with its front at -z).
 */
public final class FireHitboxes
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final short HIDDEN = 32767;
    /** Blocks every box is grown by round its piece (tools/fire/hitboxes.py's PAD). */
    public static final double PAD = 0.1D;
    private static final Map<String, Optional<Shapes>> LOADED = new ConcurrentHashMap<>();

    /** One model's pieces and their boxes, frame by frame, for each of its animations. */
    public record Shapes(String model, List<String> pieces, Map<String, Frames> anims)
    {
        /**
         * The pieces' boxes at {@code seconds} into {@code anim} (a loop goes round; a move holds its last frame), each
         * {@code {x0, y0, z0, x1, y1, z1}} or null where nothing of that piece shows; the first animation's first frame
         * for an animation it hasn't got (its death).
         */
        public double[][] at(String anim, double seconds, boolean loop)
        {
            Frames f = this.anims.get(anim);
            if (f == null)
            {
                f = this.anims.values().iterator().next();
                seconds = 0.0D;
            }
            int n = f.boxes.length;
            int k = (int) Math.floor(Math.max(0.0D, seconds) / f.step + 0.5D);
            if (loop && n > 1)
            {
                k %= n - 1;          // (its last frame is its first again)
            }
            return f.boxes[Math.min(k, n - 1)];
        }

        /** The index of the piece named {@code name}, or -1. */
        public int piece(String name)
        {
            return this.pieces.indexOf(name);
        }
    }

    /** One animation's frames: {@code boxes[frame][piece]}. */
    public record Frames(double step, double[][][] boxes)
    {
    }

    private FireHitboxes() {}

    /** The pieces of this model ({@code emberdactyla}, {@code mobhermitc}...), or null if it has none. */
    @Nullable
    public static Shapes of(String model)
    {
        return LOADED.computeIfAbsent(model, FireHitboxes::load).orElse(null);
    }

    private static Optional<Shapes> load(String model)
    {
        String path = "/assets/" + Dross.MODID + "/hitboxes/" + model + ".bin";
        try (InputStream in = FireHitboxes.class.getResourceAsStream(path))
        {
            if (in == null)
            {
                return Optional.empty();
            }
            DataInputStream data = new DataInputStream(in);
            byte[] magic = new byte[4];
            data.readFully(magic);
            if (!"DRHB".equals(new String(magic, java.nio.charset.StandardCharsets.US_ASCII)) || data.readShort() != 1)
            {
                LOGGER.error("[dross] {} isn't a hitbox file this version reads", path);
                return Optional.empty();
            }
            int pieces = data.readShort();
            String[] names = new String[pieces];
            for (int i = 0; i < pieces; i++)
            {
                names[i] = data.readUTF();
            }
            int count = data.readShort();
            Map<String, Frames> anims = new HashMap<>();
            for (int a = 0; a < count; a++)
            {
                String name = data.readUTF();
                double step = data.readFloat();
                int frames = data.readShort();
                double[][][] boxes = new double[frames][pieces][];
                for (int f = 0; f < frames; f++)
                {
                    for (int p = 0; p < pieces; p++)
                    {
                        short[] v = new short[6];
                        for (int i = 0; i < 6; i++)
                        {
                            v[i] = data.readShort();
                        }
                        if (v[0] == HIDDEN)
                        {
                            continue;
                        }
                        boxes[f][p] = new double[] {v[0] / 16.0D, v[1] / 16.0D, v[2] / 16.0D, v[3] / 16.0D, v[4] / 16.0D, v[5] / 16.0D};
                    }
                }
                anims.put(name, new Frames(step, boxes));
            }
            return Optional.of(new Shapes(model, List.of(names), anims));
        }
        catch (IOException | RuntimeException e)
        {
            LOGGER.error("[dross] couldn't read the hitboxes {}", path, e);
            return Optional.empty();
        }
    }
}
