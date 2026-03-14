---
phase: quick
plan: 1
subsystem: android-ui
tags: [scroll, lazy-column, bullet-tree, ux]
dependency_graph:
  requires: []
  provides: [fix-android-scroll-on-enter]
  affects: [BulletTreeScreen]
tech_stack:
  added: []
  patterns: [LazyListState.layoutInfo visibility check, scroll-to-bottom strategy]
key_files:
  created: []
  modified:
    - android/app/src/main/java/com/gmaingret/notes/presentation/bullet/BulletTreeScreen.kt
decisions:
  - Use layoutInfo.visibleItemsInfo to determine visibility before scrolling
  - Use targetFirstVisible = max(0, idx - visibleCount + 1) for bottom-aligned scroll
  - Preserve existing scrollToItem(idx) behavior for upward navigation
metrics:
  duration: ~5 minutes
  completed: 2026-03-14
  tasks_completed: 1
  files_modified: 1
---

# Quick Fix 1: Fix Android Scroll Position When Creating Bullets

**One-liner:** Replaced unconditional scrollToItem with visibility-aware logic that scrolls the new bullet to the bottom of the viewport (keeping the previous bullet visible) only when needed.

## What Was Done

The `LaunchedEffect(focusedBulletId)` block in `BulletTreeScreen.kt` previously called `lazyListState.scrollToItem(idx)` unconditionally every time the focused bullet changed. This caused the newly created bullet (via Enter key) to jump to the top of the viewport, pushing the previously edited bullet out of view.

The fix implements three-case logic:

1. **Already fully visible** — no scroll at all (Enter key on a bullet in the middle of the screen)
2. **Below the viewport** (Enter key case) — scroll to `max(0, idx - visibleCount + 1)` so the new bullet appears at the bottom, keeping the previous bullet visible above it
3. **Above the viewport** — scroll to `idx` (existing behavior, shows item at top)

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Fix scroll-to-focused-bullet to avoid hiding the previous bullet | a91748c | BulletTreeScreen.kt |

## Checkpoint Pending

Task 2 is a `checkpoint:human-verify` — device testing required. APK was installed via `adb install` and is ready for manual verification on device.

## Deviations from Plan

None — plan executed exactly as written.

## Self-Check: PASSED

- BulletTreeScreen.kt modified: FOUND
- Commit a91748c: FOUND (build successful, 41 tasks, 6 executed)
