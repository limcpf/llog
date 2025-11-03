package io.site.bloggen.util;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class FlatJsonTest {
    @Test void parsesFlatPairs() {
        String j = "{\n  \"a\": \"1\",\n  \"b\": \"x y\"\n}";
        Map<String,String> m = FlatJson.parse(j);
        assertEquals("1", m.get("a"));
        assertEquals("x y", m.get("b"));
    }
}

