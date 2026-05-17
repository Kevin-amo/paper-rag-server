import { http } from './http';
import type {
  AdminUser,
  CreateAdminUserPayload,
  PageResponse,
  ResetPasswordPayload,
  UpdateAdminUserPayload,
  UserRole,
  UserStatus,
} from '../types';

export interface ListAdminUsersParams {
  page?: number;
  size?: number;
  keyword?: string;
  status?: UserStatus | '';
}

function compactParams(params: Record<string, unknown>) {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== ''),
  );
}

export async function listAdminUsers(params: ListAdminUsersParams = {}) {
  const { data } = await http.get<PageResponse<AdminUser>>('/admin/users', {
    params: compactParams({
      page: params.page ?? 0,
      size: params.size ?? 20,
      keyword: params.keyword,
      status: params.status,
    }),
  });
  return data;
}

export async function createAdminUser(payload: CreateAdminUserPayload) {
  const { data } = await http.post<AdminUser>('/admin/users', payload);
  return data;
}

export async function updateAdminUser(id: string, payload: UpdateAdminUserPayload) {
  const { data } = await http.patch<AdminUser>(`/admin/users/${id}`, payload);
  return data;
}

export async function updateAdminUserRoles(id: string, roles: UserRole[]) {
  const { data } = await http.patch<AdminUser>(`/admin/users/${id}/roles`, { roles });
  return data;
}

export async function updateAdminUserStatus(id: string, status: UserStatus) {
  const { data } = await http.patch<AdminUser>(`/admin/users/${id}/status`, { status });
  return data;
}

export async function resetAdminUserPassword(id: string, payload: ResetPasswordPayload) {
  await http.post(`/admin/users/${id}/reset-password`, payload);
}

export async function deleteAdminUser(id: string) {
  await http.delete(`/admin/users/${id}`);
}