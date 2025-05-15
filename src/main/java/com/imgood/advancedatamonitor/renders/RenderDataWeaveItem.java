package com.imgood.advancedatamonitor.renders;

import com.gtnewhorizons.modularui.api.GlStateManager;
import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RenderDataWeaveItem implements IItemRenderer {
    private static final ResourceLocation advanceDataDisplayTexture = new ResourceLocation(
            AdvanceDataMonitor.MODID + ":textures/model/AdvanceDataMonitor.png"
    );
    private static final IModelCustom advanceDtaDisplayModel = AdvancedModelLoader.loadModel(
            new ResourceLocation(AdvanceDataMonitor.MODID + ":model/DataWeave.obj")
    );
    private static final int SCROLL_SPEED = 30; // 像素/秒
    private static final int LINE_HEIGHT = 10;   // 行高
    private static final int MAX_LINES = 10;      // 最大显示行数


    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return type == ItemRenderType.INVENTORY ||
                type == ItemRenderType.ENTITY ||
                type == ItemRenderType.EQUIPPED_FIRST_PERSON ||
                type == ItemRenderType.EQUIPPED;
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

        // ========== 文字信息渲染 ==========
        GL11.glPushMatrix();
        applyTextTransform(type);
        renderBoundPositionText(type, item);
        GL11.glPopMatrix();

        // ========== 方块模型渲染 ==========
        if (shouldRenderBlock(type, item)) {
            GL11.glPushMatrix();
            try {
                renderBlockModel(item);
            } finally {
                GL11.glPopMatrix();
            }
        }
        GL11.glPushMatrix();
        applyNbtScrollTransform(type); // 新增变换方法
        renderNbtScrollText(type, item);
        GL11.glPopMatrix();
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
            if (block != null) {
                ItemStack stack = new ItemStack(block, 1, nbt.getInteger("boundMeta"));
                try {
                    return stack.getDisplayName();
                } catch (Exception e) {
                    AdvanceDataMonitor.LOG.error("Error getting block name", e);
                }
            }
        }
        return "";
    }

    private boolean shouldRenderBlock(ItemRenderType type, ItemStack item) {
        return type == ItemRenderType.EQUIPPED_FIRST_PERSON &&
                item.hasTagCompound() &&
                item.getTagCompound().hasKey("boundBlock");
    }

    private void renderBlockModel(ItemStack item) {
        NBTTagCompound nbt = item.getTagCompound();
        if (nbt == null) return;

        // 从NBT获取方块信息
        String blockId = nbt.getString("boundBlock");


        int meta;
        NBTTagCompound teNbt = nbt.getCompoundTag("boundTE");
        String blockName = teNbt.getString("id");
        if (blockName.contains("BaseMetaTileEntity")){
            meta = teNbt.getInteger("mID");
        } else {
            meta = nbt.getInteger("boundMeta");
        }

        // 创建方块ItemStack
        Block block = Block.getBlockFromName(blockId);
        if (block == null) return;

        ItemStack blockStack = createBlockStack(block, meta, teNbt);

        // 执行渲染
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            applyBlockModelTransforms();
            renderBlockStack(blockStack);
        } finally {
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }

    private ItemStack createBlockStack(Block block, int meta, NBTTagCompound teNbt) {
        ItemStack stack = new ItemStack(block, 1, meta);
        if (!teNbt.hasNoTags()) {
            NBTTagCompound stackNbt = new NBTTagCompound();
            stackNbt.setTag("BlockEntityTag", teNbt);
            stack.setTagCompound(stackNbt);
        }
        return stack;
    }

    private void applyBlockModelTransforms() {
        GL11.glTranslatef(0f, 1.2f, 0f);
        GL11.glScalef(0.025f, 0.025f, 0.025f);
        //GL11.glRotatef(45.0f, 0.0f, 1.0f, 0.0f);
        GL11.glRotatef(-60f, 1.7f, 4f, 2f);
        GL11.glRotatef(170.0f, 0.0f, 0.0f, 1.0f);
        GL11.glRotatef(40.0f, 1, 1f, 0f);
        GL11.glRotatef(-35.0f, 0, 0, 1);
        GL11.glRotatef(-10f, 1, 0, 0);
        GL11.glRotatef(20f, 0, 1, 0);

    }

    private void renderBlockStack(ItemStack stack) {
        // 保存所有OpenGL状态
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

        // 配置渲染环境
        GlStateManager.disableCull(); // 禁用面剔除
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        RenderHelper.enableStandardItemLighting();

        // 绑定方块纹理
        Minecraft.getMinecraft().renderEngine.bindTexture(TextureMap.locationBlocksTexture);

        // 调整渲染参数防止Z-fighting
        GL11.glTranslatef(0.5f, 0.5f, 0.5f);
        GL11.glScalef(0.8f, 0.8f, 0.8f);
        GL11.glRotatef(-30.0f, 1.0f, 0.0f, 0.0f); // 视角倾斜

        // 核心渲染调用
        try {
            RenderItem.getInstance().renderItemIntoGUI(
                    Minecraft.getMinecraft().fontRenderer,
                    Minecraft.getMinecraft().getTextureManager(),
                    stack,
                    0, 0
            );
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("Block rendering failed", e);
        }

        // 恢复环境
        RenderHelper.disableStandardItemLighting();
        GlStateManager.enableCull(); // 恢复面剔除
        GlStateManager.disableRescaleNormal();

        // 强制刷新深度缓冲
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.depthMask(false);
        GlStateManager.depthMask(true);

        GL11.glPopAttrib();
        GL11.glPopMatrix();
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
            String value = teNbt.getTag(key).toString();
            String line = key + ": " + value;

            // 新增字符数限制逻辑
            if (line.length() > MAX_CHARS) {
                line = line.substring(0, MAX_CHARS-3) + "..."; // 保留17字符+3个点=20字符
            }

            nbtLines.add(line);
        }

        if (nbtLines.isEmpty()) return;

        final int totalLines = nbtLines.size();
        final int contentHeight = totalLines * LINE_HEIGHT;
        final int viewportHeight = MAX_LINES * LINE_HEIGHT;

        // 计算循环滚动参数（添加内容高度取模实现循环）
        final long scrollOffset = (System.currentTimeMillis() * SCROLL_SPEED / 1000) % contentHeight;
        final int startLine = (int) (scrollOffset / LINE_HEIGHT);
        final int offsetInLine = (int) (scrollOffset % LINE_HEIGHT);

        GlStateManager.pushAttrib();
        try {
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            // 基础Y轴偏移（实现平滑滚动）
            int yBase = -offsetInLine;

            // 渲染可视区域内的行（多渲染1行保证过渡平滑）
            for (int i = 0; i < MAX_LINES + 1; i++) {
                // 计算循环索引
                int lineIndex = (startLine + i) % totalLines;
                // 计算当前行Y位置
                int lineY = yBase + i * LINE_HEIGHT;

                // 判断是否在可视区域内（上下扩展1行高度）
                if (lineY > -LINE_HEIGHT && lineY < viewportHeight) {
                    String originalText = nbtLines.get(lineIndex);
                    String wrappedText = wrapText(font, originalText, 200);

                    // 根据类型调整渲染位置
                    int renderY = lineY + 50;
                    if (type == ItemRenderType.INVENTORY) {
                        renderY += 8; // 库存视图特殊偏移
                    }

                    font.drawString(
                            wrappedText,
                            20,       // 保持右侧位置
                            renderY,  // 动态计算的Y位置
                            0x00FFFF   // 绿色文字
                    );
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
                currentLine.append(word).append(" ");
            } else {
                result.append(currentLine).append("\n");
                currentLine = new StringBuilder(word).append(" ");
            }
        }
        return result.append(currentLine).toString().trim();
    }
}