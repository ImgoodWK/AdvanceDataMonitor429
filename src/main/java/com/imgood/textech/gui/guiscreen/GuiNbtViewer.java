package com.imgood.textech.gui.guiscreen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Mouse;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.imgood.textech.gui.custom.ADM_GuiButton;
import com.imgood.textech.gui.custom.ADM_GuiScreen;
import com.imgood.textech.gui.custom.AdmGuiTextures;
import com.imgood.textech.gui.framework.UiPanel;
import com.imgood.textech.gui.framework.UiThemes;

public class GuiNbtViewer extends ADM_GuiScreen {

    private static final int PANEL_WIDTH = 440;
    private static final int PANEL_HEIGHT = 320;
    private JsonObject nbtData;
    private List<TreeEntry> entries = new ArrayList<>();
    private int scrollY;

    public GuiNbtViewer(JsonObject data) {
        this.nbtData = data;
        setBackgroundTexture(AdmGuiTextures.BACKGROUND_SUB);
        setSize(PANEL_WIDTH, PANEL_HEIGHT);
        setStretch(false);
        buildTree(null, nbtData, 0);
    }

    private void buildTree(TreeEntry parent, JsonObject obj, int depth) {
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();

            TreeEntry treeEntry = new TreeEntry(key, value, depth, parent);
            if (parent != null) {
                parent.addChild(treeEntry);
            }
            entries.add(treeEntry);

            if (value.isJsonObject()) {
                JsonObject valueObj = value.getAsJsonObject();
                if (valueObj.has("type") && valueObj.get("type")
                    .getAsString()
                    .equals("LIST")) {
                    // LIST 类型：展开子项
                    if (valueObj.has("value") && valueObj.get("value")
                        .isJsonArray()) {
                        JsonArray listItems = valueObj.get("value")
                            .getAsJsonArray();
                        int index = 0;
                        for (JsonElement item : listItems) {
                            JsonObject itemObj = new JsonObject();
                            itemObj.addProperty("type", "LIST_ITEM");
                            itemObj.add("value", item);
                            buildTree(treeEntry, itemObj, depth + 1);
                        }
                    }
                } else {
                    // 普通对象：递归子节点
                    buildTree(treeEntry, valueObj, depth + 1);
                }
            }
        }
    }

    @Override
    protected void handleAdmMouseClicked(int x, int y, int btn) {
        int yPos = panelY() + 42 - scrollY;
        for (TreeEntry entry : entries) {
            if (entry.isVisible()) {
                if (x >= panelX() + 14 && x < panelX() + panelWidth() - 14 && y >= yPos && y < yPos + 10) {
                    if (entry.hasChildren()) {
                        entry.expanded = !entry.expanded;
                    }
                    break;
                }
                yPos += 10;
            }
        }
        super.handleAdmMouseClicked(x, y, btn);
    }

    @Override
    public void initGui() {
        super.initGui();
        setPosition((width - PANEL_WIDTH) / 2, (height - PANEL_HEIGHT) / 2);
        this.buttonList.clear();
        this.buttonList.add(
            new ADM_GuiButton(
                0,
                panelX() + panelWidth() - 72,
                panelY() + panelHeight() - 28,
                56,
                20,
                I18n.format("adm.button.close")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            mc.displayGuiScreen(null);
        }
    }

    @Override
    protected void drawAdmScreen(int mouseX, int mouseY, float partialTicks) {
        int contentX = panelX() + 14;
        int contentY = panelY() + 32;
        int contentWidth = panelWidth() - 28;
        int contentHeight = panelHeight() - 72;
        UiPanel.drawSection(UiThemes.ADM, contentX - 3, contentY - 3, contentWidth + 6, contentHeight + 6);
        drawCenteredString(
            fontRendererObj,
            I18n.format("adm.title.nbt_viewer"),
            panelX() + panelWidth() / 2,
            panelY() + 10,
            0xD7F7FF);
        int yPos = contentY + 10 - scrollY;
        TreeEntry hoveredEntry = null;
        boolean hoveredTextWasTrimmed = false;

        for (TreeEntry entry : entries) {
            if (entry.isVisible()) {
                String displayText = getIndent(entry.depth);
                if (entry.hasChildren()) {
                    displayText += (entry.expanded ? "[-] " : "[+] ");
                }
                displayText += entry.getDisplayText();
                if (yPos >= contentY && yPos < contentY + contentHeight) {
                    String renderedText = fontRendererObj.trimStringToWidth(displayText, contentWidth - 8);
                    drawString(fontRendererObj, renderedText, contentX, yPos, 0xD7F7FF);
                    if (mouseX >= contentX && mouseX < contentX + contentWidth - 4
                        && mouseY >= yPos
                        && mouseY < yPos + 10) {
                        hoveredEntry = entry;
                        hoveredTextWasTrimmed = !renderedText.equals(displayText);
                    }
                }
                yPos += 10;
            }
        }

        super.drawAdmScreen(mouseX, mouseY, partialTicks);
        if (hoveredEntry != null && (hoveredEntry.hasChildren() || hoveredTextWasTrimmed)) {
            String action = hoveredEntry.hasChildren()
                ? I18n.format(hoveredEntry.expanded ? "adm.nbt.tooltip.collapse" : "adm.nbt.tooltip.expand")
                : I18n.format("adm.nbt.tooltip.truncated");
            drawAdmTooltip(
                mouseX,
                mouseY,
                Math.min(340, panelWidth() - 40),
                action,
                I18n.format("adm.nbt.tooltip.full_value", hoveredEntry.getDisplayText()));
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int visibleEntries = 0;
        for (TreeEntry entry : entries) {
            if (entry.isVisible()) {
                visibleEntries++;
            }
        }
        int maxScroll = Math.max(0, visibleEntries * 10 - Math.max(10, PANEL_HEIGHT - 72));
        scrollY = Math.max(0, Math.min(maxScroll, scrollY - Integer.signum(wheel) * 30));
    }

    private String getIndent(int depth) {
        return new String(new char[depth]).replace("\0", "  ");
    }

    private class TreeEntry {

        String key;
        JsonElement data;
        int depth;
        boolean expanded = false;
        TreeEntry parent;
        List<TreeEntry> children = new ArrayList<>();

        public TreeEntry(String key, JsonElement data, int depth, TreeEntry parent) {
            this.key = key;
            this.data = data;
            this.depth = depth;
            this.parent = parent;
        }

        public String getDisplayText() {
            if (data.isJsonObject()) {
                JsonObject obj = data.getAsJsonObject();
                if (obj.has("type")) {
                    String type = obj.get("type")
                        .getAsString();
                    if (type.equals("LIST")) {
                        return key + ": LIST";
                    } else if (type.equals("LIST_ITEM")) {
                        return "[" + key + "]: " + getValueDisplay(obj.get("value"));
                    } else if (obj.has("value")) {
                        return key + ": " + type + " = " + getValueDisplay(obj.get("value"));
                    }
                }
                return key + ": OBJECT";
            }
            return key + ": " + data.toString();
        }

        private String getValueDisplay(JsonElement value) {
            if (value.isJsonPrimitive()) {
                return value.getAsString();
            } else if (value.isJsonObject()) {
                return "{...}";
            } else if (value.isJsonArray()) {
                return "[...]";
            }
            return value.toString();
        }

        public boolean isVisible() {
            TreeEntry current = this.parent;
            while (current != null) {
                if (!current.expanded) return false;
                current = current.parent;
            }
            return true;
        }

        public void addChild(TreeEntry child) {
            children.add(child);
        }

        public boolean hasChildren() {
            return !children.isEmpty();
        }

        public TreeEntry getParent() {
            return parent;
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
