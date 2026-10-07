## 2025-05-18 - XML Attribute Injection in XMLTV Generator
**Vulnerability:** `xml.sax.saxutils.escape()` was used when formatting XML attributes (`id` and `src`), but by default `escape()` only replaces `&`, `<`, and `>`, leaving double quotes unescaped in attribute context.
**Learning:** Formatting strings directly into XML attributes like `id="{escape(val)}"` permits attribute injection if `val` contains double quotes.
**Prevention:** Always pass `entities={'"': "&quot;"}` to `escape()` or use `quoteattr()` when embedding string values into XML attribute quotes.
