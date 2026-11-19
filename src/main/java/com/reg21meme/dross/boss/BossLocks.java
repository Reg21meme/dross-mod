package com.reg21meme.dross.boss;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.enchant.RisenUndead;
import com.reg21meme.dross.quest.oracle.OracleBoss;
import com.reg21meme.dross.quest.oracle.OracleRules;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * THE LOCKED BOSSES (QUEST_PLAN.md, "Locked bosses"; server side). A boss <b>at home</b> (the Colossus of a lair, the Rat
 * King of a rat cave, the Huntsman and his Alpha Moose of a village, the Emberdactyl and its young of a world volcano:
 * {@link DrossBoss#lockedAs}) fights only the players whose Oracle quest has reached it (the quest area's rule,
 * {@link OracleRules#mayFightBoss}; players in creative or spectator mode always may). For it, a player it's locked for
 * counts as nobody:
 * <ul>
 *   <li>they don't wake it, make it rise or bring it home (each boss's own keeper and wake-up ask {@link #mayMeet} or
 *       {@link #isLockedFor});</li>
 *   <li>it never picks them as a target, and drops one it had ({@link #onChangeTarget}, {@link #onLivingTick}; each boss's
 *       own target choice skips them too);</li>
 *   <li>nothing credited to them hurts it: their own blows, arrows and thrown spears, their tamed pets, their Necromancy
 *       risen undead ({@link #refusesHit}: the hit is cancelled, nothing else);</li>
 *   <li>they don't count for its extra-players bonus, and don't keep it from its reset ({@link BossRules}).</li>
 * </ul>
 * Its own blows still hurt whoever they hit. A boss from a spawn egg, {@code /dross boss summon}, a test arena or a
 * showcase is never locked (its {@code lockedAs} is null). Its arena is warded too: {@link BossWards}.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class BossLocks
{
    /** A home boss looks at its target every this many ticks, and drops it if it's a player it's locked for. */
    public static final int TARGET_CHECK_TICKS = 10;

    private BossLocks() {}

    // ------------------------------------------------------------------ The rule

    /** The quest boss this one is locked behind while it's at home, or null (not a boss, or not at home). Server side. */
    @Nullable
    public static OracleBoss lockOf(@Nullable Entity boss)
    {
        return boss instanceof DrossBoss b && !boss.level().isClientSide ? b.lockedAs() : null;
    }

    /** True if this boss (at home) is locked for this player: to it they count as nobody. */
    public static boolean isLockedFor(@Nullable Entity boss, @Nullable Entity who)
    {
        if (!(who instanceof Player player))
        {
            return false;
        }
        OracleBoss lock = lockOf(boss);
        return lock != null && !OracleRules.mayFightBoss(player, lock);
    }

    /**
     * True if this player may meet this boss at its home: wake it, make it rise, bring it there (before it even exists).
     * Creative players may (so testers see them); spectators are left out by the keepers themselves.
     */
    public static boolean mayMeet(Player player, OracleBoss boss)
    {
        return OracleRules.mayFightBoss(player, boss);
    }

    /**
     * True if this hit on a boss at home is refused: it's credited to a player the boss is locked for. Damage that
     * bypasses invulnerability ({@code /kill}, the void) is never refused.
     */
    public static boolean refusesHit(LivingEntity boss, DamageSource source)
    {
        OracleBoss lock = lockOf(boss);
        if (lock == null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || boss.getServer() == null)
        {
            return false;
        }
        ServerPlayer player = creditedPlayer(source, boss.getServer());
        return player != null && !OracleRules.mayFightBoss(player, lock);
    }

    // ------------------------------------------------------------------ Who's behind a hit

    /**
     * The player a hit is credited to: the player themselves (their blow, or their arrow or thrown spear: the damage's
     * attacker), their tamed animal's owner, their risen undead's owner (a risen skeleton's arrow too, even after the
     * skeleton has crumbled). Null for anything else, or an owner who isn't online.
     */
    @Nullable
    public static ServerPlayer creditedPlayer(DamageSource source, MinecraftServer server)
    {
        ServerPlayer player = playerBehind(source.getEntity(), server);
        if (player == null)
        {
            player = playerBehind(source.getDirectEntity(), server);
        }
        Entity direct = source.getDirectEntity();
        if (player == null && direct != null && direct.getPersistentData().hasUUID(RisenUndead.PROJECTILE_OWNER_KEY))
        {
            player = server.getPlayerList().getPlayer(direct.getPersistentData().getUUID(RisenUndead.PROJECTILE_OWNER_KEY));
        }
        return player;
    }

    /** The player behind this entity: the player themselves, their risen undead's or tamed animal's owner, a shot's shooter's. */
    @Nullable
    public static ServerPlayer playerBehind(@Nullable Entity entity, MinecraftServer server)
    {
        for (int depth = 0; entity != null && depth < 4; depth++)
        {
            if (entity instanceof ServerPlayer player)
            {
                return player;
            }
            UUID owner = RisenUndead.getOwnerId(entity);
            if (owner == null && entity instanceof OwnableEntity ownable)
            {
                owner = ownable.getOwnerUUID();
            }
            if (owner != null)
            {
                return server.getPlayerList().getPlayer(owner);
            }
            entity = entity instanceof Projectile projectile ? projectile.getOwner() : null;
        }
        return null;
    }

    // ------------------------------------------------------------------ The hooks

    /** A hit credited to a player the boss (at home) is locked for is cancelled. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event)
    {
        LivingEntity victim = event.getEntity();
        if (!victim.level().isClientSide && refusesHit(victim, event.getSource()))
        {
            event.setCanceled(true);
        }
    }

    /** A boss at home never takes a player it's locked for as its target. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onChangeTarget(LivingChangeTargetEvent event)
    {
        LivingEntity boss = event.getEntity();
        if (!boss.level().isClientSide && isLockedFor(boss, event.getNewTarget()))
        {
            event.setNewTarget(null);
        }
    }

    /** ...and lets go of one it had (their quest went back, say). */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event)
    {
        if (event.getEntity() instanceof Mob mob && mob instanceof DrossBoss && !mob.level().isClientSide
                && mob.tickCount % TARGET_CHECK_TICKS == 0 && mob.getTarget() != null && isLockedFor(mob, mob.getTarget()))
        {
            mob.setTarget(null);
        }
    }
}
