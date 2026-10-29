package com.reg21meme.dross.boss.fire;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.client.EmberdactylBarArt;
import com.reg21meme.dross.boss.golem.ColossusGameTest;
import java.io.IOException;
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
 * Headless checks of the Emberdactyl's boss bar ({@code /dross showcase bossbars emberdactyl}): both looks of the user's
 * pick (the adult's and the young's) are there, each fill exactly the 182 pixels of Minecraft's bar, their name above it,
 * their texture in the mod; the showcase shows a player the two bars, named by the fight's keys, and replaces them when
 * shown again.
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class EmberdactylBarShowcaseGameTest
{
    private EmberdactylBarShowcaseGameTest() {}

    @GameTest(template = ColossusGameTest.ARENA, batch = "dross_ember_bars", timeoutTicks = ColossusGameTest.ARENA_WAIT_TICKS + 200)
    public static void emberdactylBarShowcase(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, EmberdactylBarShowcaseGameTest::emberdactylBarShowcase))
        {
            return;
        }
        for (String key : EmberdactylBarShowcase.LOOKS)
        {
            EmberdactylBarArt.Look look = EmberdactylBarArt.look(key);
            if (look == null || look.fillWidth() != 182 || look.nameY() > -9 || look.height() < 19)
            {
                throw new GameTestAssertException("the Emberdactyl's " + key + " bar is missing or the wrong size: " + look);
            }
        }
        String path = "/assets/" + EmberdactylBarArt.TEXTURE.getNamespace() + "/" + EmberdactylBarArt.TEXTURE.getPath();
        try (InputStream in = EmberdactylBarShowcaseGameTest.class.getResourceAsStream(path))
        {
            if (in == null)
            {
                throw new GameTestAssertException("the Emberdactyl's bar texture " + path + " isn't in the mod");
            }
        }
        catch (IOException e)
        {
            throw new GameTestAssertException("couldn't read " + path + ": " + e);
        }
        ServerPlayer player = ColossusGameTest.joinPlayer(helper, "ember_bar_watcher", ColossusGameTest.MIDDLE, 0.0F);
        try
        {
            var source = player.createCommandSourceStack().withSuppressedOutput();
            check(EmberdactylBarShowcase.show(source), player);
            List<ServerBossEvent> first = EmberdactylBarShowcase.shownTo(player);
            check(EmberdactylBarShowcase.show(source), player);
            if (first.stream().anyMatch(old -> old.getPlayers().contains(player)))
            {
                throw new GameTestAssertException("showing the bars again should take the old ones away");
            }
        }
        finally
        {
            EmberdactylBarShowcase.shownTo(player).forEach(ServerBossEvent::removeAllPlayers);
            ColossusGameTest.leaveAll(List.of(player));
        }
        helper.succeed();
    }

    private static void check(int result, ServerPlayer player)
    {
        List<ServerBossEvent> bars = EmberdactylBarShowcase.shownTo(player);
        List<String> looks = EmberdactylBarShowcase.LOOKS;
        if (result != 1 || bars.size() != looks.size())
        {
            throw new GameTestAssertException("the showcase should show " + looks.size() + " bars, but shows " + bars.size());
        }
        for (int i = 0; i < looks.size(); i++)
        {
            ServerBossEvent bar = bars.get(i);
            if (!(bar.getName().getContents() instanceof TranslatableContents contents)
                    || !contents.getKey().equals(EmberdactylBarShowcase.KEY_PREFIX + looks.get(i)) || !bar.getPlayers().contains(player))
            {
                throw new GameTestAssertException("bar " + (i + 1) + " should be the " + looks.get(i) + " bar, shown to the player");
            }
        }
    }
}
