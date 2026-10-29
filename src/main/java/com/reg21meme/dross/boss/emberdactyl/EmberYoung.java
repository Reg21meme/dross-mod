package com.reg21meme.dross.boss.emberdactyl;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.quest.DrossAdvancements;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import static com.reg21meme.dross.boss.emberdactyl.EmberTuning.*;

/**
 * The Emberdactyl's young reborn (design E2A): 175 health, bursting out of its egg when the adult breaks apart (or from
 * its own spawn egg). The same moves, each hit as hard as the adult's, but 2.5 times as fast, with almost no rest between
 * them, a dash straight through you, and smaller tornadoes; it keeps near its nest (the user's stage 2 rule: "bam bam ...
 * no rest"). Beaten, it hardens into an obsidian statue that stands a while, and "Wings Clipped" goes to the player who
 * killed it and everyone near (the user: only when you kill the young; never for {@code /kill}).
 */
public class EmberYoung extends EmberBoss
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double[][] SIZES = {{3.2D, 3.2D}, {2.5D, 3.0D}, {5.0D, 5.0D}, {5.0D, 5.0D}, {4.5D, 6.0D},
            {4.5D, 6.0D}, {2.2D, 2.2D}};
    private static final double[][] FLY = {{0.0D, 9.5D, -9.0D}, {0.0D, 8.5D, -4.5D}, {4.1D, 7.0D, 1.4D},
            {-4.1D, 7.0D, 1.4D}, {7.2D, 4.8D, 4.7D}, {-7.2D, 4.8D, 4.7D}, {0.0D, 4.5D, 5.0D}};
    private static final double[][] GROUND = {{0.0D, 6.3D, -8.8D}, {0.0D, 4.8D, -4.5D}, {1.9D, 0.5D, 0.1D},
            {-1.9D, 0.5D, 0.1D}, {3.3D, 3.0D, 3.6D}, {-3.3D, 3.0D, 3.6D}, {0.0D, 1.5D, 5.0D}};

    public EmberYoung(EntityType<? extends EmberYoung> type, Level level)
    {
        super(type, level, YOUNG, design("E2A"), SIZES, FLY, GROUND);
        this.xpReward = YOUNG_XP;
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, YOUNG.health())
                .add(Attributes.ARMOR, YOUNG.armor())
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 96.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.4D)
                .add(Attributes.FLYING_SPEED, 1.2D);
    }

    @Override
    protected Component barName()
    {
        return Component.translatable("boss.dross.emberdactyl.bar.young");
    }

    @Override
    protected String soundPrefix()
    {
        return "entity.emberyoung";
    }

    @Override
    protected int designNumber()
    {
        return design("E2A");
    }

    @Override
    protected boolean dashes()
    {
        return true;
    }

    /** "Wings Clipped" for whoever beat it (never for {@code /kill}); its volcano's fight is over for good. */
    @Override
    protected void onDeathStart(DamageSource source)
    {
        if (!(this.level() instanceof ServerLevel level))
        {
            return;
        }
        if (this.volcano != 0L)
        {
            EmberVolcanoData.get(level.getServer()).setState(this.volcano, EmberVolcanoData.State.BEATEN);
        }
        if (source.is(DamageTypes.GENERIC_KILL))
        {
            return;
        }
        Set<ServerPlayer> victors = new LinkedHashSet<>();
        ServerPlayer killer = playerBehind(source.getEntity());
        if (killer != null)
        {
            victors.add(killer);
        }
        for (ServerPlayer p : level.players())
        {
            if (!p.isSpectator() && p.distanceToSqr(this) <= VICTOR_RANGE * VICTOR_RANGE)
            {
                victors.add(p);
            }
        }
        for (ServerPlayer p : victors)
        {
            DrossAdvancements.grant(p, DrossAdvancements.WINGS_CLIPPED);
            EmberTrophies.reward(p);
        }
        LOGGER.info("The Emberdactyl's young was beaten at {}; \"Wings Clipped\" for {}", this.blockPosition().toShortString(),
                victors.stream().map(p -> p.getName().getString()).toList());
    }

    /** The player behind a hit: themselves, their arrow's shooter, their pet's owner. */
    static ServerPlayer playerBehind(Entity e)
    {
        if (e instanceof Projectile projectile)
        {
            e = projectile.getOwner();
        }
        if (e instanceof OwnableEntity pet)
        {
            e = pet.getOwner();
        }
        return e instanceof ServerPlayer player ? player : null;
    }

    /** It stands an obsidian statue a while (its death hardens it), then crumbles. */
    @Override
    protected boolean deathTick(int ticks)
    {
        return ticks >= YOUNG_STATUE_TICKS;
    }
}
