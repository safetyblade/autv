## 2025-05-18 - Compose Semantics for TV Guide Rows
**Learning:** Custom Compose list items representing current playback state (like TV channel rows) need explicit `selected = selected` semantics attached to the row so TalkBack and screen readers announce selection status when navigating lists.
**Action:** Always add `semantics { this.selected = isSelected }` to interactive row components that represent an active or playing item.

## 2025-05-19 - Content Description in Composite List Items
**Learning:** When custom Compose list rows contain nested metadata (like channel number, name, and current show title), setting `contentDescription` on the parent row must combine the channel details with the current programme title (`schedule.now?.title`) so TalkBack announces full context in a single utterance rather than skipping nested show info.
**Action:** Always include live programme titles alongside channel metadata in row-level `contentDescription` semantics for EPG channel list items.
