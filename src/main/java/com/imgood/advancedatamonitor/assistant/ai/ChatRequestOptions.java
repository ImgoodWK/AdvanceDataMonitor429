package com.imgood.advancedatamonitor.assistant.ai;

public class ChatRequestOptions {

    public final boolean webSearchEnabled;
    public final String webSearchMode;
    public final boolean stream;

    public ChatRequestOptions(boolean webSearchEnabled, String webSearchMode, boolean stream) {
        this.webSearchEnabled = webSearchEnabled;
        this.webSearchMode = webSearchMode;
        this.stream = stream;
    }
}
