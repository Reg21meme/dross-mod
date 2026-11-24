package com.reg21meme.dross.villager;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.quest.DrossAdvancements;
import com.reg21meme.dross.quest.DrossGuideBookItem;
import com.reg21meme.dross.quest.trader.TraderDialogue;
import com.reg21meme.dross.quest.trader.TraderProgress;
import com.reg21meme.dross.registry.ModItems;
import com.reg21meme.dross.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * The Dross trader: a villager-like NPC who gives the quest into the Dross (PRE_DROSS_PLAN.md: his story, three knight
 * ruins, the Portal Orb and where the cathedral is; the quest area's {@code quest.trader}) and, once he's made a player
 * their orb, sells the Dross Guide Book to those who have been to the Dross. He is based on AbstractVillager, so he has
 * no profession, can't breed and never changes jobs. He is persistent (never despawns).
 * <p>
 * Right-click (main hand, not sneaking): see {@link #mobInteract}. Over him floats each player's marker for his quest,
 * like the Blind Oracle's. All his lines are lang keys named {@code dross.trader.*}.
 * <p>
 * He can be hurt and knocked back but can never die (a world only ever gets its one trader, so we don't want him lost):
 * at half health or less he teleports home and fully heals.
 * <p>
 * He lives in the world's one Rift Chapel ({@link TraderHut}, {@link RiftChapels}: at the edge of a village the seed
 * picks, made with the land, with him in it) and wanders within {@link #LEASH} blocks of it. On his first tick he's
 * checked against the "once a village" rule ({@link TraderVillageData}): a second trader from a village that already had
 * him (or while the world has its trader) is removed at once.
 * If he strays too far, falls, or ends up underground, he teleports back into the hut (home is his spot in it).
 * The hut's whole footprint is saved on him ({@code HutBox}), so a roof over his head never counts as
 * "underground". A trader from a spawn egg (or {@code /summon}) treats the spot he was spawned at as his home and,
 * having no hut footprint, uses a small box round that spot instead ({@link #EGG_HOME_HALF}).
 * While any player is within his area, he glows electric blue (visible through walls) so he's easy to find.
 * <p>
 * Every trader wears the quest trader's skin, skin 2, The Archivist ({@link TraderSkins#QUEST_TRADER}).
 */
public class DrossTrader extends AbstractVillager
{
    private static final Logger LOGGER = LogUtils.getLogger();

    // ---- His shop (after the quest): only the Dross Guide Book (the user, November 23, 2026). ----
    /** Plain books for the Dross Guide Book (no emeralds). Only offered to players who have "Enter the Dross". */
    private static final int GUIDE_BOOK_COST_BOOKS = 3;
    /** The shop has no real use limit. */
    private static final int SHOP_MAX_USES = Integer.MAX_VALUE;

    // ---- Making a Portal Orb: the puff between his hands ----
    /** How far in front of him (blocks) and how far up his height the puff is. */
    private static final double ORB_PUFF_FORWARD = 0.5D;
    private static final double ORB_PUFF_HEIGHT = 0.55D;
    /** The portal's blue particles and the smoke in the puff. */
    private static final int ORB_PUFF_PARTICLES = 40;
    private static final int ORB_PUFF_SMOKE = 8;

    /** He stays within this many blocks of his hut in X and Z. */
    private static final int LEASH = 50;
    /** Falling more than this many blocks sends him home. */
    private static final float MAX_FALL = 4.0F;
    /** Being more than this many blocks below the surface sends him home. */
    private static final int MAX_DEPTH = 3;
    /**
     * A trader with no hut footprint (from a spawn egg or {@code /summon}) counts the 5x5 round his home (home +- this
     * many blocks in X and Z) as "inside my hut" for the underground rule.
     */
    private static final int EGG_HOME_HALF = 2;
    private static final String TAG_HOME = "HomePos";
    /** The hut footprint, saved as six ints: min X, Y, Z, then max X, Y, Z. */
    private static final String TAG_HUT_BOX = "HutBox";
    /** True once his chapel's trees are felled. */
    private static final String TAG_TIDIED = "ChapelTidied";
    /** His chapel's middle (its ground block) and the way its door faces (a 2D direction number). */
    private static final String TAG_CHAPEL_CENTER = "ChapelCenter";
    private static final String TAG_CHAPEL_FACING = "ChapelFacing";
    /** His village's key (its start chunk; his chapel's own chunk for one on its own): only a chapel's trader has it. */
    private static final String TAG_VILLAGE = "TraderVillage";

    /** His spot inside his hut (or his spawn spot, for a spawn-egg trader). Set on his first tick if missing. */
    @Nullable
    private BlockPos home;
    /** Everything his hut covers, floor to roof, or null if he has none saved (see {@link #isInsideHut}). */
    @Nullable
    private BoundingBox hutBox;
    /** His Rift Chapel's middle and the way its door faces (null for a spawn-egg trader): what its tidy-up puts back. */
    @Nullable
    private BlockPos chapelCenter;
    @Nullable
    private Direction chapelFacing;
    /** True once the trees that grew into his chapel have been felled ({@link #tidyChapel}); saved. */
    private boolean chapelTidied;
    /** The felling under way, if any (not saved: it starts again from the start, which gives the same result). */
    @Nullable
    private RiftChapelTidy tidy;
    /** His village's key (null for a spawn-egg trader): what the "once a village" rule counts. */
    @Nullable
    private Long village;
    /** True once he's been checked against the "once a village" rule this time he's loaded (not saved: checked every load). */
    private boolean admitted;

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

    /** Sets his home, with no hut footprint (a spawn-egg trader). He wanders within {@link #LEASH} blocks of it. */
    public void setHome(BlockPos center)
    {
        this.setHome(center, null);
    }

    /**
     * Sets his home: his spot inside his hut, and the hut's footprint (null if there is none). He wanders within
     * {@link #LEASH} blocks of it. The footprint is copied, so the caller's box can't change under him.
     */
    public void setHome(BlockPos center, @Nullable BoundingBox hut)
    {
        this.home = center.immutable();
        this.hutBox = hut == null ? null : hut.moved(0, 0, 0);
        this.restrictTo(this.home, LEASH);
    }

    /** Which Rift Chapel is his: its middle and the way its door faces (set when the chapel is made with the land). */
    public void setChapel(BlockPos center, Direction facing)
    {
        this.chapelCenter = center.immutable();
        this.chapelFacing = facing;
    }

    /** Which village he came to (its start chunk), set when his chapel is made with the land. */
    public void setVillage(long village)
    {
        this.village = village;
    }

    /** His village's key, or null for a spawn-egg (or {@code /summon}) trader. */
    @Nullable
    public Long getVillage()
    {
        return this.village;
    }

    /** His home (his spot in his chapel, or a spawn-egg trader's spawn spot), or null before his first tick. */
    @Nullable
    public BlockPos getHome()
    {
        return this.home;
    }

    /** His chapel's footprint, or null for a spawn-egg (or {@code /summon}) trader. */
    @Nullable
    public BoundingBox getHutBox()
    {
        return this.hutBox;
    }

    /** True once the trees that grew into his chapel have been felled (never for a trader with no chapel). */
    public boolean isChapelTidied()
    {
        return this.chapelTidied;
    }

    @Override
    public void aiStep()
    {
        super.aiStep();
        if (!(this.level() instanceof ServerLevel serverLevel))
        {
            return;
        }
        if (!this.admitted && this.village != null && !this.admit(serverLevel))
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
        }
        if (!this.chapelTidied && this.hutBox != null)
        {
            this.tidyChapel(serverLevel);
        }
    }

    /**
     * The "once a village" rule ({@link TraderVillageData#admit}): true if he may stay; otherwise he's removed (a village
     * that already had the trader never gets another, and a world has only one).
     */
    private boolean admit(ServerLevel level)
    {
        this.admitted = true;
        TraderVillageData data = TraderVillageData.get(level.getServer().overworld());
        if (data.admit(this.getUUID(), this.village))
        {
            return true;
        }
        LOGGER.warn("[Dross] A second Dross trader came to {} (village chunk {}, {}): {}, so he's removed", this.blockPosition().toShortString(),
                ChunkPos.getX(this.village), ChunkPos.getZ(this.village), data.hadTrader(this.village)
                        ? "that village already had its trader" : "the world already has its trader, " + data.trader());
        this.discard();
        return false;
    }

    /** A chapel trader can only die by {@code /kill} (or the like): the world has no trader then ({@link TraderVillageData#forget}). */
    @Override
    public void die(DamageSource source)
    {
        super.die(source);
        if (this.level() instanceof ServerLevel level && this.village != null)
        {
            TraderVillageData.get(level.getServer().overworld()).forget(this.getUUID());
        }
    }

    /**
     * Once, the first time the land round his chapel is loaded: the trees that grew into it after it was made with the
     * land (Minecraft's trees come after the structures, in its chunks and their neighbours') are felled, whole (the
     * portal site's {@code TreeFelling}, in steps of at most about 40 ms a tick; {@link RiftChapelTidy}). Saved as done.
     */
    private void tidyChapel(ServerLevel level)
    {
        if (this.tidy == null)
        {
            if (this.tickCount % 20 != 0 || !RiftChapelTidy.landLoaded(level, this.hutBox))
            {
                return;
            }
            this.tidy = new RiftChapelTidy(level, this.hutBox, this.chapelCenter, this.chapelFacing);
        }
        if (this.tidy.step())
        {
            this.tidy.log();
            this.tidy = null;
            this.chapelTidied = true;
        }
    }

    /** Glow (blue, through walls) while any player is within his area, so he's easy to find. */
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
        return DrossColors.TRADER_GLOW;
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

    /**
     * Is this spot inside his own hut? This is what stops the "too far below the surface" rule from sending him
     * home while he stands under his own roof (under the chapel's 18-block-tall roof that rule would be true
     * everywhere inside).
     * <ul>
     *   <li>With a saved hut footprint: inside the whole 3D box, so a cave under the hut doesn't count.</li>
     *   <li>Without one (a spawn-egg or {@code /summon} trader): 5x5 around home, from one block below home to
     *       three above.</li>
     * </ul>
     */
    private boolean isInsideHut(BlockPos pos)
    {
        if (this.hutBox != null)
        {
            return this.hutBox.isInside(pos);
        }
        return Math.abs(pos.getX() - this.home.getX()) <= EGG_HOME_HALF
                && Math.abs(pos.getZ() - this.home.getZ()) <= EGG_HOME_HALF
                && pos.getY() >= this.home.getY() - 1 && pos.getY() <= this.home.getY() + 3;
    }

    /**
     * Enderman-style teleport into the middle of his hut, with the portal's blue particles. The one shared
     * "go home" path: too far, fell, too deep, half health and /dross trader sendhome all use it.
     * He leaves any vehicle first (the vehicle is left behind).
     */
    public void teleportHome(ServerLevel level)
    {
        if (this.home == null)
        {
            return;
        }
        // Get out of any boat/minecart/horse first (the vehicle stays where it is), and drop any
        // passengers, so nothing is dragged along or blocks the move. Only then play the effects.
        this.stopRiding();
        this.ejectPassengers();
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
        removeGuideOfferFromTag(tag);
        if (this.home != null)
        {
            tag.put(TAG_HOME, NbtUtils.writeBlockPos(this.home));
        }
        if (this.hutBox != null)
        {
            tag.putIntArray(TAG_HUT_BOX, new int[] {
                    this.hutBox.minX(), this.hutBox.minY(), this.hutBox.minZ(),
                    this.hutBox.maxX(), this.hutBox.maxY(), this.hutBox.maxZ()});
        }
        tag.putBoolean(TAG_TIDIED, this.chapelTidied);
        if (this.chapelCenter != null && this.chapelFacing != null)
        {
            tag.put(TAG_CHAPEL_CENTER, NbtUtils.writeBlockPos(this.chapelCenter));
            tag.putInt(TAG_CHAPEL_FACING, this.chapelFacing.get2DDataValue());
        }
        if (this.village != null)
        {
            tag.putLong(TAG_VILLAGE, this.village);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        // The guide book offer is per player and never kept between sessions.
        this.getOffers().removeIf(DrossTrader::isGuideBookOffer);
        if (tag.contains(TAG_HOME))
        {
            // Read the hut footprint first, so home and footprint are set together, once. (fromCorners, not the
            // six-number constructor, which throws if a saved box is ever the wrong way round.)
            BoundingBox hut = null;
            int[] corners = tag.getIntArray(TAG_HUT_BOX);
            if (corners.length == 6)
            {
                hut = BoundingBox.fromCorners(
                        new Vec3i(corners[0], corners[1], corners[2]),
                        new Vec3i(corners[3], corners[4], corners[5]));
            }
            this.setHome(NbtUtils.readBlockPos(tag.getCompound(TAG_HOME)), hut);
        }
        this.chapelTidied = tag.getBoolean(TAG_TIDIED);
        if (tag.contains(TAG_CHAPEL_CENTER, Tag.TAG_COMPOUND))
        {
            this.setChapel(NbtUtils.readBlockPos(tag.getCompound(TAG_CHAPEL_CENTER)), Direction.from2DDataValue(tag.getInt(TAG_CHAPEL_FACING)));
        }
        this.village = tag.contains(TAG_VILLAGE, Tag.TAG_LONG) ? tag.getLong(TAG_VILLAGE) : null;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer)
    {
        return false;
    }

    // ---------------------------------------------------------------- talking and trading

    /**
     * Right-click. Only the main hand counts, and sneaking falls through to vanilla. Server side: his quest's next line or
     * two ({@link TraderDialogue}: his offer, the knight ruins, the Portal Orb, then his reminders; PRE_DROSS_PLAN.md).
     * Once he's made this player an orb, his shop opens after his line if he has something for them (only the Dross
     * Guide Book, for players who have "Enter the Dross"); the trading screen never opens before that.
     */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand)
    {
        if (!this.isAlive() || this.isTrading() || player.isSecondaryUseActive())
        {
            return super.mobInteract(player, hand);
        }
        if (hand != InteractionHand.MAIN_HAND)
        {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer)
        {
            this.talkTo(serverPlayer);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    private void talkTo(ServerPlayer player)
    {
        TraderDialogue.Result result = TraderDialogue.talk(player, new TraderDialogue.Speaker()
        {
            @Override
            public void say(Component line)
            {
                DrossTrader.this.say(player, line);
            }

            @Override
            public void conjure()
            {
                DrossTrader.this.conjureOrb();
            }
        });
        this.playSound(SoundEvents.VILLAGER_AMBIENT, 1.0F, this.getVoicePitch());
        if (result == TraderDialogue.Result.SHOP && TraderProgress.isShopOpen(player))
        {
            this.updateGuideBookOffer(player);
            if (!this.getOffers().isEmpty())
            {
                this.setTradingPlayer(player);
                this.openTradingScreen(player, this.getDisplayName(), 1);
            }
        }
    }

    /** Says one line in chat to this player, like a player would: {@code <Dross Trader> line}. */
    private void say(ServerPlayer player, Component line)
    {
        player.sendSystemMessage(Component.translatable("chat.type.text", this.getDisplayName(), line));
    }

    /**
     * He makes a Portal Orb in his hands (the quest area gives it): a puff of the portal's blue and some smoke between his
     * hands, a chime of glass and his "yes".
     */
    private void conjureOrb()
    {
        if (!(this.level() instanceof ServerLevel level))
        {
            return;
        }
        Vec3 hands = this.position().add(this.getLookAngle().multiply(ORB_PUFF_FORWARD, 0.0D, ORB_PUFF_FORWARD))
                .add(0.0D, this.getBbHeight() * ORB_PUFF_HEIGHT, 0.0D);
        level.sendParticles(ModParticles.DROSS_PORTAL.get(), hands.x, hands.y, hands.z, ORB_PUFF_PARTICLES, 0.2D, 0.2D, 0.2D, 0.6D);
        level.sendParticles(ParticleTypes.POOF, hands.x, hands.y, hands.z, ORB_PUFF_SMOKE, 0.15D, 0.15D, 0.15D, 0.02D);
        level.playSound(null, hands.x, hands.y, hands.z, SoundEvents.ENCHANTMENT_TABLE_USE, this.getSoundSource(), 1.0F, 1.0F);
        level.playSound(null, hands.x, hands.y, hands.z, SoundEvents.AMETHYST_BLOCK_CHIME, this.getSoundSource(), 1.0F, 0.8F);
        this.playSound(SoundEvents.VILLAGER_CELEBRATE, 1.0F, this.getVoicePitch());
    }

    // ---------------------------------------------------------------- the shop

    @Override
    protected void updateTrades()
    {
        this.getOffers().addAll(buildOffers());
    }

    /**
     * His shop's lasting offers (same for everyone; the screen only opens for players he's made a Portal Orb): none since
     * Necromancy and Deathforged were removed (November 23, 2026). The Guide Book comes and goes per player
     * ({@link #updateGuideBookOffer}).
     */
    private static List<MerchantOffer> buildOffers()
    {
        return new ArrayList<>();
    }

    /**
     * The Dross Guide Book offer lives only while a player who has "Enter the Dross" is trading:
     * it is added just before the screen opens, removed when the trade ends, and never saved.
     */
    private void updateGuideBookOffer(ServerPlayer player)
    {
        MerchantOffers offers = this.getOffers();
        offers.removeIf(DrossTrader::isGuideBookOffer);
        if (DrossAdvancements.has(player, DrossAdvancements.ENTERED_THE_DROSS))
        {
            offers.add(new MerchantOffer(
                    new ItemStack(Items.BOOK, GUIDE_BOOK_COST_BOOKS),
                    DrossGuideBookItem.create(),   // a book with its pages already written in
                    SHOP_MAX_USES,
                    0,
                    0.0F));
        }
    }

    /** When the trade ends (screen closed, or he's hurt, etc.), the per-player offer goes away. */
    @Override
    public void setTradingPlayer(@Nullable Player player)
    {
        super.setTradingPlayer(player);
        if (player == null)
        {
            this.getOffers().removeIf(DrossTrader::isGuideBookOffer);
        }
    }

    private static boolean isGuideBookOffer(MerchantOffer offer)
    {
        return offer.getResult().is(ModItems.DROSS_GUIDE_BOOK.get());
    }

    /** Takes the guide book offer out of the saved data (an offer could be present if he's saved mid-trade). */
    private static void removeGuideOfferFromTag(CompoundTag tag)
    {
        if (!tag.contains("Offers", Tag.TAG_COMPOUND))
        {
            return;
        }
        ListTag recipes = tag.getCompound("Offers").getList("Recipes", Tag.TAG_COMPOUND);
        String guideId = String.valueOf(ForgeRegistries.ITEMS.getKey(ModItems.DROSS_GUIDE_BOOK.get()));
        recipes.removeIf(entry -> entry instanceof CompoundTag recipe
                && guideId.equals(recipe.getCompound("sell").getString("id")));
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
