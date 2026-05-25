export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

export type UserRole = 'USER' | 'ADMIN';
export type UserStatus = 'ACTIVE' | 'DISABLED';
export type DocumentStatus = 'PENDING' | 'PROCESSING' | 'INDEXED' | 'READY' | 'FAILED' | string;
export type MessageRole = 'USER' | 'ASSISTANT';

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

export interface AgentAskPayload {
  conversationId?: string;
  question: string;
  topK?: number;
}

export interface LiteratureSearchResult {
  title: string | null;
  authors: string[];
  abstractText: string | null;
  year: number | null;
  publishedDate: string | null;
  updatedDate: string | null;
  categories: string[];
  primaryCategory: string | null;
  doi: string | null;
  url: string | null;
  pdfUrl: string | null;
  source: string;
  externalId: string | null;
}

export interface LiteratureSearchMessageMetadata {
  type: 'LITERATURE_SEARCH_RESULT';
  query: string;
  params: {
    limit: number | null;
    dateFrom: string | null;
    sortBy: 'relevance' | 'date' | null;
    categories: string[];
  };
  items: LiteratureSearchResult[];
}

export interface AgentStepTrace {
  index: number;
  thoughtSummary: string;
  action: string;
  actionInput: Record<string, unknown>;
  observationSummary: string;
}

export interface AgentResultMetadata {
  type: 'AGENT_RESULT';
  agent: string;
  steps: AgentStepTrace[];
  literature?: LiteratureSearchMessageMetadata;
  localPaperChunks?: Array<Record<string, unknown>>;
  stopReason?: string;
}

export type ConversationMessageMetadata = Record<string, unknown> | LiteratureSearchMessageMetadata | AgentResultMetadata | null;

export interface AnswerCitation {
  sourceId: string;
  chunkId: string;
  chunkIndex: number;
  title: string;
  excerpt: string;
  rankScore: number;
}

export interface AgentStreamEvent {
  type: 'start' | 'step' | 'thought' | 'tool_call' | 'tool_result' | 'delta' | 'done' | 'error';
  conversationId: string | null;
  step: number | null;
  thought: string | null;
  toolName: string | null;
  toolInput: Record<string, unknown>;
  observation: string | null;
  delta: string | null;
  answer: string | null;
  citations: AnswerCitation[];
  metadata: AgentResultMetadata | Record<string, unknown>;
  message: string | null;
}

export interface Conversation {
  id: string;
  ownerUserId: string;
  title: string;
  createdAt: string;
  updatedAt: string;
}

export interface ConversationMessage {
  id: string;
  conversationId: string;
  role: MessageRole;
  messageOrder: number;
  content: string;
  citations: AnswerCitation[];
  metadata: ConversationMessageMetadata;
  createdAt: string;
  streaming?: boolean;
}

export interface CreateConversationPayload {
  title?: string;
}

export interface UpdateConversationPayload {
  title: string;
}

export interface AuthUser {
  id: string;
  username: string;
  displayName: string | null;
  email: string | null;
  avatarUrl: string | null;
  roles: UserRole[];
}

export interface LoginPayload {
  username: string;
  password: string;
}

export interface RegisterEmailCodePayload {
  email: string;
}

export interface RegisterPayload {
  username: string;
  password: string;
  email: string;
  emailCode: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: AuthUser;
}

export interface AdminUser {
  id: string;
  username: string;
  displayName: string | null;
  email: string | null;
  avatarUrl: string | null;
  status: UserStatus;
  roles: UserRole[];
  lastLoginAt: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface CreateAdminUserPayload {
  username: string;
  password: string;
  displayName?: string;
  email?: string;
  roles: UserRole[];
}

export interface UpdateAdminUserPayload {
  displayName?: string;
  email?: string;
}

export interface ResetPasswordPayload {
  password: string;
}