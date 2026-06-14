package com.imgood.advancedatamonitor.voice;

public interface VoiceStatusListener {

    void onVoiceStatus(String status);

    void onTranscription(String text);

    void onVoiceError(String error);
}
