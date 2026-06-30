package com.imgood.textech.voice;

import java.io.IOException;

import com.imgood.textech.Config;

public class SpeechToTextClient {

    private final HttpSpeechToTextClient httpClient = new HttpSpeechToTextClient();
    private final VoskSpeechToTextClient voskClient = new VoskSpeechToTextClient();

    public String transcribe(byte[] pcmAudio) throws IOException {
        if (Config.isHttpVoiceMode()) {
            return httpClient.transcribe(pcmAudio);
        }
        return voskClient.transcribe(pcmAudio);
    }

    public String describeMode() {
        return Config.isHttpVoiceMode() ? "HTTP STT" : "offline Vosk";
    }
}
