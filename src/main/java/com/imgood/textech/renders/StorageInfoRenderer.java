package com.imgood.textech.renders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.registry.GameData;

/**
 * Renders dataType = "storage" as item icons with their AE network counts.
 */
public class StorageInfoRenderer implements IADMRender {

    private final Minecraft mc = Minecraft.getMinecraft();

    @Override
    public void render(NBTTagCompound nbt, double x, double y, double z, int facing) {
        if (!nbt.getBoolean("enable")) return;

        NBTTagList items = nbt.getTagList("storageItems", 10);
        List<NBTTagCompound> visibleItems = collectVisibleItems(items, nbt);
        String displayName = nbt.getString("displayName");
        boolean hasTitle = displayName != null && !displayName.isEmpty();
        if (visibleItems.isEmpty() && !hasTitle) return;

        int columns = nbt.hasKey("storageColumns") ? Math.max(1, nbt.getInteger("storageColumns")) : 4;
        float spacing = nbt.hasKey("storageSpacing") ? nbt.getFloat("storageSpacing") : 0.45f;
        float iconScale = nbt.hasKey("storageIconScale") ? nbt.getFloat("storageIconScale") : 1.0f;
        float textScale = nbt.hasKey("textScale") ? nbt.getFloat("textScale") : 1.0f;
        float displayNameScale = nbt.hasKey("displayNameScale") ? nbt.getFloat("displayNameScale") : 1.0f;
        float scale = nbt.hasKey("scale") ? nbt.getFloat("scale") : 0.3f;
        float rotX = nbt.getFloat("rotationX");
        float rotY = nbt.getFloat("rotationY");
        float rotZ = nbt.getFloat("rotationZ");
        int textColor = parseHexColorOrDefault(nbt.getString("textColor"), 0xFFFFFF);
        int displayNameColor = parseHexColorOrDefault(nbt.getString("displayNameColor"), 0xFFFFFF);
        double textAlpha = nbt.hasKey("textAlpha") ? nbt.getDouble("textAlpha") : 1.0;
        double nameAlpha = nbt.hasKey("nameAlpha") ? nbt.getDouble("nameAlpha") : 1.0;
        int packedTextColor = ((int) (clamp(textAlpha) * 255) << 24) | (textColor & 0x00FFFFFF);
        int packedTitleColor = ((int) (clamp(nameAlpha) * 255) << 24) | (displayNameColor & 0x00FFFFFF);

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glRotatef(180, 0, 0, 1);
            GL11.glRotatef(rotX, 1, 0, 0);
            GL11.glRotatef(rotY, 0, 1, 0);
            GL11.glRotatef(rotZ, 0, 0, 1);
            GL11.glScalef(scale, scale, scale);

            int rows = visibleItems.isEmpty() ? 0 : (int) Math.ceil(visibleItems.size() / (double) columns);
            float titleHeight = hasTitle ? mc.fontRenderer.FONT_HEIGHT * displayNameScale * 0.02f * 1.5f : 0.0f;
            float totalWidth = (Math.min(columns, Math.max(1, visibleItems.size())) - 1) * spacing;
            float totalHeight = Math.max(0, rows - 1) * spacing + titleHeight;
            float currentTop = -totalHeight / 2.0f;

            if (hasTitle) {
                renderCenteredText(displayName, 0.0f, currentTop, packedTitleColor, displayNameScale);
                currentTop += titleHeight;
            }

            for (int i = 0; i < visibleItems.size(); i++) {
                NBTTagCompound entry = visibleItems.get(i);
                ItemStack stack = ItemStack.loadItemStackFromNBT(entry.getCompoundTag("item"));
                if (stack == null) continue;

                String entryType = entry.hasKey("type") ? entry.getString("type") : "item";

                int col = i % columns;
                int row = i / columns;
                float px = col * spacing - totalWidth / 2.0f;
                float py = currentTop + row * spacing;

                GL11.glPushMatrix();
                GL11.glTranslatef(px, py, 0.05f);

                if ("fluid".equals(entryType)) {
                    renderFluidIcon(stack, iconScale);
                } else if ("essentia".equals(entryType)) {
                    renderEssentiaIcon(stack, iconScale);
                } else {
                    renderItem(stack, iconScale);
                }

                renderStorageText(entry, nbt, packedTextColor, textScale, iconScale);
                GL11.glPopMatrix();
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private void renderItem(ItemStack stack, float iconScale) {
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

            // AE2 storage monitors flatten the normal GUI item renderer instead of drawing raw atlas quads.
            // Using the shared RenderItem keeps Forge custom inventory renderers on the same path as AE2.
            float aeItemScale = 0.8f * iconScale;
            GL11.glRotatef(180.0f, 1.0f, 0.0f, 0.0f);
            GL11.glScalef(1.0f, -1.0f, 1.0f);
            GL11.glScalef(aeItemScale / 32.0f, aeItemScale / 32.0f, 0.0001f);
            GL11.glTranslatef(-8.0f, -5.0f, 0.0f);
            RenderItem.getInstance()
                .renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, 0, 0);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    /**
     * Render a fluid entry in AE2 terminal style â€?a tinted square with the
     * fluid's still icon, similar to how AE2FluidCraft displays fluids in its
     * terminal grid.
     */
    private void renderFluidIcon(ItemStack stack, float iconScale) {
        FluidStack fluidStack = null;
        if (stack != null && stack.getTagCompound() != null) {
            fluidStack = FluidStack.loadFluidStackFromNBT(stack.getTagCompound());
        }
        if (fluidStack == null || fluidStack.getFluid() == null) {
            // fallback: render the bucket item
            renderItem(stack, iconScale);
            return;
        }

        Fluid fluid = fluidStack.getFluid();
        IIcon icon = fluid.getStillIcon();
        if (icon == null) {
            icon = fluid.getIcon();
        }
        if (icon == null) {
            renderItem(stack, iconScale);
            return;
        }

        int color = fluid.getColor(fluidStack);
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        float size = 0.6f * iconScale;
        float halfSize = size / 2.0f;
        float uMin = icon.getMinU();
        float uMax = icon.getMaxU();
        float vMin = icon.getMinV();
        float vMax = icon.getMaxV();

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);

            mc.getTextureManager()
                .bindTexture(TextureMap.locationBlocksTexture);
            GL11.glColor4f(r, g, b, 1.0f);

            Tessellator tess = Tessellator.instance;
            tess.startDrawingQuads();
            tess.addVertexWithUV(-halfSize, -halfSize, 0.0, uMin, vMax);
            tess.addVertexWithUV(halfSize, -halfSize, 0.0, uMax, vMax);
            tess.addVertexWithUV(halfSize, halfSize, 0.0, uMax, vMin);
            tess.addVertexWithUV(-halfSize, halfSize, 0.0, uMin, vMin);
            tess.draw();

            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    /**
     * Render a Thaumcraft essentia entry as an aspect icon.
     * Uses thaumcraft.api.aspects.Aspect to fetch the aspect image.
     * If Thaumcraft is not present, falls back to a purple-tinted square.
     */
    @SuppressWarnings("unchecked")
    private void renderEssentiaIcon(ItemStack stack, float iconScale) {
        String aspectTag = null;
        if (stack != null && stack.getTagCompound() != null) {
            aspectTag = stack.getTagCompound()
                .getString("aspectTag");
        }

        ResourceLocation aspectImage = null;
        if (aspectTag != null && !aspectTag.isEmpty()) {
            try {
                Class<?> aspectClass = Class.forName("thaumcraft.api.aspects.Aspect");
                Object aspect = aspectClass.getMethod("getAspect", String.class)
                    .invoke(null, aspectTag);
                if (aspect != null) {
                    aspectImage = (ResourceLocation) aspectClass.getMethod("getImage")
                        .invoke(aspect);
                }
            } catch (Throwable ignored) {
                // Thaumcraft not available, fall through to colored square
            }
        }

        float size = 0.55f * iconScale;
        float halfSize = size / 2.0f;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);

            if (aspectImage != null) {
                mc.getTextureManager()
                    .bindTexture(aspectImage);
                GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            } else {
                // Fallback: purple square for unknown essentia
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glColor4f(0.6f, 0.2f, 0.8f, 1.0f);
            }

            Tessellator tess = Tessellator.instance;
            tess.startDrawingQuads();
            tess.addVertexWithUV(-halfSize, -halfSize, 0.0, 0.0, 1.0);
            tess.addVertexWithUV(halfSize, -halfSize, 0.0, 1.0, 1.0);
            tess.addVertexWithUV(halfSize, halfSize, 0.0, 1.0, 0.0);
            tess.addVertexWithUV(-halfSize, halfSize, 0.0, 0.0, 0.0);
            tess.draw();

            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private void renderCenteredText(String text, float x, float y, int color, float textScale) {
        if (text == null || text.isEmpty()) return;
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0.1f);
        GL11.glScalef(textScale * 0.02f, textScale * 0.02f, textScale * 0.02f);
        int width = mc.fontRenderer.getStringWidth(text);
        mc.fontRenderer.drawString(text, -width / 2, 0, color, false);
        GL11.glPopMatrix();
    }

    private void renderStorageText(NBTTagCompound entry, NBTTagCompound nbt, int packedTextColor, float textScale,
        float iconScale) {
        boolean showItemCount = !nbt.hasKey("showItemCount") || nbt.getBoolean("showItemCount");
        boolean showItemDelta = nbt.hasKey("showItemDelta") && nbt.getBoolean("showItemDelta");
        boolean showItemName = !nbt.hasKey("showItemName") || nbt.getBoolean("showItemName");
        int countOrder = nbt.hasKey("itemCountOrder") ? nbt.getInteger("itemCountOrder") : 0;
        int deltaOrder = nbt.hasKey("itemDeltaOrder") ? nbt.getInteger("itemDeltaOrder") : 1;
        int nameOrder = nbt.hasKey("itemNameOrder") ? nbt.getInteger("itemNameOrder") : 2;

        java.util.List<LineEntry> lines = new java.util.ArrayList<>();
        if (showItemCount) lines.add(new LineEntry(countOrder, formatCount(entry.getLong("count"))));
        if (showItemDelta) lines.add(new LineEntry(deltaOrder, formatSignedCount(entry.getLong("countDelta"))));
        if (showItemName) lines.add(new LineEntry(nameOrder, entry.getString("displayName")));
        java.util.Collections.sort(lines, new java.util.Comparator<LineEntry>() {

            @Override
            public int compare(LineEntry left, LineEntry right) {
                return Integer.compare(left.order, right.order);
            }
        });

        float baseY = 0.30f * iconScale;
        float lineSpacing = nbt.hasKey("storageLineSpacing") ? nbt.getFloat("storageLineSpacing") : 0.22f;
        float stepY = lineSpacing * textScale;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).text;
            renderCenteredText(line, 0.0f, baseY + (i * stepY), getStorageTextColor(line, packedTextColor), textScale);
        }
    }

    private int getStorageTextColor(String text, int fallbackColor) {
        if (text == null || text.isEmpty() || "0".equals(text)) return fallbackColor;
        int alpha = fallbackColor & 0xFF000000;
        if (text.charAt(0) == '+') return alpha | 0x00FF00;
        if (text.charAt(0) == '-') return alpha | 0xFF0000;
        return fallbackColor;
    }

    private static class LineEntry {

        private final int order;
        private final String text;

        private LineEntry(int order, String text) {
            this.order = order;
            this.text = text;
        }
    }

    private List<NBTTagCompound> collectVisibleItems(NBTTagList items, NBTTagCompound nbt) {
        List<NBTTagCompound> visibleItems = new ArrayList<>();
        String indexText = nbt.getString("storageCellIndex");
        boolean hasIndexFilter = indexText != null && !indexText.trim()
            .isEmpty();
        int indexFilter = -1;
        if (hasIndexFilter) {
            try {
                indexFilter = Integer.parseInt(indexText.trim());
            } catch (NumberFormatException ignored) {
                indexFilter = -1;
            }
        }

        boolean showItems = !nbt.hasKey("showItems") || nbt.getBoolean("showItems");
        boolean showFluids = !nbt.hasKey("showFluids") || nbt.getBoolean("showFluids");
        boolean showEssentia = !nbt.hasKey("showEssentia") || nbt.getBoolean("showEssentia");

        for (int i = 0; i < items.tagCount(); i++) {
            NBTTagCompound entry = items.getCompoundTagAt(i);
            if (hasIndexFilter && entry.getInteger("slot") != indexFilter) continue;

            String entryType = entry.hasKey("type") ? entry.getString("type") : "item";
            if ("fluid".equals(entryType) && !showFluids) continue;
            if ("essentia".equals(entryType) && !showEssentia) continue;
            if ((!"fluid".equals(entryType) && !"essentia".equals(entryType)) && !showItems) continue;

            visibleItems.add(entry);
        }
        sortVisibleItems(visibleItems, nbt.hasKey("storageSortMode") ? nbt.getInteger("storageSortMode") : 0);
        return visibleItems;
    }

    private void sortVisibleItems(List<NBTTagCompound> visibleItems, int sortMode) {
        if (sortMode == 1) {
            Collections.sort(visibleItems, new Comparator<NBTTagCompound>() {

                @Override
                public int compare(NBTTagCompound left, NBTTagCompound right) {
                    return Long.compare(right.getLong("count"), left.getLong("count"));
                }
            });
        } else if (sortMode == 2) {
            Collections.sort(visibleItems, new Comparator<NBTTagCompound>() {

                @Override
                public int compare(NBTTagCompound left, NBTTagCompound right) {
                    ItemStack leftStack = ItemStack.loadItemStackFromNBT(left.getCompoundTag("item"));
                    ItemStack rightStack = ItemStack.loadItemStackFromNBT(right.getCompoundTag("item"));
                    String leftMod = getModId(leftStack);
                    String rightMod = getModId(rightStack);
                    int modCompare = leftMod.compareToIgnoreCase(rightMod);
                    if (modCompare != 0) return modCompare;
                    String leftName = leftStack == null ? "" : leftStack.getDisplayName();
                    String rightName = rightStack == null ? "" : rightStack.getDisplayName();
                    return leftName.compareToIgnoreCase(rightName);
                }
            });
        }
    }

    private String getModId(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return "";
        try {
            String itemName = GameData.getItemRegistry()
                .getNameForObject(stack.getItem());
            int separator = itemName == null ? -1 : itemName.indexOf(':');
            return separator > 0 ? itemName.substring(0, separator) : itemName == null ? "" : itemName;
        } catch (Throwable ignored) {
            String unlocalizedName = stack.getItem()
                .getUnlocalizedName(stack);
            int separator = unlocalizedName == null ? -1 : unlocalizedName.indexOf('.');
            return separator > 0 ? unlocalizedName.substring(0, separator) : "";
        }
    }

    private String formatSignedCount(long count) {
        if (count > 0) return "+" + formatCount(count);
        if (count < 0) return "-" + formatCount(-count);
        return "0";
    }

    private String formatCount(long count) {
        if (count < 1000) return String.valueOf(count);
        String[] units = { "k", "m", "b", "t", "p" };
        double value = count;
        int unitIndex = -1;
        while (value >= 1000.0 && unitIndex < units.length - 1) {
            value /= 1000.0;
            unitIndex++;
        }
        return value >= 100.0 ? String.format("%.0f%s", value, units[unitIndex])
            : String.format("%.1f%s", value, units[unitIndex]);
    }

    @Override
    public void cleanup() {}

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private int parseHexColorOrDefault(String hexString, int defaultColor) {
        if (hexString == null || hexString.isEmpty()) return defaultColor;
        if (hexString.startsWith("#")) hexString = hexString.substring(1);
        try {
            return (int) Long.parseLong(hexString, 16);
        } catch (NumberFormatException e) {
            return defaultColor;
        }
    }
}
