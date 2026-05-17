import axios from 'axios';
import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  deleteConversation,
  listConversationMessages,
  listConversations,
} from '../api/conversations';
import { getErrorMessage } from '../api/http';
import type { Conversation, ConversationMessage } from '../types';

function asArray<T>(value: T[] | { items?: T[] } | unknown, label: string): T[] {
  if (Array.isArray(value)) {
    return value;
  }

  if (value && typeof value === 'object' && 'items' in value && Array.isArray(value.items)) {
    return value.items;
  }

  throw new Error(`${label} 接口返回格式异常`);
}

function isNotFound(error: unknown) {
  return axios.isAxiosError(error) && error.response?.status === 404;
}

export function useConversations() {
  const conversationsLoading = ref(false);
  const messagesLoading = ref(false);
  const cleaningConversations = ref(false);
  const conversations = ref<Conversation[]>([]);
  const activeConversationId = ref<string | null>(null);
  const conversationMessages = ref<ConversationMessage[]>([]);

  async function loadConversations(selectFirst = false) {
    conversationsLoading.value = true;
    try {
      conversations.value = asArray<Conversation>(await listConversations(), '会话列表');
      if (selectFirst && !activeConversationId.value && conversations.value.length > 0) {
        await selectConversation(conversations.value[0].id);
      }
    } catch (error) {
      ElMessage.error(getErrorMessage(error));
    } finally {
      conversationsLoading.value = false;
    }
  }

  async function loadMessages(conversationId: string) {
    messagesLoading.value = true;
    try {
      const messages = asArray<ConversationMessage>(await listConversationMessages(conversationId), '会话消息');
      if (messages.length > 0 || activeConversationId.value !== conversationId || conversationMessages.value.length === 0) {
        conversationMessages.value = messages;
      }
    } catch (error) {
      if (isNotFound(error)) {
        conversations.value = conversations.value.filter((conversation) => conversation.id !== conversationId);
        if (activeConversationId.value === conversationId) {
          activeConversationId.value = null;
          conversationMessages.value = [];
        }
        return;
      }
      ElMessage.error(getErrorMessage(error));
    } finally {
      messagesLoading.value = false;
    }
  }

  async function selectConversation(conversationId: string) {
    activeConversationId.value = conversationId;
    await loadMessages(conversationId);
  }

  function createNewConversation() {
    activeConversationId.value = null;
    conversationMessages.value = [];
  }

  async function removeConversation(conversationId: string) {
    try {
      await deleteConversation(conversationId);
      conversations.value = conversations.value.filter((conversation) => conversation.id !== conversationId);

      if (activeConversationId.value === conversationId) {
        activeConversationId.value = conversations.value[0]?.id ?? null;
        if (activeConversationId.value) {
          await loadMessages(activeConversationId.value);
        } else {
          conversationMessages.value = [];
        }
      }

      ElMessage.success('会话已删除');
    } catch (error) {
      if (isNotFound(error)) {
        conversations.value = conversations.value.filter((conversation) => conversation.id !== conversationId);
        if (activeConversationId.value === conversationId) {
          activeConversationId.value = null;
          conversationMessages.value = [];
        }
        ElMessage.success('会话已从列表移除');
        return;
      }
      ElMessage.error(getErrorMessage(error));
    }
  }

  function isDisposableMessage(message: ConversationMessage) {
    const content = message.content.trim();
    return (!content || content === '-') && !message.citations?.length;
  }

  async function cleanEmptyConversations() {
    if (!conversations.value.length) {
      return;
    }

    cleaningConversations.value = true;
    let cleanedCount = 0;

    try {
      for (const conversation of [...conversations.value]) {
        let messages: ConversationMessage[];
        try {
          messages = asArray<ConversationMessage>(await listConversationMessages(conversation.id), '会话消息');
        } catch (error) {
          if (!isNotFound(error)) {
            throw error;
          }
          messages = [];
        }

        const shouldDelete = messages.length === 0 || messages.every(isDisposableMessage);
        if (!shouldDelete) {
          continue;
        }

        try {
          await deleteConversation(conversation.id);
        } catch (error) {
          if (!isNotFound(error)) {
            throw error;
          }
        }
        conversations.value = conversations.value.filter((item) => item.id !== conversation.id);
        cleanedCount += 1;
      }

      if (cleanedCount > 0) {
        ElMessage.success(`已清理 ${cleanedCount} 个空会话`);
      } else {
        ElMessage.info('没有需要清理的空会话');
      }

      activeConversationId.value = null;
      conversationMessages.value = [];
      await loadConversations(true);
    } catch (error) {
      ElMessage.error(getErrorMessage(error));
    } finally {
      cleaningConversations.value = false;
    }
  }

  return {
    conversationsLoading,
    messagesLoading,
    cleaningConversations,
    conversations,
    activeConversationId,
    conversationMessages,
    loadConversations,
    loadMessages,
    selectConversation,
    createNewConversation,
    removeConversation,
    cleanEmptyConversations,
  };
}