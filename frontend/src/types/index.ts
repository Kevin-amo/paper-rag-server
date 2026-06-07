export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

export type UserRole = 'USER' | 'ADMIN' | 'REVIEWER';
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

export interface ChangePasswordPayload {
  currentPassword: string;
  newPassword: string;
}

export interface ChangeDisplayNamePayload {
  displayName: string;
}

export interface ChangeEmailCodePayload {
  email: string;
}

export interface ChangeEmailPayload {
  email: string;
  emailCode: string;
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

export type ReviewTaskStatus = 'PENDING' | 'REVIEWING' | 'COMPLETED' | 'NEEDS_REVIEW' | string;
export type ReviewReportStatus = 'AI_GENERATED' | 'ADJUSTED' | 'CONFIRMED' | 'COMPLETED' | string;

export interface UploadReviewPaperPayload {
  file: File;
  sourceId?: string;
  title?: string;
}

export interface ReviewScoringRule {
  level: string;
  range: [number, number];
  description: string;
}

export interface ReviewCriterion {
  id: string;
  code: string;
  name: string;
  description: string | null;
  maxScore: number;
  weight: number;
  version: number;
  category: string | null;
  evidenceRequired: boolean;
  scoringRules: Array<Record<string, unknown>> | Record<string, unknown> | null;
  enabled: boolean;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface ReviewScoreItem {
  code: string;
  name: string;
  score: number;
  maxScore: number;
  reason: string;
  confidence?: number;
}

export interface ReviewRiskItem {
  type: string;
  level: 'LOW' | 'MEDIUM' | 'HIGH' | string;
  evidence: string;
  suggestion: string;
}

export interface ReviewRiskRecord {
  id: string;
  reportId: string;
  taskId: string;
  riskType: string;
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL' | string;
  evidence: string | null;
  evidenceLocation: Record<string, unknown>;
  suggestion: string | null;
  detector: string | null;
  confidence: number | null;
  status: 'OPEN' | 'CONFIRMED' | 'IGNORED' | 'RESOLVED' | string;
  reviewerNote: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface UpdateReviewRiskPayload {
  status: 'OPEN' | 'CONFIRMED' | 'IGNORED' | 'RESOLVED';
  reviewerNote?: string | null;
}

export interface ReviewComments {
  summary?: string;
  strengths?: string[];
  weaknesses?: string[];
  suggestions?: string[];
  finalAdvice?: string;
  [key: string]: unknown;
}

export interface ReviewReport {
  id: string;
  taskId: string;
  documentId: string;
  reviewerUserId: string | null;
  paperSections: Record<string, unknown>;
  scores: ReviewScoreItem[] | unknown;
  comments: ReviewComments | Record<string, unknown>;
  risks: ReviewRiskItem[] | unknown;
  criterionVersion: number | null;
  modelVersion: string | null;
  promptVersion: string | null;
  confidence: number | null;
  manualDelta: Record<string, unknown> | null;
  totalScore: number | null;
  finalRecommendation: string | null;
  status: ReviewReportStatus;
  generatedAt: string | null;
  adjustedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ReviewTask {
  id: string;
  documentId: string;
  submitterUserId: string;
  reviewerUserId: string | null;
  sourceId: string;
  title: string;
  status: ReviewTaskStatus;
  assignedAt: string | null;
  dueAt: string | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
  document: DocumentDetail | null;
  report: ReviewReport | null;
}

export interface ListReviewTasksParams {
  keyword?: string;
  status?: ReviewTaskStatus | '';
  page?: number;
  size?: number;
}

export interface UpdateReviewReportPayload {
  paperSections?: Record<string, unknown>;
  scores?: ReviewScoreItem[] | unknown;
  comments?: ReviewComments | Record<string, unknown>;
  risks?: ReviewRiskItem[] | unknown;
  totalScore?: number | null;
  finalRecommendation?: string | null;
  status?: ReviewReportStatus;
}
