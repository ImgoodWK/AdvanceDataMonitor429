package com.imgood.textech.assistant.ai;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/** Regression coverage for bounded, linear-time web-search response parsing. */
public class WebSearchServiceTest {

    @Test
    public void parsesNormalDuckDuckGoResultsAndEntities() {
        String html = "<a class=\"result__a\" href=\"//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fdocs\">"
            + "A &amp; B &quot;guide&quot;</a>"
            + "<a class=\"result__snippet\">Use &lt;code&gt; safely&#39;.</a>";

        List<WebSearchResult> results = WebSearchService.parseDuckDuckGoResults(html, 5);

        Assert.assertEquals(1, results.size());
        Assert.assertEquals("A & B \"guide\"", results.get(0).title);
        Assert.assertEquals("https://example.com/docs", results.get(0).url);
        // Entity decoding intentionally happens after tag stripping, matching the
        // pre-hardening parser's observable output.
        Assert.assertEquals("Use <code> safely'.", results.get(0).snippet);
    }

    @Test
    public void limitsResultsWhenResultPrefixesRepeat() {
        StringBuilder html = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            html.append("<a class=\"result__a\" href=\"https://example.com/")
                .append(i)
                .append("\">Result ")
                .append(i)
                .append("</a>");
            html.append("<td class=\"result__snippet\">Snippet ")
                .append(i)
                .append("</td>");
        }

        List<WebSearchResult> results = WebSearchService.parseDuckDuckGoResults(html.toString(), 3);

        Assert.assertEquals(3, results.size());
        Assert.assertEquals("https://example.com/2", results.get(2).url);
        Assert.assertEquals("Snippet 2", results.get(2).snippet);
    }

    @Test(timeout = 1000)
    public void malformedHtmlDoesNotBacktrackOrHang() {
        StringBuilder input = new StringBuilder(100_000);
        for (int i = 0; i < 50_000; i++) {
            input.append('<');
        }
        input.append(" tail");

        List<WebSearchResult> results = WebSearchService
            .parseDuckDuckGoResults("<a class=\"result__a\" href=\"https://example.com\">" + input, 5);

        Assert.assertTrue(results.isEmpty());
    }

    @Test
    public void missingClosingTagsAreIgnoredWithoutProducingPartialResults() {
        String html = "<a class=\"result__a\" href=\"https://example.com\">unfinished"
            + "<td class=\"result__snippet\">also unfinished";

        Assert.assertTrue(
            WebSearchService.parseDuckDuckGoResults(html, 5)
                .isEmpty());
    }

    @Test
    public void responseBodyHasExplicitByteLimit() throws Exception {
        byte[] body = new byte[2 * 1024 * 1024 + 1];

        try {
            WebSearchService.readStream(new ByteArrayInputStream(body));
            Assert.fail("Expected oversized response to be rejected");
        } catch (IOException expected) {
            Assert.assertEquals("HTTP response exceeds maximum size.", expected.getMessage());
        }

        String small = "line 1\nline 2";
        Assert.assertEquals(
            "line 1line 2",
            WebSearchService.readStream(new ByteArrayInputStream(small.getBytes(StandardCharsets.UTF_8))));
    }
}
