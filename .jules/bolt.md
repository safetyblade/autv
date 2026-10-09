## 2025-05-18 - Fast String Searching for M3U Attribute Parsing
**Learning:** Replacing dynamic regex compiles/searches with simple `str.find()` when parsing M3U key-value pairs (`key="value"`) yields a ~54% execution time reduction in playlist parsing with zero regex overhead.
**Action:** Prefer direct string slice and search operations over `re.search` for simple quoted M3U key-value attributes.

## 2025-05-18 - Direct Element Copying vs XML Serialization in ElementTree
**Learning:** Replacing `ET.fromstring(ET.tostring(elem, encoding="utf-8"))` with `copy.deepcopy(elem)` when cloning XML elements during `iterparse` yields a ~5.8x to 8x speedup (~83% to 87% time reduction) by eliminating byte serialization and re-parsing.
**Action:** Use `copy.deepcopy(elem)` instead of `ET.fromstring(ET.tostring(...))` when cloning `xml.etree.ElementTree.Element` objects.

## 2025-05-18 - Memoized String Normalization and Fast String Slicing in M3U Guide Processing
**Learning:** Combining `@functools.lru_cache` for string canonicalization with `str.find()` / `str.rfind()` string slicing for attribute mutation (`set_attr`/`set_name`) reduces playlist guide processing time by ~59% (~2.45x speedup) by avoiding repetitive regex compilations and unicode normalizations across large channel lists.
**Action:** Use `@lru_cache` on channel normalization functions and favor `str.find()` / `str.rfind()` over `re.search` / `re.sub` for string attribute updates.
