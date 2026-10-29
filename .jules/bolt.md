## 2025-05-18 - Fast String Searching for M3U Attribute Parsing
**Learning:** Replacing dynamic regex compiles/searches with simple `str.find()` when parsing M3U key-value pairs (`key="value"`) yields a ~54% execution time reduction in playlist parsing with zero regex overhead.
**Action:** Prefer direct string slice and search operations over `re.search` for simple quoted M3U key-value attributes.

## 2025-05-18 - Direct Element Copying vs XML Serialization in ElementTree
**Learning:** Replacing `ET.fromstring(ET.tostring(elem, encoding="utf-8"))` with `copy.deepcopy(elem)` when cloning XML elements during `iterparse` yields a ~5.8x to 8x speedup (~83% to 87% time reduction) by eliminating byte serialization and re-parsing.
**Action:** Use `copy.deepcopy(elem)` instead of `ET.fromstring(ET.tostring(...))` when cloning `xml.etree.ElementTree.Element` objects.

## 2025-05-18 - String Slicing vs Regex Substitution in M3U Attribute Updating
**Learning:** Direct string finding (`str.find`) and slicing in `set_attr` and `set_name` outperform `re.subn` with cached pre-compiled regexes by ~1.64x to 1.92x (~39% to 48% speedup), eliminating regex engine overhead for simple string attribute updates.
**Action:** Prefer direct string search (`str.find`/`str.rfind`) and slice replacement over regex substitution when updating key-value attributes and trailing names in M3U lines.

## 2025-05-18 - Pre-Casefolding and Hash Map Lookup in M3U Channel Matching
**Learning:** Replacing repeated O(N) linear list scans with `str.casefold()` on every comparison in `add_selected()` with pre-casefolded entries and a `dict.setdefault()` lookup dictionary reduces channel selection lookup time by ~89% (~9.1x speedup).
**Action:** Pre-casefold list items and build a `dict.setdefault()` lookup map for exact case-insensitive match before fallback substring searching on large playlists.

## 2025-05-18 - Early EPG Programme Filtering in ElementTree Parsing
**Learning:** Pre-filtering EPG source programmes by playlist channel IDs and canonical channel names during `iterparse` reduces EPG XML processing overhead by ~3.25x (~69% execution time reduction) by bypassing `copy.deepcopy` and memory allocation for hundreds of unneeded non-playlist channels.
**Action:** When parsing large XMLTV EPG sources, pre-compute target channel lookup sets and skip element copying/storage for unneeded channel IDs during `iterparse`.

## 2025-05-18 - Early Substring Check Guard Before Regex Fullmatch
**Learning:** Adding a fast `if "jmp2.uk/plu-" not in url:` string membership check prior to `re.fullmatch` in `replace_pluto_redirect` reduced URL checking execution time by ~63.6% (~2.75x speedup) by bypassing regex engine matching overhead for non-Pluto-redirect URLs.
**Action:** Always place a fast string membership check (`if substring not in text: return`) before performing regex operations on large data collections where most items will not match.

## 2025-05-18 - Fast Query Parameter Slicing & Single-Pass M3U Quarantine Scanning
**Learning:** Extracting query parameters like `jwt=` directly via `str.find()` and slicing instead of calling `parse_qs(urlsplit(url).query)` yields a ~1.90x speedup (~47.5% time reduction) by bypassing full URL structure parsing and dictionary construction. Furthermore, advancing line pointers in single-pass M3U header-stream pairs eliminates redundant re-validation of URLs in quarantine loops (~1.87x overall quarantine speedup).
**Action:** Prefer direct string slicing (`str.find("param=")`) for extracting known URL query parameter values and advance loop counters past associated body lines in paired M3U list iterations.

## 2025-05-18 - Fast Substring Guard & String Search in Stream Candidate Filtering
**Learning:** Adding a fast substring check (`if "jmp2.uk" in line or ...`) prior to attribute extraction and URL parsing in stream candidate extraction, combined with replacing `re.search` for `tvg-chno` with `attr()`, yielded a ~35.7% execution time reduction (~1.56x speedup) by avoiding regex and URL structure parsing for non-candidate streams.
**Action:** Always filter stream lines with fast substring membership guards before performing regex searches or full URL decomposition.

## 2025-05-18 - Native String `isdigit()` and `lower()` Over Regex Fullmatch in CSV Validation
**Learning:** Replacing `re.fullmatch(r"[0-9]+", ...)` with native `str.isdigit()` and replacing `str.casefold()` with `str.lower()` for ASCII fixed sets in CSV guide row processing yields a ~17.4% execution speedup (~1.21x) by bypassing regex engine compilation and Unicode casefolding overhead.
**Action:** Use native `str.isdigit()` for numeric validation and `str.lower()` for ASCII string equality checks instead of regex fullmatch or casefold in high-frequency CSV iteration.
