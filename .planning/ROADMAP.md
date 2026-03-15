# Roadmap: Notes

## Milestones

- ✅ **v1.0 MVP** — Phases 1-4 (shipped 2026-03-10)
- ✅ **v1.1 Mobile & UI Polish** — Phases 5-8 (shipped 2026-03-11)
- ✅ **v2.0 Native Android Client** — Phases 9-12 (shipped 2026-03-14)
- ✅ **v2.1 Android Home Screen Widget** — Phases 13-15 (shipped 2026-03-15)
- 🚧 **v2.2 Security Hardening** — Phases 16-18 (in progress)

## Phases

<details>
<summary>✅ v1.0 MVP (Phases 1-4) — SHIPPED 2026-03-10</summary>

- [x] Phase 1: Foundation (6/6 plans) — completed 2026-03-09
- [x] Phase 2: Core Outliner (8/8 plans) — completed 2026-03-09
- [x] Phase 3: Rich Content (9/9 plans) — completed 2026-03-09
- [x] Phase 4: Attachments, Comments, and Mobile (9/9 plans) — completed 2026-03-10

Full details: [`.planning/milestones/v1.0-ROADMAP.md`](milestones/v1.0-ROADMAP.md)

</details>

<details>
<summary>✅ v1.1 Mobile & UI Polish (Phases 5-8) — SHIPPED 2026-03-11</summary>

- [x] Phase 5: Mobile Layout Foundation (4/4 plans) — completed 2026-03-10
- [x] Phase 6: Dark Mode (5/5 plans) — completed 2026-03-10
- [x] Phase 7: Icons, Fonts, and PWA (5/5 plans) — completed 2026-03-10
- [x] Phase 7.1: UI Polish Tweaks (4/4 plans) — completed 2026-03-10
- [x] Phase 8: Swipe Polish and Quick-Open Palette (5/5 plans) — completed 2026-03-11

Full details: [`.planning/milestones/v1.1-ROADMAP.md`](milestones/v1.1-ROADMAP.md)

</details>

<details>
<summary>✅ v2.0 Native Android Client (Phases 9-12) — SHIPPED 2026-03-14</summary>

- [x] Phase 9: Android Foundation and Auth (5/5 plans) — completed 2026-03-12
- [x] Phase 10: Document Management (3/3 plans) — completed 2026-03-12
- [x] Phase 11: Bullet Tree (4/4 plans) — completed 2026-03-12
- [x] Phase 12: Reactivity and Polish (5/5 plans) — completed 2026-03-12

Full details: [`.planning/milestones/v2.0-ROADMAP.md`](milestones/v2.0-ROADMAP.md)

</details>

<details>
<summary>✅ v2.1 Android Home Screen Widget (Phases 13-15) — SHIPPED 2026-03-15</summary>

- [x] Phase 13: Widget Foundation (4/4 plans) — completed 2026-03-14
- [x] Phase 14: Background Sync and Auth (2/2 plans) — completed 2026-03-14
- [x] Phase 15: Interactive Actions (2/2 plans) — completed 2026-03-14

Full details: [`.planning/milestones/v2.1-ROADMAP.md`](milestones/v2.1-ROADMAP.md)

</details>

### 🚧 v2.2 Security Hardening (In Progress)

**Milestone Goal:** Fix all HIGH and MEDIUM severity backend security vulnerabilities — injection, XSS, upload abuse, token exposure, weak auth, and missing API protection.

- [x] **Phase 16: Injection and Upload Hardening** — Escape ILIKE metacharacters, restrict upload types, sanitize filenames, serve SVG as attachment, verify bullet ownership on upload (completed 2026-03-15)
- [x] **Phase 17: Auth and Session Security** — Move JWT to hash fragment, implement server-side refresh token revocation on logout and password change, strengthen password policy (completed 2026-03-15)
- [x] **Phase 18: API Protection** — Add rate limiting across data endpoints, document CSRF mitigation by Bearer token architecture (completed 2026-03-15)

## Phase Details

### Phase 16: Injection and Upload Hardening
**Goal**: Server correctly defends against query injection and file upload abuse
**Depends on**: Nothing (first phase of milestone)
**Requirements**: INJ-01, INJ-02, INJ-03, UPLD-01, UPLD-02, UPLD-03
**Success Criteria** (what must be TRUE):
  1. Searching for text containing % or _ returns literal matches without exploding into wildcard results
  2. Navigating to a tag that contains % or _ shows only bullets tagged with that exact tag
  3. Uploading a .html, .exe, or .js file is rejected with an error message
  4. Downloading an uploaded SVG triggers a file-save dialog instead of rendering in the browser
  5. Uploading a file to a bullet owned by a different user is rejected with a 404 error
**Plans**: 2 plans
Plans:
- [ ] 16-01-PLAN.md — ILIKE escaping and SVG force-download
- [ ] 16-02-PLAN.md — File type allowlist and bullet ownership verification

### Phase 17: Auth and Session Security
**Goal**: Tokens are never exposed in URLs, sessions are revocable, and weak passwords are rejected at registration
**Depends on**: Phase 16
**Requirements**: SESS-01, SESS-02, SESS-03, SESS-04, AUTH-01, AUTH-02
**Success Criteria** (what must be TRUE):
  1. After Google OAuth login, the browser URL bar shows no token parameter (hash fragment is cleared immediately)
  2. After logout, the old refresh token cannot be used to obtain a new access token
  3. After a password change, existing sessions on other devices are invalidated
  4. Registering with a common password (e.g., "Password1") is rejected with an informative error
  5. Registering with a password lacking character diversity is rejected with a clear policy message
**Plans**: 2 plans
Plans:
- [ ] 17-01-PLAN.md — Password policy and OAuth hash fragment fix
- [ ] 17-02-PLAN.md — Refresh token revocation and password change endpoint

### Phase 18: API Protection
**Goal**: Data endpoints are protected against brute-force abuse, and CSRF is mitigated by Bearer token architecture
**Depends on**: Phase 17
**Requirements**: API-01, API-02
**Success Criteria** (what must be TRUE):
  1. Sending more than the configured request limit to /api/bullets in a short window returns 429 Too Many Requests
  2. CSRF is mitigated by design: all data endpoints require Bearer token auth (not auto-sent by browsers), and the refresh endpoint uses SameSite=Strict cookies
  3. Normal in-app usage (create bullet, edit, delete) continues to work without any visible change for the user
**Plans**: 1 plan
Plans:
- [ ] 18-01-PLAN.md — Rate limiting and CSRF documentation

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Foundation | v1.0 | 6/6 | Complete | 2026-03-09 |
| 2. Core Outliner | v1.0 | 8/8 | Complete | 2026-03-09 |
| 3. Rich Content | v1.0 | 9/9 | Complete | 2026-03-09 |
| 4. Attachments, Comments, and Mobile | v1.0 | 9/9 | Complete | 2026-03-10 |
| 5. Mobile Layout Foundation | v1.1 | 4/4 | Complete | 2026-03-10 |
| 6. Dark Mode | v1.1 | 5/5 | Complete | 2026-03-10 |
| 7. Icons, Fonts, and PWA | v1.1 | 5/5 | Complete | 2026-03-10 |
| 7.1. UI Polish Tweaks | v1.1 | 4/4 | Complete | 2026-03-10 |
| 8. Swipe Polish and Quick-Open Palette | v1.1 | 5/5 | Complete | 2026-03-11 |
| 9. Android Foundation and Auth | v2.0 | 5/5 | Complete | 2026-03-12 |
| 10. Document Management | v2.0 | 3/3 | Complete | 2026-03-12 |
| 11. Bullet Tree | v2.0 | 4/4 | Complete | 2026-03-12 |
| 12. Reactivity and Polish | v2.0 | 5/5 | Complete | 2026-03-12 |
| 13. Widget Foundation | v2.1 | 4/4 | Complete | 2026-03-14 |
| 14. Background Sync and Auth | v2.1 | 2/2 | Complete | 2026-03-14 |
| 15. Interactive Actions | v2.1 | 2/2 | Complete | 2026-03-14 |
| 16. Injection and Upload Hardening | v2.2 | 2/2 | Complete | 2026-03-15 |
| 17. Auth and Session Security | v2.2 | 2/2 | Complete | 2026-03-15 |
| 18. API Protection | 1/1 | Complete    | 2026-03-15 | - |
