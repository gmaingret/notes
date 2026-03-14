# PRD — Android Home Screen Widget: List Document

**Project**: notes (gmaingret/notes)
**Date**: 2026-03-14
**Status**: Draft

---

## 1. Overview

Add a resizable Android home screen widget that displays the root-level bullet points of a chosen document and lets the user add and delete items directly from the widget — ideal for grocery lists, shopping lists, or quick task lists.

---

## 2. Goals

- Allow users to pin any document as a home screen widget
- Show root-level bullets as a flat list in the widget
- Support adding a new bullet and deleting an existing bullet without opening the app
- Keep the widget in sync with server state

---

## 3. Non-Goals

- No nested bullet display (root level only)
- No item completion / checkbox toggle
- No drag-to-reorder in the widget
- No offline / local-only mode
- No iOS widget

---

## 4. User Stories

| ID | Story |
|----|-------|
| W-1 | As a user, I can add the Notes widget to my home screen and choose which document it shows. |
| W-2 | As a user, I can see the current bullet items of the selected document at a glance. |
| W-3 | As a user, I can tap "+" in the widget to type and add a new bullet to the document. |
| W-4 | As a user, I can tap a delete icon next to any bullet to remove it. |
| W-5 | As a user, I can manually refresh the widget to see the latest content. |
| W-6 | As a user, each widget instance can point to a different document. |

---

## 5. UX / Design

### 5.1 Widget Layout

```
┌─────────────────────────────────────┐
│ 📄 Grocery List               [↻]  │  ← Header row
├─────────────────────────────────────┤
│ • Milk                         [🗑] │
│ • Eggs                         [🗑] │
│ • Bread                        [🗑] │
│ • Orange juice                 [🗑] │
│   (scrollable)                      │
├─────────────────────────────────────┤
│              [+ Add item]           │  ← Footer row
└─────────────────────────────────────┘
```

- **Header**: document title (truncated) + refresh icon button
- **List area**: scrollable, one row per root-level bullet
  - Each row: bullet text (left) + trash icon (right)
- **Footer**: full-width "Add item" button

### 5.2 Minimum / Default Size

| Property | Value |
|----------|-------|
| Min width | 180 dp |
| Min height | 110 dp |
| Resize | Horizontal and vertical |
| Widget category | Home screen |

### 5.3 Add Item Flow

1. User taps **[+ Add item]**
2. A lightweight transparent dialog Activity (`AddBulletActivity`) slides up over the home screen
3. The dialog shows a single `TextField` and **Add** / **Cancel** buttons
4. On confirm: the new bullet is created via `POST /api/bullets` and the widget refreshes
5. On cancel or after creation: the activity dismisses and the widget updates

### 5.4 Delete Item Flow

1. User taps the trash icon on a row
2. `DELETE /api/bullets/{id}` is called immediately (no confirmation dialog)
3. The widget re-renders without that item

### 5.5 Widget Configuration

When the user adds the widget from the home screen:
1. Android launches `NotesWidgetConfigActivity` automatically
2. A simple document picker list is shown (calls `GET /api/documents`)
3. User taps the desired document
4. The widget is created showing that document's bullets

### 5.6 Empty State

When the document has no bullets:
```
┌─────────────────────────────┐
│ 📄 My List              [↻] │
├─────────────────────────────┤
│   No items yet.             │
├─────────────────────────────┤
│        [+ Add item]         │
└─────────────────────────────┘
```

### 5.7 Error / Loading State

- **Loading**: shows a spinner in the list area
- **Auth error / network error**: shows "Tap to refresh" message

---

## 6. Technical Specification

### 6.1 Tech Stack

| Component | Technology |
|-----------|-----------|
| Widget framework | Jetpack Glance 1.1.x (`androidx.glance:glance-appwidget`) |
| DI in widget | Hilt (`@AndroidEntryPoint` on `GlanceAppWidgetReceiver`) |
| State storage | Glance `AppWidgetStateDefinition` + DataStore |
| Widget prefs | DataStore (`appWidgetId → documentId`) |
| API calls | Existing `BulletRepository`, `DocumentRepository` via Hilt |
| Auth | Reuse existing `TokenStorage` (Tink-encrypted); widget calls `POST /api/auth/refresh` if access token is missing |

### 6.2 New Files

```
android/app/src/main/java/com/gmaingret/notes/
└── presentation/
    └── widget/
        ├── NotesWidgetReceiver.kt         # GlanceAppWidgetReceiver — registered in Manifest
        ├── NotesWidget.kt                 # Main Glance AppWidget (UI + state)
        ├── NotesWidgetConfigActivity.kt   # Document picker shown on widget add
        ├── AddBulletActivity.kt           # Transparent dialog Activity for text input
        └── WidgetPreferences.kt           # DataStore: appWidgetId → documentId mapping

android/app/src/main/res/xml/
└── notes_widget_info.xml                  # AppWidget provider metadata
```

### 6.3 Modified Files

| File | Change |
|------|--------|
| `android/gradle/libs.versions.toml` | Add Glance + Glance-Material3 versions |
| `android/app/build.gradle.kts` | Add Glance dependencies |
| `android/app/src/main/AndroidManifest.xml` | Register receiver + 2 activities |
| `TokenStorage.kt` (data/local) | Verify refresh token persists across app restarts (needed by widget) |

### 6.4 Existing Code Reused

| Existing | Used for |
|----------|---------|
| `GetDocumentsUseCase` | Config activity document list |
| `GetBulletsUseCase` | Fetching bullets in widget |
| `CreateBulletUseCase` | Adding bullet from overlay |
| `BulletRepository.deleteBullet()` | Delete action |
| `AuthApi.refresh()` | Fresh access token in widget context |
| `TokenStorage` | Shared token access |

### 6.5 API Calls Made by Widget

| Action | Endpoint |
|--------|---------|
| Load bullets | `GET /api/bullets/documents/{docId}/bullets` |
| Add bullet | `POST /api/bullets` `{documentId, content, parentId: null, position}` |
| Delete bullet | `DELETE /api/bullets/{id}` |
| Refresh token | `POST /api/auth/refresh` |
| Load document list (config) | `GET /api/documents` |

### 6.6 Widget Refresh Strategy

- **Periodic**: `updatePeriodMillis = 1 800 000` (30 min, minimum Android allows is 30 min)
- **On action**: Any delete or add triggers an immediate local state update + re-render
- **Manual**: Refresh button in header triggers API re-fetch
- **On app open**: Optionally broadcast a refresh intent when the main app is foregrounded

### 6.7 AndroidManifest Additions

```xml
<!-- Widget receiver -->
<receiver android:name=".presentation.widget.NotesWidgetReceiver"
          android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE"/>
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/notes_widget_info"/>
</receiver>

<!-- Config activity (auto-launched by Android on widget add) -->
<activity android:name=".presentation.widget.NotesWidgetConfigActivity"
          android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_CONFIGURE"/>
    </intent-filter>
</activity>

<!-- Overlay activity for typing a new bullet -->
<activity android:name=".presentation.widget.AddBulletActivity"
          android:theme="@style/Theme.AppCompat.Dialog"
          android:exported="false"/>
```

---

## 7. Constraints & Risks

| Risk | Mitigation |
|------|-----------|
| Glance does not support `TextField` natively | Use a separate dialog `Activity` (`AddBulletActivity`) |
| Widget runs outside app process — access token is in-memory only | Widget calls `POST /api/auth/refresh` to get a fresh token using the persisted refresh cookie |
| Android minimum widget update interval is 30 min | Manual refresh button + local optimistic updates on actions |
| Glance + Hilt integration requires specific setup | Follow official Glance + Hilt sample patterns |
| Widget may appear if user is logged out | Show "Please open the app to log in" error state |

---

## 8. Effort Estimate

| Task | Effort |
|------|--------|
| Glance dependencies + manifest + XML metadata | XS |
| `WidgetPreferences.kt` DataStore helper | XS |
| Auth token persistence check / fix | S |
| `NotesWidgetConfigActivity` (document picker) | S |
| `NotesWidget.kt` Glance UI (all states) | M |
| `AddBulletActivity.kt` overlay | S |
| Delete + refresh actions | S |
| Error / loading / empty states | S |
| Device/emulator testing | M |
| **Total** | **~1–2 days** |

Complexity driver: Glance + Hilt integration quirks and the auth token sharing across process boundaries. The rest closely follows existing app patterns.

---

## 9. Out of Scope for v1 (Potential v2)

- Swipe-to-delete gesture
- Completion checkbox / clear-completed
- Widget appearance customization (color, font size)
- Notification when widget document changes
- Multiple list views / tabbed widget
- Offline queue for add/delete when no network
