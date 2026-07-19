package com.imgood.textech.webae.assistant;

import java.lang.reflect.Field;

import org.junit.Assert;
import org.junit.Test;

import com.imgood.textech.Config;
import com.imgood.textech.webae.assistant.WebAiConfigStore.ConfigView;
import com.imgood.textech.webae.assistant.WebAiConfigStore.ProfileView;
import com.imgood.textech.webae.assistant.WebAssistantService.ClientAiContext;

public class WebAiConfigSecurityTest {

    @Test
    public void publicViewCannotExposeApiKeyOrCiphertext() {
        for (Field field : ConfigView.class.getFields()) {
            String name = field.getName().toLowerCase();
            Assert.assertFalse("public view exposes secret field: " + field.getName(), "apikey".equals(name));
            Assert.assertFalse("public view exposes ciphertext: " + field.getName(), name.contains("encrypted"));
        }
        for (Field field : ProfileView.class.getFields()) {
            String name = field.getName().toLowerCase();
            Assert.assertFalse("profile view exposes secret field: " + field.getName(), "apikey".equals(name));
            Assert.assertFalse("profile view exposes ciphertext: " + field.getName(), name.contains("encrypted"));
        }
    }

    @Test
    public void baseUrlRequiresHttpsExceptForLoopback() {
        Assert.assertEquals("https://api.example.com/v1",
            WebAiConfigStore.validateBaseUrl("https://api.example.com/v1/"));
        Assert.assertEquals("http://127.0.0.1:11434/v1",
            WebAiConfigStore.validateBaseUrl("http://127.0.0.1:11434/v1"));
        assertRejected("http://api.example.com/v1");
        assertRejected("https://user:pass@api.example.com/v1");
        assertRejected("https://api.example.com/v1?key=secret");
    }

    @Test
    public void nativeProtocolsAreSelectedOnlyForTheirProviders() {
        Assert.assertEquals(WebAiConfigStore.PROTOCOL_ANTHROPIC,
            WebAiConfigStore.protocolFor("anthropic"));
        Assert.assertEquals(WebAiConfigStore.PROTOCOL_GEMINI,
            WebAiConfigStore.protocolFor("gemini"));
        Assert.assertEquals(WebAiConfigStore.PROTOCOL_OPENAI,
            WebAiConfigStore.protocolFor("deepseek"));
        Assert.assertEquals(WebAiConfigStore.PROTOCOL_OPENAI,
            WebAiConfigStore.protocolFor("custom"));
    }

    @Test
    public void dualFlagsControlSourcesAndContextHasNoSecretField() {
        boolean previousServer = Config.webAiServerKeyEnabled;
        boolean previousBrowser = Config.webAiBrowserKeyEnabled;
        try {
            Config.webAiServerKeyEnabled = false;
            Config.webAiBrowserKeyEnabled = true;
            Assert.assertTrue(WebAiConfigStore.isBrowserKeyEnabled());
            Assert.assertFalse(WebAiConfigStore.isServerKeyEnabled());
            Assert.assertTrue(WebAiConfigStore.instance().runtimes().isEmpty());
            Assert.assertEquals(WebAiConfigStore.SOURCE_BROWSER,
                WebAiConfigStore.normalizeAiSource("browser"));
            for (Field field : ClientAiContext.class.getFields()) {
                String name = field.getName().toLowerCase();
                Assert.assertFalse("browser context exposes an API key field: " + field.getName(),
                    "apikey".equals(name) || name.contains("encrypted"));
                Assert.assertFalse("browser context exposes a secret field: " + field.getName(), name.contains("secret"));
            }
            Config.webAiServerKeyEnabled = true;
            Config.webAiBrowserKeyEnabled = true;
            Assert.assertEquals(WebAiConfigStore.SOURCE_SERVER,
                WebAiConfigStore.normalizeAiSource("server"));
            Assert.assertEquals(WebAiConfigStore.SOURCE_BROWSER,
                WebAiConfigStore.normalizeAiSource("browser"));
        } finally {
            Config.webAiServerKeyEnabled = previousServer;
            Config.webAiBrowserKeyEnabled = previousBrowser;
        }
    }

    @Test
    public void providerSideFailuresAreDetectedForFailover() {
        Assert.assertTrue(WebAiCompletionService.isProviderSideFailure(
            new java.io.IOException("AI provider request failed (HTTP 429): quota exceeded")));
        Assert.assertTrue(WebAiCompletionService.isProviderSideFailure(
            new java.io.IOException("connection refused")));
        Assert.assertTrue(WebAiCompletionService.isProviderSideFailure(
            new java.io.IOException("AI response content was empty.")));
    }

    private static void assertRejected(String value) {
        try {
            WebAiConfigStore.validateBaseUrl(value);
            Assert.fail("Expected URL to be rejected: " + value);
        } catch (IllegalArgumentException expected) {}
    }
}
