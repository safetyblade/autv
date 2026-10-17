## 2025-05-18 - Fast String Searching for M3U Attribute Parsing
**Learning:** Replacing dynamic regex compiles/searches with simple `str.find()` when parsing M3U key-value pairs (`key="value"`) yields a ~54% execution time reduction in playlist parsing with zero regex overhead.
**Action:** Prefer direct string slice and search operations over `re.search` for simple quoted M3U key-value attributes.

## 2025-05-18 - Direct Element Copying vs XML Serialization in ElementTree
**Learning:** Replacing `ET.fromstring(ET.tostring(elem, encoding="utf-8"))` with `copy.deepcopy(elem)` when cloning XML elements during `iterparse` yields a ~5.8x to 8x speedup (~83% to 87% time reduction) by eliminating byte serialization and re-parsing.
**Action:** Use `copy.deepcopy(elem)` instead of `ET.fromstring(ET.tostring(...))` when cloning `xml.etree.ElementTree.Element` objects.

## 2025-05-18 - Single-Pass Regex Substitution and Regex Caching in M3U Attribute Updating
**Learning:** Using `re.compile().subn()` for single-pass search-and-replace in `set_attr()` eliminates redundant `re.search()` checks and dynamic regex compilation overhead, reducing M3U attribute modification time by ~61%.
**Action:** Use `subn()` on cached pre-compiled regex objects when conditionally updating key-value attributes in strings.

## 2025-05-18 - Pre-Casefolding and Hash Map Lookup in M3U Channel Matching
**Learning:** Replacing repeated O(N) linear list scans with `str.casefold()` on every comparison in `add_selected()` with pre-casefolded entries and a `dict.setdefault()` lookup dictionary reduces channel selection lookup time by ~89% (~9.1x speedup).
**Action:** Pre-casefold list items and build a `dict.setdefault()` lookup map for exact case-insensitive match before fallback substring searching on large playlists.

## 2025-05-18 - Pre-compiled Regexes for High-Frequency String Normalization
**Learning:** Replacing dynamic `re.sub(pattern, ...)` with pre-compiled regex objects at module level in `canonical()` reduces string normalization execution time by ~33% (~1.49x speedup) during high-volume string processing.
**Action:** Always pre-compile regex objects at module level when performing repeated regex substitutions in loops or high-frequency utility functions.
