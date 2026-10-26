package com.reg21meme.dross.boss.huntsman;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.Locale;

/**
 * A quick look at one of his shots on foot, tick by tick (its own namespace, so it runs alone:
 * {@code -PgameTestNamespaces=dross_shotprobe}): what he plays, how far into it he is, and the arrows he looses.
 */
@GameTestHolder(HuntShotProbeGameTest.NAMESPACE)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class HuntShotProbeGameTest
{
    static final String NAMESPACE = "dross_shotprobe";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String EMPTY = "empty";
    private static final ResourceLocation EMPTY_ID = new ResourceLocation(NAMESPACE, EMPTY);
    private static final int SKY_Y = 250;
    private static final int HALF = 20;

    private HuntShotProbeGameTest() {}

    @GameTest(template = EMPTY, batch = "dross_shotprobe", timeoutTicks = 400)
    public static void quickShotOnFoot(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos base = new BlockPos(origin.getX(), SKY_Y, origin.getZ());
        for (int x = -HALF; x <= HALF; x++)
        {
            for (int z = -HALF; z <= HALF; z++)
            {
                level.setBlock(base.offset(x, -1, z), Blocks.BARRIER.defaultBlockState(), 2);
            }
        }
        Huntsman h = ModEntities.HUNTSMAN.get().create(level);
        Villager v = EntityType.VILLAGER.create(level);
        if (h == null || v == null)
        {
            throw new GameTestAssertException("couldn't make them");
        }
        h.setNoAi(true);
        h.moveTo(base.getX() + 0.5D, base.getY(), base.getZ() + 0.5D, 270.0F, 0.0F);
        level.addFreshEntity(h);
        v.setNoAi(true);
        v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0F);
        v.setHealth(1000.0F);
        v.moveTo(base.getX() + 8.5D, base.getY(), base.getZ() + 0.5D, 0.0F, 0.0F);
        level.addFreshEntity(v);
        helper.runAfterDelay(3, () ->
        {
            if (h.getVehicle() != null)
            {
                net.minecraft.world.entity.Entity moose = h.getVehicle();
                h.stopRiding();
                moose.discard();                       // (on foot, alone)
            }
            h.moveTo(base.getX() + 0.5D, base.getY(), base.getZ() + 0.5D, 270.0F, 0.0F);
            h.setYBodyRot(270.0F);
        });
        helper.runAfterDelay(5, () ->
        {
            h.shotTarget = v;
            h.shotBase = 1.0D;
            h.shotFrost = HuntFight.Frost.NONE;
            boolean played = h.play("quick_shot");
            LOGGER.info("[shot probe] played {}: showcase {}, alive {}, mounted {}, anims {}", played, h.isShowcase(),
                    h.isAlive(), h.isMounted(), h.animations().size());
        });
        for (int t = 6; t <= 60; t++)
        {
            int when = t;
            helper.runAfterDelay(t, () ->
            {
                StringBuilder arrows = new StringBuilder();
                for (Arrow a : level.getEntitiesOfClass(Arrow.class, new AABB(base).inflate(40.0D)))
                {
                    arrows.append(String.format(Locale.ROOT, " [arrow at %.1f %.1f %.1f owner %s dmg %.1f]", a.getX(),
                            a.getY(), a.getZ(), a.getOwner() == h ? "him" : String.valueOf(a.getOwner()),
                            a.getBaseDamage()));
                }
                LOGGER.info(String.format(Locale.ROOT, "[shot probe] %d: %s %d of %d, alive %s, target %s, health %.0f%s",
                        when, h.current().name(), h.playingTicks(), h.current().ticks(), h.isAlive(), h.shotTarget,
                        v.getHealth(), arrows));
            });
        }
        helper.runAfterDelay(70, () ->
        {
            h.discard();
            v.discard();
            helper.succeed();
        });
    }

    /** The post-split fight against a raged Colossus and its golems (HuntProbeGameTest's), alone. */
    @GameTest(template = EMPTY, batch = "dross_shotprobe_duel", timeoutTicks = 1200)
    public static void duelVersusColossus(GameTestHelper helper)
    {
        HuntProbeGameTest.ragedVersus(helper, ModEntities.CINDER_COLOSSUS.get(), 215.0F, true);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        if (!ForgeGameTestHooks.isGametestEnabled())
        {
            return;
        }
        StructureTemplate template = event.getServer().getStructureManager().getOrCreate(EMPTY_ID);
        CompoundTag tag = new CompoundTag();
        tag.put("size", ints(1, 1, 1));
        ListTag palette = new ListTag();
        palette.add(NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));
        tag.put("palette", palette);
        ListTag blocks = new ListTag();
        CompoundTag block = new CompoundTag();
        block.put("pos", ints(0, 0, 0));
        block.putInt("state", 0);
        blocks.add(block);
        tag.put("blocks", blocks);
        tag.put("entities", new ListTag());
        template.load(BuiltInRegistries.BLOCK.asLookup(), tag);
    }

    private static ListTag ints(int... values)
    {
        ListTag list = new ListTag();
        for (int v : values)
        {
            list.add(IntTag.valueOf(v));
        }
        return list;
    }
}
