package com.reg21meme.dross.boss.frostfolk;

import com.reg21meme.dross.boss.rimefall.RimefallAnims;

import java.util.List;

/**
 * The Frostfolk's animations ({@code animation.frostfolk.<name>}, made by {@code tools/rimefall/mobs.py frostfolk}: keyed
 * by hand, blocky like Minecraft's villagers). Each calling carries its tool one of a few ways ({@link Carry}), and its
 * idle, walk, run, talk and chop are made for it, so the tool stays in its fist and out of its sleeve (the user, October
 * 24, 2026: "the items go through the arms when they should be in their hands"). They die the vanilla way (no death
 * animation of their own: the user, "just the regular death animation"). The gaits' speeds are the blocks a second their
 * feet cover at the animation's own pace (the generator's meta "speed").
 */
public final class FolkAnims
{
    public static final String MOB = "frostfolk";
    private static final double WALK_SPEED = 1.41D;
    private static final double RUN_SPEED = 3.71D;
    private static final int TALK_TICKS = 40;
    private static final int CHOP_TICKS = 22;

    // nothing in its hands (the archer, the tanner, the elder)
    public static final RimefallAnims.Anim IDLE = RimefallAnims.loop(MOB, "idle", 0.0D);
    public static final RimefallAnims.Anim WALK = RimefallAnims.loop(MOB, "walk", WALK_SPEED);
    public static final RimefallAnims.Anim RUN = RimefallAnims.loop(MOB, "run", RUN_SPEED);
    public static final RimefallAnims.Anim TALK = RimefallAnims.once(MOB, "talk", TALK_TICKS);
    public static final RimefallAnims.Anim CHOP = RimefallAnims.once(MOB, "chop", CHOP_TICKS);
    // a long tool stood upright in front of its arm (spears, the pike, the crook, the hoe, the rod)
    public static final RimefallAnims.Anim IDLE_POLE = RimefallAnims.loop(MOB, "idle_pole", 0.0D);
    public static final RimefallAnims.Anim WALK_POLE = RimefallAnims.loop(MOB, "walk_pole", WALK_SPEED);
    public static final RimefallAnims.Anim RUN_POLE = RimefallAnims.loop(MOB, "run_pole", RUN_SPEED);
    public static final RimefallAnims.Anim TALK_POLE = RimefallAnims.once(MOB, "talk_pole", TALK_TICKS);
    public static final RimefallAnims.Anim CHOP_POLE = RimefallAnims.once(MOB, "chop_pole", CHOP_TICKS);
    // a short one held out ahead of its fist (the axe, the cleaver)
    public static final RimefallAnims.Anim IDLE_HANG = RimefallAnims.loop(MOB, "idle_hang", 0.0D);
    public static final RimefallAnims.Anim WALK_HANG = RimefallAnims.loop(MOB, "walk_hang", WALK_SPEED);
    public static final RimefallAnims.Anim RUN_HANG = RimefallAnims.loop(MOB, "run_hang", RUN_SPEED);
    public static final RimefallAnims.Anim TALK_HANG = RimefallAnims.once(MOB, "talk_hang", TALK_TICKS);
    public static final RimefallAnims.Anim CHOP_HANG = RimefallAnims.once(MOB, "chop_hang", CHOP_TICKS);
    /** The elder's chop: he draws the sword off his back for it. */
    public static final RimefallAnims.Anim CHOP_SWORD = RimefallAnims.once(MOB, "chop_sword", CHOP_TICKS);

    public static final RimefallAnims.Anim SPEAR_COMBO = RimefallAnims.once(MOB, "spear_combo", 38);
    public static final RimefallAnims.Anim THRUST_SWEEP = RimefallAnims.once(MOB, "thrust_sweep", 28);
    public static final RimefallAnims.Anim QUICK_SHOT = RimefallAnims.once(MOB, "quick_shot", 38);
    public static final RimefallAnims.Anim AIMED_SHOT = RimefallAnims.once(MOB, "aimed_shot", 58);
    public static final RimefallAnims.Anim HORN_BLOW = RimefallAnims.once(MOB, "horn_blow", 74);
    /** Every one, in order (their index is what's synced). */
    public static final List<RimefallAnims.Anim> ALL = List.of(IDLE, WALK, RUN, SPEAR_COMBO, THRUST_SWEEP, CHOP, QUICK_SHOT,
            AIMED_SHOT, HORN_BLOW, TALK, IDLE_POLE, WALK_POLE, RUN_POLE, TALK_POLE, CHOP_POLE, IDLE_HANG, WALK_HANG, RUN_HANG,
            TALK_HANG, CHOP_HANG, CHOP_SWORD);
    /** The quick shot's arrow leaves the bow at this tick; the horn's call starts at this one. */
    public static final int SHOT_RELEASE = 23;
    public static final int HORN_CALL = 17;

    /** How a calling carries what it holds, and the idle, walk, run, talk and chop made for it (the generator's
     * {@code STYLE}). */
    public enum Carry
    {
        FREE(IDLE, WALK, RUN, TALK, CHOP),
        POLE(IDLE_POLE, WALK_POLE, RUN_POLE, TALK_POLE, CHOP_POLE),
        HANG(IDLE_HANG, WALK_HANG, RUN_HANG, TALK_HANG, CHOP_HANG),
        /** The elder: nothing in his hands, his sword on his back until he strikes. */
        SWORD(IDLE, WALK, RUN, TALK, CHOP_SWORD);

        public final RimefallAnims.Anim idle;
        public final RimefallAnims.Anim walk;
        public final RimefallAnims.Anim run;
        public final RimefallAnims.Anim talk;
        public final RimefallAnims.Anim chop;

        Carry(RimefallAnims.Anim idle, RimefallAnims.Anim walk, RimefallAnims.Anim run, RimefallAnims.Anim talk,
              RimefallAnims.Anim chop)
        {
            this.idle = idle;
            this.walk = walk;
            this.run = run;
            this.talk = talk;
            this.chop = chop;
        }
    }

    private FolkAnims() {}
}
