package com.imgood.textech.webae.qqbot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imgood.textech.webae.qqbot.QqBotCommandRouter.RouteResult;

public class QqBotCoreTest {

    @Test
    public void parsesGroupMessageAndCleansMention() {
        JsonObject data = json("{\"id\":\"m1\",\"group_openid\":\"g1\",\"content\":\"<@!bot> /tps\","
            + "\"author\":{\"member_openid\":\"u1\",\"username\":\"Alice\"}}");
        QqBotMessage message = QqBotMessage.fromDispatch("GROUP_AT_MESSAGE_CREATE", data, "e1", 1000L);
        assertNotNull(message);
        assertEquals("group", message.targetType);
        assertEquals("g1", message.targetId);
        assertEquals("u1", message.senderId);
        assertEquals("/tps", message.content);
    }

    @Test
    public void rejectsNonMessageEvents() {
        assertNull(QqBotMessage.fromDispatch("READY", json("{}"), "", 1L));
    }

    @Test
    public void normalizesIdsAndScheduledTargets() {
        QqBotConfig cfg = new QqBotConfig();
        cfg.allowedGroupIds = Arrays.asList(" g1 ", "g1", "g2");
        cfg.scheduledReportTargets = Arrays.asList("g1", "c2c:u1", "channel:");
        QqBotConfig normalized = QqBotConfigValidator.normalize(cfg);
        assertEquals(Arrays.asList("g1", "g2"), normalized.allowedGroupIds);
        assertEquals(Arrays.asList("group:g1", "c2c:u1"), normalized.scheduledReportTargets);
    }

    @Test
    public void enabledConfigRequiresCredentials() {
        QqBotConfig cfg = new QqBotConfig();
        cfg.enabled = true;
        assertTrue(QqBotConfigValidator.validate(cfg, false).contains("AppID"));
        cfg.appId = "app";
        assertTrue(QqBotConfigValidator.validate(cfg, false).contains("ClientSecret"));
        assertNull(QqBotConfigValidator.validate(cfg, true));
    }

    @Test
    public void routesDeterministicAndAiCommands() {
        QqBotConfig cfg = new QqBotConfig();
        QqBotSnapshot snapshot = new QqBotSnapshot();
        snapshot.tps = 18.5D;
        snapshot.mspt = 42.2D;
        snapshot.onlinePlayers = 2;
        snapshot.maxPlayers = 20;
        snapshot.playerNames = Arrays.asList("Alice", "Bob");

        RouteResult tps = QqBotCommandRouter.route(cfg, snapshot, "/tps", false);
        assertEquals("reply", tps.kind);
        assertTrue(tps.reply.contains("18.5"));

        RouteResult ai = QqBotCommandRouter.route(cfg, snapshot, "解释一下无尽锭", false);
        assertEquals("ai", ai.kind);
        assertFalse(ai.aiText.isEmpty());

        RouteResult reset = QqBotCommandRouter.route(cfg, snapshot, "/reset", false);
        assertTrue(reset.clearConversation);
    }

    private static JsonObject json(String value) {
        return new JsonParser().parse(value).getAsJsonObject();
    }
}
