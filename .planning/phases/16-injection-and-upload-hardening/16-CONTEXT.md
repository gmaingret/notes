# Phase 16: Injection and Upload Hardening - Context

**Gathered:** 2026-03-15
**Status:** Ready for planning

<domain>
## Phase Boundary

Fix ILIKE wildcard injection in search and tag queries, restrict file upload types to a safe allowlist, sanitize filenames in Content-Disposition headers, serve SVGs as forced downloads to prevent stored XSS, and verify bullet ownership on all attachment operations.

</domain>

<decisions>
## Implementation Decisions

### Upload allowlist
- Broad allowlist: images (jpg, jpeg, png, gif, webp, avif, svg), PDFs, plain text (txt, csv, json, xml, md), office docs (docx, xlsx, pptx), archives (zip, tar.gz)
- SVG stays allowed for upload (XSS prevented by force-download on serve)
- Block all executables, scripts (.exe, .bat, .sh, .js, .html, .htm, .php, etc.)
- Rejection message is clear and lists accepted types: "File type .exe is not allowed. Supported: images, PDFs, documents, archives"

### Ownership checks
- Return 404 Not Found (not 403) when a user tries to access another user's bullet — hides bullet existence from attackers
- Apply ownership verification on ALL attachment routes: upload, list, and download
- Consistent pattern: verify bullet belongs to authenticated user before any operation

### SVG handling
- Force-download only: Content-Disposition: attachment for SVG files
- All other images (jpg, png, gif, webp, avif) and PDFs remain inline (current behavior unchanged)
- No SVG sanitization — force-download is simpler and bulletproof

### Claude's Discretion
- ILIKE escape implementation details (helper function vs inline)
- Filename sanitization approach for Content-Disposition headers
- Exact allowlist validation implementation (multer fileFilter vs route-level check)
- Error message formatting

</decisions>

<specifics>
## Specific Ideas

No specific requirements — standard security hardening patterns apply.

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `searchService.ts`: Single search function with ILIKE pattern — straightforward to add escaping
- `tagService.ts`: `getBulletsForTag` uses same ILIKE pattern — same fix applies
- `attachments.ts` route: Already has multer with diskStorage and file size limit — add fileFilter
- `attachmentService.ts`: `getAttachment` already filters by userId — extend pattern to upload/list
- `EXT_MIME` map in attachments.ts: Already maps extensions to MIME types — can double as allowlist basis

### Established Patterns
- All routes use `requireAuth` middleware for user extraction
- Services filter by `userId` for data isolation (bullets, documents, bookmarks)
- Error pattern: 404 for "not found or access denied" (see deleteAttachment)

### Integration Points
- `createAttachment(userId, bulletId, ...)` — needs bullet ownership check before insert
- `getAttachmentsByBullet(userId, bulletId)` — needs bullet ownership check before list
- Content-Disposition header in GET /:id/file route — needs filename sanitization
- Multer config — needs fileFilter callback for type restriction

</code_context>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 16-injection-and-upload-hardening*
*Context gathered: 2026-03-15*
