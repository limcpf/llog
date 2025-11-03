package io.site.bloggen.infra;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SlugTest {
    @Test void slugifiesAscii() {
        assertEquals("hello-world", Slug.of("Hello World!"));
    }
    @Test void slugifiesKoreanToFallback() {
        String s = Slug.of("첫 글!");
        assertEquals("post", s);
    }
}

