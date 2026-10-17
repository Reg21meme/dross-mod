package com.reg21meme.dross.villager;

import com.reg21meme.dross.Dross;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * The skins drawn for the Dross trader's villager model. Each keeps the number it had among the ten candidates the
 * user picked from, so the texture names stay the same:
 * <ul>
 *   <li>2, The Archivist: the quest trader ({@link #QUEST_TRADER}, the user's pick). Every trader wears it, the real
 *       one and spawn-egg traders alike.</li>
 *   <li>8, Blind Oracle, and 3, Ragged Seeker: the hub's two survivors ({@link Survivor}: the mentor and his
 *       student).</li>
 *   <li>1, Hooded Watcher: kept for later (the user's pick, not used yet), for a possible enemy in the Dross.</li>
 * </ul>
 * Safe on the server: only names and texture paths live here, no client classes. The textures are in
 * {@code assets/dross/textures/entity/trader/} and are drawn by {@code tools/skins/draw_skins.py}.
 */
public final class TraderSkins
{
    /** The quest trader's look: skin 2, The Archivist. */
    public static final int QUEST_TRADER = 2;
    /** The hub's mentor survivor: skin 8, Blind Oracle. */
    public static final int BLIND_ORACLE = 8;
    /** The hub's student survivor: skin 3, Ragged Seeker. */
    public static final int RAGGED_SEEKER = 3;

    /**
     * One skin.
     *
     * @param number  its number (1, 2, 3 or 8; the numbers from the candidate list)
     * @param name    a short name
     * @param texture the 64x64 villager-layout texture
     */
    public record TraderSkin(int number, String name, ResourceLocation texture) {}

    /** Every skin, in number order. */
    public static final List<TraderSkin> ALL = List.of(
            skin(1, "hooded_watcher", "Hooded Watcher"),
            skin(2, "archivist", "The Archivist"),
            skin(3, "ragged_seeker", "Ragged Seeker"),
            skin(8, "blind_oracle", "Blind Oracle"));

    /** The skin with this number, or the quest trader's look ({@link #QUEST_TRADER}) if there is none. Never null. */
    public static TraderSkin lookFor(int number)
    {
        TraderSkin skin = byNumber(number);
        return skin != null ? skin : byNumber(QUEST_TRADER);
    }

    /** The skin with this number, or null if there is none. */
    @Nullable
    public static TraderSkin byNumber(int number)
    {
        for (TraderSkin skin : ALL)
        {
            if (skin.number() == number)
            {
                return skin;
            }
        }
        return null;
    }

    /** Texture file names follow the number and id: {@code skin_01_hooded_watcher.png}. */
    private static TraderSkin skin(int number, String id, String name)
    {
        String path = String.format(Locale.ROOT, "textures/entity/trader/skin_%02d_%s.png", number, id);
        return new TraderSkin(number, name, new ResourceLocation(Dross.MODID, path));
    }

    private TraderSkins() {}
}
