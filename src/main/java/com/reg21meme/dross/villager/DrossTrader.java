package com.reg21meme.dross.villager;

import com.reg21meme.dross.world.PortalSite;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.LookAtTradingPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;

/**
 * The Dross trader: a villager-like NPC with one fixed trade (1 nether star -> Dross Portal Map).
 * He is based on AbstractVillager, so he has no profession, can't breed and never changes jobs.
 * He is persistent (never despawns), invulnerable (he only spawns once, so we don't want him lost)
 * and has no walking goals, so he stays where he spawned.
 */
public class DrossTrader extends AbstractVillager
{
    /** How many times the map can be bought. See the explanation in the report. */
    private static final int MAP_MAX_USES = 3;
    /** Map scale 2 = same zoom as a vanilla explorer map (4 blocks per pixel, 512x512 blocks). */
    private static final byte MAP_SCALE = 2;

    public DrossTrader(EntityType<? extends DrossTrader> type, Level level)
    {
        super(type, level);
        this.setPersistenceRequired();
        this.setInvulnerable(true);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.5D);
    }

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new TradeWithPlayerGoal(this));
        this.goalSelector.addGoal(2, new LookAtTradingPlayerGoal(this));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer)
    {
        return false;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand)
    {
        if (this.isAlive() && !this.isTrading() && !player.isSecondaryUseActive())
        {
            if (!this.level().isClientSide)
            {
                this.setTradingPlayer(player);
                this.openTradingScreen(player, this.getDisplayName(), 1);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    @Override
    protected void updateTrades()
    {
        MerchantOffers offers = this.getOffers();
        if (this.level() instanceof ServerLevel serverLevel)
        {
            offers.add(new MerchantOffer(
                    new ItemStack(Items.NETHER_STAR),
                    createPortalMap(serverLevel),
                    MAP_MAX_USES,
                    0,      // no villager XP
                    0.0F)); // no price changes
        }
    }

    /** Builds the filled map on the server, like a vanilla explorer map: centered on the site with a target marker. */
    private static ItemStack createPortalMap(ServerLevel level)
    {
        ServerLevel overworld = level.getServer().overworld();
        BlockPos site = PortalSite.getFramePos(overworld);
        ItemStack map = MapItem.create(overworld, site.getX(), site.getZ(), MAP_SCALE, true, true);
        MapItem.renderBiomePreviewMap(overworld, map);
        MapItemSavedData.addTargetDecoration(map, site, "+", MapDecoration.Type.TARGET_X);
        map.setHoverName(Component.translatable("filled_map.dross.portal"));
        return map;
    }

    @Override
    protected void rewardTradeXp(MerchantOffer offer)
    {
        // The trader has no levels, so trading gives no XP orbs.
    }

    @Override
    public boolean showProgressBar()
    {
        return false;
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent)
    {
        return null;
    }

    @Override
    protected SoundEvent getAmbientSound()
    {
        return this.isTrading() ? SoundEvents.VILLAGER_TRADE : SoundEvents.VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return SoundEvents.VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return SoundEvents.VILLAGER_DEATH;
    }

    @Override
    protected SoundEvent getTradeUpdatedSound(boolean isYesSound)
    {
        return isYesSound ? SoundEvents.VILLAGER_YES : SoundEvents.VILLAGER_NO;
    }

    @Override
    public SoundEvent getNotifyTradeSound()
    {
        return SoundEvents.VILLAGER_YES;
    }
}
