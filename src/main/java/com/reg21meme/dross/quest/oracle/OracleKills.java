package com.reg21meme.dross.quest.oracle;

import com.reg21meme.dross.Dross;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Counts the kills of the Oracle's KILL quests (QUEST_PLAN.md, "Kills that count"). A death counts for a player only
 * while their quest is that KILL quest and ACTIVE, the mob is in its tag (and grown, if it wants adults only), and the
 * player killed it: their own blow, arrow or thrown spear, their tamed animal's, or their Necromancy risen undead's.
 * A death with no killer named (a burn, a fall) goes to whoever the mob credits with it (vanilla's kill credit: a player
 * who hit it in the last few seconds).
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class OracleKills
{
    /** Marks a mob whose death was already looked at (some mobs' deaths fire the event twice). */
    private static final String COUNTED_TAG = "DrossOracleCounted";

    private OracleKills() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event)
    {
        LivingEntity victim = event.getEntity();
        if (event.isCanceled() || victim.level().isClientSide() || victim instanceof ServerPlayer)
        {
            return;
        }
        if (victim.getPersistentData().getBoolean(COUNTED_TAG))
        {
            return;
        }
        ServerPlayer player = killer(victim, event.getSource());
        if (player == null)
        {
            return;
        }
        victim.getPersistentData().putBoolean(COUNTED_TAG, true);
        if (counts(player, victim))
        {
            OracleProgress.addKill(player);
        }
    }

    /** True if this mob's death counts for the player's current quest right now. */
    public static boolean counts(ServerPlayer player, LivingEntity victim)
    {
        OracleRecord record = OracleProgress.record(player);
        OracleQuest quest = record.quest();
        if (quest.kind() != OracleQuest.Kind.KILL || record.state() != OracleState.ACTIVE || quest.targets() == null)
        {
            return false;
        }
        if (!victim.getType().is(quest.targets()))
        {
            return false;
        }
        return !quest.adultsOnly() || !victim.isBaby();
    }

    /** The player credited with this death, or null. */
    @Nullable
    public static ServerPlayer killer(LivingEntity victim, DamageSource source)
    {
        ServerPlayer player = owner(victim, source.getEntity());
        if (player == null && source.getEntity() == null)
        {
            player = owner(victim, victim.getKillCredit());
        }
        return player;
    }

    /** The player behind this entity: the player themselves, or their tamed animal's owner. */
    @Nullable
    private static ServerPlayer owner(LivingEntity victim, @Nullable Entity entity)
    {
        if (entity == null || victim.getServer() == null)
        {
            return null;
        }
        if (entity instanceof ServerPlayer player)
        {
            return player;
        }
        UUID owner = entity instanceof OwnableEntity ownable ? ownable.getOwnerUUID() : null;
        return owner == null ? null : victim.getServer().getPlayerList().getPlayer(owner);
    }
}
