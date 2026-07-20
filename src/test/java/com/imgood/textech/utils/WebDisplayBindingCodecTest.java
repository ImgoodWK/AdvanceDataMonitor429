package com.imgood.textech.utils;

import org.junit.Assert;
import org.junit.Test;

public class WebDisplayBindingCodecTest {

    @Test
    public void parsesLiveBinding() throws Exception {
        String json = "{"
            + "\"format\":\"textech-webae-display-binding\","
            + "\"version\":1,"
            + "\"mode\":\"dashboard_live\","
            + "\"displayId\":\"abc12345deadbeef\","
            + "\"viewToken\":\"0123456789abcdef0123456789abcdef\","
            + "\"title\":\"Plant\","
            + "\"viewportHint\":{\"width\":800,\"height\":600},"
            + "\"webaeOrigin\":\"http://127.0.0.1:8090\""
            + "}";
        WebDisplayBindingCodec.Binding binding = WebDisplayBindingCodec.parse(json);
        Assert.assertEquals("dashboard_live", binding.mode);
        Assert.assertEquals("abc12345deadbeef", binding.displayId);
        Assert.assertEquals(800, binding.viewportWidth);
        Assert.assertEquals(64, binding.bindingHash.length());
        Assert.assertTrue(WebDisplayBindingCodec.looksLikeBinding(json));
    }

    @Test(expected = WebDisplayBindingCodec.BindingException.class)
    public void rejectsBadMode() throws Exception {
        WebDisplayBindingCodec.parse(
            "{\"format\":\"textech-webae-display-binding\",\"version\":1,\"mode\":\"nope\",\"displayId\":\"abcdefgh\",\"viewToken\":\"0123456789abcdef\"}");
    }
}
