package com.imgood.advancedatamonitor.voice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imgood.advancedatamonitor.Config;

public class HttpSpeechToTextClient {

    public String transcribe(byte[] pcmAudio) throws IOException {
        if (pcmAudio == null || pcmAudio.length == 0) {
            throw new IOException("No voice data captured.");
        }
        String baseUrl = Config.voiceSttBaseUrl == null || Config.voiceSttBaseUrl.trim()
            .isEmpty() ? Config.aiApiBaseUrl : Config.voiceSttBaseUrl.trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String apiKey = Config.getVoiceSttApiKey();
        if (apiKey.isEmpty() && !isLocalBaseUrl(baseUrl)) {
            throw new IOException("Missing STT API key.");
        }
        URL url = new URL(baseUrl + "/v1/audio/transcriptions");
        String boundary = "ADMVoice" + System.currentTimeMillis();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        int timeout = Math.max(5, Config.voiceSttTimeoutSeconds) * 1000;
        connection.setConnectTimeout(timeout);
        connection.setReadTimeout(timeout);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        if (!apiKey.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        byte[] wav = WavEncoder.encodePcm16Mono(pcmAudio, 16000);
        try (OutputStream out = connection.getOutputStream()) {
            writeField(out, boundary, "model", Config.voiceSttModel);
            writeFile(out, boundary, "file", "voice.wav", "audio/wav", wav);
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        }
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        String response = read(stream);
        connection.disconnect();
        if (code < 200 || code >= 300) {
            throw new IOException("STT failed (HTTP " + code + "): " + response);
        }
        JsonObject json = new JsonParser().parse(response)
            .getAsJsonObject();
        return json.has("text") ? json.get("text")
            .getAsString()
            .trim() : response.trim();
    }

    private boolean isLocalBaseUrl(String baseUrl) {
        try {
            String host = new URL(baseUrl).getHost();
            return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                || "::1".equals(host)
                || "[::1]".equals(host);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void writeField(OutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private void writeFile(OutputStream out, String boundary, String name, String fileName, String contentType,
        byte[] body) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(
            ("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + fileName + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private String read(InputStream stream) throws IOException {
        if (stream == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = stream.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
