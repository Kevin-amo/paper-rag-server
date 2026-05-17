import { ref, type Ref } from 'vue';
import { ElMessage } from 'element-plus';
import { askQuestion } from '../api/rag';
import { getErrorMessage } from '../api/http';
import type { ConversationMessage } from '../types';

export interface RagChatBindings {
  activeConversationId: Ref<string | null>;
  conversationMessages: Ref<ConversationMessage[]>;
  loadConversations: () => Promise<void>;
  loadMessages: (conversationId: string) => Promise<void>;
}

export function useRagChat(bindings: RagChatBindings) {
  const ragLoading = ref(false);

  async function ask(payload: { question: string; topK?: number }) {
    ragLoading.value = true;
    const tempMessage: ConversationMessage = {
      id: `pending-${Date.now()}`,
      conversationId: bindings.activeConversationId.value ?? 'pending',
      role: 'USER',
      messageOrder: bindings.conversationMessages.value.length + 1,
      content: payload.question,
      citations: [],
      createdAt: new Date().toISOString(),
    };
    bindings.conversationMessages.value = [...bindings.conversationMessages.value, tempMessage];

    try {
      const answer = await askQuestion({
        conversationId: bindings.activeConversationId.value ?? undefined,
        question: payload.question,
        topK: payload.topK,
      });
      const conversationId = answer.conversationId;
      bindings.activeConversationId.value = conversationId;
      bindings.conversationMessages.value = [
        ...bindings.conversationMessages.value.map((message) => (
          message.id === tempMessage.id
            ? { ...message, conversationId }
            : message
        )),
        {
          id: `answer-${Date.now()}`,
          conversationId,
          role: 'ASSISTANT',
          messageOrder: tempMessage.messageOrder + 1,
          content: answer.answer || '模型未返回回答内容',
          citations: answer.citations ?? [],
          createdAt: new Date().toISOString(),
        },
      ];
      await bindings.loadConversations();
    } catch (error) {
      const errorMessage = getErrorMessage(error);
      const failedAt = new Date().toISOString();
      bindings.conversationMessages.value = [
        ...bindings.conversationMessages.value,
        {
          id: `failed-${Date.now()}`,
          conversationId: tempMessage.conversationId,
          role: 'ASSISTANT',
          messageOrder: tempMessage.messageOrder + 1,
          content: `回答生成失败：${errorMessage}`,
          citations: [],
          createdAt: failedAt,
        },
      ];
      ElMessage.error(errorMessage);
    } finally {
      ragLoading.value = false;
    }
  }

  return {
    ragLoading,
    ask,
  };
}