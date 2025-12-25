package io.site.bloggen.util;

import java.util.ArrayList;
import java.util.List;

public final class Markdown {
    private Markdown() {
    }

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

        // List state: stack of "ul" or "ol"
        java.util.Stack<String> listTypeStack = new java.util.Stack<>();
        // Indent stack prevents mixed indentation issues, though mostly we just count
        java.util.Stack<Integer> indentStack = new java.util.Stack<>();

        boolean inBq = false;
        StringBuilder bq = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            // Normalize tabs to 4 spaces
            line = line.replace("\t", "    ");

            // 1. Code Block Handling (simplistic)
            String ltrim = line.stripLeading();
            if (ltrim.startsWith("```")) {
                // Close any open lists/paragraphs/blockquotes before code block
                if (para.length() > 0) {
                    out.add("<p>" + inline(para.toString().trim()) + "</p>");
                    para.setLength(0);
                }
                while (!listTypeStack.isEmpty()) {
                    out.add("</" + listTypeStack.pop() + ">");
                    indentStack.pop();
                }
                if (inBq) {
                    out.add(renderBlockquote(bq.toString()));
                    bq.setLength(0);
                    inBq = false;
                }

                if (!inCode) {
                    inCode = true;
                    code.setLength(0);
                    codeLang = ltrim.substring(3).trim();
                } else {
                    inCode = false;
                    String cls = codeLang.isEmpty() ? "" : " class=\"language-" + escape(codeLang) + "\"";
                    out.add("<pre><code" + cls + ">" + escape(code.toString()) + "</code></pre>");
                    codeLang = "";
                }
                continue;
            }
            if (inCode) {
                code.append(line).append('\n');
                continue;
            }

            // 2. Table detection
            if (!line.isBlank() && i + 1 < lines.length) {
                String next = lines[i + 1].trim();
                // If it looks like a table, close everything else
                if (looksLikeTableHeader(line) && isTableSeparatorRow(next)) {
                    if (para.length() > 0) {
                        out.add("<p>" + inline(para.toString().trim()) + "</p>");
                        para.setLength(0);
                    }
                    while (!listTypeStack.isEmpty()) {
                        out.add("</" + listTypeStack.pop() + ">");
                        indentStack.pop();
                    }
                    if (inBq) {
                        out.add(renderBlockquote(bq.toString()));
                        bq.setLength(0);
                        inBq = false;
                    }

                    java.util.List<String> headers = splitTableRow(line);
                    java.util.List<String> aligns = parseAlignRow(lines[i + 1], headers.size());
                    i += 2;
                    java.util.List<java.util.List<String>> rows = new java.util.ArrayList<>();
                    while (i < lines.length) {
                        String r = lines[i];
                        if (r.isBlank())
                            break;
                        if (!r.contains("|"))
                            break;
                        rows.add(splitTableRow(r));
                        i++;
                    }
                    i--;
                    out.add(renderTable(headers, aligns, rows));
                    continue;
                }
            }

            // 3. Horizontal Rule
            String tline = line.trim();
            if (tline.matches("^[-*_]{3,}$")) {
                if (para.length() > 0) {
                    out.add("<p>" + inline(para.toString().trim()) + "</p>");
                    para.setLength(0);
                }
                while (!listTypeStack.isEmpty()) {
                    out.add("</" + listTypeStack.pop() + ">");
                    indentStack.pop();
                }
                if (inBq) {
                    out.add(renderBlockquote(bq.toString()));
                    bq.setLength(0);
                    inBq = false;
                }
                out.add("<hr />");
                continue;
            }

            // 4. Blank Line
            if (line.isBlank()) {
                if (para.length() > 0) {
                    out.add("<p>" + inline(para.toString().trim()) + "</p>");
                    para.setLength(0);
                }
                // Only close lists if double blank? No, standard md usually closes on blank if
                // next line isn't list.
                // But simplified: blank line breaks paragraph. Lists might continue if next is
                // list.
                // For safety and simplicity in this custom parser: close paragraph. Keep lists
                // open?
                // Let's close lists on blank line to match previous behavior if strict.
                // But real markdown allows loose lists. Let's try to KEEP lists open on blank
                // lines for now,
                // but if the next line is NOT a list item, they will be closed then.
                if (inBq) {
                    out.add(renderBlockquote(bq.toString()));
                    bq.setLength(0);
                    inBq = false;
                }
                continue;
            }

            // 5. Headings
            if (ltrim.startsWith("#")) {
                if (para.length() > 0) {
                    out.add("<p>" + inline(para.toString().trim()) + "</p>");
                    para.setLength(0);
                }
                while (!listTypeStack.isEmpty()) {
                    out.add("</" + listTypeStack.pop() + ">");
                    indentStack.pop();
                }
                if (inBq) {
                    out.add(renderBlockquote(bq.toString()));
                    bq.setLength(0);
                    inBq = false;
                }
                int level = Math.min(6, (int) ltrim.chars().takeWhile(ch -> ch == '#').count());
                String text = ltrim.substring(level).trim();
                out.add("<h" + level + ">" + inline(text) + "</h" + level + ">");
                continue;
            }

            // 6. Blockquote
            if (ltrim.startsWith(">")) {
                if (para.length() > 0) {
                    out.add("<p>" + inline(para.toString().trim()) + "</p>");
                    para.setLength(0);
                }
                while (!listTypeStack.isEmpty()) {
                    out.add("</" + listTypeStack.pop() + ">");
                    indentStack.pop();
                }
                inBq = true;
                String content = ltrim.substring(1).stripLeading();
                bq.append(content).append(' ');
                continue;
            }

            // 7. List Items (UL / OL)
            // Calc indent
            int indent = 0;
            while (indent < line.length() && line.charAt(indent) == ' ') {
                indent++;
            }
            // Normalizing indent: 2 spaces = 1 level is standard-ish for bullet, 3 or 4 for
            // ordered.
            // Let's approximate: strict 2 spaces or tab?
            // We'll treat every 2 spaces as a level increment potential.

            String content = line.trim();
            boolean isUl = content.matches("^[-*+]\\s+(.*)$");
            boolean isOl = content.matches("^\\d+\\.\\s+(.*)$");

            if (isUl || isOl) {
                if (para.length() > 0) {
                    out.add("<p>" + inline(para.toString().trim()) + "</p>");
                    para.setLength(0);
                }
                if (inBq) {
                    out.add(renderBlockquote(bq.toString()));
                    bq.setLength(0);
                    inBq = false;
                }

                String type = isUl ? "ul" : "ol";

                // Determine desired level from indent.
                // Root = indent 0. Nested 1 = indent 2-3?
                // Let's use the indent stack.
                // logic: if indent > last_indent -> nesting.

                int lastIndent = indentStack.isEmpty() ? -1 : indentStack.peek();

                if (indent > lastIndent) {
                    // Start new nested list
                    out.add("<" + type + ">");
                    listTypeStack.push(type);
                    indentStack.push(indent);
                } else {
                    // Close lists until we match indent or empty
                    while (!indentStack.isEmpty() && indent < indentStack.peek()) {
                        out.add("</" + listTypeStack.pop() + ">");
                        indentStack.pop();
                    }
                    // If mismatch type at same level, switch
                    if (!listTypeStack.isEmpty() && !listTypeStack.peek().equals(type)) {
                        out.add("</" + listTypeStack.pop() + ">");
                        indentStack.pop();
                        // reopen
                        out.add("<" + type + ">");
                        listTypeStack.push(type);
                        indentStack.push(indent);
                    }
                    // If stack empty now (was closed down), start new
                    if (listTypeStack.isEmpty()) {
                        out.add("<" + type + ">");
                        listTypeStack.push(type);
                        indentStack.push(indent);
                    }
                }

                // Extract text
                String itemText = "";
                if (isUl)
                    itemText = content.replaceFirst("^[-*+]\\s+", "");
                else
                    itemText = content.replaceFirst("^\\d+\\.\\s+", "");

                out.add("<li>" + inline(itemText) + "</li>");
                continue;
            }

            // 8. Normal text / Continuation
            // If it's not a list item, but we are inside a list...
            // Standard markdown: indented text continues item.
            // Parsing that is hard without lookahead. Use simple rule:
            // if we are in list, and line is not empty, AND not a new item...
            // close list? Or append to prev item?
            // Custom parser simplifcation: Break list on non-list line (unless blank? we
            // handled blank above)

            // If we are here, it's text.
            if (!listTypeStack.isEmpty()) {
                // If text and we have open list... strictly speaking, should close list unless
                // indented?
                // For now, close list to allow paragraph to start.
                while (!listTypeStack.isEmpty()) {
                    out.add("</" + listTypeStack.pop() + ">");
                    indentStack.pop();
                }
            }
            if (inBq) {
                // append to blockquote
                String val = line.stripLeading();
                bq.append(val).append(' ');
                continue;
            }

            para.append(line).append(' ');
        }

        // Final cleanup
        if (para.length() > 0)
            out.add("<p>" + inline(para.toString().trim()) + "</p>");
        while (!listTypeStack.isEmpty()) {
            out.add("</" + listTypeStack.pop() + ">");
            indentStack.pop();
        }
        if (inBq)
            out.add(renderBlockquote(bq.toString()));
        return String.join("\n", out);
    }

    private static boolean looksLikeTableHeader(String line) {
        String t = line.trim();
        return t.contains("|");
    }

    private static boolean isTableSeparatorRow(String line) {
        if (!line.contains("|"))
            return false;
        var cells = splitTableRow(line);
        int ok = 0;
        for (String c : cells) {
            String x = c.trim().replace(" ", "");
            if (x.isEmpty())
                continue;
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
        if (t.startsWith("|"))
            t = t.substring(1);
        if (t.endsWith("|"))
            t = t.substring(0, t.length() - 1);
        String[] parts = t.split("\\|", -1);
        java.util.List<String> cells = new java.util.ArrayList<>();
        for (String p : parts)
            cells.add(p.trim());
        return cells;
    }

    private static java.util.List<String> parseAlignRow(String line, int cols) {
        var raw = splitTableRow(line);
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < Math.max(cols, raw.size()); i++) {
            String cell = i < raw.size() ? raw.get(i) : "";
            String x = cell.trim().replace(" ", "");
            boolean left = x.startsWith(":");
            boolean right = x.endsWith(":");
            String core = x.replace(":", "");
            if (!core.matches("-{3,}")) {
                out.add("");
                continue;
            }
            if (left && right)
                out.add("center");
            else if (right)
                out.add("right");
            else if (left)
                out.add("left");
            else
                out.add("");
        }
        // ensure size == cols
        while (out.size() < cols)
            out.add("");
        if (out.size() > cols)
            return out.subList(0, cols);
        return out;
    }

    private static String renderTable(java.util.List<String> headers, java.util.List<String> aligns,
            java.util.List<java.util.List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table>\n");
        sb.append("  <thead><tr>\n");
        for (int i = 0; i < headers.size(); i++) {
            String a = i < aligns.size() ? aligns.get(i) : "";
            String style = a.isEmpty() ? "" : " style=\"text-align: " + a + "\"";
            sb.append("    <th").append(style).append(">").append(inline(headers.get(i))).append("</th>\n");
        }
        sb.append("  </tr></thead>\n");
        sb.append("  <tbody>\n");
        for (var r : rows) {
            sb.append("    <tr>\n");
            for (int i = 0; i < headers.size(); i++) {
                String cell = i < r.size() ? r.get(i) : "";
                String a = i < aligns.size() ? aligns.get(i) : "";
                String style = a.isEmpty() ? "" : " style=\"text-align: " + a + "\"";
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
        if (text == null || text.isBlank())
            text = extractBetween(html, "<li>", "</li>");
        if (text == null || text.isBlank())
            text = extractBetween(html, "<blockquote>", "</blockquote>");
        if (text == null)
            text = html.replaceAll("<[^>]+>", " ");
        text = text.replaceAll("\s+", " ").trim();
        if (text.length() > 180)
            text = text.substring(0, 177) + "…";
        return text;
    }

    private static String extractBetween(String s, String open, String close) {
        int i = s.indexOf(open);
        if (i < 0)
            return null;
        int j = s.indexOf(close, i + open.length());
        if (j < 0)
            return null;
        String inner = s.substring(i + open.length(), j);
        return inner.replaceAll("<[^>]+>", "");
    }

    private static String renderBlockquote(String content) {
        String inner = inline(content.trim());
        if (inner.isBlank())
            return "";
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
