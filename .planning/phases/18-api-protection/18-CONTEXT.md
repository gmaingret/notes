# Phase 18: API Protection - Context

**Gathered:** 2026-03-15
**Status:** Ready for planning

<domain>
## Phase Boundary

Add rate limiting to all data endpoints and resolve the CSRF audit finding. Rate limiting prevents abuse (data exfiltration, disk exhaustion, DoS). CSRF is resolved by documenting that Bearer token auth already prevents CSRF by design.

</domain>

<decisions>
## Implementation Decisions

### Rate limiting
- 100 requests per 15 minutes per IP for all data endpoints
- Per-IP keying (same pattern as existing auth limiters in app.ts)
- Apply to: /api/bullets, /api/documents, /api/search, /api/tags, /api/attachments, /api/bookmarks, /api/undo
- Use existing `express-rate-limit` package (already installed, v7.5.0)
- Return 429 Too Many Requests when limit exceeded
- Same limits for web and Android clients — no differentiation

### CSRF resolution
- Skip CSRF token implementation — resolved by design
- All data endpoints use Bearer token auth (Authorization header), which is CSRF-proof (browsers don't auto-send custom headers)
- Only cookie-authenticated endpoint is POST /api/auth/refresh, already behind SameSite: strict
- Document the justification in code comments and REQUIREMENTS.md (mark API-02 as resolved-by-design)
- No code changes needed for CSRF — just documentation

### Claude's Discretion
- Whether to use a single global data limiter or per-route group limiters
- Whether to add a comment in app.ts explaining the CSRF design decision
- Rate limit error response format (plain 429 vs JSON body with retry-after info)

</decisions>

<specifics>
## Specific Ideas

No specific requirements — standard rate limiting patterns apply.

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `app.ts`: Already imports `express-rate-limit` and configures two limiters (authLimiter: 20/15min, refreshLimiter: 60/15min)
- Same pattern can be replicated for data endpoints — just add another `rateLimit()` instance

### Established Patterns
- Rate limiters configured in `app.ts` createApp() factory before routes are mounted
- `standardHeaders: true, legacyHeaders: false` used consistently
- Routes mounted in `index.ts` via `app.use('/api/...', router)`

### Integration Points
- `app.ts`: Add new data limiter in the same section as authLimiter/refreshLimiter
- Apply via `app.use('/api/bullets', dataLimiter)` etc., or a single `app.use('/api', dataLimiter)` with exclusions for already-limited auth routes
- `index.ts`: Routes already mounted — no changes needed if limiter is applied in app.ts

</code_context>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 18-api-protection*
*Context gathered: 2026-03-15*
