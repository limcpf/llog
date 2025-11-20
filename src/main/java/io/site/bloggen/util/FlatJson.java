package io.site.bloggen.util;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FlatJson {
    private static final Pattern PAIR = Pattern.compile("\\\"(.*?)\\\"\\s*:\\s*\\\"(.*?)\\\"");
    private FlatJson() {}
    public static Map<String,String> parse(String json) {
        Map<String,String> out = new HashMap<>();
        Matcher m = PAIR.matcher(json);
        while (m.find()) out.put(m.group(1), m.group(2));
        return out;
    }
}

