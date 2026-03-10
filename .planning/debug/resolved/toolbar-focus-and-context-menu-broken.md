---
status: resolved
trigger: "Two UI bugs in the Flutter web notes app — (1) selecting a bullet and clicking a toolbar button causes the bullet to lose focus and the toolbar to disappear, and (2) right-clicking anywhere does not show the expected custom context menu."
created: 2026-03-10T00:00:00Z
updated: 2026-03-10T00:00:00Z
---

## Current Focus

hypothesis: "Bug 2 (context menu not visible): The ContextMenu component uses position:fixed but renders inside a dnd-kit BulletNode whose outer div has BOTH overflow:hidden AND CSS transform applied. CSS transform on an ancestor creates a new containing block for fixed-positioned descendants, so the menu is positioned/clipped relative to the transformed div instead of the viewport — making it invisible."
test: "Read BulletNode.tsx outer div style: confirmed overflow:hidden at line 200 and transform:CSS.Transform.toString(transform) at line 87. The ContextMenu renders in the inner content div which is a child of this transformed+clipped ancestor."
expecting: "Wrapping the ContextMenu in createPortal(…, document.body) will render it directly under body, outside the transformed ancestor, making it visible."
next_action: "Apply createPortal fix and deploy."

## Symptoms

expected:
- Test 1: Select a bullet item, click a toolbar button → action applies, editor stays focused, toolbar stays visible
- Test 2 and 3: Right-click anywhere in the app → custom context menu appears with options: indent | outdent | up | down | undo | redo | attachment | comment | bookmark | complete | delete

actual:
- Test 1: Selecting a bullet and tapping a toolbar button makes the bullet lose focus and the toolbar disappears
- Test 2 and 3: No context menu appears at all on right-click

errors: None reported — features just silently fail

reproduction:
- Test 1: Open a note with bullet points on web, select/click a bullet, click any toolbar button → toolbar vanishes
- Test 2 and 3: Right-click anywhere on the web app → nothing happens

started: Never worked — newly implemented features

platform: Web (React/TypeScript/Vite app, NOT Flutter)

## Eliminated

(none yet)

## Evidence

- timestamp: 2026-03-10T01:00:00Z
  checked: BulletNode.tsx outer div style (lines 193-203) and dnd-kit transform (line 87)
  found: The outer div has overflow:'hidden' AND style includes transform:CSS.Transform.toString(transform). The ContextMenu with position:fixed renders inside this div's subtree.
  implication: CSS transform on an ancestor makes it a containing block for position:fixed children. The fixed menu is positioned and clipped relative to the BulletNode's transformed div, not the viewport. This makes it invisible. Fix: use createPortal to render ContextMenu directly under document.body, escaping the transformed ancestor entirely.

- timestamp: 2026-03-10T01:00:00Z
  checked: Applied createPortal fix to BulletNode.tsx — import createPortal from 'react-dom', wrap ContextMenu render in createPortal(..., document.body)
  found: Fix implemented.
  implication: ContextMenu will now render outside the dnd-kit transform context and be visible at the correct viewport coordinates.

- timestamp: 2026-03-10T00:00:00Z
  checked: FocusToolbar.tsx line 159-162
  found: onMouseDown={(e) => e.preventDefault()} is on the outer container DIV, not on individual buttons
  implication: This SHOULD prevent blur when clicking inside the toolbar — BUT browser blur fires on mousedown BEFORE click. The e.preventDefault() on the container div's onMouseDown DOES propagate to prevent default, which means blur should be suppressed. However, this needs further examination.

- timestamp: 2026-03-10T00:00:00Z
  checked: BulletContent.tsx handleBlur() lines 252-266
  found: handleBlur calls setFocusedBulletId(null) which removes the focused bullet from store. BulletTree.tsx line 207-215 renders FocusToolbar only when focusedBulletId is truthy. So if blur fires, the toolbar disappears immediately, before the button's click event fires.
  implication: On web, mousedown fires first (triggering blur on the currently focused element), then the click fires. If mousedown on a toolbar button doesn't call e.preventDefault() before the focus change, the blur will fire, clearing focusedBulletId, unmounting the toolbar, and the button's click never fires.

- timestamp: 2026-03-10T00:00:00Z
  checked: FocusToolbar.tsx container div onMouseDown handler
  found: onMouseDown={(e) => e.preventDefault()} IS present on the container div. On web, mousedown on a button within the toolbar div should bubble up and the preventDefault() should prevent the default focus-steal. BUT: the blur fires from the contentEditable div when it loses focus — e.preventDefault() on mousedown DOES suppress focus transfer in most browsers. This should work.
  implication: Wait — need to re-examine. The issue may be that the blur on the contentEditable fires because the click on the toolbar button causes the contentEditable to lose focus. preventDefault() on mousedown prevents the NEW element from getting focus, but does it prevent the OLD element from blurring? On most browsers, YES — if the mousedown is prevented, the focused element (contentEditable) does NOT lose focus. So the toolbar's e.preventDefault() should work. BUT: the issue says the toolbar disappears. Let me look more carefully at whether the onBlur handler in BulletContent has a guard.

- timestamp: 2026-03-10T00:00:00Z
  checked: BulletContent.tsx onBlur line 705: onBlur={isEditing ? handleBlur : undefined}
  found: onBlur is only attached when isEditing=true. And handleBlur calls setFocusedBulletId(null).
  implication: Even if e.preventDefault() suppresses focus loss, if the blur event fires for any reason it will clear focusedBulletId. The question is: does e.preventDefault() on the toolbar container's mousedown actually prevent blur on the contentEditable div?

- timestamp: 2026-03-10T00:00:00Z
  checked: BulletContent.tsx handleFocus() line 268-275
  found: handleFocus sets isEditing=true AND setFocusedBulletId(bullet.id). So the toolbar appears when a bullet is focused.
  implication: The toolbar shows correctly. The question is whether clicking toolbar buttons causes blur.

- timestamp: 2026-03-10T00:00:00Z
  checked: FocusToolbar container div — onMouseDown is on the wrapper div. Individual buttons do NOT have onMouseDown preventDefault.
  found: When user clicks a toolbar button: (1) mousedown fires on the button → bubbles to container div → e.preventDefault() called. This SHOULD prevent the contentEditable from losing focus. (2) click fires on the button → action executes. This should be correct.
  implication: The mechanism is correct in theory. BUT there may be a subtle issue: the onBlur in BulletContent is NOT conditioned on whether the focus went somewhere within the toolbar. Any blur will clear focusedBulletId(null). If for some reason blur fires before e.preventDefault() is processed, the toolbar disappears.

- timestamp: 2026-03-10T00:00:00Z
  checked: Context menu — BulletNode.tsx lines 204-207
  found: onContextMenu is on the outer BulletNode div. It sets contextMenuPos state local to BulletNode. The ContextMenu renders INSIDE the BulletNode's content area. Right-click on a bullet WILL trigger the context menu because onContextMenu is on the BulletNode div.
  implication: The context menu SHOULD work when right-clicking ON a bullet. But right-clicking OUTSIDE any bullet (on whitespace/padding areas around the document) will NOT trigger the context menu because there's no document-level contextmenu handler. The symptom says "right-click anywhere does not show context menu" — this could mean right-clicking on empty areas (not on a bullet row).

- timestamp: 2026-03-10T00:00:00Z
  checked: FocusToolbar — actual toolbar for web vs the issue description
  found: The issue says "toolbar button causes bullet to lose focus and toolbar disappears." The FocusToolbar appears when focusedBulletId is set. The buttons in FocusToolbar have plain onClick handlers but NO onMouseDown={e=>e.preventDefault()} individually. The container div has onMouseDown={e=>e.preventDefault()}. The real question is whether this container-level preventDefault is enough to stop blur.
  implication: CONFIRMED ROOT CAUSE for Bug 1: In web browsers, when you mousedown on a non-focusable element (like a button with preventDefault), it prevents focus from moving to THAT element, but the previously focused element (contentEditable) still fires blur in some browser/React combinations. The real issue is that blur fires when focus leaves the contentEditable. If mousedown is prevented, browsers should NOT move focus away from the contentEditable — but React's synthetic event system may process events differently. Additionally, clicking the toolbar's file input (for attach) DOES require focus, which will definitely cause blur.

## Resolution

root_cause: |
  BUG 1 (Toolbar disappears): FIXED. handleBlur in BulletContent checks relatedTarget for data-focus-toolbar attribute; blur from the contentEditable when focus moves INTO the toolbar is suppressed. Confirmed working by user.

  BUG 2 (Context menu not visible at all): The ContextMenu component uses position:fixed, but it was rendered as a child of BulletNode's outer div which has a CSS transform applied by dnd-kit (CSS.Transform.toString). A CSS transform on an ancestor creates a new containing block for position:fixed descendants — the menu was being positioned and clipped relative to the transformed BulletNode div (which also has overflow:hidden), making it entirely invisible.

  Fix: Wrapped the per-bullet ContextMenu in createPortal(..., document.body) so it renders directly under <body>, outside the transformed ancestor, and is correctly positioned at viewport coordinates.

fix: |
  Files changed (this session — context menu portal fix):
  - BulletNode.tsx: import createPortal from 'react-dom'; wrap ContextMenu render in createPortal(..., document.body)

  Previously fixed (Bug 1 — toolbar focus):
  - BulletContent.tsx, FocusToolbar.tsx, BulletNode.tsx, BulletTree.tsx, ContextMenu.tsx

verification: |
  Deployed to https://notes.gregorymaingret.fr and confirmed by user:
  - Test 1 (toolbar stays visible after clicking toolbar button): CONFIRMED FIXED
  - Test 2 (right-click on bullet shows context menu): CONFIRMED FIXED
  - Test 3 (right-click on empty space shows context menu): CONFIRMED FIXED
files_changed:
  - client/src/components/DocumentView/BulletContent.tsx
  - client/src/components/DocumentView/FocusToolbar.tsx
  - client/src/components/DocumentView/BulletNode.tsx
  - client/src/components/DocumentView/BulletTree.tsx
  - client/src/components/DocumentView/ContextMenu.tsx
