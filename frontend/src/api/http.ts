import axios from 'axios';

const rawPrefix = import.meta.env.VITE_API_PREFIX?.trim() ?? '';

export const apiPrefix = rawPrefix === '/' ? '' : rawPrefix.replace(/\/$/, '');

export const http = axios.create({
  baseURL: apiPrefix,
  timeout: 30000,
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