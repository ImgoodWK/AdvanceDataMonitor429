package com.imgood.textech.webae.console;

import java.lang.reflect.Method;

import org.junit.Assert;
import org.junit.Test;

import com.imgood.textech.webae.api.WebApiRouter;

public class AdminCommandServiceTest {

    @Test
    public void stripsOptionalLeadingSlashes() {
        Assert.assertEquals("say hello", AdminCommandService.normalizeCommand("  /say hello  "));
        Assert.assertEquals("kick Alex", AdminCommandService.normalizeCommand("/// kick Alex"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMultilineCommands() {
        AdminCommandService.normalizeCommand("say one\nstop");
    }

    @Test
    public void classifiesHighRiskRootsWithoutSubstringMatches() {
        Assert.assertTrue(AdminCommandService.isHighRisk("/stop"));
        Assert.assertTrue(AdminCommandService.isHighRisk("whitelist add Alex"));
        Assert.assertFalse(AdminCommandService.isHighRisk("stopsound Alex"));
        Assert.assertFalse(AdminCommandService.isHighRisk("say stop"));
    }

    @Test
    public void normalizesDynamicConsoleRoutesForDiagnostics() throws Exception {
        Method normalize = WebApiRouter.class.getDeclaredMethod("normalizeRoute", String.class);
        normalize.setAccessible(true);
        Assert.assertEquals(
            "/api/admin/server-console/presets/{id}",
            normalize.invoke(null, "/api/admin/server-console/presets/123"));
        Assert.assertEquals(
            "/api/admin/server-console/history/{id}",
            normalize.invoke(null, "/api/admin/server-console/history/123"));
        Assert.assertEquals(
            "/api/admin/server-console/history/clear",
            normalize.invoke(null, "/api/admin/server-console/history/clear"));
    }
}
