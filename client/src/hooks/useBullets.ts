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
};

function bulletKey(documentId: string) {
  return ['bullets', documentId];
}

export function useDocumentBullets(documentId: string) {
  return useQuery<Bullet[]>({
    queryKey: bulletKey(documentId),
    queryFn: () => apiClient.get<Bullet[]>(`/api/documents/${documentId}/bullets`),
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
      const optimistic: Bullet = {
        id: `optimistic-${Date.now()}`,
        documentId: vars.documentId,
        userId: '',
        parentId: vars.parentId,
        content: vars.content,
        position: 0,
        isComplete: false,
        isCollapsed: false,
        deletedAt: null,
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
    onSettled: (_data, _err, vars) =>
      qc.invalidateQueries({ queryKey: bulletKey(vars.documentId) }),
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
