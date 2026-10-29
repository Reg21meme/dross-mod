package com.reg21meme.dross.boss.fire;

import java.util.List;

/**
 * Every fire mob sound (the Emberdactyl's and its young's, the Pallbearer's, the Cinder Hermit's), written by
 * {@code tools/fire/sounds.py} (don't edit by hand: change the sounds there and run it again). All of them are
 * original, synthesized by that tool. {@code /dross showcase boss_sounds emberdactyl} and
 * {@code /dross showcase mob_sounds cinder_wastes} list them in chat with a button that plays each one; the showcase
 * models play their animations' {@link #CUES}.
 */
public final class FireSounds
{
    /**
     * One sound event.
     *
     * @param group    the mob it belongs to (the heading it's listed under)
     * @param event    its id ({@code dross:<event>})
     * @param label    what it is
     * @param versions how many versions it picks from at random (each also its own event, {@code <event>.v<n>},
     *                 when there are several)
     * @param pick     which version the showcase models play: 0 each in turn (1, 2, 3, 1...), n always version n
     * @param about    a few words on each version (may be empty)
     */
    public record Sound(String group, String event, String label, int versions, int pick, List<String> about)
    {
        /** The event of version {@code n} (1 up), or the event itself when it has only one. */
        public String version(int n)
        {
            return this.versions > 1 ? this.event + ".v" + n : this.event;
        }

        /** The sound event {@code event} is, or null. */
        public static Sound of(String event)
        {
            return ALL.stream().filter(s -> s.event().equals(event)).findFirst().orElse(null);
        }
    }

    /** A sound one of a mob's animations makes, {@code tick} ticks into it (every time round, for a loop). */
    public record Cue(String mob, String anim, int tick, String event)
    {
    }

    public static final List<Sound> ALL = List.of(
            new Sound("Emberdactyl", "entity.emberdactyl.screech", "Its screech, a kaiju's, an octave deeper than its young's (the user's pick: 1)", 3, 1, List.of("the long call: up out of a growl, held and trilling, sinking into a roar", "two harsh barks, then the long scream", "a rattling throat-trill breaking into a long, falling, growling roar")),
            new Sound("Emberdactyl", "entity.emberdactyl.ambient", "A deep throaty rattle, a short deep squawk, a furnace breath with embers hissing and its beak clicking (3 versions)", 3, 0, List.of("", "", "")),
            new Sound("Emberdactyl", "entity.emberdactyl.flap", "One beat of its wings: a deep whump of air, the membrane snapping taut (2 versions)", 2, 0, List.of("", "")),
            new Sound("Emberdactyl", "entity.emberdactyl.gust", "The wing gust that sends out its fire tornadoes: a whump, a huge whoosh, a roar of flame (2 versions)", 2, 0, List.of("", "")),
            new Sound("Emberdactyl", "entity.emberdactyl.dive", "The dive: the air roaring and buffeting past it, a deep howl rising, its wings flaring open (2 versions)", 2, 0, List.of("", "")),
            new Sound("Emberdactyl", "entity.emberdactyl.snap", "Its obsidian beak snapping shut: a glassy clack and the jaw's weight (2 versions)", 2, 0, List.of("", "")),
            new Sound("Emberdactyl", "entity.emberdactyl.grab", "Its talons closing on you: a crunch, a heavy scrape (2 versions, in turn)", 2, 0, List.of("the talon crunch the user liked", "a deeper crunch of more pieces, a heavier weight")),
            new Sound("Emberdactyl", "entity.emberdactyl.hurt", "Hurt: a short harsh shriek over a roar (2 versions)", 2, 0, List.of("", "")),
            new Sound("Emberdactyl", "entity.emberdactyl.death", "Death: a last falling scream, the crash, a death rattle and its last breath", 1, 0, List.of("")),
            new Sound("Emberdactyl", "entity.emberdactyl.step", "A taloned foot the size of a cart on rock", 1, 0, List.of("")),
            new Sound("Emberdactyl", "entity.emberdactyl.break", "Its body cracking open and breaking apart (its death; 2 versions, in turn)", 2, 0, List.of("four slabs falling", "three slabs falling")),
            new Sound("Young Reborn", "entity.emberyoung.screech", "Its screech: a harsh squawk, the same squawk again, then the long scream (the user's, October 27)", 1, 0, List.of("")),
            new Sound("Young Reborn", "entity.emberyoung.hurt", "Hurt (2 versions)", 2, 0, List.of("", "")),
            new Sound("Young Reborn", "entity.emberyoung.flap", "One quick beat of its wings (2 versions)", 2, 0, List.of("", "")),
            new Sound("Young Reborn", "entity.emberyoung.harden", "Hardening into obsidian as it dies: a cooling hiss, crackle and ticks, a glassy ring", 1, 0, List.of("")),
            new Sound("Pallbearer", "entity.pallbearer.ambient", "A rattling groan, chains and a moan (2 versions)", 2, 0, List.of("", "")),
            new Sound("Pallbearer", "entity.pallbearer.drum", "The procession's funeral drum: three slow, deep beats", 1, 0, List.of("")),
            new Sound("Pallbearer", "entity.pallbearer.roar", "Its roar, evil and skeletal (the user's pick: 3, the demon choir)", 3, 3, List.of("the crypt roar: the jaw creaks open, a deep hollow roar with a ghost a tritone over it", "the death rattle: a gargling growl rising to a howl, teeth chattering, a hiss", "the demon choir: three hollow voices out of tune, a whisper, the jaw creaking shut")),
            new Sound("Pallbearer", "entity.pallbearer.swing", "Its backhand swing", 1, 0, List.of("")),
            new Sound("Pallbearer", "entity.pallbearer.slam", "The coffin's weight slammed down, embers bursting", 1, 0, List.of("")),
            new Sound("Pallbearer", "entity.pallbearer.hurt", "Hurt: a groan and rattling bones (2 versions)", 2, 0, List.of("", "")),
            new Sound("Pallbearer", "entity.pallbearer.death", "Death, an evil Halloween one (the user's pick: 3, the dying laugh, with the bells withering away)", 3, 3, List.of("the falling wail: a ghostly wail sinking away, his censers clanging, the last toll", "the bells' dirge: three tolls each a quarter-tone flatter over a deep moan, the last ringing on", "the dying laugh: an evil cackle with his bells, the collapse, the toll, the bells withering away and a last creak")),
            new Sound("Pallbearer", "entity.pallbearer.step", "A heavy foot in the ash", 1, 0, List.of("")),
            new Sound("Cinder Hermit", "entity.hermit.ambient", "Clicking mandibles, a bubbling (2 versions)", 2, 0, List.of("", "")),
            new Sound("Cinder Hermit", "entity.hermit.clang", "Its bronze bell struck (the stunning clang)", 1, 0, List.of("")),
            new Sound("Cinder Hermit", "entity.hermit.snap", "Its magma claw snapping shut (2 versions)", 2, 0, List.of("", "")),
            new Sound("Cinder Hermit", "entity.hermit.hide", "Pulling into its shell: a scrape and a hollow thunk", 1, 0, List.of("")),
            new Sound("Cinder Hermit", "entity.hermit.emerge", "Climbing out of its shell: a scrape, its legs tapping, the shell rocking", 1, 0, List.of("")),
            new Sound("Cinder Hermit", "entity.hermit.hurt", "Hurt: a chittering squeal", 1, 0, List.of("")),
            new Sound("Cinder Hermit", "entity.hermit.death", "Death: its clicks fading, its shell thunking down", 1, 0, List.of("")),
            new Sound("Cinder Hermit", "entity.hermit.step", "Its legs tapping the ground", 1, 0, List.of("")));

    public static final List<Cue> CUES = List.of(
            new Cue("emberdactyl", "hover", 6, "entity.emberdactyl.flap"),
            new Cue("emberdactyl", "fly", 8, "entity.emberdactyl.flap"),
            new Cue("emberdactyl", "dive", 2, "entity.emberdactyl.dive"),
            new Cue("emberdactyl", "screech", 12, "entity.emberdactyl.screech"),
            new Cue("emberdactyl", "gust", 27, "entity.emberdactyl.gust"),
            new Cue("emberdactyl", "grab", 15, "entity.emberdactyl.grab"),
            new Cue("emberdactyl", "snap", 7, "entity.emberdactyl.snap"),
            new Cue("emberdactyl", "walk", 0, "entity.emberdactyl.step"),
            new Cue("emberdactyl", "walk", 20, "entity.emberdactyl.step"),
            new Cue("emberdactyl", "death", 4, "entity.emberdactyl.death"),
            new Cue("emberdactyl", "death", 64, "entity.emberdactyl.break"),
            new Cue("emberyoung", "hover", 4, "entity.emberyoung.flap"),
            new Cue("emberyoung", "fly", 5, "entity.emberyoung.flap"),
            new Cue("emberyoung", "dive", 1, "entity.emberdactyl.dive"),
            new Cue("emberyoung", "screech", 8, "entity.emberyoung.screech"),
            new Cue("emberyoung", "gust", 17, "entity.emberdactyl.gust"),
            new Cue("emberyoung", "grab", 9, "entity.emberdactyl.grab"),
            new Cue("emberyoung", "snap", 4, "entity.emberdactyl.snap"),
            new Cue("emberyoung", "death", 2, "entity.emberyoung.hurt"),
            new Cue("emberyoung", "death", 36, "entity.emberyoung.harden"),
            new Cue("pallbearer", "walk", 0, "entity.pallbearer.step"),
            new Cue("pallbearer", "walk", 26, "entity.pallbearer.step"),
            new Cue("pallbearer", "walk", 0, "entity.pallbearer.drum"),
            new Cue("pallbearer", "swipe", 12, "entity.pallbearer.swing"),
            new Cue("pallbearer", "slam", 24, "entity.pallbearer.slam"),
            new Cue("pallbearer", "roar", 9, "entity.pallbearer.roar"),
            new Cue("pallbearer", "death", 0, "entity.pallbearer.death"),
            new Cue("hermit", "walk", 0, "entity.hermit.step"),
            new Cue("hermit", "walk", 10, "entity.hermit.step"),
            new Cue("hermit", "snap", 12, "entity.hermit.snap"),
            new Cue("hermit", "pinch", 7, "entity.hermit.snap"),
            new Cue("hermit", "pinch", 14, "entity.hermit.snap"),
            new Cue("hermit", "hide", 2, "entity.hermit.hide"),
            new Cue("hermit", "peek", 4, "entity.hermit.ambient"),
            new Cue("hermit", "come_out", 28, "entity.hermit.emerge"),
            new Cue("hermit", "death", 0, "entity.hermit.death"));

    private FireSounds() {}
}
