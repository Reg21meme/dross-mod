package com.reg21meme.dross.boss.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.ratking.RatKingTuning;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Draws the Dross bosses' boss bars ourselves (client only): Minecraft's own bars come in seven colours and none of
 * them is lava orange. The server sends a normal boss bar (a {@code ServerBossEvent}); its name is a translation key,
 * which is how a bar is recognised here (by the whole key, so one boss's bars never take another's look). Minecraft's
 * drawing of it is cancelled and this draws the name and the bar in its place:
 * <ul>
 *   <li>the Cinder Colossus ({@code boss.dross.cinder_colossus.}...): {@code shell}, obsidian purple with glassy
 *       glints; {@code cracking}, the same obsidian split by glowing orange cracks; {@code core}, lava red with brighter
 *       streaks flowing along it;</li>
 *   <li>the Rat King ({@code boss.dross.rat_king.bar}, {@code .rage} in his Rage): the user's pick, a rusty sewer pipe
 *       with a rat peeking over it, drawn from its texture ({@link RatKingBarArt}: the empty bar, the full bar cut off at
 *       his health, the decorations, the name).</li>
 * </ul>
 * Any other bar is left to Minecraft. Every colour comes from {@link DrossColors} (the "Boss" section).
 */
@Mod.EventBusSubscriber(modid = Dross.MODID, value = Dist.CLIENT)
public final class DrossBossBars
{
    /** The Cinder Colossus's bar names, one per stage. */
    private static final String COLOSSUS_SHELL = "boss.dross.cinder_colossus.shell";
    private static final String COLOSSUS_CRACKING = "boss.dross.cinder_colossus.cracking";
    private static final String COLOSSUS_CORE = "boss.dross.cinder_colossus.core";
    /** Minecraft's boss bar size: 182 pixels wide, 5 tall, the name 9 pixels above it. */
    private static final int WIDTH = 182;
    private static final int HEIGHT = 5;
    private static final int NAME_ABOVE = 9;
    /** The obsidian's glints and the cracks repeat every this many pixels along the bar. */
    private static final int GLINT_EVERY = 13;
    private static final int CRACK_EVERY = 19;
    /** The lava's bright streaks: how far apart, how long, how fast they flow (pixels a tick). */
    private static final int STREAK_EVERY = 23;
    private static final int STREAK_LENGTH = 5;
    private static final float STREAK_SPEED = 0.6F;

    private DrossBossBars() {}

    @SubscribeEvent
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event)
    {
        LerpingBossEvent bar = event.getBossEvent();
        if (!(bar.getName().getContents() instanceof TranslatableContents contents))
        {
            return;
        }
        String key = contents.getKey();
        String colossus = switch (key)
        {
            case COLOSSUS_SHELL -> "shell";
            case COLOSSUS_CRACKING -> "cracking";
            case COLOSSUS_CORE -> "core";
            default -> null;
        };
        if (colossus != null)
        {
            event.setCanceled(true);
            draw(event.getGuiGraphics(), event.getX(), event.getY(), bar.getProgress(), bar.getName(), colossus,
                    event.getPartialTick());
            return;
        }
        if (key.equals(RatKingTuning.BAR_KEY) || key.equals(RatKingTuning.BAR_KEY_RAGE))
        {
            event.setCanceled(true);
            boolean rage = key.equals(RatKingTuning.BAR_KEY_RAGE);
            drawRatKing(event.getGuiGraphics(), event.getX(), event.getY(), bar.getProgress(), bar.getName(), rage);
            event.setIncrement(RatKingBarArt.HEIGHT);
        }
    }

    /**
     * The Rat King's bar ({@link RatKingBarArt}): the empty bar, the full bar cut off at the health, the decorations and
     * the name. Its name stands higher than vanilla's, so it's moved down to sit where vanilla's would (it never runs off
     * the top of the screen); it takes {@link RatKingBarArt#HEIGHT} pixels.
     */
    private static void drawRatKing(GuiGraphics g, int x, int barY, float progress, Component name, boolean rage)
    {
        RatKingBarArt.Look look = rage ? RatKingBarArt.RAGE : RatKingBarArt.NORMAL;
        int y = barY + Math.max(0, -RatKingBarArt.NAME_Y - NAME_ABOVE);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        blitPart(g, look.empty(), x, y);
        int filled = Math.round(Math.max(0.0F, Math.min(1.0F, progress)) * look.fillWidth());
        if (filled > 0)
        {
            RatKingBarArt.Part full = look.full();
            g.blit(RatKingBarArt.TEXTURE, x + full.x() + look.fillLeft(), y + full.y(), full.u() + look.fillLeft(), full.v(), filled,
                    full.height(), RatKingBarArt.TEXTURE_WIDTH, RatKingBarArt.TEXTURE_HEIGHT);
        }
        blitPart(g, look.decorations(), x, y);
        RenderSystem.disableBlend();
        Font font = Minecraft.getInstance().font;
        int nameColor = rage ? DrossColors.RAT_KING_BAR_NAME_RAGE : DrossColors.RAT_KING_BAR_NAME;
        g.drawString(font, name, x + WIDTH / 2 - font.width(name) / 2, y + RatKingBarArt.NAME_Y, nameColor, true);
    }

    private static void blitPart(GuiGraphics g, RatKingBarArt.Part part, int x, int y)
    {
        g.blit(RatKingBarArt.TEXTURE, x + part.x(), y + part.y(), part.u(), part.v(), part.width(), part.height(),
                RatKingBarArt.TEXTURE_WIDTH, RatKingBarArt.TEXTURE_HEIGHT);
    }

    private static void draw(GuiGraphics g, int x, int y, float progress, Component name, String style, float partialTick)
    {
        Minecraft mc = Minecraft.getInstance();
        float time = (mc.level != null ? mc.level.getGameTime() : 0L) + partialTick;
        int filled = Math.round(Math.max(0.0F, Math.min(1.0F, progress)) * WIDTH);

        // The frame and the empty part.
        g.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, opaque(DrossColors.BOSS_BAR_OUTLINE));
        g.fill(x, y, x + WIDTH, y + HEIGHT, opaque(DrossColors.BOSS_BAR_EMPTY));

        int nameColor;
        switch (style)
        {
            case "core" -> {
                drawBands(g, x, y, filled, DrossColors.BOSS_BAR_LAVA_LIGHT, DrossColors.BOSS_BAR_LAVA, DrossColors.BOSS_BAR_LAVA_DARK);
                drawStreaks(g, x, y, filled, time);
                nameColor = DrossColors.BOSS_BAR_NAME_CORE;
            }
            case "cracking" -> {
                drawObsidian(g, x, y, filled);
                drawCracks(g, x, y, filled, time);
                nameColor = DrossColors.BOSS_BAR_NAME_CRACKING;
            }
            default -> {
                drawObsidian(g, x, y, filled);
                nameColor = DrossColors.BOSS_BAR_NAME_SHELL;
            }
        }

        Font font = mc.font;
        int textWidth = font.width(name);
        g.drawString(font, name, x + WIDTH / 2 - textWidth / 2, y - NAME_ABOVE, nameColor, true);
    }

    /** The filled part in three bands: a light top row, the middle, a dark bottom row. */
    private static void drawBands(GuiGraphics g, int x, int y, int filled, int light, int middle, int dark)
    {
        if (filled <= 0)
        {
            return;
        }
        g.fill(x, y, x + filled, y + 1, opaque(light));
        g.fill(x, y + 1, x + filled, y + HEIGHT - 1, opaque(middle));
        g.fill(x, y + HEIGHT - 1, x + filled, y + HEIGHT, opaque(dark));
    }

    /** Obsidian: purple bands with glassy glints dotted along it. */
    private static void drawObsidian(GuiGraphics g, int x, int y, int filled)
    {
        drawBands(g, x, y, filled, DrossColors.BOSS_BAR_OBSIDIAN_LIGHT, DrossColors.BOSS_BAR_OBSIDIAN,
                DrossColors.BOSS_BAR_OBSIDIAN_DARK);
        for (int gx = 5; gx < filled; gx += GLINT_EVERY)
        {
            int row = 1 + (gx / GLINT_EVERY) % 2;
            g.fill(x + gx, y + row, x + gx + 1, y + row + 1, opaque(DrossColors.BOSS_BAR_OBSIDIAN_GLINT));
        }
    }

    /** Glowing orange cracks zigzagging across the obsidian, their hot middles flickering. */
    private static void drawCracks(GuiGraphics g, int x, int y, int filled, float time)
    {
        int[] zigzag = {0, 1, 0, -1, 0};
        for (int i = 0; ; i++)
        {
            int cx = 8 + i * CRACK_EVERY + (i * 7) % 5;
            if (cx >= filled - 1)
            {
                break;
            }
            boolean hot = ((int) (time / 3.0F) + i) % 4 != 0;
            for (int row = 0; row < HEIGHT; row++)
            {
                int px = x + cx + zigzag[row];
                // The dim glow either side, then the crack itself (white-hot in the middle rows).
                g.fill(px - 1, y + row, px, y + row + 1, opaque(DrossColors.BOSS_BAR_CRACK_GLOW));
                g.fill(px + 1, y + row, px + 2, y + row + 1, opaque(DrossColors.BOSS_BAR_CRACK_GLOW));
                boolean middle = row >= 1 && row <= 3;
                g.fill(px, y + row, px + 1, y + row + 1, opaque(middle && hot ? DrossColors.BOSS_BAR_CRACK_HOT
                        : DrossColors.BOSS_BAR_CRACK));
            }
        }
    }

    /** Bright streaks flowing along the lava. */
    private static void drawStreaks(GuiGraphics g, int x, int y, int filled, float time)
    {
        int shift = (int) (time * STREAK_SPEED) % STREAK_EVERY;
        for (int sx = -STREAK_EVERY + shift; sx < filled; sx += STREAK_EVERY)
        {
            int row = 1 + Math.floorMod(sx / STREAK_EVERY, 2);
            int from = Math.max(0, sx);
            int to = Math.min(filled, sx + STREAK_LENGTH);
            if (to > from)
            {
                g.fill(x + from, y + row, x + to, y + row + 1, opaque(DrossColors.BOSS_BAR_LAVA_HOT));
            }
        }
    }

    private static int opaque(int rgb)
    {
        return 0xFF000000 | rgb;
    }
}
