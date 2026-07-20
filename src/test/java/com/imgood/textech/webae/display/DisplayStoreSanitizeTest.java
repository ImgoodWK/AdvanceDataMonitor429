package com.imgood.textech.webae.display;

import org.junit.Assert;
import org.junit.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class DisplayStoreSanitizeTest {

    @Test
    public void stripsSecretKeysFromLayout() {
        JsonObject layout = new JsonParser()
            .parse("{\"widgets\":[],\"apiKey\":\"secret\",\"webhookUrl\":\"http://x\",\"titleColor\":\"#fff\"}")
            .getAsJsonObject();
        JsonObject clean = DisplayStore.sanitizeLayout(layout);
        Assert.assertNotNull(clean);
        Assert.assertFalse(clean.has("apiKey"));
        Assert.assertFalse(clean.has("webhookUrl"));
        Assert.assertTrue(clean.has("widgets"));
        Assert.assertEquals(
            "#fff",
            clean.get("titleColor")
                .getAsString());
    }
}
