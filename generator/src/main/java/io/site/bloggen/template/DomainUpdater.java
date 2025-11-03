package io.site.bloggen.template;

import io.site.bloggen.core.SiteConfig;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DomainUpdater {
    private DomainUpdater() {}

    public static String apply(String html, SiteConfig cfg) {
        String domain = cfg.domain().replaceAll("/$", "");
        String siteName = cfg.siteName();
        // canonical
        html = html.replaceAll("(<link\\s+rel=\\\"canonical\\\"\\s+href=\\\")https?://[^\\\"<>]*", "$1" + Matcher.quoteReplacement(domain));
        // og:url
        html = html.replaceAll("(<meta\\s+property=\\\"og:url\\\"\\s+content=\\\")https?://[^\\\"<>]*", "$1" + Matcher.quoteReplacement(domain));
        // og:image
        Pattern p = Pattern.compile("(<meta\\s+property=\\\"og:image\\\"\\s+content=\\\")(.*?)\\\"\\s*/?>");
        Matcher m = p.matcher(html);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String url = m.group(2);
            String repl = url;
            if (url.startsWith("http://") || url.startsWith("https://")) {
                repl = domain + url.replaceFirst("^https?://[^/]*", "");
            } else if (url.startsWith("/")) {
                repl = domain + url;
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + repl + "\" />"));
        }
        m.appendTail(sb);
        html = sb.toString();
        // RSS title
        html = html.replaceAll("(<link\\s+rel=\\\"alternate\\\"\\s+type=\\\"application/rss\\+xml\\\"\\s+title=\\\")[^\\\"]*(\\\")",
                "$1" + Matcher.quoteReplacement(siteName) + "$2");
        return html;
    }
}

