package com.reg21meme.dross.boss.rage;

import com.reg21meme.dross.DrossColors;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import javax.annotation.Nullable;

/**
 * The Rage Wand's teams (the user's request, October 16): right-clicking with the wand moves it to the next team
 * (Blue, Red, Green, Yellow, Purple, Blue...), and a mob it hits is raged onto the wand's team. Raged mobs fight the raged
 * mobs of the other teams, never their own (see {@link Rage}). Each team has a colour ({@code DrossColors.RAGE_TEAM_*}),
 * a name (lang key {@code item.dross.rage_wand.team.<id>}) and the wand's crystal shows it (item model override
 * {@code dross:rage_team}, {@link #modelValue}).
 */
public enum RageTeam
{
    BLUE("blue", DrossColors.RAGE_TEAM_BLUE),
    RED("red", DrossColors.RAGE_TEAM_RED),
    GREEN("green", DrossColors.RAGE_TEAM_GREEN),
    YELLOW("yellow", DrossColors.RAGE_TEAM_YELLOW),
    PURPLE("purple", DrossColors.RAGE_TEAM_PURPLE);

    /** A wand that has never been switched is on this team. */
    public static final RageTeam DEFAULT = BLUE;

    private final String id;
    private final int color;

    RageTeam(String id, int color)
    {
        this.id = id;
        this.color = color;
    }

    /** Its saved name (on the wand and on raged mobs). */
    public String id()
    {
        return this.id;
    }

    /** Its colour (0xRRGGBB). */
    public int color()
    {
        return this.color;
    }

    /** The next team in the wand's cycle: Blue, Red, Green, Yellow, Purple, then Blue again. */
    public RageTeam next()
    {
        RageTeam[] all = values();
        return all[(this.ordinal() + 1) % all.length];
    }

    /** Its name ("Blue"...), in its colour. */
    public MutableComponent displayName()
    {
        return Component.translatable("item.dross.rage_wand.team." + this.id)
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(this.color)));
    }

    /** The value of the wand's item model property: 0 for Blue up to 1 for Purple (the model's overrides pick the crystal). */
    public float modelValue()
    {
        return this.ordinal() / (float) (values().length - 1);
    }

    /** The team with this saved name, or null. */
    @Nullable
    public static RageTeam byId(@Nullable String id)
    {
        for (RageTeam team : values())
        {
            if (team.id.equals(id))
            {
                return team;
            }
        }
        return null;
    }
}
