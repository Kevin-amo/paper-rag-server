import { http, uploadHttp } from './http';
import type {
  AuthUser,
  ChangeDisplayNamePayload,
  ChangeEmailCodePayload,
  ChangeEmailPayload,
  ChangePasswordPayload,
  LoginPayload,
  LoginResponse,
  RegisterEmailCodePayload,
  RegisterPayload,
} from '../types';

export async function login(payload: LoginPayload) {
  const { data } = await http.post<LoginResponse>('/auth/login', payload);
  return data;
}

export async function requestRegisterEmailCode(payload: RegisterEmailCodePayload) {
  await http.post('/auth/register/email-code', payload);
}

export async function register(payload: RegisterPayload) {
  const { data } = await http.post<LoginResponse>('/auth/register', payload);
  return data;
}

export async function getCurrentUser() {
  const { data } = await http.get<AuthUser>('/auth/me');
  return data;
}

export async function uploadAvatar(file: File) {
  const formData = new FormData();
  formData.append('file', file);

  const { data } = await uploadHttp.post<AuthUser>('/auth/me/avatar', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
  return data;
}

export async function changePassword(payload: ChangePasswordPayload) {
  const { data } = await http.post<AuthUser>('/auth/me/password', payload);
  return data;
}

export async function changeDisplayName(payload: ChangeDisplayNamePayload) {
  const { data } = await http.post<AuthUser>('/auth/me/display-name', payload);
  return data;
}

export async function requestChangeEmailCode(payload: ChangeEmailCodePayload) {
  await http.post('/auth/me/email-code', payload);
}

export async function changeEmail(payload: ChangeEmailPayload) {
  const { data } = await http.post<AuthUser>('/auth/me/email', payload);
  return data;
}

export async function logout() {
  await http.post('/auth/logout');
}