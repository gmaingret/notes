---
status: awaiting_human_verify
trigger: "Continue debugging bullet tree UI — drag is locked vertically, horizontal movement is completely blocked during drag."
created: 2026-03-12T00:00:00Z
updated: 2026-03-12T02:00:00Z
---

## Current Focus

hypothesis: CONFIRMED. In onDragStopped, targetDepthDelta is computed from a local val that evaluates isDragging — but isDragging is already false when the handler fires. So targetDepthDelta was always 0 at drop time, meaning the bullet always returned to its original depth regardless of horizontal movement.
test: Build + install + manual drag-indent test
expecting: Dropping bullet after dragging right makes it a child of the bullet above; dragging left outdents it
next_action: Await user verification of indent-on-drop

## Symptoms

expected: Dropping a bullet at a different horizontal offset changes its indent level (reparents it)
actual: Bullet always returns to its original depth on drop — no reparenting occurs
errors: None (behavior issue only)
reproduction: Long-press bullet, drag right (visual feedback confirms horizontal movement), release — bullet snaps back to same indent level
started: After horizontal drag visual fix (prior session)

## Eliminated

- hypothesis: Custom DragGestureDetector wrapper cannot intercept horizontal movement
  evidence: Bytecode confirms detectDragGesturesAfterLongPress delivers full 2D Offset; dragAmount.x is nonzero; our wrapper correctly accumulates dragHorizontalOffset
  timestamp: 2026-03-12T00:01:00Z

- hypothesis: Custom DragGestureDetector wrapping LongPress did NOT work (user checkpoint)
  evidence: Partially correct — the accumulation DID work, but there was no visual output because translationX was never applied to the dragging item
  timestamp: 2026-03-12T00:01:00Z

- hypothesis: dragHorizontalOffset state accumulation is broken
  evidence: Visual drag confirmed working (prior session fix); the state IS correct at drop time
  timestamp: 2026-03-12T02:00:00Z

## Evidence

- timestamp: 2026-03-12T00:00:00Z
  checked: checkpoint response
  found: Drag locked vertically; DragGestureDetector wrapper did not work
  implication: Either accumulation is wrong, or visual output is missing

- timestamp: 2026-03-12T00:01:00Z
  checked: ReorderableLazyListKt$ReorderableItem$offsetModifier$1$1.class bytecode
  found: invoke() calls getDraggingItemOffset().getY() then setTranslationY() — X component never used
  implication: The library ONLY applies translationY to the dragging item; our dragHorizontalOffset state was being accumulated but never displayed

- timestamp: 2026-03-12T00:01:00Z
  checked: DragGestureDetector$LongPress.class bytecode
  found: detect() calls detectDragGesturesAfterLongPress which delivers full 2D Offset; dragAmount.x IS non-zero
  implication: The accumulation in our DragGestureDetector wrapper is correct

- timestamp: 2026-03-12T00:01:00Z
  checked: ReorderableLazyCollectionState.onDrag bytecode
  found: Full dragAmount Offset is added to draggingItemDraggedDelta; not axis-restricted in the state
  implication: Library tracks full offset internally but the ReorderableItem offset modifier only reads Y

- timestamp: 2026-03-12T02:00:00Z
  checked: BulletTreeScreen.kt onDragStopped handler (lines 222-294)
  found: targetDepthDelta is a local val inside ReorderableItem lambda: `if (isDragging) { ... } else 0`. When onDragStopped fires, isDragging is already false, so targetDepthDelta == 0. Line 231 computed: targetDepth = flatBullet.depth + 0 = original depth. No reparenting ever happened.
  implication: Root cause confirmed. dragHorizontalOffset state was correct but never read in onDragStopped — only the stale zero-value targetDepthDelta was used.

## Resolution

root_cause: |
  Two bugs converged:
  1. (Prior session) ReorderableItem offset modifier applied only translationY — no visual horizontal feedback.
  2. (This session) In onDragStopped, `targetDepthDelta` is a local val computed with `isDragging` which is
     already false at drop time, making it always 0. The `dragHorizontalOffset` state (correct value) was
     never read in the drop handler — only the always-zero `targetDepthDelta` was used for depth calculation.
     Result: every drop kept the bullet at its original depth regardless of horizontal displacement.

fix: |
  BulletTreeScreen.kt onDragStopped: replaced usage of stale `targetDepthDelta` with direct computation
  from `dragHorizontalOffset` state:
    val indentPx = with(density) { 24.dp.toPx() }
    val currentDepth = if (currentIndex >= 0) liveFlatList[currentIndex].depth else flatBullet.depth
    val droppedDepthDelta = (dragHorizontalOffset / indentPx).roundToInt().coerceIn(-currentDepth, 1)
    val targetDepth = (currentDepth + droppedDepthDelta).coerceAtLeast(0)
  Also fixed cycle-prevention to use currentDepth (post-reorder depth) instead of captured flatBullet.depth.

verification: Build succeeded (assembleDebug), unit tests passed (testDebugUnitTest)
files_changed:
  - presentation/bullet/BulletRow.kt: added dragHorizontalOffsetPx parameter, apply translationX in graphicsLayer (prior session)
  - presentation/bullet/BulletTreeScreen.kt: pass dragHorizontalOffsetPx to BulletRow (prior session); fix drop handler to compute depth delta from dragHorizontalOffset state (this session)
