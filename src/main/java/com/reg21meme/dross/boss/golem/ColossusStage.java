package com.reg21meme.dross.boss.golem;

/**
 * The three stages of the Cinder Colossus fight, and the numbers that change from one to the next (the rest of the
 * fight's numbers are in {@link ColossusTuning}).
 * <ol>
 *   <li>{@link #SHELL} (100% to 70% of its health): slow and heavy, in its dormant rock shell (look:
 *       {@link GolemPhase#DORMANT}). Swipes, throws rocks, slams the ground, charges.</li>
 *   <li>{@link #CRACKING} (70% to 35%): its volcanoes have erupted and the shell is breaking up, lava glowing through
 *       (look: {@link GolemPhase#ERUPTED} with loose plates and bright cracks). A bit faster and more aggressive; adds
 *       the volcano lob (lava pools) and summons stone golems.</li>
 *   <li>{@link #LAVA_CORE} (35% to 0%): the shell has burst off (look: {@link GolemPhase#CORE}). Faster, angrier; every
 *       hit sets you on fire, its charge leaves a longer trail of fire, it summons lava golems instead and throws no
 *       more rocks.</li>
 * </ol>
 * Each stage change starts with a roar that throws the players near it back, then an attack: four stone golems
 * (Cracking) or four lava blobs (Lava Core). Which move it picks in each stage is in the chance tables of
 * {@link ColossusTuning}; while a player's risen undead are near it may summon in any stage, the Shell too.
 * The stage is saved as the golem's look ({@link GolemPhase}), so a rejoin mid-fight restores both.
 */
public enum ColossusStage
{
    // Speed: 25% faster in every stage than the first version (0.15, 0.17, 0.20), at the user's request. Minecraft moves
    // a walking mob by its speed squared, so it really covers about 56% more ground: about 1.5, 2.0 and 2.8 blocks a
    // second (a player walks 4.3).
    // Ranged damage taken (the user's numbers): bows and other projectiles barely scratch the stone and burn up in
    // the lava, so it can't be beaten easily from afar. Melee is never reduced.
    //        armour  speed    damage x  cooldown x  pause  turn speed  ranged taken  name on the boss bar
    SHELL(    12.0D,  0.1875D, 1.0F,     1.0F,       24,    6.0F,       0.50F,        "shell",
    // The user's damage per stage (October 17), in half-hearts (a player has 20): swipe, charge, slam, the roar (the
    // first hit's roar in the Shell; entering Cracking; entering the Lava Core), and the blobs a volcano lob spits.
    //        swipe  charge  slam  roar  lob blobs
              13.0F, 12.0F,  7.0F, 0.0F, 2),
    CRACKING(  8.0D,  0.2125D, 1.15F,    0.8F,       16,    8.0F,       0.35F,        "cracking",
              15.0F, 13.0F,  8.0F, 2.0F, 2),
    LAVA_CORE( 4.0D,  0.25D,   1.3F,     0.6F,       10,   11.0F,       0.15F,        "core",
              16.0F, 14.0F, 10.0F, 4.0F, 4);

    /** Armour points (a player in full diamond armour has 20): the rock shell is tough, the lava much less so. */
    public final double armor;
    /** Movement speed (Minecraft's speed attribute; a zombie is 0.23). The walk animation is sped up to match. */
    public final double speed;
    /**
     * The thrown rock's and the lava blobs' damage is multiplied by this (they get harder as it breaks down). The swipe,
     * charge, slam and roar have their own numbers per stage (below).
     */
    public final float damageScale;
    /** Every attack's cooldown is multiplied by this (it attacks more often). */
    public final float cooldownScale;
    /** Ticks it waits after one attack before it starts the next (20 ticks = 1 second). */
    public final int attackPause;
    /** How fast it turns, in degrees a tick (while walking and while it aims an attack). */
    public final float turnSpeed;
    /**
     * Projectile damage (arrows, tridents, fireballs, snowballs...: anything tagged {@code minecraft:is_projectile}) is
     * multiplied by this before its armour counts; melee hits aren't. Shell 50%, Cracking 35%, Lava Core 15%.
     */
    public final float rangedDamageTaken;
    /** The end of the boss bar's name key: boss.dross.cinder_colossus.<this>. */
    public final String barKey;
    /** Damage of a swipe, a charge's hit and a ground slam (half-hearts: 13 is 6.5 hearts). */
    public final float swipeDamage;
    public final float chargeDamage;
    public final float slamDamage;
    /**
     * Damage of the roar to every player it throws back: the Shell's is the first hit's roar (none), Cracking's the roar
     * as it erupts into that stage, the Lava Core's the roar as its shell bursts.
     */
    public final float roarDamage;
    /** Lava blobs a volcano lob spits out (2 in Cracking, 4 in the Lava Core; the Shell never lobs). */
    public final int lobBlobs;

    ColossusStage(double armor, double speed, float damageScale, float cooldownScale, int attackPause, float turnSpeed,
                  float rangedDamageTaken, String barKey, float swipeDamage, float chargeDamage, float slamDamage,
                  float roarDamage, int lobBlobs)
    {
        this.swipeDamage = swipeDamage;
        this.chargeDamage = chargeDamage;
        this.slamDamage = slamDamage;
        this.roarDamage = roarDamage;
        this.lobBlobs = lobBlobs;
        this.armor = armor;
        this.speed = speed;
        this.damageScale = damageScale;
        this.cooldownScale = cooldownScale;
        this.attackPause = attackPause;
        this.turnSpeed = turnSpeed;
        this.rangedDamageTaken = rangedDamageTaken;
        this.barKey = barKey;
    }

    /** The stage a golem with this look is in. */
    public static ColossusStage of(GolemPhase phase)
    {
        return switch (phase)
        {
            case DORMANT -> SHELL;
            case ERUPTED -> CRACKING;
            case CORE -> LAVA_CORE;
        };
    }

    /** The boss bar's name (a translation key; the client's custom bar recognises it by the boss.dross. start). */
    public String barName()
    {
        return "boss.dross.cinder_colossus." + this.barKey;
    }
}
