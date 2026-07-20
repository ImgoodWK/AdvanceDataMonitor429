package com.imgood.textech.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WebDashboardSnapshotCodecTest {

    private static final String VALID = "{" + "\"format\":\"textech-webae-display-snapshot\","
        + "\"version\":1,"
        + "\"title\":\"Factory\","
        + "\"viewport\":{\"width\":640,\"height\":360,\"background\":\"#FF08111F\"},"
        + "\"primitives\":["
        + "{\"kind\":\"rect\",\"x\":0,\"y\":0,\"w\":100,\"h\":50,\"fill\":\"#CC102030\"},"
        + "{\"kind\":\"text\",\"x\":4,\"y\":5,\"w\":80,\"h\":16,\"text\":\"TPS 20\",\"color\":\"#FFFFFFFF\",\"size\":14,\"weight\":700,\"align\":\"left\"},"
        + "{\"kind\":\"polyline\",\"points\":[0,10,20,5,40,12],\"color\":\"#FF00FFFF\",\"lineWidth\":2}"
        + "]}";

    @Test
    public void roundTripsBoundedSnapshot() throws Exception {
        WebDashboardSnapshotCodec.EncodedSnapshot encoded = WebDashboardSnapshotCodec.encode(VALID);
        assertTrue(encoded.compressed.length < VALID.getBytes("UTF-8").length);
        assertEquals(64, encoded.hash.length());

        WebDashboardSnapshotCodec.DecodedSnapshot decoded = WebDashboardSnapshotCodec.decode(encoded.compressed);
        assertEquals("Factory", decoded.title);
        assertEquals(640, decoded.width);
        assertEquals(360, decoded.height);
        assertEquals(3, decoded.primitives.size());
        assertEquals(encoded.hash, decoded.hash);
    }

    @Test(expected = WebDashboardSnapshotCodec.SnapshotException.class)
    public void rejectsUnknownFormat() throws Exception {
        WebDashboardSnapshotCodec.encode(VALID.replace("textech-webae-display-snapshot", "other"));
    }
}
