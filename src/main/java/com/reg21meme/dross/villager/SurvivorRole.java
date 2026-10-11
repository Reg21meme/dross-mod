package com.reg21meme.dross.villager;

import java.util.Locale;
import net.minecraft.network.chat.Component;

/**
 * Which of the hub's two survivors a {@link Survivor} is. The role decides the skin, the name, the voice and the lines.
 * <ul>
 *   <li>{@link #MENTOR}: <b>the Blind Oracle</b> (trader skin 8). She leads: she wanders the hub, and waits for her
 *       student when it falls behind.</li>
 *   <li>{@link #STUDENT}: <b>the Ragged Seeker</b> (trader skin 3). It follows the Oracle wherever she goes.</li>
 * </ul>
 * Safe on the server: only names, numbers and lang keys live here.
 */
public enum SurvivorRole
{
    MENTOR("mentor", "blind_oracle", TraderSkins.BLIND_ORACLE, 0.8F, 5),
    STUDENT("student", "ragged_seeker", TraderSkins.RAGGED_SEEKER, 1.2F, 5);

    /** The id saved on the entity ({@code Role}) and used in its lang keys: {@code dross.survivor.<id>.line.<n>}. */
    private final String id;
    /** The name's lang key ends with this: {@code entity.dross.survivor.<nameId>}. */
    private final String nameId;
    /** The trader skin it wears (see {@link TraderSkins}). */
    private final int skinNumber;
    /** Its voice: the villager sounds are played at about this pitch (the Oracle lower, the Seeker higher). */
    private final float voicePitch;
    /**
     * How many lines it says, in a cycle, when right-clicked: {@code dross.survivor.<id>.line.1} up to this number.
     * Adding a line to the lang file means raising this number too.
     */
    private final int lineCount;

    SurvivorRole(String id, String nameId, int skinNumber, float voicePitch, int lineCount)
    {
        this.id = id;
        this.nameId = nameId;
        this.skinNumber = skinNumber;
        this.voicePitch = voicePitch;
        this.lineCount = lineCount;
    }

    public String id()
    {
        return this.id;
    }

    /** Its name, as players see it: "Blind Oracle" or "Ragged Seeker" (lang keys {@code entity.dross.survivor.*}). */
    public Component displayName()
    {
        return Component.translatable("entity.dross.survivor." + this.nameId);
    }

    /** The skin it wears: never null (8 and 3 are both in {@link TraderSkins#ALL}). */
    public TraderSkins.TraderSkin skin()
    {
        return TraderSkins.lookFor(this.skinNumber);
    }

    public int skinNumber()
    {
        return this.skinNumber;
    }

    public float voicePitch()
    {
        return this.voicePitch;
    }

    public int lineCount()
    {
        return this.lineCount;
    }

    /** The lang key of line {@code number} (1 to {@link #lineCount()}). */
    public String lineKey(int number)
    {
        return "dross.survivor." + this.id + ".line." + number;
    }

    /** The role saved as {@code id}, or the mentor's for anything else (a summoned survivor with no role). */
    public static SurvivorRole byId(String id)
    {
        for (SurvivorRole role : values())
        {
            if (role.id.equals(id.toLowerCase(Locale.ROOT)))
            {
                return role;
            }
        }
        return MENTOR;
    }

    /** The role with this ordinal (synced to players as a number), or the mentor's if it's out of range. */
    public static SurvivorRole byIndex(int index)
    {
        SurvivorRole[] all = values();
        return index >= 0 && index < all.length ? all[index] : MENTOR;
    }
}
