package com.reg21meme.dross.command;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.huntsman.AlphaMoose;
import com.reg21meme.dross.boss.huntsman.HuntAnims;
import com.reg21meme.dross.boss.huntsman.HuntMob;
import com.reg21meme.dross.boss.huntsman.Huntsman;
import com.reg21meme.dross.world.PortalSiteGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Headless checks of the Huntsman's showcase ({@code /dross showcase boss_designs huntsman}): every animation the
 * mod asks for is in its model's file (made by tools/rimefall/mobs.py), the model and textures are in the mod; the
 * showcase puts the Huntsman in the Alpha Moose's saddle (wearing his riding animations), on foot, and the moose alone;
 * each plays the animations it has (and refuses the others), a move goes back to standing once it's done, and they
 * can't be hurt.
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class HuntsmanShowcaseGameTest
{
    private static final BlockPos AT = new BlockPos(-43000, 200, 39000);
    /** Ticks given to the loaded land, then to the spawned exhibits, before they're looked at. */
    private static final int SETTLE = 10;

    private HuntsmanShowcaseGameTest() {}

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_huntsman_showcase", timeoutTicks = 400)
    public static void huntsmanOnShow(GameTestHelper helper)
    {
        checkFiles(HuntAnims.HUNTSMAN);
        checkFiles(HuntAnims.RIDING);
        checkFiles(HuntAnims.ALPHA_MOOSE);
        ServerLevel level = helper.getLevel().getServer().overworld();
        // The land round the spot loaded and kept loaded while the test runs (so the exhibits are in the world and
        // tick: a move only goes back to standing in their tick), then a moment for it to settle.
        List<ChunkPos> forced = new ArrayList<>();
        for (int dx = -32; dx <= 32; dx += 16)
        {
            for (int dz = -32; dz <= 32; dz += 16)
            {
                ChunkPos c = new ChunkPos(AT.offset(dx, 0, dz));
                level.setChunkForced(c.x, c.z, true);
                level.getChunk(c.x, c.z);
                forced.add(c);
            }
        }
        Runnable unforce = () -> forced.forEach(c -> level.setChunkForced(c.x, c.z, false));
        helper.runAfterDelay(SETTLE, () ->
        {
            List<HuntMob> row = HuntsmanShowcase.spawnRow(level, AT, Direction.NORTH);
            helper.runAfterDelay(SETTLE, () ->
            {
                try
                {
                    checkRow(helper, level, row, unforce);
                }
                catch (RuntimeException e)
                {
                    row.forEach(HuntMob::discard);
                    unforce.run();
                    throw e;
                }
            });
        });
    }

    private static void checkRow(GameTestHelper helper, ServerLevel level, List<HuntMob> row, Runnable unforce)
    {
        long riders = row.stream().filter(m -> m instanceof Huntsman h && h.isMounted()).count();
        long walkers = row.stream().filter(m -> m instanceof Huntsman h && !h.isMounted()).count();
        long moose = row.stream().filter(m -> m instanceof AlphaMoose).count();
        if (riders != 1 || walkers != 1 || moose != 2)
        {
            throw new GameTestAssertException("the showcase should have 1 rider, 1 Huntsman on foot and 2 moose, but has "
                    + riders + ", " + walkers + " and " + moose);
        }
        Huntsman rider = (Huntsman) row.stream().filter(m -> m instanceof Huntsman h && h.isMounted()).findFirst().orElseThrow();
        if (rider.animations() != HuntAnims.RIDING || rider.getVehicle() == null
                || rider.position().distanceTo(rider.getVehicle().position()) > 0.01D)
        {
            throw new GameTestAssertException("the riding Huntsman should sit at the moose's own position with his riding animations");
        }
        // (played on the row itself: the test world can take a while to list newly spawned mobs in fresh land)
        Vec3 near = Vec3.atCenterOf(AT);
        int played = HuntsmanShowcase.play(row, "spear_combo");
        if (played != 1)
        {
            throw new GameTestAssertException("only the Huntsman on foot has spear_combo, but " + played + " played it; near: "
                    + describe(level, near));
        }
        if (HuntsmanShowcase.play(row, "ride_gallop") != 1)
        {
            throw new GameTestAssertException("only the riding Huntsman has ride_gallop; near: " + describe(level, near));
        }
        AlphaMoose carrying = (AlphaMoose) rider.getVehicle();
        if (!carrying.current().name().equals("run"))
        {
            throw new GameTestAssertException("the moose carrying him should gallop with him, but plays " + carrying.current().name());
        }
        int reared = HuntsmanShowcase.play(row, "rear");
        if (reared != 2)
        {
            StringBuilder spawned = new StringBuilder();
            for (HuntMob m : row)
            {
                spawned.append(m.getClass().getSimpleName()).append(" at ").append(m.blockPosition().toShortString())
                        .append(m.isRemoved() ? " removed: " + m.getRemovalReason() : " in the world").append(" health ")
                        .append(m.getHealth()).append("; ");
            }
            throw new GameTestAssertException("both moose should rear, but " + reared + " did; near: " + describe(level, near)
                    + " spawned: " + spawned);
        }
        for (HuntMob mob : row)
        {
            if (mob.hurt(level.damageSources().generic(), 50.0F))
            {
                throw new GameTestAssertException(mob.getName().getString() + " on show shouldn't be hurt");
            }
        }
        HuntMob walker = row.stream().filter(m -> m instanceof Huntsman h && !h.isMounted()).findFirst().orElseThrow();
        int back = HuntAnims.named(HuntAnims.HUNTSMAN, "spear_combo").ticks() + HuntMob.HOLD_AFTER + 5;
        helper.runAfterDelay(back, () ->
        {
            try
            {
                if (!walker.current().name().equals("idle"))
                {
                    throw new GameTestAssertException("after his spear combo he should stand (idle), but plays " + walker.current().name());
                }
            }
            finally
            {
                row.forEach(HuntMob::discard);
                unforce.run();
            }
            helper.succeed();
        });
    }

    private static String describe(ServerLevel level, Vec3 near)
    {
        StringBuilder found = new StringBuilder();
        for (HuntMob m : HuntsmanShowcase.nearby(level, near))
        {
            found.append(m.getClass().getSimpleName()).append(" at ").append(m.blockPosition().toShortString())
                    .append(" playing ").append(m.current().name()).append("; ");
        }
        return found.length() == 0 ? "nothing" : found.toString();
    }

    private static void checkFiles(List<HuntAnims.Anim> anims)
    {
        String model = anims.get(0).model();
        String base = "/assets/" + Dross.MODID + "/";
        for (String path : List.of("geo/entity/" + model + "/" + model + ".geo.json", "textures/entity/" + model + "/" + model + ".png",
                "textures/entity/" + model + "/" + model + "_glow.png", "textures/entity/" + model + "/" + model + "_ice.png"))
        {
            try (InputStream in = HuntsmanShowcaseGameTest.class.getResourceAsStream(base + path))
            {
                if (in == null)
                {
                    throw new GameTestAssertException(path + " isn't in the mod");
                }
            }
            catch (java.io.IOException e)
            {
                throw new GameTestAssertException("couldn't read " + path + ": " + e);
            }
        }
        String file = base + "animations/entity/" + model + "/" + model + ".animation.json";
        try (InputStream in = HuntsmanShowcaseGameTest.class.getResourceAsStream(file))
        {
            if (in == null)
            {
                throw new GameTestAssertException(file + " isn't in the mod");
            }
            JsonObject all = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonObject("animations");
            for (HuntAnims.Anim a : anims)
            {
                String id = "animation." + model + "." + a.name();
                if (!all.has(id))
                {
                    throw new GameTestAssertException(file + " has no " + id);
                }
                double length = all.getAsJsonObject(id).has("animation_length")
                        ? all.getAsJsonObject(id).get("animation_length").getAsDouble() : 0.0D;
                if (!a.loop() && Math.abs(length * 20.0D - a.ticks()) > 1.5D)
                {
                    throw new GameTestAssertException(id + " lasts " + length + " s in its file, but the mod thinks "
                            + a.ticks() + " ticks");
                }
            }
        }
        catch (java.io.IOException e)
        {
            throw new GameTestAssertException("couldn't read " + file + ": " + e);
        }
    }
}
