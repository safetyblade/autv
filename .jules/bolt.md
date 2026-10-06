## 2025-10-07 - Avoid M3U line slicing in playlist parser loops
**Learning:** In M3U playlist generators, doing `lines[i + 1:]` inside a line loop creates sublist allocations of size O(N) for every `#EXTINF:` header, leading to O(N^2) time and memory overhead. Also, repeated uncompiled `re.search` calls in attribute lookup add regex compilation overhead.
**Action:** Use stateful single-pass index iteration (`while i < num_lines:`) and direct string marker splitting for M3U attribute extraction.
