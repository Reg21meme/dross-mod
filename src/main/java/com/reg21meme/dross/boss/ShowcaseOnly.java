package com.reg21meme.dross.boss;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.fire.FireDesigns;
import com.reg21meme.dross.boss.rimefall.RimefallDesigns;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The models only the design galleries show (the user, November 7, 2026: "do not include the showcase galleries ... remove
 * it from the jar file, you can keep it in my system"): the fifteen fire servant candidates
 * ({@code /dross showcase boss_designs cinder_wastes}), the Rimefall candidates' statues ({@code ... rimefall}) and the
 * Emberdactyl egg's looks the fight never wears (E3A, E3C, E3D). They stay in the project, and the dev runs and the
 * tests have them; the released jar leaves them out (build.gradle's {@code jar} task, "Showcase-only models"). Without
 * them their galleries aren't offered, a gallery skips the designs it hasn't, and the renderers draw nothing for one
 * (a candidate made with the dev run, in a world opened with the jar).
 */
public final class ShowcaseOnly
{
    /** Which of the mod's files are in this build (looked up once each). */
    private static final Map<String, Boolean> PRESENT = new ConcurrentHashMap<>();

    private ShowcaseOnly() {}

    /** True if this file (a path from the mod's root, "assets/dross/...") is in this build of the mod. */
    public static boolean present(String path)
    {
        return PRESENT.computeIfAbsent(path, p ->
        {
            try
            {
                IModFileInfo info = ModList.get().getModFileById(Dross.MODID);
                if (info == null)
                {
                    return true;
                }
                Path file = info.getFile().findResource(p.split("/"));
                return Files.exists(file);
            }
            catch (RuntimeException e)
            {
                return true; // (can't tell: as if it were there, as in the dev runs)
            }
        });
    }

    /**
     * True if this fire design's model is in this build (a design that wears a mob's skeleton always is). A fire servant
     * candidate goes with its gallery: the jar keeps the Firestorm's first look for the Emberdactyl's fire tornado, but
     * not as a gallery of one.
     */
    public static boolean has(FireDesigns.Design design)
    {
        if (design.animated())
        {
            return true;
        }
        if (FireDesigns.SERVANTS.contains(design))
        {
            return fireServants();
        }
        return present("assets/dross/geo/entity/fire/" + design.id() + ".geo.json");
    }

    /** True if this Rimefall candidate's statue is in this build. */
    public static boolean has(RimefallDesigns.Design design)
    {
        return present("assets/dross/geo/entity/rimefall/" + design.id() + ".geo.json");
    }

    /** True if the fire servant gallery's models are in this build (its first, the Cinder Widow's: 1A). */
    public static boolean fireServants()
    {
        return present("assets/dross/geo/entity/fire/" + FireDesigns.SERVANTS.get(0).id() + ".geo.json");
    }

    /** True if the Rimefall gallery's statues are in this build. */
    public static boolean rimefallStatues()
    {
        return RimefallDesigns.ALL.stream().anyMatch(ShowcaseOnly::has);
    }
}
