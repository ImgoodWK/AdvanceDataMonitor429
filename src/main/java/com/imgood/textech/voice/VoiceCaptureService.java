package com.imgood.textech.voice;

import java.io.ByteArrayOutputStream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

import com.imgood.textech.AdvanceDataMonitor;

public class VoiceCaptureService {

    private static final AudioFormat FORMAT = new AudioFormat(16000F, 16, 1, true, false);
    private volatile boolean recording;
    private TargetDataLine line;
    private String activeInputDevice = "Unknown microphone";
    private ByteArrayOutputStream buffer;
    private Thread captureThread;

    public synchronized boolean isRecording() {
        return recording;
    }

    public synchronized void start() throws Exception {
        if (recording) {
            return;
        }
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        this.line = openTargetLine(info);
        this.line.open(FORMAT);
        this.line.start();
        this.buffer = new ByteArrayOutputStream();
        this.recording = true;
        this.captureThread = new Thread(this::captureLoop, "ADM Voice Capture");
        this.captureThread.setDaemon(true);
        this.captureThread.start();
    }

    public synchronized byte[] stop() {
        if (!recording) {
            return new byte[0];
        }
        recording = false;
        if (line != null) {
            line.stop();
            line.close();
        }
        try {
            if (captureThread != null) {
                captureThread.join(1000L);
            }
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
        }
        return buffer == null ? new byte[0] : buffer.toByteArray();
    }

    public synchronized String getActiveInputDevice() {
        return activeInputDevice == null || activeInputDevice.trim()
            .isEmpty() ? "Unknown microphone" : activeInputDevice;
    }

    private TargetDataLine openTargetLine(DataLine.Info info) throws Exception {
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        for (Mixer.Info mixerInfo : mixers) {
            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            if (mixer.isLineSupported(info)) {
                TargetDataLine targetLine = (TargetDataLine) mixer.getLine(info);
                this.activeInputDevice = describeMixer(mixerInfo);
                return targetLine;
            }
        }
        TargetDataLine targetLine = (TargetDataLine) AudioSystem.getLine(info);
        this.activeInputDevice = describeLine(targetLine);
        return targetLine;
    }

    private String describeMixer(Mixer.Info mixerInfo) {
        StringBuilder builder = new StringBuilder();
        if (mixerInfo.getName() != null && !mixerInfo.getName()
            .trim()
            .isEmpty()) {
            builder.append(
                mixerInfo.getName()
                    .trim());
        }
        if (mixerInfo.getDescription() != null && !mixerInfo.getDescription()
            .trim()
            .isEmpty()
            && !mixerInfo.getDescription()
                .equalsIgnoreCase(mixerInfo.getName())) {
            if (builder.length() > 0) {
                builder.append(" - ");
            }
            builder.append(
                mixerInfo.getDescription()
                    .trim());
        }
        return builder.length() == 0 ? "Unknown microphone" : builder.toString();
    }

    private String describeLine(TargetDataLine targetLine) {
        return targetLine.getLineInfo() == null ? "Unknown microphone"
            : targetLine.getLineInfo()
                .toString();
    }

    private void captureLoop() {
        byte[] chunk = new byte[4096];
        while (recording && line != null) {
            int read = line.read(chunk, 0, chunk.length);
            if (read > 0 && buffer != null) {
                buffer.write(chunk, 0, read);
            }
        }
        AdvanceDataMonitor.LOG.debug("Voice capture stopped");
    }

    public static AudioFormat format() {
        return FORMAT;
    }
}
