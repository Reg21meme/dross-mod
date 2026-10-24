package com.reg21meme.dross.boss.huntsman.folk;

import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.rimefall.RimefallAnims;

import java.util.List;
import java.util.Set;

/**
 * The hunter villagers' callings (the user, October 23, 2026: "we have a spear and circular shield one, an axe one, a
 * long spear one, a bow and arrow one, then a horn dude with the flag ... make the basic dudes, not the warriors, do 5
 * ... also add an elder who has scarred eyes and a beard and is the one with a sword; this guy has 50 health"). Each
 * is its own entity type ({@code dross:hunter_<key>}) with its own spawn egg; all of them are one model
 * ({@code tools/rimefall/rimemobs/hunter_folk.py}: the Huntsman's, drawn at half his size), each showing only its own
 * gear ({@link #shows}).
 */
public enum FolkRole
{
    // warriors: 25 to 30 health
    SPEARMAN("spearman", Kind.WARRIOR, 30.0D, 4.0D, 4.0D, Attack.COMBO, DrossColors.HUNTER_SPEARMAN_EGG_SPOTS, Set.of("spear", "spear_free", "shield", "antlers", "snout")),
    AXEMAN("axeman", Kind.WARRIOR, 28.0D, 6.0D, 3.0D, Attack.CHOP, DrossColors.HUNTER_AXEMAN_EGG_SPOTS, Set.of("axe", "antlers", "snout")),
    PIKEMAN("pikeman", Kind.WARRIOR, 28.0D, 5.0D, 3.0D, Attack.PIKE, DrossColors.HUNTER_PIKEMAN_EGG_SPOTS, Set.of("long_spear", "antlers", "snout")),
    ARCHER("archer", Kind.WARRIOR, 25.0D, 3.0D, 2.0D, Attack.CHOP, DrossColors.HUNTER_ARCHER_EGG_SPOTS, Set.of("bow_back", "quiver_hip", "antlers", "snout")),
    HORNBLOWER("hornblower", Kind.WARRIOR, 26.0D, 4.0D, 3.0D, Attack.THRUST_SWEEP, DrossColors.HUNTER_HORNBLOWER_EGG_SPOTS,
            Set.of("spear", "spear_free", "horn_belt", "flag", "antlers", "snout")),
    // workers: they fight with their tools, weakly
    FARMER("farmer", Kind.WORKER, 20.0D, 3.0D, 0.0D, Attack.CHOP, DrossColors.HUNTER_FARMER_EGG_SPOTS, Set.of("hoe", "basket", "snout")),
    BUTCHER("butcher", Kind.WORKER, 20.0D, 4.0D, 0.0D, Attack.CHOP, DrossColors.HUNTER_BUTCHER_EGG_SPOTS, Set.of("cleaver", "apron", "snout")),
    HERDER("herder", Kind.WORKER, 20.0D, 3.0D, 0.0D, Attack.CHOP, DrossColors.HUNTER_HERDER_EGG_SPOTS, Set.of("crook", "snout")),
    FISHER("fisher", Kind.WORKER, 20.0D, 2.0D, 0.0D, Attack.CHOP, DrossColors.HUNTER_FISHER_EGG_SPOTS, Set.of("rod", "fish", "snout")),
    TANNER("tanner", Kind.WORKER, 20.0D, 3.0D, 0.0D, Attack.CHOP, DrossColors.HUNTER_TANNER_EGG_SPOTS, Set.of("scraper", "hide_roll", "snout")),
    // the elder: the one you talk to
    ELDER("elder", Kind.ELDER, 50.0D, 7.0D, 4.0D, Attack.CHOP, DrossColors.HUNTER_ELDER_EGG_SPOTS, Set.of("sword", "antlers", "face"));

    public enum Kind
    {
        WARRIOR, WORKER, ELDER
    }

    /** A melee attack: its animation and the moments (ticks) its blows land, and how far it reaches past its body. */
    public enum Attack
    {
        CHOP(FolkAnims.CHOP, new int[] {10}, 2.4D),
        /** The spearman's: thrust, thrust, sweep (the Huntsman's own combo). */
        COMBO(FolkAnims.SPEAR_COMBO, new int[] {8, 16, 26}, 2.6D),
        THRUST_SWEEP(FolkAnims.THRUST_SWEEP, new int[] {8, 18}, 2.6D),
        PIKE(FolkAnims.THRUST_SWEEP, new int[] {8, 18}, 3.6D);

        public final RimefallAnims.Anim anim;
        public final int[] hits;
        public final double reach;

        Attack(RimefallAnims.Anim anim, int[] hits, double reach)
        {
            this.anim = anim;
            this.hits = hits;
            this.reach = reach;
        }
    }

    /** Every gear bone of the model a calling may show (the others are hidden for it). */
    public static final List<String> GEAR = List.of("spear", "spear_free", "bow_back", "horn_belt", "quiver_hip", "shield",
            "axe", "long_spear", "sword", "hoe", "cleaver", "crook", "rod", "scraper", "basket", "fish", "flag", "apron",
            "hide_roll", "antlers", "snout", "face");

    public final String key;
    public final Kind kind;
    public final double health;
    public final double damage;
    public final double armor;
    public final Attack attack;
    /** Its spawn egg's spots (the base is every calling's: {@link DrossColors#HUNTER_FOLK_EGG_BASE}). */
    public final int eggSpots;
    public final Set<String> shows;

    FolkRole(String key, Kind kind, double health, double damage, double armor, Attack attack, int eggSpots, Set<String> shows)
    {
        this.key = key;
        this.kind = kind;
        this.health = health;
        this.damage = damage;
        this.armor = armor;
        this.attack = attack;
        this.eggSpots = eggSpots;
        this.shows = shows;
    }

    /** The archer shoots (his bow); everyone else fights hand to hand. */
    public boolean shoots()
    {
        return this == ARCHER;
    }

    /** The horn-blower calls the others with his horn when the village turns. */
    public boolean blowsHorn()
    {
        return this == HORNBLOWER;
    }

    /** Its entity type's name ({@code dross:hunter_<key>}). */
    public String typeName()
    {
        return "hunter_" + this.key;
    }

    /** How many lines it says when you talk to it (lang keys {@code dross.hunter_folk.<key>.line.<n>}). */
    public int lines()
    {
        return this == ELDER ? FolkTuning.ELDER_LINES : FolkTuning.FOLK_LINES;
    }
}
