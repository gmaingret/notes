# Requirements: Notes — Security Hardening

**Defined:** 2026-03-15
**Core Value:** Users can capture and organize personal knowledge in an infinitely nested bullet outline that works seamlessly on both desktop and mobile, with all data staying private on their own server.

## v2.2 Requirements

Requirements for security hardening. Each maps to roadmap phases.

### Injection & XSS

- [ ] **INJ-01**: ILIKE metacharacters (%, _) are escaped in search queries before pattern matching
- [ ] **INJ-02**: ILIKE metacharacters are escaped in tag search queries (getBulletsForTag)
- [ ] **INJ-03**: SVG files are served with Content-Disposition: attachment (never inline) to prevent stored XSS

### Token & Session Security

- [ ] **SESS-01**: OAuth callback passes JWT via URL hash fragment (#token=) instead of query string (?token=)
- [ ] **SESS-02**: Client reads token from hash fragment and clears it from URL
- [ ] **SESS-03**: Refresh tokens are stored server-side and invalidated on logout
- [ ] **SESS-04**: Refresh tokens are invalidated on password change

### Upload & Attachment Security

- [ ] **UPLD-01**: File uploads are restricted to an allowlist of safe MIME types (no .html, .exe, .js, etc.)
- [ ] **UPLD-02**: Filenames in Content-Disposition headers are sanitized (quotes, newlines, control chars stripped)
- [ ] **UPLD-03**: Attachment upload verifies the authenticated user owns the target bullet

### Authentication Hardening

- [ ] **AUTH-01**: Password policy enforces character diversity (uppercase, lowercase, digit, or special char)
- [ ] **AUTH-02**: Passwords are checked against a list of commonly breached passwords

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
| INJ-01 | — | Pending |
| INJ-02 | — | Pending |
| INJ-03 | — | Pending |
| SESS-01 | — | Pending |
| SESS-02 | — | Pending |
| SESS-03 | — | Pending |
| SESS-04 | — | Pending |
| UPLD-01 | — | Pending |
| UPLD-02 | — | Pending |
| UPLD-03 | — | Pending |
| AUTH-01 | — | Pending |
| AUTH-02 | — | Pending |
| API-01 | — | Pending |
| API-02 | — | Pending |

**Coverage:**
- v2.2 requirements: 14 total
- Mapped to phases: 0
- Unmapped: 14 ⚠️

---
*Requirements defined: 2026-03-15*
*Last updated: 2026-03-15 after initial definition*
