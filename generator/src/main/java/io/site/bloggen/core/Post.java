package io.site.bloggen.core;

import java.time.LocalDate;
import java.util.List;

public record Post(String fileName, String url, LocalDate date, String title, List<String> tags, String description) {
    public String year() { return String.valueOf(date.getYear()); }
}

