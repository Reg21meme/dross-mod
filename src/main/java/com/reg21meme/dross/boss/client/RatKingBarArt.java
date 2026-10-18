package com.reg21meme.dross.boss.client;

import com.reg21meme.dross.Dross;
import net.minecraft.resources.ResourceLocation;

/**
 * Where each part of the Rat King's boss bar is in its texture and where it's drawn, written by
 * {@code tools/ratking/bossbars.py} (don't edit by hand: change the drawing there and run it again). The bar is the
 * user's pick of the nine designs made, No. 9 "Pipe & eyes", in its normal look and its Rage look; the texture holds,
 * for both looks, the empty bar, the full bar and the decorations.
 * <p>
 * Every position is relative to Minecraft's boss bar position (the x, y of its 182 x 5 bar). To draw it: blit the empty
 * bar; blit the full bar over it, cut off at the health (its columns from {@link Look#fillLeft()} for
 * {@code round(progress * fillWidth())} pixels, at the same offset); blit the decorations on top; draw the name
 * centred on x + 91 at y + {@link #NAME_Y}; and leave {@link #HEIGHT} pixels before the next bar.
 */
public final class RatKingBarArt
{
    /**
     * One piece of the bar.
     *
     * @param u      where it is in the texture (left)...
     * @param v      ...and top
     * @param width  its size in pixels: wide...
     * @param height ...and tall
     * @param x      where it's drawn, from the boss bar's x...
     * @param y      ...and y (negative: above the bar or to its left)
     */
    public record Part(int u, int v, int width, int height, int x, int y)
    {
    }

    /**
     * One look of the bar.
     *
     * @param empty       the empty bar
     * @param full        the full bar (the same size and place as the empty one)
     * @param decorations drawn over both, never cut off
     * @param fillLeft    the fill starts this many pixels in from the full bar's left edge...
     * @param fillRight   ...and stops this many before its right edge
     */
    public record Look(Part empty, Part full, Part decorations, int fillLeft, int fillRight)
    {
        /** How many pixels wide the fill is at full health (182, like Minecraft's bar). */
        public int fillWidth()
        {
            return full.width() - fillLeft - fillRight;
        }
    }

    /** The texture ({@code textures/gui/rat_king_bar.png}) and its size (for blit). */
    public static final ResourceLocation TEXTURE = new ResourceLocation(Dross.MODID, "textures/gui/rat_king_bar.png");
    public static final int TEXTURE_WIDTH = 256;
    public static final int TEXTURE_HEIGHT = 128;
    /** The usual look (sickly green). */
    public static final Look NORMAL = new Look(new Part(0, 0, 194, 18, -6, -5),
            new Part(0, 20, 194, 18, -6, -5),
            new Part(0, 40, 31, 10, 76, -11), 6, 6);
    /** The Rage look (red). */
    public static final Look RAGE = new Look(new Part(0, 52, 194, 18, -6, -5),
            new Part(0, 72, 194, 18, -6, -5),
            new Part(0, 92, 31, 10, 76, -11), 6, 6);
    /** Where the name's top goes, from the bar's y (Minecraft: -9). */
    public static final int NAME_Y = -21;
    /** Pixels each bar takes, from its name's top to the next bar's (Minecraft: 19). */
    public static final int HEIGHT = 39;

    private RatKingBarArt() {}
}
