---
phase: 07-icons-fonts-and-pwa
plan: "05"
subsystem: ui
tags: [pwa, manifest, favicon, svg, sharp, png, vite]

# Dependency graph
requires:
  - phase: 07-03
    provides: Lucide icons replacing Unicode/emoji interactive icons
  - phase: 07-04
    provides: Inter and JetBrains Mono variable fonts from fontsource
provides:
  - SVG letter-mark favicon (dark background #1a1a1a, white "N", rx=18 rounded corners)
  - manifest.webmanifest with standalone display mode and icon references
  - 192x192 and 512x512 PNG home screen icons generated via sharp from favicon.svg
  - Updated index.html with favicon.svg and manifest link (replaced vite.svg default)
  - generate-icons.mjs script for future icon regeneration
affects: [phase-8, deployment, pwa]

# Tech tracking
tech-stack:
  added: [sharp (icon generation script, dev-only)]
  patterns: [commit generated PNGs to git — do NOT run icon generation on server]

key-files:
  created:
    - client/public/favicon.svg
    - client/public/manifest.webmanifest
    - client/public/icon-192.png
    - client/public/icon-512.png
    - client/scripts/generate-icons.mjs
  modified:
    - client/index.html

key-decisions:
  - "favicon.svg uses <text> with font-family='Arial, Helvetica, sans-serif' — librsvg on Linux can render system fonts so sharp can generate PNGs without path data"
  - "Generated PNGs committed to git — not regenerated on server — avoids sharp binary dependency in Docker image"
  - "No apple-touch-icon or apple-mobile-web-app-capable meta tags added — deprecated per MDN anti-patterns in RESEARCH.md"
  - "theme_color and background_color both #ffffff — light theme primary; dark mode theming handled by CSS custom properties"

patterns-established:
  - "PWA icon generation: local node script using sharp → commit outputs to git → server gets files via Docker COPY"

requirements-completed: [PWA-01, PWA-02, PWA-03]

# Metrics
duration: 8min
completed: 2026-03-10
---

# Phase 7 Plan 05: PWA Assets Summary

**SVG letter-mark favicon (N on #1a1a1a), manifest.webmanifest with standalone display, and 192/512px PNG icons generated via sharp — completing all 6 phase-7 requirements (VISL-01 thru PWA-03)**

## Performance

- **Duration:** ~8 min
- **Started:** 2026-03-10T18:30:00Z
- **Completed:** 2026-03-10T18:38:00Z
- **Tasks:** 1 auto task + 1 checkpoint (human-verify, auto-approved)
- **Files modified:** 6

## Accomplishments
- Created favicon.svg: letter-mark N, dark #1a1a1a rounded background, white text, Arial/Helvetica system font
- Created manifest.webmanifest: name="Notes", display="standalone", start_url="/", 192/512 icon references
- Generated icon-192.png (3.1KB) and icon-512.png (13KB) locally using sharp from the SVG
- Updated index.html: replaced /vite.svg with /favicon.svg, added `<link rel="manifest" href="/manifest.webmanifest">`
- All 24 iconsAndFonts tests green: VISL-01 (13 tests), VISL-02 (2), VISL-03 (2), PWA-01 (4), PWA-02 (2), PWA-03 (1)
- Deployed to server (scp + docker compose up -d --build) — build succeeded, container started

## Task Commits

Each task was committed atomically:

1. **Task 1: Create favicon SVG, manifest, PNG icons, update index.html** - `fac5cdd` (feat)

**Plan metadata:** (docs commit follows this summary)

## Files Created/Modified
- `client/public/favicon.svg` - Letter-mark N SVG favicon, dark background, no external font references
- `client/public/manifest.webmanifest` - PWA manifest: name, standalone display, start_url, 192/512 icon refs
- `client/public/icon-192.png` - 192x192 PNG home screen icon, generated via sharp (3.1KB)
- `client/public/icon-512.png` - 512x512 PNG home screen icon, generated via sharp (13KB)
- `client/scripts/generate-icons.mjs` - Node ESM script to regenerate PNGs from SVG (run locally, not on server)
- `client/index.html` - Updated favicon link to /favicon.svg, added manifest link

## Decisions Made
- Used `<text>` with `font-family="Arial, Helvetica, sans-serif"` in SVG — librsvg (used by sharp on Linux) supports system fonts, so PNGs render correctly without needing SVG path data for the letter N
- Generated PNGs are committed to git rather than generated at Docker build time — keeps the Docker image free of sharp binary dependency
- No apple-touch-icon or apple-mobile-web-app-capable meta tags per RESEARCH.md anti-patterns

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered
None. sharp was already installed from plan 02. SVG with Arial font rendered correctly via sharp/librsvg on the local machine. PNG sizes exceeded 1KB threshold (3.1KB and 13KB).

## User Setup Required
None — no external service configuration required.

## Next Phase Readiness
- Phase 7 (Icons, Fonts, and PWA) is now fully complete — all 6 requirements satisfied (VISL-01, VISL-02, VISL-03, PWA-01, PWA-02, PWA-03)
- App is deployed to https://notes.gregorymaingret.fr — awaiting user visual confirmation for final phase approval
- After user approval: push to phase-7/icons-fonts-and-pwa branch, open PR, merge to main

---
*Phase: 07-icons-fonts-and-pwa*
*Completed: 2026-03-10*
