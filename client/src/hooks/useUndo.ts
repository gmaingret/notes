import { useEffect, useCallback } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useUiStore } from '../store/uiStore';
import { apiClient } from '../api/client';

export function useGlobalKeyboard() {
  const queryClient = useQueryClient();
  const { sidebarOpen, setSidebarOpen } = useUiStore();

  const handleUndo = useCallback(async () => {
    await apiClient.post('/api/undo', {});
    // Invalidate ALL bullet queries regardless of document — undo scope is
    // per-user globally (UNDO-02), so a previously-visited document may have
    // been affected. Using the ['bullets'] prefix (no docId) ensures every
    // cached bullet query is stale and will re-fetch on next visit.
    await queryClient.invalidateQueries({ queryKey: ['bullets'] });
  }, [queryClient]);

  const handleRedo = useCallback(async () => {
    await apiClient.post('/api/redo', {});
    await queryClient.invalidateQueries({ queryKey: ['bullets'] });
  }, [queryClient]);

  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      const mod = e.ctrlKey || e.metaKey;
      if (!mod) return;

      // Skip undo/redo if a contenteditable has focus — BulletContent handles it there.
      // This prevents double-firing to the same endpoint.
      const isContentEditable = (document.activeElement as HTMLElement | null)?.contentEditable === 'true';

      // Ctrl+Z = undo (must preventDefault to block browser native undo)
      if (e.key === 'z' && !e.shiftKey) {
        if (isContentEditable) return; // let BulletContent handle it
        e.preventDefault();
        void handleUndo();
        return;
      }
      // Ctrl+Y = redo
      if (e.key === 'y') {
        if (isContentEditable) return; // let BulletContent handle it
        e.preventDefault();
        void handleRedo();
        return;
      }
      // Ctrl+E = toggle sidebar
      if (e.key === 'e') {
        e.preventDefault();
        setSidebarOpen(!sidebarOpen);
        return;
      }
      // Ctrl+P = search (Phase 3 — no-op stub)
      if (e.key === 'p') {
        e.preventDefault();
        console.log('[Phase 3] Search not yet implemented');
        return;
      }
      // Ctrl+* = bookmarks (Phase 3 — no-op stub)
      // '*' key = Shift+8, so check e.key === '*'
      if (e.key === '*') {
        e.preventDefault();
        console.log('[Phase 3] Bookmarks not yet implemented');
        return;
      }
    }

    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [handleUndo, handleRedo, sidebarOpen, setSidebarOpen]);
}
