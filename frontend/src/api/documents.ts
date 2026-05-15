import { apiPrefix, http, uploadHttp } from './http';
import { getAccessToken } from '../composables/authState';
import type {
  BatchDocumentIngestionResponse,
  BatchUploadDocumentPayload,
  DocumentAsset,
  DocumentChunk,
  DocumentDetail,
  DocumentIngestionResult,
  DocumentSummary,
  ListChunksParams,
  ListDocumentsParams,
  PageResponse,
  UploadDocumentPayload,
} from '../types';

function compactParams(params: Record<string, unknown>) {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== ''),
  );
}

export async function uploadDocument(payload: UploadDocumentPayload) {
  const formData = new FormData();
  formData.append('file', payload.file);

  if (payload.sourceId) {
    formData.append('sourceId', payload.sourceId);
  }

  if (payload.title) {
    formData.append('title', payload.title);
  }

  const { data } = await uploadHttp.post<DocumentIngestionResult>('/documents', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });

  return data;
}

export async function uploadDocumentsBatch(payload: BatchUploadDocumentPayload) {
  const formData = new FormData();
  const items = payload.items.map((item) => ({
    fileName: item.file.name,
    sourceId: item.sourceId,
    title: item.title,
  }));

  payload.items.forEach((item) => {
    formData.append('files', item.file);
  });
  formData.append('items', JSON.stringify(items));

  const { data } = await uploadHttp.post<BatchDocumentIngestionResponse>('/documents/batch', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });

  return data;
}

export async function listDocuments(params: ListDocumentsParams) {
  const { data } = await http.get<PageResponse<DocumentSummary>>('/documents', {
    params: compactParams({
      keyword: params.keyword,
      status: params.status,
      page: params.page ?? 0,
      size: params.size ?? 10,
    }),
  });

  return data;
}

export async function getDocumentDetail(sourceId: string) {
  const { data } = await http.get<DocumentDetail>(`/documents/${sourceId}`);
  return data;
}

export async function getDocumentChunks(sourceId: string, params: ListChunksParams = {}) {
  const { data } = await http.get<PageResponse<DocumentChunk>>(`/documents/${sourceId}/chunks`, {
    params: compactParams({
      page: params.page ?? 0,
      size: params.size ?? 50,
    }),
  });

  return data;
}

export async function listDocumentAssets(sourceId: string, assetIds: string[]) {
  const { data } = await http.get<DocumentAsset[]>(`/documents/${sourceId}/assets`, {
    params: compactParams({
      assetIds: assetIds.length ? assetIds.join(',') : undefined,
    }),
  });

  return data;
}

export function getDocumentAssetContentUrl(sourceId: string, assetId: string) {
  const token = getAccessToken();
  const query = token ? `?access_token=${encodeURIComponent(token)}` : '';
  return `${apiPrefix}/documents/${encodeURIComponent(sourceId)}/assets/${encodeURIComponent(assetId)}/content${query}`;
}

export async function deleteDocument(sourceId: string) {
  await http.delete(`/documents/${sourceId}`);
}
