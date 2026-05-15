import axios from 'axios';
import { clearAuthSession, getAccessToken } from '../composables/authState';

const rawPrefix = import.meta.env.VITE_API_PREFIX?.trim() ?? '';

export const apiPrefix = rawPrefix === '/' ? '' : rawPrefix.replace(/\/$/, '');

export const http = axios.create({
  baseURL: apiPrefix,
  timeout: 30000,
});

export const uploadHttp = axios.create({
  baseURL: apiPrefix,
  timeout: 300000,
});

[http, uploadHttp].forEach((client) => {
  client.interceptors.request.use((config) => {
    const token = getAccessToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  });

  client.interceptors.response.use(
    (response) => response,
    (error) => {
      if (axios.isAxiosError(error) && error.response?.status === 401) {
        clearAuthSession();
        if (window.location.pathname !== '/login') {
          window.location.assign('/login');
        }
      }
      return Promise.reject(error);
    },
  );
});

export function getErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data;

    if (typeof data === 'string' && data.trim()) {
      return data;
    }

    if (data && typeof data === 'object' && 'message' in data && typeof data.message === 'string') {
      return data.message;
    }

    if (error.message) {
      return error.message;
    }
  }

  if (error instanceof Error) {
    return error.message;
  }

  return '请求失败，请稍后重试';
}