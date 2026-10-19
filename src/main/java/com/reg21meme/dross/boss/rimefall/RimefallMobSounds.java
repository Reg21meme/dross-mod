package com.reg21meme.dross.boss.rimefall;

import java.util.List;

/**
 * Every Rimefall animal sound (the Rimehorn's, the moose's and the Pond Skater's), written by
 * {@code tools/rimefall/sounds.py} (don't edit by hand: change the sounds there and run it again). All of them are
 * original, synthesized by that tool. {@code /dross showcase mob_sounds rimefall} lists them in chat with a button that
 * plays each one.
 */
public final class RimefallMobSounds
{
    /**
     * One sound event.
     *
     * @param group    the animal it belongs to (the heading it's listed under)
     * @param event    its id ({@code dross:<event>})
     * @param label    what it is
     * @param versions how many versions it picks from at random
     */
    public record Sound(String group, String event, String label, int versions)
    {
    }

    public static final List<Sound> ALL = List.of(
            new Sound("Rimehorn", "entity.rimehorn.ambient", "Chuffs and a rumble, a long growl, a frosty snort, a deep \"hmmph\" (4 versions)", 4),
            new Sound("Rimehorn", "entity.rimehorn.hurt", "Hurt: a gruff roar and a chuff (2 versions)", 2),
            new Sound("Rimehorn", "entity.rimehorn.death", "Death: a sinking roar, a last frosty breath, a heavy fall", 1),
            new Sound("Rimehorn", "entity.rimehorn.step", "Deep, heavy footfalls on packed snow", 2),
            new Sound("Rimehorn", "entity.rimehorn.eat", "Crunching snow and a low rumble", 2),
            new Sound("Rimehorn", "entity.rimehorn.flick", "The horn flick: a chuff, a deep heave and the air (2 versions)", 2),
            new Sound("Rimehorn", "entity.rimehorn.rush", "The rush: a roar and thundering heavy feet (2 versions)", 2),
            new Sound("Moose", "entity.moose.ambient", "A cow's long nasal wail, a bull's croaks, a nasal honk, a snuffle (4 versions)", 4),
            new Sound("Moose", "entity.moose.hurt", "Hurt: a wavering bleat (2 versions)", 2),
            new Sound("Moose", "entity.moose.death", "Death: a long wavering moan", 1),
            new Sound("Moose", "entity.moose.step", "Light, woody hoof clops on snow", 2),
            new Sound("Moose", "entity.moose.eat", "Nibbling and lip smacks", 2),
            new Sound("Moose", "entity.moose.charge", "The charge: a rising nasal bellow and clopping hooves (2 versions)", 2),
            new Sound("Moose", "entity.moose.attack", "The ram: a croak and the hollow clack of its antlers (2 versions)", 2),
            new Sound("Pond Skater", "entity.pondskater.ambient", "Chittering clicks, a dry rasp, a buzz, taps (4 versions)", 4),
            new Sound("Pond Skater", "entity.pondskater.hurt", "Hurt (2 versions)", 2),
            new Sound("Pond Skater", "entity.pondskater.death", "Death: a dying buzz and a crack", 1),
            new Sound("Pond Skater", "entity.pondskater.step", "Its legs tapping the ground", 2),
            new Sound("Pond Skater", "entity.pondskater.skate", "Gliding over the ice (2 versions)", 2),
            new Sound("Pond Skater", "entity.pondskater.attack", "The stab: a hiss and a sharp strike (2 versions)", 2));

    private RimefallMobSounds() {}
}
