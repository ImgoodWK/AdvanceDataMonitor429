package com.imgood.textech.webae.spark;

import org.junit.Assert;
import org.junit.Test;

import com.google.gson.JsonObject;

public class SparkAiAnalysisServiceTest {

    @Test
    public void outboundPayloadContainsOnlyBoundedAggregates() {
        SparkProfile profile = new SparkProfile();
        profile.id = "profile-a";
        profile.mode = "server";
        profile.messages.add("raw spark output must stay local");
        profile.resultUrl = "https://viewer.example/secret-id";

        SparkProfile.Hotspot hotspot = new SparkProfile.Hotspot();
        hotspot.className = "example.ModClass";
        hotspot.methodName = "tick";
        hotspot.percent = 42.0D;
        profile.hotspots.add(hotspot);

        JsonObject outbound = SparkAiAnalysisService.boundedProfile(profile, "A");
        String json = outbound.toString();
        Assert.assertTrue(json.contains("example.ModClass"));
        Assert.assertFalse(json.contains("raw spark output"));
        Assert.assertFalse(json.contains("viewer.example"));
        Assert.assertFalse(outbound.has("messages"));
        Assert.assertFalse(outbound.has("resultUrl"));
    }
}
