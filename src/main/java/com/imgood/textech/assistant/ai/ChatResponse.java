package com.imgood.textech.assistant.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChatResponse {

    public final String content;
    public final List<Source> sources;
    public final boolean webSearchFallbackUsed;

    public ChatResponse(String content, List<Source> sources, boolean webSearchFallbackUsed) {
        this.content = content == null ? "" : content;
        this.sources = sources == null ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(sources));
        this.webSearchFallbackUsed = webSearchFallbackUsed;
    }

    public String contentWithSources() {
        StringBuilder builder = new StringBuilder(this.content);
        if (!this.sources.isEmpty()) {
            builder.append("\n\nSources:");
            int index = 1;
            for (Source source : this.sources) {
                builder.append("\n")
                    .append(index++)
                    .append(". ");
                if (!source.title.isEmpty()) {
                    builder.append(source.title)
                        .append(" - ");
                }
                builder.append(source.url);
            }
        }
        return builder.toString();
    }

    public static class Source {

        public final String title;
        public final String url;

        public Source(String title, String url) {
            this.title = title == null ? "" : title;
            this.url = url == null ? "" : url;
        }
    }
}
