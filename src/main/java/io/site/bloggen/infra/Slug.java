package io.site.bloggen.infra;

import java.text.Normalizer;

public final class Slug {
    private Slug() {}
    public static String of(String title) {
        String n = Normalizer.normalize(title, Normalizer.Form.NFKD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        n = n.toLowerCase()
             .replaceAll("[^a-z0-9\\-\\s]", " ")
             .trim()
             .replaceAll("\\s+", "-");
        if (n.isEmpty()) n = "post";
        return n;
    }
}

