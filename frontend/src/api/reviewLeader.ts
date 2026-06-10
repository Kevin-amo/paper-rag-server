import { http } from './http';
import type {
  AdminReviewTaskSummary,
  LeaderAssignReviewersPayload,
  ReviewAssignment,
  ReviewConsensus,
  ReviewGroup,
  ReviewGroupMember,
  ReviewReport,
  UpdateReviewConsensusPayload,
} from '../types';

export async function listLeaderGroups() {
  const { data } = await http.get<ReviewGroup[]>('/review-leader/groups');
  return data;
}

export async function listLeaderGroupMembers(groupId: string) {
  const { data } = await http.get<ReviewGroupMember[]>(`/review-leader/groups/${groupId}/members`);
  return data;
}

export async function listLeaderGroupTasks(groupId: string) {
  const { data } = await http.get<AdminReviewTaskSummary[]>(`/review-leader/groups/${groupId}/tasks`);
  return data;
}

export async function listLeaderUnassignedTasks(groupId: string) {
  const { data } = await http.get<AdminReviewTaskSummary[]>(`/review-leader/groups/${groupId}/tasks/unassigned`);
  return data;
}

export async function assignLeaderTask(groupId: string, taskId: string, payload: LeaderAssignReviewersPayload) {
  const { data } = await http.post<ReviewAssignment[]>(`/review-leader/groups/${groupId}/tasks/${taskId}/assignments`, payload);
  return data;
}

export async function listLeaderTaskReports(groupId: string, taskId: string) {
  const { data } = await http.get<ReviewReport[]>(`/review-leader/groups/${groupId}/tasks/${taskId}/reports`);
  return data;
}

export async function getLeaderTaskConsensus(groupId: string, taskId: string) {
  const { data } = await http.get<ReviewConsensus | null>(`/review-leader/groups/${groupId}/tasks/${taskId}/consensus`);
  return data;
}

export async function recalculateLeaderTaskConsensus(groupId: string, taskId: string) {
  const { data } = await http.post<ReviewConsensus>(`/review-leader/groups/${groupId}/tasks/${taskId}/consensus/recalculate`);
  return data;
}

export async function updateLeaderTaskConsensus(groupId: string, taskId: string, payload: UpdateReviewConsensusPayload) {
  const { data } = await http.patch<ReviewConsensus>(`/review-leader/groups/${groupId}/tasks/${taskId}/consensus`, payload);
  return data;
}

export async function confirmLeaderTaskConsensus(groupId: string, taskId: string) {
  const { data } = await http.post<ReviewConsensus>(`/review-leader/groups/${groupId}/tasks/${taskId}/consensus/confirm`);
  return data;
}