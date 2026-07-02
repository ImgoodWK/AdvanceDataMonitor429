package com.imgood.textech.assistant.ai;

public final class WebSearchResult {

    public final String title;
    public final String url;
    public final String snippet;

    public WebSearchResult(String title, String url, String snippet) {
        this.title = title == null ? "" : title.trim();
        this.url = url == null ? "" : url.trim();
        this.snippet = snippet == null ? "" : snippet.trim();
    }
}
