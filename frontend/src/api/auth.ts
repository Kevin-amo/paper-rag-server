import { http } from './http';
import type { AuthUser, LoginPayload, LoginResponse, RegisterEmailCodePayload, RegisterPayload } from '../types';

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

export async function logout() {
  await http.post('/auth/logout');
}