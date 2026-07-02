package com.imgood.textech.assistant.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WebSearchData {

    public final String provider;
    public final String context;
    public final List<WebSearchResult> results;

    public WebSearchData(String provider, String context, List<WebSearchResult> results) {
        this.provider = provider == null ? "" : provider;
        this.context = context == null ? "" : context;
        this.results = results == null ? Collections.<WebSearchResult>emptyList()
            : Collections.unmodifiableList(new ArrayList<>(results));
    }

    public boolean hasResults() {
        return !this.results.isEmpty();
    }
}
