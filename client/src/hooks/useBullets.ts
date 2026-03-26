import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
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

// ─── Generic optimistic mutation factory ────────────────────────────────────

type OptimisticVars = { documentId: string };

function useOptimisticBulletMutation<TVars extends OptimisticVars>(opts: {
  mutationFn: (vars: TVars) => Promise<unknown>;
  /** Return updated cache. If omitted, no optimistic update (just invalidate). */
  optimisticUpdate?: (old: Bullet[], vars: TVars) => Bullet[];
  errorMessage: string;
  /** Extra queryKeys to invalidate on settle (beyond bullets). */
  extraInvalidate?: string[][];
  /** If set, run onSuccess instead of onSettled invalidation. */
  skipSettledInvalidate?: boolean;
}) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: opts.mutationFn,
    onMutate: opts.optimisticUpdate
      ? async (vars: TVars) => {
          await qc.cancelQueries({ queryKey: bulletKey(vars.documentId) });
          const prev = qc.getQueryData<Bullet[]>(bulletKey(vars.documentId));
          qc.setQueryData(bulletKey(vars.documentId), (old: Bullet[] = []) =>
            opts.optimisticUpdate!(old, vars)
          );
          return { prev };
        }
      : undefined,
    onError: (err: unknown, vars: TVars, ctx: { prev?: Bullet[] } | undefined) => {
      if (ctx?.prev) qc.setQueryData(bulletKey(vars.documentId), ctx.prev);
      toast.error(opts.errorMessage, { description: (err as Error).message });
    },
    onSettled: opts.skipSettledInvalidate
      ? undefined
      : (_data: unknown, _err: unknown, vars: TVars) => {
          qc.invalidateQueries({ queryKey: bulletKey(vars.documentId) });
          for (const key of opts.extraInvalidate ?? []) {
            qc.invalidateQueries({ queryKey: key });
          }
        },
  });
}

// ─── Mutations ──────────────────────────────────────────────────────────────

export function useCreateBullet() {
  const qc = useQueryClient();

  const mutation = useMutation({
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
        const siblings = bullets.filter(b => b.parentId === vars.parentId && !b.deletedAt);
        if (siblings.length > 0) {
          optimisticPosition = Math.min(...siblings.map(s => s.position)) - 1;
        }
      }

      const optimisticId = `optimistic-${Date.now()}`;
      const optimistic: Bullet = {
        id: optimisticId,
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
      return { prev, optimisticId };
    },
    onError: (err, vars, ctx) => {
      if (ctx?.prev) qc.setQueryData(bulletKey(vars.documentId), ctx.prev);
      toast.error('Failed to save bullet', { description: (err as Error).message });
    },
    onSettled: (_data, _err, vars) =>
      qc.invalidateQueries({ queryKey: bulletKey(vars.documentId) }),
  });

  return mutation;
}

export function usePatchBullet() {
  return useOptimisticBulletMutation<{ id: string; documentId: string; content?: string; isComplete?: boolean; isCollapsed?: boolean }>({
    mutationFn: (vars) => apiClient.patch<Bullet>(`/api/bullets/${vars.id}`, vars),
    optimisticUpdate: (old, vars) => old.map(b => b.id === vars.id ? { ...b, ...vars } : b),
    errorMessage: 'Failed to save bullet',
  });
}

export function useSoftDeleteBullet() {
  return useOptimisticBulletMutation<{ id: string; documentId: string }>({
    mutationFn: (vars) => apiClient.delete<void>(`/api/bullets/${vars.id}`),
    optimisticUpdate: (old, vars) =>
      old.map(b => b.id === vars.id ? { ...b, deletedAt: new Date().toISOString() } : b),
    errorMessage: 'Failed to delete bullet',
    extraInvalidate: [['tags'], ['tag-bullets']],
  });
}

function useSimpleBulletMutation(
  urlSuffix: string,
  errorMessage: string,
  method: 'post' | 'patch' = 'post'
) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { id: string; documentId: string }) =>
      method === 'post'
        ? apiClient.post<Bullet>(`/api/bullets/${vars.id}/${urlSuffix}`)
        : apiClient.patch<Bullet>(`/api/bullets/${vars.id}`, {}),
    onError: (err: unknown) => {
      toast.error(errorMessage, { description: (err as Error).message });
    },
    onSettled: (_data: unknown, _err: unknown, vars: { id: string; documentId: string }) =>
      qc.invalidateQueries({ queryKey: bulletKey(vars.documentId) }),
  });
}

export function useIndentBullet() {
  return useSimpleBulletMutation('indent', 'Failed to indent bullet');
}

export function useOutdentBullet() {
  return useSimpleBulletMutation('outdent', 'Failed to outdent bullet');
}

export function useMoveBullet() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { id: string; documentId: string; newParentId: string | null; afterId: string | null }) =>
      apiClient.post<Bullet>(`/api/bullets/${vars.id}/move`, {
        newParentId: vars.newParentId,
        afterId: vars.afterId,
      }),
    onError: (err: unknown) => {
      toast.error('Failed to reorder bullet', { description: (err as Error).message });
    },
    onSettled: (_data: unknown, _err: unknown, vars: { id: string; documentId: string; newParentId: string | null; afterId: string | null }) =>
      qc.invalidateQueries({ queryKey: bulletKey(vars.documentId) }),
  });
}

export function useSetCollapsed() {
  return useOptimisticBulletMutation<{ id: string; documentId: string; isCollapsed: boolean }>({
    mutationFn: (vars) => apiClient.patch<Bullet>(`/api/bullets/${vars.id}`, { isCollapsed: vars.isCollapsed }),
    optimisticUpdate: (old, vars) =>
      old.map(b => b.id === vars.id ? { ...b, isCollapsed: vars.isCollapsed } : b),
    errorMessage: 'Failed to collapse bullet',
    skipSettledInvalidate: true,
  });
}

export function useMarkComplete() {
  return useOptimisticBulletMutation<{ id: string; documentId: string; isComplete: boolean }>({
    mutationFn: (vars) => apiClient.patch<Bullet>(`/api/bullets/${vars.id}`, { isComplete: vars.isComplete }),
    optimisticUpdate: (old, vars) =>
      old.map(b => b.id === vars.id ? { ...b, isComplete: vars.isComplete } : b),
    errorMessage: 'Failed to mark bullet complete',
  });
}

export function useBulletUndoCheckpoint() {
  return useMutation({
    mutationFn: (vars: { id: string; content: string; previousContent: string }) =>
      apiClient.post<void>(`/api/bullets/${vars.id}/undo-checkpoint`, {
        content: vars.content,
        previousContent: vars.previousContent,
      }),
  });
}

export function usePatchNote() {
  return useMutation({
    mutationFn: (vars: { id: string; note: string | null }) =>
      apiClient.patch<Bullet>(`/api/bullets/${vars.id}`, { note: vars.note }),
  });
}
