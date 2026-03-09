# Deferred Items

## Pre-existing TypeScript Errors (not introduced by Plan 02-06)

Found via `npx tsc -p tsconfig.app.json --noEmit`:

1. `BulletContent.tsx:34` - `text` declared but never read (pre-existing from Plan 04, in `isCursorAtEnd`)
2. `BulletContent.tsx:231` - `prevSibling` declared but never read (pre-existing from Plan 04, in Backspace handler)
3. `BulletNode.tsx:68` - `opacity` specified more than once (pre-existing from Plan 05)
4. `bulletTree.test.tsx:3` - `isCursorAtEnd` imported but never used in tests (pre-existing from Plan 01)

Note: These do NOT affect runtime behavior. `npx tsc --noEmit` (without -p tsconfig.app.json) exits 0 due to project reference caching.
