import { ref, type Ref } from 'vue';
import { ElMessage } from 'element-plus';
import { askQuestionStream } from '../api/rag';
import { getErrorMessage } from '../api/http';
import type { Conversation, ConversationMessage, RagStreamEvent } from '../types';

export interface RagChatBindings {
  activeConversationId: Ref<string | null>;
  conversations: Ref<Conversation[]>;
  conversationMessages: Ref<ConversationMessage[]>;
  loadConversations: () => Promise<void>;
  loadMessages: (conversationId: string) => Promise<void>;
}

export function useRagChat(bindings: RagChatBindings) {
  const ragLoading = ref(false);

  async function ask(payload: { question: string; topK?: number }) {
    ragLoading.value = true;
    const now = Date.now();
    const tempMessage: ConversationMessage = {
      id: `pending-${now}`,
      conversationId: bindings.activeConversationId.value ?? 'pending',
      role: 'USER',
      messageOrder: bindings.conversationMessages.value.length + 1,
      content: payload.question,
      citations: [],
      createdAt: new Date().toISOString(),
    };
    const assistantMessage: ConversationMessage = {
      id: `answer-${now}`,
      conversationId: bindings.activeConversationId.value ?? 'pending',
      role: 'ASSISTANT',
      messageOrder: tempMessage.messageOrder + 1,
      content: '',
      citations: [],
      createdAt: new Date().toISOString(),
      streaming: true,
    };
    bindings.conversationMessages.value = [...bindings.conversationMessages.value, tempMessage, assistantMessage];

    try {
      let shouldRefreshConversations = false;
      await askQuestionStream(
        {
          conversationId: bindings.activeConversationId.value ?? undefined,
          question: payload.question,
          topK: payload.topK,
        },
        (event) => {
          if (handleStreamEvent(event, tempMessage.id, assistantMessage.id)) {
            shouldRefreshConversations = true;
            void bindings.loadConversations();
          }
        },
      );
      if (shouldRefreshConversations || !isKnownConversation(bindings.activeConversationId.value)) {
        await bindings.loadConversations();
      }
    } catch (error) {
      const errorMessage = getErrorMessage(error);
      updateAssistantMessage(assistantMessage.id, {
        content: `回答生成失败：${errorMessage}`,
        citations: [],
        streaming: false,
      });
      ElMessage.error(errorMessage);
    } finally {
      ragLoading.value = false;
    }
  }

  function handleStreamEvent(event: RagStreamEvent, userMessageId: string, assistantMessageId: string) {
    if (event.type === 'start' && event.conversationId) {
      const isNewConversation = !isKnownConversation(event.conversationId);
      bindings.activeConversationId.value = event.conversationId;
      updateMessageConversationId(userMessageId, event.conversationId);
      updateMessageConversationId(assistantMessageId, event.conversationId);
      return isNewConversation;
    }

    if (event.type === 'delta' && event.delta) {
      appendAssistantContent(assistantMessageId, event.delta);
      return false;
    }

    if (event.type === 'done') {
      if (event.conversationId) {
        bindings.activeConversationId.value = event.conversationId;
        updateMessageConversationId(userMessageId, event.conversationId);
        updateMessageConversationId(assistantMessageId, event.conversationId);
      }
      updateAssistantMessage(assistantMessageId, {
        content: event.answer || currentMessageContent(assistantMessageId) || '模型未返回回答内容',
        citations: event.citations ?? [],
        streaming: false,
      });
      return false;
    }

    if (event.type === 'error') {
      throw new Error(event.message || '回答生成失败');
    }
  }

  function updateMessageConversationId(messageId: string, conversationId: string) {
    bindings.conversationMessages.value = bindings.conversationMessages.value.map((message) => (
      message.id === messageId ? { ...message, conversationId } : message
    ));
  }

  function appendAssistantContent(messageId: string, delta: string) {
    bindings.conversationMessages.value = bindings.conversationMessages.value.map((message) => (
      message.id === messageId ? { ...message, content: `${message.content}${delta}` } : message
    ));
  }

  function updateAssistantMessage(messageId: string, patch: Partial<ConversationMessage>) {
    bindings.conversationMessages.value = bindings.conversationMessages.value.map((message) => (
      message.id === messageId ? { ...message, ...patch } : message
    ));
  }

  function currentMessageContent(messageId: string) {
    return bindings.conversationMessages.value.find((message) => message.id === messageId)?.content ?? '';
  }

  function isKnownConversation(conversationId: string | null) {
    return !!conversationId && bindings.conversations.value.some((conversation) => conversation.id === conversationId);
  }

  return {
    ragLoading,
    ask,
  };
}