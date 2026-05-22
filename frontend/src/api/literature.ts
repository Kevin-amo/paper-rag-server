import { literatureHttp } from './http';
import type { LiteratureSearchPayload, LiteratureSearchResponse } from '../types';

export async function searchLiterature(payload: LiteratureSearchPayload) {
  const { data } = await literatureHttp.post<LiteratureSearchResponse>('/literature/search', payload);
  return data;
}