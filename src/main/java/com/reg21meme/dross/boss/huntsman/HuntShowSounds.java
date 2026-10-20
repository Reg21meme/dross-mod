package com.reg21meme.dross.boss.huntsman;

import com.reg21meme.dross.registry.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.List;
import java.util.function.Supplier;

/**
 * Placeholder sounds while the Huntsman and the Alpha Moose are on show (CLAUDE.md step 14: their own sounds come
 * later, as options for the user to pick from): at a few named moments of their animations ({@link HuntCues#moment})
 * a vanilla or wild-moose sound plays, so the moves can be judged with their timing. The loud ones carry far (a volume
 * over 1 reaches 16 blocks times the volume).
 */
public final class HuntShowSounds
{
    /** One sound: at {@code moment} of {@code model}'s animation {@code anim}. */
    private record Cue(String model, String anim, String moment, Supplier<SoundEvent> sound, float volume, float pitch)
    {
    }

    /** A goat horn's call, about 2.5 s long (the second of the game's eight: the user found the first pick, 7.5 s,
     * too long for how long he holds the horn to his lips). */
    private static final Supplier<SoundEvent> HORN = () -> SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(1).value();

    private static final List<Cue> CUES = List.of(
            // the Alpha Moose's death: its last great bellow as it rears, then the crash onto its side
            new Cue(HuntAnims.MOOSE, "death", "cry", ModSounds.MOOSE_CHARGE, 5.0F, 0.55F),
            new Cue(HuntAnims.MOOSE, "death", "down", () -> SoundEvents.GENERIC_BIG_FALL, 2.5F, 0.5F),
            new Cue(HuntAnims.MOOSE, "bellow", "start", ModSounds.MOOSE_CHARGE, 4.0F, 0.65F),
            // his death: the spear hitting the snow, then him
            new Cue(HuntAnims.FOOT, "death", "lands", () -> SoundEvents.TRIDENT_HIT_GROUND, 1.0F, 0.8F),
            new Cue(HuntAnims.FOOT, "death", "falls", () -> SoundEvents.GENERIC_BIG_FALL, 1.5F, 0.8F),
            // the Horn of the Hunt (a goat horn's call for now)
            new Cue(HuntAnims.FOOT, "horn_blow", "blow", HORN, 4.0F, 1.0F),
            new Cue(HuntAnims.RIDER, "ride_horn", "blow", HORN, 4.0F, 1.0F));

    private HuntShowSounds() {}

    /** Plays what's due {@code tick} ticks into {@code mob}'s animation {@code a} (server side). */
    public static void play(HuntMob mob, HuntAnims.Anim a, int tick)
    {
        for (Cue cue : CUES)
        {
            if (cue.model().equals(a.model()) && cue.anim().equals(a.name())
                    && HuntCues.moment(cue.model(), cue.anim(), cue.moment()) == tick)
            {
                mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), cue.sound().get(), SoundSource.HOSTILE,
                        cue.volume(), cue.pitch());
            }
        }
    }
}
