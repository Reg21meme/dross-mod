"""
Writes the two generated Java design lists the mod reads (boss/ratking/RatKingDesigns.java and RatDesigns.java):
names for the showcase signs, and every animation timing the mod's particles and effects follow, in ticks.
"""
KING_TEMPLATE = '''package com.reg21meme.dross.boss.ratking;

import java.util.List;

/**
 * The Rat King's design candidates, written by {{@code tools/ratking/build.py}} (don't edit by hand: change the designs
 * in {{@code tools/ratking/ratdesigns/}} and run the build again). The showcase spawns one Rat King per design; the
 * renderer finds each design's files by its {{@link Design#id()}}.
 * <p>
 * Times are ticks from the start of each animation (GeckoLib starts it {{@code RatKing.ANIMATION_BLEND_TICKS}} after the
 * action does); the mod times its particles, effects and sounds to them: the Zzz and snores while asleep, the startle
 * and the yawn, the dirt flying while he digs and dives and when he bursts out, his eyes turning red, the train whistle
 * and the steam out of his ears in the rage, the thud, the staff's clatter and the crown's clink in the death; and in
 * the fight, the sweep's hit, the moment (and the spot) his cast lets the poison ball go, and his spear charge's dash
 * (its speed and reach).
 */
public final class RatKingDesigns
{{
    /**
     * One design.
     *
     * @param number      the showcase's order (1, 2, 3 ...)
     * @param label       what its sign says after "No. "
     * @param id          its files' name ({{@code geo/entity/rat_king/<id>.geo.json}} ...)
     * @param name        its working name (the sign's second line)
     * @param mood        a few words about it (the sign's third line)
     * @param curledSleep true: he sleeps curled up on the ground (his staff beside him); false: slumped on his staff
     * @param hasGlow     true if its {{@code <id>_glow.png}} has anything in it (an orb, gems, candle flames, a censer)
     * @param slamForward where his staff's foot strikes the ground in the rage: blocks in front of him...
     * @param slamRight   ...and to his right
     * @param runBlocks   how far one loop of his run would carry him (blocks): play it at
     *                    (blocks a tick he moves) / (runBlocks / runTicks) times its speed so his feet don't slide
     * @param runTicks    the run loop's length
     * @param sweepTicks  the sweep's length (his close-range staff blow)
     * @param sweepHit    when the staff passes his front: the moment it hits
     * @param sweepReach  how far from his middle the staff's orb reaches at the hit (blocks)
     * @param castTicks   the cast's length (he launches a poison ball)
     * @param castRelease when the ball leaves the staff's orb
     * @param castForward where it leaves from (the orb at the release): blocks in front of him...
     * @param castUp      ...above his feet...
     * @param castRight   ...and to his right
     * @param popupHoleForward how far in front of his feet (blocks) the hole he pops up out of is: right under him
     *                    (under his head as he stands straight up, so he comes straight up out of it)
     * @param chargeStartTicks the spear charge's wind-up animation (it follows GeckoLib's blend; the mod's wind-up is
     *                    RatKingTuning.CHARGE_WINDUP_TICKS, from the start of the move)
     * @param chargeBlocks how far one loop of his dash would carry him (blocks): play it at
     *                    (blocks a tick he moves) / (chargeBlocks / chargeTicks) times its speed so his feet don't slide
     * @param chargeTicks the dash loop's length
     * @param chargeReach how far in front of his middle (blocks) the staff's orb leads in the dash: the charge hits
     *                    what's within it
     * @param chargeSkidTicks the skid when the charge misses (it follows GeckoLib's blend; the mod's recovery is
     *                    RatKingTuning.CHARGE_RECOVER_TICKS)
     */
    public record Design(int number, String label, String id, String name, String mood, boolean curledSleep,
                         boolean hasGlow, int wakeTicks, int wakeStartle, int burrowTicks, int digFrom, int digTo,
                         int dive, int popupTicks, int popupPeek, int popupBurst, int popupLand, int rageTicks,
                         int rageEyes, List<Integer> rageSteam, int rageSlam, List<Integer> rageStomps, int deathTicks,
                         int deathFall, int deathTongue, int deathXEyes, float walkBlocks, int walkTicks,
                         int sleepTicks, int wakeYawn, int wakeGrab, int rageScreech, int deathClatter,
                         int deathCrown, float slamForward, float slamRight, float runBlocks, int runTicks,
                         int sweepTicks, int sweepHit, float sweepReach, int castTicks, int castRelease,
                         float castForward, float castUp, float castRight, float popupHoleForward,
                         int chargeStartTicks, float chargeBlocks, int chargeTicks, float chargeReach,
                         int chargeSkidTicks)
    {{
    }}

    public static final List<Design> ALL = List.of(
{designs});

    /** How far in front of his feet (blocks) the dirt hole he digs and dives into is (his mound while he tunnels). */
    public static final float HOLE_FORWARD = {hole_forward}F;

    /** The design with this number, or the first one. */
    public static Design byNumber(int number)
    {{
        for (Design d : ALL)
        {{
            if (d.number() == number)
            {{
                return d;
            }}
        }}
        return ALL.get(0);
    }}

    private RatKingDesigns() {{}}
}}
'''

RAT_TEMPLATE = '''package com.reg21meme.dross.boss.ratking;

import java.util.List;

/**
 * The Rat King's rats, written by {{@code tools/ratking/build.py}} (don't edit by hand: change
 * {{@code tools/ratking/ratdesigns/rats.py}} and run the build again): the regular rats ({{@code dross:rat}}) and the
 * enraged rats, a separate scarier breed ({{@code dross:enraged_rat}}). The renderer finds each design's files by its
 * {{@link Design#id()}} under {{@code entity/rat/}}.
 */
public final class RatDesigns
{{
    /**
     * One rat design.
     *
     * @param number   1 to 4 within its kind (the showcase's order)
     * @param label    what its sign says after "No. " (R1-R4, E1-E4)
     * @param id       its files' name
     * @param name     its working name
     * @param mood     a few words about it
     * @param enraged  true for the enraged breed
     * @param hasGlow  true if its {{@code <id>_glow.png}} has anything in it (glowing eyes, boils)
     * @param runBlocks how far it would travel in one loop of its run animation, in blocks
     * @param runTicks  the run animation's length
     */
    public record Design(int number, String label, String id, String name, String mood, boolean enraged,
                         boolean hasGlow, float runBlocks, int runTicks)
    {{
    }}

    public static final List<Design> REGULAR = List.of(
{regular});

    public static final List<Design> ENRAGED = List.of(
{enraged});

    /** The design with this number (1-4) of a kind, or that kind's first one. */
    public static Design byNumber(boolean enraged, int number)
    {{
        for (Design d : enraged ? ENRAGED : REGULAR)
        {{
            if (d.number() == number)
            {{
                return d;
            }}
        }}
        return (enraged ? ENRAGED : REGULAR).get(0);
    }}

    private RatDesigns() {{}}
}}
'''


def ticks(s):
    return int(round(float(s) * 20.0))


def _str(s):
    return '"' + str(s).replace('\\', '\\\\').replace('"', '\\"') + '"'


def _list(values):
    return "List.of(" + ", ".join(str(ticks(v)) for v in values) + ")"


def king_entry(d, anims, has_glow):
    w, b, p, r, de = anims["wake"], anims["burrow"], anims["popup"], anims["rage"], anims["death"]
    walk = anims["walk"]
    curled = d.STYLE.get("sleep") == "curl"
    return ("            new Design({n}, {label}, {id}, {name}, {mood}, {curled}, {glow},\n"
            "                    {wake}, {startle}, {burrow}, {dig_from}, {dig_to}, {dive},\n"
            "                    {popup}, {peek}, {burst}, {land}, {rage}, {eyes}, {steam}, {slam}, {stomps},\n"
            "                    {death}, {fall}, {tongue}, {xeyes}, {walk_blocks}F, {walk_ticks},\n"
            "                    {sleep}, {yawn}, {grab}, {screech}, {clatter}, {crown}, {slam_f}F, {slam_r}F,\n"
            "                    {run_blocks}F, {run_ticks}, {sweep}, {sweep_hit}, {sweep_reach}F, {cast}, {release},\n"
            "                    {cast_f}F, {cast_u}F, {cast_r}F, {popup_hole}F,\n"
            "                    {charge_start}, {charge_blocks}F, {charge_ticks}, {charge_reach}F, {charge_skid})").format(
        n=d.NUMBER, label=_str(d.LABEL), id=_str(d.ID), name=_str(d.NAME), mood=_str(d.MOOD),
        curled="true" if curled else "false", glow="true" if has_glow else "false",
        wake=ticks(w.length), startle=ticks(w.meta["startle"]), burrow=ticks(b.length),
        dig_from=ticks(b.meta["dig_from"]), dig_to=ticks(b.meta["dig_to"]), dive=ticks(b.meta["dive"]),
        popup=ticks(p.length), peek=ticks(p.meta["peek"]), burst=ticks(p.meta["burst"]), land=ticks(p.meta["land"]),
        rage=ticks(r.length), eyes=ticks(r.meta["eyes"]), steam=_list(r.meta["steam"]), slam=ticks(r.meta["slam"]),
        stomps=_list(r.meta["stomps"]), death=ticks(de.length), fall=ticks(de.meta["fall"]),
        tongue=ticks(de.meta["tongue"]), xeyes=ticks(de.meta["xeyes"]),
        walk_blocks=f"{walk.meta['stride_blocks']:.3f}", walk_ticks=ticks(walk.length),
        sleep=ticks(anims["sleep"].length), yawn=ticks(w.meta["yawn"]), grab=ticks(w.meta["grab"]),
        screech=ticks(r.meta["screech"]), clatter=ticks(de.meta["clatter"]), crown=ticks(de.meta["crown"]),
        slam_f=f"{r.meta.get('slam_forward', 0.5):.3f}", slam_r=f"{r.meta.get('slam_right', 0.0):.3f}",
        run_blocks=f"{anims['run'].meta['stride_blocks']:.3f}", run_ticks=ticks(anims["run"].length),
        sweep=ticks(anims["sweep"].length), sweep_hit=ticks(anims["sweep"].meta["hit"]),
        sweep_reach=f"{anims['sweep'].meta['reach']:.3f}", cast=ticks(anims["cast"].length),
        release=ticks(anims["cast"].meta["release"]), cast_f=f"{anims['cast'].meta['cast_forward']:.3f}",
        cast_u=f"{anims['cast'].meta['cast_up']:.3f}", cast_r=f"{anims['cast'].meta['cast_right']:.3f}",
        popup_hole=f"{p.meta['hole_forward'] / 16.0:.4f}",
        charge_start=ticks(anims["charge_start"].length),
        charge_blocks=f"{anims['charge'].meta['stride_blocks']:.3f}", charge_ticks=ticks(anims["charge"].length),
        charge_reach=f"{anims['charge'].meta['reach']:.3f}", charge_skid=ticks(anims["charge_skid"].length))


def rat_entry(number, d, anims, has_glow):
    run = anims["run"]
    return ("            new Design({n}, {label}, {id}, {name}, {mood}, {enraged}, {glow}, {rb}F, {rt})").format(
        n=number, label=_str(d.LABEL), id=_str(d.ID), name=_str(d.NAME), mood=_str(d.MOOD),
        enraged="true" if d.ENRAGED else "false", glow="true" if has_glow else "false",
        rb=f"{run.meta['stride_blocks']:.3f}", rt=ticks(run.length))


def write_kings(path, entries, hole_forward):
    """hole_forward: the burrow's hole (units in front of his feet; the pop-up's is each design's own)."""
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(KING_TEMPLATE.format(designs=",\n".join(entries), hole_forward=f"{hole_forward / 16.0:.4f}"))


def write_rats(path, regular, enraged):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(RAT_TEMPLATE.format(regular=",\n".join(regular), enraged=",\n".join(enraged)))
