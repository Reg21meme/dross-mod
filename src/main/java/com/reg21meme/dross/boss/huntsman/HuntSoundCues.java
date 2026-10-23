package com.reg21meme.dross.boss.huntsman;

import com.reg21meme.dross.registry.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.List;
import java.util.function.Supplier;

/**
 * The sounds played at moments of the Huntsman's and the Alpha Moose's animations, in the fight and on show alike: the
 * user's picks of October 23, 2026 (made by {@code tools/huntsman/sounds.py}, listed in {@link HuntsmanSounds}), timed so
 * the part that matters (a thrust's whoosh, a kick's thump, the stamp) lands on the animation's moment
 * ({@link HuntCues#moment}). His horn is still a goat horn (the user: "the one we have is fine"). The rest of their
 * sounds are played where it happens: his arrows ({@link HuntMob}), the war cry, the arrow rain, a frost arrow's hit
 * and the ice spikes ({@link HuntFight}), the bola ({@link Bola}), his thrown spear ({@link HuntSpear}), a spear blow
 * that lands and his leaping strike's landing ({@link FootGoal}), and their own voices and steps ({@link Huntsman},
 * {@link AlphaMoose}). The loud ones carry far (a volume over 1 reaches 16 blocks times the volume).
 */
public final class HuntSoundCues
{
    /**
     * One sound: at {@code moment} of {@code model}'s animation {@code anim} plus {@code offset} ticks (a whoosh starts
     * before the blow it's for), or, with no moment, {@code offset} ticks after the animation starts (1 = at once).
     */
    private record Cue(String model, String anim, String moment, int offset, Supplier<SoundEvent> sound, float volume)
    {
        int tick()
        {
            if (this.moment == null)
            {
                return this.offset;
            }
            int at = HuntCues.moment(this.model, this.anim, this.moment);
            return at < 0 ? -1 : Math.max(1, at + this.offset);
        }
    }

    /** A goat horn's call, about 2.5 s long (the second of the game's eight: the user found the first pick, 7.5 s,
     * too long for how long he holds the horn to his lips; then kept it over the original horns, October 23, 2026). */
    private static final Supplier<SoundEvent> HORN = () -> SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(1).value();

    /** How far before a blow its whoosh starts (ticks: the swing sound's rush peaks this far in). */
    private static final int THRUST_LEAD = -3;
    private static final int SWEEP_LEAD = -6;
    /** How far before a shot he draws (the draw's creak runs about 1.1 s). */
    private static final int DRAW_LEAD = -22;

    private static final String M = HuntAnims.MOOSE;
    private static final String F = HuntAnims.FOOT;
    private static final String R = HuntAnims.RIDER;

    private static final List<Cue> CUES = List.of(
            // the Alpha Moose
            new Cue(M, "death", "cry", 0, ModSounds.ALPHA_MOOSE_DEATH, 5.0F),
            new Cue(M, "death", "down", 0, () -> SoundEvents.GENERIC_BIG_FALL, 2.5F),
            new Cue(M, "bellow", "start", 0, ModSounds.ALPHA_MOOSE_BELLOW, 4.0F),
            new Cue(M, "rear", "neigh", 0, ModSounds.ALPHA_MOOSE_REAR, 4.5F),
            new Cue(M, "back_kick", "hit", -8, ModSounds.ALPHA_MOOSE_KICK, 2.0F),       // its thump 0.42 s in
            new Cue(M, "ram", "hit", -4, ModSounds.ALPHA_MOOSE_RAM, 2.0F),              // the antlers' clack 0.22 s in
            new Cue(M, "antler_toss", "hit", -4, ModSounds.ALPHA_MOOSE_RAM, 2.0F),
            new Cue(M, "stomp", "hit", -7, ModSounds.ALPHA_MOOSE_STOMP, 2.5F),          // the hooves 0.35 s in
            new Cue(M, "crack_stamp", "hit", -22, ModSounds.ALPHA_MOOSE_CRACK_STAMP, 3.0F),   // the stamp 1.1 s in
            // the Huntsman on foot
            new Cue(F, "death", "cry", 0, ModSounds.HUNTSMAN_DEATH, 2.5F),
            new Cue(F, "death", "lands", 0, () -> SoundEvents.TRIDENT_HIT_GROUND, 1.0F),
            new Cue(F, "death", "falls", 0, () -> SoundEvents.GENERIC_BIG_FALL, 1.5F),
            new Cue(F, "horn_blow", "blow", 0, HORN, 4.0F),
            new Cue(F, "spear_combo", "hits.0", THRUST_LEAD, ModSounds.HUNTSMAN_SPEAR_THRUST, 1.2F),
            new Cue(F, "spear_combo", "hits.0", -1, ModSounds.HUNTSMAN_EFFORT, 1.2F),
            new Cue(F, "spear_combo", "hits.1", THRUST_LEAD, ModSounds.HUNTSMAN_SPEAR_THRUST, 1.2F),
            new Cue(F, "spear_combo", "hits.1", -1, ModSounds.HUNTSMAN_EFFORT, 1.2F),
            new Cue(F, "spear_combo", "hits.2", SWEEP_LEAD, ModSounds.HUNTSMAN_SPEAR_SWEEP, 1.2F),
            new Cue(F, "spear_combo", "hits.2", -2, ModSounds.HUNTSMAN_EFFORT, 1.2F),
            new Cue(F, "thrust_sweep", "hits.0", THRUST_LEAD, ModSounds.HUNTSMAN_SPEAR_THRUST, 1.2F),
            new Cue(F, "thrust_sweep", "hits.0", -1, ModSounds.HUNTSMAN_EFFORT, 1.2F),
            new Cue(F, "thrust_sweep", "hits.1", SWEEP_LEAD, ModSounds.HUNTSMAN_SPEAR_SWEEP, 1.2F),
            new Cue(F, "thrust_sweep", "hits.1", -2, ModSounds.HUNTSMAN_EFFORT, 1.2F),
            new Cue(F, "leap_strike", "hits.0", -10, ModSounds.HUNTSMAN_EFFORT, 1.2F),  // the spring (it lands at the hit)
            new Cue(F, "throw_spear", "release", -2, ModSounds.HUNTSMAN_EFFORT, 1.2F),
            new Cue(F, "aimed_shot", "release", DRAW_LEAD, ModSounds.HUNTSMAN_BOW_DRAW, 1.0F),
            new Cue(F, "quick_shot", "release", DRAW_LEAD, ModSounds.HUNTSMAN_BOW_DRAW, 1.0F),
            new Cue(F, "backstep_shoot", "shots.0", DRAW_LEAD, ModSounds.HUNTSMAN_BOW_DRAW, 1.0F),
            new Cue(F, "backstep_shoot", "lands", 0, ModSounds.HUNTSMAN_LAND, 1.2F),
            new Cue(F, "arrow_rain", null, 1, ModSounds.HUNTSMAN_BOW_DRAW, 1.0F),
            new Cue(F, "bola_throw", null, 1, ModSounds.HUNTSMAN_BOLA_WHIRL, 1.2F),
            new Cue(F, "land", null, 1, ModSounds.HUNTSMAN_LAND, 1.5F),
            // the Huntsman in the saddle
            new Cue(R, "ride_horn", "blow", 0, HORN, 4.0F),
            new Cue(R, "ride_thrust", "hits.0", THRUST_LEAD, ModSounds.HUNTSMAN_SPEAR_THRUST, 1.2F),
            new Cue(R, "ride_thrust", "hits.0", -1, ModSounds.HUNTSMAN_EFFORT, 1.2F),
            new Cue(R, "ride_shoot", "shots.0", DRAW_LEAD, ModSounds.HUNTSMAN_BOW_DRAW, 1.0F),
            new Cue(R, "ride_shoot", "shots.1", -17, ModSounds.HUNTSMAN_BOW_DRAW, 1.0F),   // just after the first shot
            new Cue(R, "ride_rain", null, 1, ModSounds.HUNTSMAN_BOW_DRAW, 1.0F),
            new Cue(R, "ride_bola", null, 1, ModSounds.HUNTSMAN_BOLA_WHIRL, 1.2F),
            new Cue(R, "ride_leap", "jump", -1, ModSounds.HUNTSMAN_EFFORT, 1.2F));

    private HuntSoundCues() {}

    /** Plays what's due {@code tick} ticks into {@code mob}'s animation {@code a} (server side). */
    public static void play(HuntMob mob, HuntAnims.Anim a, int tick)
    {
        for (Cue cue : CUES)
        {
            if (cue.model().equals(a.model()) && cue.anim().equals(a.name()) && cue.tick() == tick)
            {
                mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), cue.sound().get(), SoundSource.HOSTILE,
                        cue.volume(), 1.0F);
            }
        }
    }
}
