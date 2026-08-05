package com.imgood.textech.gui.guiscreen;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Keyboard;

import com.imgood.textech.Config;
import com.imgood.textech.assistant.ai.AiProviderProfiles;
import com.imgood.textech.assistant.ai.AiProviderProfiles.ProviderProfile;
import com.imgood.textech.assistant.ai.WebSearchService;
import com.imgood.textech.gui.GuiResponsiveLayout;
import com.imgood.textech.gui.custom.ADM_GuiButton;
import com.imgood.textech.gui.custom.ADM_GuiScreen;
import com.imgood.textech.gui.custom.ADM_GuiTextField;
import com.imgood.textech.gui.custom.AdmGuiTextures;
import com.imgood.textech.gui.framework.UiFeedbackArea;

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
    private static final int BUTTON_SEARCH_FALLBACK = 21;
    private static final int PREFERRED_PANEL_WIDTH = 620;
    private static final int PREFERRED_PANEL_HEIGHT = 500;
    private static final int PANEL_MARGIN = 4;
    private static final int TEXT_COLOR = 0x00FFFF;
    private static final int TEXT_HOVER_COLOR = 0x0055FF;

    private static final String[] SEARCH_MODES = WebSearchService.allProviders();

    private final GuiScreen parent;
    private ADM_GuiTextField apiKeyField;
    private ADM_GuiTextField baseUrlField;
    private ADM_GuiTextField modelField;
    private ADM_GuiTextField timeoutField;
    private ADM_GuiTextField maxTokensField;
    private ADM_GuiTextField temperatureField;
    private ADM_GuiTextField searchApiKeyField;
    private ADM_GuiTextField searchBaseUrlField;
    private ADM_GuiTextField searchMaxResultsField;
    private ADM_GuiTextField focusedField;
    private ProviderProfile provider;
    private int modelIndex;
    private boolean networkEnabled;
    private boolean webSearchEnabled;
    private boolean searchFallback;
    private boolean debugLogging;
    private boolean streamingEnabled;
    private boolean voiceEnabled;
    private String voiceMode;
    private String searchMode;
    private String statusMessage = "";
    private boolean statusError;
    private int offsetX;
    private int offsetY;
    private int panelWidth = PREFERRED_PANEL_WIDTH;
    private int panelHeight = PREFERRED_PANEL_HEIGHT;
    private boolean compactLayout;

    public GuiAISettings(GuiScreen parent) {
        this.parent = parent;
        this.setBackgroundTexture(AdmGuiTextures.BACKGROUND_SUB);
        this.setSize(PREFERRED_PANEL_WIDTH, PREFERRED_PANEL_HEIGHT);
        this.setStretch(false);
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();
        GuiResponsiveLayout.Panel panel = GuiResponsiveLayout
            .fitCentered(this.width, this.height, PREFERRED_PANEL_WIDTH, PREFERRED_PANEL_HEIGHT, PANEL_MARGIN);
        this.offsetX = panel.x();
        this.offsetY = panel.y();
        this.panelWidth = panel.width();
        this.panelHeight = panel.height();
        this.compactLayout = this.panelWidth < PREFERRED_PANEL_WIDTH || this.panelHeight < PREFERRED_PANEL_HEIGHT;
        this.setSize(this.panelWidth, this.panelHeight);
        this.setPosition(this.offsetX, this.offsetY);
        this.provider = AiProviderProfiles.detectProfile();
        this.modelIndex = findModelIndex(this.provider, Config.aiModel);
        this.networkEnabled = Config.aiNetworkEnabled;
        this.webSearchEnabled = Config.aiWebSearchEnabled;
        this.debugLogging = Config.aiDebugLogging;
        this.streamingEnabled = Config.aiStreamingEnabled;
        this.voiceEnabled = Config.voiceAssistantEnabled;
        this.voiceMode = Config.voiceSttMode;
        this.searchMode = WebSearchService.normalizeProvider(Config.aiWebSearchMode);
        this.searchFallback = Config.aiSearchFallback;

        if (this.compactLayout) {
            initCompactControls();
        } else {
            initExpandedControls();
        }
        this.focusedField = this.modelField;
        this.modelField.setFocused(true);
    }

    private void initExpandedControls() {
        int x = this.offsetX + 170;
        int y = this.offsetY + 64;
        this.apiKeyField = createField(
            x,
            y + 8,
            240,
            Config.aiApiKey.isEmpty() ? Config.getAiApiKey() : Config.aiApiKey,
            "adm.ai.settings.key_hint");
        this.baseUrlField = createField(x, y + 40, 240, Config.aiApiBaseUrl, "adm.ai.settings.base_hint");
        this.modelField = createField(x, y + 72, 240, Config.aiModel, "adm.ai.settings.model_hint");
        this.timeoutField = createField(x, y + 104, 240, String.valueOf(Config.aiTimeoutSeconds), "");
        this.maxTokensField = createField(x, y + 136, 240, String.valueOf(Config.aiMaxTokens), "");
        this.temperatureField = createField(x, y + 168, 240, String.valueOf(Config.aiTemperature), "");
        this.searchApiKeyField = createField(
            x,
            y + 200,
            240,
            Config.getAiSearchApiKey(),
            "adm.ai.settings.search_key_hint");
        this.searchBaseUrlField = createField(
            x,
            y + 232,
            240,
            Config.aiSearchBaseUrl,
            "adm.ai.settings.search_base_hint");
        this.searchMaxResultsField = createField(
            x,
            y + 264,
            240,
            String.valueOf(Config.aiSearchMaxResults),
            "adm.ai.settings.search_max_hint");

        this.buttonList.add(createButton(BUTTON_PROVIDER, this.offsetX + 440, y + 32, 130, 20, providerText()));
        this.buttonList.add(
            createButton(BUTTON_MODEL, this.offsetX + 440, y + 64, 130, 20, I18n.format("adm.ai.settings.next_model")));
        this.buttonList.add(
            createButton(
                BUTTON_NETWORK,
                this.offsetX + 54,
                y + 292,
                120,
                20,
                boolText("adm.ai.settings.network", this.networkEnabled)));
        this.buttonList.add(
            createButton(
                BUTTON_SEARCH,
                this.offsetX + 184,
                y + 292,
                120,
                20,
                boolText("adm.ai.settings.search", this.webSearchEnabled)));
        this.buttonList.add(createButton(BUTTON_SEARCH_MODE, this.offsetX + 314, y + 292, 140, 20, modeText()));
        this.buttonList.add(
            createButton(
                BUTTON_SEARCH_FALLBACK,
                this.offsetX + 464,
                y + 292,
                106,
                20,
                boolText("adm.ai.settings.search_fallback", this.searchFallback)));
        this.buttonList.add(
            createButton(
                BUTTON_DEBUG,
                this.offsetX + 54,
                y + 320,
                120,
                20,
                boolText("adm.ai.settings.debug", this.debugLogging)));
        this.buttonList.add(
            createButton(
                BUTTON_STREAM,
                this.offsetX + 184,
                y + 320,
                120,
                20,
                boolText("adm.ai.settings.stream", this.streamingEnabled)));
        this.buttonList.add(
            createButton(
                BUTTON_VOICE,
                this.offsetX + 314,
                y + 320,
                120,
                20,
                boolText("adm.ai.settings.voice", this.voiceEnabled)));
        this.buttonList.add(createButton(BUTTON_VOICE_MODE, this.offsetX + 444, y + 320, 126, 20, voiceModeText()));
        this.buttonList.add(
            createButton(
                BUTTON_SAVE,
                this.offsetX + this.panelWidth - 160,
                this.offsetY + this.panelHeight - 34,
                68,
                20,
                I18n.format("adm.button.save")));
        this.buttonList.add(
            createButton(
                BUTTON_BACK,
                this.offsetX + this.panelWidth - 84,
                this.offsetY + this.panelHeight - 34,
                68,
                20,
                I18n.format("adm.label.back")));
    }

    private void initCompactControls() {
        int innerX = this.offsetX + 12;
        int gap = 6;
        int columnWidth = Math.max(80, (this.panelWidth - 24 - gap * 2) / 3);
        int rowHeight = 28;
        int labelTop = this.offsetY + 22;

        this.apiKeyField = createCompactField(
            0,
            innerX,
            labelTop,
            columnWidth,
            gap,
            rowHeight,
            Config.aiApiKey.isEmpty() ? Config.getAiApiKey() : Config.aiApiKey,
            "adm.ai.settings.key_hint");
        this.baseUrlField = createCompactField(
            1,
            innerX,
            labelTop,
            columnWidth,
            gap,
            rowHeight,
            Config.aiApiBaseUrl,
            "adm.ai.settings.base_hint");
        this.modelField = createCompactField(
            2,
            innerX,
            labelTop,
            columnWidth,
            gap,
            rowHeight,
            Config.aiModel,
            "adm.ai.settings.model_hint");
        this.timeoutField = createCompactField(
            3,
            innerX,
            labelTop,
            columnWidth,
            gap,
            rowHeight,
            String.valueOf(Config.aiTimeoutSeconds),
            "");
        this.maxTokensField = createCompactField(
            4,
            innerX,
            labelTop,
            columnWidth,
            gap,
            rowHeight,
            String.valueOf(Config.aiMaxTokens),
            "");
        this.temperatureField = createCompactField(
            5,
            innerX,
            labelTop,
            columnWidth,
            gap,
            rowHeight,
            String.valueOf(Config.aiTemperature),
            "");
        this.searchApiKeyField = createCompactField(
            6,
            innerX,
            labelTop,
            columnWidth,
            gap,
            rowHeight,
            Config.getAiSearchApiKey(),
            "adm.ai.settings.search_key_hint");
        this.searchBaseUrlField = createCompactField(
            7,
            innerX,
            labelTop,
            columnWidth,
            gap,
            rowHeight,
            Config.aiSearchBaseUrl,
            "adm.ai.settings.search_base_hint");
        this.searchMaxResultsField = createCompactField(
            8,
            innerX,
            labelTop,
            columnWidth,
            gap,
            rowHeight,
            String.valueOf(Config.aiSearchMaxResults),
            "adm.ai.settings.search_max_hint");

        int fullInnerWidth = this.panelWidth - 24;
        int providerY = this.offsetY + 108;
        int providerWidth = (fullInnerWidth - gap) / 2;
        this.buttonList.add(createButton(BUTTON_PROVIDER, innerX, providerY, providerWidth, 18, providerText()));
        this.buttonList.add(
            createButton(
                BUTTON_MODEL,
                innerX + providerWidth + gap,
                providerY,
                fullInnerWidth - providerWidth - gap,
                18,
                I18n.format("adm.ai.settings.next_model")));

        int toggleGap = 4;
        int toggleWidth = (fullInnerWidth - toggleGap * 3) / 4;
        int rowOneY = this.offsetY + 130;
        int rowTwoY = this.offsetY + 151;
        addCompactToggle(
            BUTTON_NETWORK,
            0,
            rowOneY,
            innerX,
            toggleWidth,
            toggleGap,
            boolText("adm.ai.settings.network", this.networkEnabled));
        addCompactToggle(
            BUTTON_SEARCH,
            1,
            rowOneY,
            innerX,
            toggleWidth,
            toggleGap,
            boolText("adm.ai.settings.search", this.webSearchEnabled));
        addCompactToggle(BUTTON_SEARCH_MODE, 2, rowOneY, innerX, toggleWidth, toggleGap, modeText());
        addCompactToggle(
            BUTTON_SEARCH_FALLBACK,
            3,
            rowOneY,
            innerX,
            toggleWidth,
            toggleGap,
            boolText("adm.ai.settings.search_fallback", this.searchFallback));
        addCompactToggle(
            BUTTON_DEBUG,
            0,
            rowTwoY,
            innerX,
            toggleWidth,
            toggleGap,
            boolText("adm.ai.settings.debug", this.debugLogging));
        addCompactToggle(
            BUTTON_STREAM,
            1,
            rowTwoY,
            innerX,
            toggleWidth,
            toggleGap,
            boolText("adm.ai.settings.stream", this.streamingEnabled));
        addCompactToggle(
            BUTTON_VOICE,
            2,
            rowTwoY,
            innerX,
            toggleWidth,
            toggleGap,
            boolText("adm.ai.settings.voice", this.voiceEnabled));
        addCompactToggle(BUTTON_VOICE_MODE, 3, rowTwoY, innerX, toggleWidth, toggleGap, voiceModeText());

        int actionY = this.offsetY + this.panelHeight - 26;
        this.buttonList.add(
            createButton(
                BUTTON_SAVE,
                this.offsetX + this.panelWidth - 160,
                actionY,
                68,
                20,
                I18n.format("adm.button.save")));
        this.buttonList.add(
            createButton(
                BUTTON_BACK,
                this.offsetX + this.panelWidth - 84,
                actionY,
                68,
                20,
                I18n.format("adm.label.back")));
    }

    private ADM_GuiTextField createCompactField(int index, int innerX, int labelTop, int columnWidth, int gap,
        int rowHeight, String text, String hintKey) {
        int column = index % 3;
        int row = index / 3;
        int x = innerX + column * (columnWidth + gap);
        int y = labelTop + row * rowHeight + 8;
        return createField(x, y, columnWidth, text, hintKey);
    }

    private void addCompactToggle(int id, int column, int y, int innerX, int width, int gap, String text) {
        this.buttonList.add(createButton(id, innerX + column * (width + gap), y, width, 18, text));
    }

    private ADM_GuiTextField createField(int x, int y, int width, String text, String hintKey) {
        final ADM_GuiTextField field = new ADM_GuiTextField(this.fontRendererObj, x, y, width, this.compactLayout ? 18 : 20)
            .setBackgroundTexture(AdmGuiTextures.TEXTFIELD_8020)
            .setFocusedBackgroundTexture(AdmGuiTextures.TEXTFIELD_HOVER_8020)
            .setHintText(hintKey.isEmpty() ? "" : I18n.format(hintKey));
        field.setMaxStringLength(2048);
        field.setText(text == null ? "" : text);
        field.setOnTextChanged(new Runnable() {

            @Override
            public void run() {
                field.setInvalid(false);
                statusMessage = "";
                statusError = false;
            }
        });
        return field;
    }

    private ADM_GuiButton createButton(int id, int x, int y, int width, int height, String text) {
        return new ADM_GuiButton(id, x, y, width, height, text).setTexture(AdmGuiTextures.BUTTON)
            .setHoverTexture(AdmGuiTextures.BUTTON_HOVER)
            .setUseHoverEffect(true)
            .setLeftDecoration(AdmGuiTextures.BUTTON_HOVER)
            .setRightDecoration(AdmGuiTextures.BUTTON_HOVER)
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
                this.searchMode = WebSearchService.nextProvider(this.searchMode);
                button.displayString = modeText();
                break;
            case BUTTON_SEARCH_FALLBACK:
                this.searchFallback = !this.searchFallback;
                button.displayString = boolText("adm.ai.settings.search_fallback", this.searchFallback);
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
                button.displayString = boolText("adm.ai.settings.voice", this.voiceEnabled);
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
        this.searchMode = WebSearchService.PROVIDER_AUTO;
        this.webSearchEnabled = false;
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
        return WebSearchService.nextProvider(current);
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
        beginValidation();
        Integer timeout = parseIntegerField(this.timeoutField);
        if (timeout == null) return;
        Integer maxTokens = parseIntegerField(this.maxTokensField);
        if (maxTokens == null) return;
        Double temperature = parseDoubleField(this.temperatureField);
        if (temperature == null) return;
        Integer searchMaxResults = parseIntegerField(this.searchMaxResultsField);
        if (searchMaxResults == null) return;
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
                timeout.intValue(),
                maxTokens.intValue(),
                temperature.doubleValue(),
                this.searchApiKeyField.getText(),
                this.searchBaseUrlField.getText(),
                searchMaxResults.intValue(),
                this.searchFallback);
            Config.saveVoiceSettings(
                this.voiceEnabled,
                Config.voicePrivacyConfirmed,
                this.voiceMode,
                Config.voiceSttBaseUrl,
                Config.voiceSttApiKey,
                Config.voiceSttModel,
                Config.voiceSttTimeoutSeconds);
            this.statusMessage = I18n.format("adm.ai.settings.saved");
            this.statusError = false;
        } catch (Exception e) {
            this.statusMessage = I18n.format("adm.ai.settings.invalid", e.getMessage());
            this.statusError = true;
        }
    }

    private void beginValidation() {
        this.statusMessage = "";
        this.statusError = false;
        ADM_GuiTextField[] fields = allFields();
        for (ADM_GuiTextField field : fields) {
            if (field != null) field.setInvalid(false);
        }
    }

    private Integer parseIntegerField(ADM_GuiTextField field) {
        try {
            return Integer.valueOf(field.getText().trim());
        } catch (NumberFormatException error) {
            rejectField(field, error);
            return null;
        }
    }

    private Double parseDoubleField(ADM_GuiTextField field) {
        try {
            return Double.valueOf(field.getText().trim());
        } catch (NumberFormatException error) {
            rejectField(field, error);
            return null;
        }
    }

    private void rejectField(ADM_GuiTextField field, Exception error) {
        this.statusMessage = I18n.format("adm.ai.settings.invalid", error.getMessage());
        this.statusError = true;
        if (this.focusedField != null && this.focusedField != field) this.focusedField.setFocused(false);
        field.setInvalid(true);
        field.setFocused(true);
        this.focusedField = field;
    }

    private ADM_GuiTextField[] allFields() {
        return new ADM_GuiTextField[] { this.apiKeyField, this.baseUrlField, this.modelField, this.timeoutField,
            this.maxTokensField, this.temperatureField, this.searchApiKeyField, this.searchBaseUrlField,
            this.searchMaxResultsField };
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
    protected void handleAdmMouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.handleAdmMouseClicked(mouseX, mouseY, mouseButton);
        focusField(this.apiKeyField, mouseX, mouseY, mouseButton);
        focusField(this.baseUrlField, mouseX, mouseY, mouseButton);
        focusField(this.modelField, mouseX, mouseY, mouseButton);
        focusField(this.timeoutField, mouseX, mouseY, mouseButton);
        focusField(this.maxTokensField, mouseX, mouseY, mouseButton);
        focusField(this.temperatureField, mouseX, mouseY, mouseButton);
        focusField(this.searchApiKeyField, mouseX, mouseY, mouseButton);
        focusField(this.searchBaseUrlField, mouseX, mouseY, mouseButton);
        focusField(this.searchMaxResultsField, mouseX, mouseY, mouseButton);
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
        this.searchApiKeyField.updateCursorCounter();
        this.searchBaseUrlField.updateCursorCounter();
        this.searchMaxResultsField.updateCursorCounter();
    }

    @Override
    protected void drawAdmScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawAdmScreen(mouseX, mouseY, partialTicks);
        drawCenteredString(
            this.fontRendererObj,
            I18n.format("adm.ai.settings.title"),
            this.width / 2,
            this.offsetY + 6,
            TEXT_COLOR);
        if (this.compactLayout) {
            drawCompactLabels();
        } else {
            drawExpandedLabels();
        }
        drawFields();
    }

    private void drawExpandedLabels() {
        int labelX = this.offsetX + 52;
        int y = this.offsetY + 64;
        drawString(this.fontRendererObj, I18n.format("adm.ai.settings.key"), labelX, y, 0xAAAAAA);
        drawString(this.fontRendererObj, I18n.format("adm.ai.settings.base"), labelX, y + 32, 0xAAAAAA);
        drawString(this.fontRendererObj, I18n.format("adm.ai.settings.model"), labelX, y + 64, 0xAAAAAA);
        drawString(this.fontRendererObj, I18n.format("adm.ai.settings.timeout"), labelX, y + 96, 0xAAAAAA);
        drawString(this.fontRendererObj, I18n.format("adm.ai.settings.tokens"), labelX, y + 128, 0xAAAAAA);
        drawString(this.fontRendererObj, I18n.format("adm.ai.settings.temperature"), labelX, y + 160, 0xAAAAAA);
        drawString(this.fontRendererObj, I18n.format("adm.ai.settings.search_key"), labelX, y + 192, 0xAAAAAA);
        drawString(this.fontRendererObj, I18n.format("adm.ai.settings.search_base"), labelX, y + 224, 0xAAAAAA);
        drawString(this.fontRendererObj, I18n.format("adm.ai.settings.search_max"), labelX, y + 256, 0xAAAAAA);
        drawString(
            this.fontRendererObj,
            I18n.format("adm.ai.settings.voice_detail", this.voiceMode),
            labelX,
            y + 344,
            0xAAAAAA);
        drawString(
            this.fontRendererObj,
            this.fontRendererObj.trimStringToWidth(
                WebSearchService.capabilityMessage(this.searchMode, this.webSearchEnabled),
                this.panelWidth - 104),
            labelX,
            y + 360,
            0xAAAAAA);
        if (!this.statusMessage.isEmpty()) {
            new UiFeedbackArea(labelX, y + 378, this.panelWidth - 104, 20)
                .draw(this.fontRendererObj, this.statusMessage, this.statusError ? 0xFF5555 : 0x55FF55);
        }
    }

    private void drawCompactLabels() {
        String[] keys = new String[] { "adm.ai.settings.key", "adm.ai.settings.base", "adm.ai.settings.model",
            "adm.ai.settings.timeout", "adm.ai.settings.tokens", "adm.ai.settings.temperature",
            "adm.ai.settings.search_key", "adm.ai.settings.search_base", "adm.ai.settings.search_max" };
        int innerX = this.offsetX + 12;
        int gap = 6;
        int columnWidth = Math.max(80, (this.panelWidth - 24 - gap * 2) / 3);
        int labelTop = this.offsetY + 22;
        for (int i = 0; i < keys.length; i++) {
            int x = innerX + (i % 3) * (columnWidth + gap);
            int y = labelTop + (i / 3) * 28;
            String label = this.fontRendererObj.trimStringToWidth(I18n.format(keys[i]), columnWidth);
            drawString(this.fontRendererObj, label, x, y, 0xAAAAAA);
        }

        int infoWidth = Math.max(20, this.panelWidth - 184);
        String capability = this.fontRendererObj
            .trimStringToWidth(WebSearchService.capabilityMessage(this.searchMode, this.webSearchEnabled), infoWidth);
        drawString(this.fontRendererObj, capability, innerX, this.offsetY + 174, 0xAAAAAA);
        if (!this.statusMessage.isEmpty()) {
            new UiFeedbackArea(innerX, this.offsetY + 185, this.panelWidth - 24, 20)
                .draw(this.fontRendererObj, this.statusMessage, this.statusError ? 0xFF5555 : 0x55FF55);
        }
    }

    private void drawFields() {
        this.apiKeyField.drawTextBox();
        this.baseUrlField.drawTextBox();
        this.modelField.drawTextBox();
        this.timeoutField.drawTextBox();
        this.maxTokensField.drawTextBox();
        this.temperatureField.drawTextBox();
        this.searchApiKeyField.drawTextBox();
        this.searchBaseUrlField.drawTextBox();
        this.searchMaxResultsField.drawTextBox();
    }

    private String providerText() {
        return I18n.format("adm.ai.settings.provider") + ": " + this.provider.displayName;
    }

    private String modeText() {
        return I18n.format("adm.ai.settings.search_engine") + ": " + this.searchMode;
    }

    private String voiceModeText() {
        String mode = I18n.format(
            Config.VOICE_STT_MODE_HTTP.equalsIgnoreCase(this.voiceMode) ? "adm.ai.settings.voice_mode.http"
                : "adm.ai.settings.voice_mode.offline");
        return I18n.format("adm.ai.settings.voice_mode", mode);
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
