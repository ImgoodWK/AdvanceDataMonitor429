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
        JsonObject data = json(
            "{\"id\":\"m1\",\"group_openid\":\"g1\",\"content\":\"<@!bot> /tps\","
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
        assertTrue(
            QqBotConfigValidator.validate(cfg, false)
                .contains("AppID"));
        cfg.appId = "app";
        assertTrue(
            QqBotConfigValidator.validate(cfg, false)
                .contains("ClientSecret"));
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

    @Test
    public void intentCompatOffKeepsAllOnWebae() {
        QqBotConfig cfg = new QqBotConfig();
        cfg.astrBotCompatEnabled = false;
        QqBotIntentClassifier.Decision decision = QqBotIntentClassifier.classify(cfg, "今天天气怎么样");
        assertEquals(QqBotIntentClassifier.Owner.WEBAE, decision.owner);
        assertEquals("compat_off", decision.reason);
    }

    @Test
    public void intentExplicitPrefixesAndKeywords() {
        QqBotConfig cfg = new QqBotConfig();
        cfg.astrBotCompatEnabled = true;

        QqBotIntentClassifier.Decision webae = QqBotIntentClassifier.classify(cfg, "gtnh 帮我看下卡顿");
        assertEquals(QqBotIntentClassifier.Owner.WEBAE, webae.owner);
        assertEquals("帮我看下卡顿", webae.textForHandler);

        QqBotIntentClassifier.Decision astr = QqBotIntentClassifier.classify(cfg, "tt：搜一下今天新闻");
        assertEquals(QqBotIntentClassifier.Owner.ASTRBOT, astr.owner);
        assertEquals("搜一下今天新闻", astr.textForHandler);

        QqBotIntentClassifier.Decision compact = QqBotIntentClassifier.classify(cfg, "tt生图 狐娘");
        assertEquals(QqBotIntentClassifier.Owner.ASTRBOT, compact.owner);
        assertEquals("生图 狐娘", compact.textForHandler);

        QqBotIntentClassifier.Decision notPrefix = QqBotIntentClassifier.classify(cfg, "ttl report");
        assertEquals(QqBotIntentClassifier.Owner.ASTRBOT, notPrefix.owner);
        assertEquals("default_astrbot", notPrefix.reason);

        QqBotIntentClassifier.Decision keyword = QqBotIntentClassifier.classify(cfg, "请查询仪表盘告警");
        assertEquals(QqBotIntentClassifier.Owner.WEBAE, keyword.owner);
        assertTrue(keyword.reason.startsWith("webae_keyword:"));

        QqBotIntentClassifier.Decision command = QqBotIntentClassifier.classify(cfg, "/tps");
        assertEquals(QqBotIntentClassifier.Owner.WEBAE, command.owner);
        assertTrue(command.reason.startsWith("webae_command:"));

        QqBotIntentClassifier.Decision other = QqBotIntentClassifier.classify(cfg, "讲个笑话");
        assertEquals(QqBotIntentClassifier.Owner.ASTRBOT, other.owner);
        assertEquals("default_astrbot", other.reason);

        // A pasted URL is handled by AstrBot's link-summary plugin. Its
        // protocol, host, or path must not accidentally match WebAE keywords
        // such as "tps" or "textech" during shared-bot ownership routing.
        QqBotIntentClassifier.Decision link = QqBotIntentClassifier.classify(cfg, "https://textech.top/tps");
        assertEquals(QqBotIntentClassifier.Owner.ASTRBOT, link.owner);
        assertEquals("default_astrbot", link.reason);

        QqBotIntentClassifier.Decision proseAndLink = QqBotIntentClassifier
            .classify(cfg, "请看 https://example.com/textech，然后查下仪表盘告警");
        assertEquals(QqBotIntentClassifier.Owner.WEBAE, proseAndLink.owner);
        assertTrue(proseAndLink.reason.startsWith("webae_keyword:"));
    }

    @Test
    public void normalizesEmptyIntentListsToDefaults() {
        QqBotConfig cfg = new QqBotConfig();
        cfg.webaeExplicitPrefixes = Arrays.asList();
        cfg.astrBotExplicitPrefixes = Arrays.asList();
        cfg.webaeIntentKeywords = Arrays.asList();
        QqBotConfig normalized = QqBotConfigValidator.normalize(cfg);
        assertFalse(normalized.webaeExplicitPrefixes.isEmpty());
        assertFalse(normalized.astrBotExplicitPrefixes.isEmpty());
        assertFalse(normalized.webaeIntentKeywords.isEmpty());
        assertTrue(normalized.webaeExplicitPrefixes.contains("gtnh"));
    }

    private static JsonObject json(String value) {
        return new JsonParser().parse(value)
            .getAsJsonObject();
    }
}
