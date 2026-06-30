package com.imgood.textech.renders;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.modularui.api.GlStateManager;
import com.imgood.textech.AdvanceDataMonitor;

public class RenderDataImprintItem implements IItemRenderer {

    private static final ResourceLocation advanceDataDisplayTexture = new ResourceLocation(
        AdvanceDataMonitor.MODID + ":textures/model/AdvanceDataMonitor.png");
    private static final IModelCustom advanceDtaDisplayModel = AdvancedModelLoader
        .loadModel(new ResourceLocation(AdvanceDataMonitor.MODID + ":model/DataWeave.obj"));
    private static final int SCROLL_SPEED = 30; // 像素/�?
    private static final int LINE_HEIGHT = 9; // 行高
    private static final int MAX_LINES = 9; // 最大显示行�?

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return type == ItemRenderType.INVENTORY || type == ItemRenderType.ENTITY
            || type == ItemRenderType.EQUIPPED_FIRST_PERSON
            || type == ItemRenderType.EQUIPPED;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return true;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        Minecraft.getMinecraft().renderEngine.bindTexture(advanceDataDisplayTexture);

        // ========== 基础模型渲染 ==========
        GL11.glPushMatrix();
        applyBaseModelTransform(type);
        renderBaseModel();
        GL11.glPopMatrix();

        if (shouldRenderFirstPersonOverlay(type)) {
            // ========== Text overlay ==========
            GL11.glPushMatrix();
            applyTextTransform(type);
            renderBoundPositionText(type, item);
            GL11.glPopMatrix();

            // ========== Bound block preview ==========
            if (shouldRenderBlock(type, item)) {
                GL11.glPushMatrix();
                try {
                    renderBlockModel(type, item);
                } finally {
                    GL11.glPopMatrix();
                }
            }

            GL11.glPushMatrix();
            applyNbtScrollTransform(type);
            renderNbtScrollText(type, item);
            GL11.glPopMatrix();
        }
    }

    private void applyBaseModelTransform(ItemRenderType type) {
        switch (type) {
            case ENTITY:
                GL11.glRotatef(-45, 0, 1, 0);
                GL11.glTranslatef(0.5F, 0.5F, 0.5F);
                GL11.glTranslatef(-1F, -0.2F, -0.5F);
                break;
            case EQUIPPED_FIRST_PERSON:
                GL11.glRotatef(-45, 0, 1, 0);
                GL11.glTranslatef(0.5F, 0.5F, 0.5F);
                GL11.glTranslatef(-1F, -0.2F, -0.5F);
                break;
            case EQUIPPED:
                GL11.glRotatef(-45, 0, 1, 0);
                GL11.glTranslatef(0.5F, 0.5F, 0.5F);
                GL11.glTranslatef(-1F, -0.2F, -0.5F);
                break;
            case INVENTORY:
                GL11.glRotatef(-90, 0, 1, 0);
                GL11.glTranslatef(0.5F, 0.5F, 0.5F);
                GL11.glTranslatef(-1F, -1.3F, -0.5F);
                break;
        }
    }

    private void renderBaseModel() {
        // 渲染不透明部分
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableLighting();
        advanceDtaDisplayModel.renderAllExcept("Lighting", "CableLighting", "RollLighting", "Roll", "Screen");

        // 渲染滚动部件
        GL11.glPushMatrix();
        GL11.glTranslated(-0.1, 0, 0.05);
        advanceDtaDisplayModel.renderOnly("Roll");
        GL11.glPopMatrix();

        // 渲染发光部件
        renderGlowingParts();
    }

    private void renderGlowingParts() {
        GlStateManager.pushAttrib();
        GlStateManager.depthMask(false);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0f, 240.0f);

        advanceDtaDisplayModel.renderOnly("Lighting", "CableLighting");

        // 半透明屏幕
        GlStateManager.color(1.0f, 1.0f, 1.0f, 0.5f);
        advanceDtaDisplayModel.renderOnly("Screen");
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        // 发光滚动部件
        GL11.glPushMatrix();
        GL11.glTranslated(-0.1, 0, 0.05);
        advanceDtaDisplayModel.renderOnly("RollLighting");
        GL11.glPopMatrix();

        GlStateManager.popAttrib();
    }

    private void applyTextTransform(ItemRenderType type) {
        switch (type) {
            case ENTITY:
                GL11.glTranslatef(0, 0.5f, 0);
                GL11.glScalef(0.015f, 0.015f, 0.015f);
                GL11.glRotatef(135, 0, 1, 0);
                break;
            case EQUIPPED_FIRST_PERSON:
                GL11.glTranslatef(-0.4f, 1.55f, -0.2f);
                GL11.glScalef(0.005f, 0.005f, 0.005f);
                GL11.glRotatef(135, 0, 1, 0);
                GL11.glRotatef(180, 0, 0, 1);
                break;
            case EQUIPPED:
                GL11.glTranslatef(0.5f, 1.0f, 0.5f);
                GL11.glScalef(0.015f, 0.015f, 0.015f);
                GL11.glRotatef(45, 0, 1, 0);
                break;
            case INVENTORY:
                GL11.glTranslatef(8.0f, 8.0f, 0);
                GL11.glScalef(0.03f, 0.03f, 0.03f);
                break;
        }
    }

    private void renderBoundPositionText(ItemRenderType type, ItemStack item) {
        NBTTagCompound nbt = item.getTagCompound();
        if (nbt == null) return;

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        String positionText = "Not Bound";
        boolean hasPosition = false;

        if (nbt.hasKey("boundPos", 10)) {
            NBTTagCompound posTag = nbt.getCompoundTag("boundPos");
            if (posTag.hasKey("x") && posTag.hasKey("y") && posTag.hasKey("z")) {
                int x = posTag.getInteger("x");
                int y = posTag.getInteger("y");
                int z = posTag.getInteger("z");
                positionText = String.format("Bound: X:%d Y:%d Z:%d", x, y, z);
                hasPosition = true;
            }
        }

        // 渲染基础文字
        renderText(font, positionText, 0x00FFFF, 0);

        // 在第一人称视角显示方块名称
        if (type == ItemRenderType.EQUIPPED_FIRST_PERSON && hasPosition) {
            String blockName = getBoundBlockName(nbt);
            if (!blockName.isEmpty()) {
                renderText(font, blockName, 0x00FFFF, font.FONT_HEIGHT + 2);
            }
        }
    }

    private void renderText(FontRenderer font, String text, int color, int yOffset) {
        GlStateManager.pushAttrib();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();

        font.drawString(text, -40, yOffset, color, false);

        GlStateManager.popAttrib();
    }

    private String getBoundBlockName(NBTTagCompound nbt) {
        if (nbt.hasKey("boundBlock")) {
            Block block = Block.getBlockFromName(nbt.getString("boundBlock"));
            if (nbt.hasKey("mName")) {
                String[] blockName = nbt.getString("boundBlock")
                    .split(":");
                return I18n.format(blockName[1] + "." + nbt.getString("mName") + ".name");
            }
            int itemMeta = getBoundItemMeta(nbt);
            if (block != null) {
                ItemStack stack = new ItemStack(block, 1, itemMeta);
                try {
                    return stack.getDisplayName();
                } catch (Exception e) {
                    AdvanceDataMonitor.LOG.error("Error getting block name", e);
                }
            }
        }
        return "";
    }

    private boolean shouldRenderFirstPersonOverlay(ItemRenderType type) {
        return type == ItemRenderType.EQUIPPED_FIRST_PERSON;
    }

    private boolean shouldRenderBlock(ItemRenderType type, ItemStack item) {
        return shouldRenderFirstPersonOverlay(type) && item.hasTagCompound()
            && item.getTagCompound()
                .hasKey("boundBlock");
    }

    private void renderBlockModel(ItemRenderType type, ItemStack item) {
        ItemStack boundStack = getBoundItemStack(item);
        if (boundStack == null) {
            return;
        }

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            applyBoundItemPreviewTransform(type);
            renderBoundItemPreview(boundStack, getBoundItemPreviewScale(type));
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("Bound item preview rendering failed", e);
        } finally {
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }

    private ItemStack getBoundItemStack(ItemStack item) {
        NBTTagCompound nbt = item.getTagCompound();
        if (nbt == null || !nbt.hasKey("boundBlock")) {
            return null;
        }
        Block block = Block.getBlockFromName(nbt.getString("boundBlock"));
        if (block == null) {
            return null;
        }
        return new ItemStack(block, 1, getBoundItemMeta(nbt));
    }

    private int getBoundItemMeta(NBTTagCompound nbt) {
        NBTTagCompound teNbt = nbt.getCompoundTag("boundTE");
        String tileId = teNbt.getString("id");
        if (tileId.contains("BaseMetaTileEntity") && teNbt.hasKey("mID")) {
            return Math.max(0, teNbt.getInteger("mID"));
        }
        if (nbt.hasKey("mId")) {
            return Math.max(0, nbt.getInteger("mId"));
        }
        return Math.max(0, nbt.hasKey("boundMeta") ? nbt.getInteger("boundMeta") : 0);
    }

    private void applyBoundItemPreviewTransform(ItemRenderType type) {
        switch (type) {
            case INVENTORY:
                GL11.glTranslatef(6.0f, 5.0f, 80.0f);
                break;
            case ENTITY:
                GL11.glTranslatef(0.42f, 0.96f, 0.10f);
                GL11.glScalef(0.0005f, 0.0005f, 0.0005f);
                GL11.glRotatef(25.0f, 1.0f, 0.0f, 0.0f);
                GL11.glRotatef(45.0f, 0.0f, 1.0f, 0.0f);
                break;
            case EQUIPPED_FIRST_PERSON:
                GL11.glTranslatef(-0.10f, 1.12f, -0.36f);
                GL11.glRotatef(135.0f, 0.0f, 1.0f, 0.0f);
                GL11.glRotatef(180.0f, 0.0f, 0.0f, 1.0f);
                GL11.glScalef(0.25f, 0.25f, 0.25f);
                break;
            case EQUIPPED:
                GL11.glTranslatef(0.58f, 0.92f, 0.22f);
                GL11.glRotatef(25.0f, 1.0f, 0.0f, 0.0f);
                GL11.glRotatef(45.0f, 0.0f, 1.0f, 0.0f);
                break;
        }
    }

    private float getBoundItemPreviewScale(ItemRenderType type) {
        switch (type) {
            case INVENTORY:
                return 18.0f;
            case EQUIPPED_FIRST_PERSON:
                return 4.5f;
            default:
                return 12.0f;
        }
    }

    private void renderBoundItemPreview(ItemStack stack, float iconScale) {
        if (stack == null || stack.getItem() == null) return;

        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0f, 240.0f);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(true);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            float aeItemScale = 0.8f * iconScale;
            GL11.glRotatef(180.0f, 1.0f, 0.0f, 0.0f);
            GL11.glScalef(1.0f, -1.0f, 1.0f);
            GL11.glScalef(aeItemScale / 32.0f, aeItemScale / 32.0f, 0.0001f);
            GL11.glTranslatef(-8.0f, -5.0f, 0.0f);
            RenderItem.getInstance()
                .renderItemAndEffectIntoGUI(
                    Minecraft.getMinecraft().fontRenderer,
                    Minecraft.getMinecraft()
                        .getTextureManager(),
                    stack,
                    0,
                    0);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    // 新增NBT滚动文本变换方法
    private void applyNbtScrollTransform(ItemRenderType type) {
        switch (type) {
            case ENTITY:
                GL11.glTranslatef(1.2f, 0.5f, 0); // 右侧偏移
                GL11.glScalef(0.015f, 0.015f, 0.015f);
                GL11.glRotatef(135, 0, 1, 0);
                break;
            case EQUIPPED_FIRST_PERSON:
                GL11.glTranslatef(0.2f, 1.55f, -0.2f); // 右侧位置调整
                GL11.glScalef(0.005f, 0.005f, 0.005f);
                GL11.glRotatef(135, 0, 1, 0);
                GL11.glRotatef(180, 0, 0, 1);
                break;
            case EQUIPPED:
                GL11.glTranslatef(1.5f, 1.0f, 0.5f); // 右侧偏移
                GL11.glScalef(0.015f, 0.015f, 0.015f);
                GL11.glRotatef(45, 0, 1, 0);
                break;
            case INVENTORY:
                GL11.glTranslatef(24.0f, 8.0f, 0); // 右侧偏移
                GL11.glScalef(0.03f, 0.03f, 0.03f);
                break;
        }
    }

    // 新增NBT滚动文本渲染方法
    private void renderNbtScrollText(ItemRenderType type, ItemStack item) {
        int MAX_CHARS = 30;
        NBTTagCompound nbt = item.getTagCompound();
        if (nbt == null) return;

        NBTTagCompound teNbt = nbt.getCompoundTag("boundTE");
        if (teNbt.hasNoTags()) return;

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        List<String> nbtLines = new ArrayList<>();

        // 收集所有NBT键值对
        @SuppressWarnings("unchecked")
        Set<String> keys = teNbt.func_150296_c();
        for (String key : keys) {
            String value = teNbt.getTag(key)
                .toString();
            String line = key + ": " + value;

            if (line.length() > MAX_CHARS) {
                line = line.substring(0, MAX_CHARS - 3) + "...";
            }
            nbtLines.add(line);
        }

        if (nbtLines.isEmpty()) return;

        final int totalLines = nbtLines.size();
        final int contentHeight = totalLines * LINE_HEIGHT;
        final int viewportHeight = MAX_LINES * LINE_HEIGHT;

        final long scrollOffset = (System.currentTimeMillis() * SCROLL_SPEED / 1000) % contentHeight;
        final int startLine = (int) (scrollOffset / LINE_HEIGHT);
        final int offsetInLine = (int) (scrollOffset % LINE_HEIGHT);

        GlStateManager.pushAttrib();
        try {
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            int yBase = -offsetInLine;

            // 定义渐变区域（视口顶部和底部20%的区域）
            final float FADE_HEIGHT = viewportHeight * 0.3f;

            for (int i = 0; i < MAX_LINES + 1; i++) {
                int lineIndex = (startLine + i) % totalLines;
                int lineY = yBase + i * LINE_HEIGHT;

                if (lineY > -LINE_HEIGHT && lineY < viewportHeight) {
                    String originalText = nbtLines.get(lineIndex);
                    String wrappedText = wrapText(font, originalText, 200);

                    int renderY = lineY + 65;
                    if (type == ItemRenderType.INVENTORY) {
                        renderY += 8;
                    }

                    // 计算透明度（基于在视口中的位置）
                    float alpha = 1.0f;

                    // 计算距离视口顶部和底部的距离
                    float distTop = Math.max(0, renderY - 50);
                    float distBottom = Math.max(0, 150 - renderY); // 150 = 50 + viewportHeight

                    // 顶部渐变：从0%�?0%高度
                    if (distTop < FADE_HEIGHT) {
                        alpha = distTop / FADE_HEIGHT;
                    }
                    // 底部渐变：从80%�?00%高度
                    else if (distBottom < FADE_HEIGHT) {
                        alpha = distBottom / FADE_HEIGHT;
                    }

                    // 确保透明度在有效范围
                    alpha = Math.max(0.0f, Math.min(1.0f, alpha));

                    // 将透明度转换为颜色�?
                    int alphaInt = (int) (alpha * 255);
                    int colorWithAlpha = (alphaInt << 24) | 0x00FFFF;

                    font.drawString(wrappedText, 20, renderY, colorWithAlpha);
                }
            }
        } finally {
            GlStateManager.popAttrib();
        }
    }

    // 新增文本换行方法
    private String wrapText(FontRenderer font, String text, int maxWidth) {
        StringBuilder result = new StringBuilder();
        StringBuilder currentLine = new StringBuilder();

        for (String word : text.split(" ")) {
            if (font.getStringWidth(currentLine + word) < maxWidth) {
                currentLine.append(word)
                    .append(" ");
            } else {
                result.append(currentLine)
                    .append("\n");
                currentLine = new StringBuilder(word).append(" ");
            }
        }
        return result.append(currentLine)
            .toString()
            .trim();
    }
}
