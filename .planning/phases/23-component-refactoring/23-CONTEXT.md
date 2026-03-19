# Phase 23: Component Refactoring - Context

**Gathered:** 2026-03-19
**Status:** Ready for planning

<domain>
## Phase Boundary

Decompose BulletContent.tsx (767 lines) and BulletNode.tsx (486 lines) into focused, testable sub-components and utility modules. Pure structural refactor — all existing behavior must remain identical. At least one extracted module must have standalone unit tests.

</domain>

<decisions>
## Implementation Decisions

### BulletContent.tsx decomposition
- Extract `isCursorAtStart`, `isCursorAtEnd`, `splitAtCursor` to `client/src/components/DocumentView/cursorUtils.ts` (already exported, pure functions)
- Extract `setCursorAtPosition`, `placeCursorAtEnd` to same `cursorUtils.ts` (currently private, DOM helpers)
- Extract `triggerDatePicker` to `client/src/components/DocumentView/datePicker.ts` (imperative DOM helper)
- Extract keyboard event handling logic into a `useKeyboardHandlers` hook in `client/src/components/DocumentView/useKeyboardHandlers.ts`
- BulletContent.tsx stays as the rendering component but drops to ~200-300 lines
- Target: under 300 lines per success criteria

### BulletNode.tsx decomposition
- Extract swipe gesture state + handlers (~lines 73-290) to `client/src/components/DocumentView/useSwipeGesture.ts` hook
- The hook returns `{ swipeX, swipeHandlers, swipeBackground, swipeIcon, exitDirection, isExiting }` — BulletNode consumes
- BulletNode stays as the thin orchestrator rendering sub-components (AttachmentRow, NoteRow, UndoToast, ContextMenu)
- Target: under 250 lines per success criteria

### Test coverage
- Write unit tests for `cursorUtils.ts` — pure functions, testable without React/DOM
- Use vitest (already configured in client)
- Test file: `client/src/components/DocumentView/cursorUtils.test.ts`
- This satisfies success criteria: "at least one extracted module has standalone unit tests that pass without mounting a React component"

### Critical constraint: saveTimerRef lifecycle
- `saveTimerRef` and `lastSavedContentRef` MUST stay in the component that owns the save lifecycle
- The keyboard handler hook receives these as parameters, not as internal state
- Breaking this contract causes character-then-Enter timing bugs (from Pitfalls research)

### Claude's Discretion
- Exact hook interface design for useKeyboardHandlers and useSwipeGesture
- Whether to extract more sub-components (e.g., swipe background rendering)
- Additional test cases beyond the minimum required

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Components to refactor
- `client/src/components/DocumentView/BulletContent.tsx` — 767 lines, cursor utils + date picker + keyboard handlers + render
- `client/src/components/DocumentView/BulletNode.tsx` — 486 lines, swipe gesture + sub-component orchestration
- `client/src/components/DocumentView/gestures.ts` — Existing gesture utilities (swipeThresholdReached)

### Research
- `.planning/research/FEATURES.md` — BulletContent/BulletNode refactor analysis, extractable pieces identified
- `.planning/research/PITFALLS.md` — saveTimerRef/lastSavedContentRef timing constraint, refactoring traps

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `gestures.ts` already exists as an extracted utility — pattern to follow for cursorUtils.ts
- `isCursorAtStart`, `isCursorAtEnd`, `splitAtCursor` already exported from BulletContent — consumers import from there
- Vitest configured in client (`npm test` runs `vitest run`)

### Established Patterns
- Utility extraction: `gestures.ts` shows the pattern — pure functions in a separate file, imported by components
- Hook extraction: `useUndo.ts`, `useBullets.ts` show the pattern — custom hooks encapsulating state + handlers

### Integration Points
- BulletContent imports: cursor utils are imported by BulletContent itself and potentially BulletTree — update import paths
- BulletNode imports: swipe gesture extracted but consumed only by BulletNode — clean internal extraction
- Existing tests: `client/src/components/DocumentView/gestures.test.ts` exists — follow same pattern for cursorUtils

### Current function inventory in BulletContent.tsx
- `isCursorAtStart(el)` — pure, exported (lines 23-32)
- `isCursorAtEnd(el)` — pure, exported (lines 34-47)
- `splitAtCursor(el)` — pure, exported (lines 49-61)
- `setCursorAtPosition(el, offset)` — DOM helper, private (lines 63-79)
- `placeCursorAtEnd(el)` — DOM helper, private (lines 81-90)
- `triggerDatePicker(onDate)` — imperative DOM, private (lines 92-115)
- `BulletContent({...})` — main component (lines 116-767)

### Current swipe section in BulletNode.tsx
- Lines 73-290: swipe state, touch handlers, threshold logic, exit animation, background rendering
- All self-contained — can be extracted to a hook cleanly

</code_context>

<specifics>
## Specific Ideas

No specific requirements — follow the existing `gestures.ts` extraction pattern for utilities and the `useBullets.ts` pattern for hooks.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 23-component-refactoring*
*Context gathered: 2026-03-19*
