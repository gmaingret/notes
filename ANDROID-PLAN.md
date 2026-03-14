# Plan: Native Android App (Kotlin/Jetpack Compose + Material Design 3)

## Context
The Notes app is currently a React 19 + TypeScript web client (Vite, Zustand, React Query) backed by Express + PostgreSQL. The goal is to create a native Android client in the same repo (`/android`) that talks to the same backend API. No new features — focus on reactivity, smooth animations, and proper Material Design 3 implementation.

## Architecture
- **Clean Architecture + MVVM**: domain/data/presentation layers
- **Libraries**: Retrofit + OkHttp, Hilt DI, Kotlin Coroutines/Flow, Jetpack Compose, Material 3, Navigation Compose
- **State**: ViewModels with StateFlow, collected via `collectAsStateWithLifecycle()`
- **Auth**: JWT bearer tokens, refresh via httpOnly cookie (OkHttp CookieJar), EncryptedSharedPreferences for access token

## 4 Sequential Prompts (MVP First)

### Prompt 1 — Foundation (`prompts/001-android-foundation.md`)
Project scaffolding, Gradle setup, package structure, Hilt DI, Retrofit API client with auth interceptor + token refresh authenticator, Login/Register screens, Material 3 theme shell, navigation graph.

### Prompt 2 — Documents (`prompts/002-android-documents.md`)
Document list in ModalNavigationDrawer, CRUD operations, float-based drag reorder, navigation to document view, last-opened persistence.

### Prompt 3 — Bullet Tree (`prompts/003-android-bullet-tree.md`)
Core feature: nested bullet tree with LazyColumn, tree flattening algorithm (port from web), create/edit/delete bullets, indent/outdent, collapse/expand with animation, complete toggle, notes field, drag-drop reorder with projection algorithm, debounced content saving, Enter/Backspace keyboard handling.

### Prompt 4 — Reactivity & Polish (`prompts/004-android-reactivity-polish.md`)
Loading/error states, pull-to-refresh, optimistic updates with rollback, swipe gestures (right=complete, left=delete), search with debounce, undo/redo, dark theme, Material 3 animations (AnimatedVisibility, animateItemPlacement, Crossfade).

## Key Files to Reference (web client patterns to port)
- `client/src/hooks/useBullets.ts` — bullet API contracts, optimistic update patterns
- `client/src/components/DocumentView/BulletTree.tsx` — `buildBulletMap`, `flattenTree`, `computeDragProjection`
- `client/src/components/DocumentView/BulletContent.tsx` — editing behavior (Enter/Backspace/debounce)
- `client/src/contexts/AuthContext.tsx` — token refresh flow
- `server/src/routes/auth.ts` — auth API contract

## Verification
After each prompt:
1. Build succeeds (`./gradlew assembleDebug` from `/android`)
2. App installs on emulator/device
3. Features from that phase work end-to-end against `https://notes.gregorymaingret.fr`
4. No crashes, proper error handling, smooth animations

## First Action After Plan Approval
Write this plan to `ANDROID-PLAN.md` at the project root.
