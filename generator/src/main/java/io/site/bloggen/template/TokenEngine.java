package io.site.bloggen.template;

import java.util.Map;

public final class TokenEngine {
    private TokenEngine() {}

    public static String apply(String text, Map<String,String> tokens) {
        String out = text;
        for (var e : tokens.entrySet()) {
            String key = "{{" + e.getKey() + "}}";
            String val = e.getValue();
            if (val == null) val = "";
            out = out.replace(key, val);
        }
        return out;
    }
}

