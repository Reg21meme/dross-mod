package com.reg21meme.dross.command;

import com.mojang.brigadier.context.CommandContext;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.knight.KnightKind;
import com.reg21meme.dross.boss.knight.KnightSounds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

/**
 * {@code /dross showcase boss_sounds knights}: lists every sound of the ten elemental knights in chat, knight by knight
 * (each heading in his element's colour), one line per sound with a numbered button for each of its versions
 * ({@code /playsound} of it on its own). The list is the boss area's {@code KnightSounds}, made by
 * {@code tools/knights/sounds.py} along with the sounds (all original). Needs cheats.
 */
public final class KnightSoundShowcase
{
    private static final String PLAY = "/playsound dross:";
    private static final String PLAY_AT = " hostile @s ~ ~ ~ 1 1";

    static int list(CommandContext<CommandSourceStack> context)
    {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("The elemental knights' sounds, all original: each element its own "
                + "character, each knight his own weight (the Wreck Knight the heaviest, the Stormblade the lightest). "
                + "Click a number to hear it (hover for what it is). The same on the page "
                + "boss-previews/knights/sounds/index.html."), false);
        String knight = null;
        int versions = 0;
        for (KnightSounds.Sound sound : KnightSounds.ALL)
        {
            if (!sound.knight().equals(knight))
            {
                knight = sound.knight();
                KnightKind kind = kindOf(knight);
                MutableComponent name = kind != null ? Component.translatable("entity.dross." + kind.typeName())
                        : Component.literal(knight);
                int colour = kind != null ? colourOf(kind.element) : DrossColors.KNIGHT_SHOWCASE_BUTTON;
                Component heading = name.withStyle(style -> style.withBold(true).withColor(colour));
                source.sendSuccess(() -> heading, false);
            }
            source.sendSuccess(() -> line(sound), false);
            versions += sound.plays().size();
        }
        int count = versions;
        source.sendSuccess(() -> Component.literal(KnightSounds.ALL.size() + " sounds, " + count + " versions."), false);
        return KnightSounds.ALL.size();
    }

    private static Component line(KnightSounds.Sound sound)
    {
        MutableComponent line = Component.literal(sound.label() + ": ");
        for (int i = 0; i < sound.plays().size(); i++)
        {
            String command = PLAY + sound.plays().get(i) + PLAY_AT;
            String what = (i + 1) + ". " + sound.versions().get(i);
            line.append(Component.literal("[" + (i + 1) + "]").withStyle(style -> style
                    .withColor(DrossColors.KNIGHT_SHOWCASE_BUTTON)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(what)))));
            line.append(Component.literal(" "));
        }
        return line;
    }

    private static KnightKind kindOf(String knight)
    {
        try
        {
            return KnightKind.valueOf(knight.toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
    }

    private static int colourOf(KnightKind.Element element)
    {
        return switch (element)
        {
            case ICE -> DrossColors.KNIGHT_ICE;
            case POISON -> DrossColors.KNIGHT_POISON;
            case FIRE -> DrossColors.KNIGHT_FIRE;
            case THUNDER -> DrossColors.KNIGHT_THUNDER;
            case EARTH -> DrossColors.KNIGHT_EARTH;
        };
    }

    private KnightSoundShowcase() {}
}
