package com.imgood.textech.voice;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;

public class VoskSpeechToTextClient {

    private static final String RESULT_PREFIX = "ADM_STT_JSON\t";
    private final EmbeddedVoiceModelManager modelManager = new EmbeddedVoiceModelManager();

    public String transcribe(byte[] pcmAudio) throws IOException {
        if (pcmAudio == null || pcmAudio.length == 0) {
            throw new IOException("No voice data captured.");
        }
        PcmAudioUtil.Stats before = PcmAudioUtil.analyze(pcmAudio);
        byte[] normalizedAudio = PcmAudioUtil.normalize(pcmAudio);
        PcmAudioUtil.Stats after = PcmAudioUtil.analyze(normalizedAudio);
        AdvanceDataMonitor.LOG
            .info("[ADM Assistant] Offline Vosk audio stats before={}, after={}", before.describe(), after.describe());
        File modelDirectory = modelManager.resolveModelDirectory();
        File wavFile = writeTempWav(normalizedAudio);
        try {
            return transcribeWithWorker(modelDirectory, wavFile);
        } finally {
            if (!wavFile.delete()) {
                wavFile.deleteOnExit();
            }
        }
    }

    private String transcribeWithWorker(File modelDirectory, File wavFile) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        command.add("-Dfile.encoding=UTF-8");
        command.add("-cp");
        command.add(workerClasspath());
        command.add(StandaloneVoskTranscriber.class.getName());
        command.add(modelDirectory.getAbsolutePath());
        command.add(wavFile.getAbsolutePath());

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            boolean finished = waitFor(process, reader, output);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Offline Vosk timed out after " + Config.voiceSttTimeoutSeconds + " seconds.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
            process.destroyForcibly();
            throw new IOException("Offline Vosk was interrupted.", e);
        }
        int exit = process.exitValue();
        String json = findResultJson(output.toString());
        if (exit != 0) {
            throw new IOException("Offline Vosk worker failed (exit " + exit + "): " + summarize(output.toString()));
        }
        if (json.isEmpty()) {
            throw new IOException("Offline Vosk worker returned no transcription: " + summarize(output.toString()));
        }
        String text = parseText(json);
        AdvanceDataMonitor.LOG.info("[ADM Assistant] Offline Vosk result: '{}'", sanitize(text));
        return text;
    }

    private boolean waitFor(Process process, BufferedReader reader, StringBuilder output)
        throws IOException, InterruptedException {
        long deadline = System.currentTimeMillis() + Math.max(5, Config.voiceSttTimeoutSeconds) * 1000L;
        while (process.isAlive()) {
            drain(reader, output);
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0L) {
                return false;
            }
            process.waitFor(Math.min(200L, remaining), TimeUnit.MILLISECONDS);
        }
        drain(reader, output);
        return true;
    }

    private void drain(BufferedReader reader, StringBuilder output) throws IOException {
        while (reader.ready()) {
            output.append(reader.readLine())
                .append('\n');
        }
    }

    private File writeTempWav(byte[] pcmAudio) throws IOException {
        File dir = Config.getVoiceDataDirectory();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Could not create voice temp directory: " + dir.getAbsolutePath());
        }
        File wavFile = File.createTempFile("adm-voice-", ".wav", dir);
        try (FileOutputStream out = new FileOutputStream(wavFile)) {
            out.write(WavEncoder.encodePcm16Mono(pcmAudio, 16000));
        }
        return wavFile;
    }

    private String javaExecutable() {
        String executable = System.getProperty("os.name", "")
            .toLowerCase()
            .contains("win") ? "java.exe" : "java";
        return new File(new File(System.getProperty("java.home"), "bin"), executable).getAbsolutePath();
    }

    private String workerClasspath() throws IOException {
        Set<String> entries = new LinkedHashSet<>();
        String current = System.getProperty("java.class.path", "");
        for (String entry : current.split(File.pathSeparator)) {
            if (!entry.trim()
                .isEmpty()) {
                entries.add(entry.trim());
            }
        }
        addCodeSource(entries, StandaloneVoskTranscriber.class.getName());
        addCodeSource(entries, "org.vosk.Model");
        addCodeSource(entries, "com.sun.jna.Native");
        if (entries.isEmpty()) {
            throw new IOException("Could not build classpath for offline Vosk worker.");
        }
        StringBuilder builder = new StringBuilder();
        for (String entry : entries) {
            if (builder.length() > 0) {
                builder.append(File.pathSeparatorChar);
            }
            builder.append(entry);
        }
        return builder.toString();
    }

    private void addCodeSource(Set<String> entries, String className) throws IOException {
        try {
            Class<?> clazz = Class.forName(className, false, VoskSpeechToTextClient.class.getClassLoader());
            if (clazz.getProtectionDomain() == null || clazz.getProtectionDomain()
                .getCodeSource() == null) {
                return;
            }
            URL location = clazz.getProtectionDomain()
                .getCodeSource()
                .getLocation();
            if (location != null && "file".equals(location.getProtocol())) {
                entries.add(URLDecoder.decode(location.getPath(), StandardCharsets.UTF_8.name()));
            }
        } catch (ClassNotFoundException ignored) {
            // Production builds shadow Vosk/JNA into the mod jar, so these classes may share the mod code source.
        }
    }

    private String findResultJson(String output) {
        for (String line : output.split("\\r?\\n")) {
            if (line.startsWith(RESULT_PREFIX)) {
                return line.substring(RESULT_PREFIX.length())
                    .trim();
            }
        }
        return "";
    }

    private String parseText(String json) {
        try {
            JsonObject object = new JsonParser().parse(json)
                .getAsJsonObject();
            return object.has("text") ? object.get("text")
                .getAsString()
                .trim() : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private String summarize(String output) {
        String sanitized = output == null ? ""
            : output.replace((char) 13, ' ')
                .trim();
        return sanitized.length() <= 800 ? sanitized : sanitized.substring(sanitized.length() - 800);
    }

    private String sanitize(String text) {
        return text == null ? ""
            : text.replace((char) 10, ' ')
                .replace((char) 13, ' ');
    }
}
