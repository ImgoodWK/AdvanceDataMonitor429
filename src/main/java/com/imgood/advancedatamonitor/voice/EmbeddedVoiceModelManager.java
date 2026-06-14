package com.imgood.advancedatamonitor.voice;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.Config;

public class EmbeddedVoiceModelManager {

    private static final String EMBEDDED_RESOURCE_ROOT = "assets/advancedatamonitor/voice/vosk";
    private static final String DEFAULT_MODEL_ID = "zh-small";
    private File cachedModelDirectory;
    private String cachedModelKey;

    public synchronized File resolveModelDirectory() throws IOException {
        ensureSupportedRuntime();
        String configuredModel = Config.voiceSttModel == null || Config.voiceSttModel.trim()
            .isEmpty() ? DEFAULT_MODEL_ID : Config.voiceSttModel.trim();
        File configuredDirectory = new File(configuredModel);
        if (configuredDirectory.isDirectory()) {
            return configuredDirectory;
        }
        String modelId = normalizeModelId(configuredModel);
        if (cachedModelDirectory != null && cachedModelDirectory.isDirectory() && modelId.equals(cachedModelKey)) {
            return cachedModelDirectory;
        }
        File target = new File(Config.getVoiceDataDirectory(), "models/vosk/" + modelId);
        if (isUsableModelDirectory(target)) {
            cachedModelDirectory = target;
            cachedModelKey = modelId;
            return target;
        }
        extractEmbeddedModel(modelId, target);
        if (!isUsableModelDirectory(target)) {
            throw new IOException(
                "Embedded Vosk model '" + modelId
                    + "' is missing. Install the with-voice mod package or set voice.sttModel to a local Vosk model directory.");
        }
        cachedModelDirectory = target;
        cachedModelKey = modelId;
        return target;
    }

    private void ensureSupportedRuntime() throws IOException {
        String os = System.getProperty("os.name", "")
            .toLowerCase();
        if (!os.contains("win")) {
            throw new IOException(
                "Embedded offline voice currently supports Windows only. Set voice.sttMode=http to use external STT on this platform.");
        }
        String arch = System.getProperty("os.arch", "")
            .toLowerCase();
        if (!(arch.contains("64") || arch.contains("amd64") || arch.contains("x86_64"))) {
            throw new IOException("Embedded offline voice requires 64-bit Java on Windows.");
        }
    }

    private String normalizeModelId(String configuredModel) {
        if ("vosk".equalsIgnoreCase(configuredModel) || "vosk-zh".equalsIgnoreCase(configuredModel)
            || isLegacyWhisperModelName(configuredModel)) {
            return DEFAULT_MODEL_ID;
        }
        return configuredModel;
    }

    private boolean isLegacyWhisperModelName(String configuredModel) {
        return "whisper-1".equalsIgnoreCase(configuredModel) || "tiny".equalsIgnoreCase(configuredModel)
            || "base".equalsIgnoreCase(configuredModel)
            || "small".equalsIgnoreCase(configuredModel)
            || "medium".equalsIgnoreCase(configuredModel)
            || "large".equalsIgnoreCase(configuredModel)
            || configuredModel.toLowerCase()
                .startsWith("whisper-");
    }

    private boolean isUsableModelDirectory(File directory) {
        return directory.isDirectory() && new File(directory, "conf").isDirectory()
            && new File(directory, "am").isDirectory();
    }

    private void extractEmbeddedModel(String modelId, File target) throws IOException {
        String resourcePrefix = EMBEDDED_RESOURCE_ROOT + "/" + modelId + "/";
        List<String> entries = listEmbeddedEntries(resourcePrefix);
        if (entries.isEmpty()) {
            throw new IOException("No embedded Vosk model resources found for '" + modelId + "'.");
        }
        if (!target.exists() && !target.mkdirs()) {
            throw new IOException("Could not create voice model cache: " + target.getAbsolutePath());
        }
        ClassLoader loader = EmbeddedVoiceModelManager.class.getClassLoader();
        for (String entry : entries) {
            if (entry.endsWith("/")) {
                continue;
            }
            String relative = entry.substring(resourcePrefix.length());
            File output = new File(target, relative.replace('/', File.separatorChar));
            File parent = output.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("Could not create voice model directory: " + parent.getAbsolutePath());
            }
            try (InputStream in = loader.getResourceAsStream(entry);
                FileOutputStream out = new FileOutputStream(output)) {
                if (in == null) {
                    throw new IOException("Missing embedded model resource: " + entry);
                }
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, read);
                }
            }
        }
        AdvanceDataMonitor.LOG
            .info("[ADM Assistant] Extracted embedded Vosk model '{}' to {}", modelId, target.getAbsolutePath());
    }

    private List<String> listEmbeddedEntries(String resourcePrefix) throws IOException {
        List<String> entries = new ArrayList<>();
        ClassLoader loader = EmbeddedVoiceModelManager.class.getClassLoader();
        Enumeration<URL> roots = loader.getResources(resourcePrefix);
        while (roots.hasMoreElements()) {
            URL url = roots.nextElement();
            if ("file".equals(url.getProtocol())) {
                File directory = new File(URLDecoder.decode(url.getPath(), StandardCharsets.UTF_8.name()));
                listFileEntries(resourcePrefix, directory, entries);
            } else if ("jar".equals(url.getProtocol())) {
                listJarEntries(url, resourcePrefix, entries);
            }
        }
        if (entries.isEmpty()) {
            entries.addAll(readManifestEntries(loader, resourcePrefix));
        }
        return entries;
    }

    private List<String> readManifestEntries(ClassLoader loader, String resourcePrefix) throws IOException {
        List<String> entries = new ArrayList<>();
        String manifestResource = resourcePrefix + "adm-model-files.txt";
        try (InputStream stream = loader.getResourceAsStream(manifestResource)) {
            if (stream == null) {
                return entries;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        entries.add(resourcePrefix + trimmed);
                    }
                }
            }
        }
        return entries;
    }

    private void listFileEntries(String resourcePrefix, File directory, List<String> entries) {
        if (!directory.isDirectory()) {
            return;
        }
        listFileEntries(resourcePrefix, directory, directory, entries);
    }

    private void listFileEntries(String resourcePrefix, File root, File current, List<String> entries) {
        File[] files = current.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            String relative = root.toURI()
                .relativize(file.toURI())
                .getPath();
            if (file.isDirectory()) {
                listFileEntries(resourcePrefix, root, file, entries);
            } else {
                entries.add(resourcePrefix + relative);
            }
        }
    }

    private void listJarEntries(URL url, String resourcePrefix, List<String> entries) throws IOException {
        JarURLConnection connection = (JarURLConnection) url.openConnection();
        try (JarFile jar = connection.getJarFile()) {
            Enumeration<JarEntry> jarEntries = jar.entries();
            while (jarEntries.hasMoreElements()) {
                JarEntry entry = jarEntries.nextElement();
                if (!entry.isDirectory() && entry.getName()
                    .startsWith(resourcePrefix)) {
                    entries.add(entry.getName());
                }
            }
        }
    }
}
