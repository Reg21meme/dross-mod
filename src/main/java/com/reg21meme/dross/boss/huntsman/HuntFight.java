package com.reg21meme.dross.boss.huntsman;

import com.reg21meme.dross.boss.rage.Rage;
import com.reg21meme.dross.quest.DrossAdvancements;
import com.reg21meme.dross.registry.ModEffects;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The Huntsman's fight (CLAUDE.md step 14; the user's attack table, {@link HuntsmanTuning}): what he and his Alpha
 * Moose share and the rules round their moves.
 * <ul>
 *   <li><b>Calm</b> they wait, the moose walking about with him in the saddle; as soon as one of them sees a foe
 *       within {@link HuntsmanTuning#SPOT_RANGE} (a player in survival or adventure, a hostile mob: he hunts those too,
 *       or a raged mob of another team), he blows his horn and the fight is on (the user, October 22, 2026: "it should
 *       blow its horn when there is a hostile nearby, not when you first attack it"); a hit starts it too.</li>
 *   <li><b>Stage 1, the Wild Hunt</b>: he rides it, and whatever either of them takes comes off one bar of
 *       {@link HuntsmanTuning#WILD_HUNT_HEALTH} (the moose keeps it); the moose's {@link WildHuntGoal} picks their
 *       moves.</li>
 *   <li><b>The split</b> when it runs out: the moose rears, he leaps off and blows his horn, and neither can be hurt for
 *       {@link HuntsmanTuning#SPLIT_INVULNERABLE_TICKS} ticks; then <b>stage 2</b>: each on his own full bar ({@link FootGoal},
 *       {@link MooseGoal}).</li>
 *   <li>When one dies the other is <b>angry</b> (faster, harder hits, moves back sooner; its bar blood red), and when
 *       both are dead every player who helped gets "The Hunter Hunted" (not for {@code /kill}).</li>
 *   <li>With no player or foe near for {@link HuntsmanTuning#BREAK_OFF_TICKS} ticks the fight is broken off: both are
 *       back at full health, the dead one too, and he's back in the saddle.</li>
 * </ul>
 * They never hurt each other (their blows, arrows, bolas, spears, spikes). The moose goes for the closest foe, he for
 * the furthest player (the user: "the moose is the close range attacker, the hunter long range"); raged, with a raged
 * enemy near, they go for it alone (the Rage's rule).
 */
public final class HuntFight
{
    /** What one of his arrows does when it hits: nothing more, the frost of the arrow rain (Slowness and the frosted
     * screen), or the aimed shot's freeze (held still a moment as well). */
    public enum Frost
    {
        NONE, FROST, FREEZE
    }

    /** Something due later on one of them; true when it's done. */
    interface Pending
    {
        boolean tick(HuntMob mob, ServerLevel level);
    }

    /** The tag his arrows carry (what their hit does). */
    static final String ARROW_TAG = "DrossHuntHit";
    /** The tag the Cracking Stamp's spikes carry (so ones left over after a restart are cleared away). */
    static final String SPIKE_TAG = "dross_hunt_spike";
    /** The angry one's extra speed (its id fixed so it's never added twice). */
    private static final UUID ANGRY_SPEED_ID = UUID.fromString("6c1a8a0e-3f9b-4d6e-9a51-2b7f3c8d4e11");
    /** A volley arrow (into the sky, harmless) is cleared away this long after it's loosed. */
    private static final int VOLLEY_CLEAR_TICKS = 60;
    /** A rain arrow is cleared away this long after it comes down. */
    private static final int RAIN_CLEAR_TICKS = 80;
    /** An aimed arrow's drop per tick squared (an arrow's gravity, 0.05, halved: it falls that far in t ticks). */
    private static final double ARROW_DROP = 0.025D;
    /** The arrow rain's few degrees of spread as it comes down. */
    private static final float RAIN_SPREAD_DEGREES = 2.0F;
    /** The frosted screen and freeze of the aimed shot (never long enough for powder snow's damage, 140). */
    private static final int FREEZE_FROST_TICKS = 130;
    /** The spikes' blocks: the middle one blue ice, the rest packed ice. */
    private static final int SPIKES = 5;

    /** Every spike standing and when it goes (game time). */
    private static final Map<Entity, Long> SPIKES_UP = new LinkedHashMap<>();

    private HuntFight() {}

    // ---------------------------------------------------------------- the pair

    /** Its partner (alive or a body still lying there), or null. */
    @Nullable
    static HuntMob partner(HuntMob mob)
    {
        if (mob.partnerId == null || !(mob.level() instanceof ServerLevel level))
        {
            return null;
        }
        Entity e = level.getEntity(mob.partnerId);
        return e instanceof HuntMob h && !h.isRemoved() ? h : null;
    }

    /** True if {@code other} is {@code mob}'s partner (or {@code mob} itself, or a hitbox of either: the Alpha
     * Moose's body parts). */
    public static boolean isPartner(HuntMob mob, @Nullable Entity other)
    {
        if (other instanceof PartEntity<?> part)
        {
            return isPartner(mob, part.getParent());
        }
        return other == mob || other != null && (other.getUUID().equals(mob.partnerId)
                || other instanceof HuntMob h && mob.getUUID().equals(h.partnerId));
    }

    static void link(HuntMob a, HuntMob b)
    {
        a.partnerId = b.getUUID();
        b.partnerId = a.getUUID();
    }

    /** The one that keeps the pair's things (the stage's clock, the break-off): the moose while it lives. */
    static boolean leads(HuntMob mob)
    {
        HuntMob p = partner(mob);
        return mob instanceof AlphaMoose || p == null || !p.isAlive();
    }

    @Nullable
    static AlphaMoose mooseOf(HuntMob mob)
    {
        return mob instanceof AlphaMoose m ? m : partner(mob) instanceof AlphaMoose m ? m : null;
    }

    @Nullable
    static Huntsman huntsmanOf(HuntMob mob)
    {
        return mob instanceof Huntsman h ? h : partner(mob) instanceof Huntsman h ? h : null;
    }

    private static void setStage(@Nullable HuntMob mob, HuntStage stage)
    {
        if (mob != null && mob.stage != stage)
        {
            mob.stage = stage;
            mob.stageTicks = 0;
        }
    }

    // ---------------------------------------------------------------- every tick

    static void tick(HuntMob mob)
    {
        if (!(mob.level() instanceof ServerLevel level))
        {
            return;
        }
        mob.stageTicks++;
        for (Iterator<Pending> it = new ArrayList<>(mob.pending).iterator(); it.hasNext(); )
        {
            Pending p = it.next();
            if (p.tick(mob, level))
            {
                mob.pending.remove(p);
            }
        }
        if (!mob.isAlive())
        {
            return;
        }
        HuntMob partner = partner(mob);
        if (mob instanceof AlphaMoose moose && mob.stage == HuntStage.WILD_HUNT)
        {
            Huntsman rider = huntsmanOf(moose);
            if (moose.wildHunt <= 0.0F)
            {
                split(moose);
            }
            else if (rider == null || !rider.isAlive() || rider.getVehicle() != moose)
            {
                toDuel(moose);                       // he's off it some other way: on with the duel
            }
        }
        if (mob.stage == HuntStage.SPLIT && leads(mob) && mob.stageTicks >= HuntsmanTuning.SPLIT_INVULNERABLE_TICKS)
        {
            toDuel(mob);
        }
        if (mob.stage == HuntStage.CALM && leads(mob) && mob.tickCount % HuntsmanTuning.SPOT_INTERVAL == 0 && spots(mob))
        {
            startFight(mob);                         // a foe in sight: his horn, and the hunt is on
        }
        if (mob.stage.fighting())
        {
            mob.setTarget(chooseTarget(mob));
            if (leads(mob))
            {
                breakOffCheck(mob, level);
            }
        }
        else if (mob.getTarget() != null)
        {
            mob.setTarget(null);
        }
        if (mob.stage == HuntStage.SPLIT)
        {
            holdStill(mob);                          // the moose rears and he leaps off from one spot
        }
        else if (mob.stage == HuntStage.DUEL && mob instanceof Huntsman h && partner instanceof AlphaMoose moose)
        {
            keepApart(h, moose);
        }
        updateBar(mob, level);
        locomote(mob);
        if (partner == null && mob.partnerId == null && mob.stage == HuntStage.CALM && mob instanceof Huntsman h
                && !h.isMounted() && mob.tickCount > 1)
        {
            HuntsmanPair.mountNew(h, level);            // a Huntsman made on his own (/summon with data) gets his moose
        }
    }

    /**
     * Walking, running or standing when it isn't making a move: what its feet are doing (the saddle's for him: his own
     * part of the gallop while the moose gallops or charges). Its speed is smoothed over a few ticks and a gait changes
     * only past a margin ({@link HuntsmanTuning#RUN_ON}...) and once it has held a few ticks, so it never flickers
     * between two (the user saw the walk "awkward and jittery"); a walk or run plays at the speed it really goes
     * ({@link HuntMob#setPace}), so hooves and boots don't slide.
     */
    static void locomote(HuntMob mob)
    {
        mob.gaitSpeed += (speed(mob) - mob.gaitSpeed) * HuntsmanTuning.GAIT_SMOOTHING;
        if (mob.busy())
        {
            mob.gaitNext = null;
            return;
        }
        if (mob instanceof Huntsman h && h.getVehicle() instanceof AlphaMoose moose)
        {
            String gait = moose.current().name();
            mob.loop(gait.equals("run") || gait.equals("charge") ? HuntAnims.GALLOP : "ride_idle");
            return;
        }
        String now = mob.current().name();
        String want;
        if (mob.forcedLoop != null)
        {
            want = mob.forcedLoop;
            mob.gaitNext = null;
        }
        else
        {
            double s = mob.gaitSpeed;
            boolean running = now.equals("run");
            boolean walking = running || now.equals("walk");
            want = s > HuntsmanTuning.RUN_ON || running && s > HuntsmanTuning.RUN_OFF ? "run"
                    : s > HuntsmanTuning.WALK_ON || walking && s > HuntsmanTuning.WALK_OFF ? "walk" : "idle";
            boolean inGait = walking || now.equals("idle");
            if (inGait && !want.equals(now))
            {
                mob.gaitNextTicks = want.equals(mob.gaitNext) ? mob.gaitNextTicks + 1 : 1;
                mob.gaitNext = want;
                if (mob.gaitNextTicks < HuntsmanTuning.GAIT_HOLD)
                {
                    want = now;                      // not for long enough yet
                }
            }
            else
            {
                mob.gaitNext = null;
            }
        }
        mob.loop(want);
        double made = mob.gaitPace(mob.current().name());
        if (made > 0.0D)
        {
            float pace = Mth.clamp((float) (mob.gaitSpeed * 20.0D / made), HuntsmanTuning.PACE_LEAST, HuntsmanTuning.PACE_MOST);
            if (Math.abs(pace - mob.pace()) >= HuntsmanTuning.PACE_STEP)
            {
                mob.setPace(pace);
            }
        }
    }

    /** At the split they stand where they are: the moose rears, he leaps off and lands, his horn. */
    private static void holdStill(HuntMob mob)
    {
        if (mob.isPassenger())
        {
            return;
        }
        mob.getNavigation().stop();
        Vec3 v = mob.getDeltaMovement();
        mob.setDeltaMovement(0.0D, v.y, 0.0D);
    }

    /**
     * In stage 2 he keeps clear of the moose (the user saw them walk into each other): inside an oval round its body
     * ({@link HuntsmanTuning#APART_AHEAD} along it, its head and antlers, {@link HuntsmanTuning#APART_SIDE} across it, its
     * antlers' spread, plus his own half width) he's pushed out, the moose a little the other way.
     */
    static void keepApart(Huntsman h, AlphaMoose moose)
    {
        double r = insideMoose(moose, h.position(), h.getBbWidth() / 2.0D);
        if (r >= 1.0D || !h.isAlive() || !moose.isAlive() || h.isPassenger())
        {
            return;
        }
        double yaw = Math.toRadians(moose.yBodyRot);
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double dx = h.getX() - moose.getX();
        double dz = h.getZ() - moose.getZ();
        double a = HuntsmanTuning.APART_AHEAD + h.getBbWidth() / 2.0D;
        double b = HuntsmanTuning.APART_SIDE + h.getBbWidth() / 2.0D;
        double na = (dx * fx + dz * fz) / (a * a);
        double ns = (dx * fz - dz * fx) / (b * b);
        double len = Math.sqrt(na * na + ns * ns);
        if (len < 1.0E-6D)
        {
            na = 0.0D;
            ns = 1.0D;                               // right on its middle: out to its side
            len = 1.0D;
        }
        double wx = (na * fx + ns * fz) / len;
        double wz = (na * fz - ns * fx) / len;
        double push = 1.0D - r;
        h.setDeltaMovement(h.getDeltaMovement().add(wx * HuntsmanTuning.APART_PUSH_HIM * push, 0.0D,
                wz * HuntsmanTuning.APART_PUSH_HIM * push));
        moose.setDeltaMovement(moose.getDeltaMovement().add(-wx * HuntsmanTuning.APART_PUSH_MOOSE * push, 0.0D,
                -wz * HuntsmanTuning.APART_PUSH_MOOSE * push));
    }

    /** How far {@code at} is inside the oval round the moose's body grown by {@code margin}: under 1 inside it. */
    static double insideMoose(AlphaMoose moose, Vec3 at, double margin)
    {
        double yaw = Math.toRadians(moose.yBodyRot);
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double dx = at.x - moose.getX();
        double dz = at.z - moose.getZ();
        double ahead = (dx * fx + dz * fz) / (HuntsmanTuning.APART_AHEAD + margin);
        double side = (dx * fz - dz * fx) / (HuntsmanTuning.APART_SIDE + margin);
        return Math.sqrt(ahead * ahead + side * side);
    }

    /** True if {@code spot} is clear of his moose (alive, on its own feet): for where his leaps land. */
    static boolean clearOfMoose(Huntsman h, Vec3 spot)
    {
        return !(partner(h) instanceof AlphaMoose moose) || !moose.isAlive()
                || insideMoose(moose, spot, h.getBbWidth() / 2.0D) >= 1.0D;
    }

    /** How far it moved this tick, across the ground. */
    static double speed(Entity e)
    {
        double dx = e.getX() - e.xo;
        double dz = e.getZ() - e.zo;
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ---------------------------------------------------------------- being hurt

    /** A blow, arrow or spike from itself or its partner. */
    static boolean friendly(HuntMob mob, DamageSource source)
    {
        Entity attacker = source.getEntity();
        return attacker != null && isPartner(mob, attacker);
    }

    static void onHurt(HuntMob mob, DamageSource source)
    {
        Entity attacker = source.getEntity();
        if (attacker instanceof Player player)
        {
            long now = mob.level().getGameTime();
            mob.creditPlayer = player.getUUID();
            mob.creditTick = (int) now;
            HuntMob p = partner(mob);
            if (p != null)
            {
                p.creditPlayer = player.getUUID();
                p.creditTick = (int) now;
            }
        }
        if (mob.stage == HuntStage.CALM && attacker instanceof LivingEntity)
        {
            startFight(mob);
        }
    }

    /** The first hit: the Wild Hunt (or the duel, if he isn't in the saddle), and his horn. */
    static void startFight(HuntMob mob)
    {
        Huntsman h = huntsmanOf(mob);
        AlphaMoose m = mooseOf(mob);
        boolean riding = h != null && m != null && h.getVehicle() == m;
        HuntStage next = riding ? HuntStage.WILD_HUNT : HuntStage.DUEL;
        setStage(h, next);
        setStage(m, next);
        if (h != null && h.isAlive())
        {
            h.play(riding ? "ride_horn" : "horn_blow");
        }
    }

    /** What either of them lost in stage 1, off the bar they share. */
    static void damageWildHunt(HuntMob mob, float amount)
    {
        AlphaMoose moose = mooseOf(mob);
        if (moose == null)
        {
            mob.setHealth(mob.getHealth() - amount);  // no moose: it's its own bar
            return;
        }
        moose.wildHunt = Math.max(0.0F, moose.wildHunt - amount);
        if (moose.wildHunt <= 0.0F && moose.stage == HuntStage.WILD_HUNT)
        {
            split(moose);
        }
    }

    /** The split: the moose rears, he leaps off (and blows his horn when he lands: {@link Huntsman}). */
    static void split(AlphaMoose moose)
    {
        Huntsman h = huntsmanOf(moose);
        setStage(moose, HuntStage.SPLIT);
        setStage(h, HuntStage.SPLIT);
        moose.wildHunt = 0.0F;
        moose.forcedLoop = null;
        moose.getNavigation().stop();
        moose.play("rear");
        if (h != null)
        {
            h.hornWhenLanded = true;
            if (h.getVehicle() == moose)
            {
                h.play(HuntAnims.LEAP);
            }
            else
            {
                h.play("horn_blow");
            }
        }
    }

    /** Stage 2: each on his own full bar. */
    static void toDuel(HuntMob mob)
    {
        for (HuntMob m : new HuntMob[] {mob, partner(mob)})
        {
            if (m != null && m.isAlive())
            {
                setStage(m, HuntStage.DUEL);
                m.setHealth(m.getMaxHealth());
                if (m instanceof Huntsman h && h.getVehicle() instanceof AlphaMoose moose)
                {
                    h.leapOff(moose);
                }
            }
        }
    }

    static void makeAngry(HuntMob mob)
    {
        mob.angry = true;
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(ANGRY_SPEED_ID) == null)
        {
            speed.addPermanentModifier(new AttributeModifier(ANGRY_SPEED_ID, "Huntsman: angry",
                    HuntsmanTuning.ANGRY_SPEED - 1.0D, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private static void calmDown(HuntMob mob)
    {
        mob.angry = false;
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null)
        {
            speed.removeModifier(ANGRY_SPEED_ID);
        }
    }

    /** Its blows and shots, angry or not. */
    public static float damageScale(HuntMob mob)
    {
        return mob.angry ? HuntsmanTuning.ANGRY_DAMAGE : 1.0F;
    }

    static double cooldownScale(HuntMob mob)
    {
        return mob.angry ? HuntsmanTuning.ANGRY_COOLDOWN : 1.0D;
    }

    // ---------------------------------------------------------------- dying

    static void onDeath(HuntMob mob, DamageSource source)
    {
        if (mob.bar != null)
        {
            mob.bar.setProgress(0.0F);
            mob.bar.removeAllPlayers();
        }
        mob.forcedLoop = null;
        mob.play("death");
        if (mob instanceof AlphaMoose)
        {
            mob.ejectPassengers();
        }
        HuntMob partner = partner(mob);
        if (partner != null && partner.isAlive())
        {
            if (partner.stage != HuntStage.DUEL)
            {
                setStage(partner, HuntStage.DUEL);
                if (partner instanceof Huntsman h && h.getVehicle() != null)
                {
                    h.stopRiding();
                }
            }
            makeAngry(partner);
            return;
        }
        setStage(mob, HuntStage.WON);
        setStage(partner, HuntStage.WON);
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
        {
            for (ServerPlayer player : victors(mob, source))
            {
                DrossAdvancements.grant(player, DrossAdvancements.THE_HUNTER_HUNTED);
            }
        }
    }

    /** Everyone who helped beat them: the player credited with the last kill (wherever they are) and every player
     * within their bars' range (not spectators), each once. */
    static List<ServerPlayer> victors(HuntMob mob, DamageSource source)
    {
        if (!(mob.level() instanceof ServerLevel level))
        {
            return List.of();
        }
        Map<UUID, ServerPlayer> out = new LinkedHashMap<>();
        UUID killer = null;
        Entity credited = source.getEntity();
        if (credited instanceof Player p)
        {
            killer = p.getUUID();
        }
        else if (mob.creditPlayer != null && level.getGameTime() - mob.creditTick <= HuntsmanTuning.CREDIT_TICKS)
        {
            killer = mob.creditPlayer;
        }
        if (killer != null)
        {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(killer);
            if (player != null)
            {
                out.put(player.getUUID(), player);
            }
        }
        double range = HuntsmanTuning.BOSS_BAR_RANGE;
        for (ServerPlayer player : level.players())
        {
            if (!player.isSpectator() && player.distanceToSqr(mob) <= range * range)
            {
                out.putIfAbsent(player.getUUID(), player);
            }
        }
        return List.copyOf(out.values());
    }

    /** A body is gone in a puff of snow. */
    static void vanish(HuntMob mob)
    {
        if (mob.level() instanceof ServerLevel level)
        {
            AABB box = mob.getBoundingBox();
            Vec3 c = box.getCenter();
            level.sendParticles(ParticleTypes.SNOWFLAKE, c.x, box.minY + 0.5D, c.z, 60, 1.0D, 0.6D, 1.0D, 0.05D);
            level.sendParticles(ParticleTypes.POOF, c.x, box.minY + 0.4D, c.z, 30, 1.0D, 0.3D, 1.0D, 0.03D);
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.POWDER_SNOW_BREAK, SoundSource.HOSTILE,
                    1.5F, 0.8F);
        }
        mob.remove(Entity.RemovalReason.KILLED);
    }

    // ---------------------------------------------------------------- breaking off

    private static void breakOffCheck(HuntMob leader, ServerLevel level)
    {
        double range = HuntsmanTuning.BREAK_OFF_RANGE;
        boolean near = false;
        for (ServerPlayer player : level.players())
        {
            if (!player.isSpectator() && player.isAlive() && player.distanceToSqr(leader) <= range * range)
            {
                near = true;
                break;
            }
        }
        // a foe they're fighting keeps it going too (a raged Rat King of another team, a Pond Skater)
        LivingEntity target = leader.getTarget();
        near |= target != null && isFoe(leader, target) && level.getEntity(target.getUUID()) == target
                && target.distanceToSqr(leader) <= range * range;
        leader.lonelyTicks = near ? 0 : leader.lonelyTicks + 1;
        if (leader.lonelyTicks >= HuntsmanTuning.BREAK_OFF_TICKS)
        {
            reset(leader, level);
        }
    }

    /** The fight broken off: both back at full health (a dead one back too), him back in the saddle, calm. */
    static void reset(HuntMob leader, ServerLevel level)
    {
        HuntMob partner = partner(leader);
        if (partner == null || !partner.isAlive())
        {
            if (partner != null)
            {
                partner.discard();
            }
            partner = leader instanceof Huntsman ? HuntsmanPair.newMoose(level, leader.position(), leader.getYRot())
                    : HuntsmanPair.newHuntsman(level, leader.position(), leader.getYRot());
            if (partner == null)
            {
                return;
            }
            link(leader, partner);
            Rage.recruit(partner, leader);              // raged, the new one is on its team too
        }
        for (HuntMob m : new HuntMob[] {leader, partner})
        {
            m.stage = HuntStage.CALM;
            m.stageTicks = 0;
            m.lonelyTicks = 0;
            m.forcedLoop = null;
            m.pending.clear();
            m.setTarget(null);
            m.getNavigation().stop();
            calmDown(m);
            m.setHealth(m.getMaxHealth());
            m.play(m.animations().get(0).name());
            if (m.bar != null)
            {
                m.bar.removeAllPlayers();
            }
        }
        Huntsman h = huntsmanOf(leader);
        AlphaMoose moose = mooseOf(leader);
        if (moose != null)
        {
            moose.wildHunt = HuntsmanTuning.WILD_HUNT_HEALTH;
        }
        if (h != null && moose != null && h.getVehicle() != moose)
        {
            h.stopRiding();
            h.moveTo(moose.getX(), moose.getY(), moose.getZ(), moose.getYRot(), 0.0F);
            h.startRiding(moose, true);
            h.play("ride_idle");
        }
    }

    // ---------------------------------------------------------------- whom they go for, which way they face

    /**
     * A foe of theirs: a player in survival or adventure, a hostile mob (they hunt those too: the user's answer, October
     * 22, 2026), or a raged mob of another team; never each other, a raged teammate, another Huntsman or Alpha Moose, or a
     * mob on show (no AI).
     */
    static boolean isFoe(HuntMob mob, @Nullable Entity e)
    {
        if (!(e instanceof LivingEntity living) || !living.isAlive() || isPartner(mob, e))
        {
            return false;
        }
        if (Rage.areEnemies(mob, e))
        {
            return true;
        }
        if (Rage.areTeammates(mob, e))
        {
            return false;
        }
        if (e instanceof Player p)
        {
            return !p.isSpectator() && !p.isCreative();
        }
        return e instanceof Enemy && !(e instanceof HuntMob) && !(e instanceof Mob m && m.isNoAi());
    }

    /** Calm, true if either of them sees a foe within {@link HuntsmanTuning#SPOT_RANGE} (raged, any raged enemy near). */
    static boolean spots(HuntMob mob)
    {
        if (Rage.hasEnemyNear(mob))
        {
            return true;
        }
        HuntMob partner = partner(mob);
        double r = HuntsmanTuning.SPOT_RANGE;
        for (LivingEntity e : mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(r),
                e -> isFoe(mob, e) && e.distanceToSqr(mob) <= r * r))
        {
            if (mob.hasLineOfSight(e) || partner != null && partner.isAlive() && partner.hasLineOfSight(e))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Whom it goes for. Raged with a raged enemy near: that enemy, nothing else (the Rage's rule: the Rat King of another
     * team, say). Otherwise the moose the closest player, the Huntsman the furthest (the same one when there's only
     * one: the moose is the close-range fighter, he the long-range one); with no player near, a hostile mob (the moose
     * the closest, he the furthest); with none, whatever last hurt either of them.
     */
    @Nullable
    static LivingEntity chooseTarget(HuntMob mob)
    {
        if (Rage.hasEnemyNear(mob))
        {
            return Rage.nearestEnemy(mob);
        }
        double range = HuntsmanTuning.FOLLOW_RANGE;
        boolean furthest = mob instanceof Huntsman;
        LivingEntity player = null;
        LivingEntity other = null;
        double playerD = 0.0D;
        double otherD = 0.0D;
        for (LivingEntity e : mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(range),
                e -> isFoe(mob, e)))
        {
            double d = e.distanceToSqr(mob);
            if (d > range * range)
            {
                continue;
            }
            if (e instanceof Player)
            {
                if (player == null || (furthest ? d > playerD : d < playerD))
                {
                    player = e;
                    playerD = d;
                }
            }
            else if (other == null || (furthest ? d > otherD : d < otherD))
            {
                other = e;
                otherD = d;
            }
        }
        if (player != null || other != null)
        {
            return player != null ? player : other;
        }
        for (HuntMob m : new HuntMob[] {mob, partner(mob)})
        {
            LivingEntity last = m != null ? m.getLastHurtByMob() : null;
            if (last != null && last.isAlive() && !last.isSpectator() && !isPartner(mob, last)
                    && !Rage.areTeammates(mob, last) && last.distanceToSqr(mob) <= range * range)
            {
                return last;
            }
        }
        return null;
    }

    /** The way it faces (the moose's, for him in the saddle). */
    static float facing(HuntMob mob)
    {
        return mob.getVehicle() != null ? mob.getVehicle().getYRot() : mob.getYRot();
    }

    /** Turns it towards {@code target} by at most {@link HuntsmanTuning#TURN} degrees. */
    static void face(HuntMob mob, Entity target)
    {
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float yaw = Mth.approachDegrees(mob.getYRot(), want, HuntsmanTuning.TURN);
        mob.setYRot(yaw);
        mob.yBodyRot = yaw;
        mob.setYHeadRot(yaw);
    }

    /** How far {@code target} is from {@code mob} across the ground (middle to middle). */
    static double flat(Entity mob, Entity target)
    {
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** How far round from straight ahead {@code target} is (degrees, 0-180). */
    static double angle(float facingYaw, Vec3 from, Vec3 to)
    {
        double want = Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG - 90.0D;
        return Math.abs(Mth.wrapDegrees(want - facingYaw));
    }

    // ---------------------------------------------------------------- picking a move

    /**
     * A move from one row of a table ({name, weight, name, weight...}): a move that can't be made right now drops out
     * and the others share its weight; null if none can.
     */
    @Nullable
    public static String pick(Object[] row, Predicate<String> can, RandomSource random)
    {
        int total = 0;
        for (int i = 0; i < row.length; i += 2)
        {
            if (can.test((String) row[i]))
            {
                total += (Integer) row[i + 1];
            }
        }
        if (total <= 0)
        {
            return null;
        }
        int roll = random.nextInt(total);
        for (int i = 0; i < row.length; i += 2)
        {
            if (can.test((String) row[i]))
            {
                roll -= (Integer) row[i + 1];
                if (roll < 0)
                {
                    return (String) row[i];
                }
            }
        }
        return null;
    }

    /** The row of a table for a distance ({@code bands}: the upper ends of every row but the last). */
    static Object[] row(Object[][] table, double[] bands, double distance)
    {
        for (int i = 0; i < bands.length; i++)
        {
            if (distance <= bands[i])
            {
                return table[i];
            }
        }
        return table[table.length - 1];
    }

    // ---------------------------------------------------------------- blows

    /** Living things (not them, not their riders, not spectators) within {@code reach} of {@code from} across the
     * ground and 3 blocks up or down, no more than {@code halfAngle} round from {@code yaw}. */
    static List<LivingEntity> within(HuntMob attacker, Vec3 from, float yaw, double reach, double halfAngle)
    {
        List<LivingEntity> out = new ArrayList<>();
        AABB box = new AABB(from, from).inflate(reach, 3.0D, reach);
        for (LivingEntity e : attacker.level().getEntitiesOfClass(LivingEntity.class, box))
        {
            if (isPartner(attacker, e) || e.isSpectator() || !e.isAlive() || e.isPassengerOfSameVehicle(attacker))
            {
                continue;
            }
            Vec3 at = e.position();
            double dx = at.x - from.x;
            double dz = at.z - from.z;
            double d = Math.sqrt(dx * dx + dz * dz) - e.getBbWidth() / 2.0D;
            if (d <= reach && (halfAngle >= 180.0D || angle(yaw, from, at) <= halfAngle))
            {
                out.add(e);
            }
        }
        return out;
    }

    /** A blow: everything {@link #within} reach takes {@code damage} (angry, more) and is thrown away from
     * {@code from}. Returns how many it hit. */
    static int strike(HuntMob attacker, Vec3 from, float yaw, double reach, double halfAngle, float damage,
                      double knock, double knockUp)
    {
        int n = 0;
        for (LivingEntity e : within(attacker, from, yaw, reach, halfAngle))
        {
            if (e.hurt(attacker.damageSources().mobAttack(attacker), damage * damageScale(attacker)))
            {
                throwFrom(e, from, knock, knockUp);
                n++;
            }
        }
        return n;
    }

    /** Throws {@code e} away from {@code from}: {@code across} blocks a tick over the ground, {@code up} up. */
    static void throwFrom(Entity e, Vec3 from, double across, double up)
    {
        double dx = e.getX() - from.x;
        double dz = e.getZ() - from.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < 1.0E-4D)
        {
            dx = 0.0D;
            dz = 1.0D;
            d = 1.0D;
        }
        double resist = e instanceof LivingEntity l ? 1.0D - l.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) : 1.0D;
        e.setDeltaMovement(dx / d * across * resist, Math.max(e.getDeltaMovement().y, up * resist), dz / d * across * resist);
        e.hurtMarked = true;
        e.hasImpulse = true;
    }

    /** A cone from its front (the horn, the bellow): everything in it takes {@code damage}, is pushed away and
     * slowed. */
    static void blast(HuntMob source, float yaw, double length, double halfAngle, float damage, double push,
                      double pushUp, int slowTicks, int slowLevel)
    {
        Vec3 from = source.modelOrigin();
        for (LivingEntity e : within(source, from, yaw, length, halfAngle))
        {
            e.hurt(source.damageSources().mobAttack(source), damage * damageScale(source));
            throwFrom(e, from, push, pushUp);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slowTicks, slowLevel));
        }
        if (source.level() instanceof ServerLevel level)
        {
            double rad = Math.toRadians(yaw);
            Vec3 ahead = new Vec3(-Math.sin(rad), 0.0D, Math.cos(rad));
            for (double d = 1.0D; d <= length; d += 1.0D)
            {
                double spread = Math.tan(Math.toRadians(halfAngle)) * d;
                Vec3 p = from.add(ahead.scale(d)).add(0.0D, 1.5D, 0.0D);
                level.sendParticles(ParticleTypes.SNOWFLAKE, p.x, p.y, p.z, 6, spread * 0.5D, 0.6D, spread * 0.5D, 0.08D);
                level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 2, spread * 0.4D, 0.4D, spread * 0.4D, 0.02D);
            }
        }
    }

    /** Everything within {@code radius} of {@code at} (a landing, the spikes) takes {@code damage} and is thrown out and
     * up. */
    static void burst(HuntMob source, Vec3 at, double radius, float damage, double knock, double up)
    {
        for (LivingEntity e : within(source, at, 0.0F, radius, 180.0D))
        {
            if (e.hurt(source.damageSources().mobAttack(source), damage * damageScale(source)))
            {
                throwFrom(e, at, knock, up);
            }
        }
    }

    // ---------------------------------------------------------------- shots

    /** Which way to loose a projectile at {@code speed} from {@code from} to hit {@code target} (leading it, allowing
     * for the drop). */
    public static Vec3 aimAt(Vec3 from, LivingEntity target, double speed)
    {
        return aimAt(from, target, speed, ARROW_DROP);
    }

    public static Vec3 aimAt(Vec3 from, LivingEntity target, double speed, double drop)
    {
        Vec3 to = target.position().add(0.0D, target.getBbHeight() * 0.6D, 0.0D);
        double t = from.distanceTo(to) / speed;
        Vec3 move = target.getDeltaMovement();
        to = to.add(move.x * t, 0.0D, move.z * t);
        Vec3 d = to.subtract(from);
        return new Vec3(d.x, d.y + drop * t * t, d.z);
    }

    /** What his arrow's hit does ({@link HuntsmanEvents} reads it). */
    static void mark(AbstractArrow arrow, Frost frost)
    {
        if (frost != Frost.NONE)
        {
            arrow.getPersistentData().putString(ARROW_TAG, frost.name());
        }
    }

    /** One of his marked arrows hit {@code victim}. */
    static void arrowHit(LivingEntity victim, String what)
    {
        if (Frost.FROST.name().equals(what))
        {
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, HuntsmanTuning.FROST_SLOW_TICKS, 0));
            victim.setTicksFrozen(Math.max(victim.getTicksFrozen(), HuntsmanTuning.FROST_FREEZE_TICKS));
        }
        else if (Frost.FREEZE.name().equals(what))
        {
            victim.addEffect(new MobEffectInstance(ModEffects.SNARED.get(), HuntsmanTuning.AIMED_FREEZE_TICKS, 0));
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, HuntsmanTuning.FROST_SLOW_TICKS, 1));
            victim.setTicksFrozen(Math.max(victim.getTicksFrozen(), FREEZE_FROST_TICKS));
        }
    }

    /** Clears {@code e} away (a harmless volley arrow) a little later. */
    static void clearLater(HuntMob mob, Entity e)
    {
        int[] left = {VOLLEY_CLEAR_TICKS};
        mob.pending.add((m, level) ->
        {
            if (--left[0] > 0)
            {
                return false;
            }
            e.discard();
            return true;
        });
    }

    /** The arrow rain: {@code arrows} frost arrows come down at random spots within
     * {@link HuntsmanTuning#RAIN_RADIUS} of {@code target}, starting a moment later, with nothing to show where (the
     * user: "don't warn the player where the arrows land"). */
    static void rain(HuntMob shooter, LivingEntity target, int arrows)
    {
        for (int i = 0; i < arrows; i++)
        {
            int[] left = {HuntsmanTuning.RAIN_DELAY + (int) (i * (double) HuntsmanTuning.RAIN_SPREAD / arrows)};
            Vec3[] last = {target.position()};
            shooter.pending.add((m, level) ->
            {
                if (target.isAlive())
                {
                    last[0] = target.position();
                }
                if (--left[0] > 0)
                {
                    return false;
                }
                RandomSource r = m.getRandom();
                double a = r.nextDouble() * Math.PI * 2.0D;
                double d = Math.sqrt(r.nextDouble()) * HuntsmanTuning.RAIN_RADIUS;
                Vec3 c = last[0];
                Arrow arrow = new Arrow(level, c.x + Math.cos(a) * d, c.y + HuntsmanTuning.RAIN_HEIGHT, c.z + Math.sin(a) * d);
                arrow.setOwner(m);
                arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                arrow.setBaseDamage(HuntsmanTuning.RAIN_BASE_DAMAGE * damageScale(m));
                arrow.shoot(0.0D, -1.0D, 0.0D, (float) HuntsmanTuning.RAIN_SPEED, RAIN_SPREAD_DEGREES);
                mark(arrow, Frost.FROST);
                level.addFreshEntity(arrow);
                int[] gone = {RAIN_CLEAR_TICKS};
                m.pending.add((m2, l2) ->
                {
                    if (--gone[0] > 0)
                    {
                        return false;
                    }
                    arrow.discard();
                    return true;
                });
                return true;
            });
        }
    }

    // ---------------------------------------------------------------- the Cracking Stamp

    /** Cracks run over the ground from {@code from} to {@code to}, then spikes of ice burst up there. */
    static void crack(AlphaMoose moose, Vec3 from, Vec3 to)
    {
        Vec3[] at = {from};
        int[] step = {0};
        BlockState crust = Blocks.PACKED_ICE.defaultBlockState();
        moose.pending.add((m, level) ->
        {
            Vec3 left = to.subtract(at[0]);
            double d = Math.sqrt(left.x * left.x + left.z * left.z);
            if (d > HuntsmanTuning.STAMP_CRACK_SPEED)
            {
                at[0] = at[0].add(left.x / d * HuntsmanTuning.STAMP_CRACK_SPEED, 0.0D, left.z / d * HuntsmanTuning.STAMP_CRACK_SPEED);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, crust), at[0].x, to.y + 0.1D, at[0].z, 6,
                        0.25D, 0.05D, 0.25D, 0.05D);
                level.sendParticles(ParticleTypes.SNOWFLAKE, at[0].x, to.y + 0.2D, at[0].z, 2, 0.2D, 0.05D, 0.2D, 0.01D);
                if (step[0]++ % 4 == 0)
                {
                    level.playSound(null, at[0].x, to.y, at[0].z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 0.5F, 0.6F);
                }
                return false;
            }
            spikes(level, to, m.getRandom());
            burst(m, to, HuntsmanTuning.STAMP_RADIUS, HuntsmanTuning.STAMP_DAMAGE, 0.3D, HuntsmanTuning.STAMP_LAUNCH);
            level.playSound(null, to.x, to.y, to.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.5F, 0.5F);
            level.playSound(null, to.x, to.y, to.z, SoundEvents.POINTED_DRIPSTONE_LAND, SoundSource.HOSTILE, 1.5F, 0.7F);
            return true;
        });
    }

    /** Spikes of ice standing for {@link HuntsmanTuning#STAMP_SPIKE_TICKS}: block displays, so nothing in the world is
     * changed. */
    static void spikes(ServerLevel level, Vec3 at, RandomSource random)
    {
        for (int i = 0; i < SPIKES; i++)
        {
            double a = i == 0 ? 0.0D : (i - 1) * Math.PI / 2.0D + random.nextDouble() * 0.6D;
            double d = i == 0 ? 0.0D : 0.7D + random.nextDouble() * 0.5D;
            float width = i == 0 ? 0.5F : 0.3F + random.nextFloat() * 0.15F;
            float height = i == 0 ? 2.2F : 1.1F + random.nextFloat() * 0.8F;
            BlockState block = (i == 0 ? Blocks.BLUE_ICE : Blocks.PACKED_ICE).defaultBlockState();
            CompoundTag tag = new CompoundTag();
            tag.putString("id", "minecraft:block_display");
            tag.put("block_state", NbtUtils.writeBlockState(block));
            CompoundTag transform = new CompoundTag();
            transform.put("translation", floats(-width / 2.0F, -0.3F, -width / 2.0F));
            transform.put("scale", floats(width, height, width));
            transform.put("left_rotation", floats(0.0F, 0.0F, 0.0F, 1.0F));
            transform.put("right_rotation", floats(0.0F, 0.0F, 0.0F, 1.0F));
            tag.put("transformation", transform);
            ListTag tags = new ListTag();
            tags.add(StringTag.valueOf(SPIKE_TAG));
            tag.put("Tags", tags);
            double x = at.x + Math.cos(a) * d;
            double z = at.z + Math.sin(a) * d;
            float yaw = random.nextFloat() * 90.0F;
            Entity spike = EntityType.loadEntityRecursive(tag, level, e ->
            {
                e.moveTo(x, at.y, z, yaw, 0.0F);
                return e;
            });
            if (spike != null && level.addFreshEntity(spike))
            {
                SPIKES_UP.put(spike, level.getGameTime() + HuntsmanTuning.STAMP_SPIKE_TICKS);
            }
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), at.x,
                at.y + 0.5D, at.z, 40, 0.6D, 0.6D, 0.6D, 0.2D);
    }

    private static ListTag floats(float... values)
    {
        ListTag list = new ListTag();
        for (float v : values)
        {
            list.add(FloatTag.valueOf(v));
        }
        return list;
    }

    /** Clears away the spikes whose time is up (every server tick). */
    static void tickSpikes(long now)
    {
        SPIKES_UP.entrySet().removeIf(e ->
        {
            if (e.getKey().isRemoved())
            {
                return true;
            }
            if (now >= e.getValue())
            {
                e.getKey().discard();
                return true;
            }
            return false;
        });
    }

    /** True if {@code e} is one of the spikes still meant to stand. */
    static boolean spikeStanding(Entity e)
    {
        return SPIKES_UP.containsKey(e);
    }

    static void clearSpikes()
    {
        SPIKES_UP.keySet().forEach(Entity::discard);
        SPIKES_UP.clear();
    }

    // ---------------------------------------------------------------- the bars

    /** Which of the Huntsman's bars (its look) it shows now, or null. */
    @Nullable
    static String barLook(HuntMob mob)
    {
        return switch (mob.stage)
        {
            case WILD_HUNT, SPLIT -> mob instanceof AlphaMoose ? "wild_hunt" : null;
            case DUEL -> mob instanceof AlphaMoose ? (mob.angry ? "alpha_moose_angry" : "alpha_moose")
                    : (mob.angry ? "huntsman_angry" : "huntsman");
            default -> null;
        };
    }

    private static void updateBar(HuntMob mob, ServerLevel level)
    {
        String look = mob.isAlive() ? barLook(mob) : null;
        if (look == null)
        {
            if (mob.bar != null && !mob.bar.getPlayers().isEmpty())
            {
                mob.bar.removeAllPlayers();
            }
            return;
        }
        Component name = Component.translatable(HuntsmanBarShowcase.KEY_PREFIX + look);
        if (mob.bar == null)
        {
            mob.bar = new ServerBossEvent(name, BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
            mob.bar.setDarkenScreen(false);
            mob.bar.setPlayBossMusic(false);
            mob.bar.setCreateWorldFog(false);
        }
        else if (!name.equals(mob.bar.getName()))
        {
            mob.bar.setName(name);
        }
        float progress = look.equals("wild_hunt") ? mob.wildHunt / HuntsmanTuning.WILD_HUNT_HEALTH
                : mob.getHealth() / mob.getMaxHealth();
        mob.bar.setProgress(Mth.clamp(progress, 0.0F, 1.0F));
        double range = HuntsmanTuning.BOSS_BAR_RANGE;
        for (ServerPlayer player : level.players())
        {
            if (player.distanceToSqr(mob) <= range * range)
            {
                mob.bar.addPlayer(player);
            }
            else
            {
                mob.bar.removePlayer(player);
            }
        }
        for (ServerPlayer player : new ArrayList<>(mob.bar.getPlayers()))
        {
            if (player.isRemoved() || player.level() != level)
            {
                mob.bar.removePlayer(player);
            }
        }
    }

    /** Its bar (null until it has shown one). For the tests. */
    @Nullable
    static ServerBossEvent bar(HuntMob mob)
    {
        return mob.bar;
    }

    /** True if {@code p} is something they throw (for the friendly-fire rule). */
    static boolean theirs(Projectile p, HuntMob mob)
    {
        return isPartner(mob, p.getOwner());
    }

    /** The registry's types, so other classes don't reach into it for these two. */
    static EntityType<AlphaMoose> mooseType()
    {
        return ModEntities.ALPHA_MOOSE.get();
    }

    static EntityType<Huntsman> huntsmanType()
    {
        return ModEntities.HUNTSMAN.get();
    }
}
