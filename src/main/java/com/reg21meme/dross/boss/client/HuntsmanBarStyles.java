package com.reg21meme.dross.boss.client;

import com.reg21meme.dross.Dross;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * The Huntsman's boss bar designs, written by {@code tools/huntsman/bossbars.py} (don't edit by hand: change the
 * drawings there and run it again). Ten designs for the user to pick from, each with five looks: the Wild Hunt (stage 1),
 * the Huntsman and the Alpha Moose (stage 2), and those two angry (when the other has died). Each design's texture holds,
 * for every look, the empty bar, the full bar and the decorations.
 * <p>
 * Every position is relative to Minecraft's boss bar position (the x, y of its 182 x 5 bar). To draw a look: blit the
 * empty bar; blit the full bar over it, cut off at the health (its columns from {@link Look#fillLeft()} for
 * {@code round(progress * fillWidth())} pixels, at the same offset); blit the decorations on top; draw the name centred
 * on x + 91 at y + {@link Style#nameY()}; and leave {@link Style#height()} pixels before the next bar.
 */
public final class HuntsmanBarStyles
{
    /** One piece of a bar: where it is in the texture (u, v), its size, and where it's drawn from the bar's x, y. */
    public record Part(int u, int v, int width, int height, int x, int y)
    {
    }

    /** One look: the empty bar, the full bar (the same size and place), the decorations, and where the fill runs. */
    public record Look(Part empty, Part full, Part decorations, int fillLeft, int fillRight)
    {
        /** How many pixels wide the fill is at full health (182, like Minecraft's bar). */
        public int fillWidth()
        {
            return full.width() - fillLeft - fillRight;
        }
    }

    /**
     * One design.
     *
     * @param number the design's number (1 to 10)
     * @param name   its working name
     */
    public record Style(int number, String name, ResourceLocation texture, int textureWidth, int textureHeight, Look wildHunt,
                       Look huntsman, Look huntsmanAngry, Look alphaMoose, Look alphaMooseAngry, int nameY, int height)
    {
        /** The look of one bar: "wild_hunt", "huntsman", "huntsman_angry", "alpha_moose" or "alpha_moose_angry". */
        public Look look(String bar)
        {
            return switch (bar)
            {
                case "huntsman" -> this.huntsman;
                case "huntsman_angry" -> this.huntsmanAngry;
                case "alpha_moose" -> this.alphaMoose;
                case "alpha_moose_angry" -> this.alphaMooseAngry;
                default -> this.wildHunt;
            };
        }
    }

    /** Every design, in order. */
    public static final List<Style> ALL = List.of(
            new Style(1, "Antler Frame",
                    new ResourceLocation(Dross.MODID, "textures/gui/huntsman_bar/option_1.png"), 256, 256,
                    new Look(new Part(0, 0, 186, 9, -2, -2), new Part(0, 11, 186, 9, -2, -2), new Part(0, 22, 221, 23, -18, -17), 2, 2),
                    new Look(new Part(0, 47, 186, 9, -2, -2), new Part(0, 58, 186, 9, -2, -2), new Part(0, 69, 217, 23, -18, -17), 2, 2),
                    new Look(new Part(0, 94, 186, 9, -2, -2), new Part(0, 105, 186, 9, -2, -2), new Part(0, 116, 217, 23, -18, -17), 2, 2),
                    new Look(new Part(0, 141, 186, 9, -2, -2), new Part(0, 152, 186, 9, -2, -2), new Part(0, 163, 225, 22, -22, -16), 2, 2),
                    new Look(new Part(0, 187, 186, 9, -2, -2), new Part(0, 198, 186, 9, -2, -2), new Part(0, 209, 225, 22, -22, -16), 2, 2),
                    -12, 24),
            new Style(2, "Hunting Horn",
                    new ResourceLocation(Dross.MODID, "textures/gui/huntsman_bar/option_2.png"), 256, 512,
                    new Look(new Part(0, 0, 197, 13, -13, -4), new Part(0, 15, 197, 13, -13, -4), new Part(0, 30, 209, 31, -11, -14), 13, 2),
                    new Look(new Part(0, 63, 197, 13, -13, -4), new Part(0, 78, 197, 13, -13, -4), new Part(0, 93, 189, 31, 9, -14), 13, 2),
                    new Look(new Part(0, 126, 197, 13, -13, -4), new Part(0, 141, 197, 13, -13, -4), new Part(0, 156, 189, 31, 9, -14), 13, 2),
                    new Look(new Part(0, 189, 197, 13, -13, -4), new Part(0, 204, 197, 13, -13, -4), new Part(0, 219, 189, 31, 9, -14), 13, 2),
                    new Look(new Part(0, 252, 197, 13, -13, -4), new Part(0, 267, 197, 13, -13, -4), new Part(0, 282, 189, 31, 9, -14), 13, 2),
                    -14, 36),
            new Style(3, "Leather & Bone",
                    new ResourceLocation(Dross.MODID, "textures/gui/huntsman_bar/option_3.png"), 256, 512,
                    new Look(new Part(0, 0, 190, 11, -4, -3), new Part(0, 13, 190, 11, -4, -3), new Part(0, 26, 205, 26, -12, -6), 4, 4),
                    new Look(new Part(0, 54, 190, 11, -4, -3), new Part(0, 67, 190, 11, -4, -3), new Part(0, 80, 205, 26, -12, -6), 4, 4),
                    new Look(new Part(0, 108, 190, 11, -4, -3), new Part(0, 121, 190, 11, -4, -3), new Part(0, 134, 205, 26, -12, -6), 4, 4),
                    new Look(new Part(0, 162, 190, 11, -4, -3), new Part(0, 175, 190, 11, -4, -3), new Part(0, 188, 205, 24, -12, -6), 4, 4),
                    new Look(new Part(0, 214, 190, 11, -4, -3), new Part(0, 227, 190, 11, -4, -3), new Part(0, 240, 205, 24, -12, -6), 4, 4),
                    -13, 38),
            new Style(4, "Cracked Ice",
                    new ResourceLocation(Dross.MODID, "textures/gui/huntsman_bar/option_4.png"), 256, 512,
                    new Look(new Part(0, 0, 192, 12, -5, -4), new Part(0, 14, 192, 12, -5, -4), new Part(0, 28, 215, 23, -17, -8), 5, 5),
                    new Look(new Part(0, 53, 192, 12, -5, -4), new Part(0, 67, 192, 12, -5, -4), new Part(0, 81, 215, 23, -17, -8), 5, 5),
                    new Look(new Part(0, 106, 192, 12, -5, -4), new Part(0, 120, 192, 12, -5, -4), new Part(0, 134, 215, 23, -17, -8), 5, 5),
                    new Look(new Part(0, 159, 192, 12, -5, -4), new Part(0, 173, 192, 12, -5, -4), new Part(0, 187, 215, 23, -17, -8), 5, 5),
                    new Look(new Part(0, 212, 192, 12, -5, -4), new Part(0, 226, 192, 12, -5, -4), new Part(0, 240, 215, 23, -17, -8), 5, 5),
                    -14, 34),
            new Style(5, "Arrow",
                    new ResourceLocation(Dross.MODID, "textures/gui/huntsman_bar/option_5.png"), 256, 256,
                    new Look(new Part(0, 0, 186, 9, -2, -2), new Part(0, 11, 186, 9, -2, -2), new Part(0, 22, 231, 21, -26, -8), 2, 2),
                    new Look(new Part(0, 45, 186, 9, -2, -2), new Part(0, 56, 186, 9, -2, -2), new Part(0, 67, 231, 21, -26, -8), 2, 2),
                    new Look(new Part(0, 90, 186, 9, -2, -2), new Part(0, 101, 186, 9, -2, -2), new Part(0, 112, 231, 21, -26, -8), 2, 2),
                    new Look(new Part(0, 135, 186, 9, -2, -2), new Part(0, 146, 186, 9, -2, -2), new Part(0, 157, 231, 21, -26, -8), 2, 2),
                    new Look(new Part(0, 180, 186, 9, -2, -2), new Part(0, 191, 186, 9, -2, -2), new Part(0, 202, 231, 21, -26, -8), 2, 2),
                    -12, 30),
            new Style(6, "Spear",
                    new ResourceLocation(Dross.MODID, "textures/gui/huntsman_bar/option_6.png"), 256, 512,
                    new Look(new Part(0, 0, 186, 11, -2, -3), new Part(0, 13, 186, 11, -2, -3), new Part(0, 26, 223, 26, -13, -7), 2, 2),
                    new Look(new Part(0, 54, 186, 11, -2, -3), new Part(0, 67, 186, 11, -2, -3), new Part(0, 80, 223, 26, -13, -7), 2, 2),
                    new Look(new Part(0, 108, 186, 11, -2, -3), new Part(0, 121, 186, 11, -2, -3), new Part(0, 134, 223, 26, -13, -7), 2, 2),
                    new Look(new Part(0, 162, 186, 11, -2, -3), new Part(0, 175, 186, 11, -2, -3), new Part(0, 188, 223, 26, -13, -7), 2, 2),
                    new Look(new Part(0, 216, 186, 11, -2, -3), new Part(0, 229, 186, 11, -2, -3), new Part(0, 242, 223, 26, -13, -7), 2, 2),
                    -12, 36),
            new Style(7, "Fur Pelt",
                    new ResourceLocation(Dross.MODID, "textures/gui/huntsman_bar/option_7.png"), 256, 512,
                    new Look(new Part(0, 0, 192, 15, -5, -4), new Part(0, 17, 192, 15, -5, -4), new Part(0, 34, 224, 22, -20, -12), 5, 5),
                    new Look(new Part(0, 58, 192, 15, -5, -4), new Part(0, 75, 192, 15, -5, -4), new Part(0, 92, 221, 16, -20, -6), 5, 5),
                    new Look(new Part(0, 110, 192, 17, -5, -5), new Part(0, 129, 192, 17, -5, -5), new Part(0, 148, 221, 16, -20, -6), 5, 5),
                    new Look(new Part(0, 166, 192, 15, -5, -4), new Part(0, 183, 192, 15, -5, -4), new Part(0, 200, 214, 22, -22, -12), 5, 5),
                    new Look(new Part(0, 224, 192, 17, -5, -5), new Part(0, 243, 192, 17, -5, -5), new Part(0, 262, 214, 22, -22, -12), 5, 5),
                    -15, 32),
            new Style(8, "Tracks in the Snow",
                    new ResourceLocation(Dross.MODID, "textures/gui/huntsman_bar/option_8.png"), 256, 256,
                    new Look(new Part(0, 0, 196, 14, -7, -6), new Part(0, 16, 196, 14, -7, -6), new Part(0, 32, 222, 17, -20, -11), 7, 7),
                    new Look(new Part(0, 51, 196, 14, -7, -6), new Part(0, 67, 196, 14, -7, -6), new Part(0, 83, 222, 17, -20, -11), 7, 7),
                    new Look(new Part(0, 102, 196, 14, -7, -6), new Part(0, 118, 196, 14, -7, -6), new Part(0, 134, 222, 17, -20, -11), 7, 7),
                    new Look(new Part(0, 153, 196, 14, -7, -6), new Part(0, 169, 196, 14, -7, -6), new Part(0, 185, 222, 17, -20, -11), 7, 7),
                    new Look(new Part(0, 204, 196, 14, -7, -6), new Part(0, 220, 196, 14, -7, -6), new Part(0, 236, 222, 17, -20, -11), 7, 7),
                    -16, 29),
            new Style(9, "Skull Trophy",
                    new ResourceLocation(Dross.MODID, "textures/gui/huntsman_bar/option_9.png"), 256, 512,
                    new Look(new Part(0, 0, 188, 10, -3, -3), new Part(0, 12, 188, 10, -3, -3), new Part(0, 24, 198, 30, -8, -21), 3, 3),
                    new Look(new Part(0, 56, 188, 10, -3, -3), new Part(0, 68, 188, 10, -3, -3), new Part(0, 80, 198, 27, -8, -18), 3, 3),
                    new Look(new Part(0, 109, 188, 10, -3, -3), new Part(0, 121, 188, 10, -3, -3), new Part(0, 133, 198, 27, -8, -18), 3, 3),
                    new Look(new Part(0, 162, 188, 10, -3, -3), new Part(0, 174, 188, 10, -3, -3), new Part(0, 186, 198, 30, -8, -21), 3, 3),
                    new Look(new Part(0, 218, 188, 10, -3, -3), new Part(0, 230, 188, 10, -3, -3), new Part(0, 242, 198, 30, -8, -21), 3, 3),
                    -31, 45),
            new Style(10, "Aurora Ice",
                    new ResourceLocation(Dross.MODID, "textures/gui/huntsman_bar/option_10.png"), 256, 256,
                    new Look(new Part(0, 0, 186, 9, -2, -2), new Part(0, 11, 186, 9, -2, -2), new Part(0, 22, 203, 21, -11, -14), 2, 2),
                    new Look(new Part(0, 45, 186, 9, -2, -2), new Part(0, 56, 186, 9, -2, -2), new Part(0, 67, 203, 21, -11, -14), 2, 2),
                    new Look(new Part(0, 90, 186, 9, -2, -2), new Part(0, 101, 186, 9, -2, -2), new Part(0, 112, 203, 21, -11, -14), 2, 2),
                    new Look(new Part(0, 135, 186, 9, -2, -2), new Part(0, 146, 186, 9, -2, -2), new Part(0, 157, 203, 21, -11, -14), 2, 2),
                    new Look(new Part(0, 180, 186, 9, -2, -2), new Part(0, 191, 186, 9, -2, -2), new Part(0, 202, 203, 21, -11, -14), 2, 2),
                    -13, 25));

    /** The design with this number (1 to 10), or the first. */
    public static Style byNumber(int number)
    {
        for (Style style : ALL)
        {
            if (style.number() == number)
            {
                return style;
            }
        }
        return ALL.get(0);
    }

    private HuntsmanBarStyles() {}
}
