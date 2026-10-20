package com.reg21meme.dross.boss.client;

import com.reg21meme.dross.Dross;
import net.minecraft.resources.ResourceLocation;

/**
 * The Huntsman's boss bars, written by {@code tools/huntsman/bossbars.py} (don't edit by hand: change the drawings there
 * and run it again). The user's picks: the Wild Hunt (stage 1) design 9, Skull Trophy; the Huntsman (stage 2) design 11,
 * Wolf &amp; Antlers; the Alpha Moose (stage 2) design 1, Antler Frame; each of the last two with its blood-red angry look
 * (when the other has died). The texture holds every look's empty bar, full bar and decorations.
 * <p>
 * Every position is relative to Minecraft's boss bar position (the x, y of its 182 x 5 bar). To draw a look: move it
 * down by {@code max(0, -nameY - 9)} so its name stays on the screen; blit the empty bar; blit the full bar over it,
 * cut off at the health (its columns from {@link Look#fillLeft()} for {@code round(progress * fillWidth())} pixels, at
 * the same offset); blit the decorations on top; draw the name centred on x + 91 at {@link Look#nameY()}; and leave
 * {@link Look#height()} pixels before the next bar.
 */
public final class HuntsmanBarArt
{
    /** One piece of a bar: where it is in the texture (u, v), its size, and where it's drawn from the bar's x, y. */
    public record Part(int u, int v, int width, int height, int x, int y)
    {
    }

    /** One look: the empty bar, the full bar (the same size and place), the decorations, where the fill runs, where its
     * name sits and how much room it takes. */
    public record Look(Part empty, Part full, Part decorations, int fillLeft, int fillRight, int nameY, int height)
    {
        /** How many pixels wide the fill is at full health (182, like Minecraft's bar). */
        public int fillWidth()
        {
            return full.width() - fillLeft - fillRight;
        }
    }

    public static final ResourceLocation TEXTURE = new ResourceLocation(Dross.MODID, "textures/gui/huntsman_bar.png");
    public static final int TEXTURE_WIDTH = 256;
    public static final int TEXTURE_HEIGHT = 512;

    /** Stage 1: The Wild Hunt, him riding the Alpha Moose, one bar for both (design 9, Skull Trophy). */
    public static final Look WILD_HUNT = new Look(new Part(0, 0, 188, 10, -3, -3),
            new Part(0, 12, 188, 10, -3, -3),
            new Part(0, 24, 198, 30, -8, -21), 3, 3, -31, 45);
    /** Stage 2: The Huntsman on foot (design 11, Wolf &amp; Antlers). */
    public static final Look HUNTSMAN = new Look(new Part(0, 56, 188, 10, -3, -3),
            new Part(0, 68, 188, 10, -3, -3),
            new Part(0, 80, 198, 32, -8, -23), 3, 3, -33, 47);
    /** Stage 2: The Huntsman, angry (the moose has died). */
    public static final Look HUNTSMAN_ANGRY = new Look(new Part(0, 114, 188, 10, -3, -3),
            new Part(0, 126, 188, 10, -3, -3),
            new Part(0, 138, 198, 32, -8, -23), 3, 3, -33, 47);
    /** Stage 2: the Alpha Moose, riderless (design 1, Antler Frame). */
    public static final Look ALPHA_MOOSE = new Look(new Part(0, 172, 186, 9, -2, -2),
            new Part(0, 183, 186, 9, -2, -2),
            new Part(0, 194, 225, 22, -22, -16), 2, 2, -12, 24);
    /** Stage 2: the Alpha Moose, angry (he has died). */
    public static final Look ALPHA_MOOSE_ANGRY = new Look(new Part(0, 218, 186, 9, -2, -2),
            new Part(0, 229, 186, 9, -2, -2),
            new Part(0, 240, 225, 22, -22, -16), 2, 2, -12, 24);

    /** The look a bar's key names ("wild_hunt", "huntsman", "huntsman_angry", "alpha_moose", "alpha_moose_angry"), or
     * null. */
    public static Look look(String key)
    {
        return switch (key)
        {
            case "wild_hunt" -> WILD_HUNT;
            case "huntsman" -> HUNTSMAN;
            case "huntsman_angry" -> HUNTSMAN_ANGRY;
            case "alpha_moose" -> ALPHA_MOOSE;
            case "alpha_moose_angry" -> ALPHA_MOOSE_ANGRY;
            default -> null;
        };
    }

    private HuntsmanBarArt() {}
}
