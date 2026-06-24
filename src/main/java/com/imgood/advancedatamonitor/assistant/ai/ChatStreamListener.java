package com.imgood.advancedatamonitor.assistant.ai;

public interface ChatStreamListener {

    void onDelta(String delta);
}
