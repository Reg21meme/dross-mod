package com.reg21meme.dross.boss.emberdactyl;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * The Emberdactyl's test commands (World site wires them into {@code /dross boss}): {@code summon emberdactyl},
 * {@code summon emberyoung} and {@code arena emberdactyl}, its volcano built in front of you with its egg and the
 * Emberdactyl perched on its rim.
 */
public final class EmberCommands
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The command the arena's button runs. */
    public static final String SUMMON_COMMAND = "/dross boss summon emberdactyl";
    /** The adult comes this far in front of you, the young this far. */
    private static final int ADULT_AHEAD = 30;
    private static final int YOUNG_AHEAD = 18;
    /** The arena's volcano stands this far in front of you (its middle: you stand just past its ground's edge). */
    private static final int ARENA_AHEAD = EmberVolcano.REACH + 20;

    private EmberCommands() {}

    /** {@code /dross boss summon emberdactyl}: it takes to the air in front of you (its home there), alone. */
    public static int summonAdult(CommandSourceStack source)
    {
        return summon(source, ModEntities.EMBERDACTYL.get(), ADULT_AHEAD, "commands.dross.boss.summon_emberdactyl.success");
    }

    /** {@code /dross boss summon emberyoung}: its young takes to the air in front of you. */
    public static int summonYoung(CommandSourceStack source)
    {
        return summon(source, ModEntities.EMBERYOUNG.get(), YOUNG_AHEAD, "commands.dross.boss.summon_emberyoung.success");
    }

    private static int summon(CommandSourceStack source, EntityType<? extends EmberBoss> type, int ahead, String success)
    {
        ServerLevel level = source.getLevel();
        if (level.getDifficulty() == Difficulty.PEACEFUL)
        {
            source.sendFailure(Component.translatable("commands.dross.boss.summon_ember.peaceful"));
            return 0;
        }
        Vec3 ground = ahead(source, ahead);
        EmberBoss boss = type.create(level);
        if (boss == null)
        {
            source.sendFailure(Component.translatable("commands.dross.boss.summon_ember.failed"));
            return 0;
        }
        float yaw = source.getRotation().y + 180.0F;
        boss.moveTo(ground.x, ground.y + EmberTuning.CALM_HOVER * 0.5D, ground.z, yaw, 0.0F);
        boss.setYHeadRot(yaw);
        boss.setYBodyRot(yaw);
        boss.setHome(ground, null, null, 0.0F, 0L);
        boss.finalizeSpawn(level, level.getCurrentDifficultyAt(boss.blockPosition()), MobSpawnType.COMMAND, null, null);
        boss.makeHostile();   // (like a spawn egg's: it fights from the moment it comes)
        level.addFreshEntity(boss);
        LOGGER.info("Summoned the {} for {} at {}", type.getDescription().getString(), source.getTextName(),
                BlockPos.containing(ground).toShortString());
        source.sendSuccess(() -> Component.translatable(success, ahead), true);
        return 1;
    }

    /** The ground {@code distance} blocks in front of the command's user. */
    private static Vec3 ahead(CommandSourceStack source, int distance)
    {
        float yaw = source.getRotation().y;
        Vec3 look = new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0.0D, Mth.cos(yaw * Mth.DEG_TO_RAD));
        Vec3 at = source.getPosition().add(look.scale(distance));
        ServerLevel level = source.getLevel();
        level.getChunkAt(BlockPos.containing(at));
        double y = EmberBoss.groundUnder(level, at.x, source.getPosition().y, at.z);   // (the floor, under a roof too)
        return new Vec3(Mth.floor(at.x) + 0.5D, y, Mth.floor(at.z) + 0.5D);
    }

    /**
     * {@code /dross boss arena emberdactyl}: builds its volcano on the land in front of you (all at once: its land is
     * generated first, a few seconds), lays its egg in the nest and perches the Emberdactyl on its rim, its home there.
     */
    public static int buildArena(CommandSourceStack source)
    {
        ServerLevel level = source.getLevel();
        Vec3 centre = ahead(source, ARENA_AHEAD);
        EmberVolcano.Plan plan = EmberVolcano.planOnLand(Mth.floor(centre.x), Mth.floor(centre.z), VolcanoPlacer.generatorLand(level),
                level.random.nextLong());
        BoundingBox box = plan.box();
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++)
        {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++)
            {
                level.getChunk(cx, cz);
            }
        }
        long started = System.nanoTime();
        plan.buildNow(level);
        LOGGER.info("Built the Emberdactyl's arena volcano for {} at {} in {} ms", source.getTextName(), plan.ground().toShortString(),
                (System.nanoTime() - started) / 1_000_000L);
        Vec3 nest = plan.eggFeet();
        EmberKeeper.Perch perch = perch(level, plan);
        EmberEgg.lay(level, nest, perch.yaw(), 0L);
        if (level.getDifficulty() != Difficulty.PEACEFUL)
        {
            Emberdactyl adult = ModEntities.EMBERDACTYL.get().create(level);
            if (adult != null)
            {
                adult.moveTo(perch.spot().x, perch.spot().y, perch.spot().z, perch.yaw(), 0.0F);
                adult.setYHeadRot(perch.yaw());
                adult.setYBodyRot(perch.yaw());
                adult.setHome(nest, nest, perch.spot(), perch.yaw(), 0L);
                adult.finalizeSpawn(level, level.getCurrentDifficultyAt(adult.blockPosition()), MobSpawnType.COMMAND, null, null);
                level.addFreshEntity(adult);
            }
        }
        MutableComponent message = Component.translatable("commands.dross.boss.arena_emberdactyl.success", ARENA_AHEAD);
        message.append(" ").append(Component.translatable("commands.dross.boss.arena_emberdactyl.summon_button").withStyle(style -> style
                .withColor(DrossColors.EMBERDACTYL_COMMAND_BUTTON)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, SUMMON_COMMAND))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("commands.dross.boss.arena.summon_hover", SUMMON_COMMAND)))));
        source.sendSuccess(() -> message, true);
        return 1;
    }

    /** Its perch on a volcano planned here (on its ledge on the rim opposite the lookout, facing outwards). */
    static EmberKeeper.Perch perch(ServerLevel level, EmberVolcano.Plan plan)
    {
        return new EmberKeeper.Perch(plan.perch(), plan.perchYaw());
    }
}
