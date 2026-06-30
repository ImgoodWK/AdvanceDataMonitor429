package com.imgood.textech.gui.guiscreen;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.assistant.ai.AiProviderProfiles;
import com.imgood.textech.assistant.ai.AiProviderProfiles.ProviderProfile;
import com.imgood.textech.gui.custom.ADM_GuiButton;
import com.imgood.textech.gui.custom.ADM_GuiScreen;
import com.imgood.textech.gui.custom.ADM_GuiTextField;

/**
 * Display names / 显示名称:
 * - EN: AI Settings
 * - ZH: AI 设置
 * Lang keys: adm.ai.settings.title
 */
public class GuiAISettings extends ADM_GuiScreen {

    private static final int BUTTON_SAVE = 10;
    private static final int BUTTON_BACK = 11;
    private static final int BUTTON_PROVIDER = 12;
    private static final int BUTTON_MODEL = 13;
    private static final int BUTTON_NETWORK = 14;
    private static final int BUTTON_SEARCH = 15;
    private static final int BUTTON_SEARCH_MODE = 16;
    private static final int BUTTON_DEBUG = 17;
    private static final int BUTTON_STREAM = 18;
    private static final int BUTTON_VOICE = 19;
    private static final int BUTTON_VOICE_MODE = 20;
    private static final int PANEL_WIDTH = 620;
    private static final int PANEL_HEIGHT = 460;
    private static final int TEXT_COLOR = 0x00FFFF;
    private static final int TEXT_HOVER_COLOR = 0x0055FF;

    private static final String[] SEARCH_MODES = { AiProviderProfiles.MODE_AUTO, AiProviderProfiles.MODE_OPENAI,
        AiProviderProfiles.MODE_OPENROUTER, AiProviderProfiles.MODE_DASHSCOPE, AiProviderProfiles.MODE_ZHIPU,
        AiProviderProfiles.MODE_GENERIC_TOOLS, AiProviderProfiles.MODE_OFF };
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
    private static final ResourceLocation BACKGROUND_TEXTURE = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/background_ADM_Sub.png");

    private final GuiScreen parent;
    private ADM_GuiTextField apiKeyField;
    private ADM_GuiTextField baseUrlField;
    private ADM_GuiTextField modelField;
    private ADM_GuiTextField timeoutField;
    private ADM_GuiTextField maxTokensField;
    private ADM_GuiTextField temperatureField;
    private ADM_GuiTextField focusedField;
    private ProviderProfile provider;
    private int modelIndex;
    private boolean networkEnabled;
    private boolean webSearchEnabled;
    private boolean debugLogging;
    private boolean streamingEnabled;
    private boolean voiceEnabled;
    private String voiceMode;
    private String searchMode;
    private String statusMessage = "";
    private int offsetX;
    private int offsetY;

    public GuiAISettings(GuiScreen parent) {
        this.parent = parent;
        this.setBackgroundTexture(BACKGROUND_TEXTURE);
        this.setSize(PANEL_WIDTH, PANEL_HEIGHT);
        this.setStretch(false);
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();
        this.offsetX = (this.width - PANEL_WIDTH) / 2;
        this.offsetY = (this.height - PANEL_HEIGHT) / 2;
        this.setPosition(this.offsetX, this.offsetY);
        this.provider = AiProviderProfiles.detectProfile();
        this.modelIndex = findModelIndex(this.provider, Config.aiModel);
        this.networkEnabled = Config.aiNetworkEnabled;
        this.webSearchEnabled = Config.aiWebSearchEnabled;
        this.debugLogging = Config.aiDebugLogging;
        this.streamingEnabled = Config.aiStreamingEnabled;
        this.voiceEnabled = Config.voiceAssistantEnabled;
        this.voiceMode = Config.voiceSttMode;
        this.searchMode = Config.aiWebSearchMode;

        int x = this.offsetX + 170;
        int y = this.offsetY + 64;
        this.apiKeyField = createField(
            x,
            y,
            Config.aiApiKey.isEmpty() ? Config.getAiApiKey() : Config.aiApiKey,
            "adm.ai.settings.key_hint");
        this.baseUrlField = createField(x, y + 32, Config.aiApiBaseUrl, "adm.ai.settings.base_hint");
        this.modelField = createField(x, y + 64, Config.aiModel, "adm.ai.settings.model_hint");
        this.timeoutField = createField(x, y + 96, String.valueOf(Config.aiTimeoutSeconds), "");
        this.maxTokensField = createField(x, y + 128, String.valueOf(Config.aiMaxTokens), "");
        this.temperatureField = createField(x, y + 160, String.valueOf(Config.aiTemperature), "");
        this.focusedField = this.modelField;
        this.modelField.setFocused(true);

        this.buttonList.add(createButton(BUTTON_PROVIDER, this.offsetX + 440, y + 32, 130, 20, providerText()));
        this.buttonList.add(
            createButton(BUTTON_MODEL, this.offsetX + 440, y + 64, 130, 20, I18n.format("adm.ai.settings.next_model")));
        this.buttonList.add(
            createButton(
                BUTTON_NETWORK,
                this.offsetX + 54,
                y + 204,
                120,
                20,
                boolText("adm.ai.settings.network", this.networkEnabled)));
        this.buttonList.add(
            createButton(
                BUTTON_SEARCH,
                this.offsetX + 184,
                y + 204,
                120,
                20,
                boolText("adm.ai.settings.search", this.webSearchEnabled)));
        this.buttonList.add(createButton(BUTTON_SEARCH_MODE, this.offsetX + 314, y + 204, 140, 20, modeText()));
        this.buttonList.add(
            createButton(
                BUTTON_DEBUG,
                this.offsetX + 54,
                y + 232,
                120,
                20,
                boolText("adm.ai.settings.debug", this.debugLogging)));
        this.buttonList.add(
            createButton(
                BUTTON_STREAM,
                this.offsetX + 184,
                y + 232,
                120,
                20,
                boolText("adm.ai.settings.stream", this.streamingEnabled)));
        this.buttonList.add(
            createButton(BUTTON_VOICE, this.offsetX + 314, y + 232, 120, 20, boolText("Voice", this.voiceEnabled)));
        this.buttonList.add(createButton(BUTTON_VOICE_MODE, this.offsetX + 444, y + 232, 126, 20, voiceModeText()));
        this.buttonList.add(
            createButton(
                BUTTON_SAVE,
                this.offsetX + PANEL_WIDTH - 160,
                this.offsetY + PANEL_HEIGHT - 34,
                68,
                20,
                I18n.format("adm.button.save")));
        this.buttonList.add(
            createButton(
                BUTTON_BACK,
                this.offsetX + PANEL_WIDTH - 84,
                this.offsetY + PANEL_HEIGHT - 34,
                68,
                20,
                I18n.format("adm.label.back")));
    }

    private ADM_GuiTextField createField(int x, int y, String text, String hintKey) {
        ADM_GuiTextField field = new ADM_GuiTextField(this.fontRendererObj, x, y + 8, 240, 20)
            .setBackgroundTexture(TEXTFIELD_TEXTURE)
            .setFocusedBackgroundTexture(TEXTFIELD_HOVER_TEXTURE)
            .setHintText(hintKey.isEmpty() ? "" : I18n.format(hintKey));
        field.setMaxStringLength(2048);
        field.setText(text == null ? "" : text);
        return field;
    }

    private ADM_GuiButton createButton(int id, int x, int y, int width, int height, String text) {
        return new ADM_GuiButton(id, x, y, width, height, text).setTexture(BUTTON_TEXTURE)
            .setHoverTexture(BUTTON_HOVER_TEXTURE)
            .setUseHoverEffect(true)
            .setLeftDecoration(BUTTON_HOVER_TEXTURE)
            .setRightDecoration(BUTTON_HOVER_TEXTURE)
            .setDecorationWidth(20)
            .setTextColor(TEXT_COLOR)
            .setTextHoverColor(TEXT_HOVER_COLOR);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case BUTTON_SAVE:
                saveSettings();
                break;
            case BUTTON_BACK:
                this.mc.displayGuiScreen(this.parent);
                break;
            case BUTTON_PROVIDER:
                cycleProvider(button);
                break;
            case BUTTON_MODEL:
                cycleModel();
                break;
            case BUTTON_NETWORK:
                this.networkEnabled = !this.networkEnabled;
                button.displayString = boolText("adm.ai.settings.network", this.networkEnabled);
                break;
            case BUTTON_SEARCH:
                this.webSearchEnabled = !this.webSearchEnabled;
                button.displayString = boolText("adm.ai.settings.search", this.webSearchEnabled);
                break;
            case BUTTON_SEARCH_MODE:
                this.searchMode = nextSearchMode(this.searchMode);
                button.displayString = modeText();
                break;
            case BUTTON_DEBUG:
                this.debugLogging = !this.debugLogging;
                button.displayString = boolText("adm.ai.settings.debug", this.debugLogging);
                break;
            case BUTTON_STREAM:
                this.streamingEnabled = !this.streamingEnabled;
                button.displayString = boolText("adm.ai.settings.stream", this.streamingEnabled);
                break;
            case BUTTON_VOICE:
                this.voiceEnabled = !this.voiceEnabled;
                button.displayString = boolText("Voice", this.voiceEnabled);
                break;
            case BUTTON_VOICE_MODE:
                this.voiceMode = Config.VOICE_STT_MODE_HTTP.equalsIgnoreCase(this.voiceMode)
                    ? Config.VOICE_STT_MODE_EMBEDDED_VOSK
                    : Config.VOICE_STT_MODE_HTTP;
                button.displayString = voiceModeText();
                break;
            default:
                break;
        }
    }

    private void cycleProvider(GuiButton button) {
        ProviderProfile[] profiles = AiProviderProfiles.allProfiles();
        int index = 0;
        for (int i = 0; i < profiles.length; i++) {
            if (profiles[i].id.equals(this.provider.id)) {
                index = i;
                break;
            }
        }
        this.provider = profiles[(index + 1) % profiles.length];
        this.modelIndex = 0;
        this.baseUrlField.setText(this.provider.baseUrl);
        this.modelField.setText(this.provider.defaultModel);
        this.searchMode = this.provider.defaultSearchMode;
        this.webSearchEnabled = !AiProviderProfiles.MODE_UNSUPPORTED.equals(this.searchMode)
            && !AiProviderProfiles.MODE_OFF.equals(this.searchMode);
        button.displayString = providerText();
        refreshButtons();
    }

    private void cycleModel() {
        String[] models = modelChoices();
        if (models.length == 0) {
            return;
        }
        this.modelIndex = (this.modelIndex + 1) % models.length;
        this.modelField.setText(models[this.modelIndex].trim());
    }

    private String[] modelChoices() {
        String[] recent = Config.getRecentAiModels();
        String[] presets = this.provider.modelPresets;
        String[] result = new String[presets.length + recent.length];
        int index = 0;
        for (String preset : presets) {
            result[index++] = preset;
        }
        for (String model : recent) {
            if (!contains(result, index, model.trim())) {
                result[index++] = model.trim();
            }
        }
        String[] compact = new String[index];
        System.arraycopy(result, 0, compact, 0, index);
        return compact;
    }

    private boolean contains(String[] values, int length, String target) {
        for (int i = 0; i < length; i++) {
            if (values[i] != null && values[i].equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    private int findModelIndex(ProviderProfile profile, String model) {
        for (int i = 0; i < profile.modelPresets.length; i++) {
            if (profile.modelPresets[i].equalsIgnoreCase(model)) {
                return i;
            }
        }
        return 0;
    }

    private String nextSearchMode(String current) {
        for (int i = 0; i < SEARCH_MODES.length; i++) {
            if (SEARCH_MODES[i].equalsIgnoreCase(current)) {
                return SEARCH_MODES[(i + 1) % SEARCH_MODES.length];
            }
        }
        return AiProviderProfiles.MODE_AUTO;
    }

    private void refreshButtons() {
        for (Object obj : this.buttonList) {
            GuiButton button = (GuiButton) obj;
            if (button.id == BUTTON_SEARCH) {
                button.displayString = boolText("adm.ai.settings.search", this.webSearchEnabled);
            } else if (button.id == BUTTON_SEARCH_MODE) {
                button.displayString = modeText();
            }
        }
    }

    private void saveSettings() {
        try {
            Config.saveAiSettings(
                this.apiKeyField.getText(),
                this.baseUrlField.getText(),
                this.modelField.getText(),
                this.searchMode,
                this.webSearchEnabled,
                this.networkEnabled,
                this.debugLogging,
                this.streamingEnabled,
                Integer.parseInt(
                    this.timeoutField.getText()
                        .trim()),
                Integer.parseInt(
                    this.maxTokensField.getText()
                        .trim()),
                Double.parseDouble(
                    this.temperatureField.getText()
                        .trim()));
            Config.saveVoiceSettings(
                this.voiceEnabled,
                Config.voicePrivacyConfirmed,
                this.voiceMode,
                Config.voiceSttBaseUrl,
                Config.voiceSttApiKey,
                Config.voiceSttModel,
                Config.voiceSttTimeoutSeconds);
            this.statusMessage = I18n.format("adm.ai.settings.saved");
        } catch (Exception e) {
            this.statusMessage = I18n.format("adm.ai.settings.invalid", e.getMessage());
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        if (this.focusedField != null) {
            this.focusedField.textboxKeyTyped(typedChar, keyCode);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        focusField(this.apiKeyField, mouseX, mouseY, mouseButton);
        focusField(this.baseUrlField, mouseX, mouseY, mouseButton);
        focusField(this.modelField, mouseX, mouseY, mouseButton);
        focusField(this.timeoutField, mouseX, mouseY, mouseButton);
        focusField(this.maxTokensField, mouseX, mouseY, mouseButton);
        focusField(this.temperatureField, mouseX, mouseY, mouseButton);
    }

    private void focusField(ADM_GuiTextField field, int mouseX, int mouseY, int mouseButton) {
        field.mouseClicked(mouseX, mouseY, mouseButton);
        if (field.isFocused()) {
            if (this.focusedField != null && this.focusedField != field) {
                this.focusedField.setFocused(false);
            }
            this.focusedField = field;
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        this.apiKeyField.updateCursorCounter();
        this.baseUrlField.updateCursorCounter();
        this.modelField.updateCursorCounter();
        this.timeoutField.updateCursorCounter();
        this.maxTokensField.updateCursorCounter();
        this.temperatureField.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawCenteredString(
            this.fontRendererObj,
            I18n.format("adm.ai.settings.title"),
            this.width / 2,
            this.offsetY + 16,
            TEXT_COLOR);
        int labelX = this.offsetX + 52;
        int y = this.offsetY + 64;
        drawString(this.fontRendererObj, I18n.format("adm.ai.settings.key"), labelX, y, 0xAAAAAA);
        drawString(this.fontRendererObj, I18n.format("adm.ai.settings.base"), labelX, y + 32, 0xAAAAAA);
        drawString(this.fontRendererObj, I18n.format("adm.ai.settings.model"), labelX, y + 64, 0xAAAAAA);
        drawString(this.fontRendererObj, I18n.format("adm.ai.settings.timeout"), labelX, y + 96, 0xAAAAAA);
        drawString(this.fontRendererObj, I18n.format("adm.ai.settings.tokens"), labelX, y + 128, 0xAAAAAA);
        drawString(this.fontRendererObj, I18n.format("adm.ai.settings.temperature"), labelX, y + 160, 0xAAAAAA);
        drawString(
            this.fontRendererObj,
            "Voice mode: " + this.voiceMode + " (details are in the voice config section).",
            labelX,
            y + 256,
            0xAAAAAA);
        drawString(
            this.fontRendererObj,
            AiProviderProfiles.searchCapability(
                this.baseUrlField.getText(),
                this.modelField.getText(),
                this.searchMode,
                this.webSearchEnabled).message,
            labelX,
            y + 272,
            0xAAAAAA);
        drawString(
            this.fontRendererObj,
            this.statusMessage,
            labelX,
            y + 292,
            this.statusMessage.startsWith("Error") ? 0xFF5555 : 0x55FF55);
        this.apiKeyField.drawTextBox();
        this.baseUrlField.drawTextBox();
        this.modelField.drawTextBox();
        this.timeoutField.drawTextBox();
        this.maxTokensField.drawTextBox();
        this.temperatureField.drawTextBox();
    }

    private String providerText() {
        return I18n.format("adm.ai.settings.provider") + ": " + this.provider.displayName;
    }

    private String modeText() {
        return I18n.format("adm.ai.settings.mode") + ": " + this.searchMode;
    }

    private String voiceModeText() {
        return "Voice STT: " + (Config.VOICE_STT_MODE_HTTP.equalsIgnoreCase(this.voiceMode) ? "HTTP" : "Offline");
    }

    private String boolText(String key, boolean value) {
        return I18n.format(key) + ": " + I18n.format(value ? "adm.ai.on" : "adm.ai.off");
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
