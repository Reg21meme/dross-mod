package com.reg21meme.dross.boss.huntsman;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.client.HuntsmanBarStyles;
import com.reg21meme.dross.boss.golem.ColossusGameTest;
import java.io.InputStream;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Headless checks of the Huntsman's boss bar showcase ({@code /dross showcase bossbars huntsman}): the ten designs are
 * all there, each look's fill exactly the 182 pixels of Minecraft's bar, its texture in the mod; the showcase shows a
 * player the fight's three bars (the Wild Hunt, the Huntsman, the Alpha Moose), or the two angry looks, named by their
 * keys with the design's number, and replaces them when another design is shown.
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class HuntsmanBarShowcaseGameTest
{
    private HuntsmanBarShowcaseGameTest() {}

    @GameTest(template = ColossusGameTest.ARENA, batch = "dross_huntsman_bars", timeoutTicks = ColossusGameTest.ARENA_WAIT_TICKS + 200)
    public static void bossBarShowcase(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, HuntsmanBarShowcaseGameTest::bossBarShowcase))
        {
            return;
        }
        if (HuntsmanBarStyles.ALL.size() != 10)
        {
            throw new GameTestAssertException("there should be 10 Huntsman boss bar designs, but there are " + HuntsmanBarStyles.ALL.size());
        }
        for (HuntsmanBarStyles.Style style : HuntsmanBarStyles.ALL)
        {
            for (String look : List.of("wild_hunt", "huntsman", "huntsman_angry", "alpha_moose", "alpha_moose_angry"))
            {
                if (style.look(look).fillWidth() != 182)
                {
                    throw new GameTestAssertException("design " + style.number() + "'s " + look + " fill is " + style.look(look).fillWidth()
                            + " pixels wide, not Minecraft's 182");
                }
            }
            if (style.nameY() > -9 || style.height() < 19)
            {
                throw new GameTestAssertException("design " + style.number() + "'s name sits at " + style.nameY() + " and it takes "
                        + style.height() + " pixels");
            }
            String path = "/assets/" + style.texture().getNamespace() + "/" + style.texture().getPath();
            try (InputStream in = HuntsmanBarShowcaseGameTest.class.getResourceAsStream(path))
            {
                if (in == null)
                {
                    throw new GameTestAssertException("design " + style.number() + "'s texture " + path + " isn't in the mod");
                }
            }
            catch (java.io.IOException e)
            {
                throw new GameTestAssertException("couldn't read " + path + ": " + e);
            }
        }
        ServerPlayer player = ColossusGameTest.joinPlayer(helper, "bar_watcher", ColossusGameTest.MIDDLE, 0.0F);
        try
        {
            var source = player.createCommandSourceStack().withSuppressedOutput();
            check(HuntsmanBarShowcase.show(source, 4, false), player, 4, List.of("wild_hunt", "huntsman", "alpha_moose"));
            List<ServerBossEvent> first = HuntsmanBarShowcase.shownTo(player);
            check(HuntsmanBarShowcase.show(source, 9, true), player, 9, List.of("huntsman_angry", "alpha_moose_angry"));
            for (ServerBossEvent old : first)
            {
                if (old.getPlayers().contains(player))
                {
                    throw new GameTestAssertException("showing another design should take the old bars away");
                }
            }
            if (HuntsmanBarShowcase.show(source, 11, false) != 0)
            {
                throw new GameTestAssertException("design 11 doesn't exist, but it was shown");
            }
        }
        finally
        {
            HuntsmanBarShowcase.shownTo(player).forEach(ServerBossEvent::removeAllPlayers);
            ColossusGameTest.leaveAll(List.of(player));
        }
        helper.succeed();
    }

    private static void check(int result, ServerPlayer player, int design, List<String> looks)
    {
        List<ServerBossEvent> bars = HuntsmanBarShowcase.shownTo(player);
        if (result != 1 || bars.size() != looks.size())
        {
            throw new GameTestAssertException("design " + design + " should show " + looks.size() + " bars, but shows " + bars.size());
        }
        for (int i = 0; i < looks.size(); i++)
        {
            ServerBossEvent bar = bars.get(i);
            if (!(bar.getName().getContents() instanceof TranslatableContents contents)
                    || !contents.getKey().equals(HuntsmanBarShowcase.KEY_PREFIX + looks.get(i))
                    || contents.getArgs().length != 1 || !String.valueOf(contents.getArgs()[0]).equals(String.valueOf(design))
                    || !bar.getPlayers().contains(player))
            {
                throw new GameTestAssertException("bar " + (i + 1) + " of design " + design + " should be " + looks.get(i) + ", shown to the player");
            }
        }
    }
}
