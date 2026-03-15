# Requirements: Notes — Security Hardening

**Defined:** 2026-03-15
**Core Value:** Users can capture and organize personal knowledge in an infinitely nested bullet outline that works seamlessly on both desktop and mobile, with all data staying private on their own server.

## v2.2 Requirements

Requirements for security hardening. Each maps to roadmap phases.

### Injection & XSS

- [x] **INJ-01**: ILIKE metacharacters (%, _) are escaped in search queries before pattern matching
- [x] **INJ-02**: ILIKE metacharacters are escaped in tag search queries (getBulletsForTag)
- [x] **INJ-03**: SVG files are served with Content-Disposition: attachment (never inline) to prevent stored XSS

### Token & Session Security

- [x] **SESS-01**: OAuth callback passes JWT via URL hash fragment (#token=) instead of query string (?token=)
- [x] **SESS-02**: Client reads token from hash fragment and clears it from URL
- [ ] **SESS-03**: Refresh tokens are stored server-side and invalidated on logout
- [ ] **SESS-04**: Refresh tokens are invalidated on password change

### Upload & Attachment Security

- [x] **UPLD-01**: File uploads are restricted to an allowlist of safe MIME types (no .html, .exe, .js, etc.)
- [x] **UPLD-02**: Filenames in Content-Disposition headers are sanitized (quotes, newlines, control chars stripped)
- [x] **UPLD-03**: Attachment upload verifies the authenticated user owns the target bullet

### Authentication Hardening

- [x] **AUTH-01**: Password policy enforces character diversity (uppercase, lowercase, digit, or special char)
- [x] **AUTH-02**: Passwords are checked against a list of commonly breached passwords

### API Protection

- [ ] **API-01**: Rate limiting is applied to data endpoints (/api/bullets, /api/documents, /api/search, /api/tags, /api/attachments)
- [ ] **API-02**: CSRF token is required on state-changing endpoints as defense-in-depth alongside SameSite cookies

## Future Requirements (Deferred — LOW severity)

### Information Disclosure

- **INFO-01**: Registration endpoint returns generic error instead of revealing email existence
- **INFO-02**: Error logging sanitized to remove stack traces and internal paths

### Configuration

- **CONF-01**: Startup validation ensures all required env vars (JWT_SECRET, JWT_REFRESH_SECRET, etc.) are present
- **CONF-02**: Explicit JSON body size limit set on express.json()

### Misconfiguration

- **MISC-01**: SPA catch-all excludes /api/ prefix to surface proper 404s for mistyped API routes

## Out of Scope

| Feature | Reason |
|---------|--------|
| Web Application Firewall (WAF) | Self-hosted single-user; overkill for threat model |
| Two-factor authentication (2FA) | No admin panel, low user count — defer |
| Content Security Policy headers | Useful but separate initiative, not part of audit fixes |
| Penetration testing tooling | Manual audit already performed |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| INJ-01 | Phase 16 | Complete |
| INJ-02 | Phase 16 | Complete |
| INJ-03 | Phase 16 | Complete |
| UPLD-01 | Phase 16 | Complete |
| UPLD-02 | Phase 16 | Complete |
| UPLD-03 | Phase 16 | Complete |
| SESS-01 | Phase 17 | Complete |
| SESS-02 | Phase 17 | Complete |
| SESS-03 | Phase 17 | Pending |
| SESS-04 | Phase 17 | Pending |
| AUTH-01 | Phase 17 | Complete |
| AUTH-02 | Phase 17 | Complete |
| API-01 | Phase 18 | Pending |
| API-02 | Phase 18 | Pending |

**Coverage:**
- v2.2 requirements: 14 total
- Mapped to phases: 14
- Unmapped: 0 ✓

---
*Requirements defined: 2026-03-15*
*Last updated: 2026-03-15 after roadmap creation*
