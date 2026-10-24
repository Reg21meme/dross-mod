package com.reg21meme.dross.boss.huntsman.folk;

import com.reg21meme.dross.boss.rimefall.RimefallAnims;

import java.util.List;

/**
 * The hunter villagers' animations ({@code animation.hunter_folk.<name>}, made by {@code tools/rimefall/mobs.py
 * hunter_folk}: the Huntsman's own on foot and their chop and talk). The gaits' speeds are the model's at the size the
 * game draws them ({@link FolkTuning#MODEL_SCALE}: the file's speeds are the Huntsman's).
 */
public final class FolkAnims
{
    public static final String MOB = "hunter_folk";
    public static final RimefallAnims.Anim IDLE = RimefallAnims.loop(MOB, "idle", 0.0D);
    public static final RimefallAnims.Anim WALK = RimefallAnims.loop(MOB, "walk", 2.02D * FolkTuning.MODEL_SCALE);
    public static final RimefallAnims.Anim RUN = RimefallAnims.loop(MOB, "run", 7.20D * FolkTuning.MODEL_SCALE);
    public static final RimefallAnims.Anim SPEAR_COMBO = RimefallAnims.once(MOB, "spear_combo", 38);
    public static final RimefallAnims.Anim THRUST_SWEEP = RimefallAnims.once(MOB, "thrust_sweep", 28);
    public static final RimefallAnims.Anim CHOP = RimefallAnims.once(MOB, "chop", 22);
    public static final RimefallAnims.Anim QUICK_SHOT = RimefallAnims.once(MOB, "quick_shot", 38);
    public static final RimefallAnims.Anim AIMED_SHOT = RimefallAnims.once(MOB, "aimed_shot", 58);
    public static final RimefallAnims.Anim HORN_BLOW = RimefallAnims.once(MOB, "horn_blow", 74);
    public static final RimefallAnims.Anim TALK = RimefallAnims.once(MOB, "talk", 40);
    public static final RimefallAnims.Anim DEATH = RimefallAnims.once(MOB, "death", 92);
    /** Every one, in order (their index is what's synced). */
    public static final List<RimefallAnims.Anim> ALL = List.of(IDLE, WALK, RUN, SPEAR_COMBO, THRUST_SWEEP, CHOP, QUICK_SHOT,
            AIMED_SHOT, HORN_BLOW, TALK, DEATH);
    /** The quick shot's arrow leaves the bow at this tick; the horn's call starts at this one. */
    public static final int SHOT_RELEASE = 23;
    public static final int HORN_CALL = 17;

    private FolkAnims() {}
}
