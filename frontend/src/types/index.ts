export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

export interface DocumentSource {
  sourceId: string;
  title: string;
  origin: string;
  metadata: Record<string, unknown> | null;
}

export interface DocumentIngestionResult {
  source: DocumentSource;
  chunkCount: number;
}

export interface DocumentSummary {
  sourceId: string;
  title: string;
  origin: string;
  fileName: string;
  fileType: string;
  fileSize: number | null;
  status: string;
  chunkCount: number;
  publishYear: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface DocumentDetail {
  sourceId: string;
  title: string;
  origin: string;
  fileName: string;
  fileType: string;
  fileSize: number | null;
  authors: unknown;
  abstractText: string | null;
  doi: string | null;
  journal: string | null;
  publishYear: number | null;
  keywords: unknown;
  contentText: string | null;
  metadata: Record<string, unknown> | null;
  status: string;
  chunkCount: number;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
  deletedAt: string | null;
}

export interface DocumentChunk {
  chunkId: string;
  chunkIndex: number;
  content: string;
  contentHash: string;
  chunkStart: number | null;
  chunkEnd: number | null;
  pageNumber: number | null;
  sectionTitle: string | null;
  metadata: Record<string, unknown> | null;
  vectorStoreId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ListDocumentsParams {
  keyword?: string;
  status?: string;
  page?: number;
  size?: number;
}

export interface ListChunksParams {
  page?: number;
  size?: number;
}

export interface UploadDocumentPayload {
  file: File;
  sourceId?: string;
  title?: string;
}

export interface BatchUploadDocumentItemPayload {
  file: File;
  sourceId?: string;
  title?: string;
}

export interface BatchUploadDocumentPayload {
  items: BatchUploadDocumentItemPayload[];
}

export interface BatchDocumentIngestionItemResponse {
  index: number;
  fileName: string;
  success: boolean;
  errorMessage: string | null;
  source: DocumentSource | null;
  chunkCount: number | null;
}

export interface BatchDocumentIngestionResponse {
  items: BatchDocumentIngestionItemResponse[];
  successCount: number;
  failureCount: number;
}

export interface AskQuestionPayload {
  question: string;
  topK?: number;
}

export interface AnswerCitation {
  sourceId: string;
  chunkId: string;
  chunkIndex: number;
  title: string;
  excerpt: string;
  score: number;
}

export interface RagAnswer {
  answer: string;
  citations: AnswerCitation[];
}