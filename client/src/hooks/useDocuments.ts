import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';

export type Document = {
  id: string;
  userId: string;
  title: string;
  position: number;
  lastOpenedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

const DOCS_KEY = ['documents'];

export function useDocuments() {
  return useQuery<Document[]>({
    queryKey: DOCS_KEY,
    queryFn: () => apiClient.get<Document[]>('/api/documents'),
  });
}

export function useCreateDocument() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (title?: string) =>
      apiClient.post<Document>('/api/documents', title ? { title } : {}),
    onSuccess: () => qc.invalidateQueries({ queryKey: DOCS_KEY }),
  });
}

export function useRenameDocument() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, title }: { id: string; title: string }) =>
      apiClient.patch<Document>(`/api/documents/${id}`, { title }),
    onSuccess: () => qc.invalidateQueries({ queryKey: DOCS_KEY }),
  });
}

export function useDeleteDocument() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      apiClient.delete<void>(`/api/documents/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: DOCS_KEY }),
  });
}

export function useReorderDocument() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, afterId }: { id: string; afterId: string | null }) =>
      apiClient.patch<Document>(`/api/documents/${id}/position`, { afterId }),
    onMutate: async ({ id, afterId }) => {
      // Optimistic update: reorder list locally without waiting for server
      await qc.cancelQueries({ queryKey: DOCS_KEY });
      const prev = qc.getQueryData<Document[]>(DOCS_KEY) ?? [];

      const item = prev.find(d => d.id === id);
      if (!item) return { prev };

      const rest = prev.filter(d => d.id !== id);
      const afterIdx = afterId ? rest.findIndex(d => d.id === afterId) : -1;
      const next = [
        ...rest.slice(0, afterIdx + 1),
        item,
        ...rest.slice(afterIdx + 1),
      ];
      qc.setQueryData(DOCS_KEY, next);
      return { prev };
    },
    onError: (_err, _vars, ctx) => {
      if (ctx?.prev) qc.setQueryData(DOCS_KEY, ctx.prev);
    },
    onSettled: () => qc.invalidateQueries({ queryKey: DOCS_KEY }),
  });
}

export function useOpenDocument() {
  return useMutation({
    mutationFn: (id: string) =>
      apiClient.post<void>(`/api/documents/${id}/open`),
  });
}

function triggerDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

export function useExportDocument() {
  return useMutation({
    mutationFn: async (doc: { id: string; title: string }) => {
      const res = await apiClient.download(`/api/documents/${doc.id}/export`);
      const blob = await res.blob();
      triggerDownload(blob, `${doc.title}.md`);
    },
  });
}

export function useExportAllDocuments() {
  return useMutation({
    mutationFn: async () => {
      const res = await apiClient.download('/api/documents/export-all');
      const blob = await res.blob();
      triggerDownload(blob, 'notes-export.zip');
    },
  });
}
