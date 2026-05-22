import { ref, type Ref } from 'vue';
import { ElMessage } from 'element-plus';
import { searchLiterature } from '../api/literature';
import { getErrorMessage } from '../api/http';
import type {
  ConversationMessage,
  LiteratureSearchMessageMetadata,
  LiteratureSearchResponse,
} from '../types';

export interface LiteratureSearchBindings {
  activeConversationId: Ref<string | null>;
  conversationMessages: Ref<ConversationMessage[]>;
  loadConversations: () => Promise<void>;
  loadMessages: (conversationId: string) => Promise<void>;
}

function createTemporaryUserMessage(question: string, conversationId: string, order: number): ConversationMessage {
  return {
    id: `literature-user-${Date.now()}-${Math.random().toString(36).slice(2)}`,
    conversationId,
    role: 'USER',
    messageOrder: order,
    content: question,
    citations: [],
    metadata: null,
    createdAt: new Date().toISOString(),
  };
}

function createTemporaryAssistantMessage(conversationId: string, order: number): ConversationMessage {
  return {
    id: `literature-assistant-${Date.now()}-${Math.random().toString(36).slice(2)}`,
    conversationId,
    role: 'ASSISTANT',
    messageOrder: order,
    content: '正在检索文献…',
    citations: [],
    metadata: null,
    createdAt: new Date().toISOString(),
    streaming: true,
  };
}

function buildAssistantMetadata(question: string, response: LiteratureSearchResponse): LiteratureSearchMessageMetadata {
  return {
    type: 'LITERATURE_SEARCH_RESULT',
    query: question,
    params: {
      limit: null,
      dateFrom: null,
      sortBy: null,
      categories: [],
    },
    items: response.items ?? [],
  };
}

export function useLiteratureSearch(bindings: LiteratureSearchBindings) {
  const literatureLoading = ref(false);

  async function search(question: string) {
    const normalizedQuestion = question.trim();
    if (!normalizedQuestion || literatureLoading.value) {
      return;
    }

    literatureLoading.value = true;
    const currentConversationId = bindings.activeConversationId.value ?? `pending-literature-${Date.now()}`;
    const currentOrder = bindings.conversationMessages.value.length + 1;
    const userMessage = createTemporaryUserMessage(normalizedQuestion, currentConversationId, currentOrder);
    const assistantMessage = createTemporaryAssistantMessage(currentConversationId, currentOrder + 1);
    bindings.conversationMessages.value = [...bindings.conversationMessages.value, userMessage, assistantMessage];

    try {
      const response = await searchLiterature({
        conversationId: bindings.activeConversationId.value ?? undefined,
        query: normalizedQuestion,
      });
      const resolvedConversationId = response.conversationId || bindings.activeConversationId.value || currentConversationId;
      const summary = response.summary?.trim() || (response.items.length > 0
        ? `找到 ${response.items.length} 篇与「${normalizedQuestion}」相关的论文`
        : `未找到与「${normalizedQuestion}」相关的论文`);
      const metadata = buildAssistantMetadata(normalizedQuestion, response);

      bindings.activeConversationId.value = resolvedConversationId;
      bindings.conversationMessages.value = bindings.conversationMessages.value.map((message) => {
        if (message.id !== userMessage.id && message.id !== assistantMessage.id) {
          return message;
        }
        if (message.id === userMessage.id) {
          return { ...message, conversationId: resolvedConversationId };
        }
        return {
          ...message,
          conversationId: resolvedConversationId,
          content: summary,
          metadata,
          streaming: false,
        };
      });

      await Promise.all([
        bindings.loadConversations(),
        bindings.loadMessages(resolvedConversationId),
      ]);
    } catch (error) {
      const errorMessage = getErrorMessage(error);
      bindings.conversationMessages.value = bindings.conversationMessages.value.map((message) => (
        message.id === assistantMessage.id
          ? {
              ...message,
              content: `文献搜索失败：${errorMessage}`,
              streaming: false,
            }
          : message
      ));
      ElMessage.error(errorMessage);
    } finally {
      literatureLoading.value = false;
    }
  }

  return {
    literatureLoading,
    search,
  };
}