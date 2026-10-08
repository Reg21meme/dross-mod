package com.reg21meme.dross.villager;

import com.reg21meme.dross.registry.ModItems;
import com.reg21meme.dross.registry.ModParticles;
import com.reg21meme.dross.world.PortalSite;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.StructureTags;
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
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Dross trader: a villager-like NPC with fixed trades (nether star -> Dross Compass,
 * Dross Portal Frame -> Admin Sword). He is based on AbstractVillager, so he has no profession,
 * can't breed and never changes jobs. He is persistent (never despawns).
 * <p>
 * He can be hurt and knocked back but can never die (he only spawns once, so we don't want him lost):
 * at half health or less he teleports home and fully heals.
 * <p>
 * He lives in a hut (see {@link TraderHut}) and wanders within {@link #LEASH} blocks of it.
 * If he strays too far, falls, or ends up underground, he teleports back into the hut.
 * A trader from a spawn egg treats the spot he was spawned at as his home.
 * While any player is within his area, he glows gold (visible through walls) so he's easy to find.
 */
public class DrossTrader extends AbstractVillager
{
    /** How many times the compass can be bought. He never restocks. */
    private static final int COMPASS_MAX_USES = 3;
    /** The Admin Sword trade has no real limit. */
    private static final int ADMIN_SWORD_MAX_USES = Integer.MAX_VALUE;

    /** He stays within this many blocks of his hut in X and Z. */
    private static final int LEASH = 50;
    /** Falling more than this many blocks sends him home. */
    private static final float MAX_FALL = 4.0F;
    /** Being more than this many blocks below the surface sends him home. */
    private static final int MAX_DEPTH = 3;
    private static final String TAG_HOME = "HomePos";
    /** Glow outline colour: gold, to match the Dross portal (same as ChatFormatting.GOLD). */
    private static final int GLOW_COLOR = 0xFFAA00;

    /** The middle of his hut floor (or his spawn spot, for a spawn-egg trader). Set on his first tick if missing. */
    @Nullable
    private BlockPos home;

    public DrossTrader(EntityType<? extends DrossTrader> type, Level level)
    {
        super(type, level);
        this.setPersistenceRequired();
        ((GroundPathNavigation) this.getNavigation()).setCanOpenDoors(true);
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
        this.goalSelector.addGoal(1, new LookAtTradingPlayerGoal(this));
        this.goalSelector.addGoal(2, new OpenDoorGoal(this, true)); // opens his door, and closes it behind him
        this.goalSelector.addGoal(3, new MoveTowardsRestrictionGoal(this, 0.6D));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.5D));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    // ---------------------------------------------------------------- home

    /** Sets the hut he lives in. He wanders within {@link #LEASH} blocks of it. */
    public void setHome(BlockPos center)
    {
        this.home = center.immutable();
        this.restrictTo(this.home, LEASH);
    }

    @Override
    public void aiStep()
    {
        super.aiStep();
        if (!(this.level() instanceof ServerLevel serverLevel))
        {
            return;
        }
        if (this.home == null)
        {
            // A spawn-egg (or /summon) trader: wherever he first appears is his home.
            this.setHome(this.blockPosition());
        }
        if (this.shouldGoHome(serverLevel))
        {
            this.teleportHome(serverLevel);
        }
        if (this.tickCount % 20 == 0)
        {
            this.updateGlow(serverLevel);
            this.rememberPosition(serverLevel);
        }
    }

    /** Glow (through walls) while any player is within his area, so he's easy to find. */
    private void updateGlow(ServerLevel level)
    {
        boolean playerNearby = false;
        for (ServerPlayer player : level.players())
        {
            if (Math.abs(player.getX() - this.home.getX()) <= LEASH && Math.abs(player.getZ() - this.home.getZ()) <= LEASH)
            {
                playerNearby = true;
                break;
            }
        }
        if (playerNearby != this.hasGlowingTag())
        {
            this.setGlowingTag(playerNearby);
        }
    }

    /** The glow outline uses this colour (normally a scoreboard team colour; we don't need a team). */
    @Override
    public int getTeamColor()
    {
        return GLOW_COLOR;
    }

    /** If this is the hut trader, remember where he is, so /dross trader can find him when he isn't loaded. */
    private void rememberPosition(ServerLevel level)
    {
        TraderSpawnData data = TraderSpawnData.get(level.getServer().overworld());
        if (this.getUUID().equals(data.getTraderId()))
        {
            data.setLastPos(this.blockPosition());
        }
    }

    /**
     * He takes damage and knockback normally, but a hit can never take his last bit of health,
     * so he never dies. At half health or less he teleports home and heals fully.
     */
    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        if (!(this.level() instanceof ServerLevel serverLevel))
        {
            return super.hurt(source, amount);
        }
        boolean wasHurt = super.hurt(source, Math.min(amount, Math.max(this.getHealth() - 1.0F, 0.0F)));
        if (this.isAlive() && this.getHealth() <= this.getMaxHealth() / 2.0F)
        {
            this.setHealth(this.getMaxHealth());
            if (this.home != null)
            {
                this.teleportHome(serverLevel);
            }
        }
        return wasHurt;
    }

    private boolean shouldGoHome(ServerLevel level)
    {
        BlockPos pos = this.blockPosition();
        if (Math.abs(pos.getX() - this.home.getX()) > LEASH || Math.abs(pos.getZ() - this.home.getZ()) > LEASH)
        {
            return true;
        }
        if (this.fallDistance > MAX_FALL)
        {
            return true;
        }
        // Underground check, twice a second. Skipped under a roof that's meant to be there:
        // his own hut, or any village building.
        if (this.tickCount % 10 == 0)
        {
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
            return pos.getY() < surface - MAX_DEPTH
                    && !this.isInsideHut(pos)
                    && !level.structureManager().getStructureWithPieceAt(pos, StructureTags.VILLAGE).isValid();
        }
        return false;
    }

    private boolean isInsideHut(BlockPos pos)
    {
        return Math.abs(pos.getX() - this.home.getX()) <= TraderHut.HALF
                && Math.abs(pos.getZ() - this.home.getZ()) <= TraderHut.HALF
                && pos.getY() >= this.home.getY() - 1 && pos.getY() <= this.home.getY() + 3;
    }

    /** Enderman-style teleport into the middle of his hut, with orange particles. Also used by /dross trader home. */
    public void teleportHome(ServerLevel level)
    {
        if (this.home == null)
        {
            return;
        }
        this.teleportEffects(level);
        this.getNavigation().stop();
        this.teleportTo(this.home.getX() + 0.5D, this.home.getY(), this.home.getZ() + 0.5D);
        this.setDeltaMovement(Vec3.ZERO);
        this.fallDistance = 0.0F;
        this.teleportEffects(level);
    }

    private void teleportEffects(ServerLevel level)
    {
        level.sendParticles(ModParticles.DROSS_PORTAL.get(),
                this.getX(), this.getY() + this.getBbHeight() / 2.0D, this.getZ(),
                64, this.getBbWidth(), this.getBbHeight() / 2.0D, this.getBbWidth(), 0.5D);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENDERMAN_TELEPORT, this.getSoundSource(), 1.0F, 1.0F);
    }

    /** He never goes through portals (his home is in the Overworld). */
    @Override
    public boolean canChangeDimensions()
    {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        if (this.home != null)
        {
            tag.put(TAG_HOME, NbtUtils.writeBlockPos(this.home));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        // Traders saved before he could take damage were invulnerable; he isn't any more (he just can't die).
        this.setInvulnerable(false);
        if (tag.contains(TAG_HOME))
        {
            this.setHome(NbtUtils.readBlockPos(tag.getCompound(TAG_HOME)));
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer)
    {
        return false;
    }

    // ---------------------------------------------------------------- trading

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
                    createCompass(serverLevel),
                    COMPASS_MAX_USES,
                    0,      // no villager XP
                    0.0F)); // no price changes
            // TESTING ONLY: see "Parked for later" in CLAUDE.md.
            offers.add(new MerchantOffer(
                    new ItemStack(ModItems.DROSS_PORTAL_FRAME.get()),
                    new ItemStack(ModItems.ADMIN_SWORD.get()),
                    ADMIN_SWORD_MAX_USES,
                    0,
                    0.0F));
        }
    }

    /**
     * The Dross Compass: a vanilla compass with lodestone-style data pointing at the portal site
     * in the Overworld. Lodestone tracking is off, so it keeps pointing there without a lodestone.
     */
    private static ItemStack createCompass(ServerLevel level)
    {
        ServerLevel overworld = level.getServer().overworld();
        // Aim at the frame's opening (the frame's bottom corner + 1 in X and Y).
        BlockPos target = PortalSite.getFramePos(overworld).offset(1, 1, 0);
        ItemStack compass = new ItemStack(Items.COMPASS);
        CompoundTag tag = compass.getOrCreateTag();
        tag.put(CompassItem.TAG_LODESTONE_POS, NbtUtils.writeBlockPos(target));
        Level.RESOURCE_KEY_CODEC.encodeStart(NbtOps.INSTANCE, Level.OVERWORLD).result()
                .ifPresent(dimension -> tag.put(CompassItem.TAG_LODESTONE_DIMENSION, dimension));
        tag.putBoolean(CompassItem.TAG_LODESTONE_TRACKED, false);
        compass.setHoverName(Component.translatable("item.dross.dross_compass").withStyle(style -> style.withItalic(false)));
        return compass;
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

    // ---------------------------------------------------------------- sounds

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
