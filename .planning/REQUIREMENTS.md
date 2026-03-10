# Requirements: Notes

**Defined:** 2026-03-10
**Core Value:** Users can capture and organize personal knowledge in an infinitely nested bullet outline that works seamlessly on both desktop and mobile, with all data staying private on their own server.

## v1.1 Requirements

Requirements for the Mobile & UI Polish milestone. Each maps to roadmap phases.

### Mobile Layout

- [ ] **MOBL-01**: User can open the sidebar on mobile via a hamburger button in the header
- [ ] **MOBL-02**: Sidebar auto-closes when user taps outside it on mobile
- [ ] **MOBL-03**: Sidebar has an explicit X close button on mobile
- [ ] **MOBL-04**: Sidebar slides in/out with a smooth off-canvas transition
- [ ] **MOBL-05**: All interactive elements have touch targets ≥44×44px
- [ ] **MOBL-06**: App layout uses 100dvh (not 100vh) for correct height on mobile browsers
- [ ] **MOBL-07**: User can toggle sidebar on desktop with Ctrl/Cmd+E

### Dark Mode

- [ ] **DRKM-01**: App automatically switches to dark theme when OS is in dark mode
- [ ] **DRKM-02**: All text/background color pairs meet WCAG AA in both light and dark themes
- [ ] **DRKM-03**: No white flash occurs on hard refresh in dark OS preference (FOUC prevention)
- [ ] **DRKM-04**: Browser chrome (scrollbars, inputs) adopts the active theme via color-scheme

### Visual Design

- [ ] **VISL-01**: All Unicode/emoji icons replaced with Lucide React SVG components
- [ ] **VISL-02**: UI text uses self-hosted Inter variable font (no Google Fonts)
- [ ] **VISL-03**: Inline code and tag chips use self-hosted JetBrains Mono variable font

### PWA

- [ ] **PWA-01**: App has a valid PWA manifest enabling Add to Home Screen
- [ ] **PWA-02**: App has 192×192 and 512×512 PNG icons for home screen
- [ ] **PWA-03**: App opens in standalone mode when launched from home screen

### Gestures

- [ ] **GEST-01**: Swiping right reveals a green backing with checkmark icon proportional to drag distance
- [ ] **GEST-02**: Swiping left reveals a red backing with trash icon proportional to drag distance
- [ ] **GEST-03**: Cancelled swipe snaps back to rest with ease-out animation
- [ ] **GEST-04**: Committed swipe animates the row out before it disappears
- [ ] **GEST-05**: dnd-kit drag sensor uses delay-based activation so it never intercepts horizontal swipes

### Quick-Open Palette

- [ ] **QKOP-01**: User can open the quick-open palette with Ctrl+K from anywhere in the app
- [ ] **QKOP-02**: Palette shows recent documents when opened with no query typed
- [ ] **QKOP-03**: Typing in the palette instantly fuzzy-matches document titles from cache
- [ ] **QKOP-04**: Typing ≥2 characters also searches bullet content via existing search endpoint
- [ ] **QKOP-05**: Bookmarks appear in palette results
- [ ] **QKOP-06**: User can navigate results with arrow keys and open with Enter
- [ ] **QKOP-07**: Palette closes on Escape or click outside

## v2 Requirements

Deferred to future milestones. Tracked but not in current roadmap.

### Dark Mode

- **DRKM-05**: User can manually override the system dark/light preference via a toggle in settings

### PWA

- **PWA-04**: Installed app on Android shows richer install prompt (requires service worker)

### Quick-Open Palette

- **QKOP-08**: Palette supports action commands (formatting, settings) beyond navigation

## Out of Scope

| Feature | Reason |
|---------|--------|
| Offline mode | Service worker cache invalidation conflicts with server-side undo/redo; explicitly out of scope per PROJECT.md |
| Manual dark mode toggle | Creates three-state complexity (system/light/dark) requiring settings UI; defer to v1.2 |
| iOS-specific workarounds | User preference — standard cross-browser patterns only |
| Framer Motion animation library | CSS transitions sufficient; add only if proven inadequate on real device |
| Full command palette (VS Code style) | ~15 real actions — navigation-only is appropriate for this app's scale |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| MOBL-01 | Phase 5 | Pending |
| MOBL-02 | Phase 5 | Pending |
| MOBL-03 | Phase 5 | Pending |
| MOBL-04 | Phase 5 | Pending |
| MOBL-05 | Phase 5 | Pending |
| MOBL-06 | Phase 5 | Pending |
| MOBL-07 | Phase 5 | Pending |
| DRKM-01 | Phase 6 | Pending |
| DRKM-02 | Phase 6 | Pending |
| DRKM-03 | Phase 6 | Pending |
| DRKM-04 | Phase 6 | Pending |
| VISL-01 | Phase 7 | Pending |
| VISL-02 | Phase 7 | Pending |
| VISL-03 | Phase 7 | Pending |
| PWA-01 | Phase 7 | Pending |
| PWA-02 | Phase 7 | Pending |
| PWA-03 | Phase 7 | Pending |
| GEST-01 | Phase 8 | Pending |
| GEST-02 | Phase 8 | Pending |
| GEST-03 | Phase 8 | Pending |
| GEST-04 | Phase 8 | Pending |
| GEST-05 | Phase 8 | Pending |
| QKOP-01 | Phase 8 | Pending |
| QKOP-02 | Phase 8 | Pending |
| QKOP-03 | Phase 8 | Pending |
| QKOP-04 | Phase 8 | Pending |
| QKOP-05 | Phase 8 | Pending |
| QKOP-06 | Phase 8 | Pending |
| QKOP-07 | Phase 8 | Pending |

**Coverage:**
- v1.1 requirements: 27 total
- Mapped to phases: 27
- Unmapped: 0 ✓

---
*Requirements defined: 2026-03-10*
*Last updated: 2026-03-10 after roadmap creation*
