package com.reg21meme.dross.boss.huntsman;

import java.util.List;

/**
 * Every sound of the Huntsman and the Alpha Moose and all the versions that were made of each, written by
 * {@code tools/huntsman/sounds.py} (don't edit by hand: change the sounds there and run it again). All of them are
 * original, synthesized by that tool; an event plays the versions the user picked ({@code picked}; all of them while
 * nothing is picked). {@link HuntSoundCues} plays them in the fight; {@code /dross showcase boss_sounds huntsman} lists
 * them in chat with a button for each version.
 */
public final class HuntsmanSounds
{
    /**
     * One sound event and its versions.
     *
     * @param group    the heading it's listed under
     * @param event    its id ({@code dross:<event>}; played, it picks one of the picked versions at random)
     * @param label    what it is
     * @param picks    each version on its own: its event id ({@code <event>.v<n>}), so a button can play just it
     * @param versions what each version is, in the same order
     * @param picked   the versions the user picked (numbered from 1), empty if they haven't yet
     */
    public record Sound(String group, String event, String label, List<String> picks, List<String> versions,
                        List<Integer> picked)
    {
    }

    public static final List<Sound> ALL = List.of(
            new Sound("The Huntsman: his voice", "entity.huntsman.cry", "War cry, when he spots you",
                    List.of("entity.huntsman.cry.v1", "entity.huntsman.cry.v2", "entity.huntsman.cry.v3"),
                    List.of("a bark and a big shout: hah-HAAAH!", "a man's wolf howl", "a growl rising into a roar"),
                    List.of(2)),
            new Sound("The Huntsman: his voice", "entity.huntsman.ambient", "Idle",
                    List.of("entity.huntsman.ambient.v1", "entity.huntsman.ambient.v2", "entity.huntsman.ambient.v3"),
                    List.of("a slow frosty breath and a low hm", "sniffing out your trail, then a growl", "a low hunting hum"),
                    List.of(1)),
            new Sound("The Huntsman: his voice", "entity.huntsman.effort", "The grunt of a blow (spear thrusts, the leap)",
                    List.of("entity.huntsman.effort.v1", "entity.huntsman.effort.v2", "entity.huntsman.effort.v3"),
                    List.of("hup!", "hah!", "hyah!"),
                    List.of()),
            new Sound("The Huntsman: his voice", "entity.huntsman.hurt", "Hurt",
                    List.of("entity.huntsman.hurt.v1", "entity.huntsman.hurt.v2", "entity.huntsman.hurt.v3"),
                    List.of("ugh!", "agh! and a hiss through his teeth", "oof, winded"),
                    List.of()),
            new Sound("The Huntsman: his voice", "entity.huntsman.death", "Death",
                    List.of("entity.huntsman.death.v1", "entity.huntsman.death.v2", "entity.huntsman.death.v3"),
                    List.of("aaagh... falling away, his last breath", "nngh... aaah, letting go", "a cry, a gasp, a long sigh"),
                    List.of()),
            new Sound("The Huntsman: his voice", "entity.huntsman.step", "Footsteps",
                    List.of("entity.huntsman.step.v1", "entity.huntsman.step.v2"),
                    List.of("a boot in the snow", "a softer one"),
                    List.of(1)),
            new Sound("The Huntsman: his voice", "entity.huntsman.land", "Landing after a leap",
                    List.of("entity.huntsman.land.v1", "entity.huntsman.land.v2"),
                    List.of("landing in the snow", "landing, more frost"),
                    List.of(1)),
            new Sound("The Huntsman: his weapons", "entity.huntsman.spear_swing", "Spear: the swing (1 a thrust, 2 a sweep)",
                    List.of("entity.huntsman.spear_swing.v1", "entity.huntsman.spear_swing.v2"),
                    List.of("a thrust", "a wide sweep"),
                    List.of(1, 2)),
            new Sound("The Huntsman: his weapons", "entity.huntsman.spear_hit", "Spear: a hit, frost bursting",
                    List.of("entity.huntsman.spear_hit.v1", "entity.huntsman.spear_hit.v2"),
                    List.of("a deep stab", "a brighter one"),
                    List.of(1)),
            new Sound("The Huntsman: his weapons", "entity.huntsman.spear_throw", "Spear: the throw",
                    List.of("entity.huntsman.spear_throw.v1", "entity.huntsman.spear_throw.v2"),
                    List.of("the throw", "the throw, its rope paying out"),
                    List.of(1)),
            new Sound("The Huntsman: his weapons", "entity.huntsman.bow_draw", "Bow: the draw",
                    List.of("entity.huntsman.bow_draw.v1", "entity.huntsman.bow_draw.v2"),
                    List.of("an arrow from the quiver, a long creak", "a shorter draw"),
                    List.of(1)),
            new Sound("The Huntsman: his weapons", "entity.huntsman.bow_shot", "Bow: the shot",
                    List.of("entity.huntsman.bow_shot.v1", "entity.huntsman.bow_shot.v2", "entity.huntsman.bow_shot.v3"),
                    List.of("a twang", "a higher twang", "a deep twang"),
                    List.of(3)),
            new Sound("The Huntsman: his weapons", "entity.huntsman.arrow_rain", "The arrow rain coming down",
                    List.of("entity.huntsman.arrow_rain.v1", "entity.huntsman.arrow_rain.v2"),
                    List.of("arrows whistling down round you", "a heavier storm"),
                    List.of(2)),
            new Sound("The Huntsman: his weapons", "entity.huntsman.arrow_hit", "One of his arrows hitting you (the frost slows you)",
                    List.of("entity.huntsman.arrow_hit.v1", "entity.huntsman.arrow_hit.v2"),
                    List.of("a thwack and frost spreading", "a shorter frost"),
                    List.of(1)),
            new Sound("The Huntsman: his weapons", "entity.huntsman.bola_whirl", "Bola: whirled overhead",
                    List.of("entity.huntsman.bola_whirl.v1", "entity.huntsman.bola_whirl.v2"),
                    List.of("whirling", "whirling faster"),
                    List.of(1)),
            new Sound("The Huntsman: his weapons", "entity.huntsman.bola_throw", "Bola: thrown and snaring you",
                    List.of("entity.huntsman.bola_throw.v1", "entity.huntsman.bola_throw.v2"),
                    List.of("thrown, wrapping, ice clacking", "the same, with a thump"),
                    List.of(2)),
            new Sound("The Alpha Moose", "entity.alpha_moose.bellow", "The bellow (its roar)",
                    List.of("entity.alpha_moose.bellow.v1", "entity.alpha_moose.bellow.v2", "entity.alpha_moose.bellow.v3"),
                    List.of("a huge, long nasal bellow", "a rutting bull's grunting roar", "a wild bugling call"),
                    List.of(3, 1)),
            new Sound("The Alpha Moose", "entity.alpha_moose.rear", "Its call as it rears (the split)",
                    List.of("entity.alpha_moose.rear.v1", "entity.alpha_moose.rear.v2", "entity.alpha_moose.rear.v3"),
                    List.of("a screaming neigh", "a deeper, rougher bray", "three rising brays"),
                    List.of(1)),
            new Sound("The Alpha Moose", "entity.alpha_moose.ambient", "Idle",
                    List.of("entity.alpha_moose.ambient.v1", "entity.alpha_moose.ambient.v2", "entity.alpha_moose.ambient.v3"),
                    List.of("deep croaky grunts", "a big frosty snort", "a low wavering moan"),
                    List.of(1, 2)),
            new Sound("The Alpha Moose", "entity.alpha_moose.hurt", "Hurt",
                    List.of("entity.alpha_moose.hurt.v1", "entity.alpha_moose.hurt.v2"),
                    List.of("a rough bray", "a deeper one"),
                    List.of(1)),
            new Sound("The Alpha Moose", "entity.alpha_moose.death", "Death",
                    List.of("entity.alpha_moose.death.v1", "entity.alpha_moose.death.v2"),
                    List.of("a last bellow and the crash", "a long breaking moan"),
                    List.of(2)),
            new Sound("The Alpha Moose", "entity.alpha_moose.gallop", "Gallop",
                    List.of("entity.alpha_moose.gallop.v1", "entity.alpha_moose.gallop.v2"),
                    List.of("one stride of its gallop", "a slower, heavier stride"),
                    List.of(2)),
            new Sound("The Alpha Moose", "entity.alpha_moose.step", "Footsteps",
                    List.of("entity.alpha_moose.step.v1", "entity.alpha_moose.step.v2"),
                    List.of("a heavy hoof", "a lighter one"),
                    List.of(1)),
            new Sound("The Alpha Moose", "entity.alpha_moose.kick", "The back kick",
                    List.of("entity.alpha_moose.kick.v1", "entity.alpha_moose.kick.v2"),
                    List.of("the back kick", "the back kick, snow flying"),
                    List.of(2)),
            new Sound("The Alpha Moose", "entity.alpha_moose.ram", "The ram and the antler toss",
                    List.of("entity.alpha_moose.ram.v1", "entity.alpha_moose.ram.v2"),
                    List.of("a croak and the antlers' clack", "a deeper clack"),
                    List.of(2)),
            new Sound("The Alpha Moose", "entity.alpha_moose.stomp", "The stomp",
                    List.of("entity.alpha_moose.stomp.v1", "entity.alpha_moose.stomp.v2"),
                    List.of("both front hooves down", "the same, the ice cracking"),
                    List.of(2)),
            new Sound("The Alpha Moose", "entity.alpha_moose.crack_stamp", "The cracking stamp",
                    List.of("entity.alpha_moose.crack_stamp.v1", "entity.alpha_moose.crack_stamp.v2"),
                    List.of("three paws and the stamp, cracks running", "more cracks"),
                    List.of(1)),
            new Sound("The Alpha Moose", "entity.alpha_moose.ice_spikes", "The clear ice spikes bursting up",
                    List.of("entity.alpha_moose.ice_spikes.v1", "entity.alpha_moose.ice_spikes.v2"),
                    List.of("small spikes, then the big one", "more small spikes"),
                    List.of(1)));

    private HuntsmanSounds() {}
}
