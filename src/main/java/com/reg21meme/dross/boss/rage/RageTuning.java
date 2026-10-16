package com.reg21meme.dross.boss.rage;

/**
 * Every number of the Rage Wand and its Rage (the user's request: a creative-only test tool that makes mobs fight each
 * other). Ticks are game ticks (20 a second).
 */
public final class RageTuning
{
    // ---------------------------------------------------------------- who fights whom

    /** A raged mob looks for raged enemies (raged mobs of another side) this far away (blocks). */
    public static final double RAGE_RANGE = 48.0D;
    /**
     * A raged mob with no raged enemy within {@link #RAGE_RANGE} for this long calms down: its Rage is removed and it
     * behaves normally again (10 seconds: time for the user to hit the next mob after the first).
     */
    public static final int RAGE_CALM_TICKS = 200;
    /** How often (ticks) a raged mob checks whether any raged enemy is still near (for calming down). */
    public static final int ENEMY_SCAN_TICKS = 10;
    /** How often (ticks) a raged mob without a target looks for the nearest raged enemy to go for. */
    public static final int TARGET_SEARCH_TICKS = 5;
    /**
     * A target the game refused (another rule cancelled it, for example "hostile mobs ignore the hub's survivors") is
     * skipped for this long (ticks) before it's tried again, so the mob picks another one meanwhile.
     */
    public static final int REFUSED_TARGET_TICKS = 100;
    /**
     * The rage's goals run at this priority (lower runs first; Minecraft's own goals use 0 and up), so a raged mob's
     * own targeting and fleeing wait while it has a raged enemy.
     */
    public static final int GOAL_PRIORITY = -10;
    /**
     * While raged, a mob's follow range (how far it tracks and finds paths to its target) is at least this many blocks:
     * a zombie's own 35 couldn't reach an enemy at the edge of {@link #RAGE_RANGE}.
     */
    public static final double MIN_FOLLOW_RANGE = RAGE_RANGE + 8.0D;

    // ---------------------------------------------------------------- the headbutt (mobs with no attack of their own)

    /** A headbutt's damage (1 heart). */
    public static final float HEADBUTT_DAMAGE = 2.0F;
    /** Ticks between two headbutts. */
    public static final int HEADBUTT_COOLDOWN = 20;
    /** A headbutter walks at its target this many times its own walking speed (goal-based mobs: cows, pigs...). */
    public static final double HEADBUTT_SPEED = 1.3D;
    /** The same for brain-based mobs (villagers, camels, sniffers...), whose walking speeds are counted differently. */
    public static final float HEADBUTT_BRAIN_SPEED = 0.7F;
    /** It headbutts when its target is this close (blocks, between the edges of their hitboxes). */
    public static final double HEADBUTT_REACH = 0.8D;
    /** How often (ticks) a headbutter works out a new path to its target. */
    public static final int HEADBUTT_REPATH_TICKS = 10;

    // ---------------------------------------------------------------- brain-based fighters (piglins, hoglins, wardens...)

    /** A raged piglin stays angry at its raged enemy for this long (ticks; topped up while the fight lasts). */
    public static final long BRAIN_ANGER_TICKS = 600L;
    /** A raged warden's anger at its raged enemy is raised to at least this (80 is "angry"). */
    public static final int WARDEN_ANGER = 120;

    // ---------------------------------------------------------------- looks and sounds

    /** A raged mob shows a red angry particle over its head every this many ticks. */
    public static final int RAGE_PARTICLE_EVERY = 30;
    /** Angry particles in the burst when the wand rages a mob. */
    public static final int RAGE_BURST_PARTICLES = 6;
    /** The particles appear this far (blocks) above its eyes. */
    public static final double PARTICLE_ABOVE_EYES = 0.5D;
    /** The particles spread this share of its width round its head. */
    public static final double PARTICLE_SPREAD = 0.35D;
    /** The small angry growl when the wand rages a mob (a ravager's roar, quieter and pitched up). */
    public static final float RAGE_SOUND_VOLUME = 0.6F;
    public static final float RAGE_SOUND_PITCH = 1.6F;
    /** When the wand can't rage a mob (a frozen showcase mob, the trader...): a dull click and a puff of smoke. */
    public static final float FIZZLE_VOLUME = 0.6F;
    public static final float FIZZLE_PITCH = 1.4F;
    public static final int FIZZLE_SMOKE = 6;

    private RageTuning() {}
}
