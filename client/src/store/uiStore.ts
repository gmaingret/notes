import { create } from 'zustand';
import { persist } from 'zustand/middleware';

type UiStore = {
  lastOpenedDocId: string | null;
  sidebarOpen: boolean;
  setLastOpenedDocId: (id: string | null) => void;
  setSidebarOpen: (open: boolean) => void;
};

export const useUiStore = create<UiStore>()(
  persist(
    (set) => ({
      lastOpenedDocId: null,
      sidebarOpen: true,
      setLastOpenedDocId: (id) => set({ lastOpenedDocId: id }),
      setSidebarOpen: (open) => set({ sidebarOpen: open }),
    }),
    { name: 'notes-ui' }
  )
);
