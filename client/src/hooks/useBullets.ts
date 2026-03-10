import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';

export type Bullet = {
  id: string;
  documentId: string;
  userId: string;
  parentId: string | null;
  content: string;
  position: number;
  isComplete: boolean;
  isCollapsed: boolean;
  deletedAt: string | null;
  note: string | null;
};

function bulletKey(documentId: string) {
  return ['bullets', documentId];
}

export function useDocumentBullets(documentId: string) {
  return useQuery<Bullet[]>({
    queryKey: bulletKey(documentId),
    queryFn: () => apiClient.get<Bullet[]>(`/api/bullets/documents/${documentId}/bullets`),
    enabled: !!documentId,
  });
}

export function useCreateBullet() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: {
      documentId: string;
      parentId: string | null;
      afterId: string | null;
      content: string;
    }) => apiClient.post<Bullet>('/api/bullets', vars),
    onMutate: async (vars) => {
      await qc.cancelQueries({ queryKey: bulletKey(vars.documentId) });
      const prev = qc.getQueryData<Bullet[]>(bulletKey(vars.documentId));
      const bullets = prev ?? [];

      // Compute an optimistic position so the bullet renders in the right place immediately
      let optimisticPosition = 0;
      if (vars.afterId) {
        const afterBullet = bullets.find(b => b.id === vars.afterId);
        if (afterBullet) {
          const siblings = bullets
            .filter(b => b.parentId === vars.parentId && !b.deletedAt)
            .sort((a, b) => a.position - b.position);
          const afterIdx = siblings.findIndex(s => s.id === vars.afterId);
          const nextSibling = afterIdx >= 0 ? siblings[afterIdx + 1] : null;
          optimisticPosition = nextSibling
            ? (afterBullet.position + nextSibling.position) / 2
            : afterBullet.position + 1;
        }
      } else {
        // afterId null → insert before all siblings
        const siblings = bullets.filter(b => b.parentId === vars.parentId && !b.deletedAt);
        if (siblings.length > 0) {
          optimisticPosition = Math.min(...siblings.map(s => s.position)) - 1;
        }
      }

      const optimistic: Bullet = {
        id: `optimistic-${Date.now()}`,
        documentId: vars.documentId,
        userId: '',
        parentId: vars.parentId,
        content: vars.content,
        position: optimisticPosition,
        isComplete: false,
        isCollapsed: false,
        deletedAt: null,
        note: null,
      };
      qc.setQueryData(bulletKey(vars.documentId), (old: Bullet[] = []) => [
        ...old,
        optimistic,
      ]);
      return { prev };
    },
    onError: (_err, vars, ctx) => {
      if (ctx?.prev) qc.setQueryData(bulletKey(vars.documentId), ctx.prev);
    },
    onSettled: (_data, _err, vars) =>
      qc.invalidateQueries({ queryKey: bulletKey(vars.documentId) }),
  });
}

export function usePatchBullet() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { id: string; documentId: string; content?: string; isComplete?: boolean; isCollapsed?: boolean }) =>
      apiClient.patch<Bullet>(`/api/bullets/${vars.id}`, vars),
    onMutate: async (vars) => {
      await qc.cancelQueries({ queryKey: bulletKey(vars.documentId) });
      const prev = qc.getQueryData<Bullet[]>(bulletKey(vars.documentId));
      qc.setQueryData(bulletKey(vars.documentId), (old: Bullet[] = []) =>
        old.map(b => b.id === vars.id ? { ...b, ...vars } : b)
      );
      return { prev };
    },
    onError: (_err, vars, ctx) => {
      if (ctx?.prev) qc.setQueryData(bulletKey(vars.documentId), ctx.prev);
    },
    onSettled: (_data, _err, vars) =>
      qc.invalidateQueries({ queryKey: bulletKey(vars.documentId) }),
  });
}

export function useSoftDeleteBullet() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { id: string; documentId: string }) =>
      apiClient.delete<void>(`/api/bullets/${vars.id}`),
    onMutate: async (vars) => {
      await qc.cancelQueries({ queryKey: bulletKey(vars.documentId) });
      const prev = qc.getQueryData<Bullet[]>(bulletKey(vars.documentId));
      qc.setQueryData(bulletKey(vars.documentId), (old: Bullet[] = []) =>
        old.map(b => b.id === vars.id ? { ...b, deletedAt: new Date().toISOString() } : b)
      );
      return { prev };
    },
    onError: (_err, vars, ctx) => {
      if (ctx?.prev) qc.setQueryData(bulletKey(vars.documentId), ctx.prev);
    },
    onSettled: (_data, _err, vars) => {
      qc.invalidateQueries({ queryKey: bulletKey(vars.documentId) });
      qc.invalidateQueries({ queryKey: ['tags'] });
      qc.invalidateQueries({ queryKey: ['tag-bullets'] });
    },
  });
}

export function useIndentBullet() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { id: string; documentId: string }) =>
      apiClient.post<Bullet>(`/api/bullets/${vars.id}/indent`),
    onSettled: (_data, _err, vars) =>
      qc.invalidateQueries({ queryKey: bulletKey(vars.documentId) }),
  });
}

export function useOutdentBullet() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { id: string; documentId: string }) =>
      apiClient.post<Bullet>(`/api/bullets/${vars.id}/outdent`),
    onSettled: (_data, _err, vars) =>
      qc.invalidateQueries({ queryKey: bulletKey(vars.documentId) }),
  });
}

export function useMoveBullet() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { id: string; documentId: string; newParentId: string | null; afterId: string | null }) =>
      apiClient.post<Bullet>(`/api/bullets/${vars.id}/move`, {
        newParentId: vars.newParentId,
        afterId: vars.afterId,
      }),
    onSettled: (_data, _err, vars) =>
      qc.invalidateQueries({ queryKey: bulletKey(vars.documentId) }),
  });
}

export function useSetCollapsed() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { id: string; documentId: string; isCollapsed: boolean }) =>
      apiClient.patch<Bullet>(`/api/bullets/${vars.id}`, { isCollapsed: vars.isCollapsed }),
    onMutate: async (vars) => {
      await qc.cancelQueries({ queryKey: bulletKey(vars.documentId) });
      const prev = qc.getQueryData<Bullet[]>(bulletKey(vars.documentId));
      qc.setQueryData(bulletKey(vars.documentId), (old: Bullet[] = []) =>
        old.map(b => b.id === vars.id ? { ...b, isCollapsed: vars.isCollapsed } : b)
      );
      return { prev };
    },
    onError: (_err, vars, ctx) => {
      if (ctx?.prev) qc.setQueryData(bulletKey(vars.documentId), ctx.prev);
    },
    onSettled: (_data, _err, vars) =>
      qc.invalidateQueries({ queryKey: bulletKey(vars.documentId) }),
  });
}

export function useMarkComplete() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { id: string; documentId: string; isComplete: boolean }) =>
      apiClient.patch<Bullet>(`/api/bullets/${vars.id}`, { isComplete: vars.isComplete }),
    onMutate: async (vars) => {
      await qc.cancelQueries({ queryKey: bulletKey(vars.documentId) });
      const prev = qc.getQueryData<Bullet[]>(bulletKey(vars.documentId));
      qc.setQueryData(bulletKey(vars.documentId), (old: Bullet[] = []) =>
        old.map(b => b.id === vars.id ? { ...b, isComplete: vars.isComplete } : b)
      );
      return { prev };
    },
    onError: (_err, vars, ctx) => {
      if (ctx?.prev) qc.setQueryData(bulletKey(vars.documentId), ctx.prev);
    },
    onSettled: (_data, _err, vars) =>
      qc.invalidateQueries({ queryKey: bulletKey(vars.documentId) }),
  });
}

export function useBulletUndoCheckpoint() {
  return useMutation({
    mutationFn: (vars: { id: string; content: string }) =>
      apiClient.post<void>(`/api/bullets/${vars.id}/undo-checkpoint`, { content: vars.content }),
  });
}

export function usePatchNote() {
  return useMutation({
    mutationFn: (vars: { id: string; note: string | null }) =>
      apiClient.patch<Bullet>(`/api/bullets/${vars.id}`, { note: vars.note }),
  });
}
