import { create } from 'zustand';
import { persist } from 'zustand/middleware';

/** Apply theme class to <html> and update color-scheme meta tag. */
function applyTheme(mode: 'system' | 'light' | 'dark') {
  if (typeof document === 'undefined') return;
  const isDark =
    mode === 'dark' ||
    (mode === 'system' && typeof window !== 'undefined' && window.matchMedia?.('(prefers-color-scheme: dark)').matches);

  document.documentElement.classList.toggle('dark', isDark);
  document.documentElement.classList.toggle('light', !isDark);

  const meta = document.querySelector('meta[name="color-scheme"]');
  if (meta) meta.setAttribute('content', isDark ? 'dark' : 'light');
}

// Apply on load from persisted state (before React renders)
try {
  const stored = JSON.parse(localStorage.getItem('notes-ui') || '{}');
  const mode = stored?.state?.themeMode || 'system';
  applyTheme(mode);
} catch { /* ignore */ }

// Re-apply when system preference changes (only matters in 'system' mode)
if (typeof window !== 'undefined' && window.matchMedia) {
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
    const stored = JSON.parse(localStorage.getItem('notes-ui') || '{}');
    const mode = stored?.state?.themeMode || 'system';
    if (mode === 'system') applyTheme('system');
  });
}

export type CanvasView =
  | { type: 'document' }
  | { type: 'filtered'; chipType: 'tag' | 'mention' | 'date'; chipValue: string }
  | { type: 'bookmarks' };

type ThemeMode = 'system' | 'light' | 'dark';

type UiStore = {
  lastOpenedDocId: string | null;
  sidebarOpen: boolean;
  sidebarTab: 'docs' | 'tags' | 'bookmarks';
  canvasView: CanvasView;
  searchOpen: boolean;
  focusedBulletId: string | null;
  quickOpenOpen: boolean;
  themeMode: ThemeMode;
  setLastOpenedDocId: (id: string | null) => void;
  setSidebarOpen: (open: boolean) => void;
  setSidebarTab: (tab: 'docs' | 'tags' | 'bookmarks') => void;
  setCanvasView: (view: CanvasView) => void;
  setSearchOpen: (open: boolean) => void;
  setFocusedBulletId: (id: string | null) => void;
  setQuickOpenOpen: (open: boolean) => void;
  setThemeMode: (mode: ThemeMode) => void;
};

export const useUiStore = create<UiStore>()(
  persist(
    (set) => ({
      lastOpenedDocId: null,
      sidebarOpen: true,
      sidebarTab: 'docs',
      canvasView: { type: 'document' } as CanvasView,
      searchOpen: false,
      focusedBulletId: null,
      quickOpenOpen: false,
      themeMode: 'system' as ThemeMode,
      setLastOpenedDocId: (id) => set({ lastOpenedDocId: id }),
      setSidebarOpen: (open) => set({ sidebarOpen: open }),
      setSidebarTab: (tab) => set({ sidebarTab: tab }),
      setCanvasView: (view) => set({ canvasView: view }),
      setSearchOpen: (open) => set({ searchOpen: open }),
      setFocusedBulletId: (id) => set({ focusedBulletId: id }),
      setQuickOpenOpen: (open) => set({ quickOpenOpen: open }),
      setThemeMode: (mode) => {
        set({ themeMode: mode });
        applyTheme(mode);
      },
    }),
    {
      name: 'notes-ui',
      partialize: (state) => ({
        lastOpenedDocId: state.lastOpenedDocId,
        themeMode: state.themeMode,
      }),
    }
  )
);
