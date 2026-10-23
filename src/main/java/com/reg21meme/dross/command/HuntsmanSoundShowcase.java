package com.reg21meme.dross.command;

import com.mojang.brigadier.context.CommandContext;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.huntsman.HuntsmanSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/**
 * {@code /dross showcase boss_sounds huntsman}: lists every sound option of the Huntsman and the Alpha Moose in chat,
 * one line per sound, with a numbered button for each of its versions ({@code /playsound} of that version on its own),
 * so the user can pick the ones they like. The list is the boss area's {@code HuntsmanSounds}, made by
 * {@code tools/huntsman/sounds.py} along with the sounds (all original). Needs cheats.
 */
public final class HuntsmanSoundShowcase
{
    private static final String PLAY = "/playsound dross:";
    private static final String PLAY_AT = " master @s ~ ~ ~ 1 1";

    static int list(CommandContext<CommandSourceStack> context)
    {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("The Huntsman's and the Alpha Moose's sounds, all original. Click a "
                + "number to hear that version (hover for what it is); a * is your pick, the one the game plays. The "
                + "same on the page boss-previews/rimefall/huntsman/sounds/index.html."), false);
        String group = null;
        int count = 0;
        for (HuntsmanSounds.Sound sound : HuntsmanSounds.ALL)
        {
            if (!sound.group().equals(group))
            {
                group = sound.group();
                Component heading = Component.literal(group).withStyle(ChatFormatting.BOLD);
                source.sendSuccess(() -> heading, false);
            }
            source.sendSuccess(() -> line(sound), false);
            count += sound.picks().size();
        }
        int versions = count;
        source.sendSuccess(() -> Component.literal(HuntsmanSounds.ALL.size() + " sounds, " + versions + " versions."), false);
        return HuntsmanSounds.ALL.size();
    }

    private static Component line(HuntsmanSounds.Sound sound)
    {
        MutableComponent line = Component.literal(sound.label() + ": ");
        for (int i = 0; i < sound.picks().size(); i++)
        {
            String command = PLAY + sound.picks().get(i) + PLAY_AT;
            boolean picked = sound.picked().contains(i + 1);
            String what = (i + 1) + ". " + sound.versions().get(i) + (picked ? " (your pick: the game plays it)" : "");
            line.append(Component.literal("[" + (i + 1) + (picked ? "*" : "") + "]").withStyle(style -> style
                    .withColor(DrossColors.HUNTSMAN_COMMAND_BUTTON)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(what)))));
            line.append(Component.literal(" "));
        }
        if (sound.picked().isEmpty())
        {
            line.append(Component.literal("(pick one)").withStyle(ChatFormatting.GRAY));
        }
        return line;
    }

    private HuntsmanSoundShowcase() {}
}
