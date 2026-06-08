import { http } from './http';
import type {
  AdminReviewTaskDetail,
  AdminReviewTaskSummary,
  AssignReviewersPayload,
  PageResponse,
  ReviewAssignment,
  ReviewConsensus,
  ReviewerLoad,
  UpdateReviewConsensusPayload,
} from '../types';

function compactParams(params: Record<string, unknown>) {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== ''),
  );
}

export async function listAdminReviewTasks(params: {
  keyword?: string;
  status?: string;
  page?: number;
  size?: number;
} = {}) {
  const { data } = await http.get<PageResponse<AdminReviewTaskSummary>>('/admin/reviews/tasks', {
    params: compactParams({
      keyword: params.keyword,
      status: params.status,
      page: params.page ?? 0,
      size: params.size ?? 20,
    }),
  });
  return data;
}

export async function getAdminReviewTask(taskId: string) {
  const { data } = await http.get<AdminReviewTaskDetail>(`/admin/reviews/tasks/${taskId}`);
  return data;
}

export async function assignReviewers(taskId: string, payload: AssignReviewersPayload) {
  const { data } = await http.post<ReviewAssignment[]>(`/admin/reviews/tasks/${taskId}/assignments`, payload);
  return data;
}

export async function listReviewerLoads() {
  const { data } = await http.get<ReviewerLoad[]>('/admin/reviews/reviewer-loads');
  return data;
}

export async function recalculateConsensus(taskId: string) {
  const { data } = await http.post<ReviewConsensus>(`/admin/reviews/tasks/${taskId}/consensus/recalculate`);
  return data;
}

export async function updateConsensus(taskId: string, payload: UpdateReviewConsensusPayload) {
  const { data } = await http.patch<ReviewConsensus>(`/admin/reviews/tasks/${taskId}/consensus`, payload);
  return data;
}

export async function confirmConsensus(taskId: string) {
  const { data } = await http.post<ReviewConsensus>(`/admin/reviews/tasks/${taskId}/consensus/confirm`);
  return data;
}
