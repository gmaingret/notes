---
status: awaiting_human_verify
trigger: "Nodes created but empty after app kill/reopen — text not saving. Two prior fixes failed."
created: 2026-03-13T00:00:00Z
updated: 2026-03-13T00:00:00Z
---

## Current Focus

hypothesis: CONFIRMED - Gson serializeNulls() causes all PATCH requests to fail Zod validation
test: Sent PATCH with null fields to server, got 400 validation errors
expecting: Fix PatchBulletRequest serialization to exclude null fields
next_action: Fix by removing serializeNulls or using @JsonAdapter to skip nulls on PatchBulletRequest

## Symptoms

expected: Create nodes, enter text, kill app, reopen → nodes have their text
actual: Nodes are created but empty, no text is saved
errors: None visible
reproduction: Create nodes, type text, force-kill app, reopen
started: Ongoing issue, two fix attempts failed

## Eliminated

- hypothesis: Debounced edits not flushed on lifecycle events
  evidence: Added flushContentEdit() on focus loss and flushAllPendingEdits() on ON_STOP/onCleared — didn't fix
  timestamp: prior attempt 1

- hypothesis: Content override not migrated from temp ID to real ID in createBullet
  evidence: Added content override migration — didn't fix
  timestamp: prior attempt 2

## Evidence

- timestamp: 2026-03-13T05:22:00Z
  checked: Server PATCH endpoint with content-only update (no null fields)
  found: Works correctly - content saved and returned on GET
  implication: Server round-trip is fine when request body has no null fields

- timestamp: 2026-03-13T05:23:00Z
  checked: Server PATCH endpoint with Gson serializeNulls output (content + null booleans)
  found: 400 error - {"errors":{"isComplete":["Expected boolean, received null"],"isCollapsed":["Expected boolean, received null"]}}
  implication: EVERY content PATCH from the Android app fails because Gson sends null for unset fields

- timestamp: 2026-03-13T05:23:30Z
  checked: NetworkModule.kt line 59
  found: GsonConverterFactory.create(GsonBuilder().serializeNulls().create())
  implication: serializeNulls() is the root cause - it sends null for Optional fields in PatchBulletRequest

- timestamp: 2026-03-13T05:24:00Z
  checked: All four PATCH types (content, isComplete, isCollapsed, note)
  found: ALL fail with same Zod validation error when other fields are null
  implication: toggleComplete, toggleCollapse, note save ALL silently fail too

## Resolution

root_cause: GsonBuilder().serializeNulls() causes PatchBulletRequest to serialize null fields (e.g., {"content":"Hello","isComplete":null,"isCollapsed":null,"note":null}). The server's Zod schema rejects null for boolean/string optional fields (expects undefined/absent, not null). Every PATCH silently fails with 400, so content is never saved. This also affected toggleComplete, toggleCollapse, and note saving.

fix: Updated server's patchBulletSchema Zod validation in bullets.ts to accept nullable values (.nullable().optional() instead of .optional()) and changed the handler's null-checks from !== undefined to != null (which covers both null and undefined in JS). The note field was left as !== undefined because null is a valid value for note (meaning "remove the note").

verification:
- curl tests confirm PATCH with null fields now succeeds (200) and content persists on GET
- 10 new unit tests in BulletTreeViewModelTest verify content editing, debounce, flush, and temp-to-real ID migration
- 6 new serialization tests in PatchBulletSerializationTest document the Gson behavior
- 5 new integration tests in BulletApiIntegrationTest verify the full server round-trip with serializeNulls format
- All 136 tests pass (except 1 pre-existing AuthScreenTest timeout, 5 integration tests skipped in non-network env)
- assembleDebug builds successfully

files_changed:
- server/src/routes/bullets.ts (Zod schema + null-check fix)
- android/app/src/test/java/com/gmaingret/notes/presentation/bullet/BulletTreeViewModelTest.kt (10 new tests)
- android/app/src/test/java/com/gmaingret/notes/data/api/PatchBulletSerializationTest.kt (new file, 6 tests)
- android/app/src/test/java/com/gmaingret/notes/data/api/BulletApiIntegrationTest.kt (new file, 5 tests)
