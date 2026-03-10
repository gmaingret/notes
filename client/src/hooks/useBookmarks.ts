import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';

export type BookmarkRow = {
  id: string;
  content: string;
  documentId: string;
  documentTitle: string;
};

export function useBookmarks() {
  return useQuery<BookmarkRow[]>({
    queryKey: ['bookmarks'],
    queryFn: () => apiClient.get<BookmarkRow[]>('/api/bookmarks'),
  });
}

export function useAddBookmark() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (bulletId: string) => apiClient.post('/api/bookmarks', { bulletId }),
    onSettled: () => qc.invalidateQueries({ queryKey: ['bookmarks'] }),
  });
}

export function useRemoveBookmark() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (bulletId: string) => apiClient.delete(`/api/bookmarks/${bulletId}`),
    onSettled: () => qc.invalidateQueries({ queryKey: ['bookmarks'] }),
  });
}
