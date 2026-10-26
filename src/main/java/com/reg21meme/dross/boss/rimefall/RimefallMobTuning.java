package com.reg21meme.dross.boss.rimefall;

/**
 * Every number for Rimefall's animals (CLAUDE.md, "The plan", step 14): the Rimehorn, the moose and the Pond Skater.
 * Speeds are Minecraft's movement-speed attribute (a walking mob covers about 21.6 x speed² blocks a second... roughly:
 * 0.2 is a cow, 0.3 a spider); distances in blocks; times in ticks (20 a second); damage in half hearts.
 */
public final class RimefallMobTuning
{
    // ---------------------------------------------------------------- the Rimehorn

    public static final double RIMEHORN_HEALTH = 40.0D;
    public static final double RIMEHORN_ARMOR = 4.0D;
    public static final double RIMEHORN_SPEED = 0.2D;
    public static final double RIMEHORN_KNOCKBACK_RESISTANCE = 0.7D;
    /** How far it notices things, and how far a hurt Rimehorn's call reaches its pack (the user's "the pack"). */
    public static final double RIMEHORN_FOLLOW_RANGE = 16.0D;
    /** The horn flick (the user: "a headbutt, like a flick up, that launches you in the air, more vertical than
     * horizontal"): its damage, how hard it throws you up and back, how far it reaches from the front of its body. */
    public static final float FLICK_DAMAGE = 6.0F;
    public static final double FLICK_LAUNCH_UP = 1.05D;
    public static final double FLICK_LAUNCH_BACK = 0.45D;
    public static final double FLICK_REACH = 3.2D;
    /** The flick's animation: its length, when the horn strikes, and the pause before the next one. */
    public static final int FLICK_TICKS = 26;
    public static final int FLICK_HIT_TICK = 9;
    public static final int FLICK_COOLDOWN = 20;
    /** The rush (the user: "if you shoot them with an arrow ... they'll rush at you", 7-8 blocks, 5 damage): when it
     * rushes (its target this far off), how far it runs, how fast (blocks a tick), and what it does to you. */
    public static final double RUSH_MIN_DISTANCE = 5.5D;
    public static final double RUSH_MAX_DISTANCE = 12.0D;
    public static final double RUSH_LENGTH = 8.0D;
    /** A quick dash (the user, October 19: "make a quick dash, make the rush a little faster"; 0.42 before): about 11
     * blocks a second, the speed its rush animation's feet move at. */
    public static final double RUSH_SPEED = 0.54D;
    public static final int RUSH_MAX_TICKS = 20;
    public static final float RUSH_DAMAGE = 5.0F;
    public static final double RUSH_KNOCKBACK = 1.1D;
    public static final double RUSH_KNOCKBACK_UP = 0.35D;
    /** A short wind-up before the rush (it lowers its head), and the wait before it may rush again. */
    public static final int RUSH_WINDUP = 5;
    public static final int RUSH_COOLDOWN = 70;
    /** How fast it closes in on its target between moves (times its speed). */
    public static final double RIMEHORN_CHASE_SPEED = 1.5D;
    /** It gives up a chase once it's this far from where the chase began, and goes back there (the user, October 19:
     * hit a calf and its herd "keeps chasing me across the biome"; its anger also runs out in 20-39 s now). */
    public static final double RIMEHORN_CHASE_LEASH = 24.0D;
    /** A natural pack (the user: 6-8): its first this many are males (the frosted coat), the rest females. */
    public static final int PACK_MALES = 2;
    /** A calf grows up in this many ticks (the user: 30-40 minutes, "their meat will give out more saturation" later;
     * vanilla's are 20). */
    public static final int RIMEHORN_GROW_TICKS = 35 * 60 * 20;
    /** Two Rimehorns in love mate once they're this close, middle to middle, past their own width (vanilla's 3 blocks
     * is less than a Rimehorn is wide: they bumped apart before they ever got that close, the user's "they struggle to
     * make a calf"). */
    public static final double MATE_REACH_PAST_WIDTH = 1.5D;
    /** How far a Rimehorn in love looks for its mate. */
    public static final double MATE_SEARCH_RANGE = 12.0D;
    /** Grazing: how often an idle Rimehorn starts eating (one chance in this many each tick) and for how long. */
    public static final int RIMEHORN_EAT_CHANCE = 600;
    public static final int RIMEHORN_EAT_TICKS = 160;
    /** Being angry: how long (seconds, picked at random in this range), like a wolf's or a polar bear's. */
    public static final int ANGER_MIN_SECONDS = 20;
    public static final int ANGER_MAX_SECONDS = 39;

    // ---------------------------------------------------------------- the moose

    public static final double MOOSE_HEALTH = 50.0D;
    public static final double MOOSE_ARMOR = 2.0D;
    public static final double MOOSE_SPEED = 0.22D;
    public static final double MOOSE_KNOCKBACK_RESISTANCE = 0.5D;
    public static final double MOOSE_FOLLOW_RANGE = 20.0D;
    /** Its charge: how fast it runs at you (times its speed). */
    public static final double MOOSE_CHARGE_SPEED = 1.9D;
    /** It gives up a chase once it's this far from where the chase began, and goes back there (like the Rimehorn). */
    public static final double MOOSE_CHASE_LEASH = 24.0D;
    /** The ram (the user: it "knocks you back more than up"): damage (the user, October 19: "make the male moose do
     * more damage than the female"; 7 for both before), the throw, how far it reaches. */
    public static final float RAM_DAMAGE_BULL = 9.0F;
    public static final float RAM_DAMAGE_COW = 6.0F;
    public static final double RAM_KNOCKBACK = 1.6D;
    public static final double RAM_KNOCKBACK_UP = 0.3D;
    public static final double RAM_REACH = 2.4D;
    public static final int RAM_TICKS = 22;
    public static final int RAM_HIT_TICK = 9;
    public static final int RAM_COOLDOWN = 25;
    /** A mother (the user: "if a moose is a mother and has a child, kind of like polar bears, it'll auto attack you"):
     * how near her calf must be, and how near you must come to her or her calf (12 to her before; the user, October 19:
     * "the range of attack for a moose mother is too far, it leaves the calf"). */
    public static final double MOTHER_CALF_RANGE = 16.0D;
    public static final double MOTHER_NOTICE_RANGE = 6.0D;
    /** She never chases you further than this from her calf, even when you hurt her (until October 25, 2026 a hurt
     * mother chased like any moose): past it she gives up and goes back to it. After giving up she won't start again for
     * a moment. */
    public static final double MOTHER_LEASH = 10.0D;
    /** A mother remembers her calf while she's within this many blocks of it (beyond MOTHER_CALF_RANGE, as a chase takes
     * her off); the leash holds whatever she chases, a player who hurt her too. */
    public static final double MOTHER_REMEMBERS = 64.0D;
    public static final int MOTHER_GIVE_UP_PAUSE = 60;
    /** Having given up, she walks back until she's this close to her calf (then she's free to graze and wander). */
    public static final double MOTHER_BACK_WITHIN = 4.0D;
    /** A naturally spawned moose is a mother with her calf this often (otherwise alone: a bull or a cow, half each). */
    public static final float MOTHER_CHANCE = 0.3F;
    /** Kneeling to graze: how often (one chance in this many each tick) and how long (the animation's 6 seconds). */
    public static final int MOOSE_EAT_CHANCE = 900;
    public static final int MOOSE_EAT_TICKS = 120;

    // ---------------------------------------------------------------- the Pond Skater

    /** Its size: the gallery design (rimedesigns/d08_pondskater.py) drawn at this much (the user, October 20: "decrease
     * the skater size by around 20%, it seems too big"). Its hitbox (ModEntities), its legs' hitboxes and spots, its reach
     * and its strides all follow. */
    public static final float SKATER_SCALE = 0.8F;
    public static final double SKATER_HEALTH = 16.0D;
    public static final double SKATER_DAMAGE = 3.0D;
    /** A spider's speed (the user: "as fast as a spider"). */
    public static final double SKATER_SPEED = 0.3D;
    public static final double SKATER_FOLLOW_RANGE = 24.0D;
    /** On ice (the user: "extremely fast, almost ocelot level ... they catch up to you instantly"): its speed times
     * this much more (2.6 x a spider's on ice, which slides too). */
    public static final double SKATER_ICE_SPEED_BONUS = 1.6D;
    /** The stab: its animation, when the beak strikes, the pause after, how far it reaches past its body. */
    public static final int STAB_TICKS = 22;
    public static final int STAB_HIT_TICK = 10;
    public static final int STAB_COOLDOWN = 12;
    public static final double STAB_REACH = 2.2D * SKATER_SCALE;
    /** Its legs' little hitboxes (the user: "make its leg also part of the hitbox, but a small hitbox for the legs"). */
    public static final float LEG_PART_WIDTH = 0.7F * SKATER_SCALE;
    public static final float LEG_PART_HEIGHT = 0.9F * SKATER_SCALE;

    private RimefallMobTuning() {}
}
