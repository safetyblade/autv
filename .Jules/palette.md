## 2025-05-18 - Compose Semantics for TV Guide Rows
**Learning:** Custom Compose list items representing current playback state (like TV channel rows) need explicit `selected = selected` semantics attached to the row so TalkBack and screen readers announce selection status when navigating lists.
**Action:** Always add `semantics { this.selected = isSelected }` to interactive row components that represent an active or playing item.

## 2025-05-19 - Content Description in Composite List Items
**Learning:** When custom Compose list rows contain nested metadata (like channel number, name, and current show title), setting `contentDescription` on the parent row must combine the channel details with the current programme title (`schedule.now?.title`) so TalkBack announces full context in a single utterance rather than skipping nested show info.
**Action:** Always include live programme titles alongside channel metadata in row-level `contentDescription` semantics for EPG channel list items.

## 2025-05-20 - Favourite State Propagation in Custom Compose Rows
**Learning:** Custom Compose list item components (like `ChannelRow`) that render visual indicators (such as favorite stars) and screen reader descriptions (`contentDescription`) need explicit favorite state boolean properties passed down from list composables so accessibility and visual states update when items are favorited.
**Action:** Always pass `isFavourite` or item state explicitly to child row Composables to ensure `contentDescription` and UI indicators remain synchronized.

## 2025-05-21 - Text-To-Speech Character Spacing for Activation Codes and Timer Units
**Learning:** Short alphanumeric user codes (e.g., TV device activation codes) and formatted time strings (`M:SS`) are mispronounced by TalkBack screen readers as words or colon ratios.
**Action:** Provide explicit `semantics { contentDescription = ... }` with character-spaced characters for activation codes ("A 8 K 9 X") and spelled-out units (minutes/seconds) for countdown timers, and clear semantics on decorative unicode chevrons (`"›"`).

## 2025-05-22 - Unavailability and Playback Error Semantics in EPG Channel Rows
**Learning:** Custom Compose channel rows setting explicit `contentDescription` on the parent row must include channel availability (`!channel.tunable`) and error state (`failed != null`) alongside `clearAndSetSemantics` so TalkBack announces stream errors or unavailability rather than omitting error context or pronouncing raw child text.
**Action:** Always append error and availability states into `contentDescription` and use `clearAndSetSemantics` on composite list item rows.

## 2025-05-23 - Content Description for Provider Settings Service Cards
**Learning:** OutlinedCard components in settings lists that display service status omit descriptive metadata (`service.description`) from TalkBack when merged automatically; explicit `contentDescription` combining service name, description, and connection status provides full context for screen reader users browsing service options.
**Action:** Use `semantics(mergeDescendants = true) { contentDescription = "${service.name}, ${service.description} · $statusText" }` on provider service selection cards in settings menus.

## 2026-10-31 - Progress Semantics and Dialog Action Labels in Settings
**Learning:** Indeterminate LinearProgressIndicator composables displayed during background operations (such as channel refreshing) do not convey purpose to TalkBack screen readers unless given explicit `contentDescription` semantics; generic modal dismiss buttons ("Close") should clarify what surface is being dismissed ("Close settings") consistent with other modals across the app.
**Action:** Add explicit `contentDescription` semantics describing the ongoing asynchronous task to progress indicators and provide disambiguated action descriptions on dialog dismissal buttons.

## 2026-11-04 - Selection Semantics and RadioButton Roles in Settings Option Lists
**Learning:** Single-selection option lists built with custom buttons (like `FocusButton` for startup channel modes and channels) using visual checkmarks ("✓ ") do not expose selection state to screen readers unless given explicit `Role.RadioButton` and `selected = isSelected` semantics; TalkBack announces raw checkmark symbols or skips state context entirely without these semantics.
**Action:** Set `semantics { this.selected = isSelected; role = Role.RadioButton; contentDescription = ... }` on interactive option buttons within settings selection lists.

## 2026-11-11 - Accessibility Semantics for Provider Account Operations and Cast Transfer Indicators
**Learning:** Indeterminate LinearProgressIndicators shown when connecting broadcaster accounts (such as ABC or 10) or handoff to a Cast receiver are ignored or reported as nameless loading indicators by TalkBack unless given explicit `contentDescription` semantics describing the ongoing asynchronous operation.
**Action:** Always provide explicit `semantics { contentDescription = ... }` for indeterminate progress bars indicating account mutation or receiver handoff.
