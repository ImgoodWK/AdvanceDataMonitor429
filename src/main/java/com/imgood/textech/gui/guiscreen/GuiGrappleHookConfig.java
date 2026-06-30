package com.imgood.textech.gui.guiscreen;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.client.GrappleClientRouteCache;
import com.imgood.textech.gui.custom.ADM_GuiButton;
import com.imgood.textech.gui.custom.ADM_GuiScreen;
import com.imgood.textech.gui.custom.ADM_GuiTextField;
import com.imgood.textech.items.GrappleHookMode;
import com.imgood.textech.items.GrappleRouteEntry;
import com.imgood.textech.items.ItemGrappleHook;
import com.imgood.textech.network.packet.PacketGrappleHookConfig;
import com.imgood.textech.network.packet.PacketGrapplePathAction;

/**
 * Display names / 显示名称:
 * - EN: Grapple Hook Settings
 * - ZH: 挂索器设�?
 * Lang keys: adm.title.grappleHookConfig
 */
public class GuiGrappleHookConfig extends ADM_GuiScreen {

    private static final ResourceLocation BACKGROUND_TEXTURE = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/background_ADM_Sub.png");
    private static final ResourceLocation BUTTON_TEXTURE = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/button_ADM.png");
    private static final ResourceLocation BUTTON_HOVER_TEXTURE = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/button_hover_ADM.png");
    private static final ResourceLocation TEXTFIELD_TEXTURE = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/textfield_ADM_8020.png");
    private static final ResourceLocation TEXTFIELD_HOVER_TEXTURE = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/textfield_hover_ADM_8020.png");

    private static final int PANEL_WIDTH = 520;
    private static final int PANEL_HEIGHT = 400;

    // 卡片背景/边框�?
    private static final int CARD_BG = 0x30004040;
    private static final int CARD_BORDER = 0x8000FFFF;
    private static final int SELECTED_ROW_BG = 0x4000AAFF;
    private static final int INLINE_BTN_BG = 0x50000000;
    private static final int INLINE_BTN_HOVER_BG = 0x7000AAAA;

    // 文本�?
    private static final int TEXT_LABEL = 0x00FFFF;
    private static final int TEXT_SECONDARY = 0xAAAAAA;
    private static final int TEXT_DIM = 0x666666;
    private static final int TEXT_ERROR = 0xFF5555;
    private static final int TEXT_OK = 0x55FF55;

    // 按钮�?
    private static final int BTN_TEXT = 0x00FFFF;
    private static final int BTN_HOVER = 0x0055FF;
    private static final int SAVE_TEXT = 0x00FF00;
    private static final int SAVE_TEXT_HOVER = 0x55FF55;
    private static final int RESET_TEXT = 0xFFAA00;
    private static final int RESET_TEXT_HOVER = 0xFFCC55;
    private static final int DELETE_TEXT = 0xFF5555;
    private static final int DELETE_TEXT_HOVER = 0xFF0000;

    // 卡片矩形
    private static final int CARD1_X = 10, CARD1_Y = 24, CARD1_W = 250, CARD1_H = 140;
    private static final int CARD2_X = 270, CARD2_Y = 24, CARD2_W = 240, CARD2_H = 140;
    private static final int CARD3_X = 10, CARD3_Y = 174, CARD3_W = 500, CARD3_H = 160;

    // 列表
    private static final int LIST_ROW_HEIGHT = 20;
    private static final int LIST_VISIBLE_ROWS = 6;
    private static final int LIST_INNER_HEIGHT = LIST_ROW_HEIGHT * LIST_VISIBLE_ROWS;
    private static final int LIST_LEFT_PAD = 6;
    private static final int INLINE_BTN_W = 44;
    private static final int INLINE_BTN_GAP = 4;

    // 按钮 ID
    private static final int BTN_MODE_QUEUE = 20;
    private static final int BTN_MODE_PLANNING = 21;
    private static final int BTN_MODE_PATH = 22;
    private static final int BTN_SAVE = 23;
    private static final int BTN_RESET = 24;
    private static final int BTN_SHOW_NAME = 25;
    private static final int BTN_SHOW_DIST = 26;
    private static final int BTN_SAVE_CFG = 27;
    private static final int BTN_CLOSE = 28;
    private static final int BTN_RENAME_CONFIRM = 29;
    private static final int BTN_RENAME_CANCEL = 30;

    private final ItemStack hookStack;
    private final EntityPlayer player;

    private ADM_GuiTextField speedField;
    private ADM_GuiTextField routeNameField;
    private ADM_GuiTextField renameField;

    private boolean showNodeName;
    private boolean showNodeDistance;
    private GrappleHookMode selectedMode;
    private String errorTips = "";

    private int scrollOffset = 0;
    private int selectedRouteIndex = -1;
    private boolean renamingActive = false;

    private int offsetX;
    private int offsetY;

    // 行内按钮命中区（绝对坐标，每帧由 drawRouteList 计算后存档供 mouseClicked 使用�?
    private static final int INLINE_BTN_H = 16;
    private int inlineBtnRowY = -1;
    private int inlinePreviewX = -1;
    private int inlineRenameX = -1;
    private int inlineDeleteX = -1;

    public GuiGrappleHookConfig(ItemStack hookStack, EntityPlayer player) {
        this.hookStack = hookStack;
        this.player = player;
        this.showNodeName = ItemGrappleHook.getShowNodeName(hookStack);
        this.showNodeDistance = ItemGrappleHook.getShowNodeDistance(hookStack);
        this.selectedMode = ItemGrappleHook.getHookMode(hookStack);
        this.setBackgroundTexture(BACKGROUND_TEXTURE);
        this.setSize(PANEL_WIDTH, PANEL_HEIGHT);
        this.setStretch(false);
        AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketGrapplePathAction.requestSync());
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.offsetX = (this.width - PANEL_WIDTH) / 2;
        this.offsetY = (this.height - PANEL_HEIGHT) / 2;
        this.setPosition(this.offsetX, this.offsetY);
        this.buttonList.clear();

        // ===== 卡片1 模式与速度 =====
        int c1x = offsetX + CARD1_X;
        int c1y = offsetY + CARD1_Y;
        addModeButton(BTN_MODE_QUEUE, c1x + 6, c1y + 30, GrappleHookMode.QUEUE);
        addModeButton(BTN_MODE_PLANNING, c1x + 88, c1y + 30, GrappleHookMode.PLANNING);
        addModeButton(BTN_MODE_PATH, c1x + 170, c1y + 30, GrappleHookMode.PATH);

        speedField = new ADM_GuiTextField(this.fontRendererObj, c1x + 130, c1y + 68, 60, 18);
        speedField.setMaxStringLength(6);
        speedField.setBackgroundTexture(TEXTFIELD_TEXTURE);
        speedField.setFocusedBackgroundTexture(TEXTFIELD_HOVER_TEXTURE);
        speedField.setText(String.format("%.1f", ItemGrappleHook.getTravelSpeed(hookStack)));

        this.buttonList.add(
            new ADM_GuiButton(
                BTN_SHOW_NAME,
                c1x + 6,
                c1y + 92,
                110,
                18,
                I18n.format(showNodeName ? "adm.button.disable" : "adm.button.enable") + " "
                    + I18n.format("adm.label.grapple.show_node_name_short")).setTexture(BUTTON_TEXTURE)
                        .setHoverTexture(BUTTON_HOVER_TEXTURE)
                        .setUseHoverEffect(true)
                        .setTextColor(BTN_TEXT)
                        .setTextHoverColor(BTN_HOVER));
        this.buttonList.add(
            new ADM_GuiButton(
                BTN_SHOW_DIST,
                c1x + 124,
                c1y + 92,
                110,
                18,
                I18n.format(showNodeDistance ? "adm.button.disable" : "adm.button.enable") + " "
                    + I18n.format("adm.label.grapple.show_node_distance_short")).setTexture(BUTTON_TEXTURE)
                        .setHoverTexture(BUTTON_HOVER_TEXTURE)
                        .setUseHoverEffect(true)
                        .setTextColor(BTN_TEXT)
                        .setTextHoverColor(BTN_HOVER));

        // ===== 卡片2 录制 =====
        int c2x = offsetX + CARD2_X;
        int c2y = offsetY + CARD2_Y;
        routeNameField = new ADM_GuiTextField(this.fontRendererObj, c2x + 90, c2y + 54, 130, 18);
        routeNameField.setMaxStringLength(32);
        routeNameField.setBackgroundTexture(TEXTFIELD_TEXTURE);
        routeNameField.setFocusedBackgroundTexture(TEXTFIELD_HOVER_TEXTURE);
        routeNameField.setText(I18n.format("adm.grapple.default_route_name"));

        this.buttonList.add(
            new ADM_GuiButton(BTN_SAVE, c2x + 30, c2y + 84, 80, 20, I18n.format("adm.button.save"))
                .setTexture(BUTTON_TEXTURE)
                .setHoverTexture(BUTTON_HOVER_TEXTURE)
                .setUseHoverEffect(true)
                .setTextColor(SAVE_TEXT)
                .setTextHoverColor(SAVE_TEXT_HOVER));
        this.buttonList.add(
            new ADM_GuiButton(BTN_RESET, c2x + 130, c2y + 84, 80, 20, I18n.format("adm.grapple.reset_buffer"))
                .setTexture(BUTTON_TEXTURE)
                .setHoverTexture(BUTTON_HOVER_TEXTURE)
                .setUseHoverEffect(true)
                .setTextColor(RESET_TEXT)
                .setTextHoverColor(RESET_TEXT_HOVER));

        // ===== 底部操作 =====
        int bottomY = offsetY + PANEL_HEIGHT - 32;
        this.buttonList.add(
            new ADM_GuiButton(
                BTN_SAVE_CFG,
                offsetX + PANEL_WIDTH - 140,
                bottomY,
                60,
                20,
                I18n.format("adm.button.save")).setTexture(BUTTON_TEXTURE)
                    .setHoverTexture(BUTTON_HOVER_TEXTURE)
                    .setUseHoverEffect(true)
                    .setTextColor(SAVE_TEXT)
                    .setTextHoverColor(SAVE_TEXT_HOVER));
        this.buttonList.add(
            new ADM_GuiButton(BTN_CLOSE, offsetX + PANEL_WIDTH - 70, bottomY, 60, 20, I18n.format("adm.button.cancel"))
                .setTexture(BUTTON_TEXTURE)
                .setHoverTexture(BUTTON_HOVER_TEXTURE)
                .setUseHoverEffect(true)
                .setTextColor(DELETE_TEXT)
                .setTextHoverColor(DELETE_TEXT_HOVER));

        // 重命名输入框初始位置（renamingActive 时动态更�?xPosition/yPosition�?
        renameField = new ADM_GuiTextField(this.fontRendererObj, offsetX + CARD3_X + 6, 0, 290, 18);
        renameField.setMaxStringLength(32);
        renameField.setBackgroundTexture(TEXTFIELD_TEXTURE);
        renameField.setFocusedBackgroundTexture(TEXTFIELD_HOVER_TEXTURE);
        updateRenameFieldPosition();
        updateRenameButtons();
    }

    private void addModeButton(int id, int x, int y, GrappleHookMode mode) {
        boolean active = selectedMode == mode;
        this.buttonList.add(
            new ADM_GuiButton(
                id,
                x,
                y,
                76,
                20,
                (active ? "§a" : "§7") + I18n.format(
                    "adm.grapple.mode." + mode.name()
                        .toLowerCase())).setTexture(BUTTON_TEXTURE)
                            .setHoverTexture(BUTTON_HOVER_TEXTURE)
                            .setUseHoverEffect(true)
                            .setTextColor(active ? 0x55FF55 : 0xAAAAAA)
                            .setTextHoverColor(BTN_HOVER));
    }

    private List<GrappleRouteEntry> getVisibleRoutes() {
        List<GrappleRouteEntry> all = GrappleClientRouteCache.getRoutes();
        List<GrappleRouteEntry> filtered = new ArrayList<GrappleRouteEntry>();
        int dim = player.worldObj == null ? 0 : player.worldObj.provider.dimensionId;
        for (GrappleRouteEntry route : all) {
            if (route.dimension == dim) {
                filtered.add(route);
            }
        }
        return filtered;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BTN_MODE_QUEUE) {
            applyMode(GrappleHookMode.QUEUE);
        } else if (button.id == BTN_MODE_PLANNING) {
            applyMode(GrappleHookMode.PLANNING);
        } else if (button.id == BTN_MODE_PATH) {
            applyMode(GrappleHookMode.PATH);
        } else if (button.id == BTN_SHOW_NAME) {
            showNodeName = !showNodeName;
            button.displayString = I18n.format(showNodeName ? "adm.button.disable" : "adm.button.enable") + " "
                + I18n.format("adm.label.grapple.show_node_name_short");
        } else if (button.id == BTN_SHOW_DIST) {
            showNodeDistance = !showNodeDistance;
            button.displayString = I18n.format(showNodeDistance ? "adm.button.disable" : "adm.button.enable") + " "
                + I18n.format("adm.label.grapple.show_node_distance_short");
        } else if (button.id == BTN_SAVE) {
            saveRecording();
        } else if (button.id == BTN_RESET) {
            AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketGrapplePathAction.resetBuffer());
        } else if (button.id == BTN_RENAME_CONFIRM) {
            renameSelectedRoute();
            renamingActive = false;
            updateRenameFieldPosition();
            updateRenameButtons();
        } else if (button.id == BTN_RENAME_CANCEL) {
            renamingActive = false;
            updateRenameFieldPosition();
            updateRenameButtons();
        } else if (button.id == BTN_SAVE_CFG) {
            saveConfig();
        } else if (button.id == BTN_CLOSE) {
            this.mc.displayGuiScreen(null);
        }
    }

    private void applyMode(GrappleHookMode mode) {
        if (selectedMode == GrappleHookMode.PLANNING && mode != GrappleHookMode.PLANNING
            && GrappleClientRouteCache.getRecordingBufferSize() > 0) {
            this.mc.displayGuiScreen(new GuiGrappleSavePrompt(player, () -> this.mc.displayGuiScreen(this), () -> {
                selectedMode = mode;
                ItemGrappleHook.setHookMode(hookStack, mode);
                AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketGrapplePathAction.setMode(mode));
                this.mc.displayGuiScreen(new GuiGrappleHookConfig(hookStack, player));
            }));
            return;
        }
        selectedMode = mode;
        ItemGrappleHook.setHookMode(hookStack, mode);
        AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketGrapplePathAction.setMode(mode));
        initGui();
    }

    private void saveRecording() {
        String name = routeNameField.getText()
            .trim();
        if (name.isEmpty()) {
            name = I18n.format("adm.grapple.default_route_name");
        }
        AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketGrapplePathAction.saveRoute(name));
    }

    private void previewSelectedRoute() {
        GrappleRouteEntry route = getSelectedRoute();
        if (route == null || route.nodes.size() < 2) {
            return;
        }
        long worldTick = player.worldObj == null ? 0L : player.worldObj.getTotalWorldTime();
        GrappleClientRouteCache.togglePreview(route.routeId, route.nodes, worldTick);
        // 不关�?GUI：用户可�?GUI 中继续切换其他路线的预览状�?
    }

    private void deleteSelectedRoute() {
        GrappleRouteEntry route = getSelectedRoute();
        if (route == null) {
            return;
        }
        AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketGrapplePathAction.deleteRoute(route.routeId));
        selectedRouteIndex = -1;
        renamingActive = false;
        updateRenameFieldPosition();
        updateRenameButtons();
    }

    private void startRenameSelectedRoute() {
        GrappleRouteEntry route = getSelectedRoute();
        if (route == null) {
            return;
        }
        renameField.setText(route.name);
        renamingActive = true;
        ensureRenameRowVisible();
        updateRenameFieldPosition();
        updateRenameButtons();
    }

    private void renameSelectedRoute() {
        GrappleRouteEntry route = getSelectedRoute();
        if (route == null) {
            return;
        }
        String name = renameField.getText()
            .trim();
        if (name.isEmpty()) {
            return;
        }
        AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketGrapplePathAction.renameRoute(route.routeId, name));
    }

    private GrappleRouteEntry getSelectedRoute() {
        List<GrappleRouteEntry> routes = getVisibleRoutes();
        if (selectedRouteIndex < 0 || selectedRouteIndex >= routes.size()) {
            return null;
        }
        return routes.get(selectedRouteIndex);
    }

    private void saveConfig() {
        try {
            double speed = Double.parseDouble(
                speedField.getText()
                    .trim());
            if (speed < 0.1D || speed > 5.0D) {
                errorTips = I18n.format("adm.grapple.speed_hint");
                return;
            }
            ItemGrappleHook.setTravelSpeed(hookStack, speed);
            ItemGrappleHook.setShowNodeName(hookStack, showNodeName);
            ItemGrappleHook.setShowNodeDistance(hookStack, showNodeDistance);
            ItemGrappleHook.setHookMode(hookStack, selectedMode);
            AdvanceDataMonitor.ADMCHANEL
                .sendToServer(new PacketGrappleHookConfig(speed, showNodeName, showNodeDistance, selectedMode.getId()));
            AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketGrapplePathAction.setMode(selectedMode));
            this.mc.displayGuiScreen(null);
        } catch (NumberFormatException e) {
            errorTips = I18n.format("adm.error.invalid_number");
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int scroll = Mouse.getEventDWheel();
        if (scroll == 0) {
            return;
        }
        List<GrappleRouteEntry> routes = getVisibleRoutes();
        int totalHeight = routes.size() * LIST_ROW_HEIGHT;
        int maxScroll = Math.max(0, totalHeight - LIST_INNER_HEIGHT);
        if (scroll > 0) {
            scrollOffset = Math.max(0, scrollOffset - LIST_ROW_HEIGHT);
        } else {
            scrollOffset = Math.min(maxScroll, scrollOffset + LIST_ROW_HEIGHT);
        }
        if (renamingActive) {
            ensureRenameRowVisible();
            updateRenameFieldPosition();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        speedField.mouseClicked(mouseX, mouseY, mouseButton);
        if (selectedMode == GrappleHookMode.PLANNING) {
            routeNameField.mouseClicked(mouseX, mouseY, mouseButton);
        }
        if (renamingActive) {
            renameField.mouseClicked(mouseX, mouseY, mouseButton);
        }

        if (mouseButton == 0) {
            int listX = offsetX + CARD3_X + LIST_LEFT_PAD;
            int listY = offsetY + CARD3_Y + 22;
            if (mouseX >= listX && mouseX <= offsetX + CARD3_X + CARD3_W - 4
                && mouseY >= listY
                && mouseY <= listY + LIST_INNER_HEIGHT) {
                int row = (mouseY - listY + scrollOffset) / LIST_ROW_HEIGHT;
                List<GrappleRouteEntry> routes = getVisibleRoutes();
                if (row >= 0 && row < routes.size()) {
                    if (row == selectedRouteIndex && inlineBtnRowY >= 0) {
                        if (mouseY >= inlineBtnRowY && mouseY <= inlineBtnRowY + INLINE_BTN_H) {
                            if (mouseX >= inlinePreviewX && mouseX <= inlinePreviewX + INLINE_BTN_W) {
                                previewSelectedRoute();
                                return;
                            }
                            if (mouseX >= inlineRenameX && mouseX <= inlineRenameX + INLINE_BTN_W) {
                                startRenameSelectedRoute();
                                return;
                            }
                            if (mouseX >= inlineDeleteX && mouseX <= inlineDeleteX + INLINE_BTN_W) {
                                deleteSelectedRoute();
                                return;
                            }
                        }
                    }
                    if (renamingActive) {
                        renamingActive = false;
                        updateRenameFieldPosition();
                        updateRenameButtons();
                    }
                    selectedRouteIndex = row;
                }
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (renamingActive && renameField.isFocused()) {
            renameField.textboxKeyTyped(typedChar, keyCode);
            return;
        }
        if (speedField.isFocused()) {
            speedField.textboxKeyTyped(typedChar, keyCode);
            return;
        }
        if (routeNameField.isFocused()) {
            routeNameField.textboxKeyTyped(typedChar, keyCode);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);

        // 标题
        this.drawCenteredString(
            this.fontRendererObj,
            I18n.format("adm.title.grappleHookConfig"),
            offsetX + PANEL_WIDTH / 2,
            offsetY + 8,
            TEXT_LABEL);

        // ===== 卡片1 模式与速度 =====
        drawCard(offsetX + CARD1_X, offsetY + CARD1_Y, CARD1_W, CARD1_H, I18n.format("adm.grapple.section.mode_speed"));
        int c1y = offsetY + CARD1_Y;
        int c1x = offsetX + CARD1_X;
        this.drawString(
            this.fontRendererObj,
            I18n.format("adm.label.grapple.mode_label"),
            c1x + 6,
            c1y + 14,
            TEXT_SECONDARY);
        this.drawString(
            this.fontRendererObj,
            I18n.format("adm.label.grapple.travel_speed_setting"),
            c1x + 6,
            c1y + 58,
            TEXT_SECONDARY);
        speedField.drawTextBox();
        this.drawString(this.fontRendererObj, I18n.format("adm.grapple.speed_hint"), c1x + 6, c1y + 122, TEXT_DIM);

        // ===== 卡片2 录制 =====
        drawCard(offsetX + CARD2_X, offsetY + CARD2_Y, CARD2_W, CARD2_H, I18n.format("adm.grapple.section.recording"));
        int c2x = offsetX + CARD2_X;
        int c2y = offsetY + CARD2_Y;
        if (selectedMode == GrappleHookMode.PLANNING) {
            this.drawString(
                this.fontRendererObj,
                I18n.format("adm.grapple.recording_buffer", GrappleClientRouteCache.getRecordingBufferSize()),
                c2x + 8,
                c2y + 24,
                TEXT_OK);
            this.drawString(
                this.fontRendererObj,
                I18n.format("adm.grapple.route_name"),
                c2x + 8,
                c2y + 42,
                TEXT_SECONDARY);
            routeNameField.drawTextBox();
        } else {
            this.drawString(
                this.fontRendererObj,
                I18n.format("adm.grapple.recording_inactive"),
                c2x + 8,
                c2y + 60,
                TEXT_DIM);
        }

        // ===== 卡片3 路线列表 =====
        drawCard(offsetX + CARD3_X, offsetY + CARD3_Y, CARD3_W, CARD3_H, I18n.format("adm.grapple.saved_routes"));
        drawRouteList(mouseX, mouseY);

        if (renamingActive) {
            renameField.drawTextBox();
        }

        // ===== 底部提示 =====
        if (!errorTips.isEmpty()) {
            this.drawString(this.fontRendererObj, errorTips, offsetX + 10, offsetY + PANEL_HEIGHT - 50, TEXT_ERROR);
        }
    }

    private void drawCard(int x, int y, int w, int h, String title) {
        drawRect(x, y, x + w, y + h, CARD_BG);
        drawRect(x, y, x + w, y + 1, CARD_BORDER);
        drawRect(x, y, x + 1, y + h, CARD_BORDER);
        drawRect(x + w - 1, y, x + w, y + h, CARD_BORDER);
        drawRect(x, y + h - 1, x + w, y + h, CARD_BORDER);
        this.fontRendererObj.drawStringWithShadow(title, x + 8, y + 6, TEXT_LABEL);
    }

    private void drawRouteList(int mouseX, int mouseY) {
        List<GrappleRouteEntry> routes = getVisibleRoutes();
        int listX = offsetX + CARD3_X + LIST_LEFT_PAD;
        int listY = offsetY + CARD3_Y + 22;
        int listRight = offsetX + CARD3_X + CARD3_W - 4;

        // 列表区裁剪背�?
        drawRect(listX - 2, listY - 2, listRight + 2, listY + LIST_INNER_HEIGHT + 2, 0x20000000);

        // 行内按钮 X（只在有选中行且�?renaming 时绘制）
        inlinePreviewX = listRight - (INLINE_BTN_W * 3 + INLINE_BTN_GAP * 2 + 4);
        inlineRenameX = inlinePreviewX + INLINE_BTN_W + INLINE_BTN_GAP;
        inlineDeleteX = inlineRenameX + INLINE_BTN_W + INLINE_BTN_GAP;
        inlineBtnRowY = -1;

        for (int i = 0; i < routes.size(); i++) {
            int y = listY + i * LIST_ROW_HEIGHT - scrollOffset;
            if (y + LIST_ROW_HEIGHT <= listY || y >= listY + LIST_INNER_HEIGHT) {
                continue;
            }
            GrappleRouteEntry route = routes.get(i);
            boolean selected = (i == selectedRouteIndex);

            if (selected) {
                drawRect(listX - 2, y, listRight + 2, y + LIST_ROW_HEIGHT, SELECTED_ROW_BG);
            }

            String line = route.name + "  (" + I18n.format("adm.grapple.node_count", route.getNodeCount()) + ")";
            this.drawString(this.fontRendererObj, line, listX, y + 6, selected ? TEXT_LABEL : 0xCCCCCC);

            if (selected) {
                inlineBtnRowY = y + 2;
                boolean previewing = GrappleClientRouteCache.isPreviewing(route.routeId);
                String previewText = I18n.format(previewing ? "adm.grapple.cancel_preview" : "adm.grapple.preview");
                int previewColor = previewing ? 0xFFCC00 : 0x00FFFF;
                drawInlineButton(inlinePreviewX, inlineBtnRowY, mouseX, mouseY, previewText, previewColor);
                drawInlineButton(
                    inlineRenameX,
                    inlineBtnRowY,
                    mouseX,
                    mouseY,
                    I18n.format("adm.grapple.rename"),
                    0xAAAAFF);
                drawInlineButton(
                    inlineDeleteX,
                    inlineBtnRowY,
                    mouseX,
                    mouseY,
                    I18n.format("adm.button.delete"),
                    0xFF5555);
            }
        }

        if (routes.isEmpty()) {
            this.drawString(this.fontRendererObj, I18n.format("adm.grapple.no_routes"), listX, listY + 4, TEXT_DIM);
        }
    }

    private void drawInlineButton(int x, int y, int mouseX, int mouseY, String text, int textColor) {
        boolean hover = mouseX >= x && mouseX <= x + INLINE_BTN_W && mouseY >= y && mouseY <= y + INLINE_BTN_H;
        drawRect(x, y, x + INLINE_BTN_W, y + INLINE_BTN_H, hover ? INLINE_BTN_HOVER_BG : INLINE_BTN_BG);
        drawRect(x, y, x + 1, y + INLINE_BTN_H, 0x6000FFFF);
        drawRect(x + INLINE_BTN_W - 1, y, x + INLINE_BTN_W, y + INLINE_BTN_H, 0x6000FFFF);
        this.drawCenteredString(
            this.fontRendererObj,
            text,
            x + INLINE_BTN_W / 2,
            y + (INLINE_BTN_H - 8) / 2,
            textColor);
    }

    private void ensureRenameRowVisible() {
        List<GrappleRouteEntry> routes = getVisibleRoutes();
        if (selectedRouteIndex < 0 || selectedRouteIndex >= routes.size()) {
            return;
        }
        // 重命名输入框出现在选中行下一行位置，需要选中�?+ 下一行都可见
        int selectedTop = selectedRouteIndex * LIST_ROW_HEIGHT;
        int renameBottom = selectedTop + LIST_ROW_HEIGHT * 2;
        if (selectedTop < scrollOffset) {
            scrollOffset = selectedTop;
        } else if (renameBottom > scrollOffset + LIST_INNER_HEIGHT) {
            scrollOffset = renameBottom - LIST_INNER_HEIGHT;
        }
    }

    private void updateRenameFieldPosition() {
        if (renameField == null) {
            return;
        }
        if (!renamingActive || selectedRouteIndex < 0) {
            // 隐藏到屏幕外
            renameField.xPosition = -1000;
            renameField.yPosition = -1000;
            renameField.setFocused(false);
            return;
        }
        int listX = offsetX + CARD3_X + LIST_LEFT_PAD;
        int listY = offsetY + CARD3_Y + 22;
        int rowY = listY + selectedRouteIndex * LIST_ROW_HEIGHT - scrollOffset;
        int renameY = rowY + LIST_ROW_HEIGHT + 2;
        // ADM_GuiTextField 内部�?xPosition=x-21 / yPosition=y-8（构造时偏移），这里直接重设以动态定位�?
        renameField.xPosition = (listX - 21);
        renameField.yPosition = (renameY - 8);
        renameField.setFocused(true);
    }

    private void updateRenameButtons() {
        this.buttonList.removeIf(b -> b.id == BTN_RENAME_CONFIRM || b.id == BTN_RENAME_CANCEL);
        if (!renamingActive || selectedRouteIndex < 0) {
            return;
        }
        int listX = offsetX + CARD3_X + LIST_LEFT_PAD;
        int listY = offsetY + CARD3_Y + 22;
        int rowY = listY + selectedRouteIndex * LIST_ROW_HEIGHT - scrollOffset;
        int renameY = rowY + LIST_ROW_HEIGHT + 2;
        int confirmX = listX + 290 + 6;
        this.buttonList.add(
            new ADM_GuiButton(BTN_RENAME_CONFIRM, confirmX, renameY, 50, 18, I18n.format("adm.button.save"))
                .setTexture(BUTTON_TEXTURE)
                .setHoverTexture(BUTTON_HOVER_TEXTURE)
                .setUseHoverEffect(true)
                .setTextColor(SAVE_TEXT)
                .setTextHoverColor(SAVE_TEXT_HOVER));
        this.buttonList.add(
            new ADM_GuiButton(BTN_RENAME_CANCEL, confirmX + 56, renameY, 50, 18, I18n.format("adm.button.cancel"))
                .setTexture(BUTTON_TEXTURE)
                .setHoverTexture(BUTTON_HOVER_TEXTURE)
                .setUseHoverEffect(true)
                .setTextColor(DELETE_TEXT)
                .setTextHoverColor(DELETE_TEXT_HOVER));
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (speedField != null) {
            speedField.updateCursorCounter();
        }
        if (routeNameField != null) {
            routeNameField.updateCursorCounter();
        }
        if (renameField != null) {
            renameField.updateCursorCounter();
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
