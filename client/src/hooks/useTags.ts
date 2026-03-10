import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../api/client';

export type TagCount = { chipType: 'tag' | 'mention' | 'date'; value: string; count: number };
export type TagBulletRow = { id: string; content: string; documentId: string; documentTitle: string };

export function useTagCounts() {
  return useQuery<TagCount[]>({
    queryKey: ['tags'],
    queryFn: () => apiClient.get<TagCount[]>('/api/tags'),
    staleTime: 0,
  });
}

export function useTagBullets(chipType: string, value: string, enabled: boolean) {
  return useQuery<TagBulletRow[]>({
    queryKey: ['tag-bullets', chipType, value],
    queryFn: () => apiClient.get<TagBulletRow[]>(`/api/tags/${chipType}/${value}/bullets`),
    enabled,
    staleTime: 0,
  });
}
