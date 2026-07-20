package com.imgood.textech.webae.alerts;

import org.junit.Assert;
import org.junit.Test;

public class WebAlertsConfigValidatorTest {

    @Test
    public void defaultsKeepBrowserChatAndWarningHudEnabled() {
        WebAlertsConfig cfg = new WebAlertsConfig();
        Assert.assertNull(WebAlertsConfigValidator.validate(cfg));
        Assert.assertTrue(cfg.browserNotifications.enabled);
        Assert.assertTrue(cfg.playerChat.enabled);
        Assert.assertTrue(cfg.playerHud.enabled);
        Assert.assertTrue(cfg.playerHud.severities.contains("warning"));
        Assert.assertTrue(cfg.playerHud.severities.contains("error"));
    }

    @Test
    public void maskedSecretsAreMergedAndNeverReturnedToBrowser() {
        WebAlertsConfig existing = new WebAlertsConfig();
        WebAlertsConfig.NotificationTarget old = qqTarget("qq-main");
        old.appSecret = "real-client-secret";
        existing.notificationTargets.add(old);

        WebAlertsConfig incoming = new WebAlertsConfig();
        WebAlertsConfig.NotificationTarget edited = qqTarget("qq-main");
        edited.appSecret = "***cret";
        incoming.notificationTargets.add(edited);

        WebAlertsConfigValidator.mergeWebhookSecrets(incoming, existing);
        Assert.assertEquals("real-client-secret", incoming.notificationTargets.get(0).appSecret);

        WebAlertsConfig client = WebhookDispatcher.sanitizeForClient(incoming);
        Assert.assertTrue(client.notificationTargets.get(0).appSecretConfigured);
        Assert.assertTrue(client.notificationTargets.get(0).appSecret.startsWith("***"));
        Assert.assertFalse(client.notificationTargets.get(0).appSecret.contains("real-client-secret"));

        WebAlertsConfig secondEdit = new WebAlertsConfig();
        WebAlertsConfig.NotificationTarget blankSecret = qqTarget("qq-main");
        blankSecret.appSecret = "";
        secondEdit.notificationTargets.add(blankSecret);
        WebAlertsConfigValidator.mergeWebhookSecrets(secondEdit, existing);
        Assert.assertEquals("real-client-secret", secondEdit.notificationTargets.get(0).appSecret);
    }

    @Test
    public void ownerFiltersMustUseUuidValues() {
        WebAlertsConfig cfg = new WebAlertsConfig();
        WebAlertsConfig.NotificationTarget target = qqTarget("qq-main");
        target.ownerUuids.add("not-a-uuid");
        cfg.notificationTargets.add(target);
        Assert.assertTrue(
            WebAlertsConfigValidator.validate(cfg)
                .contains("invalid owner UUID"));
    }

    private static WebAlertsConfig.NotificationTarget qqTarget(String id) {
        WebAlertsConfig.NotificationTarget target = new WebAlertsConfig.NotificationTarget();
        target.id = id;
        target.type = "qq_official";
        target.appId = "1024";
        target.appSecret = "secret";
        target.targetType = "group";
        target.targetId = "group-openid";
        return target;
    }
}
