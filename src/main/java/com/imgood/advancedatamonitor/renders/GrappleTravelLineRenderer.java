package com.imgood.advancedatamonitor.renders;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.imgood.advancedatamonitor.client.GrappleClientCache;
import com.imgood.advancedatamonitor.client.GrappleSelectionUtil;
import com.imgood.advancedatamonitor.items.ItemGrappleHook;
import com.imgood.advancedatamonitor.utils.BlockPos;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Preview lines while grapple-traveling: rhythmic blue/red selection lines, animated dashed white queue path.
 */
@SideOnly(Side.CLIENT)
public class GrappleTravelLineRenderer {

    private static final float BLUE_LINE_WIDTH = 3.5F;
    private static final float GLOW_WIDTH_FACTOR = 2.1F;

    private static final float LINE_ALPHA = 0.80F;
    private static final float GLOW_ALPHA = 0.22F;

    private static final float BLUE_R = 0.30F;
    private static final float BLUE_G = 0.70F;
    private static final float BLUE_B = 1.00F;

    private static final float RED_R = 1.00F;
    private static final float RED_G = 0.28F;
    private static final float RED_B = 0.28F;
    private static final float RED_LINE_WIDTH = 3.5F;
    private static final float RED_HIGHLIGHT_R = 1.00F;
    private static final float RED_HIGHLIGHT_G = 0.55F;
    private static final float RED_HIGHLIGHT_B = 0.55F;

    /** Queue path: animated dashed silver-white route (distinct from solid blue/red selection lines). */
    private static final float QUEUE_LINE_WIDTH = 2.4F;
    private static final float QUEUE_DASH_LENGTH = 0.55F;
    private static final float QUEUE_GAP_LENGTH = 0.38F;
    /** Dash march speed along the path (blocks / second). */
    private static final float QUEUE_MARCH_SPEED = 3.2F;
    private static final float QUEUE_DASH_ALPHA = 0.68F;
    private static final float QUEUE_SPARK_ALPHA = 0.85F;
    private static final float QUEUE_SILVER_R = 0.82F;
    private static final float QUEUE_SILVER_G = 0.88F;
    private static final float QUEUE_SILVER_B = 1.00F;

    /** ~96 BPM beat grid for blue selection line rhythm. */
    private static final float BEAT_HZ = 1.6F;

    private static final float FLOW_SEGMENT = 0.18F;
    private static final float HIGHLIGHT_ALPHA = 0.50F;

    /** Subdivisions for endpoint fade along blue/red preview lines. */
    private static final int GRADIENT_SEGMENTS = 24;
    /** Line ends: higher transparency (lower alpha). */
    private static final float END_FADE_FACTOR = 0.22F;
    /** Line center: lower transparency (higher alpha). */
    private static final float MID_FADE_FACTOR = 1.00F;

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        if (player == null || mc.theWorld == null || mc.currentScreen != null) {
            return;
        }
        if (!GrappleClientCache.isAttached()) {
            return;
        }

        World world = mc.theWorld;
        float partialTicks = event.partialTicks;
        RenderManager rm = RenderManager.instance;
        double[] offset = resolveLineRenderOffset(player, rm, partialTicks);
        double ox = offset[0];
        double oy = offset[1];
        double oz = offset[2];

        float animSeconds = (world.getTotalWorldTime() + partialTicks) / 20.0F;

        boolean drawBlue = false;
        double blueX1 = 0.0D;
        double blueY1 = 0.0D;
        double blueZ1 = 0.0D;
        double blueX2 = 0.0D;
        double blueY2 = 0.0D;
        double blueZ2 = 0.0D;

        if (ItemGrappleHook.isHoldingHook(player)) {
            BlockPos target = GrappleClientCache.getSelectedTarget();
            if (target != null && isValidPreviewTarget(target)) {
                double[] start = GrappleSelectionUtil.getLinePreviewStartPosition(player, partialTicks);
                double[] end = GrappleSelectionUtil.getNodeLinePosition(world, target);
                if (start != null && end != null) {
                    drawBlue = true;
                    blueX1 = start[0];
                    blueY1 = start[1];
                    blueZ1 = start[2];
                    blueX2 = end[0];
                    blueY2 = end[1];
                    blueZ2 = end[2];
                }
            }
        }

        boolean selectionReachable = drawBlue
            && GrappleSelectionUtil.isTravelReachable(player, GrappleClientCache.getSelectedTarget(), partialTicks);

        List<BlockPos> queuePath = buildQueuePathNodes();
        boolean drawQueue = queuePath.size() >= 2;

        if (!drawBlue && !drawQueue) {
            return;
        }

        beginLineRender();
        try {
            if (drawBlue) {
                if (selectionReachable) {
                    drawRhythmicBlueLine(blueX1, blueY1, blueZ1, blueX2, blueY2, blueZ2, ox, oy, oz, animSeconds);
                } else {
                    drawRhythmicRedLine(blueX1, blueY1, blueZ1, blueX2, blueY2, blueZ2, ox, oy, oz, animSeconds);
                }
            }
            if (drawQueue) {
                drawWhiteQueuePath(world, queuePath, ox, oy, oz, animSeconds);
            }
        } finally {
            endLineRender();
        }
    }

    private static void drawWhiteQueuePath(World world, List<BlockPos> pathNodes, double ox, double oy, double oz,
        float animSeconds) {
        for (int i = 0; i < pathNodes.size() - 1; i++) {
            BlockPos previous = pathNodes.get(i);
            BlockPos next = pathNodes.get(i + 1);
            double[] start = GrappleSelectionUtil.getNodeLinePosition(world, previous);
            double[] end = GrappleSelectionUtil.getNodeLinePosition(world, next);
            if (start == null || end == null) {
                continue;
            }
            drawAnimatedDashedQueueSegment(
                start[0],
                start[1],
                start[2],
                end[0],
                end[1],
                end[2],
                ox,
                oy,
                oz,
                animSeconds,
                i);
        }
    }

    /**
     * Marching dashed queue route: silver dashes crawl forward with comet-tail fade and spark tips.
     * No beat-sync — visually distinct from blue/red solid rhythmic lines.
     */
    private static void drawAnimatedDashedQueueSegment(double x1, double y1, double z1, double x2, double y2, double z2,
        double ox, double oy, double oz, float animSeconds, int segmentIndex) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.05D) {
            return;
        }

        double ux = dx / len;
        double uy = dy / len;
        double uz = dz / len;
        float cycleLen = QUEUE_DASH_LENGTH + QUEUE_GAP_LENGTH;
        float phase = (animSeconds * QUEUE_MARCH_SPEED + segmentIndex * cycleLen * 0.42F) % cycleLen;
        float breathe = 0.88F + 0.12F * (float) Math.sin(animSeconds * 2.4F + segmentIndex * 0.9F);

        Tessellator tess = Tessellator.instance;

        GL11.glLineWidth(QUEUE_LINE_WIDTH * 1.55F);
        tess.startDrawing(GL11.GL_LINES);
        appendQueueDashSegments(tess, x1, y1, z1, ux, uy, uz, len, phase, cycleLen, ox, oy, oz, breathe, true);
        tess.draw();

        GL11.glLineWidth(QUEUE_LINE_WIDTH);
        tess.startDrawing(GL11.GL_LINES);
        appendQueueDashSegments(tess, x1, y1, z1, ux, uy, uz, len, phase, cycleLen, ox, oy, oz, breathe, false);
        tess.draw();

        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glLineWidth(QUEUE_LINE_WIDTH * 0.85F);
        tess.startDrawing(GL11.GL_LINES);
        appendQueueDashSparks(tess, x1, y1, z1, ux, uy, uz, len, phase, cycleLen, ox, oy, oz, breathe);
        tess.draw();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private static void appendQueueDashSegments(Tessellator tess, double x1, double y1, double z1, double ux, double uy,
        double uz, double len, float phase, float cycleLen, double ox, double oy, double oz, float breathe,
        boolean glowPass) {
        double cursor = phase;
        while (cursor < len) {
            double dashStart = cursor;
            double dashEnd = cursor + QUEUE_DASH_LENGTH;
            if (dashEnd > 0.0D && dashStart < len) {
                double s0 = Math.max(0.0D, dashStart);
                double s1 = Math.min(len, dashEnd);
                if (s1 - s0 > 0.03D) {
                    appendQueueDashBody(tess, x1, y1, z1, ux, uy, uz, s0, s1, ox, oy, oz, breathe, glowPass);
                }
            }
            cursor += cycleLen;
        }
    }

    /** Comet-tail brightness inside each dash: dim tail, bright leading tip. */
    private static void appendQueueDashBody(Tessellator tess, double x1, double y1, double z1, double ux, double uy,
        double uz, double s0, double s1, double ox, double oy, double oz, float breathe, boolean glowPass) {
        int microSteps = 4;
        for (int i = 0; i < microSteps; i++) {
            float local0 = (float) i / microSteps;
            float local1 = (float) (i + 1) / microSteps;
            double pos0 = s0 + (s1 - s0) * local0;
            double pos1 = s0 + (s1 - s0) * local1;
            float headBias = (local0 + local1) * 0.5F;
            float alpha = (glowPass ? GLOW_ALPHA * 0.55F : QUEUE_DASH_ALPHA) * breathe * (0.28F + 0.72F * headBias);
            if (alpha < 0.02F) {
                continue;
            }
            tess.setColorRGBA_F(QUEUE_SILVER_R, QUEUE_SILVER_G, QUEUE_SILVER_B, alpha);
            tess.addVertex(x1 + ux * pos0 + ox, y1 + uy * pos0 + oy, z1 + uz * pos0 + oz);
            tess.addVertex(x1 + ux * pos1 + ox, y1 + uy * pos1 + oy, z1 + uz * pos1 + oz);
        }
    }

    /** Bright spark at the leading tip of each dash. */
    private static void appendQueueDashSparks(Tessellator tess, double x1, double y1, double z1, double ux, double uy,
        double uz, double len, float phase, float cycleLen, double ox, double oy, double oz, float breathe) {
        double cursor = phase;
        while (cursor < len) {
            double dashEnd = cursor + QUEUE_DASH_LENGTH;
            if (dashEnd > 0.0D && dashEnd <= len) {
                double tipStart = Math.max(0.0D, dashEnd - 0.12D);
                float alpha = QUEUE_SPARK_ALPHA * breathe;
                tess.setColorRGBA_F(1.00F, 1.00F, 1.00F, alpha);
                tess.addVertex(x1 + ux * tipStart + ox, y1 + uy * tipStart + oy, z1 + uz * tipStart + oz);
                tess.addVertex(x1 + ux * dashEnd + ox, y1 + uy * dashEnd + oy, z1 + uz * dashEnd + oz);
            }
            cursor += cycleLen;
        }
    }

    /**
     * Blue selection line with beat-synced groove: sharp pulse on width/brightness/glow, dual energy bursts
     * racing along the wire once per beat (180° apart).
     */
    private static void drawRhythmicBlueLine(double x1, double y1, double z1, double x2, double y2, double z2,
        double ox, double oy, double oz, float animSeconds) {
        drawRhythmicLine(
            x1,
            y1,
            z1,
            x2,
            y2,
            z2,
            ox,
            oy,
            oz,
            animSeconds,
            BLUE_R,
            BLUE_G,
            BLUE_B,
            BLUE_LINE_WIDTH,
            0.50F,
            0.90F,
            1.00F);
    }

    /** Red unreachable preview with the same beat-synced rhythm as the blue line. */
    private static void drawRhythmicRedLine(double x1, double y1, double z1, double x2, double y2, double z2, double ox,
        double oy, double oz, float animSeconds) {
        drawRhythmicLine(
            x1,
            y1,
            z1,
            x2,
            y2,
            z2,
            ox,
            oy,
            oz,
            animSeconds,
            RED_R,
            RED_G,
            RED_B,
            RED_LINE_WIDTH,
            RED_HIGHLIGHT_R,
            RED_HIGHLIGHT_G,
            RED_HIGHLIGHT_B);
    }

    private static void drawRhythmicLine(double x1, double y1, double z1, double x2, double y2, double z2, double ox,
        double oy, double oz, float animSeconds, float baseR, float baseG, float baseB, float lineWidth,
        float highlightR, float highlightG, float highlightB) {
        float beatPhase = animSeconds * BEAT_HZ;
        float envelope = beatEnvelope(beatPhase);
        float shimmer = 0.94F + 0.06F * (float) Math.sin(beatPhase * 4.0F * (float) Math.PI);

        float width = lineWidth * (0.80F + 0.42F * envelope);
        float alpha = LINE_ALPHA * shimmer * (0.58F + 0.42F * envelope);
        float glowAlpha = GLOW_ALPHA * (0.40F + 1.70F * envelope);
        float glowWidthFactor = GLOW_WIDTH_FACTOR * (0.88F + 0.55F * envelope);

        drawGradientLineSegment(
            x1,
            y1,
            z1,
            x2,
            y2,
            z2,
            ox,
            oy,
            oz,
            baseR,
            baseG,
            baseB,
            alpha,
            width,
            glowAlpha,
            glowWidthFactor);

        drawRhythmicFlowHighlights(
            x1,
            y1,
            z1,
            x2,
            y2,
            z2,
            ox,
            oy,
            oz,
            beatPhase,
            envelope,
            lineWidth,
            highlightR,
            highlightG,
            highlightB);
    }

    /** cos²(2π·phase): sharp accent on each beat downbeat. */
    private static float beatEnvelope(float beatPhase) {
        float c = (float) Math.cos(beatPhase * 2.0F * (float) Math.PI);
        return c * c;
    }

    /** sin(π·t): fades both endpoints, peaks at the center. */
    private static float lineFadeFactor(float t) {
        if (t <= 0.0F || t >= 1.0F) {
            return END_FADE_FACTOR;
        }
        float peak = (float) Math.sin(t * (float) Math.PI);
        return END_FADE_FACTOR + (MID_FADE_FACTOR - END_FADE_FACTOR) * peak;
    }

    private static void drawGradientLineSegment(double x1, double y1, double z1, double x2, double y2, double z2,
        double ox, double oy, double oz, float r, float g, float b, float alpha, float width, float glowAlpha,
        float glowWidthFactor) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        Tessellator tess = Tessellator.instance;

        GL11.glLineWidth(width * glowWidthFactor);
        tess.startDrawing(GL11.GL_LINES);
        appendGradientSubsegments(tess, x1, y1, z1, dx, dy, dz, ox, oy, oz, r, g, b, glowAlpha);
        tess.draw();

        GL11.glLineWidth(width);
        tess.startDrawing(GL11.GL_LINES);
        appendGradientSubsegments(tess, x1, y1, z1, dx, dy, dz, ox, oy, oz, r, g, b, alpha);
        tess.draw();
    }

    private static void appendGradientSubsegments(Tessellator tess, double x1, double y1, double z1, double dx,
        double dy, double dz, double ox, double oy, double oz, float r, float g, float b, float baseAlpha) {
        for (int i = 0; i < GRADIENT_SEGMENTS; i++) {
            float t0 = (float) i / GRADIENT_SEGMENTS;
            float t1 = (float) (i + 1) / GRADIENT_SEGMENTS;
            float fade = (lineFadeFactor(t0) + lineFadeFactor(t1)) * 0.5F;
            float segAlpha = baseAlpha * fade;
            if (segAlpha < 0.01F) {
                continue;
            }
            tess.setColorRGBA_F(r, g, b, segAlpha);
            tess.addVertex(x1 + dx * t0 + ox, y1 + dy * t0 + oy, z1 + dz * t0 + oz);
            tess.addVertex(x1 + dx * t1 + ox, y1 + dy * t1 + oy, z1 + dz * t1 + oz);
        }
    }

    /** Two beat-locked pulses chase along the line; brightness fades as each pulse travels. */
    private static void drawRhythmicFlowHighlights(double x1, double y1, double z1, double x2, double y2, double z2,
        double ox, double oy, double oz, float beatPhase, float envelope, float lineWidth, float highlightR,
        float highlightG, float highlightB) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        double lenSq = dx * dx + dy * dy + dz * dz;
        if (lenSq < 1.0E-6D) {
            return;
        }

        float segLen = FLOW_SEGMENT * (0.55F + 0.65F * envelope);
        Tessellator tess = Tessellator.instance;
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glLineWidth(lineWidth * (1.25F + 0.55F * envelope));
        tess.startDrawing(GL11.GL_LINES);

        for (int pulse = 0; pulse < 2; pulse++) {
            float phase = beatPhase - pulse * 0.5F;
            float t0 = phase - (float) Math.floor(phase);
            float t1 = t0 + segLen;
            if (t1 > 1.0F) {
                t1 = 1.0F;
            }
            if (t1 <= t0 + 0.01F) {
                continue;
            }
            float travelFade = 1.0F - t0;
            float hAlpha = HIGHLIGHT_ALPHA * envelope * travelFade * travelFade;
            if (hAlpha < 0.04F) {
                continue;
            }
            appendGradientSubsegments(
                tess,
                x1,
                y1,
                z1,
                dx,
                dy,
                dz,
                ox,
                oy,
                oz,
                highlightR,
                highlightG,
                highlightB,
                hAlpha,
                t0,
                t1);
        }

        tess.draw();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private static void appendGradientSubsegments(Tessellator tess, double x1, double y1, double z1, double dx,
        double dy, double dz, double ox, double oy, double oz, float r, float g, float b, float baseAlpha, float tStart,
        float tEnd) {
        int steps = Math.max(2, (int) Math.ceil((tEnd - tStart) * GRADIENT_SEGMENTS));
        for (int i = 0; i < steps; i++) {
            float localT0 = tStart + (tEnd - tStart) * i / steps;
            float localT1 = tStart + (tEnd - tStart) * (i + 1) / steps;
            float fade = (lineFadeFactor(localT0) + lineFadeFactor(localT1)) * 0.5F;
            float segAlpha = baseAlpha * fade;
            if (segAlpha < 0.01F) {
                continue;
            }
            tess.setColorRGBA_F(r, g, b, segAlpha);
            tess.addVertex(x1 + dx * localT0 + ox, y1 + dy * localT0 + oy, z1 + dz * localT0 + oz);
            tess.addVertex(x1 + dx * localT1 + ox, y1 + dy * localT1 + oy, z1 + dz * localT1 + oz);
        }
    }

    private static double[] resolveLineRenderOffset(EntityPlayer player, RenderManager rm, float partialTicks) {
        return new double[] { -rm.viewerPosX, -rm.viewerPosY, -rm.viewerPosZ };
    }

    /**
     * Queue path vertices are always node icon positions. While sliding, include the in-progress segment
     * (anchor -> travel target) until the next hop begins.
     */
    private static List<BlockPos> buildQueuePathNodes() {
        List<BlockPos> path = new ArrayList<BlockPos>();
        if (GrappleClientCache.isTraveling()) {
            appendUniqueNode(
                path,
                new BlockPos(
                    GrappleClientCache.getAnchorX(),
                    GrappleClientCache.getAnchorY(),
                    GrappleClientCache.getAnchorZ()));
            appendUniqueNode(
                path,
                new BlockPos(
                    GrappleClientCache.getTravelTargetX(),
                    GrappleClientCache.getTravelTargetY(),
                    GrappleClientCache.getTravelTargetZ()));
            for (BlockPos queued : GrappleClientCache.getTravelQueue()) {
                appendUniqueNode(path, queued);
            }
            return path;
        }
        BlockPos selected = GrappleClientCache.getSelectedTarget();
        if (selected != null && isValidPreviewTarget(selected)) {
            appendUniqueNode(path, selected);
            for (BlockPos queued : GrappleClientCache.getTravelQueue()) {
                appendUniqueNode(path, queued);
            }
        }
        return path;
    }

    private static void appendUniqueNode(List<BlockPos> path, BlockPos node) {
        if (node == null) {
            return;
        }
        if (!path.isEmpty() && path.get(path.size() - 1)
            .equals(node)) {
            return;
        }
        path.add(node);
    }

    private static boolean isValidPreviewTarget(BlockPos target) {
        return target.getX() != GrappleClientCache.getAnchorX() || target.getY() != GrappleClientCache.getAnchorY()
            || target.getZ() != GrappleClientCache.getAnchorZ();
    }

    private static void beginLineRender() {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private static void endLineRender() {
        GL11.glLineWidth(1.0F);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LIGHTING);
    }
}
