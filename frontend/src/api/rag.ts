import { http } from './http';
import type { AskQuestionPayload, RagAnswer } from '../types';

export async function askQuestion(payload: AskQuestionPayload) {
  const { data } = await http.post<RagAnswer>('/rag/ask', payload);
  return data;
}