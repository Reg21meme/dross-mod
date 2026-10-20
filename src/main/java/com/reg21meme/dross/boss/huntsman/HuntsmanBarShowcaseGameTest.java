package com.reg21meme.dross.boss.huntsman;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.client.HuntsmanBarArt;
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
 * Headless checks of the Huntsman's boss bars ({@code /dross showcase bossbars huntsman}): all five looks (the user's
 * picks: the Wild Hunt, the Huntsman, the Alpha Moose and the last two angry) are there, each fill exactly the 182 pixels
 * of Minecraft's bar, their texture in the mod; the showcase shows a player the fight's three bars, or the two angry
 * looks, named by their keys, and replaces them when shown again.
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
        for (String key : HuntsmanBarShowcase.LOOKS)
        {
            HuntsmanBarArt.Look look = HuntsmanBarArt.look(key);
            if (look == null)
            {
                throw new GameTestAssertException("the Huntsman's bars have no " + key + " look");
            }
            if (look.fillWidth() != 182)
            {
                throw new GameTestAssertException("the " + key + " bar's fill is " + look.fillWidth() + " pixels wide, not Minecraft's 182");
            }
            if (look.nameY() > -9 || look.height() < 19)
            {
                throw new GameTestAssertException("the " + key + " bar's name sits at " + look.nameY() + " and it takes "
                        + look.height() + " pixels");
            }
        }
        String path = "/assets/" + HuntsmanBarArt.TEXTURE.getNamespace() + "/" + HuntsmanBarArt.TEXTURE.getPath();
        try (InputStream in = HuntsmanBarShowcaseGameTest.class.getResourceAsStream(path))
        {
            if (in == null)
            {
                throw new GameTestAssertException("the Huntsman's bar texture " + path + " isn't in the mod");
            }
        }
        catch (java.io.IOException e)
        {
            throw new GameTestAssertException("couldn't read " + path + ": " + e);
        }
        ServerPlayer player = ColossusGameTest.joinPlayer(helper, "bar_watcher", ColossusGameTest.MIDDLE, 0.0F);
        try
        {
            var source = player.createCommandSourceStack().withSuppressedOutput();
            check(HuntsmanBarShowcase.show(source, false), player, List.of("wild_hunt", "huntsman", "alpha_moose"));
            List<ServerBossEvent> first = HuntsmanBarShowcase.shownTo(player);
            check(HuntsmanBarShowcase.show(source, true), player, List.of("huntsman_angry", "alpha_moose_angry"));
            for (ServerBossEvent old : first)
            {
                if (old.getPlayers().contains(player))
                {
                    throw new GameTestAssertException("showing the bars again should take the old ones away");
                }
            }
        }
        finally
        {
            HuntsmanBarShowcase.shownTo(player).forEach(ServerBossEvent::removeAllPlayers);
            ColossusGameTest.leaveAll(List.of(player));
        }
        helper.succeed();
    }

    private static void check(int result, ServerPlayer player, List<String> looks)
    {
        List<ServerBossEvent> bars = HuntsmanBarShowcase.shownTo(player);
        if (result != 1 || bars.size() != looks.size())
        {
            throw new GameTestAssertException("the showcase should show " + looks.size() + " bars, but shows " + bars.size());
        }
        for (int i = 0; i < looks.size(); i++)
        {
            ServerBossEvent bar = bars.get(i);
            if (!(bar.getName().getContents() instanceof TranslatableContents contents)
                    || !contents.getKey().equals(HuntsmanBarShowcase.KEY_PREFIX + looks.get(i)) || !bar.getPlayers().contains(player))
            {
                throw new GameTestAssertException("bar " + (i + 1) + " should be the " + looks.get(i) + " bar, shown to the player");
            }
        }
    }
}
