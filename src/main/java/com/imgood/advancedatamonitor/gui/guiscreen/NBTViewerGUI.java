package com.imgood.advancedatamonitor.gui.guiscreen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiButton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NBTViewerGUI extends GuiScreen {
    private JsonObject nbtData;
    private List<TreeEntry> entries = new ArrayList<>();
    private int scrollY;

    public NBTViewerGUI(JsonObject data) {
        this.nbtData = data;
        System.out.println("TestGUI"+data);
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
                if (valueObj.has("type") && valueObj.get("type").getAsString().equals("LIST")) {
                    // 如果是LIST类型，处理其数组内容
                    if (valueObj.has("value") && valueObj.get("value").isJsonArray()) {
                        JsonArray listItems = valueObj.get("value").getAsJsonArray();
                        int index = 0;
                        for (JsonElement item : listItems) {
                            JsonObject itemObj = new JsonObject();
                            itemObj.addProperty("type", "LIST_ITEM");
                            itemObj.add("value", item);
                            buildTree(treeEntry, itemObj, depth + 1);
                        }
                    }
                } else {
                    // 普通对象继续递归
                    buildTree(treeEntry, valueObj, depth + 1);
                }
            }
        }
    }

    @Override
    protected void mouseClicked(int x, int y, int btn) {
        int yPos = 20 - scrollY;
        for (TreeEntry entry : entries) {
            if (entry.isVisible()) {
                if (y >= yPos && y < yPos + 10) {
                    if (entry.hasChildren()) {
                        entry.expanded = !entry.expanded;
                    }
                    break;
                }
                yPos += 10;
            }
        }
        super.mouseClicked(x, y, btn);
    }

    @Override
    public void initGui() {
        this.buttonList.add(new GuiButton(0, width - 60, height - 30, 50, 20, "Close"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            mc.displayGuiScreen(null);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int yPos = 20 - scrollY;

        for (TreeEntry entry : entries) {
            if (entry.isVisible()) {
                String displayText = getIndent(entry.depth);
                if (entry.hasChildren()) {
                    displayText += (entry.expanded ? "▼ " : "▶ ");
                }
                displayText += entry.getDisplayText();
                drawString(fontRendererObj, displayText, 20, yPos, 0xFFFFFF);
                yPos += 10;
            }
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
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
                    String type = obj.get("type").getAsString();
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
        return false; // 返回 false 以使游戏不暂停
    }
}