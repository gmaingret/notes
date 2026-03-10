import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../api/client';

export type SearchResult = {
  id: string;
  content: string;
  documentId: string;
  documentTitle: string;
};

export function useSearch(query: string) {
  return useQuery<SearchResult[]>({
    queryKey: ['search', query],
    queryFn: () => apiClient.get<SearchResult[]>(`/api/search?q=${encodeURIComponent(query)}`),
    enabled: query.length >= 2,
    staleTime: 10_000,
  });
}
