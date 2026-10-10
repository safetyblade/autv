## 2025-05-18 - Safe Regex Replacement for User Input in Python
**Vulnerability:** Unsanitized strings with backslashes or capture references (e.g., `\1`) passed as replacement strings to `re.sub` trigger `re.error: invalid group reference` or perform unintended match substitution.
**Learning:** In Python `re.sub(pattern, repl, string)`, when `repl` is a string, backslashes are interpreted as group backreferences. Using a callable (`lambda _: safe_str`) forces `re.sub` to treat the replacement as a literal string.
**Prevention:** Always use a lambda function `lambda _: replacement_text` for the `repl` parameter in `re.sub` when replacing matched patterns with variable or external input.
