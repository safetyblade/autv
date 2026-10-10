package dev.prestwich.autv.guide

/** Row bounds include partially visible lazy items; membership alone is insufficient. */
data class GuideRowBounds(val index: Int, val offset: Int, val size: Int)
data class GuideScroll(val index: Int, val offset: Int)

object GuideViewport {
    fun reveal(target: Int, start: Int, end: Int, rows: List<GuideRowBounds>): GuideScroll {
        val row = rows.firstOrNull { it.index == target }
        if (row != null && row.offset >= start && row.offset + row.size <= end)
            // Also cancel an older pending offscreen request when keys reverse before layout.
            return GuideScroll(target, -(row.offset - start))
        if (rows.isEmpty() || target < rows.first().index || row != null && row.offset < start)
            return GuideScroll(target, 0)
        val size = row?.size ?: rows.first().size
        // Negative scroll offset places the target at the bottom, keeping neighbouring rows visible.
        return GuideScroll(target, -(end - start - size).coerceAtLeast(0))
    }
}
