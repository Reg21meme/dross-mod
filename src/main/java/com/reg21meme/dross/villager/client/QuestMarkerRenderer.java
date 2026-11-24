package com.reg21meme.dross.villager.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.quest.oracle.OracleMarker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * CLIENT ONLY. Draws a quest marker over a character's head (QUEST_PLAN.md, "Markers over his head"; PRE_DROSS_PLAN.md,
 * "His quests"): what this player's quest wants of him, as the server last told their game
 * ({@code quest.client.OracleClientState}), so each player sees their own. It's drawn over the Blind Oracle
 * ({@link SurvivorRenderer}, {@code marker()}) and over the Dross Trader ({@link DrossTraderRenderer}, {@code traderMarker()}):
 * <ul>
 *   <li>{@link OracleMarker#NEW}: a red "?" (a new quest is waiting);</li>
 *   <li>{@link OracleMarker#ACTIVE}: a yellow "..." (the quest is going);</li>
 *   <li>{@link OracleMarker#DONE}: a green "!" (it's done: go back to him);</li>
 *   <li>{@link OracleMarker#NONE}: nothing.</li>
 * </ul>
 * It floats about a block over his head, always facing the camera, bobbing slowly, full-bright (so it glows a little at
 * night), seen from up to {@value #VIEW_DISTANCE} blocks, a little bigger the further off you are so it stays easy to
 * spot. It's drawn like a name tag, but never through walls.
 *
 * <p>The picture is pixel art ({@code textures/gui/quest_markers.png}, drawn by {@code tools/skins/quest_markers.py}),
 * one cell per marker in layers: its body (shades of grey tinted with the marker's colour, so its outline is a dark
 * shade of it) and its shine (white, untinted, so the highlights are lighter than the colour) drawn over it. The sheet
 * also has a soft glow round each outline (its first row), no longer drawn (the user, November 18, 2026: "remove the
 * blue glow").</p>
 */
public final class QuestMarkerRenderer
{
    private static final ResourceLocation SHEET = new ResourceLocation(Dross.MODID, "textures/gui/quest_markers.png");

    // ---- The sheet's layout: the same as tools/skins/quest_markers.py (CELL_W, CELL_H, SHEET_W, SHEET_H) ----
    /** One marker's cell, in texture pixels. Column 0 the "?", 1 the "...", 2 the "!". */
    private static final int CELL_WIDTH = 20;
    private static final int CELL_HEIGHT = 16;
    private static final int SHEET_WIDTH = 64;
    private static final int SHEET_HEIGHT = 48;
    /** The layers' rows in the sheet, drawn in this order. */
    @SuppressWarnings("unused")
    private static final int GLOW_ROW = 0;
    private static final int BODY_ROW = 1;
    private static final int SHINE_ROW = 2;

    // ---- Where and how big ----
    /** Blocks per texture pixel up close: a cell is 20 / 24 of a block across, the "?" itself half a block tall. */
    private static final float PIXEL = 1.0F / 24.0F;
    /** The gap between the top of his hitbox and the bottom of the marker (blocks): it floats about a block over his head. */
    private static final float LIFT = 0.8F;
    /** How far off it's seen (blocks from the camera). */
    static final double VIEW_DISTANCE = 48.0D;
    /** It starts growing this far off (blocks)... */
    private static final double GROW_FROM = 12.0D;
    /** ...and is this many times its size from here on, so it's still easy to spot from {@link #VIEW_DISTANCE}. */
    private static final double GROW_TO = 40.0D;
    private static final float FAR_SCALE = 1.75F;

    // ---- Bobbing ----
    /** How far it bobs up and down from its middle (blocks). */
    private static final float BOB_HEIGHT = 0.06F;
    /** One bob up and down takes this many ticks (2.5 seconds). */
    private static final float BOB_TICKS = 50.0F;

    /** How far each layer is drawn in front of the one before (blocks), so they never flicker against each other. */
    private static final float LAYER_GAP = 0.002F;

    private QuestMarkerRenderer() {}

    /**
     * Draws this marker over him (nothing for {@link OracleMarker#NONE}). Called after the character itself, with its
     * renderer's pose stack (at his feet).
     */
    static void render(LivingEntity oracle, OracleMarker marker, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       EntityRenderDispatcher dispatcher)
    {
        int column = columnOf(marker);
        if (column < 0)
        {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && oracle.isInvisibleTo(minecraft.player))
        {
            return;
        }
        double distanceSqr = dispatcher.distanceToSqr(oracle);
        if (distanceSqr > VIEW_DISTANCE * VIEW_DISTANCE)
        {
            return;
        }
        double distance = Math.sqrt(distanceSqr);
        float grow = 1.0F + (FAR_SCALE - 1.0F) * (float) Mth.clamp((distance - GROW_FROM) / (GROW_TO - GROW_FROM), 0.0D, 1.0D);
        float width = CELL_WIDTH * PIXEL * grow;
        float height = CELL_HEIGHT * PIXEL * grow;
        float time = oracle.tickCount + partialTick;
        float bob = Mth.sin(time * Mth.TWO_PI / BOB_TICKS) * BOB_HEIGHT;

        poseStack.pushPose();
        // Its bottom stays put as it grows, so it never sinks onto his name tag.
        poseStack.translate(0.0D, oracle.getBbHeight() + LIFT + BOB_HEIGHT + bob, 0.0D);
        // Face the camera: x right, y up, z towards the viewer.
        poseStack.mulPose(dispatcher.cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        Matrix4f matrix = poseStack.last().pose();
        // Drawn like a name tag (the text render type, depth-tested: hidden by walls), full-bright.
        VertexConsumer consumer = buffers.getBuffer(RenderType.text(SHEET));
        int light = LightTexture.FULL_BRIGHT;
        // (No glow layer round it since the user's "remove the blue glow", November 18, 2026: the sheet keeps it, unused.)
        quad(consumer, matrix, width, height, LAYER_GAP, column, BODY_ROW, colorOf(marker), light);
        quad(consumer, matrix, width, height, 2.0F * LAYER_GAP, column, SHINE_ROW, 0xFFFFFF, light);
        poseStack.popPose();
    }

    /** True if this marker is drawn at all. */
    static boolean isShown(OracleMarker marker)
    {
        return columnOf(marker) >= 0;
    }

    /** A box round where his marker can be, at its biggest and at the top of its bob (for the view check). */
    static AABB cullingBox(LivingEntity oracle)
    {
        double half = CELL_WIDTH * PIXEL * FAR_SCALE / 2.0D;
        double bottom = oracle.getY() + oracle.getBbHeight() + LIFT;
        double top = bottom + 2.0D * BOB_HEIGHT + CELL_HEIGHT * PIXEL * FAR_SCALE;
        return new AABB(oracle.getX() - half, bottom, oracle.getZ() - half, oracle.getX() + half, top, oracle.getZ() + half).inflate(0.5D);
    }

    /** The marker's column in the sheet, or -1 for none. */
    private static int columnOf(OracleMarker marker)
    {
        return switch (marker)
        {
            case NEW -> 0;
            case ACTIVE -> 1;
            case DONE -> 2;
            case NONE -> -1;
        };
    }

    private static int colorOf(OracleMarker marker)
    {
        return switch (marker)
        {
            case NEW -> DrossColors.QUEST_MARKER_NEW;
            case ACTIVE -> DrossColors.QUEST_MARKER_ACTIVE;
            case DONE -> DrossColors.QUEST_MARKER_DONE;
            case NONE -> 0xFFFFFF; // (never drawn)
        };
    }

    /** One layer: a quad from its bottom middle up, {@code z} towards the viewer, its cell tinted {@code rgb}. */
    private static void quad(VertexConsumer consumer, Matrix4f matrix, float width, float height, float z, int column, int row, int rgb, int light)
    {
        float u0 = (column * CELL_WIDTH) / (float) SHEET_WIDTH;
        float u1 = (column * CELL_WIDTH + CELL_WIDTH) / (float) SHEET_WIDTH;
        float v0 = (row * CELL_HEIGHT) / (float) SHEET_HEIGHT;
        float v1 = (row * CELL_HEIGHT + CELL_HEIGHT) / (float) SHEET_HEIGHT;
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        float half = width / 2.0F;
        // Counter-clockwise as the viewer sees it (the text render type culls back faces).
        consumer.vertex(matrix, -half, 0.0F, z).color(r, g, b, 255).uv(u0, v1).uv2(light).endVertex();
        consumer.vertex(matrix, half, 0.0F, z).color(r, g, b, 255).uv(u1, v1).uv2(light).endVertex();
        consumer.vertex(matrix, half, height, z).color(r, g, b, 255).uv(u1, v0).uv2(light).endVertex();
        consumer.vertex(matrix, -half, height, z).color(r, g, b, 255).uv(u0, v0).uv2(light).endVertex();
    }
}
