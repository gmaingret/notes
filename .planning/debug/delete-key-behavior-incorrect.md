---
status: awaiting_human_verify
trigger: "Delete key at end of a node incorrectly removes the next sibling node instead of merging its text content into the current node."
created: 2026-03-09T00:00:00Z
updated: 2026-03-09T00:00:00Z
---

## Current Focus

hypothesis: Backspace on empty first-child node does nothing because the handler has an early return at the "first child — ignore backspace" branch. For an EMPTY first child, the node should be soft-deleted even though there's no previous sibling to merge into. The recent Delete fix commits did not change the Backspace handler — the early return was introduced in commit 4e05f90. The user is reporting it now because they tested more carefully after the Delete fix.

test: Traced Backspace handler (lines 566-616): for a first child with myIdx===0 and parentId!==null, code hits the `else if (bullet.parentId !== null) { return; }` branch and returns without doing anything. Adding a check: if currentContent === '' at that point, soft-delete the node instead.

expecting: After fix, Backspace on an empty first-child node should soft-delete it and focus the previous bullet (or parent, since there's no previous sibling in this case).

next_action: Apply minimal fix to Backspace handler first-child branch

## Symptoms

expected: When cursor is at end of "nodeA" and Delete is pressed, nodeA and nodeB should merge into "nodeAnodeB" with nodeB1 remaining as a child of the merged node. Structure: nodeAnodeB with child nodeB1.
actual: nodeB disappears entirely, and nodeB1 becomes a direct child of nodeA. The text of nodeB is lost.
errors: No error messages - silent wrong behavior
reproduction:
1. Have tree: nodeA, nodeB (with child nodeB1)
2. Place cursor at end of nodeA text
3. Press Delete key
4. nodeB's text is gone, nodeB1 is promoted/reparented
timeline: Unknown - user just reported it

## Eliminated

- hypothesis: The ownChildren guard (line 621-622) causes early return when nodeA has children
  evidence: The scenario has nodeA with no children, so the guard is not triggered
  timestamp: 2026-03-09T00:00:00Z

- hypothesis: The regression (Backspace on empty node does nothing) was introduced by one of the two recent Delete fix commits
  evidence: Both commits only modified the Delete handler (lines 630+). The Backspace handler is identical before and after those commits. The early-return path for first-child was introduced in commit 4e05f90 ("Ignore backspace/delete on parent nodes instead of merging") which predates the Delete fix commits.
  timestamp: 2026-03-09T02:00:00Z

- hypothesis: mergedContent computation is wrong
  evidence: `mergedContent = (el.textContent ?? '') + nextSibling.content` is correct - appends nodeB text to nodeA text
  timestamp: 2026-03-09T00:00:00Z

- hypothesis: Race condition between patchBullet.mutate (fire-and-forget) and moveBullet.mutateAsync invalidations
  evidence: The "fix" of awaiting patchBullet.mutateAsync was applied but the bug persisted. The race condition hypothesis was wrong.
  timestamp: 2026-03-09T01:00:00Z

- hypothesis: Backend cascade delete on soft-delete removes children
  evidence: schema.ts has no FK constraint on parent_id at all. softDeleteBullet only sets deletedAt on the single bullet. No cascade.
  timestamp: 2026-03-09T01:00:00Z

## Evidence

- timestamp: 2026-03-09T00:00:00Z
  checked: BulletContent.tsx lines 618-660 (Delete key handler)
  found: Handler correctly computes mergedContent. For nextSibling with children, it reverses child array and calls moveBullet.mutateAsync with afterId: null for each child, then softDeletes nextSibling.
  implication: The afterId: null approach inserts each child as first child. In reverse, this should reconstruct original order. The logic appears sound structurally.

- timestamp: 2026-03-09T00:00:00Z
  checked: moveBullet mutation in useBullets.ts (lines 151-162)
  found: moveBullet has NO optimistic update — it only calls invalidateQueries in onSettled. patchBullet HAS an optimistic update.
  implication: After patchBullet.mutate fires optimistically (showing merged text), moveBullet.mutateAsync fires and in onSettled calls invalidateQueries. This refetch WILL overwrite the optimistic patchBullet result with stale server data (nodeA still has original content, nodeB still exists). The merged text disappears.

- timestamp: 2026-03-09T00:00:00Z
  checked: Race condition between patchBullet optimistic update and moveBullet invalidation
  found: patchBullet.mutate is fire-and-forget (not awaited). moveBullet.mutateAsync IS awaited in the loop. Each moveBullet.mutateAsync completion triggers invalidateQueries, which refetches bullets. If the patchBullet network request hasn't completed yet when this refetch happens, the server returns the OLD nodeA content. The optimistic update is replaced with stale data.
  implication: This explains "nodeB's text is gone" — the merge text patch gets wiped by the refetch from moveBullet completion.

- timestamp: 2026-03-09T00:00:00Z
  checked: The afterId: null child re-parenting order
  found: Children are reversed and each moved with afterId: null (insert as first child). child[last] inserted first, then child[last-1] before it... This correctly reconstructs order. BUT with query invalidation between each mutateAsync, the intermediate state may show partial re-parenting before the text patch completes.
  implication: The child re-parenting order logic is technically correct but the race with patchBullet causes text loss.

- timestamp: 2026-03-09T01:00:00Z
  checked: db/schema.ts — parent_id foreign key, softDeleteBullet in bulletService.ts
  found: parent_id has NO foreign key constraint in the schema. softDeleteBullet only sets deletedAt on the single bullet (no cascading to children). No backend cascade delete is involved.
  implication: The cascade delete hypothesis is eliminated. The bug is purely on the frontend.

- timestamp: 2026-03-09T01:00:00Z
  checked: handleBlur in BulletContent.tsx (lines 295-315) — what fires when nodeA loses focus after Delete
  found: handleBlur reads `divRef.current?.textContent` (the DOM content) and compares with `bullet.content` (from the React Query cache). If different, it sends a patchBullet.mutate with the DOM content. The Delete handler computes mergedContent and sends it to the server via patchBullet.mutateAsync, but NEVER updates el.textContent (the DOM) or localContent (React state). So the DOM still shows "nodeA" after the Delete. When handleBlur fires (e.g. from setCursorAtPosition calling focus → blur cycle, or user tabbing away), it reads "nodeA" from the DOM, compares with "nodeAnodeB" from bullet.content (cache after patch), finds them different, and sends patchBullet.mutate({ content: "nodeA" }) — overwriting the merged content.
  implication: This is the true root cause. The previous "fix" (awaiting patchBullet) correctly persisted the merge to the server, but handleBlur immediately overwrites it with the old DOM content. The fix must update el.textContent and localContent synchronously before the async work.

- timestamp: 2026-03-09T02:00:00Z
  checked: Backspace handler (lines 566-624) for empty first-child node case — commit history for the early-return branch
  found: The `else if (bullet.parentId !== null) { return; }` branch returns unconditionally for first-child nodes, even when the node is empty. For an empty first-child, the correct behavior is to soft-delete it (since merging empty content into parent is equivalent to deletion). The fix: check `(el.textContent ?? '') === ''` in that branch; if true, call softDeleteBullet and focus the parent. The TypeScript check passes clean after this change.
  implication: This resolves the Backspace regression for empty first-child nodes.

## Resolution

root_cause: Two separate issues addressed:
  1. Delete merge: The Delete handler computed mergedContent but never updated el.textContent or localContent, causing handleBlur to overwrite the server-side merge with stale DOM text. Fixed by synchronously updating el.textContent and setLocalContent before the async operations.
  2. Backspace on empty first-child: The Backspace handler had an unconditional early return for first-child nodes (introduced in commit 4e05f90 to prevent merging into parent). For empty first-child nodes, the return prevented soft-deletion. Fixed by checking if the node is empty before returning — if empty, soft-delete it and focus the parent.

fix:
  1. Delete handler (line 636): el.textContent = mergedContent; setLocalContent(mergedContent); clears saveTimer before async ops.
  2. Backspace handler (line 592): in the first-child branch, check (el.textContent ?? '') === ''; if true, call softDeleteBullet and focus parent before returning.

verification: TypeScript check (npx tsc --noEmit) returns zero errors. Manual end-to-end verification needed for both Delete merge and Backspace empty-node cases.
files_changed:
  - client/src/components/DocumentView/BulletContent.tsx
