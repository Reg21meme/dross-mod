package com.reg21meme.dross.boss.frostfolk;

import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.rimefall.RimefallAnims;

import java.util.List;
import java.util.Set;

/**
 * The Frostfolk's callings (the user, October 23, 2026: "we have a spear and circular shield one, an axe one, a long
 * spear one, a bow and arrow one, then a horn dude with the flag ... make the basic dudes, not the warriors, do 5 ...
 * also add an elder who has scarred eyes and a beard and is the one with a sword; this guy has 50 health"; their look,
 * October 24: a pillager's build, blue hoods in different styles). Each is its own entity type
 * ({@code dross:frostfolk_<key>}); a spawn egg for each kind (warriors, workers, the elder); all of them are one model
 * ({@code tools/rimefall/rimemobs/frostfolk.py}), each with its own texture and showing only its own hood style, cape
 * and tools ({@link #shows}).
 */
public enum FolkRole
{
    // warriors: 25 to 30 health
    SPEARMAN("spearman", Kind.WARRIOR, 30.0D, 4.0D, 4.0D, Attack.COMBO,
            Set.of("hood_peak", "cape_pelt", "spear", "shield")),
    AXEMAN("axeman", Kind.WARRIOR, 28.0D, 6.0D, 3.0D, Attack.CHOP,
            Set.of("hood_furtrim", "cape_pelt", "axe")),
    PIKEMAN("pikeman", Kind.WARRIOR, 28.0D, 5.0D, 3.0D, Attack.PIKE,
            Set.of("hood_point", "cape_pelt", "long_spear")),
    ARCHER("archer", Kind.WARRIOR, 25.0D, 3.0D, 2.0D, Attack.CHOP,
            Set.of("hood_cowl", "drape", "bow", "quiver")),
    HORNBLOWER("hornblower", Kind.WARRIOR, 26.0D, 4.0D, 3.0D, Attack.THRUST_SWEEP,
            Set.of("hood_ears", "cape_pelt", "spear", "horn_belt", "horn_held", "flag")),
    // workers: they fight with their tools, weakly
    FARMER("farmer", Kind.WORKER, 20.0D, 3.0D, 0.0D, Attack.CHOP,
            Set.of("hood_horn", "hoe", "basket")),
    BUTCHER("butcher", Kind.WORKER, 20.0D, 4.0D, 0.0D, Attack.CHOP,
            Set.of("cleaver", "apron")),
    HERDER("herder", Kind.WORKER, 20.0D, 3.0D, 0.0D, Attack.CHOP,
            Set.of("hood_flaps", "crook")),
    FISHER("fisher", Kind.WORKER, 20.0D, 2.0D, 0.0D, Attack.CHOP,
            Set.of("hood_brim", "rod")),
    TANNER("tanner", Kind.WORKER, 20.0D, 3.0D, 0.0D, Attack.CHOP,
            Set.of("fringe", "scraper", "hide_roll")),
    // the elder: the one you talk to
    ELDER("elder", Kind.ELDER, 50.0D, 7.0D, 4.0D, Attack.CHOP,
            Set.of("hood_tail", "beard", "cape_long", "sword"));

    /** Warriors, workers, the elder; each kind has one spawn egg ({@link FrostfolkSpawnEggItem}: a random calling of
     * it), in their hoods' blue with its own spots. */
    public enum Kind
    {
        WARRIOR(DrossColors.FROSTFOLK_WARRIOR_EGG_SPOTS), WORKER(DrossColors.FROSTFOLK_WORKER_EGG_SPOTS),
        ELDER(DrossColors.FROSTFOLK_ELDER_EGG_SPOTS);

        public final int eggBase = DrossColors.FROSTFOLK_EGG_BASE;
        public final int eggSpots;

        Kind(int eggSpots)
        {
            this.eggSpots = eggSpots;
        }
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

    /** Every bone of the model only some callings show (its hood styles, capes and tools; the others hide it). */
    public static final List<String> GEAR = List.of("hood_peak", "hood_furtrim", "hood_point", "hood_cowl", "hood_ears",
            "hood_horn", "hood_flaps", "hood_brim", "hood_tail", "beard", "cape_pelt", "cape_long", "drape", "fringe", "apron",
            "quiver", "flag", "hide_roll", "horn_belt", "spear", "long_spear", "axe", "sword", "hoe", "cleaver", "crook", "rod",
            "scraper", "bow", "basket", "horn_held", "shield");

    public final String key;
    public final Kind kind;
    public final double health;
    public final double damage;
    public final double armor;
    public final Attack attack;
    public final Set<String> shows;

    FolkRole(String key, Kind kind, double health, double damage, double armor, Attack attack, Set<String> shows)
    {
        this.key = key;
        this.kind = kind;
        this.health = health;
        this.damage = damage;
        this.armor = armor;
        this.attack = attack;
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

    /** Its entity type's name ({@code dross:frostfolk_<key>}). */
    public String typeName()
    {
        return "frostfolk_" + this.key;
    }

    /** How many lines it says when you talk to it (lang keys {@code dross.frostfolk.<key>.line.<n>}). */
    public int lines()
    {
        return switch (this)
        {
            case ELDER -> FolkTuning.ELDER_LINES;
            case SPEARMAN -> FolkTuning.SPEARMAN_LINES;
            case AXEMAN -> FolkTuning.AXEMAN_LINES;
            case PIKEMAN -> FolkTuning.PIKEMAN_LINES;
            case ARCHER -> FolkTuning.ARCHER_LINES;
            case FISHER -> FolkTuning.FISHER_LINES;
            default -> FolkTuning.FOLK_LINES;
        };
    }
}
