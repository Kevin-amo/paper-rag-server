import { apiPrefix, http, longRunningHttp, uploadHttp } from './http';
import { compactParams } from '../utils/params';
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
  PaperStructuredParse,
  PaperStructuredParseStatus,
  UploadDocumentPayload,
} from '../types';

let tokenProvider: (() => string) | null = null;

export function setDocumentsTokenProvider(provider: () => string) {
  tokenProvider = provider;
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

export async function getPaperStructuredParse(sourceId: string) {
  const { data } = await http.get<PaperStructuredParse>(`/documents/${sourceId}/structured-parse`);
  return data;
}

export async function getPaperStructuredParseStatus(sourceId: string) {
  const { data } = await http.get<PaperStructuredParseStatus>(`/documents/${sourceId}/structured-parse/status`);
  return data;
}

export async function regeneratePaperStructuredParse(sourceId: string) {
  const { data } = await longRunningHttp.post<PaperStructuredParse>(`/documents/${sourceId}/structured-parse/regenerate`);
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
  const token = tokenProvider?.();
  const query = token ? `?access_token=${encodeURIComponent(token)}` : '';
  return `${apiPrefix}/documents/${encodeURIComponent(sourceId)}/assets/${encodeURIComponent(assetId)}/content${query}`;
}

export async function deleteDocument(sourceId: string) {
  await http.delete(`/documents/${sourceId}`);
}

export async function deleteAllDocuments() {
  await http.delete('/documents');
}
