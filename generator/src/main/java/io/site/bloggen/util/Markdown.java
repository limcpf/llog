package io.site.bloggen.util;

import java.util.ArrayList;
import java.util.List;

public final class Markdown {
    private Markdown() {}

    public static String toHtml(String md) {
        // Remove front matter if present
        String s = md;
        if (s.stripLeading().startsWith("---")) {
            int start = s.indexOf('\n');
            int end = s.indexOf("\n---", start);
            if (start >= 0 && end > start) {
                s = s.substring(end + 4); // skip "\n---"
            }
        }
        List<String> out = new ArrayList<>();
        String[] lines = s.split("\r?\n");
        boolean inCode = false;
        StringBuilder code = new StringBuilder();
        String codeLang = "";
        StringBuilder para = new StringBuilder();
        boolean inUl = false, inOl = false;
        StringBuilder list = new StringBuilder();
        for (String line : lines) {
            String ltrim = line.stripLeading();
            if (ltrim.startsWith("```") ){
                if (!inCode) {
                    inCode = true; code.setLength(0); codeLang = ltrim.substring(3).trim();
                } else {
                    inCode = false;
                    String cls = codeLang.isEmpty() ? "" : " class=\"language-" + escape(codeLang) + "\"";
                    out.add("<pre><code" + cls + ">" + escape(code.toString()) + "</code></pre>");
                    codeLang = "";
                }
                continue;
            }
            if (inCode) { code.append(line).append('\n'); continue; }

            if (line.isBlank()) {
                if (para.length() > 0) { out.add("<p>" + inline(para.toString().trim()) + "</p>"); para.setLength(0);} 
                if (inUl) { out.add("<ul>\n" + list + "</ul>"); list.setLength(0); inUl = false; }
                if (inOl) { out.add("<ol>\n" + list + "</ol>"); list.setLength(0); inOl = false; }
                continue;
            }
            // headings
            if (ltrim.startsWith("#")) {
                if (para.length() > 0) { out.add("<p>" + inline(para.toString().trim()) + "</p>"); para.setLength(0);} 
                if (inUl) { out.add("<ul>\n" + list + "</ul>"); list.setLength(0); inUl = false; }
                if (inOl) { out.add("<ol>\n" + list + "</ol>"); list.setLength(0); inOl = false; }
                int level = Math.min(6, (int) ltrim.chars().takeWhile(ch -> ch == '#').count());
                String text = ltrim.substring(level).trim();
                out.add("<h"+level+">" + inline(text) + "</h"+level+">");
                continue;
            }
            // unordered list item
            java.util.regex.Matcher mUL = java.util.regex.Pattern.compile("^\\s*[-*+]\\s+(.+)$").matcher(line);
            if (mUL.find()) {
                if (para.length() > 0) { out.add("<p>" + inline(para.toString().trim()) + "</p>"); para.setLength(0);} 
                if (inOl) { out.add("<ol>\n" + list + "</ol>"); list.setLength(0); inOl = false; }
                inUl = true;
                list.append("  <li>").append(inline(mUL.group(1).trim())).append("</li>\n");
                continue;
            }
            // ordered list item
            java.util.regex.Matcher mOL = java.util.regex.Pattern.compile("^\\s*\\d+\\.\\s+(.+)$").matcher(line);
            if (mOL.find()) {
                if (para.length() > 0) { out.add("<p>" + inline(para.toString().trim()) + "</p>"); para.setLength(0);} 
                if (inUl) { out.add("<ul>\n" + list + "</ul>"); list.setLength(0); inUl = false; }
                inOl = true;
                list.append("  <li>").append(inline(mOL.group(1).trim())).append("</li>\n");
                continue;
            }
            para.append(line).append(' ');
        }
        if (para.length() > 0) out.add("<p>" + inline(para.toString().trim()) + "</p>");
        if (inUl) out.add("<ul>\n" + list + "</ul>");
        if (inOl) out.add("<ol>\n" + list + "</ol>");
        return String.join("\n", out);
    }

    public static String firstParagraphText(String md) {
        String html = toHtml(md);
        int i = html.indexOf("<p>");
        int j = html.indexOf("</p>", i+3);
        if (i >= 0 && j > i) {
            String inner = html.substring(i+3, j);
            return inner.replaceAll("<[^>]+>", "");
        }
        return "";
    }

    private static String inline(String s) {
        // images ![alt](url) -> <img src="url" alt="alt" />
        s = s.replaceAll("!\\[([^]]*)\\]\\(([^)]+)\\)", "<img src=\"$2\" alt=\"$1\" />");
        // links [text](url)
        s = s.replaceAll("\\[([^]]*)\\]\\(([^)]+)\\)", "<a href=\"$2\">$1</a>");
        // bold **text**
        s = s.replaceAll("\\*\\*([^*]+)\\*\\*", "<strong>$1</strong>");
        // emphasis *text*
        s = s.replaceAll("\\*([^*]+)\\*", "<em>$1</em>");
        // code `code`
        s = s.replaceAll("`([^`]+)`", "<code>$1</code>");
        return s;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
