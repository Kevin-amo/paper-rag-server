import { http } from './http';
import type { Conversation, ConversationMessage, CreateConversationPayload } from '../types';

export async function listConversations() {
  const { data } = await http.get<Conversation[]>('/conversations');
  return data;
}

export async function createConversation(payload: CreateConversationPayload = {}) {
  const { data } = await http.post<Conversation>('/conversations', payload);
  return data;
}

export async function listConversationMessages(conversationId: string) {
  const { data } = await http.get<ConversationMessage[]>(`/conversations/${conversationId}/messages`);
  return data;
}

export async function deleteConversation(conversationId: string) {
  await http.delete(`/conversations/${conversationId}`);
}