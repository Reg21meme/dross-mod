package com.reg21meme.dross.boss.client;

import com.reg21meme.dross.Dross;
import net.minecraft.resources.ResourceLocation;

/**
 * The Emberdactyl's boss bar, written by {@code tools/fire/bossbars.py} (don't edit by hand: change the drawing in
 * {@code tools/fire/emberbars.py} and run it again). The user's pick, the Winged Volcano ("combine the wings of 2 with
 * the volcano of 3"): a volcano on the middle of the bar and burning wings spread out from its ends, in two looks: the
 * adult's ({@code boss.dross.emberdactyl.bar.adult}, "Emberdactyl: The Sky That Burns": obsidian, the volcano smoking)
 * and its young reborn's ({@code boss.dross.emberdactyl.bar.young}, "Emberdactyl: The Ember That Remains": hotter, the
 * volcano erupting). The texture holds both looks' empty bar, full bar and decorations.
 * <p>
 * Every position is relative to Minecraft's boss bar position (the x, y of its 182 x 5 bar). To draw a look: move it
 * down by {@code max(0, -nameY - 9)} so its name stays on the screen; blit the empty bar; blit the full bar over it,
 * cut off at the health (its columns from {@link Look#fillLeft()} for {@code round(progress * fillWidth())} pixels, at
 * the same offset); blit the decorations on top; draw the name centred on x + 91 at {@link Look#nameY()}; and leave
 * {@link Look#height()} pixels before the next bar.
 */
public final class EmberdactylBarArt
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

    public static final ResourceLocation TEXTURE = new ResourceLocation(Dross.MODID, "textures/gui/emberdactyl_bar.png");
    public static final int TEXTURE_WIDTH = 256;
    public static final int TEXTURE_HEIGHT = 128;

    /** The adult's look, "Emberdactyl: The Sky That Burns": obsidian wings and rails, the volcano smoking. */
    public static final Look ADULT = new Look(new Part(0, 0, 186, 9, -2, -2),
            new Part(0, 11, 186, 9, -2, -2),
            new Part(0, 22, 238, 34, -28, -21), 2, 2, -31, 49);
    /** The young reborn's look, "Emberdactyl: The Ember That Remains": magma crust, the volcano erupting. */
    public static final Look YOUNG = new Look(new Part(0, 58, 186, 9, -2, -2),
            new Part(0, 69, 186, 9, -2, -2),
            new Part(0, 80, 238, 34, -28, -21), 2, 2, -31, 49);

    /** The look a bar's key names (what follows {@code boss.dross.emberdactyl.bar.}: "adult" or "young"), or null. */
    public static Look look(String key)
    {
        return switch (key)
        {
            case "adult" -> ADULT;
            case "young" -> YOUNG;
            default -> null;
        };
    }

    private EmberdactylBarArt() {}
}
