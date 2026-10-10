## 2025-05-19 - M3U Field Sanitization for Upstream Metadata
**Vulnerability:** Newlines (`\r`, `\n`) and double quotes (`"`) in upstream channel titles or attributes allow CRLF line injection (injecting arbitrary M3U directives) or attribute breakouts in generated playlist files.
**Learning:** Unfiltered string interpolation in M3U `#EXTINF` header fields allows untrusted stream provider metadata to break header structure or introduce arbitrary lines into playlist files.
**Prevention:** Sanitize string inputs in `normalise()` or M3U generation functions by stripping newlines (`.replace("\r", " ").replace("\n", " ")`) and converting double quotes to single quotes (`.replace('"', "'")`).

## 2025-05-18 - Safe Regex Replacement for User Input in Python
**Vulnerability:** Unsanitized strings with backslashes or capture references (e.g., `\1`) passed as replacement strings to `re.sub` trigger `re.error: invalid group reference` or perform unintended match substitution.
**Learning:** In Python `re.sub(pattern, repl, string)`, when `repl` is a string, backslashes are interpreted as group backreferences. Using a callable (`lambda _: safe_str`) forces `re.sub` to treat the replacement as a literal string.
**Prevention:** Always use a lambda function `lambda _: replacement_text` for the `repl` parameter in `re.sub` when replacing matched patterns with variable or external input.
