import { useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';

type UndoStatus = { canUndo: boolean; canRedo: boolean };

const UNDO_STATUS_KEY = ['undo-status'];

export function useUndoStatus() {
  return useQuery<UndoStatus>({
    queryKey: UNDO_STATUS_KEY,
    queryFn: () => apiClient.get<UndoStatus>('/api/undo/status'),
    refetchInterval: false,
    staleTime: 0,
  });
}

/** Call after any mutation that affects undo state. */
export function useInvalidateUndoStatus() {
  const qc = useQueryClient();
  return () => qc.invalidateQueries({ queryKey: UNDO_STATUS_KEY });
}
