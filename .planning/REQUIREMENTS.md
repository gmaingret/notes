# Requirements: Notes v2.3

**Defined:** 2026-03-19
**Core Value:** Users can capture and organize personal knowledge in an infinitely nested bullet outline that works seamlessly on both desktop and mobile, with all data staying private on their own server.

## v2.3 Requirements

Requirements for the Robustness & Quality milestone. Each maps to roadmap phases.

### CI/CD

- [ ] **CICD-01**: Server CI workflow runs lint and tests on every PR to main
- [ ] **CICD-02**: Client CI workflow runs lint and build validation on every PR to main

### Error Handling

- [ ] **ERR-01**: All API endpoints return errors in a consistent format (`{ error, code?, details? }`)
- [ ] **ERR-02**: Undo/redo routes return user-friendly error responses (e.g., 422 "nothing to undo") instead of raw 500s
- [ ] **ERR-03**: React error boundary at DocumentView level catches rendering crashes and auto-resets on document navigation

### Client Resilience

- [ ] **RES-01**: Web client automatically retries failed requests after refreshing an expired access token (401 interceptor with shared promise lock and retry guard)
- [ ] **RES-02**: Mutation failures display toast notifications to the user (sonner-based, separate from existing UndoToast)

### Undo Coverage

- [ ] **UNDO-01**: User can undo/redo toggling bullet complete/incomplete
- [ ] **UNDO-02**: User can undo/redo changes to bullet notes
- [ ] **UNDO-03**: User can undo/redo bulk deletion of completed bullets

### Configuration

- [ ] **CONF-01**: UPLOAD_MAX_SIZE_MB and UPLOAD_PATH environment variables are wired to actual upload logic (not hardcoded)

### Code Quality

- [x] **QUAL-01**: BulletContent component (768 lines) decomposed into focused, testable sub-components
- [ ] **QUAL-02**: BulletNode component (487 lines) decomposed into focused, testable sub-components

## Future Requirements

Deferred from v2.3 scope.

### Pagination

- **PAG-01**: Documents list endpoint supports pagination (cursor or offset-based)

### Validation

- **VAL-01**: Client validates API responses at runtime using Zod schemas

## Out of Scope

| Feature | Reason |
|---------|--------|
| Documents list pagination | Low severity — unbounded list only matters at hundreds of docs |
| Client-side Zod validation | Defensive only — no current failures from unvalidated responses |
| Auto-deploy on merge | Deliberately manual deploy workflow (SCP before git push) per CLAUDE.md |
| Per-bullet error boundaries | Hundreds of boundary instances; DocumentView-level is sufficient |
| Infinite 401 retry | Must retry once only; second failure clears auth and redirects to login |
| Undo for collapse/expand | Would truncate redo history on every expand/collapse — harmful UX |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| CICD-01 | Phase 19 | Pending |
| CICD-02 | Phase 19 | Pending |
| ERR-01 | Phase 19 | Pending |
| ERR-02 | Phase 19 | Pending |
| CONF-01 | Phase 19 | Pending |
| ERR-03 | Phase 20 | Pending |
| RES-02 | Phase 20 | Pending |
| RES-01 | Phase 21 | Pending |
| UNDO-01 | Phase 22 | Pending |
| UNDO-02 | Phase 22 | Pending |
| UNDO-03 | Phase 22 | Pending |
| QUAL-01 | Phase 23 | Complete |
| QUAL-02 | Phase 23 | Pending |

**Coverage:**
- v2.3 requirements: 13 total
- Mapped to phases: 13
- Unmapped: 0

---
*Requirements defined: 2026-03-19*
*Last updated: 2026-03-19 after roadmap creation — all 13 requirements mapped*
