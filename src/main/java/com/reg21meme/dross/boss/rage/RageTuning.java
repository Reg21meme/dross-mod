package com.reg21meme.dross.boss.rage;

import net.minecraft.world.BossEvent;

/**
 * Every number of the Rage Wand and its Rage (the user's request: a creative-only test tool that makes mobs fight each
 * other, in teams). Ticks are game ticks (20 a second). The Rage and a mob's team never wear off (the user, October 16:
 * "they stay their team forever"), so there's no calming down any more.
 */
public final class RageTuning
{
    // ---------------------------------------------------------------- who fights whom

    /** A raged mob looks for raged enemies (raged mobs of another team) this far away (blocks; the user: 40). */
    public static final double RAGE_RANGE = 40.0D;
    /** How often (ticks) a raged mob checks whether any raged enemy is near (while one is, it ignores players). */
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
    /**
     * In the bedrock test arena a raged mob sees raged enemies anywhere in it (the user, October 31: its far ends are
     * 89 blocks apart): it searches this far (corner to corner, floor to roof) and tracks and finds paths this far.
     */
    public static final double ARENA_RAGE_RANGE = 136.0D;
    public static final double ARENA_FOLLOW_RANGE = ARENA_RAGE_RANGE;
    /**
     * When a mob joins or switches team, every raged mob of that team this close (blocks) lets go of it, and it of them
     * (the user, October 17: team colour beats natural enmity). As far as a raged mob can track a target.
     */
    public static final double SETTLE_RANGE = MIN_FOLLOW_RANGE;
    /**
     * The safety net: how often (ticks) a raged mob makes sure it holds nothing against a teammate (target, anger,
     * brain memories) and that none of its target goals is stuck on a target the game refused.
     */
    public static final int TEAMMATE_CHECK_TICKS = 10;

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

    /** A raged mob shows a few specks of its team's colour over its head every this many ticks... */
    public static final int RAGE_PARTICLE_EVERY = 20;
    public static final int RAGE_PARTICLES = 2;
    /** ...a burst of them when the wand rages it or switches its team, a smaller one when it joins its boss's team... */
    public static final int RAGE_BURST_PARTICLES = 14;
    public static final int JOIN_BURST_PARTICLES = 6;
    /** ...each this big (Minecraft's dust size). */
    public static final float TEAM_PARTICLE_SIZE = 1.3F;
    /** The particles appear this far (blocks) above its eyes. */
    public static final double PARTICLE_ABOVE_EYES = 0.5D;
    /** The particles spread this share of its width round its head. */
    public static final double PARTICLE_SPREAD = 0.35D;
    /** The small angry growl when the wand rages a mob (a ravager's roar, quieter and pitched up). */
    public static final float RAGE_SOUND_VOLUME = 0.6F;
    public static final float RAGE_SOUND_PITCH = 1.6F;
    /** When the wand can't rage a mob (a frozen showcase mob, a survivor at the hub...): a dull click and a puff of smoke. */
    public static final float FIZZLE_VOLUME = 0.6F;
    public static final float FIZZLE_PITCH = 1.4F;
    public static final int FIZZLE_SMOKE = 6;

    // ---------------------------------------------------------------- the wand's teams

    /** Right-clicking with the wand moves it to the next team: a small click (a stone button, pitched up). */
    public static final float TEAM_CLICK_VOLUME = 0.5F;
    public static final float TEAM_CLICK_PITCH = 1.6F;

    // ---------------------------------------------------------------- seeing the teams with the wand

    /** Holding the wand (either hand), you see the teams more easily (the user, October 30: "particles come out from
     *  mobs when you hold the Rage Wand, to see what team they are on more easily"): every this many ticks, every raged
     *  mob and every player on a team within this many blocks gives off this many specks of its team's colour all over
     *  its body, this big; only the player holding the wand sees them. */
    public static final int WAND_SHOW_EVERY = 4;
    public static final double WAND_SHOW_RANGE = 64.0D;
    public static final int WAND_SHOW_PARTICLES = 5;
    public static final float WAND_SHOW_SIZE = 1.8F;

    private RageTuning() {}

    /** A raged Ender Dragon ({@code RageDragon}): after each charge through its enemy it flies on this far past it and
     *  this high, then turns and charges again; it spits a fireball at it every this many ticks when it can see it within
     *  this many blocks. */
    public static final double DRAGON_PASS_PAST = 30.0D;
    public static final double DRAGON_PASS_UP = 12.0D;
    public static final int DRAGON_FIREBALL_EVERY = 70;
    public static final double DRAGON_FIREBALL_RANGE = 64.0D;
    /** With no enemy near it circles this far round where it was raged, this high over it, this far round (radians) a
     *  leg at a time. */
    public static final double DRAGON_CIRCLE_RADIUS = 24.0D;
    public static final double DRAGON_CIRCLE_UP = 14.0D;
    public static final double DRAGON_CIRCLE_STEP = 1.0D;

    // ---------------------------------------------------------------- many at once (the user, October 31)

    /** The Rage Wand's burst (sneak + right-click): every mob this far round the spot you look at (up to
     *  {@link #WAND_BURST_REACH} blocks away) is raged onto the wand's team; then the wand waits this long (ticks). */
    public static final double WAND_BURST_RADIUS = 8.0D;
    public static final double WAND_BURST_REACH = 48.0D;
    public static final int WAND_BURST_COOLDOWN = 20;
    /** Where a Rage Bow's arrow lands, every mob this far round it is raged onto the bow's team. */
    public static final double BOW_BURST_RADIUS = 5.0D;
    /** The burst's look: the dust rings' points, its size, the puff in the middle, its roar. */
    public static final int BURST_RING_POINTS = 48;
    public static final float BURST_DUST_SIZE = 1.6F;
    public static final int BURST_CENTRE_PARTICLES = 30;
    public static final float BURST_VOLUME = 0.8F;
    public static final float BURST_PITCH = 1.4F;
    /** The wand's beam to the burst (dust every this many blocks) and a bow arrow's trail. */
    public static final double BEAM_STEP = 0.8D;
    public static final float ARROW_TRAIL_SIZE = 1.0F;

    // ---------------------------------------------------------------- the dragon's and the warden's bars

    /** A raged Ender Dragon or warden shows Minecraft's plain boss bar to players this close (blocks; the user,
     *  October 31: "basic is fine"). */
    public static final double BAR_RANGE = 96.0D;
    /** Who sees it is checked this often (ticks). */
    public static final int BAR_PLAYERS_EVERY = 10;
    /** A bar whose mob hasn't ticked for this long (ticks: unloaded, its land not ticking) leaves every screen. */
    public static final long BAR_STALE_TICKS = 20L;
    /** The dragon's bar in its own pink (as in the End), the warden's blue. */
    public static final BossEvent.BossBarColor BAR_COLOR_DRAGON = BossEvent.BossBarColor.PINK;
    public static final BossEvent.BossBarColor BAR_COLOR_WARDEN = BossEvent.BossBarColor.BLUE;
}
