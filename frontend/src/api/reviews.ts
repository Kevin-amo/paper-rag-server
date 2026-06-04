import { http, uploadHttp } from './http';
import type {
  DocumentUploadAcceptedResponse,
  ListReviewTasksParams,
  PageResponse,
  ReviewCriterion,
  ReviewReport,
  ReviewTask,
  UpdateReviewReportPayload,
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