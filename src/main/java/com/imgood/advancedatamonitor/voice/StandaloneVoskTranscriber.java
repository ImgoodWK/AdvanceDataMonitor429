package com.imgood.advancedatamonitor.voice;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;

import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imgood.advancedatamonitor.AdvanceDataMonitor;

public class StandaloneVoskTranscriber {

    private static final String RESULT_PREFIX = "ADM_STT_JSON\t";

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: StandaloneVoskTranscriber <modelDir> <wavFile>");
        }
        LibVosk.setLogLevel(LogLevel.INFO);
        File modelDir = new File(args[0]);
        File wavFile = new File(args[1]);
        byte[] pcm = readPcmFromWav(wavFile);
        try (Model model = new Model(modelDir.getAbsolutePath());
            Recognizer recognizer = new Recognizer(model, 16000.0F)) {
            String bestText = "";
            int offset = 0;
            while (offset < pcm.length) {
                int length = Math.min(4096, pcm.length - offset);
                byte[] chunk = new byte[length];
                System.arraycopy(pcm, offset, chunk, 0, length);
                if (recognizer.acceptWaveForm(chunk, length)) {
                    bestText = chooseBetter(bestText, extractText(recognizer.getResult(), "text"));
                } else {
                    bestText = chooseBetter(bestText, extractText(recognizer.getPartialResult(), "partial"));
                }
                offset += length;
            }
            String finalResult = recognizer.getFinalResult();
            String finalText = extractText(finalResult, "text");
            if (finalText.isEmpty() && !bestText.isEmpty()) {
                JsonObject object = new JsonObject();
                object.addProperty("text", bestText);
                finalResult = object.toString();
            }
            AdvanceDataMonitor.LOG.info(RESULT_PREFIX + finalResult);
        }
    }

    private static String chooseBetter(String current, String candidate) {
        String existing = current == null ? "" : current.trim();
        String next = candidate == null ? "" : candidate.trim();
        return next.length() >= existing.length() ? next : existing;
    }

    private static String extractText(String json, String key) {
        try {
            JsonObject object = new JsonParser().parse(json)
                .getAsJsonObject();
            return object.has(key) ? object.get(key)
                .getAsString()
                .trim() : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private static byte[] readPcmFromWav(File wavFile) throws Exception {
        byte[] wav = readAll(wavFile);
        int offset = findDataChunkOffset(wav);
        byte[] pcm = new byte[Math.max(0, wav.length - offset)];
        System.arraycopy(wav, offset, pcm, 0, pcm.length);
        return pcm;
    }

    private static byte[] readAll(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static int findDataChunkOffset(byte[] wav) {
        for (int i = 12; i + 8 < wav.length; i++) {
            if (wav[i] == 'd' && wav[i + 1] == 'a' && wav[i + 2] == 't' && wav[i + 3] == 'a') {
                return i + 8;
            }
        }
        return Math.min(44, wav.length);
    }
}
