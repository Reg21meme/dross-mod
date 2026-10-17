package com.reg21meme.dross.boss.ratking;

import java.util.List;

/**
 * Every Rat King and rat sound, written by {@code tools/ratking/sounds.py} (don't edit by hand: change the sounds there
 * and run it again). All of them are original, synthesized by that tool. {@code /dross showcase boss_sounds ratking}
 * lists them in chat with a button that plays each one.
 */
public final class RatKingSounds
{
    /**
     * One sound event.
     *
     * @param group    the heading it's listed under
     * @param event    its id ({@code dross:<event>})
     * @param label    what it is
     * @param versions how many versions it picks from at random
     * @param used     true if the mod plays it now; the rest wait for the fights
     */
    public record Sound(String group, String event, String label, int versions, boolean used)
    {
    }

    /** Which train whistle his rage plays (1, 2 or 3; all three are in the showcase). */
    public static final int RAGE_WHISTLE = 1;

    public static final List<Sound> ALL = List.of(
            new Sound("Rat King: voice", "entity.rat_king.ambient", "Sly snickers, sniffs, muttering and a cackle (4 versions)", 4, true),
            new Sound("Rat King: voice", "entity.rat_king.hurt", "Hurt (3 versions)", 3, true),
            new Sound("Rat King: voice", "entity.rat_king.death", "The silly death's long squeal", 1, true),
            new Sound("Rat King: voice", "entity.rat_king.startle", "Jolted awake: 'eep!' and 'huh?!'", 2, true),
            new Sound("Rat King: voice", "entity.rat_king.yawn", "A big yawn", 1, true),
            new Sound("Rat King: voice", "entity.rat_king.snore", "A snore with a little whistle (every 4 seconds while he sleeps)", 1, true),
            new Sound("Rat King: rage", "entity.rat_king.screech", "The rage's screech", 1, true),
            new Sound("Rat King: rage", "entity.rat_king.rage_whistle_1", "Train whistle 1: 'choo-choo-chooo' (with his steam puffs)", 1, true),
            new Sound("Rat King: rage", "entity.rat_king.rage_whistle_2", "Train whistle 2: one long wailing blast", 1, true),
            new Sound("Rat King: rage", "entity.rat_king.rage_whistle_3", "Train whistle 3: a shrill toy train", 1, true),
            new Sound("Rat King: rage", "entity.rat_king.stomp", "Tantrum stomps", 2, true),
            new Sound("Rat King: rage", "entity.rat_king.staff_slam", "The staff slam and the orb ringing", 1, true),
            new Sound("Rat King: moves", "entity.rat_king.step", "Soft padded footsteps", 3, true),
            new Sound("Rat King: moves", "entity.rat_king.dig", "Clawing at the dirt", 2, true),
            new Sound("Rat King: moves", "entity.rat_king.dive", "Diving head first into the ground", 1, true),
            new Sound("Rat King: moves", "entity.rat_king.rumble", "The mound trembling before he pops up", 1, true),
            new Sound("Rat King: moves", "entity.rat_king.pop", "Popping up: pop, dirt and a 'ta-da'", 2, true),
            new Sound("Rat King: moves", "entity.rat_king.staff_grab", "Picking up his staff", 1, true),
            new Sound("Rat King: moves", "entity.rat_king.thud", "Falling flat on his back (with a silly boing)", 1, true),
            new Sound("Rat King: moves", "entity.rat_king.staff_clatter", "His staff clattering down", 1, true),
            new Sound("Rat King: moves", "entity.rat_king.crown_clink", "His crown landing on his belly", 1, true),
            new Sound("Rat King: moves", "entity.rat_king.charge", "The spear charge: a war-cry squeak or a rising squeal, scurrying claws, the air rushing (2 versions)", 2, true),
            new Sound("Rat King: attacks (for the fight; pick your favourites)", "entity.rat_king.attack.swipe_1", "Claw swipe, version 1", 1, false),
            new Sound("Rat King: attacks (for the fight; pick your favourites)", "entity.rat_king.attack.swipe_2", "Claw swipe, version 2", 1, false),
            new Sound("Rat King: attacks (for the fight; pick your favourites)", "entity.rat_king.attack.swipe_3", "Claw swipe, version 3", 1, false),
            new Sound("Rat King: attacks (for the fight; pick your favourites)", "entity.rat_king.attack.bite_1", "Bite, version 1", 1, false),
            new Sound("Rat King: attacks (for the fight; pick your favourites)", "entity.rat_king.attack.bite_2", "Bite, version 2", 1, false),
            new Sound("Rat King: attacks (for the fight; pick your favourites)", "entity.rat_king.attack.bite_3", "Bite, version 3", 1, false),
            new Sound("Rat King: attacks (for the fight; pick your favourites)", "entity.rat_king.attack.staff_1", "Staff strike, version 1", 1, false),
            new Sound("Rat King: attacks (for the fight; pick your favourites)", "entity.rat_king.attack.staff_2", "Staff strike, version 2", 1, false),
            new Sound("Rat King: attacks (for the fight; pick your favourites)", "entity.rat_king.attack.staff_3", "Staff strike, version 3", 1, false),
            new Sound("Rat King: attacks (for the fight; pick your favourites)", "entity.rat_king.attack.orb_1", "Plague orb spell, version 1", 1, false),
            new Sound("Rat King: attacks (for the fight; pick your favourites)", "entity.rat_king.attack.orb_2", "Plague orb spell, version 2", 1, false),
            new Sound("Rat King: attacks (for the fight; pick your favourites)", "entity.rat_king.attack.orb_3", "Plague orb spell, version 3", 1, false),
            new Sound("Rat King: attacks (for the fight; pick your favourites)", "entity.rat_king.attack.summon_1", "Summoning his rats, version 1", 1, false),
            new Sound("Rat King: attacks (for the fight; pick your favourites)", "entity.rat_king.attack.summon_2", "Summoning his rats, version 2", 1, false),
            new Sound("Rat King: attacks (for the fight; pick your favourites)", "entity.rat_king.attack.summon_3", "Summoning his rats, version 3", 1, false),
            new Sound("Rat King: attacks (for the fight; pick your favourites)", "entity.rat_king.attack.screech_1", "Sonic screech, version 1", 1, false),
            new Sound("Rat King: attacks (for the fight; pick your favourites)", "entity.rat_king.attack.screech_2", "Sonic screech, version 2", 1, false),
            new Sound("Rat King: attacks (for the fight; pick your favourites)", "entity.rat_king.attack.tail_whip_1", "Tail whip, version 1", 1, false),
            new Sound("Rat King: attacks (for the fight; pick your favourites)", "entity.rat_king.attack.tail_whip_2", "Tail whip, version 2", 1, false),
            new Sound("Rat King: attacks (for the fight; pick your favourites)", "entity.rat_king.attack.plague_breath_1", "Plague breath, version 1", 1, false),
            new Sound("Rat King: attacks (for the fight; pick your favourites)", "entity.rat_king.attack.plague_breath_2", "Plague breath, version 2", 1, false),
            new Sound("Plague Rat", "entity.rat.ambient", "Squeaks, sniffs and chittering (4 versions)", 4, true),
            new Sound("Plague Rat", "entity.rat.hurt", "Hurt (2 versions)", 2, true),
            new Sound("Plague Rat", "entity.rat.death", "Death", 1, true),
            new Sound("Plague Rat", "entity.rat.step", "Tiny pattering feet", 2, true),
            new Sound("Plague Rat", "entity.rat.attack", "Bite (2 versions, for the fight)", 2, false),
            new Sound("Plague Ravager", "entity.enraged_rat.ambient", "Growls, hissing snarls and a sick gurgle (3 versions)", 3, true),
            new Sound("Plague Ravager", "entity.enraged_rat.hurt", "Hurt (2 versions)", 2, true),
            new Sound("Plague Ravager", "entity.enraged_rat.death", "Death", 1, true),
            new Sound("Plague Ravager", "entity.enraged_rat.hiss", "Hiss (2 versions, for the fight)", 2, false),
            new Sound("Plague Ravager", "entity.enraged_rat.step", "Heavier pattering feet", 2, true),
            new Sound("Plague Ravager", "entity.enraged_rat.attack", "Snarling bites (3 versions, for the fight)", 3, false));

    private RatKingSounds() {}
}
