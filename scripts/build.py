#!/usr/bin/env python3
"""
Minimal static updater:
- Reads site.json (domain, site_name, rss_title, og_default)
- Updates canonical, og:url, og:image domain base in all HTML
- Updates RSS <link> title
- Updates feed.xml, sitemap.xml, robots.txt domains

No external deps. Idempotent. Safe string replacement.
"""
from __future__ import annotations
import json, re, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def load_site_config(path: Path) -> dict:
    with path.open('r', encoding='utf-8') as f:
        return json.load(f)

def replace_domain_in_html(text: str, domain: str, site_name: str) -> str:
    # canonical
    text = re.sub(r'(<link\s+rel="canonical"\s+href=")https?://[^"<>]*', r"\1" + domain, text)
    # og:url
    text = re.sub(r'(<meta\s+property="og:url"\s+content=")https?://[^"<>]*', r"\1" + domain, text)
    # og:image: if absolute, swap domain; if root-relative, prefix domain
    def _og_image(m):
        prefix = m.group(1)
        url = m.group(2)
        if url.startswith('http://') or url.startswith('https://'):
            # strip scheme+host, keep path
            path = re.sub(r'^https?://[^/]*', '', url)
            return prefix + domain + path + '" />'
        elif url.startswith('/'):
            return prefix + domain + url + '" />'
        else:
            return prefix + url + '" />'
    text = re.sub(r'(<meta\s+property="og:image"\s+content=")(.*?)"\s*/?>', _og_image, text)
    # RSS link title
    text = re.sub(r'(<link\s+rel="alternate"\s+type="application/rss\+xml"\s+title=")[^"]*(")', r"\1" + site_name + r"\2", text)
    return text

def replace_domain_in_feed(text: str, domain: str) -> str:
    text = re.sub(r'(<link>)https?://[^<]*(</link>)', r"\1" + domain + r"/\2", 1)  # channel link (first match)
    text = re.sub(r'https?://[^<]*/posts/([A-Za-z0-9\-_.]+\.html)', domain + r'/posts/\1', text)
    return text

def replace_domain_in_sitemap(text: str, domain: str) -> str:
    return re.sub(r'https?://[^<]+', lambda m: re.sub(r'^https?://[^/]+', domain, m.group(0)), text)

def replace_domain_in_robots(text: str, domain: str) -> str:
    return re.sub(r'^(Sitemap:\s+)https?://.*$', r"\1" + domain + "/sitemap.xml", text, flags=re.MULTILINE)

def main() -> int:
    cfg = load_site_config(ROOT / 'site.json')
    domain = cfg.get('domain', '').rstrip('/')
    site_name = cfg.get('site_name', '')
    if not domain:
        print('error: site.json missing domain', file=sys.stderr)
        return 2

    # HTML files
    html_files = list(ROOT.rglob('*.html'))
    for path in html_files:
        text = path.read_text(encoding='utf-8')
        new_text = replace_domain_in_html(text, domain, site_name)
        if new_text != text:
            path.write_text(new_text, encoding='utf-8')

    # feed.xml
    feed = ROOT / 'feed.xml'
    if feed.exists():
        t = feed.read_text(encoding='utf-8')
        nt = replace_domain_in_feed(t, domain)
        if nt != t:
            feed.write_text(nt, encoding='utf-8')

    # sitemap.xml
    sm = ROOT / 'sitemap.xml'
    if sm.exists():
        t = sm.read_text(encoding='utf-8')
        nt = replace_domain_in_sitemap(t, domain)
        if nt != t:
            sm.write_text(nt, encoding='utf-8')

    # robots.txt
    rb = ROOT / 'robots.txt'
    if rb.exists():
        t = rb.read_text(encoding='utf-8')
        nt = replace_domain_in_robots(t, domain)
        if nt != t:
            rb.write_text(nt, encoding='utf-8')

    print('Updated domain/meta across HTML, feed, sitemap, robots.')
    return 0

if __name__ == '__main__':
    raise SystemExit(main())

