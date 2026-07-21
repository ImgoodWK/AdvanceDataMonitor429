package com.imgood.textech.gui.guiscreen;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import org.lwjgl.input.Keyboard;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.client.WebSurfaceClientCache;
import com.imgood.textech.client.websurface.HttpFrameWebSurfaceSource;
import com.imgood.textech.client.websurface.McefWebSurfaceSource;
import com.imgood.textech.client.websurface.WebSurfaceSourceRouter;
import com.imgood.textech.gui.custom.ADM_GuiTextField;
import com.imgood.textech.gui.custom.AbstractMonitorSubGui;
import com.imgood.textech.network.packet.PacketMonitorWebSurface;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.textech.utils.WebDashboardSnapshotCodec;
import com.imgood.textech.utils.WebDisplayBindingCodec;

/**
 * Clipboard-driven configuration for a passive WebAE dashboard snapshot.
 */
public class GuiSubWebDashboard extends AbstractMonitorSubGui {

    private static final int BUTTON_SAVE = 0;
    private static final int BUTTON_CANCEL = 1;
    private static final int BUTTON_IMPORT = 2;
    private static final int BUTTON_COPY = 3;
    private static final int BUTTON_QUALITY = 4;
    private static final int BUTTON_BRIGHT = 5;
    private static final int BUTTON_ENABLE = 6;

    private final boolean newBinding;
    private ADM_GuiTextField displayNameField;
    private ADM_GuiTextField scaleField;
    private ADM_GuiTextField opacityField;
    private ADM_GuiTextField xOffsetField;
    private ADM_GuiTextField yOffsetField;
    private ADM_GuiTextField zOffsetField;
    private ADM_GuiTextField rotationXField;
    private ADM_GuiTextField rotationYField;
    private ADM_GuiTextField rotationZField;

    private String snapshotHash = "";
    private byte[] importedPayload;
    private WebDashboardSnapshotCodec.DecodedSnapshot snapshotInfo;
    private WebDisplayBindingCodec.Binding liveBinding;
    private String bindingJson = "";
    private String surfaceMode = TileEntityAdvanceDataMonitor.MODE_DASHBOARD_SNAPSHOT;
    private int textureWidth = 512;
    private boolean fullBright = true;
    private String status = "";
    private ADM_GuiTextField originField;

    public GuiSubWebDashboard(EntityPlayer player, World world, TileEntityAdvanceDataMonitor tileEntity, int index,
        boolean newBinding) {
        super(player, world, tileEntity, index);
        this.newBinding = newBinding;
        this.startOffsetX = -255;
        this.startOffsetY = -155;
        this.setSize(550, 340);
    }

    @Override
    protected void assignTextField(String row, int fieldIndex, ADM_GuiTextField field) {
        if ("Left".equals(row)) {
            switch (fieldIndex) {
                case 0:
                    displayNameField = field;
                    break;
                case 1:
                    scaleField = field;
                    break;
                case 2:
                    opacityField = field;
                    break;
                case 3:
                    xOffsetField = field;
                    break;
                case 4:
                    yOffsetField = field;
                    break;
                case 5:
                    originField = field;
                    break;
                default:
                    break;
            }
        } else {
            switch (fieldIndex) {
                case 0:
                    zOffsetField = field;
                    break;
                case 1:
                    rotationXField = field;
                    break;
                case 2:
                    rotationYField = field;
                    break;
                case 3:
                    rotationZField = field;
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public void initGui() {
        if (newBinding) {
            Keyboard.enableRepeatEvents(true);
            if (!isInitialized) isEnabled = true;
        } else {
            beginInitGui();
        }
        String[] previousValues = null;
        if (isInitialized && displayNameField != null) {
            previousValues = new String[] { displayNameField.getText(), scaleField.getText(), opacityField.getText(),
                xOffsetField.getText(), yOffsetField.getText(),
                originField != null ? originField.getText() : HttpFrameWebSurfaceSource.resolveOrigin(""),
                zOffsetField.getText(), rotationXField.getText(), rotationYField.getText(), rotationZField.getText() };
        }
        layoutMonitorPanel();
        textFieldsLeft.clear();
        for (int i = 0; i < 6; i++) textFieldsLeft.add(null);
        autoTextField("Left", textFieldsLeft, 0, 28, offsetX + 125, offsetY + 40, 105, 18);
        textFieldsRight.clear();
        for (int i = 0; i < 4; i++) textFieldsRight.add(null);
        autoTextField("Right", textFieldsRight, 0, 28, offsetX + 365, offsetY + 40, 105, 18);

        NBTTagCompound existing = newBinding ? null : tileEntity.getDataBound(index);
        String defaultOrigin = existing != null && existing.hasKey(TileEntityAdvanceDataMonitor.WEB_ORIGIN_KEY)
            ? existing.getString(TileEntityAdvanceDataMonitor.WEB_ORIGIN_KEY)
            : HttpFrameWebSurfaceSource.resolveOrigin("");
        String[] values = previousValues != null ? previousValues
            : new String[] {
                existing == null ? I18n.format("adm.web_dashboard.default_name") : existing.getString("displayName"),
                String.valueOf(existing == null ? 0.3F : existing.getFloat("scale")),
                String.valueOf(
                    existing == null || !existing.hasKey("webOpacity") ? 1.0F : existing.getFloat("webOpacity")),
                String.valueOf(existing == null ? 0.0F : existing.getFloat("xOffset")),
                String.valueOf(existing == null ? -0.5F : existing.getFloat("yOffset")), defaultOrigin,
                String.valueOf(existing == null ? -0.48F : existing.getFloat("zOffset")),
                String.valueOf(existing == null ? 0.0F : existing.getFloat("rotationX")),
                String.valueOf(existing == null ? 0.0F : existing.getFloat("rotationY")),
                String.valueOf(existing == null ? 0.0F : existing.getFloat("rotationZ")) };
        ADM_GuiTextField[] fields = { displayNameField, scaleField, opacityField, xOffsetField, yOffsetField,
            originField, zOffsetField, rotationXField, rotationYField, rotationZField };
        for (int i = 0; i < fields.length; i++) {
            if (fields[i] != null && i < values.length) fields[i].setText(values[i]);
        }
        for (ADM_GuiTextField field : textFieldsLeft) {
            if (field != null) field.setMaxStringLength(field == displayNameField || field == originField ? 128 : 16);
        }
        for (ADM_GuiTextField field : textFieldsRight) field.setMaxStringLength(16);

        if (!isInitialized && existing != null) {
            snapshotHash = existing.getString(TileEntityAdvanceDataMonitor.WEB_DASHBOARD_HASH_KEY);
            textureWidth = normalizeTextureWidth(
                existing.hasKey("webTextureWidth") ? existing.getInteger("webTextureWidth") : 512);
            fullBright = !existing.hasKey("webFullBright") || existing.getBoolean("webFullBright");
            isEnabled = !existing.hasKey("enable") || existing.getBoolean("enable");
            surfaceMode = existing.hasKey(TileEntityAdvanceDataMonitor.WEB_SURFACE_MODE_KEY)
                ? existing.getString(TileEntityAdvanceDataMonitor.WEB_SURFACE_MODE_KEY)
                : TileEntityAdvanceDataMonitor.MODE_DASHBOARD_SNAPSHOT;
            if (TileEntityAdvanceDataMonitor.MODE_DASHBOARD_SNAPSHOT.equals(surfaceMode)
                && snapshotHash.length() == 64) {
                byte[] local = WebSurfaceClientCache.getContent(snapshotHash);
                if (local != null) decodeInfo(local);
                WebSurfaceClientCache.requestContentIfNeeded(
                    world.provider.dimensionId,
                    tileEntity.xCoord,
                    tileEntity.yCoord,
                    tileEntity.zCoord,
                    index,
                    snapshotHash);
            }
        }
        isInitialized = true;

        buttonList.clear();
        buttonList.add(
            monitorButton(
                BUTTON_IMPORT,
                offsetX + 25,
                offsetY + 205,
                120,
                20,
                I18n.format("adm.button.web_dashboard_import"),
                textColor,
                textHoverColor));
        buttonList.add(
            monitorButton(
                BUTTON_COPY,
                offsetX + 155,
                offsetY + 205,
                110,
                20,
                I18n.format("adm.button.web_dashboard_copy"),
                textColor,
                textHoverColor));
        buttonList.add(
            monitorButton(
                BUTTON_QUALITY,
                offsetX + 275,
                offsetY + 205,
                70,
                20,
                textureWidth + "px",
                textColor,
                textHoverColor));
        buttonList.add(
            monitorButton(
                BUTTON_BRIGHT,
                offsetX + 355,
                offsetY + 205,
                80,
                20,
                I18n.format(fullBright ? "adm.button.web_dashboard_bright" : "adm.button.web_dashboard_lit"),
                textColor,
                textHoverColor));
        buttonList.add(
            monitorButton(
                BUTTON_ENABLE,
                offsetX + 445,
                offsetY + 205,
                65,
                20,
                I18n.format(isEnabled ? "adm.button.disable" : "adm.button.enable"),
                textColor,
                textHoverColor));
        buttonList.add(
            monitorButton(
                BUTTON_SAVE,
                offsetX + 175,
                offsetY + 275,
                80,
                20,
                I18n.format("adm.button.save"),
                textColor,
                textHoverColor));
        buttonList.add(
            monitorButton(
                BUTTON_CANCEL,
                offsetX + 275,
                offsetY + 275,
                80,
                20,
                I18n.format("adm.button.cancel"),
                textColor,
                textHoverColor));
        fieldHints.clear();
        fieldHints.put(scaleField, "adm.hint.scale");
        fieldHints.put(opacityField, "adm.hint.web_dashboard_opacity");
        fieldHints.put(xOffsetField, "adm.hint.xoffset");
        fieldHints.put(yOffsetField, "adm.hint.yoffset");
        if (originField != null) fieldHints.put(originField, "adm.hint.web_dashboard_origin");
        fieldHints.put(zOffsetField, "adm.hint.zoffset");
        fieldHints.put(rotationXField, "adm.hint.rotationx");
        fieldHints.put(rotationYField, "adm.hint.rotationy");
        fieldHints.put(rotationZField, "adm.hint.rotationz");
        displayNameField.setFocused(true);
        focusedField = displayNameField;
        refreshStatus();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (snapshotInfo == null && snapshotHash.length() == 64) {
            byte[] payload = WebSurfaceClientCache.getContent(snapshotHash);
            if (payload != null) {
                decodeInfo(payload);
                refreshStatus();
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case BUTTON_IMPORT:
                importClipboard();
                break;
            case BUTTON_COPY:
                copySnapshot();
                break;
            case BUTTON_QUALITY:
                textureWidth = textureWidth == 256 ? 512 : (textureWidth == 512 ? 1024 : 256);
                button.displayString = textureWidth + "px";
                break;
            case BUTTON_BRIGHT:
                fullBright = !fullBright;
                button.displayString = I18n
                    .format(fullBright ? "adm.button.web_dashboard_bright" : "adm.button.web_dashboard_lit");
                break;
            case BUTTON_ENABLE:
                isEnabled = !isEnabled;
                button.displayString = I18n.format(isEnabled ? "adm.button.disable" : "adm.button.enable");
                break;
            case BUTTON_SAVE:
                saveDashboard();
                break;
            case BUTTON_CANCEL:
                openMainGui();
                break;
            default:
                break;
        }
    }

    private void importClipboard() {
        String json = getClipboardString();
        if (json == null || json.trim()
            .isEmpty()) {
            errorTips = I18n.format("adm.error.web_dashboard_clipboard_empty");
            return;
        }
        if (WebDisplayBindingCodec.looksLikeBinding(json)) {
            try {
                liveBinding = WebDisplayBindingCodec.parse(json);
                bindingJson = json.trim();
                surfaceMode = liveBinding.mode;
                snapshotHash = liveBinding.bindingHash;
                importedPayload = null;
                snapshotInfo = null;
                if (originField != null && (originField.getText()
                    .trim()
                    .isEmpty()
                    || HttpFrameWebSurfaceSource.resolveOrigin("")
                        .equals(
                            originField.getText()
                                .trim()))) {
                    if (liveBinding.webaeOrigin != null && !liveBinding.webaeOrigin.isEmpty()) {
                        originField.setText(liveBinding.webaeOrigin);
                    }
                }
                if (displayNameField.getText()
                    .trim()
                    .isEmpty()
                    || I18n.format("adm.web_dashboard.default_name")
                        .equals(displayNameField.getText())) {
                    displayNameField.setText(liveBinding.title);
                }
                errorTips = "";
                refreshStatus();
                return;
            } catch (WebDisplayBindingCodec.BindingException e) {
                errorTips = I18n.format("adm.error.web_dashboard_invalid") + " (" + e.getMessage() + ")";
                return;
            }
        }
        try {
            WebDashboardSnapshotCodec.EncodedSnapshot encoded = WebDashboardSnapshotCodec.encode(json);
            importedPayload = encoded.compressed;
            snapshotHash = encoded.hash;
            snapshotInfo = encoded.decoded;
            liveBinding = null;
            bindingJson = "";
            surfaceMode = TileEntityAdvanceDataMonitor.MODE_DASHBOARD_SNAPSHOT;
            WebSurfaceClientCache.acceptContent(snapshotHash, importedPayload);
            if (displayNameField.getText()
                .trim()
                .isEmpty()
                || I18n.format("adm.web_dashboard.default_name")
                    .equals(displayNameField.getText())) {
                displayNameField.setText(snapshotInfo.title);
            }
            errorTips = "";
            refreshStatus();
        } catch (WebDashboardSnapshotCodec.SnapshotException e) {
            errorTips = I18n.format("adm.error.web_dashboard_invalid") + " (" + e.getMessage() + ")";
        }
    }

    private void copySnapshot() {
        if (liveBinding != null && bindingJson != null && !bindingJson.isEmpty()) {
            setClipboardString(bindingJson);
            status = I18n.format("adm.status.web_dashboard_copied");
            errorTips = "";
            return;
        }
        byte[] payload = importedPayload != null ? importedPayload : WebSurfaceClientCache.getContent(snapshotHash);
        if (payload == null) {
            errorTips = I18n.format("adm.error.web_dashboard_not_loaded");
            return;
        }
        try {
            setClipboardString(WebDashboardSnapshotCodec.decode(payload).rawJson);
            status = I18n.format("adm.status.web_dashboard_copied");
            errorTips = "";
        } catch (WebDashboardSnapshotCodec.SnapshotException e) {
            errorTips = I18n.format("adm.error.web_dashboard_invalid");
        }
    }

    private void saveDashboard() {
        boolean live = TileEntityAdvanceDataMonitor.MODE_DASHBOARD_LIVE.equals(surfaceMode)
            || TileEntityAdvanceDataMonitor.MODE_LIVE_URL.equals(surfaceMode);
        if (newBinding && !live && importedPayload == null) {
            errorTips = I18n.format("adm.error.web_dashboard_required");
            return;
        }
        if (newBinding && live && liveBinding == null) {
            errorTips = I18n.format("adm.error.web_dashboard_required");
            return;
        }
        try {
            NBTTagCompound config = new NBTTagCompound();
            config.setString("dataType", TileEntityAdvanceDataMonitor.DATA_TYPE_WEBAE_DASHBOARD);
            config.setString("renderType", TileEntityAdvanceDataMonitor.RENDER_TYPE_WEB_SURFACE);
            config.setString(TileEntityAdvanceDataMonitor.WEB_SURFACE_MODE_KEY, surfaceMode);
            config.setString(
                "displayName",
                displayNameField.getText()
                    .trim());
            config.setFloat(
                "scale",
                Float.parseFloat(
                    scaleField.getText()
                        .trim()));
            config.setFloat(
                "webOpacity",
                Float.parseFloat(
                    opacityField.getText()
                        .trim()));
            config.setFloat(
                "xOffset",
                Float.parseFloat(
                    xOffsetField.getText()
                        .trim()));
            config.setFloat(
                "yOffset",
                Float.parseFloat(
                    yOffsetField.getText()
                        .trim()));
            config.setFloat(
                "zOffset",
                Float.parseFloat(
                    zOffsetField.getText()
                        .trim()));
            config.setFloat(
                "rotationX",
                Float.parseFloat(
                    rotationXField.getText()
                        .trim()));
            config.setFloat(
                "rotationY",
                Float.parseFloat(
                    rotationYField.getText()
                        .trim()));
            config.setFloat(
                "rotationZ",
                Float.parseFloat(
                    rotationZField.getText()
                        .trim()));
            config.setInteger("webTextureWidth", textureWidth);
            config.setBoolean("webFullBright", fullBright);
            config.setBoolean("enable", isEnabled);
            config.setString(TileEntityAdvanceDataMonitor.WEB_DASHBOARD_HASH_KEY, snapshotHash);
            String origin = originField != null ? originField.getText()
                .trim() : "";
            config.setString(TileEntityAdvanceDataMonitor.WEB_ORIGIN_KEY, origin);

            if (live && liveBinding != null) {
                config.setString("webBindingJson", bindingJson);
                config.setString(TileEntityAdvanceDataMonitor.WEB_DISPLAY_ID_KEY, liveBinding.displayId);
                config.setString(TileEntityAdvanceDataMonitor.WEB_VIEW_TOKEN_KEY, liveBinding.viewToken);
                config.setString(
                    TileEntityAdvanceDataMonitor.WEB_LIVE_URL_KEY,
                    liveBinding.url == null ? "" : liveBinding.url);
                config.setString(
                    TileEntityAdvanceDataMonitor.WEB_EMBED_PATH_KEY,
                    liveBinding.embedPath == null ? "" : liveBinding.embedPath);
                config.setString("webDashboardTitle", liveBinding.title);
                config.setInteger("webDashboardViewportWidth", liveBinding.viewportWidth);
                config.setInteger("webDashboardViewportHeight", liveBinding.viewportHeight);
            } else if (snapshotInfo != null) {
                config.setString("webDashboardTitle", snapshotInfo.title);
                config.setInteger("webDashboardPrimitiveCount", snapshotInfo.primitives.size());
                config.setInteger("webDashboardRawBytes", snapshotInfo.rawBytes);
                config.setInteger("webDashboardViewportWidth", snapshotInfo.width);
                config.setInteger("webDashboardViewportHeight", snapshotInfo.height);
            }

            NBTTagCompound local = (NBTTagCompound) config.copy();
            local.setString("XYZ", tileEntity.xCoord + "," + tileEntity.yCoord + "," + tileEntity.zCoord);
            tileEntity.setDisplayData(index, local);
            AdvanceDataMonitor.ADMCHANEL.sendToServer(
                PacketMonitorWebSurface.upload(
                    tileEntity.xCoord,
                    tileEntity.yCoord,
                    tileEntity.zCoord,
                    index,
                    snapshotHash,
                    live ? null : importedPayload,
                    config));
            openMainGui();
        } catch (NumberFormatException e) {
            errorTips = I18n.format("adm.error.web_dashboard_number");
        }
    }

    private void decodeInfo(byte[] payload) {
        try {
            snapshotInfo = WebDashboardSnapshotCodec.decode(payload);
        } catch (WebDashboardSnapshotCodec.SnapshotException e) {
            errorTips = I18n.format("adm.error.web_dashboard_invalid");
        }
    }

    private void refreshStatus() {
        if (liveBinding != null) {
            status = I18n
                .format("adm.status.web_dashboard_live", liveBinding.title, liveBinding.mode, liveBinding.displayId);
            return;
        }
        if (snapshotInfo == null) {
            status = snapshotHash.length() == 64 ? I18n.format("adm.status.web_dashboard_loading")
                : I18n.format("adm.status.web_dashboard_none");
            return;
        }
        status = I18n.format(
            "adm.status.web_dashboard_ready",
            snapshotInfo.title,
            Integer.valueOf(snapshotInfo.primitives.size()),
            Integer.valueOf(snapshotInfo.rawBytes / 1024));
    }

    private String mcefStatusLine() {
        boolean live = TileEntityAdvanceDataMonitor.MODE_DASHBOARD_LIVE.equals(surfaceMode)
            || TileEntityAdvanceDataMonitor.MODE_LIVE_URL.equals(surfaceMode);
        if (!live) {
            return I18n.format("adm.status.web_dashboard_frame_snapshot_not_web");
        }

        String source = WebSurfaceSourceRouter.getLastSource();
        String detail = WebSurfaceSourceRouter.getLastDetail();
        if (WebSurfaceSourceRouter.SOURCE_MCEF.equals(source)) {
            return I18n.format("adm.status.web_dashboard_frame_mcef");
        }
        if (WebSurfaceSourceRouter.SOURCE_BROWSER_JPEG.equals(source)) {
            return I18n.format("adm.status.web_dashboard_frame_browser");
        }
        if (WebSurfaceSourceRouter.SOURCE_SPA_JPEG.equals(source)) {
            return I18n.format("adm.status.web_dashboard_frame_spa");
        }
        if (WebSurfaceSourceRouter.SOURCE_SERVER_HTML.equals(source)) {
            return I18n.format("adm.status.web_dashboard_frame_server_html");
        }
        if (WebSurfaceSourceRouter.SOURCE_SNAPSHOT.equals(source)) {
            return I18n.format("adm.status.web_dashboard_frame_snapshot_not_web")
                + (detail.isEmpty() ? "" : " [" + detail + "]");
        }

        String pending;
        if (TileEntityAdvanceDataMonitor.MODE_LIVE_URL.equals(surfaceMode)) {
            pending = I18n.format("adm.status.web_dashboard_frame_live_url_need_mcef");
        } else if (Config.webSurfaceUseMcef && McefWebSurfaceSource.isClassPresent()
            && McefWebSurfaceSource.isAvailable()) {
                pending = I18n.format("adm.status.web_dashboard_frame_mcef_pending");
            } else if (Config.webSurfaceUseMcef && McefWebSurfaceSource.isClassPresent()
                && !McefWebSurfaceSource.isAvailable()) {
                    pending = I18n.format("adm.status.web_dashboard_frame_mcef_broken");
                } else {
                    pending = I18n.format("adm.status.web_dashboard_frame_browser_pending");
                }
        if (detail != null && !detail.isEmpty()) {
            return pending + " [" + detail + "]";
        }
        return pending;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawCenteredString(fontRendererObj, I18n.format("adm.title.web_dashboard"), width / 2, offsetY + 8, 0x00FFFF);
        String[] leftLabels = { I18n.format("adm.label.displayname"), I18n.format("adm.label.scaled"),
            I18n.format("adm.label.web_dashboard_opacity"), I18n.format("adm.label.xoffset"),
            I18n.format("adm.label.yoffset"), I18n.format("adm.label.web_dashboard_origin") };
        String[] rightLabels = { I18n.format("adm.label.zoffset"), I18n.format("adm.label.rotationx"),
            I18n.format("adm.label.rotationy"), I18n.format("adm.label.rotationz") };
        autoText(leftLabels, 0, 28, offsetX + 25, offsetY + 45, 0x00FFFF);
        autoText(rightLabels, 0, 28, offsetX + 275, offsetY + 45, 0x00FFFF);
        drawTextFieldsWithHover(mouseX, mouseY);
        fontRendererObj.drawStringWithShadow(status, offsetX + 25, offsetY + 240, 0xAAAAAA);
        fontRendererObj.drawStringWithShadow(mcefStatusLine(), offsetX + 25, offsetY + 252, 0x88AABB);
        fontRendererObj
            .drawStringWithShadow(I18n.format("adm.hint.web_dashboard_static"), offsetX + 25, offsetY + 266, 0x777777);
        if (!errorTips.isEmpty())
            fontRendererObj.drawStringWithShadow(errorTips, offsetX + 25, offsetY + 290, 0xFF5555);
        drawFocusedFieldHint(offsetX + 25, offsetY + 310);
    }

    private static int normalizeTextureWidth(int width) {
        if (width >= 768) return 1024;
        if (width >= 384) return 512;
        return 256;
    }
}
