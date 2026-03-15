---
gsd_state_version: 1.0
milestone: v2.2
milestone_name: Security Hardening
status: ready_to_plan
stopped_at: Completed 18-api-protection 18-01-PLAN.md
last_updated: "2026-03-15T10:32:28.071Z"
last_activity: 2026-03-15 — v2.2 roadmap created; phases 16-18 defined
progress:
  total_phases: 3
  completed_phases: 3
  total_plans: 5
  completed_plans: 5
---

---
gsd_state_version: 1.0
milestone: v2.2
milestone_name: Security Hardening
status: ready_to_plan
stopped_at: null
last_updated: "2026-03-15T12:00:00Z"
last_activity: 2026-03-15 — Roadmap created; phases 16-18 defined
progress:
  total_phases: 3
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-15)

**Core value:** Users can capture and organize personal knowledge in an infinitely nested bullet outline that works seamlessly on both desktop and mobile, with all data staying private on their own server.
**Current focus:** Phase 16 — Injection and Upload Hardening

## Current Position

Phase: 16 of 18 (Injection and Upload Hardening)
Plan: 0 of TBD in current phase
Status: Ready to plan
Last activity: 2026-03-15 — v2.2 roadmap created; phases 16-18 defined

Progress: [████████░░] ~83% (15/18 phases complete across all milestones)

## Performance Metrics

**Velocity (all milestones):**

| Milestone | Phases | Plans | Days | Plans/Day |
|-----------|--------|-------|------|-----------|
| v1.0 MVP | 4 | 32 | 2 | 16 |
| v1.1 Mobile & UI Polish | 5 | 23 | 2 | 11.5 |
| v2.0 Native Android | 4 | 17 | 3 | 5.7 |
| v2.1 Widget | 3 | 8 | 1 | 8 |
| **Total** | **16** | **80** | **8** | **10** |
| Phase 16-injection-and-upload-hardening P01 | 6m | 2 tasks | 6 files |
| Phase 16-injection-and-upload-hardening P02 | 6m | 1 tasks | 3 files |
| Phase 17-auth-and-session-security P01 | 525533m | 2 tasks | 4 files |
| Phase 17-auth-and-session-security P02 | 4m | 2 tasks | 5 files |
| Phase 18-api-protection P01 | 1 | 2 tasks | 3 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.

Recent decisions affecting current work:
- All security fixes are backend-only (Express/Node server) except SESS-01/SESS-02 which require a client-side change to the OAuth callback handler
- [Phase 16-01]: escapeIlike placed in shared utils module to avoid duplication between searchService and tagService
- [Phase 16-01]: SVG force-download chosen over sanitization; filename sanitization applied to all attachments in Content-Disposition header
- [Phase 16-02]: Return 404 (not 403) when bullet not owned by user — hides bullet existence
- [Phase 16-02]: Multer ALLOWED_EXTENSIONS Set pattern for file type allowlist; fileFilter rejects with plain Error returning 400 directly
- [Phase 17-auth-and-session-security]: Common password check runs before character-diversity rules for better UX on well-known breached passwords
- [Phase 17-auth-and-session-security]: Static in-process common password blocklist chosen over npm package for self-hosted single-user app
- [Phase 17-auth-and-session-security]: Password policy enforced at registration only (not login) per plan specification
- [Phase 17-auth-and-session-security]: Store SHA-256 hash of refresh token in DB — DB compromise does not leak usable tokens
- [Phase 17-auth-and-session-security]: Soft revocation (revokedAt column) preserves audit trail; revokeAllUserTokensExcept keeps current session active on password change
- [Phase 18-api-protection]: dataLimiter set to 100 req/15min per IP — generous for normal usage, blocks automated scraping
- [Phase 18-api-protection]: CSRF API-02 closed as resolved-by-design: Bearer token auth never auto-sent by browsers; refresh endpoint uses SameSite=Strict

### Pending Todos

None.

### Blockers/Concerns

- Server disk reached 100% during Phase 8 deploy — run `docker builder prune` before building if disk is tight

## Session Continuity

Last session: 2026-03-15T10:30:35.367Z
Stopped at: Completed 18-api-protection 18-01-PLAN.md
Resume file: None
