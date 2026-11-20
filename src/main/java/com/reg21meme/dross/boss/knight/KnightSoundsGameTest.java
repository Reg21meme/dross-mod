package com.reg21meme.dross.boss.knight;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.golem.ColossusGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Headless test (GameTest) of the elemental knights' own sounds ({@code tools/knights/sounds.py}): every event in the
 * generated {@link KnightSounds} is registered, listed in {@code sounds.json} (each version's event too) and its files
 * are in the mod; every knight has its idle, grunt, hurt, death and step sounds, and every sound the fight plays
 * ({@link ElementalKnight#knightSound}) is one of them.
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class KnightSoundsGameTest
{
    /** The sounds {@link ElementalKnight}, {@link KnightAnchor} and {@link KnightFireball} play, by knight. */
    private static final String[][] PLAYED = {
            {"thawed_axe", "swing", "smash_windup", "smash", "spike", "big_spike"},
            {"thawed_spear", "thrust", "guard", "poke", "block"},
            {"blight_warden", "cast", "rain_call", "rain"},
            {"sporebound", "reap", "spore_windup", "spore_burst"},
            {"cinder", "swing", "slam_windup", "slam"},
            {"flamehand", "fireball", "charge", "great_fireball", "blast", "fizzle"},
            {"wreck", "throw_windup", "anchor_throw", "anchor_hook", "anchor_haul", "anchor_clang", "anchor_catch",
                    "slam_windup", "anchor_slam"},
            {"stormblade", "slash", "dash", "dash_crackle", "trail"},
            {"geode", "pick", "burst_windup", "crystal_burst", "crystals"},
            {"rockfall", "heave", "throw", "rockfall_windup", "rockfall"},
    };
    private static final String[] EVERY_KNIGHT = {"ambient", "effort", "hurt", "death", "step"};

    private KnightSoundsGameTest() {}

    @GameTest(template = ColossusGameTest.ARENA, batch = "dross_knight_sounds", timeoutTicks = 200)
    public static void everyKnightSoundIsInTheMod(GameTestHelper helper)
    {
        JsonObject sounds;
        try (InputStream in = KnightSoundsGameTest.class.getResourceAsStream("/assets/dross/sounds.json"))
        {
            if (in == null)
            {
                throw new GameTestAssertException("assets/dross/sounds.json isn't in the mod");
            }
            sounds = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        catch (java.io.IOException e)
        {
            throw new GameTestAssertException("couldn't read sounds.json: " + e);
        }
        List<String> problems = new ArrayList<>();
        for (KnightSounds.Sound sound : KnightSounds.ALL)
        {
            if (!ForgeRegistries.SOUND_EVENTS.containsKey(new ResourceLocation(Dross.MODID, sound.event())))
            {
                problems.add(sound.event() + " isn't registered");
            }
            if (!sounds.has(sound.event()))
            {
                problems.add(sound.event() + " isn't in sounds.json");
            }
            for (String play : sound.plays())
            {
                if (!sounds.has(play))
                {
                    problems.add(play + " isn't in sounds.json");
                }
            }
            for (String file : sound.files())
            {
                try (InputStream in = KnightSoundsGameTest.class.getResourceAsStream("/assets/dross/" + file))
                {
                    if (in == null || in.read() < 0)
                    {
                        problems.add(file + " isn't in the mod");
                    }
                }
                catch (java.io.IOException e)
                {
                    problems.add(file + " can't be read: " + e);
                }
            }
        }
        for (KnightKind kind : KnightKind.values())
        {
            for (String name : EVERY_KNIGHT)
            {
                needs(problems, kind, name);
            }
        }
        for (String[] row : PLAYED)
        {
            KnightKind kind = KnightKind.valueOf(row[0].toUpperCase(java.util.Locale.ROOT));
            for (int i = 1; i < row.length; i++)
            {
                needs(problems, kind, row[i]);
            }
        }
        if (!problems.isEmpty())
        {
            throw new GameTestAssertException(problems.size() + " problems with the knights' sounds, the first: "
                    + String.join("; ", problems.subList(0, Math.min(5, problems.size()))));
        }
        helper.succeed();
    }

    private static void needs(List<String> problems, KnightKind kind, String name)
    {
        String event = KnightSounds.event(kind, name);
        if (KnightSounds.ALL.stream().noneMatch(s -> s.event().equals(event)))
        {
            problems.add(event + " is played but isn't in KnightSounds");
        }
    }
}
