package com.imgood.textech.voice;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.Assert;
import org.junit.Test;

/** Regression coverage for embedded voice archive extraction boundaries. */
public class EmbeddedVoiceModelManagerTest {

    private static final String RESOURCE_PREFIX = "assets/textech/voice/vosk/zh-small/";

    @Test
    public void extractsNormalJarEntry() throws Exception {
        Path workspace = Files.createTempDirectory("textech-voice-jar-normal");
        Path jar = createJar(
            workspace.resolve("voice.jar"),
            entryMap(RESOURCE_PREFIX + "conf/model.conf", "model configuration"));
        Path target = workspace.resolve("target");

        invokeCopyFromJar(
            jar.toFile(),
            RESOURCE_PREFIX,
            Arrays.asList(RESOURCE_PREFIX + "conf/model.conf"),
            target.toFile());

        Assert.assertEquals(
            "model configuration",
            new String(Files.readAllBytes(target.resolve("conf/model.conf")), StandardCharsets.UTF_8));
    }

    @Test
    public void missingJarEntryDoesNotShiftFollowingOutput() throws Exception {
        Path workspace = Files.createTempDirectory("textech-voice-jar-missing");
        String missing = RESOURCE_PREFIX + "conf/missing.conf";
        String present = RESOURCE_PREFIX + "conf/present.conf";
        Path jar = createJar(workspace.resolve("voice.jar"), entryMap(present, "present content"));
        Path target = workspace.resolve("target");

        invokeCopyFromJar(jar.toFile(), RESOURCE_PREFIX, Arrays.asList(missing, present), target.toFile());

        Assert.assertFalse(Files.exists(target.resolve("conf/missing.conf")));
        Assert.assertEquals(
            "present content",
            new String(Files.readAllBytes(target.resolve("conf/present.conf")), StandardCharsets.UTF_8));
    }

    @Test
    public void rejectsJarTraversalBeforeWritingAnything() throws Exception {
        Path workspace = Files.createTempDirectory("textech-voice-jar-traversal");
        String malicious = RESOURCE_PREFIX + "../escaped.txt";
        Path jar = createJar(workspace.resolve("voice.jar"), entryMap(malicious, "must not extract"));
        Path target = workspace.resolve("target");

        assertRejected(new ThrowingOperation() {

            @Override
            public void run() throws Exception {
                invokeCopyFromJar(jar.toFile(), RESOURCE_PREFIX, Arrays.asList(malicious), target.toFile());
            }
        });

        Assert.assertFalse(Files.exists(workspace.resolve("escaped.txt")));
    }

    @Test
    public void rejectsManifestTraversalBeforeWritingAnything() throws Exception {
        Path workspace = Files.createTempDirectory("textech-voice-manifest-traversal");
        String manifest = RESOURCE_PREFIX + "adm-model-files.txt";
        Path jar = createJar(workspace.resolve("voice.jar"), entryMap(manifest, "../manifest-escaped.txt\n"));
        Path target = workspace.resolve("target");

        @SuppressWarnings("unchecked")
        List<String> entries = (List<String>) invokePrivate(
            "readManifestEntriesFromJar",
            new Class<?>[] { java.io.File.class, String.class },
            new Object[] { jar.toFile(), RESOURCE_PREFIX });

        assertRejected(new ThrowingOperation() {

            @Override
            public void run() throws Exception {
                invokeCopyFromJar(jar.toFile(), RESOURCE_PREFIX, entries, target.toFile());
            }
        });

        Assert.assertFalse(Files.exists(workspace.resolve("manifest-escaped.txt")));
    }

    @Test
    public void rejectsDirectorySourceBackslashTraversalBeforeWritingAnything() throws Exception {
        Path workspace = Files.createTempDirectory("textech-voice-directory-traversal");
        Path source = workspace.resolve("source");
        Files.createDirectories(source.resolve(RESOURCE_PREFIX));
        String malicious = RESOURCE_PREFIX + "nested\\..\\directory-escaped.txt";
        Path target = workspace.resolve("target");

        assertRejected(new ThrowingOperation() {

            @Override
            public void run() throws Exception {
                invokeCopyFromDirectory(source.toFile(), RESOURCE_PREFIX, Arrays.asList(malicious), target.toFile());
            }
        });

        Assert.assertFalse(Files.exists(workspace.resolve("directory-escaped.txt")));
    }

    private static Map<String, String> entryMap(String name, String content) {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put(name, content);
        return entries;
    }

    private static Path createJar(Path path, Map<String, String> entries) throws IOException {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey()));
                output.write(
                    entry.getValue()
                        .getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return path;
    }

    private static void invokeCopyFromJar(java.io.File jar, String prefix, List<String> entries, java.io.File target)
        throws Exception {
        invokePrivate(
            "copyEntriesFromJarFile",
            new Class<?>[] { java.io.File.class, String.class, List.class, java.io.File.class },
            new Object[] { jar, prefix, entries, target });
    }

    private static void invokeCopyFromDirectory(java.io.File root, String prefix, List<String> entries,
        java.io.File target) throws Exception {
        invokePrivate(
            "copyEntriesFromDirectory",
            new Class<?>[] { java.io.File.class, String.class, List.class, java.io.File.class },
            new Object[] { root, prefix, entries, target });
    }

    private static Object invokePrivate(String name, Class<?>[] parameterTypes, Object[] arguments) throws Exception {
        Method method = EmbeddedVoiceModelManager.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        try {
            return method.invoke(new EmbeddedVoiceModelManager(), arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw exception;
        }
    }

    private static void assertRejected(ThrowingOperation operation) throws Exception {
        try {
            operation.run();
            Assert.fail("Expected malicious archive path to be rejected");
        } catch (IOException expected) {
            Assert.assertTrue(
                expected.getMessage(),
                expected.getMessage()
                    .contains("Refusing archive"));
        }
    }

    private interface ThrowingOperation {

        void run() throws Exception;
    }

}
