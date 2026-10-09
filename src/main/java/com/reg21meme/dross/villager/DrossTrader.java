package com.reg21meme.dross.villager;

import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.enchant.NecromancyScheme;
import com.reg21meme.dross.quest.DrossAdvancements;
import com.reg21meme.dross.quest.DrossGuideBookItem;
import com.reg21meme.dross.quest.QuestProgress;
import com.reg21meme.dross.quest.QuestRequirement;
import com.reg21meme.dross.registry.ModEnchantments;
import com.reg21meme.dross.registry.ModItems;
import com.reg21meme.dross.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
import net.minecraft.world.entity.item.ItemEntity;
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
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Dross trader: a villager-like NPC who runs the early-game quest and, once a player has finished it,
 * sells Necromancy and Deathforged books. He is based on AbstractVillager, so he has no profession,
 * can't breed and never changes jobs. He is persistent (never despawns).
 * <p>
 * Right-click (main hand, not sneaking): see {@link #mobInteract}. Before a player has finished the quest
 * his trading screen never opens. All his lines are lang keys named {@code dross.trader.dialogue.*}.
 * <p>
 * He can be hurt and knocked back but can never die (he only spawns once, so we don't want him lost):
 * at half health or less he teleports home and fully heals.
 * <p>
 * He lives in a hut (see {@link TraderHut}; today the Rift Chapel) and wanders within {@link #LEASH} blocks of it.
 * If he strays too far, falls, or ends up underground, he teleports back into the hut (home is his spot in it).
 * The hut's whole footprint is saved on him ({@code HutBox}), so a roof over his head never counts as
 * "underground". Traders from before that (the old 5x5 netherite hut) and spawn-egg traders have no footprint
 * and keep the old small-hut rule. A trader from a spawn egg treats the spot he was spawned at as his home.
 * While any player is within his area, he glows electric blue (visible through walls) so he's easy to find.
 */
public class DrossTrader extends AbstractVillager
{
    // ---- Shop prices (after the quest). Each costs emeralds plus one plain book. ----
    /** Emeralds for Necromancy level I (Necromancy has five levels; he sells only the lowest). */
    private static final int NECROMANCY_EMERALDS = 32;
    /** Emeralds for Deathforged I. */
    private static final int DEATHFORGED_EMERALDS = 24;
    /** The Deathforged level he sells. */
    private static final int DEATHFORGED_LEVEL = 1;
    /** Plain books for the Dross Guide Book (no emeralds). Only offered to players who have "Enter the Dross". */
    private static final int GUIDE_BOOK_COST_BOOKS = 3;
    /** The shop has no real use limit. */
    private static final int SHOP_MAX_USES = Integer.MAX_VALUE;
    /**
     * Bump this whenever the shop changes. A trader saved with a different number gets his saved offers
     * replaced by the current shop once, when he loads. (Traders saved before this existed have no number.)
     */
    private static final int SHOP_VERSION = 2;
    private static final String TAG_SHOP_VERSION = "ShopVersion";

    /** He stays within this many blocks of his hut in X and Z. */
    private static final int LEASH = 50;
    /** Falling more than this many blocks sends him home. */
    private static final float MAX_FALL = 4.0F;
    /** Being more than this many blocks below the surface sends him home. */
    private static final int MAX_DEPTH = 3;
    /**
     * The old hut was 5x5 (home +- this many blocks in X and Z). A trader with no saved hut footprint (one from
     * before the chapel, or from a spawn egg) uses this rule for "inside my hut".
     */
    private static final int LEGACY_HUT_HALF = 2;
    private static final String TAG_HOME = "HomePos";
    /** The hut footprint, saved as six ints: min X, Y, Z, then max X, Y, Z. */
    private static final String TAG_HUT_BOX = "HutBox";

    /** His spot inside his hut (or his spawn spot, for a spawn-egg trader). Set on his first tick if missing. */
    @Nullable
    private BlockPos home;
    /** Everything his hut covers, floor to roof, or null if he has none saved (see {@link #isInsideHut}). */
    @Nullable
    private BoundingBox hutBox;

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

    /**
     * Is this spot inside his own hut? This is what stops the "too far below the surface" rule from sending him
     * home while he stands under his own roof (under the chapel's 18-block-tall roof that rule would be true
     * everywhere inside).
     * <ul>
     *   <li>With a saved hut footprint: inside the whole 3D box, so a cave under the hut doesn't count.</li>
     *   <li>Without one (old 5x5 netherite hut, spawn egg): the old small-hut rule, 5x5 around home,
     *       from one block below home to three above.</li>
     * </ul>
     */
    private boolean isInsideHut(BlockPos pos)
    {
        if (this.hutBox != null)
        {
            return this.hutBox.isInside(pos);
        }
        return Math.abs(pos.getX() - this.home.getX()) <= LEGACY_HUT_HALF
                && Math.abs(pos.getZ() - this.home.getZ()) <= LEGACY_HUT_HALF
                && pos.getY() >= this.home.getY() - 1 && pos.getY() <= this.home.getY() + 3;
    }

    /**
     * Enderman-style teleport into the middle of his hut, with the portal's blue particles. The one shared
     * "go home" path: too far, fell, too deep, half health and /dross trader home all use it.
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
        tag.putInt(TAG_SHOP_VERSION, SHOP_VERSION);
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
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        // Traders saved before he could take damage were invulnerable; he isn't any more (he just can't die).
        this.setInvulnerable(false);
        // The guide book offer is per player and never kept between sessions.
        this.getOffers().removeIf(DrossTrader::isGuideBookOffer);
        if (tag.getInt(TAG_SHOP_VERSION) != SHOP_VERSION)
        {
            // One-time migration: an old trader's saved trades are replaced by the current shop.
            MerchantOffers offers = this.getOffers();
            offers.clear();
            offers.addAll(buildOffers());
        }
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
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer)
    {
        return false;
    }

    // ---------------------------------------------------------------- talking and trading

    /**
     * Right-click. Only the main hand counts, and sneaking falls through to vanilla. Server side, in order:
     * <ol>
     *   <li>A player's first right-click ever: the opening speech (and then, if they're holding something he
     *       wants, the next step in the same click).</li>
     *   <li>Holding something he wants: hand it in (full amount only).</li>
     *   <li>Earned the compass but has none: a free new one.</li>
     *   <li>Quest complete: the shop opens (first, if they have no Rift Key, he mentions that a lost key can be
     *       forged again). Otherwise: he repeats what's still needed.</li>
     * </ol>
     * The trading screen never opens before the quest is complete.
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
        ItemStack held = player.getMainHandItem();

        boolean firstTime = !QuestProgress.hasSpokenIntro(player);
        if (firstTime)
        {
            QuestProgress.markIntroSpoken(player);
            this.say(player, "intro");
        }

        // Holding something he wants: take it. This includes the very first click, straight after the
        // opening speech, so the player doesn't have to click a second time.
        if (this.wantsHandIn(player, held))
        {
            this.handIn(player, held);
            return;
        }
        if (firstTime)
        {
            this.playSound(SoundEvents.VILLAGER_AMBIENT, 1.0F, this.getVoicePitch());
            return;
        }

        if (QuestProgress.hasEarnedCompass(player) && !hasDrossCompass(player))
        {
            giveItem(player, DrossCompass.create(player.serverLevel()));
            this.say(player, "compass_replaced");
            this.playSound(SoundEvents.VILLAGER_YES, 1.0F, this.getVoicePitch());
            return;
        }

        if (QuestProgress.isQuestComplete(player))
        {
            if (!hasRiftKey(player) && !DrossAdvancements.has(player, DrossAdvancements.ENTERED_THE_DROSS))
            {
                // No key on them, and they aren't holding materials for one (he'd have taken those above):
                // tell them a lost key can be forged again, and what that costs. The shop opens as usual.
                // Not once they've been through: the key is used up lighting the portal, which then stays open.
                this.say(player, "lost_key", stillNeeded(player));
            }
            this.updateGuideBookOffer(player);
            this.setTradingPlayer(player);
            this.openTradingScreen(player, this.getDisplayName(), 1);
        }
        else
        {
            this.remind(player);
        }
    }

    /**
     * Should the item in the player's hand be treated as a hand-in?
     * Before the quest is complete: any quest item (so he can answer "not yet" or "already have it").
     * After it: only a key material he still needs, and only if the player has no Rift Key
     * (a replacement key). Otherwise a right-click opens the shop as normal.
     */
    private boolean wantsHandIn(ServerPlayer player, ItemStack held)
    {
        if (QuestProgress.isQuestComplete(player))
        {
            return !hasRiftKey(player) && QuestProgress.getWantedRequirement(player, held) != null;
        }
        return QuestRequirement.forItem(held) != null;
    }

    /** Takes the full amount or nothing (the quest API does the taking), then answers. */
    private void handIn(ServerPlayer player, ItemStack held)
    {
        QuestProgress.HandInResult result = QuestProgress.handIn(player, held);
        QuestRequirement requirement = result.requirement();
        switch (result.outcome())
        {
            case NOT_WANTED -> this.remind(player);
            case NOT_YET -> this.refuse(player, "not_yet");
            case ALREADY_HANDED_IN -> this.refuse(player, "already_have", requirement.getItem().getDescription());
            case NOT_ENOUGH -> this.refuse(player, "not_enough", requirement.describe());
            case ACCEPTED ->
            {
                this.say(player, "accepted", stillNeeded(player));
                this.playSound(SoundEvents.VILLAGER_YES, 1.0F, this.getVoicePitch());
            }
            case TRIBUTES_COMPLETE ->
            {
                giveItem(player, DrossCompass.create(player.serverLevel()));
                this.say(player, "tributes_complete", stillNeeded(player));
                this.playSound(SoundEvents.VILLAGER_CELEBRATE, 1.0F, this.getVoicePitch());
            }
            case KEY_FORGED ->
            {
                giveItem(player, new ItemStack(ModItems.RIFT_KEY.get()));
                this.say(player, QuestProgress.getKeysForged(player) > 1 ? "key_replaced" : "key_forged");
                this.playSound(SoundEvents.VILLAGER_CELEBRATE, 1.0F, this.getVoicePitch());
            }
        }
    }

    /** Repeats what's still needed (before the quest is complete). */
    private void remind(ServerPlayer player)
    {
        boolean tributes = QuestProgress.getStage(player) == QuestProgress.Stage.TRIBUTES;
        this.say(player, tributes ? "reminder_tributes" : "reminder_key_materials", stillNeeded(player));
        this.playSound(SoundEvents.VILLAGER_AMBIENT, 1.0F, this.getVoicePitch());
    }

    private void refuse(ServerPlayer player, String key, Object... args)
    {
        this.say(player, key, args);
        this.playSound(SoundEvents.VILLAGER_NO, 1.0F, this.getVoicePitch());
    }

    /** The tributes still needed, or (once those are in) the key materials still needed, as "Echo Shard x3, ...". */
    private static Component stillNeeded(ServerPlayer player)
    {
        List<QuestRequirement> list = QuestProgress.getStage(player) == QuestProgress.Stage.TRIBUTES
                ? QuestProgress.getStillNeeded(player)
                : QuestProgress.getMissingKeyMaterials(player);
        MutableComponent text = Component.empty();
        for (int i = 0; i < list.size(); i++)
        {
            if (i > 0)
            {
                text.append(", ");
            }
            text.append(list.get(i).describe());
        }
        return text;
    }

    /** Says one line of his dialogue in chat, like a player would: {@code <Dross Trader> line}. */
    private void say(ServerPlayer player, String key, Object... args)
    {
        Component line = Component.translatable("dross.trader.dialogue." + key, args);
        player.sendSystemMessage(Component.translatable("chat.type.text", this.getDisplayName(), line));
    }

    /** Puts the item in the player's inventory, or drops it at their feet if it's full. */
    private static void giveItem(ServerPlayer player, ItemStack stack)
    {
        if (!player.getInventory().add(stack) && !stack.isEmpty())
        {
            ItemEntity entity = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack);
            entity.setDeltaMovement(0, 0, 0);
            entity.setNoPickUpDelay();
            entity.setTarget(player.getUUID());
            player.level().addFreshEntity(entity);
        }
    }

    private static boolean hasDrossCompass(ServerPlayer player)
    {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
        {
            if (DrossCompass.isDrossCompass(player.getInventory().getItem(i)))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean hasRiftKey(ServerPlayer player)
    {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
        {
            if (player.getInventory().getItem(i).is(ModItems.RIFT_KEY.get()))
            {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- the shop

    @Override
    protected void updateTrades()
    {
        this.getOffers().addAll(buildOffers());
    }

    /**
     * His shop (same for everyone; the screen only opens for players who finished the quest).
     * No XP, no price changes, no restock, unlimited uses.
     */
    private static List<MerchantOffer> buildOffers()
    {
        Enchantment necromancy = ModEnchantments.NECROMANCY.get();
        List<MerchantOffer> list = new ArrayList<>();
        // Necromancy has five levels (I-V); he sells the lowest, level I (the enchantment's minimum level).
        // The book comes from NecromancyScheme so it carries the "new numbering" marker.
        list.add(bookOffer(NECROMANCY_EMERALDS, NecromancyScheme.createBook(necromancy.getMinLevel())));
        list.add(bookOffer(DEATHFORGED_EMERALDS, ModEnchantments.DEATHFORGED.get(), DEATHFORGED_LEVEL));
        return list;
    }

    /** Emeralds plus one plain book for an enchanted book of the given enchantment and level. */
    private static MerchantOffer bookOffer(int emeralds, Enchantment enchantment, int level)
    {
        return bookOffer(emeralds, EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment, level)));
    }

    /** Emeralds plus one plain book for the given result item. */
    private static MerchantOffer bookOffer(int emeralds, ItemStack result)
    {
        return new MerchantOffer(
                new ItemStack(Items.EMERALD, emeralds),
                new ItemStack(Items.BOOK),
                result,
                SHOP_MAX_USES,
                0,      // no villager XP
                0.0F);  // no price changes
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
