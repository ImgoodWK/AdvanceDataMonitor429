package com.imgood.advancedatamonitor.renders;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.lwjgl.opengl.GL11;

/**
 * 渲染 dataType = "crafting" 的多行文本信息。
 * textAlign 为整数：0=左对齐, 1=居中, 2=右对齐
 *
 * 修改说明：
 * - 新增对 monitorNetworkWide 的支持：
 * true -> 使用 "networkLines" 列表（全网络统计数据，由服务端通过 getStatsInfo() 生成）
 * false -> 使用 "lines" 列表（原有逻辑，可能为模板或单个 CPU 信息）
 * - 若 monitorNetworkWide 为 true 但 "networkLines" 不存在，则回退到 "lines"。
 */
public class CraftingInfoRenderer implements IADMRender {

    private final Minecraft mc = Minecraft.getMinecraft();

    @Override
    public void render(NBTTagCompound nbt, double x, double y, double z, int facing) {
        if (!nbt.getBoolean("enable")) return;

        // 根据 monitorNetworkWide 选择数据源
        boolean monitorNetworkWide = nbt.getBoolean("monitorNetworkWide");
        NBTTagList linesTag;
        if (monitorNetworkWide && nbt.hasKey("networkLines")) {
            linesTag = nbt.getTagList("networkLines", 8);
        } else {
            linesTag = nbt.getTagList("lines", 8);
        }

        String displayName = nbt.getString("displayName");
        boolean hasTitle = displayName != null && !displayName.isEmpty();

        // 如果没有任何内容可以渲染则退出
        if (linesTag.tagCount() == 0 && !hasTitle) return;

        // 读取所有渲染属性
        int color = parseHexColorOrDefault(nbt.getString("textColor"), 0xFFFFFF);
        double textAlpha = nbt.getFloat("textAlpha");
        float textScale = nbt.hasKey("textScale") ? nbt.getFloat("textScale") : 1.0f;
        int textAlign = nbt.hasKey("textAlign") ? nbt.getInteger("textAlign") : 1; // 默认居中
        if (textAlign < 0 || textAlign > 2) textAlign = 1;

        // 标题相关属性
        float displayNameScale = nbt.hasKey("displayNameScale") ? nbt.getFloat("displayNameScale") : 1.0f;
        int displayNameColor = parseHexColorOrDefault(nbt.getString("displayNameColor"), 0xFFFFFF);
        double nameAlpha = nbt.getFloat("nameAlpha");

        float scale = nbt.hasKey("scale") ? nbt.getFloat("scale") : 0.3f;
        float rotX = nbt.getFloat("rotationX");
        float rotY = nbt.getFloat("rotationY");
        float rotZ = nbt.getFloat("rotationZ");

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();

        try {
            // 基础渲染状态
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_TEXTURE_2D);

            GL11.glRotatef(180, 0, 0, 1);
            GL11.glRotatef(rotX, 1, 0, 0);
            GL11.glRotatef(rotY, 0, 1, 0);
            GL11.glRotatef(rotZ, 0, 0, 1);
            GL11.glScalef(scale, scale, 1.0f);

            int lineColor = ((int) (textAlpha * 255) << 24) | (color & 0x00FFFFFF);
            int titleColor = ((int) (nameAlpha * 255) << 24) | (displayNameColor & 0x00FFFFFF);

            int lineCount = linesTag.tagCount();
            float fontHeightLine = mc.fontRenderer.FONT_HEIGHT * textScale * 0.02f;
            float lineSpacing = fontHeightLine * 1.2f;
            float titleSpacing = mc.fontRenderer.FONT_HEIGHT * displayNameScale * 0.02f * 1.2f;
            float totalHeight;
            if (hasTitle) {
                totalHeight = titleSpacing + lineCount * lineSpacing;
            } else {
                totalHeight = lineCount * lineSpacing;
            }

            float topY = -totalHeight / 2.0f;
            float currentY = topY;

            if (hasTitle) {
                GL11.glPushMatrix();
                GL11.glTranslatef(0, currentY, 0.1f);

                int titleWidth = mc.fontRenderer.getStringWidth(displayName);
                double scaledTitleWidth = titleWidth * displayNameScale * 0.02f;
                if (textAlign == 1) {
                    GL11.glTranslatef((float) (-scaledTitleWidth / 2.0), 0, 0);
                } else if (textAlign == 2) {
                    GL11.glTranslatef((float) (-scaledTitleWidth), 0, 0);
                }

                GL11.glScalef(displayNameScale * 0.02f, displayNameScale * 0.02f, displayNameScale * 0.02f);
                mc.fontRenderer.drawString(displayName, 0, 0, titleColor, false);
                GL11.glPopMatrix();
                currentY += titleSpacing;
            }

            for (int i = 0; i < lineCount; i++) {
                String line = linesTag.getStringTagAt(i);
                if (line == null || line.isEmpty()) continue;

                GL11.glPushMatrix();
                GL11.glTranslatef(0, currentY, 0.05f);

                int textWidth = mc.fontRenderer.getStringWidth(line);
                double scaledWidth = textWidth * textScale * 0.02f;
                if (textAlign == 1) {
                    GL11.glTranslatef((float) (-scaledWidth / 2.0), 0, 0);
                } else if (textAlign == 2) {
                    GL11.glTranslatef((float) (-scaledWidth), 0, 0);
                }

                GL11.glScalef(textScale * 0.02f, textScale * 0.02f, textScale * 0.02f);
                mc.fontRenderer.drawString(line, 0, 0, lineColor, false);
                GL11.glPopMatrix();
                currentY += lineSpacing;
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    @Override
    public void cleanup() {}

    private int parseHexColorOrDefault(String hexString, int defaultColor) {
        if (hexString == null || hexString.isEmpty()) return defaultColor;
        if (hexString.startsWith("#")) {
            hexString = hexString.substring(1);
        }
        try {
            return (int) Long.parseLong(hexString, 16);
        } catch (NumberFormatException e) {
            return defaultColor;
        }
    }
}
