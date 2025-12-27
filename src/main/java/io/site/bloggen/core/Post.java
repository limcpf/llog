package io.site.bloggen.core;

import java.time.LocalDateTime;
import java.util.List;

public record Post(String fileName, String url, LocalDateTime date, String title, List<String> tags, String description,
        String readingTime, String categoryPath,
        String series, Integer seriesOrder) {
    public String year() {
        return String.valueOf(date.getYear());
    }
}
