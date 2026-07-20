package com.imgood.textech.webae.alerts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class QqIdProbeParserTest {

    @After
    public void tearDown() {
        QqIdProbeService.instance()
            .resetForTest();
    }

    @Test
    public void parsesC2cOpenidFromMessageEvent() {
        JsonObject data = obj("{\"openid\":\"USER_OPENID_1\",\"content\":\"hello probe\"}");
        QqIdDiscovery discovery = QqIdProbeParser.fromDispatch("C2C_MESSAGE_CREATE", data, 1000L);
        assertNotNull(discovery);
        assertEquals("c2c", discovery.kind);
        assertEquals("USER_OPENID_1", discovery.targetId);
        assertEquals("C2C_MESSAGE_CREATE", discovery.eventType);
        assertTrue(discovery.preview.contains("hello"));
    }

    @Test
    public void parsesC2cOpenidFromAuthorFallback() {
        JsonObject data = obj("{\"author\":{\"user_openid\":\"AUTHOR_OID\"},\"content\":\"hi\"}");
        QqIdDiscovery discovery = QqIdProbeParser.fromDispatch("FRIEND_ADD", data, 2000L);
        assertNotNull(discovery);
        assertEquals("c2c", discovery.kind);
        assertEquals("AUTHOR_OID", discovery.targetId);
    }

    @Test
    public void parsesGroupOpenid() {
        JsonObject data = obj("{\"group_openid\":\"GROUP_OID_9\",\"content\":\"@bot ping\"}");
        QqIdDiscovery discovery = QqIdProbeParser.fromDispatch("GROUP_AT_MESSAGE_CREATE", data, 3000L);
        assertNotNull(discovery);
        assertEquals("group", discovery.kind);
        assertEquals("GROUP_OID_9", discovery.targetId);
    }

    @Test
    public void parsesChannelIdFromAtMessage() {
        JsonObject data = obj("{\"channel_id\":\"CHANNEL_55\",\"content\":\"@bot\"}");
        QqIdDiscovery discovery = QqIdProbeParser.fromDispatch("AT_MESSAGE_CREATE", data, 4000L);
        assertNotNull(discovery);
        assertEquals("channel", discovery.kind);
        assertEquals("CHANNEL_55", discovery.targetId);
    }

    @Test
    public void parsesChannelCreateUsesId() {
        JsonObject data = obj("{\"id\":\"NEW_CHANNEL\",\"name\":\"alerts\"}");
        QqIdDiscovery discovery = QqIdProbeParser.fromDispatch("CHANNEL_CREATE", data, 5000L);
        assertNotNull(discovery);
        assertEquals("channel", discovery.kind);
        assertEquals("NEW_CHANNEL", discovery.targetId);
    }

    @Test
    public void returnsNullForUnknownEvent() {
        assertNull(QqIdProbeParser.fromDispatch("READY", obj("{}"), 1L));
        assertNull(QqIdProbeParser.fromDispatch("C2C_MESSAGE_CREATE", obj("{}"), 1L));
    }

    @Test
    public void serviceDedupesDiscoveriesByKindAndId() {
        QqIdProbeService service = QqIdProbeService.instance();
        service.resetForTest();
        service.offerDiscoveryForTest(new QqIdDiscovery("c2c", "A", "C2C_MESSAGE_CREATE", "one", 1L));
        service.offerDiscoveryForTest(new QqIdDiscovery("c2c", "A", "C2C_MESSAGE_CREATE", "two", 2L));
        service.offerDiscoveryForTest(new QqIdDiscovery("group", "G1", "GROUP_AT_MESSAGE_CREATE", "g", 3L));

        QqIdProbeService.Status status = service.snapshot();
        assertTrue(status.running);
        assertEquals(2, status.discoveries.size());
        assertEquals("two", status.discoveries.get(0).preview);
        assertEquals("group", status.discoveries.get(1).kind);
    }

    @Test
    public void startRequiresCredentials() {
        QqIdProbeService.StartResult result = QqIdProbeService.instance()
            .start("", "secret", null, null, 0L);
        assertFalse(result.success);
        assertTrue(result.error.contains("appId"));
    }

    private static JsonObject obj(String json) {
        return new JsonParser().parse(json)
            .getAsJsonObject();
    }
}
