import { apiPrefix } from './http';
import { clearAuthSession, getAccessToken } from '../composables/authState';
import type { AgentAskPayload, AgentStreamEvent } from '../types';

export async function askAgentStream(
  payload: AgentAskPayload,
  onEvent: (event: AgentStreamEvent) => void,
) {
  const token = getAccessToken();
  const response = await fetch(`${apiPrefix}/agent/ask/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(payload),
  });

  if (response.status === 401) {
    clearAuthStateAndRedirect();
    throw new Error('登录已过期，请重新登录');
  }

  if (!response.ok) {
    throw new Error(await readResponseError(response));
  }

  if (!response.body) {
    throw new Error('浏览器不支持流式响应');
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';

  while (true) {
    const { value, done } = await reader.read();
    if (done) {
      break;
    }
    buffer += decoder.decode(value, { stream: true });
    buffer = emitCompleteSseEvents(buffer, onEvent);
  }

  buffer += decoder.decode();
  emitCompleteSseEvents(`${buffer}\n\n`, onEvent);
}

function emitCompleteSseEvents(buffer: string, onEvent: (event: AgentStreamEvent) => void) {
  const normalized = buffer.replace(/\r\n/g, '\n');
  const chunks = normalized.split('\n\n');
  const remainder = chunks.pop() ?? '';

  for (const chunk of chunks) {
    const event = parseSseEvent(chunk);
    if (event) {
      onEvent(event);
    }
  }

  return remainder;
}

function parseSseEvent(chunk: string): AgentStreamEvent | null {
  const data = chunk
    .split('\n')
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart())
    .join('\n');

  if (!data) {
    return null;
  }

  return JSON.parse(data) as AgentStreamEvent;
}

async function readResponseError(response: Response) {
  const text = await response.text();
  if (!text.trim()) {
    return '请求失败，请稍后重试';
  }

  try {
    const payload = JSON.parse(text) as { message?: string };
    return payload.message || text;
  } catch {
    return text;
  }
}

function clearAuthStateAndRedirect() {
  clearAuthSession();
  if (window.location.pathname !== '/login') {
    window.location.assign('/login');
  }
}