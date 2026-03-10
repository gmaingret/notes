---
status: resolved
trigger: "Phase 3 markdown/chip rewrite of BulletContent.tsx introduced 8 regressions"
created: 2026-03-09T00:00:00Z
updated: 2026-03-09T18:30:00Z
resolved: 2026-03-09T18:30:00Z
---

## Current Focus

hypothesis: RESOLVED — all 8 regressions confirmed fixed by user.
test: User verified at https://notes.gregorymaingret.fr
expecting: N/A
next_action: DONE

## Symptoms

expected:
- Up/Down arrow buttons move focus between bullets
- Enter creates a new bullet AND focuses it
- Typing !! triggers a native date picker
- Marking a bullet complete can be toggled (mark/unmark)
- Bookmarks can be added via context menu
- Search finds existing bullet text
- Enter key creates a new bullet below (does NOT delete current)
- Bullet content persists after clicking elsewhere (blur) or pressing Enter

actual:
1. Up/Down buttons do not move focus to adjacent bullets
2. Pressing Enter does not focus the newly created bullet
3. Typing !! does nothing (no date picker)
4. Unmark complete does not work
5. Add to bookmark does not work (context menu item broken)
6. Search returns no results for existing bullet text
7. Pressing Enter DELETES the current bullet point
8. Writing a bullet and clicking elsewhere or pressing Enter causes the bullet to DISAPPEAR

errors: No console errors reported by user — visual/behavioral failures only

reproduction:
- Load https://notes.gregorymaingret.fr
- Open any document with bullets
- Try any of the above actions

started: After Phase 3 deployment (today). Worked after Phase 2.

## Eliminated

(none — root causes were found and confirmed)

## Evidence

- timestamp: 2026-03-09T00:01:00Z
  checked: BulletContent.tsx render logic
  found: |
    In view mode (isEditing=false), renders a <span> with NO id attribute, NO onKeyDown handler.
    In edit mode (isEditing=true), renders a <div id="bullet-{id}" contentEditable>.
    BulletNode passes no onFocus prop to BulletContent.
    The id attribute is ONLY on the edit-mode div (line 627).
  implication: |
    Issues 1+2: Arrow key navigation and post-Enter focus both use
    `document.querySelectorAll('[id^="bullet-"]')` and `document.getElementById("bullet-{id}")`.
    In view mode, NO elements have this id, so navigation finds nothing and newEl is null.
    Issue 3: handleInput (which detects !!) is only on the edit-mode div's onInput.

- timestamp: 2026-03-09T00:03:00Z
  checked: Issue 8 (blur loses content) — handleBlur and setIsEditing interaction
  found: |
    When setIsEditing(false) is called inside handlers (Enter, Escape), React re-renders.
    The formerly focused contenteditable div loses its contenteditable attribute, triggering
    a browser blur event. The onBlur handler then reads divRef.current?.textContent — but
    on an element that React was simultaneously unmounting/mutating, this returned '' (empty).
    It then called patchBullet.mutate({content: ''}) which OVERWROTE the bullet's real
    content with an empty string.
  implication: Root cause of issues 7, 8, and collaterally 4, 5, 6.

- timestamp: 2026-03-09T14:50:00Z
  checked: Issues 1, 2, 5 — remaining after prior fix
  found: |
    Issue 1 (Arrow navigation): onKeyDown was conditionally set to undefined when isEditing=false.
    Issue 2 (Enter focus): createBullet uses optimistic updates with temp id; getElementById failed.
    Issue 5 (Bookmark): "relation bookmarks does not exist" — 0001_bookmarks.sql migration
    existed in migrations/ but was NOT registered in migrations/meta/_journal.json.
    Drizzle's migrate() only applies entries listed in the journal. Table was never created.

- timestamp: 2026-03-09T15:50:00Z
  checked: Exhaustive code trace of BulletContent.tsx after second rewrite
  found: |
    Single persistent div approach confirmed correct on paper. Diagnostic logging deployed
    to confirm runtime behavior matches expectations.

## Resolution

root_cause: |
  Two architectural bugs in BulletContent.tsx introduced by the Phase 3 isEditing split,
  plus one missing migration:

  BUG A (causes issues 1, 2, 3): The id="bullet-{id}" attribute only existed on the
  edit-mode contenteditable div. In view mode, the component rendered a <span> with
  no id. All DOM lookups (querySelectorAll('[id^="bullet-"]'), getElementById) found
  nothing in view mode. This broke: Up/Down arrow navigation, post-Enter bullet focus,
  Backspace merge target focus. Issue 3 (!!) was not separately broken — it worked
  once in edit mode, but reaching edit mode was broken by the same missing-id problem.

  BUG B (causes issues 7, 8, and collaterally 4, 5, 6): When setIsEditing(false) was
  called inside handlers (Enter, Escape), React re-rendered the component. The formerly
  focused contenteditable div lost its contenteditable attribute, triggering a browser
  blur event. The onBlur handler then read divRef.current?.textContent — but on an
  element that React was simultaneously unmounting/mutating, this returned '' (empty).
  It then called patchBullet.mutate({content: ''}) which overwrote the bullet's real
  content with empty string. This caused: visible bullet deletion (content wiped),
  content loss on blur/Enter. Issues 4 (unmark complete) and 5 (bookmark) appeared
  broken because the blur-wipe clobbered the bullet content, making it look like the
  context menu actions failed. Issue 6 (search) returned no results because bullets
  already wiped to empty string didn't match any search query.

  INFRASTRUCTURE BUG (issue 5 also): The bookmarks migration file existed in
  migrations/ but was not registered in migrations/meta/_journal.json, so the
  bookmarks table was never created by Drizzle on startup.

fix: |
  Single architectural change to BulletContent.tsx:
  - Replaced the mount/unmount pattern (view=<span>, edit=<div>) with a single
    persistent <div id="bullet-{id}"> that is always in the DOM
  - View mode: contentEditable=false, innerHTML set to rendered HTML via useLayoutEffect
  - Edit mode: contentEditable=true, textContent set to plain content via useLayoutEffect
  - tabIndex=0 ensures the div is focusable in both modes for arrow key navigation
  - Added isSwitchingModeRef: set to true before leaveEditMode(), checked in handleBlur
    to skip saving when blur is caused by programmatic mode switching (not real user blur)
  - Added pendingChipRef: set in onMouseDown to detect chip clicks before focus fires,
    preventing edit mode entry for chip interactions
  - handleFocus: enters edit mode for all normal focus events (click, arrow nav, Enter)
  - saveTimerRef.current explicitly nulled after timer fires (prevents stale ref check)
  - Fixed date picker: use input.showPicker() with click() fallback
  - Added pendingFocusBulletId to uiStore and self-focus logic in BulletContent
  - Registered 0001_bookmarks.sql in migrations/meta/_journal.json

verification: |
  User confirmed all issues resolved at https://notes.gregorymaingret.fr on 2026-03-09.

files_changed:
  - client/src/components/DocumentView/BulletContent.tsx (multiple commits)
  - client/src/store/uiStore.ts
  - server/db/migrations/meta/_journal.json
