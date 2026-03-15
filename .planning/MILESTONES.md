# Milestones
## v2.1 Android Home Screen Widget (Shipped: 2026-03-15)

**Phases completed:** 3 phases (13-15), 8 plans
**Timeline:** 2026-03-14 (1 day)
**Files changed:** 60 | Lines added: 8,600 | Widget Kotlin LOC: 2,417 | Test LOC: 1,492
**Requirements:** 12/12 satisfied | **Audit:** tech_debt (no blockers) | **Nyquist:** 3/3 compliant

**Key accomplishments:**
1. Jetpack Glance widget with document picker config activity, auth gate, Material 3 theming (light/dark), and all display states (loading, error, empty, session expired, document not found)
2. WorkManager 15-minute periodic background sync with Tink-encrypted WidgetStateStore cache, independent auth via persisted refresh token, and graceful SESSION_EXPIRED handling
3. In-app mutation triggers — BulletTreeViewModel fires widget refresh at 9 mutation call sites; onResume refresh; immediate logout/login state management
4. Delete bullet directly from widget via ActionCallback with optimistic remove, server rollback on failure, and 401 auth detection
5. Add bullet via lightweight transparent overlay Activity with pre-focused text field, optimistic insert at top of list, temp-to-real ID replacement on server confirmation
6. 58 unit tests across 10 test files covering all widget logic with MockK + Robolectric

**Known tech debt:** WidgetEntryPoint.createBulletUseCase() orphaned (AddBulletActivity uses @Inject directly); clearAll() on single widget deletion affects multi-widget scenarios; post-login widget recovery is eventual (up to 15 min)

**Archive:** `.planning/milestones/v2.1-ROADMAP.md` | `.planning/milestones/v2.1-REQUIREMENTS.md` | `.planning/milestones/v2.1-MILESTONE-AUDIT.md`

---

## v2.0 Native Android Client (Shipped: 2026-03-14)

**Phases completed:** 4 phases (9-12), 17 plans, ~35 tasks
**Timeline:** 2026-03-12 → 2026-03-14 (3 days)
**Files changed:** 156 | Lines added: 13,086 | Kotlin LOC: 12,200

**Key accomplishments:**
1. Native Android app with Clean Architecture (MVVM + Use Cases), Hilt DI, Retrofit + OkHttp with JWT auth, Google SSO via Credential Manager, Tink-encrypted token storage
2. Document management in ModalNavigationDrawer with full CRUD, drag-reorder via Reorderable library, last-opened persistence across cold starts
3. Infinite nested bullet outliner in flat LazyColumn with FlattenTreeUseCase DFS, all editing interactions (Enter/Backspace/indent/outdent), markdown rendering, #tag/@mention/!!date chips
4. Swipe-to-complete (green) and swipe-to-delete (red) with proportional reveal and haptic feedback, long-press context menu
5. Inline search with 300ms debounce, bookmarks screen, attachment viewer with Coil image loading, pull-to-refresh on all screens
6. Material 3 dark theme, smooth animations (AnimatedVisibility, Crossfade, animateContentSize), 48dp touch targets

**Known tech debt:** refreshToken cookie not cleared on logout (DataStoreCookieJar.clearAll never called); TagApi.getBulletsByTag defined but unused

**Archive:** `.planning/milestones/v2.0-ROADMAP.md` | `.planning/milestones/v2.0-REQUIREMENTS.md`

---

## v1.1 Mobile & UI Polish (Shipped: 2026-03-11)

**Phases completed:** 5 phases (5, 6, 7, 7.1, 8), 23 plans
**Timeline:** 2026-03-10 → 2026-03-11 (2 days)

**Key accomplishments:**
1. Responsive mobile layout — off-canvas sidebar drawer with CSS translateX, hamburger button, backdrop dismiss, X close button, 44×44px touch targets, and Ctrl+E desktop toggle
2. System-preference dark mode — full CSS custom property token system (light/dark), FOUC-prevention synchronous inline script, color-scheme meta tag, WCAG AA contrast throughout
3. Lucide SVG icons replacing all Unicode/emoji glyphs; Inter Variable + JetBrains Mono Variable self-hosted (no Google Fonts); SVG letter-mark favicon; PWA manifest for home screen installation
4. UI polish — FocusToolbar 11 Lucide icons, 2px bullet row breathing room, persistent sidebar footer (Export all / Logout), + button auto-opens inline rename on new documents
5. Swipe animation polish — proportional icon scale (0.5x→1.2x pulse at threshold), exit-direction slide-off animation before mutation via onTransitionEnd
6. Quick-open palette (Ctrl+F) with recent docs empty state, grouped search results (Documents / Bullets / Bookmarks), full keyboard navigation, mobile search button in document header

**Archive:** `.planning/milestones/v1.1-ROADMAP.md` | `.planning/milestones/v1.1-REQUIREMENTS.md`

---


## v1.0 MVP (Shipped: 2026-03-10)

**Phases completed:** 4 phases, 32 plans
**Timeline:** 2026-03-09 → 2026-03-10 (2 days)
**Files changed:** 204 | Lines added: 42,417

**Key accomplishments:**
1. Self-hosted Docker app at notes.gregorymaingret.fr — email/password + Google SSO, document management, Markdown export
2. Infinite bullet outliner with keyboard shortcuts (Tab/Shift+Tab/Ctrl+arrows), drag-and-drop reorder, collapse/zoom with breadcrumbs
3. Server-persisted undo/redo — 50 levels, survives page refresh, wraps all mutations
4. Inline markdown rendering, #tag/@mention/!!date chips, Tag Browser sidebar, full-text search, bookmarks
5. File attachments (image Lightbox, PDF thumbnail, any file type on Docker volume), bullet comments with slide-in panel
6. Mobile support — swipe right=complete, swipe left=delete, long-press context menu, FocusToolbar above keyboard

**Archive:** `.planning/milestones/v1.0-ROADMAP.md` | `.planning/milestones/v1.0-REQUIREMENTS.md`

---

