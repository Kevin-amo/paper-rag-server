<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import EmptyState from './common/EmptyState.vue';
import ConfirmDeleteButton from './common/ConfirmDeleteButton.vue';
import type { Conversation, ConversationMessage } from '../types';

const props = defineProps<{
  loading: boolean;
  conversations: Conversation[];
  messages: ConversationMessage[];
  activeConversationId: string | null;
  conversationsLoading?: boolean;
  messagesLoading?: boolean;
  cleaningConversations?: boolean;
}>();

const emit = defineEmits<{
  submit: [payload: { question: string; topK?: number }];
  createConversation: [];
  cleanEmptyConversations: [];
  selectConversation: [conversationId: string];
  deleteConversation: [conversationId: string];
}>();

const form = ref({
  question: '',
  topK: 3,
});
const messageListRef = ref<HTMLElement | null>(null);

const activeConversation = computed(() => props.conversations.find((item) => item.id === props.activeConversationId) ?? null);
const sortedMessages = computed(() => props.messages
  .filter((message) => {
    const content = message.content.trim();
    return (content && content !== '-') || message.citations?.length;
  })
  .sort((a, b) => a.messageOrder - b.messageOrder));

watch(
  () => props.messages.length,
  async () => {
    await nextTick();
    messageListRef.value?.scrollTo({ top: messageListRef.value.scrollHeight, behavior: 'smooth' });
  },
);

function handleSubmit() {
  if (!form.value.question.trim()) {
    ElMessage.warning('请输入问题');
    return;
  }

  emit('submit', {
    question: form.value.question.trim(),
    topK: form.value.topK || undefined,
  });
  form.value.question = '';
}

function formatDate(value: string) {
  return value ? new Date(value).toLocaleString() : '-';
}

function previewTitle(conversation: Conversation) {
  return conversation.title || '新会话';
}
</script>

<template>
  <el-card shadow="never" class="rag-card">
    <template #header>
      <div class="rag-header">
        <div>
          <p class="section-kicker">RAG Assistant</p>
          <h2>论文问答</h2>
          <p>基于已解析文档进行检索增强回答，并展示引用来源。</p>
        </div>
        <div class="header-actions">
          <el-button :loading="props.cleaningConversations" @click="emit('cleanEmptyConversations')">
            清理空会话
          </el-button>
          <el-button type="primary" @click="emit('createConversation')">
            新建会话
          </el-button>
        </div>
      </div>
    </template>

    <div class="chat-layout">
      <aside class="conversation-panel" v-loading="props.conversationsLoading">
        <EmptyState v-if="!props.conversations.length" compact title="暂无会话" description="发送问题或新建会话后会出现在这里。" />
        <div v-else class="conversation-list">
          <div
            v-for="conversation in props.conversations"
            :key="conversation.id"
            class="conversation-item"
            :class="{ active: conversation.id === props.activeConversationId }"
          >
            <button
              class="conversation-select"
              type="button"
              @click="emit('selectConversation', conversation.id)"
            >
              <span class="conversation-title">{{ previewTitle(conversation) }}</span>
              <span class="conversation-time">{{ formatDate(conversation.updatedAt) }}</span>
            </button>
            <ConfirmDeleteButton
              class="delete-button"
              title="确认删除这个会话吗？"
              confirm-text="删除"
              @confirm="emit('deleteConversation', conversation.id)"
            />
          </div>
        </div>
      </aside>

      <section class="message-panel">
        <div class="message-title">
          <div>
            <strong>{{ activeConversation ? previewTitle(activeConversation) : '临时会话' }}</strong>
            <span>{{ activeConversation ? formatDate(activeConversation.updatedAt) : '发送第一条消息后自动创建' }}</span>
          </div>
          <el-tag size="small" type="info">Top K {{ form.topK }}</el-tag>
        </div>

        <div ref="messageListRef" class="message-list" v-loading="props.messagesLoading">
          <EmptyState v-if="!sortedMessages.length" title="开始提问" description="可以询问论文观点、方法、结论、实验设置或引用依据。" />
          <template v-else>
            <article
              v-for="message in sortedMessages"
              :key="message.id"
              class="message-bubble"
              :class="message.role === 'USER' ? 'user-message' : 'assistant-message'"
            >
              <div class="message-role">{{ message.role === 'USER' ? '你' : '助手' }}</div>
              <div class="message-content">{{ message.content }}</div>
              <div v-if="message.citations?.length" class="citation-list">
                <el-card v-for="citation in message.citations" :key="`${message.id}-${citation.chunkId}`" shadow="never" class="citation-card">
                  <div class="citation-meta">
                    <strong>{{ citation.title || citation.sourceId }}</strong>
                    <el-tag size="small">相关度 {{ citation.rankScore.toFixed(4) }}</el-tag>
                  </div>
                  <p>{{ citation.excerpt }}</p>
                  <small>{{ citation.sourceId }} · 分块 #{{ citation.chunkIndex }}</small>
                </el-card>
              </div>
              <time>{{ formatDate(message.createdAt) }}</time>
            </article>
          </template>
        </div>

        <el-form class="composer" label-position="top" @submit.prevent>
          <el-form-item label="问题">
            <el-input
              v-model="form.question"
              type="textarea"
              :rows="4"
              resize="none"
              placeholder="例如：这篇论文的核心贡献是什么？请给出引用依据。"
              @keydown.ctrl.enter="handleSubmit"
            />
          </el-form-item>
          <div class="composer-actions">
            <el-form-item label="召回数量" class="topk-field">
              <el-input-number v-model="form.topK" :min="1" :max="10" />
            </el-form-item>
            <el-button type="primary" :loading="props.loading" @click="handleSubmit">发送问题</el-button>
          </div>
        </el-form>
      </section>
    </div>
  </el-card>
</template>

<style scoped>
.rag-card {
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  box-shadow: var(--app-shadow);
}

.rag-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.section-kicker {
  margin: 0 0 6px;
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.rag-header h2 {
  margin: 0;
  font-size: 20px;
}

.rag-header p:last-child {
  margin: 6px 0 0;
  color: var(--app-text-muted);
}

.chat-layout {
  display: grid;
  grid-template-columns: 190px minmax(0, 1fr);
  gap: 16px;
  min-height: 680px;
}

.conversation-panel {
  min-height: 100%;
  padding-right: 12px;
  border-right: 1px solid var(--app-border);
}

.conversation-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.conversation-item {
  width: 100%;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  padding: 10px;
  border: 1px solid var(--app-border);
  border-radius: 14px;
  background: #fff;
  color: var(--app-text);
}

.conversation-select {
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  border: 0;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.conversation-item.active {
  border-color: var(--app-primary);
  background: var(--app-primary-soft);
}

.conversation-title {
  width: 100%;
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-time {
  font-size: 12px;
  color: var(--app-text-muted);
}

.delete-button {
  align-self: flex-end;
}

.message-panel {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.message-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 16px;
  background: var(--app-surface-soft);
}

.message-title div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.message-title span {
  font-size: 12px;
  color: var(--app-text-muted);
}

.message-list {
  min-height: 380px;
  max-height: 520px;
  overflow-y: auto;
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--app-border);
  border-radius: 18px;
  background: #f8fafc;
}

.message-bubble {
  max-width: 88%;
  padding: 12px 14px;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
}

.user-message {
  align-self: flex-end;
  background: var(--app-primary);
  color: #fff;
}

.assistant-message {
  align-self: flex-start;
}

.message-role {
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 700;
  opacity: 0.8;
}

.message-content {
  white-space: pre-wrap;
  line-height: 1.7;
}

.message-bubble time {
  display: block;
  margin-top: 8px;
  font-size: 11px;
  opacity: 0.7;
}

.citation-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
}

.citation-card {
  border-radius: 14px;
}

.citation-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.citation-card p {
  margin: 10px 0 6px;
  color: #334155;
  white-space: pre-wrap;
}

.citation-card small {
  color: var(--app-text-muted);
}

.composer {
  padding: 14px;
  border: 1px solid var(--app-border);
  border-radius: 18px;
  background: #fff;
}

.composer-actions {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
}

.topk-field {
  margin-bottom: 0;
}

@media (max-width: 760px) {
  .rag-header,
  .chat-layout,
  .composer-actions {
    align-items: stretch;
    grid-template-columns: 1fr;
    flex-direction: column;
  }

  .conversation-panel {
    padding-right: 0;
    padding-bottom: 12px;
    border-right: none;
    border-bottom: 1px solid var(--app-border);
  }

  .message-bubble {
    max-width: 100%;
  }
}
</style>