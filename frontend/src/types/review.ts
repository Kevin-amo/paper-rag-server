import type { UserRole } from './auth';
import type { DocumentDetail, PaperStructuredParse, PaperStructuredParseStatus } from './document';
import type { PageResponse } from './common';

export interface AnswerCitation {
  sourceId: string;
  chunkId: string;
  chunkIndex: number;
  title: string;
  excerpt: string;
  rankScore: number;
}

export type ReviewTaskStatus = 'PENDING' | 'PENDING_ASSIGNMENT' | 'ASSIGNED' | 'REVIEWING' | 'IN_REVIEW' | 'SUBMITTED' | 'COMPLETED' | 'CONSENSUS_CONFIRMED' | string;
export type ReviewAssignmentRole = 'LEAD' | 'REVIEWER' | 'ARBITER';
export type ReviewAssignmentStatus = 'ASSIGNED' | 'REVIEWING' | 'SUBMITTED' | 'RETURNED' | 'CANCELLED';
export type ReviewConsensusStatus = 'DRAFT' | 'IN_DISCUSSION' | 'CONFIRMED' | 'ARCHIVED' | string;
export type ReviewBatchStatus = 'DRAFT' | 'ACTIVE' | 'CLOSED' | 'ARCHIVED' | string;
export type ReviewGroupStatus = 'ACTIVE' | 'DISABLED' | string;
export type ReviewGroupMemberRole = 'LEADER' | 'REVIEWER' | string;
export type ReviewReportStatus = 'AI_GENERATED' | 'ADJUSTED' | 'CONFIRMED' | 'COMPLETED' | string;

export interface ReviewBatch {
  id: string;
  name: string;
  description: string | null;
  status: ReviewBatchStatus;
  startsAt: string | null;
  endsAt: string | null;
  createdByUserId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ReviewBatchPayload {
  name: string;
  description?: string | null;
  status?: ReviewBatchStatus;
  startsAt?: string | null;
  endsAt?: string | null;
}

export interface ReviewGroup {
  id: string;
  batchId: string;
  name: string;
  leaderUserId: string;
  leaderUsername: string | null;
  leaderDisplayName: string | null;
  status: ReviewGroupStatus;
  memberCount: number;
  taskCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface ReviewGroupPayload {
  batchId: string;
  name: string;
  leaderUserId: string;
  status?: ReviewGroupStatus;
}

export interface ReviewGroupMember {
  id: string;
  groupId: string;
  userId: string;
  username: string | null;
  displayName: string | null;
  memberRole: ReviewGroupMemberRole;
  status: string;
  joinedAt: string | null;
  removedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ReviewGroupMemberUpdatePayload {
  leaderUserId: string;
  memberUserIds: string[];
}

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

export interface ReviewAssignment {
  id: string;
  taskId: string;
  reviewerUserId: string;
  reviewerUsername: string | null;
  reviewerDisplayName: string | null;
  role: ReviewAssignmentRole;
  status: ReviewAssignmentStatus;
  assignedAt: string | null;
  dueAt: string | null;
  submittedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ReviewConsensus {
  id: string;
  taskId: string;
  leadReviewerUserId: string | null;
  leadReviewerUsername: string | null;
  leadReviewerDisplayName: string | null;
  scoreSummary: Record<string, unknown> | null;
  commentSummary: Record<string, unknown> | null;
  disagreementItems: Array<Record<string, unknown>> | null;
  finalScore: number | null;
  finalRecommendation: string | null;
  status: ReviewConsensusStatus;
  confirmedByUserId: string | null;
  confirmedByUsername: string | null;
  confirmedByDisplayName: string | null;
  confirmedAt: string | null;
  submittedReports: Array<Record<string, unknown>> | null;
  createdAt: string;
  updatedAt: string;
}

export interface AssignReviewersPayload {
  reviewerUserIds: string[];
  leadReviewerUserId: string;
  dueAt?: string | null;
}

export interface LeaderAssignReviewersPayload {
  reviewerUserIds: string[];
  dueAt?: string | null;
}

export interface UpdateReviewConsensusPayload {
  finalScore?: number | null;
  finalRecommendation?: string | null;
}

export interface ReviewerLoad {
  reviewerUserId: string;
  username: string | null;
  displayName: string | null;
  assignedCount: number;
  reviewingCount: number;
  submittedCount: number;
}

export interface AdminReviewTaskSummary {
  id: string;
  documentId: string;
  submitterUserId: string;
  sourceId: string;
  title: string;
  status: ReviewTaskStatus;
  assignmentCount: number;
  submittedCount: number;
  leadReviewerUserId: string | null;
  leadReviewerUsername: string | null;
  leadReviewerDisplayName: string | null;
  dueAt: string | null;
  consensusStatus: ReviewConsensusStatus;
  createdAt: string;
  updatedAt: string;
}

export interface AdminReviewTaskDetail {
  task: ReviewTask;
  assignments: ReviewAssignment[];
  submittedReports: ReviewReport[];
  consensus: ReviewConsensus | null;
}

export interface ReviewReport {
  id: string;
  taskId: string;
  documentId: string;
  reviewerUserId: string | null;
  reviewerUsername: string | null;
  reviewerDisplayName: string | null;
  assignmentId: string | null;
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
  currentAssignment: ReviewAssignment | null;
  assignments: ReviewAssignment[];
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
  status?: ReviewAssignmentStatus | '';
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

export type { PageResponse };
