package com.imgood.advancedatamonitor.client;

import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentText;

import org.lwjgl.input.Keyboard;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.Config;
import com.imgood.advancedatamonitor.gui.guiscreen.GuiAIChat;
import com.imgood.advancedatamonitor.voice.PcmAudioUtil;
import com.imgood.advancedatamonitor.voice.SpeechToTextClient;
import com.imgood.advancedatamonitor.voice.VoiceCaptureService;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;

public class VoiceAssistantKeyHandler {

    private final KeyBinding keyBinding = new KeyBinding(
        "key.advancedatamonitor.voice_assistant",
        Keyboard.KEY_V,
        "key.categories.advancedatamonitor");
    private final VoiceCaptureService captureService = new VoiceCaptureService();
    private final SpeechToTextClient speechToTextClient = new SpeechToTextClient();

    public void register() {
        ClientRegistry.registerKeyBinding(this.keyBinding);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (!this.keyBinding.isPressed()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        AdvanceDataMonitor.LOG.info(
            "[ADM Assistant] Voice key pressed: enabled={}, privacyConfirmed={}, recording={}",
            Config.voiceAssistantEnabled,
            Config.voicePrivacyConfirmed,
            captureService.isRecording());
        if (!Config.voiceAssistantEnabled) {
            notifyPlayer("Voice assistant is disabled in AI settings/config.");
            return;
        }
        if (!Config.voicePrivacyConfirmed) {
            Config.setVoicePrivacyConfirmed(true);
            notifyPlayer("Voice privacy confirmed. Press the voice key again to start recording.");
            return;
        }
        if (captureService.isRecording()) {
            stopAndTranscribe(mc);
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        try {
            captureService.start();
            String inputDevice = captureService.getActiveInputDevice();
            AdvanceDataMonitor.LOG.info("[ADM Assistant] Voice recording started with input device: {}", inputDevice);
            notifyPlayer("Using microphone: " + inputDevice);
            notifyPlayer("Listening... press the voice key again to submit.");
            GuiAIChat.updateGlobalVoiceStatus("Listening... mic: " + inputDevice);
        } catch (Exception e) {
            notifyPlayer("Voice capture failed: " + e.getMessage());
            GuiAIChat.updateGlobalVoiceStatus("Voice capture failed: " + e.getMessage());
        }
    }

    private void stopAndTranscribe(Minecraft mc) {
        byte[] audio = captureService.stop();
        PcmAudioUtil.Stats stats = PcmAudioUtil.analyze(audio);
        AdvanceDataMonitor.LOG.info(
            "[ADM Assistant] Voice recording stopped: {} bytes captured, {}.",
            audio == null ? 0 : audio.length,
            stats.describe());
        if (stats.isProbablySilent()) {
            notifyPlayer(
                "Voice input is very quiet (" + stats.describe() + "). Check the selected microphone or speak closer.");
        } else if (stats.durationSeconds(16000) < 2.0D) {
            notifyPlayer(
                "Voice clip is short (" + stats.describe()
                    + "). Speak for at least 2 seconds for offline recognition.");
        }
        String mode = speechToTextClient.describeMode();
        notifyPlayer("Transcribing voice with " + mode + "...");
        GuiAIChat.updateGlobalVoiceStatus("Transcribing voice with " + mode + "...");
        Thread worker = new Thread(() -> {
            try {
                String text = speechToTextClient.transcribe(audio);
                if (text == null || text.trim()
                    .isEmpty()) {
                    AdvanceDataMonitor.LOG.warn("[ADM Assistant] STT returned empty text.");
                    throw new IllegalStateException("Empty transcription.");
                }
                AdvanceDataMonitor.LOG.info("[ADM Assistant] STT succeeded: '{}'", sanitize(text));
                runOnClientThread(new Runnable() {

                    @Override
                    public void run() {
                        submitText(text);
                    }
                });
            } catch (Throwable e) {
                final String error = e.getMessage() == null ? e.getClass()
                    .getSimpleName() : e.getMessage();
                AdvanceDataMonitor.LOG.error("[ADM Assistant] STT failed", e);
                runOnClientThread(new Runnable() {

                    @Override
                    public void run() {
                        notifyPlayer("STT failed: " + error);
                        GuiAIChat.updateGlobalVoiceStatus("STT failed: " + error);
                    }
                });
            }
        }, "ADM Speech To Text");
        worker.setDaemon(true);
        worker.start();
    }

    private void submitText(String text) {
        Minecraft mc = Minecraft.getMinecraft();
        GuiAIChat gui;
        AdvanceDataMonitor.LOG.info("[ADM Assistant] Submitting transcribed prompt to chat: '{}'", sanitize(text));
        if (mc.currentScreen instanceof GuiAIChat) {
            gui = (GuiAIChat) mc.currentScreen;
        } else {
            gui = new GuiAIChat(mc.currentScreen);
            mc.displayGuiScreen(gui);
        }
        gui.submitAssistantPrompt(text);
    }

    private void runOnClientThread(Runnable runnable) {
        Minecraft mc = Minecraft.getMinecraft();
        try {
            Method method = mc.getClass()
                .getMethod("func_152344_a", Runnable.class);
            method.invoke(mc, runnable);
        } catch (Exception ignored) {
            runnable.run();
        }
    }

    private String sanitize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace((char) 10, ' ')
            .replace((char) 13, ' ');
    }

    private void notifyPlayer(String text) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText("[ADM AI] " + text));
        }
    }
}
