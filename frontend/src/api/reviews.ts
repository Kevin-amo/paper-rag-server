import { http, uploadHttp } from './http';
import type {
  DocumentUploadAcceptedResponse,
  DocumentJobResponse,
  ListReviewTasksParams,
  PageResponse,
  PaperStructuredParse,
  ReviewCriterion,
  ReviewAssignment,
  ReviewConsensus,
  ReviewReport,
  ReviewRiskRecord,
  ReviewTask,
  UpdateReviewConsensusPayload,
  UpdateReviewReportPayload,
  UpdateReviewRiskPayload,
  UploadReviewPaperPayload,
} from '../types';

function compactParams(params: Record<string, unknown>) {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== ''),
  );
}

export async function uploadReviewPaper(payload: UploadReviewPaperPayload) {
  const formData = new FormData();
  formData.append('file', payload.file);

  if (payload.sourceId) {
    formData.append('sourceId', payload.sourceId);
  }

  if (payload.title) {
    formData.append('title', payload.title);
  }

  const { data } = await uploadHttp.post<DocumentUploadAcceptedResponse>('/reviews/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });

  return data;
}

export async function getReviewUploadJob(jobId: string) {
  const { data } = await http.get<DocumentJobResponse>(`/documents/jobs/${jobId}`);
  return data;
}

export async function listReviewTasks(params: ListReviewTasksParams = {}) {
  const { data } = await http.get<PageResponse<ReviewTask>>('/reviews/tasks', {
    params: compactParams({
      keyword: params.keyword,
      status: params.status,
      page: params.page ?? 0,
      size: params.size ?? 20,
    }),
  });
  return data;
}

export async function getReviewTask(taskId: string) {
  const { data } = await http.get<ReviewTask>(`/reviews/tasks/${taskId}`);
  return data;
}

export async function generateReviewReport(taskId: string) {
  const { data } = await http.post<ReviewReport>(`/reviews/tasks/${taskId}/ai-review`);
  return data;
}

export async function getReviewTaskStructuredParse(taskId: string) {
  const { data } = await http.get<PaperStructuredParse>(`/reviews/tasks/${taskId}/structured-parse`);
  return data;
}

export async function regenerateReviewTaskStructuredParse(taskId: string) {
  const { data } = await http.post<PaperStructuredParse>(`/reviews/tasks/${taskId}/structured-parse/regenerate`);
  return data;
}

export async function updateReviewReport(reportId: string, payload: UpdateReviewReportPayload) {
  const { data } = await http.patch<ReviewReport>(`/reviews/reports/${reportId}`, payload);
  return data;
}

export async function listReviewCriteria(includeDisabled = false) {
  const { data } = await http.get<ReviewCriterion[]>('/reviews/criteria', {
    params: compactParams({ includeDisabled }),
  });
  return data;
}

export async function listReviewRisks(reportId: string) {
  const { data } = await http.get<ReviewRiskRecord[]>(`/reviews/reports/${reportId}/risks`);
  return data;
}

export async function updateReviewRisk(riskId: string, payload: UpdateReviewRiskPayload) {
  const { data } = await http.put<ReviewRiskRecord>(`/reviews/risks/${riskId}`, payload);
  return data;
}

export async function confirmReviewRisk(riskId: string) {
  const { data } = await http.post<ReviewRiskRecord>(`/reviews/risks/${riskId}/confirm`);
  return data;
}

export async function ignoreReviewRisk(riskId: string) {
  const { data } = await http.post<ReviewRiskRecord>(`/reviews/risks/${riskId}/ignore`);
  return data;
}

export async function resolveReviewRisk(riskId: string) {
  const { data } = await http.post<ReviewRiskRecord>(`/reviews/risks/${riskId}/resolve`);
  return data;
}

export async function submitReviewAssignment(assignmentId: string) {
  const { data } = await http.post<ReviewAssignment>(`/reviews/assignments/${assignmentId}/submit`);
  return data;
}

export async function getReviewConsensus(taskId: string) {
  const { data } = await http.get<ReviewConsensus>(`/reviews/tasks/${taskId}/consensus`);
  return data;
}

export async function updateReviewConsensus(taskId: string, payload: UpdateReviewConsensusPayload) {
  const { data } = await http.patch<ReviewConsensus>(`/reviews/tasks/${taskId}/consensus`, payload);
  return data;
}

export async function confirmReviewConsensus(taskId: string) {
  const { data } = await http.post<ReviewConsensus>(`/reviews/tasks/${taskId}/consensus/confirm`);
  return data;
}
