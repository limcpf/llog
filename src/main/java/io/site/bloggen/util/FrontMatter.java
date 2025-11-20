package io.site.bloggen.util;

import java.util.HashMap;
import java.util.Map;

public final class FrontMatter {
    public static Map<String,String> parse(String markdown) {
        Map<String,String> out = new HashMap<>();
        if (markdown == null) return out;
        String s = markdown;
        if (s.startsWith("\uFEFF")) s = s.substring(1); // strip UTF-8 BOM if present
        s = s.stripLeading();
        if (!s.startsWith("---")) return out;
        int start = s.indexOf("\n");
        if (start < 0) return out;
        int end = s.indexOf("\n---", start);
        if (end < 0) return out;
        String block = s.substring(start, end).trim();
        for (String line : block.split("\n")) {
            if (line.isBlank() || line.trim().startsWith("#")) continue;
            int idx = line.indexOf(":");
            if (idx <= 0) continue;
            String key = line.substring(0, idx).trim();
            String val = line.substring(idx+1).trim();
            // strip quotes if present
            if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                val = val.substring(1, val.length()-1);
            }
            out.put(key, val);
        }
        return out;
    }
}
