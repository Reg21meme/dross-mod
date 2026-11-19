package com.reg21meme.dross.boss.knight;

import com.reg21meme.dross.DrossColors;

import java.util.Locale;

/**
 * The ten elemental knights (CLAUDE.md step 18; the user's pairs, November 9, 2026): two for each element, each its own
 * entity type {@code dross:knight_<name>} and spawn egg, its model made by {@code tools/knights/mobs.py}
 * ({@code knightmob/<model>}). Every knight has two moves (the user: "you don't have to do too much, 2 attacks"), what
 * each does is in {@link ElementalKnight}; the numbers here are its health, armour, speed and how its moves are used.
 * <p>
 * Each row: its model; its element; health; armour; movement speed; how much of a knockback it shrugs off; how far it
 * likes to stand from its foe while its moves cool down (0: it walks right up); its two moves.
 */
public enum KnightKind
{
    THAWED_AXE("thawedaxe", Element.ICE, 90, 8, 0.25, 0.6, 0.0,
            new Move("swing", 0.0, 4.0, 22),
            new Move("smash", 2.0, 12.0, 160),
            DrossColors.KNIGHT_THAWED_AXE_EGG_BASE, DrossColors.KNIGHT_THAWED_AXE_EGG_SPOTS),
    THAWED_SPEAR("thawedspear", Element.ICE, 90, 10, 0.25, 0.6, 0.0,
            new Move("thrust", 0.0, 5.0, 20),
            new Move("shield_wall", 0.0, 4.5, 200),
            DrossColors.KNIGHT_THAWED_SPEAR_EGG_BASE, DrossColors.KNIGHT_THAWED_SPEAR_EGG_SPOTS),
    BLIGHT_WARDEN("warden", Element.POISON, 80, 6, 0.24, 0.5, 10.0,
            new Move("cast", 4.0, 24.0, 50),
            new Move("blight_rain", 0.0, 7.0, 220),
            DrossColors.KNIGHT_WARDEN_EGG_BASE, DrossColors.KNIGHT_WARDEN_EGG_SPOTS),
    SPOREBOUND("sporebound", Element.POISON, 90, 6, 0.26, 0.6, 0.0,
            new Move("reap", 0.0, 4.5, 26),
            new Move("spore_burst", 0.0, 6.5, 200),
            DrossColors.KNIGHT_SPOREBOUND_EGG_BASE, DrossColors.KNIGHT_SPOREBOUND_EGG_SPOTS),
    CINDER("cinder", Element.FIRE, 100, 10, 0.24, 0.7, 0.0,
            new Move("swing", 0.0, 4.0, 26),
            new Move("slam", 0.0, 6.0, 180),
            DrossColors.KNIGHT_CINDER_EGG_BASE, DrossColors.KNIGHT_CINDER_EGG_SPOTS),
    FLAMEHAND("flamehand", Element.FIRE, 70, 6, 0.27, 0.5, 12.0,
            new Move("fireball", 3.0, 28.0, 26),
            new Move("great_fireball", 6.0, 32.0, 260),
            DrossColors.KNIGHT_FLAMEHAND_EGG_BASE, DrossColors.KNIGHT_FLAMEHAND_EGG_SPOTS),
    WRECK("wreck", Element.THUNDER, 110, 12, 0.22, 0.8, 0.0,
            new Move("anchor_throw", 5.0, 20.0, 140),
            new Move("anchor_slam", 0.0, 5.0, 40),
            DrossColors.KNIGHT_WRECK_EGG_BASE, DrossColors.KNIGHT_WRECK_EGG_SPOTS),
    STORMBLADE("stormblade", Element.THUNDER, 70, 6, 0.36, 0.4, 0.0,
            new Move("slash", 0.0, 3.8, 16),
            new Move("dash", 4.0, 16.0, 70),
            DrossColors.KNIGHT_STORMBLADE_EGG_BASE, DrossColors.KNIGHT_STORMBLADE_EGG_SPOTS),
    GEODE("geode", Element.EARTH, 100, 10, 0.23, 0.85, 0.0,
            new Move("pick", 0.0, 4.5, 26),
            new Move("crystal_burst", 0.0, 9.0, 180),
            DrossColors.KNIGHT_GEODE_EGG_BASE, DrossColors.KNIGHT_GEODE_EGG_SPOTS),
    ROCKFALL("rockfall", Element.EARTH, 110, 12, 0.22, 0.85, 9.0,
            new Move("throw", 5.0, 28.0, 70),
            new Move("rockfall", 0.0, 16.0, 220),
            DrossColors.KNIGHT_ROCKFALL_EGG_BASE, DrossColors.KNIGHT_ROCKFALL_EGG_SPOTS);

    /** The five elements: what each knight shrugs off (fire, cold, poison, lightning, being thrown about). */
    public enum Element
    {
        ICE, POISON, FIRE, THUNDER, EARTH;

        /** Never burns: fire, lava and magma do nothing (the user, November 19, 2026: the thunder, fire and rock knights
         * immune to fire). Their entity types are fire-immune (registry/ModEntities). */
        public boolean fireproof()
        {
            return this == FIRE || this == THUNDER || this == EARTH;
        }
    }

    /**
     * One move: its animation, the distance to its foe (blocks, from its middle) it's used at, and how long until it can
     * be used again (ticks, from its start).
     */
    public record Move(String anim, double minRange, double maxRange, int cooldown)
    {
    }

    public final String model;
    public final Element element;
    public final double health;
    public final double armor;
    public final double speed;
    public final double knockbackResistance;
    /** While both its moves cool down it keeps about this far from its foe (0: it closes in). */
    public final double keepAway;
    public final Move first;
    public final Move second;
    public final int eggBase;
    public final int eggSpots;

    KnightKind(String model, Element element, double health, double armor, double speed, double knockbackResistance,
               double keepAway, Move first, Move second, int eggBase, int eggSpots)
    {
        this.model = model;
        this.element = element;
        this.health = health;
        this.armor = armor;
        this.speed = speed;
        this.knockbackResistance = knockbackResistance;
        this.keepAway = keepAway;
        this.first = first;
        this.second = second;
        this.eggBase = eggBase;
        this.eggSpots = eggSpots;
    }

    /** Its entity type's name ({@code knight_thawed_axe}...). */
    public String typeName()
    {
        return "knight_" + this.name().toLowerCase(Locale.ROOT);
    }

    /** Its two moves (0 the first, 1 the second). */
    public Move move(int index)
    {
        return index == 0 ? this.first : this.second;
    }
}
