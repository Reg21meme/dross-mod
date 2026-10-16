package com.reg21meme.dross.boss.client;

import com.reg21meme.dross.Dross;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * The Rat King's boss bar designs, written by {@code tools/ratking/bossbars.py} (don't edit by hand: change the
 * drawings there and run it again). Eight options, each with a normal look and a Rage look; each option's texture
 * holds, for both looks, the empty bar, the full bar and the decorations.
 * <p>
 * Every position is relative to Minecraft's boss bar position (the x, y of its 182 x 5 bar). To draw one: blit the
 * empty bar; blit the full bar over it, cut off at the health (its columns from {@link Look#fillLeft()} for
 * {@code round(progress * fillWidth())} pixels, at the same offset); blit the decorations on top; draw the name
 * centred on x + 91 at y + {@link Style#nameY()}; and leave {@link Style#height()} pixels before the next bar.
 */
public final class RatKingBarStyles
{
    /**
     * One piece of a bar.
     *
     * @param u      where it is in the option's texture (left)...
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
     * One look of an option.
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

    /**
     * One option.
     *
     * @param number        1 to 8
     * @param name          its working name
     * @param texture       its texture ({@code textures/gui/rat_king_bar/option_<n>.png})
     * @param textureWidth  the texture's size (for blit): wide...
     * @param textureHeight ...and tall
     * @param normal        the usual look (sickly green)
     * @param rage          the Rage look (red)
     * @param nameY         where the name's top goes, from the bar's y (Minecraft: -9)
     * @param height        pixels each bar takes, from its name's top to the next bar's (Minecraft: 19)
     */
    public record Style(int number, String name, ResourceLocation texture, int textureWidth, int textureHeight,
                        Look normal, Look rage, int nameY, int height)
    {
    }

    public static final List<Style> ALL = List.of(
            new Style(1, "Crown",
                    new ResourceLocation(Dross.MODID, "textures/gui/rat_king_bar/option_1.png"),
                    256, 64,
                    new Look(new Part(0, 0, 184, 7, -1, -1),
                            new Part(0, 9, 184, 7, -1, -1),
                            new Part(0, 18, 19, 11, 82, -6), 1, 1),
                    new Look(new Part(0, 31, 184, 7, -1, -1),
                            new Part(0, 40, 184, 7, -1, -1),
                            new Part(0, 49, 19, 11, 82, -6), 1, 1),
                    -16, 27),
            new Style(2, "Nose & whiskers",
                    new ResourceLocation(Dross.MODID, "textures/gui/rat_king_bar/option_2.png"),
                    256, 128,
                    new Look(new Part(0, 0, 184, 7, -1, -1),
                            new Part(0, 9, 184, 7, -1, -1),
                            new Part(0, 18, 41, 15, 71, -8), 1, 1),
                    new Look(new Part(0, 35, 184, 7, -1, -1),
                            new Part(0, 44, 184, 7, -1, -1),
                            new Part(0, 53, 41, 17, 71, -8), 1, 1),
                    -18, 32),
            new Style(3, "Curling tail",
                    new ResourceLocation(Dross.MODID, "textures/gui/rat_king_bar/option_3.png"),
                    256, 128,
                    new Look(new Part(0, 0, 184, 7, -1, -1),
                            new Part(0, 9, 184, 7, -1, -1),
                            new Part(0, 18, 14, 17, 181, -12), 1, 1),
                    new Look(new Part(0, 37, 184, 7, -1, -1),
                            new Part(0, 46, 184, 7, -1, -1),
                            new Part(0, 55, 14, 17, 181, -12), 1, 1),
                    -10, 21),
            new Style(4, "Fur edge",
                    new ResourceLocation(Dross.MODID, "textures/gui/rat_king_bar/option_4.png"),
                    256, 128,
                    new Look(new Part(0, 0, 184, 7, -1, -1),
                            new Part(0, 9, 184, 7, -1, -1),
                            new Part(0, 18, 187, 14, -2, -5), 1, 1),
                    new Look(new Part(0, 34, 184, 7, -1, -1),
                            new Part(0, 43, 184, 7, -1, -1),
                            new Part(0, 52, 188, 19, -3, -8), 1, 1),
                    -18, 34),
            new Style(5, "Gnawed",
                    new ResourceLocation(Dross.MODID, "textures/gui/rat_king_bar/option_5.png"),
                    256, 128,
                    new Look(new Part(0, 0, 188, 11, -3, -3),
                            new Part(0, 13, 188, 11, -3, -3),
                            new Part(0, 26, 164, 9, 6, -2), 3, 3),
                    new Look(new Part(0, 37, 188, 11, -3, -3),
                            new Part(0, 50, 188, 11, -3, -3),
                            new Part(0, 63, 185, 9, -2, -2), 3, 3),
                    -13, 26),
            new Style(6, "Sewer pipe",
                    new ResourceLocation(Dross.MODID, "textures/gui/rat_king_bar/option_6.png"),
                    256, 128,
                    new Look(new Part(0, 0, 194, 18, -6, -5),
                            new Part(0, 20, 194, 18, -6, -5),
                            new Part(0, 40, 1, 1, 0, 0), 6, 6),
                    new Look(new Part(0, 43, 194, 18, -6, -5),
                            new Part(0, 63, 194, 18, -6, -5),
                            new Part(0, 83, 1, 1, 0, 0), 6, 6),
                    -13, 31),
            new Style(7, "Claw & orb",
                    new ResourceLocation(Dross.MODID, "textures/gui/rat_king_bar/option_7.png"),
                    256, 128,
                    new Look(new Part(0, 0, 184, 7, -1, -1),
                            new Part(0, 9, 184, 7, -1, -1),
                            new Part(0, 18, 20, 17, -17, -6), 1, 1),
                    new Look(new Part(0, 37, 184, 7, -1, -1),
                            new Part(0, 46, 184, 7, -1, -1),
                            new Part(0, 55, 20, 17, -17, -6), 1, 1),
                    -10, 26),
            new Style(8, "Beady eyes",
                    new ResourceLocation(Dross.MODID, "textures/gui/rat_king_bar/option_8.png"),
                    256, 128,
                    new Look(new Part(0, 0, 184, 7, -1, -1),
                            new Part(0, 9, 184, 7, -1, -1),
                            new Part(0, 18, 37, 13, 73, -11), 1, 1),
                    new Look(new Part(0, 33, 184, 7, -1, -1),
                            new Part(0, 42, 184, 7, -1, -1),
                            new Part(0, 51, 37, 13, 73, -11), 1, 1),
                    -21, 32),
            new Style(9, "Pipe & eyes",
                    new ResourceLocation(Dross.MODID, "textures/gui/rat_king_bar/option_9.png"),
                    256, 128,
                    new Look(new Part(0, 0, 194, 18, -6, -5),
                            new Part(0, 20, 194, 18, -6, -5),
                            new Part(0, 40, 31, 10, 76, -11), 6, 6),
                    new Look(new Part(0, 52, 194, 18, -6, -5),
                            new Part(0, 72, 194, 18, -6, -5),
                            new Part(0, 92, 31, 10, 76, -11), 6, 6),
                    -21, 39));

    /** The option with this number, or the first one. */
    public static Style byNumber(int number)
    {
        for (Style s : ALL)
        {
            if (s.number() == number)
            {
                return s;
            }
        }
        return ALL.get(0);
    }

    private RatKingBarStyles() {}
}
