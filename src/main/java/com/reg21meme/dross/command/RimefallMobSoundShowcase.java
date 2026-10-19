package com.reg21meme.dross.command;

import com.mojang.brigadier.context.CommandContext;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.rimefall.RimefallMobSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/**
 * {@code /dross showcase mob_sounds rimefall}: lists every sound of Rimefall's animals in chat (the Rimehorn's, the
 * moose's, the Pond Skater's), each with a [play] button ({@code /playsound}; a sound with several versions plays one
 * at random). The list is the boss area's {@code RimefallMobSounds}, made by {@code tools/rimefall/sounds.py} along
 * with the sounds (all original). Needs cheats.
 */
public final class RimefallMobSoundShowcase
{
    private static final String PLAY = "/playsound dross:";
    private static final String PLAY_AT = " master @s ~ ~ ~ 1 1";

    static int list(CommandContext<CommandSourceStack> context)
    {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("Rimefall's animals' sounds, all original. Click [play] to hear one; "
                + "a sound with several versions plays one at random (every version is on the page "
                + "boss-previews/rimefall/mobs/sounds/index.html)."), false);
        String group = null;
        for (RimefallMobSounds.Sound sound : RimefallMobSounds.ALL)
        {
            if (!sound.group().equals(group))
            {
                group = sound.group();
                Component heading = Component.literal(group).withStyle(ChatFormatting.BOLD);
                source.sendSuccess(() -> heading, false);
            }
            source.sendSuccess(() -> line(sound), false);
        }
        int count = RimefallMobSounds.ALL.size();
        source.sendSuccess(() -> Component.literal(count + " sounds."), false);
        return count;
    }

    private static Component line(RimefallMobSounds.Sound sound)
    {
        String command = PLAY + sound.event() + PLAY_AT;
        MutableComponent line = Component.literal("[play]").withStyle(style -> style
                .withColor(DrossColors.RIMEFALL_SHOWCASE_BUTTON)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("dross:" + sound.event() + (sound.versions() > 1 ? " (" + sound.versions()
                                + " versions)" : "")))));
        return line.append(Component.literal(" " + sound.label()));
    }

    private RimefallMobSoundShowcase() {}
}
