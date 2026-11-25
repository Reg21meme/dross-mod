package com.reg21meme.dross.villager;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.quest.DrossAdvancements;
import com.reg21meme.dross.quest.oracle.OracleDialogue;
import com.reg21meme.dross.quest.oracle.SeekerDialogue;
import com.reg21meme.dross.quest.oracle.OracleMarker;
import com.reg21meme.dross.quest.oracle.OracleProgress;
import com.reg21meme.dross.quest.oracle.OracleQuest;
import com.reg21meme.dross.quest.oracle.OracleQuests;
import com.reg21meme.dross.quest.oracle.OracleState;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Headless tests (GameTests) for right-clicking the hub's survivors ({@link Survivor#mobInteract}), in
 * {@link SurvivorGameTest}'s arena with its stand-in players (they keep every chat line they get):
 * <ul>
 *   <li>{@link #rightClickSaysALine}: a right-click says a line in chat with its name as the speaker: the Blind Oracle's
 *       quest lines (a new player's first quest), the Ragged Seeker's lore lines, in order; the off hand and sneaking do
 *       nothing; no menu opens.</li>
 *   <li>{@link #oracleRightClicksMoveTheQuest}: a new player right-clicking the Blind Oracle hears his story line by
 *       line (only that player), gets "The Oracle's Plea" with its last line, and the next quest is waiting; clicking on
 *       starts it (the yellow marker) and then reminds; the Ragged Seeker's lines never touch the quest; once every
 *       quest is done the Oracle says his old lore lines.</li>
 * </ul>
 */
@GameTestHolder(SurvivorTalkGameTest.NAMESPACE)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class SurvivorTalkGameTest
{
    /**
     * The tests' GameTest namespace: the mod's, so the normal run has them. To run them alone, set it to a namespace of
     * their own (say "dross_survivor_talk") and run {@code .\gradlew.bat runGameTestServer
     * -PgameTestNamespaces=dross_survivor_talk -PgameTestWorld=<a new folder>}: the arena is then made under that
     * namespace too ({@link #onServerStarted}).
     */
    static final String NAMESPACE = Dross.MODID;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BATCH = "dross_survivor_talk";
    private static final int TIMEOUT = SurvivorGameTest.LOAD_BACKSTOP_TICKS + 40;
    private static final int STAND_Y = SurvivorGameTest.STAND_Y;
    private static final String ORACLE_NAME = "entity.dross.survivor.blind_oracle";
    private static final String SEEKER_NAME = "entity.dross.survivor.ragged_seeker";

    private SurvivorTalkGameTest() {}

    /** The survivors' arena, made under this class's own namespace when it isn't the mod's ({@link SurvivorGameTest} makes the mod's). */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        if (!ForgeGameTestHooks.isGametestEnabled() || NAMESPACE.equals(Dross.MODID))
        {
            return;
        }
        StructureTemplate template = event.getServer().getStructureManager()
                .getOrCreate(new ResourceLocation(NAMESPACE, SurvivorGameTest.ARENA));
        template.load(event.getServer().registryAccess().lookupOrThrow(Registries.BLOCK), SurvivorGameTest.arenaTag());
    }

    // ---------------------------------------------------------------------------------------------
    // Lines
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = SurvivorGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void rightClickSaysALine(GameTestHelper helper)
    {
        if (SurvivorGameTest.waitForArena(helper, SurvivorTalkGameTest::rightClickSaysALine))
        {
            return;
        }
        BoundingBox area = SurvivorGameTest.insideArea(helper);
        Survivor mentor = SurvivorGameTest.spawn(helper, SurvivorRole.MENTOR, new BlockPos(6, STAND_Y, 6), new BlockPos(6, STAND_Y, 6), area);
        Survivor student = SurvivorGameTest.spawn(helper, SurvivorRole.STUDENT, new BlockPos(7, STAND_Y, 6), new BlockPos(7, STAND_Y, 6), area);
        SurvivorGameTest.pair(mentor, student);
        SurvivorGameTest.TestPlayer player = SurvivorGameTest.TestPlayer.join(helper.getLevel(), "survivor-test-player");
        OracleQuest plea = OracleQuests.ORACLE_PLEA;
        helper.startSequence()
                .thenExecute(() -> {
                    try
                    {
                        List<String> problems = new ArrayList<>();
                        // A new player: the Oracle's lines are his story, the first quest's offer.
                        expectLine(problems, player, mentor, InteractionHand.MAIN_HAND, plea.key("offer.1"), ORACLE_NAME);
                        expectLine(problems, player, mentor, InteractionHand.MAIN_HAND, plea.key("offer.2"), ORACLE_NAME);
                        expectLine(problems, player, student, InteractionHand.MAIN_HAND, "dross.survivor.student.line.1", SEEKER_NAME);
                        expectLine(problems, player, mentor, InteractionHand.OFF_HAND, null, null);
                        player.setShiftKeyDown(true);
                        expectLine(problems, player, mentor, InteractionHand.MAIN_HAND, null, null);
                        player.setShiftKeyDown(false);
                        expectLine(problems, player, mentor, InteractionHand.MAIN_HAND, plea.key("offer.3"), ORACLE_NAME);
                        expect(problems, player.containerMenu == player.inventoryMenu, "a menu opened: " + player.containerMenu);
                        SurvivorGameTest.fail(problems);
                        LOGGER.info("[survivor test] Right-clicks: the Blind Oracle said his story's lines 1, 2 and 3 and the Ragged Seeker his line 1, "
                                + "in chat with their names; the off hand and sneaking said nothing; no menu opened");
                    }
                    finally
                    {
                        player.leave();
                        mentor.discard();
                        student.discard();
                    }
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // The Oracle's quests
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = SurvivorGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void oracleRightClicksMoveTheQuest(GameTestHelper helper)
    {
        if (SurvivorGameTest.waitForArena(helper, SurvivorTalkGameTest::oracleRightClicksMoveTheQuest))
        {
            return;
        }
        BoundingBox area = SurvivorGameTest.insideArea(helper);
        Survivor mentor = SurvivorGameTest.spawn(helper, SurvivorRole.MENTOR, new BlockPos(10, STAND_Y, 10), new BlockPos(10, STAND_Y, 10), area);
        Survivor student = SurvivorGameTest.spawn(helper, SurvivorRole.STUDENT, new BlockPos(11, STAND_Y, 10), new BlockPos(11, STAND_Y, 10), area);
        SurvivorGameTest.pair(mentor, student);
        SurvivorGameTest.TestPlayer player = SurvivorGameTest.TestPlayer.join(helper.getLevel(), "oracle-talk-test-player");
        SurvivorGameTest.TestPlayer bystander = SurvivorGameTest.TestPlayer.join(helper.getLevel(), "oracle-talk-bystander");
        OracleQuest plea = OracleQuests.ORACLE_PLEA;
        OracleQuest giants = OracleQuests.GIANT_SLAYER;
        helper.startSequence()
                .thenExecute(() -> {
                    try
                    {
                        List<String> problems = new ArrayList<>();
                        expectAt(problems, player, plea, OracleState.AVAILABLE, "a new player");
                        expect(problems, OracleProgress.marker(player) == OracleMarker.NEW, "a new player should see the red ? over him (NEW), not "
                                + OracleProgress.marker(player));

                        // The Ragged Seeker's lines never touch the quest.
                        for (int i = 1; i <= 3; i++)
                        {
                            expectSaid(problems, player, student, "dross.survivor.student.line." + i, SEEKER_NAME);
                        }
                        expectAt(problems, player, plea, OracleState.AVAILABLE, "after three of the Ragged Seeker's lines");

                        // His story, a line a click (the Seeker's clicks didn't move it on: it starts at its first line).
                        for (int i = 1; i <= plea.offerLines(); i++)
                        {
                            expect(problems, !DrossAdvancements.has(player, plea.advancement()), "\"The Oracle's Plea\" came before his story's end (line " + i + ")");
                            expectSaid(problems, player, mentor, plea.key("offer." + i), ORACLE_NAME);
                        }
                        expect(problems, DrossAdvancements.has(player, plea.advancement()), "his story's last line should grant dross:" + plea.advancement());
                        expectAt(problems, player, giants, OracleState.AVAILABLE, "after his story");
                        expect(problems, OracleProgress.marker(player) == OracleMarker.NEW, "the next quest waiting should show the red ?, not "
                                + OracleProgress.marker(player));
                        expect(problems, oracleLines(bystander).isEmpty(), "a player standing by heard " + oracleLines(bystander).size()
                                + " of the Oracle's lines meant for someone else");

                        // The next quest's offer starts it; then each click reminds.
                        for (int i = 1; i <= giants.offerLines(); i++)
                        {
                            expectSaid(problems, player, mentor, giants.key("offer." + i), ORACLE_NAME);
                        }
                        expectAt(problems, player, giants, OracleState.ACTIVE, "after the giants' offer");
                        expect(problems, OracleProgress.marker(player) == OracleMarker.ACTIVE, "a going quest should show the yellow ..., not "
                                + OracleProgress.marker(player));
                        expectSaid(problems, player, mentor, giants.key("remind"), ORACLE_NAME);
                        expectSaid(problems, player, student, "dross.survivor.student.line.4", SEEKER_NAME);
                        expectAt(problems, player, giants, OracleState.ACTIVE, "after a reminder and the Seeker's line");
                        expect(problems, OracleProgress.kills(player) == 0, "talking counted " + OracleProgress.kills(player) + " kills");

                        // A big boss's quest going: the Seeker's jokes about it, in turn; beaten, his line for it.
                        OracleQuest colossus = OracleQuests.SET_IN_STONE;
                        OracleProgress.setQuest(player, colossus);
                        for (int i = 1; i <= colossus.offerLines(); i++)
                        {
                            expectSaid(problems, player, mentor, colossus.key("offer." + i), ORACLE_NAME);
                            if (i == colossus.spellLine())
                            {
                                expect(problems, mentor.isCasting(), "his spell should go off on the Colossus's offer line " + i);
                                mentor.endCastForTests(); // (a player waits for it to end; the test doesn't)
                            }
                        }
                        expectAt(problems, player, colossus, OracleState.ACTIVE, "after the Colossus's offer");
                        for (int i = 1; i <= SeekerDialogue.BOSS_LINES + 1; i++)
                        {
                            int joke = (i - 1) % SeekerDialogue.BOSS_LINES + 1;
                            expectSaid(problems, player, student, SeekerDialogue.key(colossus.advancement(), String.valueOf(joke)), SEEKER_NAME);
                        }
                        expectAt(problems, player, colossus, OracleState.ACTIVE, "after the Seeker's jokes");

                        // Every quest done: the Oracle's lines for an old friend, in turn (never his first lore lines).
                        OracleProgress.setQuest(player, OracleQuests.DEFEAT_THE_DROSS);
                        OracleProgress.skip(player);
                        expect(problems, OracleProgress.isFinished(player), "skipping the last quest should finish them");
                        expect(problems, OracleProgress.marker(player) == OracleMarker.NONE, "a player with every quest done should see no marker");
                        for (int i = 1; i <= OracleDialogue.AFTER_LINES + 1; i++)
                        {
                            int after = (i - 1) % OracleDialogue.AFTER_LINES + 1;
                            expectSaid(problems, player, mentor, "dross.oracle.after." + after, ORACLE_NAME);
                        }
                        expect(problems, OracleProgress.isFinished(player), "his lines for an old friend changed the quest");
                        // The Seeker's lines for after, in turn.
                        for (int i = 1; i <= SeekerDialogue.AFTER_LINES + 1; i++)
                        {
                            int after = (i - 1) % SeekerDialogue.AFTER_LINES + 1;
                            expectSaid(problems, player, student, SeekerDialogue.key("after", String.valueOf(after)), SEEKER_NAME);
                        }
                        SurvivorGameTest.fail(problems);
                        LOGGER.info("[survivor test] Right-clicking the Blind Oracle: his story's {} lines granted \"The Oracle's Plea\", the giants' "
                                + "{} lines started that quest, then a reminder; the Ragged Seeker's lines left the quest alone, and on the Colossus's "
                                + "quest he joked about it; with every quest done the Oracle said his lines for an old friend",
                                plea.offerLines(), giants.offerLines());
                    }
                    finally
                    {
                        player.leave();
                        bystander.leave();
                        mentor.discard();
                        student.discard();
                    }
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /**
     * Right-clicks with that hand. With a key, it must answer (the action is used up) with exactly one new chat line,
     * "<name> line", whose name has {@code nameKey} and whose line has {@code lineKey}; without one, nothing must happen.
     */
    private static void expectLine(List<String> problems, SurvivorGameTest.TestPlayer player, Survivor survivor, InteractionHand hand,
                                   @Nullable String lineKey, @Nullable String nameKey)
    {
        int before = player.messages.size();
        InteractionResult result = survivor.interact(player, hand);
        List<Component> said = player.messages.subList(before, player.messages.size());
        String what = survivor.getRole().id() + " (" + hand + (player.isShiftKeyDown() ? ", sneaking" : "") + ")";
        if (lineKey == null)
        {
            expect(problems, !result.consumesAction() && said.isEmpty(), "right-clicking the " + what + " should do nothing, but it gave "
                    + result + " and said " + said.size() + " line(s)");
            return;
        }
        expect(problems, result.consumesAction(), "right-clicking the " + what + " gave " + result);
        if (said.size() != 1)
        {
            problems.add("right-clicking the " + what + " said " + said.size() + " lines, not 1");
            return;
        }
        checkChatLine(problems, said.get(0), what, lineKey, nameKey);
    }

    /**
     * Right-clicks with the main hand: it must answer with exactly one chat line ("<name> line") with these keys. Other
     * messages the click brings (an advancement's announcement) are allowed.
     */
    private static void expectSaid(List<String> problems, SurvivorGameTest.TestPlayer player, Survivor survivor, String lineKey, String nameKey)
    {
        int before = player.messages.size();
        InteractionResult result = survivor.interact(player, InteractionHand.MAIN_HAND);
        List<Component> lines = chatLines(player.messages.subList(before, player.messages.size()));
        String what = survivor.getRole().id();
        expect(problems, result.consumesAction(), "right-clicking the " + what + " gave " + result);
        if (lines.size() != 1)
        {
            problems.add("right-clicking the " + what + " (expecting " + lineKey + ") said " + lines.size() + " chat lines, not 1");
            return;
        }
        checkChatLine(problems, lines.get(0), what, lineKey, nameKey);
    }

    private static void checkChatLine(List<String> problems, Component message, String what, String lineKey, String nameKey)
    {
        if (!(message.getContents() instanceof TranslatableContents chat) || !"chat.type.text".equals(chat.getKey()) || chat.getArgs().length != 2)
        {
            problems.add("the " + what + "'s line isn't a chat line with a speaker: " + message);
            return;
        }
        Object name = chat.getArgs()[0];
        Object line = chat.getArgs()[1];
        expect(problems, name instanceof Component speaker && speaker.getContents() instanceof TranslatableContents key && nameKey.equals(key.getKey()),
                "the " + what + "'s speaker is " + name + ", not " + nameKey);
        expect(problems, line instanceof Component text && text.getContents() instanceof TranslatableContents key && lineKey.equals(key.getKey()),
                "the " + what + " said " + line + ", not " + lineKey);
    }

    /** The messages that are chat lines with a speaker ("<name> line"). */
    private static List<Component> chatLines(List<Component> messages)
    {
        List<Component> lines = new ArrayList<>();
        for (Component message : messages)
        {
            if (message.getContents() instanceof TranslatableContents chat && "chat.type.text".equals(chat.getKey()))
            {
                lines.add(message);
            }
        }
        return lines;
    }

    /** The Blind Oracle's chat lines this player got. */
    private static List<Component> oracleLines(SurvivorGameTest.TestPlayer player)
    {
        List<Component> lines = new ArrayList<>();
        for (Component message : chatLines(player.messages))
        {
            if (((TranslatableContents) message.getContents()).getArgs()[0] instanceof Component speaker
                    && speaker.getContents() instanceof TranslatableContents key && ORACLE_NAME.equals(key.getKey()))
            {
                lines.add(message);
            }
        }
        return lines;
    }

    private static void expectAt(List<String> problems, SurvivorGameTest.TestPlayer player, OracleQuest quest, OracleState state, String when)
    {
        OracleQuest at = OracleProgress.current(player);
        OracleState now = OracleProgress.state(player);
        expect(problems, at == quest && now == state, when + ": the quest should be " + quest.id() + " " + state + ", but it's " + at.id() + " " + now);
    }

    private static void expect(List<String> problems, boolean ok, String problem)
    {
        SurvivorGameTest.expect(problems, ok, problem);
    }
}
