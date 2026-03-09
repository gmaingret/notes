import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';

export type Attachment = {
  id: string;
  bulletId: string;
  userId: string;
  filename: string;
  mimeType: string;
  size: number;
  storagePath: string;
  createdAt: string;
};

function attachmentKey(bulletId: string) {
  return ['attachments', bulletId];
}

export function useBulletAttachments(bulletId: string) {
  return useQuery<Attachment[]>({
    queryKey: attachmentKey(bulletId),
    queryFn: () => apiClient.get<Attachment[]>(`/api/attachments/bullets/${bulletId}`),
    enabled: !!bulletId,
  });
}

export function useUploadAttachment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ bulletId, file }: { bulletId: string; file: File }) => {
      const formData = new FormData();
      formData.append('file', file);
      return apiClient.upload<Attachment>(`/api/attachments/bullets/${bulletId}`, formData);
    },
    onSettled: (_data, _err, vars) =>
      qc.invalidateQueries({ queryKey: attachmentKey(vars.bulletId) }),
  });
}

export function useDeleteAttachment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ attachmentId }: { attachmentId: string; bulletId: string }) =>
      apiClient.delete<void>(`/api/attachments/${attachmentId}`),
    onSettled: (_data, _err, vars) =>
      qc.invalidateQueries({ queryKey: attachmentKey(vars.bulletId) }),
  });
}
