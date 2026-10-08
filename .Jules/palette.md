## 2025-05-18 - Compose Semantics for TV Guide Rows
**Learning:** Custom Compose list items representing current playback state (like TV channel rows) need explicit `selected = selected` semantics attached to the row so TalkBack and screen readers announce selection status when navigating lists.
**Action:** Always add `semantics { this.selected = isSelected }` to interactive row components that represent an active or playing item.
