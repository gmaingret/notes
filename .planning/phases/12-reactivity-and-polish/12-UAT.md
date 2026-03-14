---
status: complete
phase: 12-reactivity-and-polish
source: 12-01-SUMMARY.md, 12-02-SUMMARY.md, 12-03-SUMMARY.md, 12-04-SUMMARY.md, 12-05-SUMMARY.md
started: 2026-03-14T12:00:00Z
updated: 2026-03-14T12:18:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Swipe Right to Complete
expected: Swipe a bullet to the right. Green background reveals proportionally. Haptic feedback at threshold. Release to mark complete. Row stays visible.
result: pass

### 2. Swipe Left to Delete
expected: Swipe a bullet to the left. Red background reveals proportionally. Haptic feedback at threshold. Release and the row slides off — bullet is deleted.
result: pass

### 3. Long-Press Context Menu
expected: Long-press on a bullet row. A context menu appears with options: Bookmark, Attachments, Delete.
result: pass

### 4. Bookmark a Bullet
expected: Use the context menu to bookmark a bullet. A star indicator appears on the bookmarked bullet row.
result: pass

### 5. View Attachments Inline
expected: On a bullet with attachments, use the context menu "Attachments" option. Attachments expand inline below the bullet — images show as thumbnails, non-image files show type icon and file size.
result: pass

### 6. Download an Attachment
expected: Tap on an attachment in the expanded list. The file downloads via the system download manager (notification appears in status bar).
result: pass

### 7. Search Bar
expected: Tap the search icon in the top app bar. The title transforms into a text input field. Type a query — after a brief delay (~300ms), search results appear as an overlay grouped by document with sticky headers.
result: pass

### 8. Search Result Highlighting
expected: In search results, the query term is visually highlighted within each matching bullet's text.
result: pass

### 9. Tap Search Result Navigates to Bullet
expected: Tap a search result. The search overlay closes, the correct document opens, and the view scrolls to the matching bullet.
result: pass

### 10. Chip-to-Search
expected: Tap a tag chip on a bullet (when not editing). The search bar activates with that tag's text pre-filled and results for that tag appear.
result: pass

### 11. Bookmarks Screen
expected: Open the navigation drawer. A "Bookmarks" entry appears above the document list. Tap it to see a list of all bookmarked bullets with their document attribution.
result: pass

### 12. Pull-to-Refresh on Bullet Tree
expected: Pull down on the bullet tree. A refresh indicator appears. Data reloads from server. Indicator disappears when done.
result: pass

### 13. Pull-to-Refresh on Document Drawer
expected: Open the document drawer. Pull down on the document list. A refresh indicator appears and the document list reloads.
result: pass

### 14. Larger Text for Readability
expected: Body text and titles appear at a comfortable mobile reading size (noticeably larger than default Material sizing).
result: pass

### 15. Smooth Expand/Collapse Animations
expected: Expanding or collapsing a note field or attachment list animates smoothly (content size transitions rather than snapping).
result: pass

### 16. Dark Theme
expected: Switch your phone to dark mode (system settings). The app follows with dark backgrounds and light text throughout all screens.
result: pass

## Summary

total: 16
passed: 16
issues: 0
pending: 0
skipped: 0

## Gaps

[none yet]
