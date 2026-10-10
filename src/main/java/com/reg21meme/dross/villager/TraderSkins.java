package com.reg21meme.dross.villager;

import com.reg21meme.dross.Dross;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * The candidate skins for the Dross trader, numbered 1 to 10 (a design showcase: {@code /dross showcase skins}
 * spawns one frozen trader per skin, see {@link DrossTrader#spawnShowcase}).
 * <p>
 * A trader's skin number is saved on him ({@link DrossTrader#getSkin()}). Number {@link #DEFAULT} (0), which every
 * trader has unless something sets another number, means the quest trader's own look: skin {@link #QUEST_TRADER},
 * The Archivist (the user's pick). So the real trader and spawn-egg traders, in old worlds and new, all wear it
 * ({@link #lookFor}). Showcase traders carry the number of the skin they show.
 * <p>
 * Kept for later (the user's picks, not used yet): skin 1, Hooded Watcher, for a possible enemy in the Dross, and
 * skins 3, Ragged Seeker, and 8, Blind Oracle, for NPCs in the Dross.
 * <p>
 * Safe on the server: only names and texture paths live here, no client classes. The textures are in
 * {@code assets/dross/textures/entity/trader/} and are drawn by {@code tools/skins/draw_skins.py}.
 */
public final class TraderSkins
{
    /** The skin number every trader has unless something sets another one: the quest trader's look. */
    public static final int DEFAULT = 0;
    /** The quest trader's look: skin 2, The Archivist. */
    public static final int QUEST_TRADER = 2;

    /**
     * One candidate skin.
     *
     * @param number  1 to 10, its number in the showcase
     * @param name    a short name that fits one line of a sign (15 characters at most)
     * @param mood    a word or two about the character (15 characters at most)
     * @param texture the 64x64 villager-layout texture
     */
    public record TraderSkin(int number, String name, String mood, ResourceLocation texture) {}

    /** Every candidate skin, in number order. */
    public static final List<TraderSkin> ALL = List.of(
            skin(1, "hooded_watcher", "Hooded Watcher", "mysterious"),
            skin(2, "archivist", "The Archivist", "scholarly"),
            skin(3, "ragged_seeker", "Ragged Seeker", "ragged"),
            skin(4, "rift_regent", "Rift Regent", "regal"),
            skin(5, "shard_hermit", "Shard Hermit", "crystal-touched"),
            skin(6, "wayfarer", "Wayfarer", "road-worn"),
            skin(7, "stargazer", "Stargazer", "starstruck"),
            skin(8, "blind_oracle", "Blind Oracle", "prophetic"),
            skin(9, "rift_warden", "Rift Warden", "vigilant"),
            skin(10, "alchemist", "Alchemist", "volatile"));

    /**
     * The skin a trader with this skin number wears: that candidate (1 to 10), or for anything else (0, the
     * default) the quest trader's look, {@link #QUEST_TRADER}. Never null.
     */
    public static TraderSkin lookFor(int number)
    {
        TraderSkin skin = byNumber(number);
        return skin != null ? skin : byNumber(QUEST_TRADER);
    }

    /** The skin with this number, or null if there is none (0, the default, has no entry here). */
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
    private static TraderSkin skin(int number, String id, String name, String mood)
    {
        String path = String.format(Locale.ROOT, "textures/entity/trader/skin_%02d_%s.png", number, id);
        return new TraderSkin(number, name, mood, new ResourceLocation(Dross.MODID, path));
    }

    private TraderSkins() {}
}
