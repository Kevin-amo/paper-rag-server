<script lang="ts">
export default {
  name: 'ChatMessageList',
};
</script>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import EmptyState from '../common/EmptyState.vue';
import CitationCards from './CitationCards.vue';
import { renderMarkdown } from '../../utils/markdown';
import type { ConversationMessage } from '../../types';

const props = defineProps<{
  messages: ConversationMessage[];
  loading?: boolean;
}>();

const listRef = ref<HTMLElement | null>(null);

const emit = defineEmits<{
  askExample: [question: string];
}>();

const scrollSignal = computed(() => {
  const last = props.messages[props.messages.length - 1];
  return `${props.messages.length}:${last?.id ?? ''}:${last?.content ?? ''}:${last?.streaming ?? false}`;
});

function formatDate(value: string) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour: '2-digit', minute: '2-digit' }) : '';
}

function assistantHtml(content: string) {
  return renderMarkdown(content || '正在生成回答...');
}

watch(
  scrollSignal,
  async () => {
    await nextTick();
    listRef.value?.scrollTo({ top: listRef.value.scrollHeight, behavior: 'smooth' });
  },
);
</script>

<template>
  <div ref="listRef" v-loading="props.loading && !props.messages.some((message) => message.streaming)" class="message-list">
    <EmptyState
      v-if="!props.messages.length"
      title="开始和你的论文知识库对话"
      description="你可以询问论文贡献、方法对比、实验结论、局限性，系统会基于已上传论文给出可追溯回答。"
    >
      <div class="example-prompts">
        <button type="button" @click="emit('askExample', '请总结我上传论文中的核心研究问题和主要贡献')">
          总结核心贡献
        </button>
        <button type="button" @click="emit('askExample', '这些论文在方法设计上有哪些共同点和差异？')">
          对比研究方法
        </button>
        <button type="button" @click="emit('askExample', '请基于引用说明相关工作的实验结论')">
          查看实验结论
        </button>
      </div>
    </EmptyState>

    <template v-else>
      <article
        v-for="message in props.messages"
        :key="message.id"
        class="message-row"
        :class="message.role === 'USER' ? 'from-user' : 'from-assistant'"
      >
        <div class="message-avatar">{{ message.role === 'USER' ? '我' : 'AI' }}</div>
        <div class="message-body">
          <div class="message-meta">
            <strong>{{ message.role === 'USER' ? '你' : '论文助手' }}</strong>
            <time>{{ formatDate(message.createdAt) }}</time>
          </div>
          <div v-if="message.role === 'USER'" class="message-content">{{ message.content }}</div>
          <div v-else class="message-content markdown-content" v-html="assistantHtml(message.content)" />
          <span v-if="message.streaming" class="streaming-indicator">正在生成...</span>
          <CitationCards v-if="message.role === 'ASSISTANT'" :citations="message.citations || []" />
        </div>
      </article>
    </template>
  </div>
</template>

<style scoped>
.message-list {
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 18px;
  padding: clamp(18px, 3vw, 30px);
  background:
    radial-gradient(circle at top right, rgba(37, 99, 235, 0.06), transparent 28rem),
    linear-gradient(180deg, #f8fbff, #f8fafc);
}

.example-prompts {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
  margin-top: 8px;
}

.example-prompts button {
  padding: 9px 13px;
  border: 1px solid rgba(37, 99, 235, 0.16);
  border-radius: 999px;
  background: #fff;
  color: var(--app-primary);
  cursor: pointer;
  font-size: 13px;
  font-weight: 700;
}

.example-prompts button:hover {
  border-color: var(--app-primary);
  background: var(--app-primary-soft);
}

.message-row {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  gap: 12px;
  max-width: min(860px, 92%);
}

.from-user {
  align-self: flex-end;
  grid-template-columns: minmax(0, 1fr) 38px;
}

.from-user .message-avatar {
  order: 2;
  color: #fff;
  background: linear-gradient(135deg, #2563eb, #4f46e5);
}

.from-user .message-body {
  order: 1;
  border-color: rgba(37, 99, 235, 0.24);
  background: #2563eb;
  color: #fff;
}

.message-avatar {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 14px;
  background: #e0ecff;
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 900;
}

.message-body {
  padding: 15px 16px;
  border: 1px solid rgba(226, 232, 240, 0.86);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 14px 38px rgba(15, 23, 42, 0.07);
}

.message-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  font-size: 12px;
}

.message-meta time {
  color: inherit;
  opacity: 0.68;
}

.message-content {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.78;
}

.markdown-content {
  white-space: normal;
}

.markdown-content :deep(p) {
  margin: 0 0 0.75em;
}

.markdown-content :deep(p:last-child),
.markdown-content :deep(ul:last-child),
.markdown-content :deep(ol:last-child),
.markdown-content :deep(pre:last-child) {
  margin-bottom: 0;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  padding-left: 1.4em;
  margin: 0.5em 0 0.85em;
}

.markdown-content :deep(code) {
  padding: 0.15em 0.35em;
  border-radius: 6px;
  background: #f1f5f9;
  color: #334155;
  font-size: 0.92em;
}

.markdown-content :deep(pre) {
  overflow-x: auto;
  padding: 12px 14px;
  border-radius: 12px;
  background: #0f172a;
  color: #e2e8f0;
}

.markdown-content :deep(pre code) {
  padding: 0;
  background: transparent;
  color: inherit;
}

.markdown-content :deep(blockquote) {
  margin: 0.7em 0;
  padding-left: 1em;
  border-left: 3px solid #cbd5e1;
  color: #475569;
}

.markdown-content :deep(a) {
  color: var(--app-primary);
  font-weight: 700;
}

.streaming-indicator {
  display: inline-flex;
  margin-top: 8px;
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 700;
}

.from-user .message-content {
  color: #fff;
}

@media (max-width: 640px) {
  .message-row,
  .from-user {
    max-width: 100%;
    grid-template-columns: 1fr;
  }

  .message-avatar {
    display: none;
  }

  .from-user .message-body {
    order: initial;
  }
}
</style>