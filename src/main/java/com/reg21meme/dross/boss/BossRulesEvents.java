package com.reg21meme.dross.boss;

import com.reg21meme.dross.Dross;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@link BossRules} at the moment of a hit (Forge's event bus): a boss's blows and shots hit harder with more players
 * in its fight (before the victim's armour, like Strength), and one hit takes at most {@link BossRules#HIT_CAP_SHARE}
 * of a boss's full health (after its armour: what it really loses).
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class BossRulesEvents
{
    private BossRulesEvents() {}

    /** A boss's blow, shot or thrown thing (whatever names it as the attacker), times its {@link BossScale#damageFactor}. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event)
    {
        Entity by = event.getSource().getEntity();
        if (by instanceof DrossBoss boss && by != event.getEntity() && boss.followsBossRules())
        {
            float factor = boss.bossScale().damageFactor();
            if (factor > 1.0F)
            {
                event.setAmount(event.getAmount() * factor);
            }
        }
    }

    /** The hit cap, last of all (after its armour and anything else that changes the amount). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event)
    {
        if (event.getEntity() instanceof DrossBoss boss && boss.followsBossRules()
                && !BossRules.uncapped(event.getSource()))
        {
            float cap = boss.hitCapHealth() * BossRules.HIT_CAP_SHARE;
            if (event.getAmount() > cap)
            {
                event.setAmount(cap);
            }
        }
    }
}
