import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export type CanvasView =
  | { type: 'document' }
  | { type: 'filtered'; chipType: 'tag' | 'mention' | 'date'; chipValue: string }
  | { type: 'bookmarks' };

type UiStore = {
  lastOpenedDocId: string | null;
  sidebarOpen: boolean;
  sidebarTab: 'docs' | 'tags' | 'bookmarks';
  canvasView: CanvasView;
  searchOpen: boolean;
  focusedBulletId: string | null;
  setLastOpenedDocId: (id: string | null) => void;
  setSidebarOpen: (open: boolean) => void;
  setSidebarTab: (tab: 'docs' | 'tags' | 'bookmarks') => void;
  setCanvasView: (view: CanvasView) => void;
  setSearchOpen: (open: boolean) => void;
  setFocusedBulletId: (id: string | null) => void;
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
      setLastOpenedDocId: (id) => set({ lastOpenedDocId: id }),
      setSidebarOpen: (open) => set({ sidebarOpen: open }),
      setSidebarTab: (tab) => set({ sidebarTab: tab }),
      setCanvasView: (view) => set({ canvasView: view }),
      setSearchOpen: (open) => set({ searchOpen: open }),
      setFocusedBulletId: (id) => set({ focusedBulletId: id }),
    }),
    {
      name: 'notes-ui',
      partialize: (state) => ({
        lastOpenedDocId: state.lastOpenedDocId,
        sidebarOpen: state.sidebarOpen,
      }),
    }
  )
);
