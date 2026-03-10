---
phase: 04-attachments-comments-and-mobile
plan: "03"
subsystem: server-api, client-api
tags: [attachments, multer, file-upload, api, drizzle]
dependency_graph:
  requires: ["04-01", "04-02"]
  provides: ["attachment-api-endpoints", "apiClient-upload"]
  affects: ["04-04", "04-05"]
tech_stack:
  added: ["multer@2.1.1", "@types/multer@2.1.0"]
  patterns: ["multer diskStorage", "UUID filename", "orphan cleanup", "multipart/form-data"]
key_files:
  created:
    - server/src/services/attachmentService.ts
    - server/src/routes/attachments.ts
  modified:
    - server/src/index.ts
    - client/src/api/client.ts
    - server/tests/attachments.test.ts
    - server/package.json
decisions:
  - "multer diskStorage writes to /data/attachments with UUID filename + original extension (node:crypto randomUUID)"
  - "Orphaned file cleanup: if DB insert fails after multer writes, fs.unlink called before returning 500"
  - "ENOENT handled gracefully in deleteAttachment — file already gone still deletes DB record"
  - "res.sendFile with root:'/' for inline file serving — not res.download()"
  - "apiClient.upload() omits Content-Type header — browser sets multipart boundary automatically"
  - "req.params cast as string to satisfy TS Express 5 type (string | string[])"
metrics:
  duration: 5min
  completed_date: "2026-03-09"
  tasks: 2
  files: 6
---

# Phase 04 Plan 03: Attachment Backend API Summary

**One-liner:** Multer-based attachment upload API with UUID storage, 4 CRUD endpoints, orphaned file cleanup, and apiClient.upload() for multipart FormData.

## What Was Built

Full attachment backend enabling Plans 04 and 05 to consume:

- `attachmentService.ts` — 4 exported functions: `createAttachment`, `getAttachmentsByBullet`, `getAttachment`, `deleteAttachment`
- `routes/attachments.ts` — 4 HTTP endpoints: POST /bullets/:bulletId, GET /bullets/:bulletId, GET /:id/file, DELETE /:id
- Multer configured with `diskStorage` → `/data/attachments`, UUID filenames preserving original extension
- 100MB file size limit — returns 413 `{error: 'File too large (max 100MB)'}` on oversize
- Router mounted at `/api/attachments` in `index.ts`
- `apiClient.upload<T>(path, formData)` added to client — omits Content-Type so browser sets multipart boundary

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | attachmentService + multer setup + Express routes | efa9ebf | server/src/services/attachmentService.ts, server/src/routes/attachments.ts, server/tests/attachments.test.ts, server/package.json |
| 2 | Register attachments router + add apiClient.upload() | 2d3f791 | server/src/index.ts, client/src/api/client.ts |

## Verification

- `npx vitest run tests/attachments.test.ts` — 4/4 tests pass
- `npx tsc --noEmit` (server) — clean
- `npx tsc --noEmit` (client) — clean
- `server/src/routes/attachments.ts` — 4 endpoints implemented
- `server/src/services/attachmentService.ts` — 4 functions exported

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed TypeScript type error on req.params in Express 5**
- **Found during:** Task 1 TypeScript compile check
- **Issue:** Express 5 types `req.params[key]` as `string | string[]` — service functions expect `string`
- **Fix:** Cast `req.params.bulletId as string`, `req.params.id as string` at call sites
- **Files modified:** server/src/routes/attachments.ts
- **Commit:** efa9ebf

## Self-Check: PASSED

- `server/src/services/attachmentService.ts` — EXISTS
- `server/src/routes/attachments.ts` — EXISTS
- Commit efa9ebf — EXISTS
- Commit 2d3f791 — EXISTS
