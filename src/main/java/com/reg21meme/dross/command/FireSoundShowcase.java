package com.reg21meme.dross.command;

import com.mojang.brigadier.context.CommandContext;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.fire.FireSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * {@code /dross showcase boss_sounds emberdactyl} lists every sound of the Emberdactyl and its young in chat, and
 * {@code /dross showcase mob_sounds cinder_wastes} every sound of the Cinder Wastes' mobs (the Pallbearer, the Cinder
 * Hermit), each with a [play] button ({@code /playsound}), or a numbered button per version when it has several (each
 * version is its own event, {@code <event>.v<n>}; hover over one for what it is). The list
 * is the boss area's {@code FireSounds}, made by {@code tools/fire/sounds.py} along with the sounds (all original).
 * Needs cheats.
 */
public final class FireSoundShowcase
{
    private static final String PLAY = "/playsound dross:";
    private static final String PLAY_AT = " master @s ~ ~ ~ 1 1";
    private static final List<String> EMBERDACTYL = List.of("Emberdactyl", "Young Reborn");
    private static final List<String> CINDER_MOBS = List.of("Pallbearer", "Cinder Hermit");

    static int emberdactyl(CommandContext<CommandSourceStack> context)
    {
        return list(context.getSource(), EMBERDACTYL, "The Emberdactyl's sounds, all original (its screech made in the "
                + "spirit of a giant film pterosaur's cry).");
    }

    static int cinderMobs(CommandContext<CommandSourceStack> context)
    {
        return list(context.getSource(), CINDER_MOBS, "The Cinder Wastes' mobs' sounds, all original.");
    }

    private static int list(CommandSourceStack source, List<String> groups, String intro)
    {
        source.sendSuccess(() -> Component.literal(intro + " Click [play], or a version's number, to hear it (the "
                + "showcase models play the versions in turn, or the one marked *; every version is also on the page "
                + "boss-previews/cinder_wastes/sounds/index.html)."), false);
        String group = null;
        int count = 0;
        for (FireSounds.Sound sound : FireSounds.ALL)
        {
            if (!groups.contains(sound.group()))
            {
                continue;
            }
            if (!sound.group().equals(group))
            {
                group = sound.group();
                Component heading = Component.literal(group).withStyle(ChatFormatting.BOLD);
                source.sendSuccess(() -> heading, false);
            }
            source.sendSuccess(() -> line(sound), false);
            count++;
        }
        int n = count;
        source.sendSuccess(() -> Component.literal(n + " sounds."), false);
        return count;
    }

    private static Component line(FireSounds.Sound sound)
    {
        MutableComponent line = Component.empty();
        if (sound.versions() < 2)
        {
            line.append(button("[play]", sound.event(), ""));
        }
        for (int n = 1; sound.versions() > 1 && n <= sound.versions(); n++)
        {
            String about = n <= sound.about().size() ? sound.about().get(n - 1) : "";
            line.append(button("[" + n + (sound.pick() == n ? "*" : "") + "]", sound.version(n), about)).append(" ");
        }
        return line.append(Component.literal(" " + sound.label()));
    }

    /** A button that plays {@code event}, its hover the event's id and a few words on it. */
    private static Component button(String text, String event, String about)
    {
        String command = PLAY + event + PLAY_AT;
        return Component.literal(text).withStyle(style -> style
                .withColor(DrossColors.FIRE_SHOWCASE_BUTTON)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("dross:" + event + (about.isEmpty() ? "" : "\n" + about)))));
    }

    private FireSoundShowcase() {}
}
