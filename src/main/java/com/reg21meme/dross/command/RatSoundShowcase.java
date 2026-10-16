package com.reg21meme.dross.command;

import com.mojang.brigadier.context.CommandContext;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.ratking.RatKingSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/**
 * {@code /dross showcase boss_sounds ratking}: lists every Rat King and rat sound in chat, under headings (his voice,
 * the rage with its three train whistles, his moves, the attack sounds for his fight, the Plague Rat, the Plague
 * Ravager), each with a [play] button that plays it to you ({@code /playsound}). It's how the user listens to them
 * and picks the attack sounds and the rage's whistle. The list is the boss area's {@code RatKingSounds}, made by
 * {@code tools/ratking/sounds.py} along with the sounds (all original). Needs cheats (the buttons run
 * {@code /playsound}).
 */
public final class RatSoundShowcase
{
    /** What a [play] button runs, before the sound's event id. */
    private static final String PLAY = "/playsound dross:";
    private static final String PLAY_AT = " master @s ~ ~ ~ 1 1";

    static int list(CommandContext<CommandSourceStack> context)
    {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("The Rat King's and his rats' sounds, all original. Click [play] to hear "
                + "one (open chat and scroll up for the rest); a sound with several versions plays one at random."), false);
        String group = null;
        for (RatKingSounds.Sound sound : RatKingSounds.ALL)
        {
            if (!sound.group().equals(group))
            {
                group = sound.group();
                Component heading = Component.literal(group).withStyle(ChatFormatting.BOLD);
                source.sendSuccess(() -> heading, false);
            }
            source.sendSuccess(() -> line(sound), false);
        }
        int count = RatKingSounds.ALL.size();
        source.sendSuccess(() -> Component.literal(count + " sounds. His rage plays train whistle " + RatKingSounds.RAGE_WHISTLE
                + "."), false);
        return count;
    }

    private static Component line(RatKingSounds.Sound sound)
    {
        String command = PLAY + sound.event() + PLAY_AT;
        MutableComponent line = Component.literal("[play]").withStyle(style -> style
                .withColor(DrossColors.RAT_SHOWCASE_BUTTON)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("dross:" + sound.event() + (sound.versions() > 1 ? " (" + sound.versions()
                                + " versions)" : "")))));
        return line.append(Component.literal(" " + sound.label() + (sound.used() ? "" : " (not used yet)")));
    }

    private RatSoundShowcase() {}
}
