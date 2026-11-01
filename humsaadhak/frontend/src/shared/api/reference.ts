import { useQuery } from '@tanstack/react-query';
import { api } from './client';
import type { DocumentType, Option } from './types';

/** Reference lists change rarely: cached for the whole session. */
const forever = { staleTime: Infinity, gcTime: Infinity } as const;

export const useSectors = () => useQuery({ queryKey: ['ref', 'sectors'], queryFn: () => api<Option[]>('/reference/sectors'), ...forever });
export const useStates = () => useQuery({ queryKey: ['ref', 'states'], queryFn: () => api<Option[]>('/reference/states'), ...forever });
export const useDocumentTypes = () =>
  useQuery({ queryKey: ['ref', 'document-types'], queryFn: () => api<DocumentType[]>('/reference/document-types'), ...forever });
