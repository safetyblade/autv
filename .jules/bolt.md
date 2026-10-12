## 2025-05-18 - Fast String Searching for M3U Attribute Parsing
**Learning:** Replacing dynamic regex compiles/searches with simple `str.find()` when parsing M3U key-value pairs (`key="value"`) yields a ~54% execution time reduction in playlist parsing with zero regex overhead.
**Action:** Prefer direct string slice and search operations over `re.search` for simple quoted M3U key-value attributes.

## 2025-05-18 - Direct Element Copying vs XML Serialization in ElementTree
**Learning:** Replacing `ET.fromstring(ET.tostring(elem, encoding="utf-8"))` with `copy.deepcopy(elem)` when cloning XML elements during `iterparse` yields a ~5.8x to 8x speedup (~83% to 87% time reduction) by eliminating byte serialization and re-parsing.
**Action:** Use `copy.deepcopy(elem)` instead of `ET.fromstring(ET.tostring(...))` when cloning `xml.etree.ElementTree.Element` objects.

## 2025-05-18 - Fast String Slicing and rfind for M3U Guide Attribute Updating
**Learning:** Replacing regex searches and callable replacements (`re.search`/`re.sub`) with `str.find()`, `str.rfind()`, and string slicing when modifying M3U attributes and channel display names yields a 2.7x to 4.6x speedup by eliminating dynamic regex compilation and match group overhead.
**Action:** Use direct string search (`str.find`/`str.rfind`) and slicing instead of regex search/replace when modifying `#EXTINF` line attributes and display names in M3U files.
