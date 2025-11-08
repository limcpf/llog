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
        boolean inBq = false;
        StringBuilder list = new StringBuilder();
        StringBuilder bq = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
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

            // Table detection: header row | separator row present
            if (!line.isBlank() && i + 1 < lines.length) {
                String next = lines[i+1].trim();
                if (looksLikeTableHeader(line) && isTableSeparatorRow(next)) {
                    // close open structures
                    if (para.length() > 0) { out.add("<p>" + inline(para.toString().trim()) + "</p>"); para.setLength(0);} 
                    if (inUl) { out.add("<ul>\n" + list + "</ul>"); list.setLength(0); inUl = false; }
                    if (inOl) { out.add("<ol>\n" + list + "</ol>"); list.setLength(0); inOl = false; }

                    java.util.List<String> headers = splitTableRow(line);
                    java.util.List<String> aligns = parseAlignRow(lines[i+1], headers.size());
                    i += 2; // advance past header + separator
                    java.util.List<java.util.List<String>> rows = new java.util.ArrayList<>();
                    while (i < lines.length) {
                        String r = lines[i];
                        if (r.isBlank()) break;
                        if (!r.contains("|")) break;
                        rows.add(splitTableRow(r));
                        i++;
                    }
                    i--; // step back one since for-loop will increment
                    out.add(renderTable(headers, aligns, rows));
                    continue;
                }
            }

            // Horizontal rule (---, ***, ___)
            String tline = line.trim();
            if (tline.matches("^[-*_]{3,}$")) {
                if (para.length() > 0) { out.add("<p>" + inline(para.toString().trim()) + "</p>"); para.setLength(0);} 
                if (inUl) { out.add("<ul>\n" + list + "</ul>"); list.setLength(0); inUl = false; }
                if (inOl) { out.add("<ol>\n" + list + "</ol>"); list.setLength(0); inOl = false; }
                if (inBq) { out.add(renderBlockquote(bq.toString())); bq.setLength(0); inBq = false; }
                out.add("<hr />");
                continue;
            }

            if (line.isBlank()) {
                if (para.length() > 0) { out.add("<p>" + inline(para.toString().trim()) + "</p>"); para.setLength(0);} 
                if (inUl) { out.add("<ul>\n" + list + "</ul>"); list.setLength(0); inUl = false; }
                if (inOl) { out.add("<ol>\n" + list + "</ol>"); list.setLength(0); inOl = false; }
                if (inBq) { out.add(renderBlockquote(bq.toString())); bq.setLength(0); inBq = false; }
                continue;
            }
            // headings
            if (ltrim.startsWith("#")) {
                if (para.length() > 0) { out.add("<p>" + inline(para.toString().trim()) + "</p>"); para.setLength(0);} 
                if (inUl) { out.add("<ul>\n" + list + "</ul>"); list.setLength(0); inUl = false; }
                if (inOl) { out.add("<ol>\n" + list + "</ol>"); list.setLength(0); inOl = false; }
                if (inBq) { out.add(renderBlockquote(bq.toString())); bq.setLength(0); inBq = false; }
                int level = Math.min(6, (int) ltrim.chars().takeWhile(ch -> ch == '#').count());
                String text = ltrim.substring(level).trim();
                out.add("<h"+level+">" + inline(text) + "</h"+level+">");
                continue;
            }
            // blockquote line
            if (ltrim.startsWith(">")) {
                if (para.length() > 0) { out.add("<p>" + inline(para.toString().trim()) + "</p>"); para.setLength(0);} 
                if (inUl) { out.add("<ul>\n" + list + "</ul>"); list.setLength(0); inUl = false; }
                if (inOl) { out.add("<ol>\n" + list + "</ol>"); list.setLength(0); inOl = false; }
                inBq = true;
                String content = ltrim.substring(1).stripLeading();
                bq.append(content).append(' ');
                continue;
            }
            // unordered list item
            java.util.regex.Matcher mUL = java.util.regex.Pattern.compile("^\\s*[-*+]\\s+(.+)$").matcher(line);
            if (mUL.find()) {
                if (para.length() > 0) { out.add("<p>" + inline(para.toString().trim()) + "</p>"); para.setLength(0);} 
                if (inOl) { out.add("<ol>\n" + list + "</ol>"); list.setLength(0); inOl = false; }
                if (inBq) { out.add(renderBlockquote(bq.toString())); bq.setLength(0); inBq = false; }
                inUl = true;
                list.append("  <li>").append(inline(mUL.group(1).trim())).append("</li>\n");
                continue;
            }
            // ordered list item
            java.util.regex.Matcher mOL = java.util.regex.Pattern.compile("^\\s*\\d+\\.\\s+(.+)$").matcher(line);
            if (mOL.find()) {
                if (para.length() > 0) { out.add("<p>" + inline(para.toString().trim()) + "</p>"); para.setLength(0);} 
                if (inUl) { out.add("<ul>\n" + list + "</ul>"); list.setLength(0); inUl = false; }
                if (inBq) { out.add(renderBlockquote(bq.toString())); bq.setLength(0); inBq = false; }
                inOl = true;
                list.append("  <li>").append(inline(mOL.group(1).trim())).append("</li>\n");
                continue;
            }
            para.append(line).append(' ');
        }
        if (para.length() > 0) out.add("<p>" + inline(para.toString().trim()) + "</p>");
        if (inUl) out.add("<ul>\n" + list + "</ul>");
        if (inOl) out.add("<ol>\n" + list + "</ol>");
        if (inBq) out.add(renderBlockquote(bq.toString()));
        return String.join("\n", out);
    }

    private static boolean looksLikeTableHeader(String line) {
        String t = line.trim();
        return t.contains("|");
    }

    private static boolean isTableSeparatorRow(String line) {
        if (!line.contains("|")) return false;
        var cells = splitTableRow(line);
        int ok = 0;
        for (String c : cells) {
            String x = c.trim().replace(" ", "");
            if (x.isEmpty()) continue;
            // at least 3 dashes, optional leading/trailing colon
            String y = x.replace(":", "");
            if (y.matches("-{-}{1,}".replace("{-}{1,}", "{3,}"))) { // trick to avoid escaping braces in patch
                ok++;
            } else if (y.matches("-{3,}")) {
                ok++;
            }
        }
        return ok >= Math.max(1, cells.size() - 1); // tolerate empty trailing cell
    }

    private static java.util.List<String> splitTableRow(String line) {
        String t = line.trim();
        if (t.startsWith("|")) t = t.substring(1);
        if (t.endsWith("|")) t = t.substring(0, t.length()-1);
        String[] parts = t.split("\\|", -1);
        java.util.List<String> cells = new java.util.ArrayList<>();
        for (String p : parts) cells.add(p.trim());
        return cells;
    }

    private static java.util.List<String> parseAlignRow(String line, int cols) {
        var raw = splitTableRow(line);
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int i=0;i<Math.max(cols, raw.size());i++) {
            String cell = i < raw.size() ? raw.get(i) : "";
            String x = cell.trim().replace(" ", "");
            boolean left = x.startsWith(":");
            boolean right = x.endsWith(":");
            String core = x.replace(":", "");
            if (!core.matches("-{3,}")) { out.add(""); continue; }
            if (left && right) out.add("center");
            else if (right) out.add("right");
            else if (left) out.add("left");
            else out.add("");
        }
        // ensure size == cols
        while (out.size() < cols) out.add("");
        if (out.size() > cols) return out.subList(0, cols);
        return out;
    }

    private static String renderTable(java.util.List<String> headers, java.util.List<String> aligns, java.util.List<java.util.List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table>\n");
        sb.append("  <thead><tr>\n");
        for (int i=0;i<headers.size();i++) {
            String a = i < aligns.size() ? aligns.get(i) : "";
            String style = a.isEmpty()?"":" style=\"text-align: "+a+"\"";
            sb.append("    <th").append(style).append(">").append(inline(headers.get(i))).append("</th>\n");
        }
        sb.append("  </tr></thead>\n");
        sb.append("  <tbody>\n");
        for (var r : rows) {
            sb.append("    <tr>\n");
            for (int i=0;i<headers.size();i++) {
                String cell = i < r.size() ? r.get(i) : "";
                String a = i < aligns.size() ? aligns.get(i) : "";
                String style = a.isEmpty()?"":" style=\"text-align: "+a+"\"";
                sb.append("      <td").append(style).append(">").append(inline(cell)).append("</td>\n");
            }
            sb.append("    </tr>\n");
        }
        sb.append("  </tbody>\n</table>");
        return sb.toString();
    }

    public static String firstParagraphText(String md) {
        String html = toHtml(md);
        String text = extractBetween(html, "<p>", "</p>");
        if (text == null || text.isBlank()) text = extractBetween(html, "<li>", "</li>");
        if (text == null || text.isBlank()) text = extractBetween(html, "<blockquote>", "</blockquote>");
        if (text == null) text = html.replaceAll("<[^>]+>", " ");
        text = text.replaceAll("\s+", " ").trim();
        if (text.length() > 180) text = text.substring(0, 177) + "…";
        return text;
    }

    private static String extractBetween(String s, String open, String close) {
        int i = s.indexOf(open);
        if (i < 0) return null;
        int j = s.indexOf(close, i + open.length());
        if (j < 0) return null;
        String inner = s.substring(i + open.length(), j);
        return inner.replaceAll("<[^>]+>", "");
    }

    private static String renderBlockquote(String content) {
        String inner = inline(content.trim());
        if (inner.isBlank()) return "";
        return "<blockquote>" + inner + "</blockquote>";
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
