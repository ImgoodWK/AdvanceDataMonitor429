package com.imgood.textech.renders;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.client.GrappleClientCache;
import com.imgood.textech.client.GrappleSelectionUtil;
import com.imgood.textech.items.ItemGrappleHook;
import com.imgood.textech.tileentity.TileEntityGrappleAnchor;
import com.imgood.textech.utils.BlockPos;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Grapple node icons are drawn as world-space billboards at each node's position (always visible).
 * Screen overlay is used only for text hints.
 */
@SideOnly(Side.CLIENT)
public class GrappleHudRenderer {

    private static final ResourceLocation NODE_ICON_TEXTURE = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/grapple_selection_icon.png");
    private static final ResourceLocation NODE_ICON_CURSOR_TEXTURE = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/grapple_selection_icon_cursor.png");

    private static final int COLOR_ICON_UNSELECTED = 0xFFFFFF;
    private static final int COLOR_ICON_SELECTED = 0x00FFFF;
    private static final int COLOR_ICON_UNREACHABLE = 0xFF4444;
    private static final int COLOR_CURSOR_FLASH_WHITE = 0xFFFFFF;

    /** Degrees per second for selected icon rotation. */
    private static final float ICON_ROTATION_SPEED = 24.0F;
    /** Pulse frequency (Hz) for selected icon scale. */
    private static final float ICON_PULSE_SPEED = 1.8F;
    private static final float ICON_PULSE_MIN = 0.88F;
    private static final float ICON_PULSE_MAX = 1.12F;
    /** Flash frequency (Hz) for cursor overlay color. */
    private static final float ICON_FLASH_SPEED = 2.5F;

    private static final float LERP = 0.22F;
    private static final float BASE_ICON_SCALE = 1.0F;
    private static final float SELECTED_BOOST = 0.55F;
    private static final float WORLD_ICON_SIZE = 1.0F;
    /** Fixed anchor half-size for billboard placement (independent of selection scale). */
    private static final float ANCHOR_HALF_SIZE = WORLD_ICON_SIZE * BASE_ICON_SCALE * 0.5F;
    /**
     * World half-size = view distance × this factor, so icons appear the same screen size at any range.
     */
    private static final float ICON_SCREEN_SCALE = 0.025F;
    /** Billboard-local Y offset as a fraction of {@code halfSize} (aligns texture center with line). */
    private static final float ICON_TEXTURE_CENTER_OFFSET_FRAC = 0.24F;
    /** Icon 2 (cursor overlay) is drawn at half the base icon size. */
    private static final float ICON_CURSOR_SIZE_SCALE = 0.5F;
    /** Text scale as a fraction of the icon half-size (keeps labels smaller than the icon). */
    private static final float LABEL_SIZE_OF_ICON = 0.066F;
    private static final float LABEL_VERTICAL_OFFSET = 1.75F;
    private static final int LABEL_BG_COLOR = 0x40000000;
    /** Detach hint color cycles white →green via sine wave. */
    private static final int DETACH_HINT_COLOR_WHITE = 0xFFFFFF;
    private static final float DETACH_HINT_COLOR_CYCLE_HZ = 1.2F;
    /** Fixed 50% opacity for detach hint text. */
    private static final int DETACH_HINT_ALPHA = (int) (0.50F * 255.0F);

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        if (player == null || mc.theWorld == null || mc.currentScreen != null) {
            return;
        }

        boolean attached = GrappleClientCache.isAttached();
        if (attached) {
            renderDetachHintScreenOverlay(mc);
        }

        if (!ItemGrappleHook.isHoldingHook(player)) {
            return;
        }

        ItemStack hookStack = player.getHeldItem();
        boolean showName = ItemGrappleHook.getShowNodeName(hookStack);
        boolean showDistance = ItemGrappleHook.getShowNodeDistance(hookStack);

        float partialTicks = event.partialTicks;
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int screenW = sr.getScaledWidth();
        int screenH = sr.getScaledHeight();

        if (!attached) {
            List<BlockPos> candidates = GrappleSelectionUtil.buildCandidateNodes(player, false);
            GrappleSelectionUtil.refreshSelection(player, candidates, false, partialTicks);
        }
        List<BlockPos> iconNodes = GrappleSelectionUtil.buildIconNodes(player, attached);
        if (iconNodes.isEmpty()) {
            if (!attached) {
                GrappleClientCache.setSelectedTarget(null);
            }
            return;
        }

        BlockPos magnetic = GrappleClientCache.getSelectedTarget();

        for (BlockPos node : iconNodes) {
            boolean selected = node.equals(magnetic);
            boolean reachable = !attached || !selected
                || GrappleSelectionUtil.isTravelReachable(player, node, partialTicks);

            float crosshairDist = GrappleSelectionUtil.crosshairDistance(player, node, partialTicks, screenW, screenH);
            if (crosshairDist >= Float.MAX_VALUE - 1.0F) {
                continue;
            }

            float targetScale = selected ? BASE_ICON_SCALE + SELECTED_BOOST : BASE_ICON_SCALE;
            float currentScale = GrappleClientCache.getIconScale(node);
            currentScale += (targetScale - currentScale) * LERP;
            GrappleClientCache.setIconScale(node, currentScale);

            double[] billboard = GrappleSelectionUtil.getNodeIconBillboardPosition(mc.theWorld, node, ANCHOR_HALF_SIZE);
            if (billboard == null) {
                continue;
            }

            int cursorColor = TileEntityGrappleAnchor
                .resolveIconCursorColor(mc.theWorld, node.getX(), node.getY(), node.getZ());
            renderWorldBillboardIcon(
                mc,
                player,
                node,
                billboard[0],
                billboard[1],
                billboard[2],
                currentScale,
                selected,
                reachable,
                cursorColor,
                showName,
                showDistance,
                partialTicks);
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.TEXT) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        if (player == null || mc.currentScreen != null) {
            return;
        }
        if (!ItemGrappleHook.isHoldingHook(player) && !GrappleClientCache.isAttached()) {
            return;
        }

        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        FontRenderer fr = mc.fontRenderer;
        int cx = sr.getScaledWidth() / 2;
        int cy = sr.getScaledHeight() / 2;
        int hintY = cy + 14;

        if (GrappleClientCache.isAttached()) {
            return;
        }

        List<BlockPos> hintRange = GrappleSelectionUtil.findNodesInRange(player, Config.grappleHintRange);
        if (hintRange.isEmpty()) {
            return;
        }

        List<BlockPos> interactRange = GrappleSelectionUtil.findNodesInRange(player, Config.grappleInteractRange);
        List<BlockPos> candidates = GrappleSelectionUtil.buildCandidateNodes(player, false);
        BlockPos selected = GrappleSelectionUtil.refreshSelection(player, candidates, false, 1.0F);
        if (selected == null) {
            selected = GrappleClientCache.getSelectedTarget();
        }

        boolean canInteract = selected != null && interactRange.contains(selected);
        String hint = canInteract ? I18n.format("adm.hint.grapple.attach") : I18n.format("adm.hint.grapple.nearby");
        fr.drawStringWithShadow(
            hint,
            cx - fr.getStringWidth(hint) / 2,
            hintY,
            canInteract ? HudRenderUtil.COLOR_TITLE : HudRenderUtil.COLOR_LABEL);
    }

    private void renderWorldBillboardIcon(Minecraft mc, EntityPlayer player, BlockPos node, double wx, double wy,
        double wz, float selectionScale, boolean selected, boolean reachable, int configuredCursorColor,
        boolean showName, boolean showDistance, float partialTicks) {

        RenderManager rm = RenderManager.instance;
        double x = wx - rm.viewerPosX;
        double y = wy - rm.viewerPosY;
        double z = wz - rm.viewerPosZ;
        float viewDist = MathHelper.sqrt_float((float) (x * x + y * y + z * z));
        if (viewDist < 0.05F) {
            viewDist = 0.05F;
        }
        float halfSize = viewDist * ICON_SCREEN_SCALE * selectionScale;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x, y, z);
            GL11.glNormal3f(0.0F, 1.0F, 0.0F);
            GL11.glRotatef(-rm.playerViewY, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(rm.playerViewX, 1.0F, 0.0F, 0.0F);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
            GL11.glTranslatef(0.0F, halfSize * ICON_TEXTURE_CENTER_OFFSET_FRAC, 0.0F);

            if (selected) {
                if (!reachable) {
                    drawBillboardTexturedQuad(mc, NODE_ICON_TEXTURE, halfSize, COLOR_ICON_UNREACHABLE);
                } else {
                    float animSeconds = (mc.theWorld.getTotalWorldTime() + partialTicks) / 20.0F;
                    float pulseWave = 0.5F + 0.5F * (float) Math.sin(animSeconds * ICON_PULSE_SPEED * Math.PI * 2.0F);
                    float pulseScale = ICON_PULSE_MIN + (ICON_PULSE_MAX - ICON_PULSE_MIN) * pulseWave;
                    float rotation = animSeconds * ICON_ROTATION_SPEED;

                    GL11.glPushMatrix();
                    GL11.glRotatef(rotation, 0.0F, 0.0F, 1.0F);
                    GL11.glScalef(pulseScale, pulseScale, 1.0F);
                    drawBillboardTexturedQuad(mc, NODE_ICON_TEXTURE, halfSize, COLOR_ICON_SELECTED);
                    GL11.glPopMatrix();

                    float flashWave = 0.5F + 0.5F * (float) Math.sin(animSeconds * ICON_FLASH_SPEED * Math.PI * 2.0F);
                    int cursorColor = lerpRgb(configuredCursorColor, COLOR_CURSOR_FLASH_WHITE, flashWave);
                    drawBillboardTexturedQuad(
                        mc,
                        NODE_ICON_CURSOR_TEXTURE,
                        halfSize * ICON_CURSOR_SIZE_SCALE,
                        cursorColor);
                }
            } else {
                drawBillboardTexturedQuad(mc, NODE_ICON_TEXTURE, halfSize, COLOR_ICON_UNSELECTED);
            }

            if (showName || showDistance) {
                renderNodeLabels(mc, player, node, halfSize, configuredCursorColor, showName, showDistance);
            }
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glDepthMask(true);
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_LIGHTING);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private void renderNodeLabels(Minecraft mc, EntityPlayer player, BlockPos node, float iconHalfSize, int nameColor,
        boolean showName, boolean showDistance) {
        FontRenderer fontRenderer = mc.fontRenderer;
        float textScale = iconHalfSize * LABEL_SIZE_OF_ICON;
        if (textScale < 0.003F) {
            textScale = 0.003F;
        }

        String nameText = showName
            ? TileEntityGrappleAnchor.resolveDisplayName(mc.theWorld, node.getX(), node.getY(), node.getZ())
            : null;
        String distanceText = null;
        if (showDistance) {
            double dist = player.getDistance(node.getX() + 0.5D, node.getY() + 0.5D, node.getZ() + 0.5D);
            distanceText = I18n.format("adm.label.grapple.distance", String.format("%.1f", dist));
        }

        if ((nameText == null || nameText.isEmpty()) && (distanceText == null || distanceText.isEmpty())) {
            return;
        }

        GL11.glPushMatrix();
        try {
            float labelStartY = -iconHalfSize * LABEL_VERTICAL_OFFSET;
            GL11.glTranslatef(0.0F, labelStartY, 0.0F);
            GL11.glScalef(-textScale, -textScale, textScale);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            int lineY = 0;
            if (nameText != null && !nameText.isEmpty()) {
                int textWidth = fontRenderer.getStringWidth(nameText);
                int halfWidth = textWidth / 2;
                drawLabelBackground(halfWidth, fontRenderer.FONT_HEIGHT);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                fontRenderer.drawString(nameText, -halfWidth, lineY, nameColor);
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                lineY += fontRenderer.FONT_HEIGHT + 1;
            }
            if (distanceText != null && !distanceText.isEmpty()) {
                int textWidth = fontRenderer.getStringWidth(distanceText);
                int halfWidth = textWidth / 2;
                drawLabelBackground(halfWidth, fontRenderer.FONT_HEIGHT);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                fontRenderer.drawString(distanceText, -halfWidth, lineY, HudRenderUtil.COLOR_LABEL);
            }
        } finally {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glPopMatrix();
        }
    }

    private static void drawLabelBackground(int halfWidth, int height) {
        int left = -halfWidth - 2;
        int right = halfWidth + 2;
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_I(LABEL_BG_COLOR & 0xFFFFFF, (LABEL_BG_COLOR >> 24) & 0xFF);
        tessellator.addVertex(left, height + 1, 0.0D);
        tessellator.addVertex(right, height + 1, 0.0D);
        tessellator.addVertex(right, -1, 0.0D);
        tessellator.addVertex(left, -1, 0.0D);
        tessellator.draw();
    }

    private static void renderDetachHintScreenOverlay(Minecraft mc) {
        if (mc.thePlayer == null || mc.currentScreen != null) {
            return;
        }
        String detachHint = I18n.format("adm.hint.grapple.detach");
        int queueSize = GrappleClientCache.getTravelQueueSize();
        if (queueSize > 0) {
            detachHint = detachHint + " | " + I18n.format("adm.hint.grapple.queued", queueSize);
        }
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        FontRenderer fr = mc.fontRenderer;
        int x = sr.getScaledWidth() / 2 - fr.getStringWidth(detachHint) / 2;
        int y = sr.getScaledHeight() / 2 + 14 + fr.FONT_HEIGHT * 4;
        drawCyclingHintText(fr, detachHint, x, y, mc);
    }

    private static void drawCyclingHintText(FontRenderer fr, String text, int x, int y, Minecraft mc) {
        float seconds = 0.0F;
        if (mc.theWorld != null) {
            seconds = mc.theWorld.getTotalWorldTime() / 20.0F;
        }
        float wave = 0.5F + 0.5F * (float) Math.sin(seconds * DETACH_HINT_COLOR_CYCLE_HZ * Math.PI * 2.0);
        int rgb = lerpRgb(DETACH_HINT_COLOR_WHITE, HudRenderUtil.COLOR_GOOD, wave);
        int argb = HudRenderUtil.packArgb(DETACH_HINT_ALPHA, rgb);
        HudRenderUtil.drawScreenTextWithAlpha(fr, text, x, y, argb);
    }

    private static void drawBillboardTexturedQuad(Minecraft mc, ResourceLocation texture, float halfSize,
        int tintColor) {
        mc.renderEngine.bindTexture(texture);
        GL11.glColor4f(
            ((tintColor >> 16) & 255) / 255.0F,
            ((tintColor >> 8) & 255) / 255.0F,
            (tintColor & 255) / 255.0F,
            0.95F);

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(-halfSize, -halfSize, 0.0D, 0.0D, 1.0D);
        tessellator.addVertexWithUV(halfSize, -halfSize, 0.0D, 1.0D, 1.0D);
        tessellator.addVertexWithUV(halfSize, halfSize, 0.0D, 1.0D, 0.0D);
        tessellator.addVertexWithUV(-halfSize, halfSize, 0.0D, 0.0D, 0.0D);
        tessellator.draw();
    }

    private static int lerpRgb(int from, int to, float t) {
        if (t <= 0.0F) {
            return from;
        }
        if (t >= 1.0F) {
            return to;
        }
        int r1 = (from >> 16) & 255;
        int g1 = (from >> 8) & 255;
        int b1 = from & 255;
        int r2 = (to >> 16) & 255;
        int g2 = (to >> 8) & 255;
        int b2 = to & 255;
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (r << 16) | (g << 8) | b;
    }
}
