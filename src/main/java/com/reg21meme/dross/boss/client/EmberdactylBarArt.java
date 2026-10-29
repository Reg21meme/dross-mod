package com.reg21meme.dross.boss.client;

import com.reg21meme.dross.Dross;
import net.minecraft.resources.ResourceLocation;

/**
 * The Emberdactyl's boss bars, written by {@code tools/fire/bossbars.py} (don't edit by hand: change the drawings in
 * {@code tools/fire/emberbars.py} and run it again). Ten candidate designs for the user to pick from, each in two looks:
 * the adult's ("Emberdactyl: The Sky That Burns") and its young reborn's ("Emberdactyl: The Ember That Remains"), the
 * same design hotter. The fight's bars ({@code boss.dross.emberdactyl.bar.adult} and {@code .young}) use design
 * {@link #PICK}; the showcase's ({@code boss.dross.emberdactyl.bar.design_<n>_<adult|young>}) any design. The texture
 * holds every look's empty bar, full bar and decorations.
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
    public static final int TEXTURE_HEIGHT = 1024;
    /** How many designs there are (numbered from 1). */
    public static final int DESIGNS = 10;
    /** The design the fight's bars use (the user picks later; PICK in tools/fire/bossbars.py). */
    public static final int PICK = 1;
    /** Each design's working name, design 1 first. */
    private static final String[] NAMES = {"Half-Moon Crest", "Burning Wings", "Volcano", "Obsidian Bill", "Ember Egg", "Ember Gaze", "Blade Horns", "Molten Crust", "Talon Grip", "Firestorm"};

    /** Each design's two looks, design 1 first: the adult's, then the young's. */
    private static final Look[][] LOOKS = {
            // 1 Half-Moon Crest
            {new Look(new Part(0, 0, 186, 9, -2, -2),
                    new Part(0, 11, 186, 9, -2, -2),
                    new Part(0, 22, 214, 23, -16, -17), 2, 2, -27, 39),
                new Look(new Part(0, 47, 186, 9, -2, -2),
                    new Part(0, 58, 186, 9, -2, -2),
                    new Part(0, 69, 214, 23, -16, -17), 2, 2, -27, 39)},
            // 2 Burning Wings
            {new Look(new Part(0, 94, 186, 9, -2, -2),
                    new Part(0, 105, 186, 9, -2, -2),
                    new Part(0, 116, 238, 31, -28, -13), 2, 2, -14, 37),
                new Look(new Part(0, 149, 186, 9, -2, -2),
                    new Part(0, 160, 186, 9, -2, -2),
                    new Part(0, 171, 238, 31, -28, -13), 2, 2, -14, 37)},
            // 3 Volcano
            {new Look(new Part(0, 204, 186, 9, -2, -2),
                    new Part(0, 215, 186, 9, -2, -2),
                    new Part(0, 226, 198, 28, -8, -21), 2, 2, -31, 43),
                new Look(new Part(0, 256, 186, 9, -2, -2),
                    new Part(0, 267, 186, 9, -2, -2),
                    new Part(0, 278, 198, 28, -8, -21), 2, 2, -31, 43)},
            // 4 Obsidian Bill
            {new Look(new Part(0, 308, 199, 12, -2, -4),
                    new Part(0, 322, 199, 12, -2, -4),
                    new Part(0, 336, 220, 24, -38, -15), 2, 15, -16, 30),
                new Look(new Part(0, 362, 199, 12, -2, -4),
                    new Part(0, 376, 199, 12, -2, -4),
                    new Part(0, 390, 220, 24, -38, -15), 2, 15, -16, 30)},
            // 5 Ember Egg
            {new Look(new Part(0, 416, 186, 9, -2, -2),
                    new Part(0, 427, 186, 9, -2, -2),
                    new Part(0, 438, 196, 26, -7, -20), 2, 2, -30, 42),
                new Look(new Part(0, 466, 186, 9, -2, -2),
                    new Part(0, 477, 186, 9, -2, -2),
                    new Part(0, 488, 196, 26, -7, -20), 2, 2, -30, 42)},
            // 6 Ember Gaze
            {new Look(new Part(0, 516, 186, 9, -2, -2),
                    new Part(0, 527, 186, 9, -2, -2),
                    new Part(0, 538, 84, 25, 49, -15), 2, 2, -25, 40),
                new Look(new Part(0, 565, 186, 9, -2, -2),
                    new Part(0, 576, 186, 9, -2, -2),
                    new Part(0, 587, 84, 25, 49, -15), 2, 2, -25, 40)},
            // 7 Blade Horns
            {new Look(new Part(0, 614, 202, 11, -10, -3),
                    new Part(0, 627, 202, 11, -10, -3),
                    new Part(0, 640, 106, 15, 38, -11), 10, 10, -21, 34),
                new Look(new Part(0, 657, 202, 11, -10, -3),
                    new Part(0, 670, 202, 11, -10, -3),
                    new Part(0, 683, 106, 15, 38, -11), 10, 10, -21, 34)},
            // 8 Molten Crust
            {new Look(new Part(0, 700, 188, 12, -3, -4),
                    new Part(0, 714, 188, 12, -3, -4),
                    new Part(0, 728, 200, 20, -9, -5), 3, 3, -15, 37),
                new Look(new Part(0, 750, 188, 12, -3, -4),
                    new Part(0, 764, 188, 12, -3, -4),
                    new Part(0, 778, 200, 22, -9, -5), 3, 3, -15, 37)},
            // 9 Talon Grip
            {new Look(new Part(0, 802, 186, 11, -2, -3),
                    new Part(0, 815, 186, 11, -2, -3),
                    new Part(0, 828, 194, 27, -6, -17), 2, 2, -27, 42),
                new Look(new Part(0, 857, 186, 11, -2, -3),
                    new Part(0, 870, 186, 11, -2, -3),
                    new Part(0, 883, 194, 27, -6, -17), 2, 2, -27, 42)},
            // 10 Firestorm
            {new Look(new Part(0, 912, 186, 9, -2, -2),
                    new Part(0, 923, 186, 9, -2, -2),
                    new Part(0, 934, 232, 24, -25, -16), 2, 2, -19, 32),
                new Look(new Part(0, 960, 186, 9, -2, -2),
                    new Part(0, 971, 186, 9, -2, -2),
                    new Part(0, 982, 233, 24, -26, -16), 2, 2, -19, 32)},
    };

    /** The working name of design {@code design} (1 to {@link #DESIGNS}), or "" if there's no such design. */
    public static String name(int design)
    {
        return design >= 1 && design <= DESIGNS ? NAMES[design - 1] : "";
    }

    /** Design {@code design}'s adult or young look, or null if there's no such design. */
    public static Look look(int design, boolean young)
    {
        return design >= 1 && design <= DESIGNS ? LOOKS[design - 1][young ? 1 : 0] : null;
    }

    /**
     * The look a bar's key names (what follows {@code boss.dross.emberdactyl.bar.}): "adult" or "young" (the fight's,
     * design {@link #PICK}), or {@code design_<n>_adult} / {@code design_<n>_young} (the showcase's); null for anything
     * else.
     */
    public static Look look(String key)
    {
        if (key.equals("adult") || key.equals("young"))
        {
            return look(PICK, key.equals("young"));
        }
        if (key.startsWith("design_"))
        {
            int cut = key.lastIndexOf('_');
            String which = key.substring(cut + 1);
            if (cut > 7 && (which.equals("adult") || which.equals("young")))
            {
                try
                {
                    return look(Integer.parseInt(key.substring(7, cut)), which.equals("young"));
                }
                catch (NumberFormatException ignored)
                {
                    return null;
                }
            }
        }
        return null;
    }

    private EmberdactylBarArt() {}
}
