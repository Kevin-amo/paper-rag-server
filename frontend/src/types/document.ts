import type { PageResponse } from './common';

export type DocumentStatus = 'PENDING' | 'PROCESSING' | 'INDEXED' | 'READY' | 'FAILED' | string;

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
  ownerUserId: string;
  title: string;
  origin: string;
  fileName: string;
  fileType: string;
  fileSize: number | null;
  status: DocumentStatus;
  chunkCount: number;
  publishYear: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface DocumentDetail {
  sourceId: string;
  ownerUserId: string;
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
  status: DocumentStatus;
  chunkCount: number;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
  deletedAt: string | null;
}

export type StructuredParseStatus = 'PENDING' | 'RULE_PARSED' | 'MODEL_COMPLETED' | 'COMPLETED' | 'FAILED' | string;

export interface PaperStructuredContent {
  title: string | null;
  abstract: string | null;
  introduction: string | null;
  literatureReview: string | null;
  methodology: string | null;
  experimentResults: string | null;
  discussion: string | null;
  conclusion: string | null;
  references: string | null;
  keywords: string[];
  researchObject: string | null;
  researchQuestion: string | null;
  innovationPoints: string[];
  methodPath: string | null;
  experimentDataSummary: string | null;
  mainConclusions: string[];
}

export interface StructuredFieldConfidence {
  source: string;
  confidence: number;
  missing: boolean;
  evidence: string | null;
}

export interface PaperStructuredParse {
  id: string;
  documentId: string;
  sourceId: string;
  status: StructuredParseStatus;
  ruleResult: PaperStructuredContent | Record<string, unknown> | null;
  modelResult: PaperStructuredContent | Record<string, unknown> | null;
  mergedResult: PaperStructuredContent | Record<string, unknown> | null;
  fieldConfidence: Record<string, StructuredFieldConfidence> | Record<string, unknown> | null;
  missingFields: string[];
  lowConfidenceFields: string[];
  rawModelOutput: string | null;
  errorMessage: string | null;
  parsedAt: string | null;
  updatedAt: string | null;
}

export interface PaperStructuredParseStatus {
  sourceId: string;
  status: StructuredParseStatus;
  missingFields: string[];
  lowConfidenceFields: string[];
  errorMessage: string | null;
  parsedAt: string | null;
  updatedAt: string | null;
}

export interface DocumentChunk {
  chunkId: string;
  ownerUserId: string;
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

export interface DocumentAsset {
  assetId: string;
  sourceId: string;
  ownerUserId: string;
  assetIndex: number;
  assetType: string;
  fileName: string | null;
  contentType: string | null;
  fileSize: number | null;
  contentHash: string | null;
  extractedText: string | null;
  textStart: number | null;
  textEnd: number | null;
  metadata: Record<string, unknown> | null;
  createdAt: string;
  updatedAt: string;
}

export interface ListDocumentsParams {
  keyword?: string;
  status?: DocumentStatus;
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

export interface DocumentUploadAcceptedResponse {
  jobId: string;
  sourceId: string;
  status: string;
  message: string;
}

export interface DocumentJobResponse {
  jobId: string;
  ownerUserId: string;
  sourceId: string;
  fileName: string;
  title: string;
  status: string;
  progress: number;
  errorMessage: string | null;
  retryCount: number;
  createdAt: string;
  updatedAt: string;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface BatchDocumentIngestionItemResponse {
  index: number;
  fileName: string;
  accepted: boolean;
  errorMessage: string | null;
  jobId: string | null;
  sourceId: string | null;
  status: string;
  message: string;
}

export interface BatchDocumentIngestionResponse {
  items: BatchDocumentIngestionItemResponse[];
  acceptedCount: number;
  failureCount: number;
}

// Re-export PageResponse for convenience
export type { PageResponse };
