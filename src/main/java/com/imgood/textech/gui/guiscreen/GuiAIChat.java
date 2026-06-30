package com.imgood.textech.gui.guiscreen;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.assistant.AssistantController;
import com.imgood.textech.assistant.AssistantFeatureConfig;
import com.imgood.textech.assistant.AssistantFeatureConfig.FeatureEntry;
import com.imgood.textech.assistant.AssistantOrderLine;
import com.imgood.textech.assistant.CraftingCandidate;
import com.imgood.textech.assistant.ai.AiProviderProfiles;
import com.imgood.textech.assistant.ai.AiProviderProfiles.SearchCapability;
import com.imgood.textech.assistant.ai.ChatRequestOptions;
import com.imgood.textech.assistant.ai.ChatResponse;
import com.imgood.textech.assistant.ai.DeepSeekChatClient;
import com.imgood.textech.assistant.ai.DeepSeekChatClient.ChatMessage;
import com.imgood.textech.gui.custom.ADM_GuiButton;
import com.imgood.textech.gui.custom.ADM_GuiScreen;
import com.imgood.textech.gui.custom.ADM_GuiTextField;

import cpw.mods.fml.common.FMLCommonHandler;

/**
 * Display names / 显示名称:
 * - EN: AI Chat
 * - ZH: AI 对话
 * Lang keys: adm.ai.title
 */
public class GuiAIChat extends ADM_GuiScreen {

    private static final int BUTTON_SEND = 0;
    private static final int BUTTON_CLOSE = 1;
    private static final int BUTTON_CLEAR = 2;
    private static final int BUTTON_SEARCH = 3;
    private static final int BUTTON_SETTINGS = 4;
    private static final int BUTTON_CANCEL = 5;
    private static final int BUTTON_NEXT_SEARCH = 6;
    private static final int BUTTON_MENU = 7;
    private static final int MAX_CONTEXT_MESSAGES = 16;
    private static final int PANEL_WIDTH = 600;
    private static final int PANEL_HEIGHT = 450;
    private static final int CHAT_PANEL_INSET_LEFT = 18;
    private static final int CHAT_PANEL_INSET_RIGHT = 22;
    private static final int INPUT_BOTTOM_MARGIN = 38;
    private static final int INPUT_LEFT = 42;
    private static final int MENU_BUTTON_WIDTH = 54;
    private static final int MENU_BUTTON_GAP = 6;
    private static final int SCROLLBAR_WIDTH = 10;
    private static final int SCROLL_BUTTON_HEIGHT = 12;
    private static final int TEXT_COLOR = 0x00FFFF;
    private static final int TEXT_HOVER_COLOR = 0x0055FF;
    private static volatile String globalVoiceStatus = "";

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

    private static final List<ChatEntry> sharedHistory = new ArrayList<ChatEntry>();

    private final GuiScreen parent;
    private final List<ChatEntry> history = sharedHistory;
    private final List<RenderLine> displayLines = new ArrayList<RenderLine>();
    private ADM_GuiTextField inputField;
    private volatile boolean waitingForResponse;
    private volatile boolean displayDirty;
    private volatile boolean scrollToBottomRequested;
    private volatile String statusMessage = "";
    private volatile DeepSeekChatClient currentClient;
    private AssistantController assistantController;
    private boolean privacyNoticeShown;
    private int nextSearchState;
    private int scrollOffset;
    private int offsetX;
    private int offsetY;
    private boolean waitingForMenuSelection;

    public GuiAIChat(GuiScreen parent) {
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

        int inputY = this.offsetY + PANEL_HEIGHT - INPUT_BOTTOM_MARGIN;
        int sendButtonX = this.offsetX + PANEL_WIDTH - 112;
        int menuButtonX = sendButtonX - MENU_BUTTON_GAP - MENU_BUTTON_WIDTH;
        this.inputField = new ADM_GuiTextField(
            this.fontRendererObj,
            this.offsetX + INPUT_LEFT,
            inputY + 8,
            menuButtonX - (this.offsetX + INPUT_LEFT) - 8,
            20).setBackgroundTexture(TEXTFIELD_TEXTURE)
                .setFocusedBackgroundTexture(TEXTFIELD_HOVER_TEXTURE)
                .setHintText(I18n.format("adm.ai.input_hint"));
        this.inputField.setMaxStringLength(1000);
        this.inputField.setFocused(true);

        this.buttonList
            .add(createButton(BUTTON_SEARCH, this.offsetX + 24, this.offsetY + 12, 78, 20, buildSearchButtonText()));
        this.buttonList.add(
            createButton(
                BUTTON_NEXT_SEARCH,
                this.offsetX + 108,
                this.offsetY + 12,
                92,
                20,
                buildNextSearchButtonText()));
        this.buttonList.add(
            createButton(
                BUTTON_SETTINGS,
                this.offsetX + PANEL_WIDTH - 164,
                this.offsetY + 12,
                46,
                20,
                I18n.format("adm.ai.settings")));
        this.buttonList.add(
            createButton(
                BUTTON_CANCEL,
                this.offsetX + PANEL_WIDTH - 112,
                this.offsetY + 12,
                46,
                20,
                I18n.format("adm.ai.cancel")));
        this.buttonList
            .add(createButton(BUTTON_MENU, menuButtonX, inputY, MENU_BUTTON_WIDTH, 20, I18n.format("adm.ai.menu")));
        this.buttonList.add(createButton(BUTTON_SEND, sendButtonX, inputY, 46, 20, I18n.format("adm.ai.send")));
        this.buttonList.add(
            createButton(BUTTON_CLEAR, this.offsetX + PANEL_WIDTH - 62, inputY, 46, 20, I18n.format("adm.ai.clear")));
        this.buttonList.add(
            createButton(
                BUTTON_CLOSE,
                this.offsetX + PANEL_WIDTH - 62,
                this.offsetY + 12,
                46,
                20,
                I18n.format("adm.ai.close")));
        this.statusMessage = globalVoiceStatus == null || globalVoiceStatus.isEmpty() ? buildConfigStatus()
            : globalVoiceStatus;
        this.assistantController = new AssistantController(this);
        this.scrollToBottomRequested = true;
        rebuildDisplayLines();
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
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        this.inputField.updateCursorCounter();
        if (this.displayDirty) {
            this.displayDirty = false;
            rebuildDisplayLines();
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case BUTTON_SEND:
                sendMessage();
                break;
            case BUTTON_CLOSE:
                this.mc.displayGuiScreen(this.parent);
                break;
            case BUTTON_CLEAR:
                synchronized (this.history) {
                    this.history.clear();
                }
                this.scrollOffset = 0;
                this.statusMessage = buildConfigStatus();
                rebuildDisplayLines();
                break;
            case BUTTON_SEARCH:
                if (Config.aiWebSearchEnabled || ensurePrivacyConfirmed()) {
                    Config.toggleAiWebSearchEnabled();
                    button.displayString = buildSearchButtonText();
                    this.statusMessage = buildConfigStatus();
                }
                break;
            case BUTTON_NEXT_SEARCH:
                this.nextSearchState = (this.nextSearchState + 1) % 3;
                button.displayString = buildNextSearchButtonText();
                this.statusMessage = buildConfigStatus();
                break;
            case BUTTON_SETTINGS:
                this.mc.displayGuiScreen(new GuiAISettings(this));
                break;
            case BUTTON_CANCEL:
                cancelRequest();
                break;
            case BUTTON_MENU:
                showFeatureMenu();
                break;
            default:
                break;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            sendMessage();
            return;
        }
        this.inputField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        this.inputField.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton == 0 && isChatScrollbarVisible()) {
            int panelX = this.offsetX + CHAT_PANEL_INSET_LEFT;
            int panelY = this.offsetY + 52;
            int panelW = PANEL_WIDTH - CHAT_PANEL_INSET_LEFT - CHAT_PANEL_INSET_RIGHT;
            int panelH = PANEL_HEIGHT - 100;
            int scrollX = panelX + panelW - SCROLLBAR_WIDTH - 3;
            if (mouseX >= scrollX && mouseX <= scrollX + SCROLLBAR_WIDTH) {
                if (mouseY >= panelY + 3 && mouseY <= panelY + 3 + SCROLL_BUTTON_HEIGHT) {
                    this.scrollOffset = 0;
                } else if (mouseY >= panelY + panelH - SCROLL_BUTTON_HEIGHT - 3 && mouseY <= panelY + panelH - 3) {
                    this.scrollOffset = maxScrollOffset();
                }
            }
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel > 0) {
            this.scrollOffset = Math.max(0, this.scrollOffset - 3);
        } else if (wheel < 0) {
            this.scrollOffset = Math.min(maxScrollOffset(), this.scrollOffset + 3);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawCenteredString(
            this.fontRendererObj,
            I18n.format("adm.ai.title"),
            this.width / 2,
            this.offsetY + 16,
            TEXT_COLOR);
        drawString(
            this.fontRendererObj,
            this.statusMessage,
            this.offsetX + 24,
            this.offsetY + 36,
            waitingForResponse ? 0xFFFF55 : 0xAAAAAA);
        drawChatPanel();
        this.inputField.drawTextBox();
    }

    private void drawChatPanel() {
        int panelX = this.offsetX + CHAT_PANEL_INSET_LEFT;
        int panelY = this.offsetY + 52;
        int panelW = PANEL_WIDTH - CHAT_PANEL_INSET_LEFT - CHAT_PANEL_INSET_RIGHT;
        int panelH = PANEL_HEIGHT - 100;
        drawRect(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xAA00AAAA);
        drawRect(panelX, panelY, panelX + panelW, panelY + panelH, 0xB0000000);

        int y = panelY + 6;
        int contentBottom = panelY + panelH - 6;
        synchronized (this.displayLines) {
            int start = Math.max(0, Math.min(this.scrollOffset, maxScrollOffsetUnsafe()));
            for (int i = start; i < this.displayLines.size(); i++) {
                RenderLine line = this.displayLines.get(i);
                int lineHeight = line.height();
                if (y + lineHeight > contentBottom) {
                    break;
                }
                if (line.candidateCells != null) {
                    drawCandidateCells(panelX + 8, y, line.candidateCells);
                } else if (line.codeBlock) {
                    drawRect(panelX + 4, y - 1, panelX + panelW - SCROLLBAR_WIDTH - 8, y + 10, 0x80303030);
                    drawString(this.fontRendererObj, line.text, panelX + 10, y, line.color);
                } else {
                    drawString(this.fontRendererObj, line.text, panelX + 8 + line.indent, y, line.color);
                }
                y += lineHeight;
            }
        }
        drawChatScrollbar(panelX, panelY, panelW, panelH);
    }

    public static void updateGlobalVoiceStatus(String status) {
        globalVoiceStatus = status == null ? "" : status;
    }

    public void submitAssistantPrompt(String prompt) {
        if (this.inputField != null) {
            this.inputField.setText(prompt == null ? "" : prompt);
        }
        sendMessage();
    }

    public void addAssistantMessage(String message) {
        synchronized (this.history) {
            this.history.add(ChatEntry.text("assistant", message == null ? "" : message));
        }
        requestScrollToBottom();
        this.statusMessage = I18n.format("adm.ai.ready");
        rebuildDisplayLines();
    }

    public void addAssistantCandidatesMessage(String message, List<CraftingCandidate> candidates) {
        synchronized (this.history) {
            this.history.add(ChatEntry.candidates(message == null ? "" : message, candidates));
        }
        requestScrollToBottom();
        this.statusMessage = I18n.format("adm.ai.ready");
        rebuildDisplayLines();
    }

    public void addAssistantBatchMessage(String message, List<AssistantOrderLine> lines) {
        synchronized (this.history) {
            this.history.add(ChatEntry.batch(message == null ? "" : message, lines));
        }
        requestScrollToBottom();
        this.statusMessage = I18n.format("adm.ai.ready");
        rebuildDisplayLines();
    }

    private void showFeatureMenu() {
        String locale = currentLocale();
        String menuText = AssistantFeatureConfig.buildFeatureMenu(locale);
        synchronized (this.history) {
            this.history.add(ChatEntry.text("assistant", menuText));
        }
        this.waitingForMenuSelection = true;
        requestScrollToBottom();
        rebuildDisplayLines();
    }

    private void sendMessage() {
        if (this.waitingForResponse) {
            return;
        }
        String prompt = this.inputField.getText()
            .trim();
        if (prompt.isEmpty()) {
            return;
        }
        // Check if user is selecting a menu item by number
        if (this.waitingForMenuSelection) {
            try {
                int menuIndex = Integer.parseInt(prompt);
                FeatureEntry feature = AssistantFeatureConfig.getFeatureByMenuIndex(menuIndex);
                if (feature != null) {
                    this.waitingForMenuSelection = false;
                    this.assistantController.setSelectedFeature(feature.key);
                    String locale = currentLocale();
                    String selectedMsg = locale.startsWith("zh") ? "已选择功能："
                        + (feature.displayName.containsKey("zh_CN") ? feature.displayName.get("zh_CN") : feature.key)
                        : "Selected feature: "
                            + (feature.displayName.containsKey("en_US") ? feature.displayName.get("en_US")
                                : feature.key);
                    selectedMsg += locale.startsWith("zh") ? "。请输入具体内容：" : ". Enter your specific content:";
                    addUserMessage(prompt);
                    synchronized (this.history) {
                        this.history.add(ChatEntry.text("assistant", selectedMsg));
                    }
                    this.inputField.setText("");
                    requestScrollToBottom();
                    rebuildDisplayLines();
                    this.statusMessage = selectedMsg;
                    return;
                }
            } catch (NumberFormatException ignored) {}
            // Invalid selection, fall through to normal processing
            this.waitingForMenuSelection = false;
            this.assistantController.clearSelectedFeature();
        }
        if (this.assistantController != null && this.assistantController.handlePrompt(prompt, currentLocale())) {
            this.inputField.setText("");
            addUserMessage(prompt);
            rebuildDisplayLines();
            return;
        }
        startNormalAiChat(prompt, true);
    }

    public void startNormalAiChatAfterAssistantParse(String prompt) {
        sendNormalAiMessage(prompt, false);
    }

    public void sendNormalAiMessage(String prompt, boolean addUserMessage) {
        startNormalAiChat(prompt, addUserMessage);
    }

    private void startNormalAiChat(String prompt, boolean addUserMessage) {
        if (this.waitingForResponse) {
            return;
        }
        if (!Config.aiNetworkEnabled) {
            this.statusMessage = I18n.format("adm.ai.network_disabled");
            synchronized (this.history) {
                this.history.add(ChatEntry.text("assistant", I18n.format("adm.ai.network_disabled_detail")));
            }
            requestScrollToBottom();
            rebuildDisplayLines();
            return;
        }
        boolean requestSearch = effectiveNextSearchEnabled();
        if (requestSearch && !ensurePrivacyConfirmed()) {
            return;
        }
        if (this.inputField != null) {
            this.inputField.setText("");
        }
        if (addUserMessage) {
            addUserMessage(prompt);
        }
        final ChatRequestOptions options = new ChatRequestOptions(
            requestSearch,
            effectiveNextSearchMode(),
            Config.aiStreamingEnabled);
        this.nextSearchState = 0;
        updateButtonText(BUTTON_NEXT_SEARCH, buildNextSearchButtonText());
        this.statusMessage = I18n.format("adm.ai.waiting");
        this.waitingForResponse = true;
        rebuildDisplayLines();

        Thread worker = new Thread(new Runnable() {

            @Override
            public void run() {
                requestAnswer(options);
            }
        }, "ADM AI Chat");
        worker.setDaemon(true);
        worker.start();
    }

    private void addUserMessage(String prompt) {
        synchronized (this.history) {
            this.history.add(ChatEntry.text("user", prompt == null ? "" : prompt));
        }
        requestScrollToBottom();
    }

    private void requestScrollToBottom() {
        this.scrollToBottomRequested = true;
    }

    private void requestAnswer(ChatRequestOptions options) {
        int streamingIndex = -1;
        StringBuilder streamingContent = new StringBuilder();
        try {
            List<ChatMessage> requestMessages = buildRequestMessages();
            DeepSeekChatClient client = new DeepSeekChatClient();
            this.currentClient = client;
            if (options.stream) {
                synchronized (this.history) {
                    streamingIndex = this.history.size();
                    this.history.add(ChatEntry.text("assistant", ""));
                }
                requestScrollToBottom();
                final int assistantIndex = streamingIndex;
                ChatResponse response = client
                    .chat(requestMessages, options, new com.imgood.textech.assistant.ai.ChatStreamListener() {

                        @Override
                        public void onDelta(String delta) {
                            synchronized (GuiAIChat.this.history) {
                                streamingContent.append(delta);
                                if (assistantIndex >= 0 && assistantIndex < GuiAIChat.this.history.size()) {
                                    GuiAIChat.this.history
                                        .set(assistantIndex, ChatEntry.text("assistant", streamingContent.toString()));
                                }
                            }
                            GuiAIChat.this.requestScrollToBottom();
                            GuiAIChat.this.displayDirty = true;
                        }
                    });
                synchronized (this.history) {
                    if (assistantIndex >= 0 && assistantIndex < this.history.size()) {
                        this.history.set(assistantIndex, ChatEntry.text("assistant", response.contentWithSources()));
                    }
                }
                requestScrollToBottom();
            } else {
                ChatResponse response = client.chat(requestMessages, options, null);
                synchronized (this.history) {
                    this.history.add(ChatEntry.text("assistant", response.contentWithSources()));
                }
                requestScrollToBottom();
            }
            this.statusMessage = I18n.format("adm.ai.ready");
        } catch (Exception e) {
            synchronized (this.history) {
                this.history.add(ChatEntry.text("assistant", I18n.format("adm.ai.error", e.getMessage())));
            }
            requestScrollToBottom();
            this.statusMessage = I18n.format("adm.ai.failed");
        } finally {
            this.currentClient = null;
            this.waitingForResponse = false;
            this.displayDirty = true;
        }
    }

    private void cancelRequest() {
        DeepSeekChatClient client = this.currentClient;
        if (client != null) {
            client.cancel();
            this.statusMessage = I18n.format("adm.ai.cancelled");
        }
    }

    private boolean ensurePrivacyConfirmed() {
        if (Config.aiPrivacyConfirmed) {
            return true;
        }
        if (!this.privacyNoticeShown) {
            this.privacyNoticeShown = true;
            synchronized (this.history) {
                this.history.add(ChatEntry.text("assistant", I18n.format("adm.ai.privacy_notice")));
            }
            requestScrollToBottom();
            this.statusMessage = I18n.format("adm.ai.privacy_waiting");
            rebuildDisplayLines();
            return false;
        }
        Config.setAiPrivacyConfirmed(true);
        synchronized (this.history) {
            this.history.add(ChatEntry.text("assistant", I18n.format("adm.ai.privacy_confirmed")));
        }
        requestScrollToBottom();
        rebuildDisplayLines();
        return true;
    }

    private boolean effectiveNextSearchEnabled() {
        if (this.nextSearchState == 1) {
            return true;
        }
        if (this.nextSearchState == 2) {
            return false;
        }
        return Config.aiWebSearchEnabled;
    }

    private String effectiveNextSearchMode() {
        if (this.nextSearchState == 2) {
            return AiProviderProfiles.MODE_OFF;
        }
        return Config.aiWebSearchMode;
    }

    private void updateButtonText(int id, String text) {
        for (Object obj : this.buttonList) {
            GuiButton button = (GuiButton) obj;
            if (button.id == id) {
                button.displayString = text;
                return;
            }
        }
    }

    private List<ChatMessage> buildRequestMessages() {
        List<ChatMessage> requestMessages = new ArrayList<>();
        requestMessages.add(new ChatMessage("system", buildSystemPrompt()));
        synchronized (this.history) {
            int start = Math.max(0, this.history.size() - MAX_CONTEXT_MESSAGES);
            for (int i = start; i < this.history.size(); i++) {
                requestMessages.add(
                    this.history.get(i)
                        .toChatMessage());
            }
        }
        return requestMessages;
    }

    private String buildSystemPrompt() {
        SearchCapability capability = AiProviderProfiles.currentSearchCapability();
        String locale = currentLocale();
        String prompt = "You are an assistant inside a Minecraft GTNH data monitor mod. " + languageInstruction(locale)
            + " Answer concisely and helpfully. Markdown is supported in the chat UI. "
            + AssistantFeatureConfig.buildCapabilityOverview(locale);
        if (capability.enabled) {
            return prompt
                + " Web search is enabled for this request. When you use current information, cite sources or clearly name where the information came from. If search is unavailable, say so instead of guessing.";
        }
        return prompt
            + " Web search is disabled or unsupported for this model. Do not claim to have checked live web results.";
    }

    private String languageInstruction(String locale) {
        String normalized = locale == null ? ""
            : locale.trim()
                .toLowerCase();
        if (normalized.startsWith("zh")) {
            return "请使用简体中文回复，除非用户明确要求其他语言。当前客户端语言是 " + locale + ".";
        }
        return "Respond in the client's configured language (" + locale
            + "), unless the user explicitly asks for another language.";
    }

    private String currentLocale() {
        try {
            String locale = FMLCommonHandler.instance()
                .getCurrentLanguage();
            return locale == null || locale.trim()
                .isEmpty() ? "en_US" : locale;
        } catch (Throwable ignored) {
            return "en_US";
        }
    }

    private void rebuildDisplayLines() {
        synchronized (this.displayLines) {
            this.displayLines.clear();
            synchronized (this.history) {
                if (this.history.isEmpty()) {
                    addMarkdownLines(I18n.format("adm.ai.empty"), 0xAAAAAA, false);
                } else {
                    for (ChatEntry message : this.history) {
                        boolean assistant = "assistant".equals(message.role);
                        String speaker = assistant ? I18n.format("adm.ai.assistant") : I18n.format("adm.ai.you");
                        this.displayLines
                            .add(new RenderLine(speaker + ":", assistant ? TEXT_COLOR : 0x55FF55, 0, false));
                        if (message.hasCandidates()) {
                            addCandidateRenderLines(message.content, message.candidates);
                        } else if (message.hasBatchLines()) {
                            addBatchRenderLines(message.content, message.batchLines);
                        } else {
                            addMarkdownLines(message.content, 0xFFFFFF, assistant);
                        }
                        this.displayLines.add(new RenderLine("", 0xFFFFFF, 0, false));
                    }
                }
            }
            this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, maxScrollOffsetUnsafe()));
            if (this.scrollToBottomRequested || this.waitingForResponse || this.displayDirty) {
                this.scrollOffset = maxScrollOffsetUnsafe();
                this.scrollToBottomRequested = false;
            }
        }
    }

    private void addCandidateRenderLines(String message, List<CraftingCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            addMarkdownLines(message, 0xFFFFFF, false);
            return;
        }
        addMarkdownLines(firstLine(message), 0xFFFFFF, false);
        int perLine = 3;
        for (int i = 0; i < candidates.size(); i += perLine) {
            List<CandidateCell> cells = new ArrayList<CandidateCell>();
            for (int j = 0; j < perLine && i + j < candidates.size(); j++) {
                CraftingCandidate candidate = candidates.get(i + j);
                if (candidate != null) {
                    cells.add(
                        new CandidateCell(
                            candidate.index,
                            candidate.toItemStack(),
                            candidate.displayName,
                            candidate.amount));
                }
            }
            this.displayLines.add(RenderLine.candidateCells(cells));
        }
    }

    private void addBatchRenderLines(String message, List<AssistantOrderLine> lines) {
        if (lines == null || lines.isEmpty()) {
            addMarkdownLines(message, 0xFFFFFF, false);
            return;
        }
        addMarkdownLines(firstLine(message), 0xFFFFFF, false);
        int perLine = 2;
        for (int i = 0; i < lines.size(); i += perLine) {
            List<CandidateCell> cells = new ArrayList<CandidateCell>();
            for (int j = 0; j < perLine && i + j < lines.size(); j++) {
                AssistantOrderLine line = lines.get(i + j);
                if (line == null) {
                    continue;
                }
                CraftingCandidate candidate = line.selectedOrFirstCandidate();
                ItemStack stack = candidate == null ? null : candidate.toItemStack();
                String name = candidate == null ? line.target : line.target + " -> " + candidate.displayName;
                cells.add(new CandidateCell(line.lineIndex, stack, name, line.amount));
            }
            this.displayLines.add(RenderLine.candidateCells(cells));
        }
    }

    private void addMarkdownLines(String text, int defaultColor, boolean parseMarkdown) {
        String normalized = (text == null ? "" : text).replace("\r\n", "\n")
            .replace('\r', '\n');
        boolean inCodeBlock = false;
        for (String rawLine : normalized.split("\n", -1)) {
            String line = rawLine;
            if (parseMarkdown && line.trim()
                .startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                continue;
            }
            if (inCodeBlock) {
                addWrappedRenderLine(line, 0xDDDDDD, 0, true);
                continue;
            }
            MarkdownStyle style = parseMarkdown ? parseMarkdownStyle(line, defaultColor)
                : new MarkdownStyle(line, defaultColor, 0);
            addWrappedRenderLine(style.text, style.color, style.indent, false);
        }
    }

    private MarkdownStyle parseMarkdownStyle(String line, int defaultColor) {
        String trimmed = line.trim();
        int indent = Math.max(0, line.indexOf(trimmed));
        int color = defaultColor;
        if (trimmed.startsWith("### ")) {
            trimmed = "§l" + trimmed.substring(4);
            color = 0x55FFFF;
        } else if (trimmed.startsWith("## ")) {
            trimmed = "§l" + trimmed.substring(3);
            color = 0x55FFFF;
        } else if (trimmed.startsWith("# ")) {
            trimmed = "§l" + trimmed.substring(2);
            color = 0x00FFFF;
        } else if (trimmed.startsWith("> ")) {
            trimmed = "| " + trimmed.substring(2);
            color = 0xAAAAAA;
            indent += 6;
        } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            trimmed = "- " + trimmed.substring(2);
            indent += 6;
        } else if (isNumberedList(trimmed)) {
            indent += 6;
        }
        trimmed = applyInlineMarkdown(trimmed);
        return new MarkdownStyle(trimmed, color, indent);
    }

    private boolean isNumberedList(String text) {
        int dot = text.indexOf('.');
        if (dot <= 0 || dot > 3 || dot + 1 >= text.length() || text.charAt(dot + 1) != ' ') {
            return false;
        }
        for (int i = 0; i < dot; i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private String applyInlineMarkdown(String text) {
        String result = replacePairedMarkdown(text, "`", "§7", "§r");
        result = replacePairedMarkdown(result, "**", "§l", "§r");
        return replacePairedMarkdown(result, "__", "§l", "§r");
    }

    private String replacePairedMarkdown(String text, String marker, String openFormat, String closeFormat) {
        StringBuilder builder = new StringBuilder();
        int index = 0;
        boolean open = false;
        while (index < text.length()) {
            if (text.startsWith(marker, index)) {
                builder.append(open ? closeFormat : openFormat);
                open = !open;
                index += marker.length();
            } else {
                builder.append(text.charAt(index));
                index++;
            }
        }
        if (open) {
            builder.append(marker);
        }
        return builder.toString();
    }

    private void addWrappedRenderLine(String text, int color, int indent, boolean codeBlock) {
        int panelW = PANEL_WIDTH - CHAT_PANEL_INSET_LEFT - CHAT_PANEL_INSET_RIGHT;
        int maxWidth = panelW - 42 - indent;
        List<String> wrapped = this.fontRendererObj.listFormattedStringToWidth(text.isEmpty() ? " " : text, maxWidth);
        for (String line : wrapped) {
            this.displayLines.add(new RenderLine(line, color, indent, codeBlock));
        }
    }

    private String firstLine(String text) {
        String normalized = (text == null ? "" : text).replace("\r\n", "\n")
            .replace('\r', '\n');
        int newline = normalized.indexOf('\n');
        return newline < 0 ? normalized : normalized.substring(0, newline);
    }

    private void drawChatScrollbar(int panelX, int panelY, int panelW, int panelH) {
        if (!isChatScrollbarVisible()) {
            return;
        }
        int scrollX = panelX + panelW - SCROLLBAR_WIDTH - 3;
        int topButtonY = panelY + 3;
        int bottomButtonY = panelY + panelH - SCROLL_BUTTON_HEIGHT - 3;
        drawRect(scrollX, topButtonY, scrollX + SCROLLBAR_WIDTH, topButtonY + SCROLL_BUTTON_HEIGHT, 0xFF006060);
        drawCenteredString(this.fontRendererObj, "^", scrollX + SCROLLBAR_WIDTH / 2, topButtonY + 2, 0xFFFFFF);
        drawRect(scrollX, bottomButtonY, scrollX + SCROLLBAR_WIDTH, bottomButtonY + SCROLL_BUTTON_HEIGHT, 0xFF006060);
        drawCenteredString(this.fontRendererObj, "v", scrollX + SCROLLBAR_WIDTH / 2, bottomButtonY + 2, 0xFFFFFF);

        int trackTop = topButtonY + SCROLL_BUTTON_HEIGHT + 2;
        int trackBottom = bottomButtonY - 2;
        int trackHeight = Math.max(1, trackBottom - trackTop);
        drawRect(scrollX + 2, trackTop, scrollX + SCROLLBAR_WIDTH - 2, trackBottom, 0x80303030);

        int maxScroll = maxScrollOffset();
        int totalHeight;
        synchronized (this.displayLines) {
            totalHeight = totalDisplayHeightUnsafe();
        }
        int thumbHeight = Math.max(12, trackHeight * chatContentHeight() / Math.max(chatContentHeight(), totalHeight));
        int thumbTravel = Math.max(0, trackHeight - thumbHeight);
        int thumbY = trackTop + (maxScroll <= 0 ? 0 : this.scrollOffset * thumbTravel / maxScroll);
        drawRect(scrollX + 1, thumbY, scrollX + SCROLLBAR_WIDTH - 1, thumbY + thumbHeight, 0xFF00AAAA);
    }

    private boolean isChatScrollbarVisible() {
        synchronized (this.displayLines) {
            return maxScrollOffsetUnsafe() > 0;
        }
    }

    private int maxScrollOffset() {
        synchronized (this.displayLines) {
            return maxScrollOffsetUnsafe();
        }
    }

    private int maxScrollOffsetUnsafe() {
        int availableHeight = chatContentHeight();
        int usedHeight = 0;
        for (int i = this.displayLines.size() - 1; i >= 0; i--) {
            int lineHeight = this.displayLines.get(i)
                .height();
            if (usedHeight + lineHeight > availableHeight) {
                return Math.max(0, i + 1);
            }
            usedHeight += lineHeight;
        }
        return 0;
    }

    private int totalDisplayHeightUnsafe() {
        int total = 0;
        for (RenderLine line : this.displayLines) {
            total += line.height();
        }
        return total;
    }

    private int chatContentHeight() {
        return PANEL_HEIGHT - 112;
    }

    private void drawCandidateCells(int x, int y, List<CandidateCell> cells) {
        if (cells == null || cells.isEmpty()) {
            return;
        }
        int panelW = PANEL_WIDTH - CHAT_PANEL_INSET_LEFT - CHAT_PANEL_INSET_RIGHT;
        int cellWidth = (panelW - 26) / 3;
        for (int i = 0; i < cells.size(); i++) {
            CandidateCell cell = cells.get(i);
            int cellX = x + i * cellWidth;
            drawRect(cellX - 2, y - 2, cellX + cellWidth - 4, y + 18, 0x50303030);
            if (cell.stack != null) {
                RenderHelper.enableGUIStandardItemLighting();
                this.itemRender.renderItemAndEffectIntoGUI(
                    this.fontRendererObj,
                    this.mc.getTextureManager(),
                    cell.stack,
                    cellX,
                    y);
                RenderHelper.disableStandardItemLighting();
            }
            String label = cell.index + ". " + cell.name + " x" + cell.amount;
            int textWidth = cellWidth - 24;
            this.fontRendererObj
                .drawString(this.fontRendererObj.trimStringToWidth(label, textWidth), cellX + 20, y + 5, 0xFFFFFF);
        }
    }

    private String buildConfigStatus() {
        if (!Config.aiNetworkEnabled) {
            return I18n.format("adm.ai.network_disabled");
        }
        if (Config.getAiApiKey()
            .isEmpty()) {
            return I18n.format("adm.ai.no_key");
        }
        SearchCapability capability = AiProviderProfiles.currentSearchCapability();
        return I18n.format("adm.ai.configured", Config.aiModel) + " | "
            + capability.profile.displayName
            + " | "
            + I18n.format(capability.enabled ? "adm.ai.search_on" : "adm.ai.search_off")
            + " ("
            + capability.mode
            + ")";
    }

    private String buildSearchButtonText() {
        return I18n.format(Config.aiWebSearchEnabled ? "adm.ai.search_on" : "adm.ai.search_off");
    }

    private String buildNextSearchButtonText() {
        if (this.nextSearchState == 1) {
            return I18n.format("adm.ai.next_search_on");
        }
        if (this.nextSearchState == 2) {
            return I18n.format("adm.ai.next_search_off");
        }
        return I18n.format("adm.ai.next_search_auto");
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private static class RenderLine {

        private final String text;
        private final int color;
        private final int indent;
        private final boolean codeBlock;
        private final List<CandidateCell> candidateCells;

        private RenderLine(String text, int color, int indent, boolean codeBlock) {
            this.text = text;
            this.color = color;
            this.indent = indent;
            this.codeBlock = codeBlock;
            this.candidateCells = null;
        }

        private RenderLine(List<CandidateCell> candidateCells) {
            this.text = "";
            this.color = 0xFFFFFF;
            this.indent = 0;
            this.codeBlock = false;
            this.candidateCells = candidateCells;
        }

        private int height() {
            return this.candidateCells == null ? 10 : 20;
        }

        private static RenderLine candidateCells(List<CandidateCell> candidateCells) {
            return new RenderLine(candidateCells);
        }
    }

    private static class CandidateCell {

        private final int index;
        private final ItemStack stack;
        private final String name;
        private final long amount;

        private CandidateCell(int index, ItemStack stack, String name, long amount) {
            this.index = index;
            this.stack = stack;
            this.name = name == null ? "" : name;
            this.amount = amount;
        }
    }

    private static class ChatEntry {

        private final String role;
        private final String content;
        private final List<CraftingCandidate> candidates;
        private final List<AssistantOrderLine> batchLines;

        private ChatEntry(String role, String content, List<CraftingCandidate> candidates,
            List<AssistantOrderLine> batchLines) {
            this.role = role == null ? "assistant" : role;
            this.content = content == null ? "" : content;
            this.candidates = candidates == null ? null : new ArrayList<CraftingCandidate>(candidates);
            this.batchLines = batchLines == null ? null : new ArrayList<AssistantOrderLine>(batchLines);
        }

        private static ChatEntry text(String role, String content) {
            return new ChatEntry(role, content, null, null);
        }

        private static ChatEntry candidates(String content, List<CraftingCandidate> candidates) {
            return new ChatEntry("assistant", content, candidates, null);
        }

        private static ChatEntry batch(String content, List<AssistantOrderLine> lines) {
            return new ChatEntry("assistant", content, null, lines);
        }

        private boolean hasCandidates() {
            return this.candidates != null && !this.candidates.isEmpty();
        }

        private boolean hasBatchLines() {
            return this.batchLines != null && !this.batchLines.isEmpty();
        }

        private ChatMessage toChatMessage() {
            return new ChatMessage(this.role, this.content);
        }
    }

    private static class MarkdownStyle {

        private final String text;
        private final int color;
        private final int indent;

        private MarkdownStyle(String text, int color, int indent) {
            this.text = text;
            this.color = color;
            this.indent = indent;
        }
    }
}
