import { ref, type Ref } from 'vue';
import { ElMessage } from 'element-plus';
import { askAgentStream } from '../api/agent';
import { getErrorMessage } from '../api/http';
import type {
  AgentResultMetadata,
  AgentStreamEvent,
  Conversation,
  ConversationMessage,
} from '../types';

export interface AgentChatBindings {
  activeConversationId: Ref<string | null>;
  activeConversation: Ref<Conversation | null>;
  conversations: Ref<Conversation[]>;
  conversationMessages: Ref<ConversationMessage[]>;
  loadConversations: () => Promise<void>;
  loadMessages: (conversationId: string) => Promise<void>;
}

export function useAgentChat(bindings: AgentChatBindings) {
  const agentLoading = ref(false);

  async function ask(payload: { question: string; topK?: number }) {
    const normalizedQuestion = payload.question.trim();
    if (!normalizedQuestion || agentLoading.value) {
      return;
    }

    agentLoading.value = true;
    const now = Date.now();
    const conversationId = bindings.activeConversationId.value ?? undefined;
    const userMessage: ConversationMessage = {
      id: `agent-user-${now}`,
      conversationId: conversationId ?? 'pending',
      role: 'USER',
      messageOrder: bindings.conversationMessages.value.length + 1,
      content: normalizedQuestion,
      citations: [],
      metadata: null,
      createdAt: new Date().toISOString(),
    };
    const assistantMessage: ConversationMessage = {
      id: `agent-assistant-${now}`,
      conversationId: conversationId ?? 'pending',
      role: 'ASSISTANT',
      messageOrder: userMessage.messageOrder + 1,
      content: '',
      citations: [],
      metadata: {
        type: 'AGENT_RESULT',
        agent: 'paper_super_agent',
        steps: [],
      },
      createdAt: new Date().toISOString(),
      streaming: true,
    };
    bindings.conversationMessages.value = [...bindings.conversationMessages.value, userMessage, assistantMessage];

    try {
      let shouldRefreshConversations = false;
      await askAgentStream(
        {
          conversationId,
          question: normalizedQuestion,
          topK: payload.topK,
        },
        (event) => {
          if (handleStreamEvent(event, userMessage.id, assistantMessage.id)) {
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
        content: `智能体执行失败：${errorMessage}`,
        citations: [],
        streaming: false,
      });
      ElMessage.error(errorMessage);
    } finally {
      agentLoading.value = false;
    }
  }

  function handleStreamEvent(event: AgentStreamEvent, userMessageId: string, assistantMessageId: string) {
    if (event.type === 'start' && event.conversationId) {
      const isNewConversation = !isKnownConversation(event.conversationId);
      bindings.activeConversationId.value = event.conversationId;
      updateMessageConversationId(userMessageId, event.conversationId);
      updateMessageConversationId(assistantMessageId, event.conversationId);
      return isNewConversation;
    }

    if (event.type === 'thought' && event.thought) {
      appendAgentStep(assistantMessageId, {
        index: event.step ?? nextStepIndex(assistantMessageId),
        thoughtSummary: event.thought,
        action: 'thinking',
        actionInput: {},
        observationSummary: '',
      });
      return false;
    }

    if (event.type === 'tool_call' && event.toolName) {
      patchLastAgentStep(assistantMessageId, {
        action: event.toolName,
        actionInput: event.toolInput ?? {},
      });
      return false;
    }

    if (event.type === 'tool_result') {
      patchLastAgentStep(assistantMessageId, {
        observationSummary: event.observation ?? '',
      });
      return false;
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
        content: event.answer || currentMessageContent(assistantMessageId) || '智能体未返回回答内容',
        citations: event.citations ?? [],
        metadata: normalizeAgentMetadata(event.metadata),
        streaming: false,
      });
      return false;
    }

    if (event.type === 'error') {
      throw new Error(event.message || '智能体执行失败');
    }

    return false;
  }

  function normalizeAgentMetadata(metadata: AgentStreamEvent['metadata']): AgentResultMetadata {
    if (metadata && typeof metadata === 'object' && 'type' in metadata && metadata.type === 'AGENT_RESULT') {
      return metadata as AgentResultMetadata;
    }
    return {
      type: 'AGENT_RESULT',
      agent: 'paper_super_agent',
      steps: [],
    };
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

  function appendAgentStep(messageId: string, step: AgentResultMetadata['steps'][number]) {
    bindings.conversationMessages.value = bindings.conversationMessages.value.map((message) => {
      if (message.id !== messageId) {
        return message;
      }
      const metadata = normalizeAgentMetadata(message.metadata as AgentStreamEvent['metadata']);
      return {
        ...message,
        metadata: {
          ...metadata,
          steps: [...metadata.steps, step],
        },
      };
    });
  }

  function patchLastAgentStep(messageId: string, patch: Partial<AgentResultMetadata['steps'][number]>) {
    bindings.conversationMessages.value = bindings.conversationMessages.value.map((message) => {
      if (message.id !== messageId) {
        return message;
      }
      const metadata = normalizeAgentMetadata(message.metadata as AgentStreamEvent['metadata']);
      if (!metadata.steps.length) {
        return message;
      }
      const steps = metadata.steps.map((step, index) => (
        index === metadata.steps.length - 1 ? { ...step, ...patch } : step
      ));
      return {
        ...message,
        metadata: {
          ...metadata,
          steps,
        },
      };
    });
  }

  function updateAssistantMessage(messageId: string, patch: Partial<ConversationMessage>) {
    bindings.conversationMessages.value = bindings.conversationMessages.value.map((message) => (
      message.id === messageId ? { ...message, ...patch } : message
    ));
  }

  function currentMessageContent(messageId: string) {
    return bindings.conversationMessages.value.find((message) => message.id === messageId)?.content ?? '';
  }

  function nextStepIndex(messageId: string) {
    const metadata = bindings.conversationMessages.value.find((message) => message.id === messageId)?.metadata;
    return normalizeAgentMetadata(metadata as AgentStreamEvent['metadata']).steps.length + 1;
  }

  function isKnownConversation(conversationId: string | null) {
    return !!conversationId && bindings.conversations.value.some((conversation) => conversation.id === conversationId);
  }

  return {
    agentLoading,
    ask,
  };
}