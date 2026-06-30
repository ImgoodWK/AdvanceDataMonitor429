package com.imgood.textech.assistant.ai;

public interface ChatStreamListener {

    void onDelta(String delta);
}
