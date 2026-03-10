---
status: awaiting_human_verify
trigger: "Multiple sidebar layout bugs on mobile and desktop views"
created: 2026-03-10T00:00:00Z
updated: 2026-03-10T00:00:00Z
---

## Current Focus

hypothesis: All four bugs have distinct, identifiable root causes now confirmed by reading the source
test: Code inspection of Sidebar.tsx, DocumentView.tsx, DocumentRow.tsx, AppPage.tsx, index.css
expecting: Fixes can be applied directly — all root causes are clear
next_action: Await human verification — bugs 1.2 and 1.3 were in Sidebar.tsx header menu (not DocumentRow). Fixed by anchoring dropdown to full-width header div and adding overlay dismiss.

## Symptoms

expected:
- Mobile: sidebar closes when a document is opened/viewed
- Mobile: "..." context menu in sidebar is fully within the viewport
- Mobile: tapping outside "..." menu closes it
- Desktop: no X close button visible

actual:
- Mobile 1.1: sidebar stays open and covers the opened document
- Mobile 1.2: "..." context menu in DocumentRow is clipped at viewport edge
- Mobile 1.3: tapping outside "..." menu does not close it
- Desktop 2.1: X close button visible on desktop

errors: none

reproduction:
- Mobile 1.x: open on mobile, open a document, or tap "..." on a document row
- Desktop 2.1: open on desktop, observe sidebar header

timeline: appeared after phase-5 MOBL-01 implementation

## Eliminated

(none yet)

## Evidence

- timestamp: 2026-03-10T00:01:00Z
  checked: DocumentRow.tsx onClick handler (line 66)
  found: navigate(`/doc/${document.id}`) is called but setSidebarOpen(false) is never called
  implication: Root cause of bug 1.1 — no code closes the sidebar when a document row is tapped on mobile

- timestamp: 2026-03-10T00:02:00Z
  checked: DocumentRow.tsx dropdown menu (lines 117-138)
  found: dropdown position is `right: 4, top: '100%'` with no overflow/viewport clamping; no click-outside handler exists
  implication: Root cause of bugs 1.2 and 1.3 — menu overflows left edge of viewport on mobile (sidebar is only 240px wide, menu is 150px, positioned from the right of the row which is near the right edge of the sidebar — BUT on mobile the sidebar is at left:0, so `right: 4` inside a 240px panel can clip if row is near the left side); and no outside-click listener so the menu never closes when tapping away

- timestamp: 2026-03-10T00:03:00Z
  checked: index.css lines 22 (`.mobile-close-btn { display: none; }`)
  found: CSS hides the X button on desktop correctly via `display: none`
  implication: Desktop X button hiding should work — but Sidebar.tsx renders it unconditionally in JSX with `className="mobile-close-btn"`. CSS should hide it. Need to verify no inline style overrides it.

- timestamp: 2026-03-10T00:04:00Z
  checked: Sidebar.tsx line 49-57, iconButtonStyle (lines 88-94)
  found: The X button uses `iconButtonStyle` which sets `display: 'flex'` as an INLINE style. This overrides the CSS `display: none` from `.mobile-close-btn` because inline styles have higher specificity than class-based rules.
  implication: Root cause of bug 2.1 — `display: 'flex'` in the inline style overrides `.mobile-close-btn { display: none }` on desktop

- timestamp: 2026-03-10T00:05:00Z
  checked: AppPage.tsx — no logic to close sidebar on navigation
  found: No effect or handler that calls setSidebarOpen(false) when docId changes
  implication: Confirms bug 1.1 — sidebar open state is never cleared on mobile when a document is selected

## Resolution

root_cause: |
  Bug 1.1: DocumentRow.onClick navigates but never calls setSidebarOpen(false). No code in
  AppPage closes the sidebar when a document is selected. Fix: call setSidebarOpen(false)
  in DocumentRow's click handler, but only on mobile (or always — sidebar state is already
  managed by CSS on desktop so calling it on desktop would hide the sidebar unnecessarily).
  Best approach: in AppPage, add a useEffect that closes sidebar when docId changes and
  isMobile is true.

  Bug 1.2: The "..." dropdown in DocumentRow uses `right: 4` which positions it relative to
  the `.document-row` container. The row is inside the 240px sidebar. On mobile, the sidebar
  is fixed at left:0. The dropdown at `right:4` means its right edge is ~4px from the right
  edge of the sidebar (240px from left). With minWidth:150, left edge is at ~90px — so it
  should not clip right. The clipping is actually on the LEFT: `right: 4` within the row
  container positions the menu's right edge near the right side of the row but rows have
  `position: relative` so the dropdown's right:4 is relative to the row, not the sidebar.
  Rows are ~228px wide (240 - 8px margin - 4px padding). So menu left = 228 - 4 - 150 = 74px
  from the row's left. This is fine. The clip must be the sidebar itself having `overflow`
  issues or the menu extending outside via z-index stacking. More likely: the sidebar has no
  `overflow: visible` set so its children's absolutely positioned menus get clipped by the
  sidebar's bounds. Fix: ensure the dropdown uses a portal or the sidebar allows overflow.
  Actually: the sidebar has `overflow: hidden` implicitly via flex layout with `overflowY: auto`
  on the inner div. The document list div has `overflowY: auto` — this clips the absolute
  positioned dropdown. Fix: use `overflow: visible` on the list container or position the
  dropdown differently. Simplest: left-align the menu (use `left: 0` instead of `right: 4`)
  so it opens rightward and stays within the sidebar width.

  Bug 1.3: DocumentRow has no click-outside handler. `setShowMenu` is only toggled by the
  button click. Fix: add a useEffect with a document-level mousedown/touchstart listener that
  closes the menu when a click occurs outside the menu element.

  Bug 2.1: iconButtonStyle sets `display: 'flex'` inline on the X close button, overriding the
  CSS `.mobile-close-btn { display: none }` rule on desktop. Fix: remove the shared iconButtonStyle
  from the X button and instead not apply display in inline style (let CSS control display), or
  apply style without the display property for the X button specifically.

fix: |
  1. Sidebar.tsx X button: don't use iconButtonStyle directly (it sets display:flex inline).
     Instead spread iconButtonStyle but omit the display property, letting CSS control it.
  2. DocumentRow.tsx onClick: call setSidebarOpen(false) when on mobile (use useIsMobile hook).
     OR handle in AppPage with a useEffect watching docId + isMobile.
  3. DocumentRow.tsx dropdown: add click-outside handler; fix positioning to left:0 to prevent
     clipping within sidebar's scroll container.
  4. AppPage.tsx or DocumentRow.tsx: close sidebar on document selection (mobile only).

files_changed:
  - client/src/components/Sidebar/Sidebar.tsx
  - client/src/components/Sidebar/DocumentRow.tsx
