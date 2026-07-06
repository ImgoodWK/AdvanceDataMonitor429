package com.imgood.textech.webae.assistant;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.textech.assistant.AssistantIntent;
import com.imgood.textech.assistant.AssistantIntentService;
import com.imgood.textech.assistant.AssistantIntentType;
import com.imgood.textech.assistant.AssistantServerServices;
import com.imgood.textech.webae.context.WebAeOwnerContext;

/**
 * Server-side bridge: Web text input → rule-based assistant intent parsing + server query.
 * AI keys stay client-side; Web panel uses rule parser only (no secrets in server logs).
 */
public final class WebAssistantService {

    private static final AssistantIntentService INTENT_SERVICE = new AssistantIntentService();

    private WebAssistantService() {}

    public static WebAssistantResult handleQuery(String ownerUuid, String text, String locale) {
        WebAssistantResult result = new WebAssistantResult();
        if (text == null || text.trim()
            .isEmpty()) {
            result.success = false;
            result.message = "Empty query.";
            result.code = "empty_query";
            return result;
        }
        if (!WebAssistantRateLimiter.tryAcquire(ownerUuid)) {
            result.success = false;
            result.message = "Rate limited. Try again shortly.";
            result.code = "rate_limited";
            result.cooldownMs = WebAssistantRateLimiter.remainingCooldownMs(ownerUuid);
            return result;
        }

        String safeLocale = locale == null || locale.isEmpty() ? "zh_CN" : locale;
        AssistantIntent intent = INTENT_SERVICE.parse(text.trim());
        if (intent == null) {
            intent = AssistantIntent.chat(text.trim());
        }

        result.intentType = intent.type != null ? intent.type.name() : "CHAT";
        result.intentTarget = intent.target == null ? "" : intent.target;

        if (intent.type == null || intent.type == AssistantIntentType.CHAT
            || intent.type == AssistantIntentType.HUD_OPEN
            || intent.type == AssistantIntentType.HUD_CLOSE
            || intent.type == AssistantIntentType.HISTORY_PREV
            || intent.type == AssistantIntentType.HISTORY_NEXT
            || intent.type == AssistantIntentType.CANCEL) {
            result.success = true;
            result.message = zh(safeLocale) ? "该指令需在客户端执行，Web 面板仅支持查询类助手功能。"
                : "This command must run in-game; Web panel supports query intents only.";
            result.code = "client_only";
            return result;
        }

        EntityPlayerMP player = WebAeOwnerContext.getOwnerPlayerOrFake(ownerUuid);
        if (player == null) {
            result.success = false;
            result.message = "Owner player context unavailable.";
            result.code = "no_player";
            return result;
        }

        try {
            String response = AssistantServerServices
                .query(player, intent.type, intent.rawText, intent.target, intent.amount, safeLocale);
            result.success = true;
            result.message = response == null ? "" : response;
            result.code = "ok";
        } catch (Exception e) {
            result.success = false;
            result.message = zh(safeLocale) ? "助手查询失败。" : "Assistant query failed.";
            result.code = "query_failed";
        }
        return result;
    }

    private static boolean zh(String locale) {
        return locale == null || locale.toLowerCase()
            .startsWith("zh");
    }

    public static final class WebAssistantResult {

        public boolean success;
        public String message = "";
        public String code = "";
        public String intentType = "";
        public String intentTarget = "";
        public long cooldownMs;
    }
}
