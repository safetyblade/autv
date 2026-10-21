## 2025-05-18 - Safe Regex Replacement for User Input in Python
**Vulnerability:** Unsanitized strings with backslashes or capture references (e.g., `\1`) passed as replacement strings to `re.sub` trigger `re.error: invalid group reference` or perform unintended match substitution.
**Learning:** In Python `re.sub(pattern, repl, string)`, when `repl` is a string, backslashes are interpreted as group backreferences. Using a callable (`lambda _: safe_str`) forces `re.sub` to treat the replacement as a literal string.
**Prevention:** Always use a lambda function `lambda _: replacement_text` for the `repl` parameter in `re.sub` when replacing matched patterns with variable or external input.

## 2026-03-30 - Enforce Word Boundaries on M3U Attribute Key Matching
**Vulnerability:** Fast string search for M3U attribute key markers (`key="`) matched prefixed attribute names like `alt-tvg-id="..."` or `custom-tvg-chno="..."`, leading to attribute spoofing and channel metadata extraction errors.
**Learning:** Checking for `key="` with `str.find()` without verifying that the preceding character is whitespace or start-of-string allows prefixed attribute names to match as valid keys.
**Prevention:** Always verify that `idx == 0 or line[idx - 1] in " \t\n\r"` when searching for key-value markers in M3U header lines.
