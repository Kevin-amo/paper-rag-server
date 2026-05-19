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
  currentUserAvatarUrl?: string | null;
}>();

const listRef = ref<HTMLElement | null>(null);

const emit = defineEmits<{
  askExample: [question: string];
}>();

const scrollSignal = computed(() => {
  const last = props.messages[props.messages.length - 1];
  return `${props.messages.length}:${last?.id ?? ''}:${last?.content ?? ''}:${last?.streaming ?? false}`;
});

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
  <div
    ref="listRef"
    v-loading="props.loading && !props.messages.some((message) => message.streaming)"
    class="message-list"
    :class="{ 'is-empty': !props.messages.length }"
  >
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
        <div class="message-avatar">
          <img v-if="message.role === 'USER' && props.currentUserAvatarUrl" :src="props.currentUserAvatarUrl" alt="用户头像">
          <span v-else>{{ message.role === 'USER' ? '我' : 'AI' }}</span>
        </div>
        <div class="message-body">
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
  gap: 22px;
  padding: clamp(24px, 4vw, 44px) 0 150px;
  background: #ffffff;
}

.message-list.is-empty {
  align-items: center;
  justify-content: center;
  gap: 0;
  padding: 48px 0 64px;
}

.message-list.is-empty :deep(.empty-state) {
  width: min(820px, calc(100% - 48px));
  min-height: 220px;
  border-color: #e5e7eb;
  background: #ffffff;
  box-shadow: none;
}

.message-list.is-empty :deep(.empty-state p) {
  max-width: 560px;
}

.message-list.is-empty :deep(.empty-icon) {
  background: #f3f4f6;
}

.example-prompts {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
  margin-top: 12px;
}

.example-prompts button {
  padding: 9px 14px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  background: #fff;
  color: #374151;
  cursor: pointer;
  font-size: 13px;
  font-weight: 700;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.04);
  transition: border-color 0.16s ease, color 0.16s ease, transform 0.16s ease;
}

.example-prompts button:hover {
  border-color: #d9e2ff;
  color: var(--app-primary);
  transform: translateY(-1px);
}

.message-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 10px;
  width: min(910px, calc(100% - 48px));
  margin-inline: auto;
  animation: message-in 0.2s ease both;
}

@keyframes message-in {
  from {
    opacity: 0;
    transform: translateY(6px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.from-user .message-body {
  justify-self: end;
  max-width: 72%;
  border-color: #dbe6ff;
  background: #edf4ff;
  color: #1f2937;
  box-shadow: none;
}

.from-assistant .message-body {
  width: 100%;
  max-width: 100%;
  border-color: transparent;
  background: transparent;
  box-shadow: none;
}

.message-avatar {
  display: none;
}

.message-body {
  width: fit-content;
  max-width: 100%;
  padding: 15px 17px;
  border: 1px solid var(--app-border);
  border-radius: 22px;
  background: #ffffff;
}

.from-assistant .message-body {
  padding: 6px 4px;
  border-radius: 0;
}

.message-content {
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--app-text);
  line-height: 1.82;
}

.markdown-content {
  white-space: normal;
  font-size: 15px;
}

.markdown-content :deep(p) {
  margin: 0 0 0.85em;
}

.markdown-content :deep(p:last-child),
.markdown-content :deep(ul:last-child),
.markdown-content :deep(ol:last-child),
.markdown-content :deep(pre:last-child) {
  margin-bottom: 0;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  padding-left: 1.45em;
  margin: 0.55em 0 0.9em;
}

.markdown-content :deep(li + li) {
  margin-top: 0.25em;
}

.markdown-content :deep(code) {
  padding: 0.15em 0.36em;
  border-radius: 6px;
  background: #f3f4f6;
  color: #374151;
  font-size: 0.92em;
}

.markdown-content :deep(pre) {
  position: relative;
  overflow-x: auto;
  margin: 1em 0;
  padding: 42px 16px 16px;
  border: 1px solid #111827;
  border-radius: 14px;
  background: #111827;
  color: #e5e7eb;
}

.markdown-content :deep(pre::before) {
  content: 'code';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 30px;
  display: flex;
  align-items: center;
  padding: 0 14px;
  border-bottom: 1px solid rgba(229, 231, 235, 0.1);
  color: #9ca3af;
  font-size: 12px;
}

.markdown-content :deep(pre code) {
  padding: 0;
  background: transparent;
  color: inherit;
}

.markdown-content :deep(blockquote) {
  margin: 0.85em 0;
  padding: 10px 14px;
  border-left: 3px solid #d9e2ff;
  border-radius: 0 12px 12px 0;
  background: #f7f8fa;
  color: #4b5563;
}

.markdown-content :deep(a) {
  color: var(--app-primary);
  font-weight: 700;
  text-decoration: none;
}

.markdown-content :deep(a:hover) {
  text-decoration: underline;
}

.streaming-indicator {
  display: inline-flex;
  margin-top: 8px;
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 700;
}

.streaming-indicator::before {
  content: '';
  width: 6px;
  height: 6px;
  margin: 6px 7px 0 0;
  border-radius: 999px;
  background: var(--app-primary);
  animation: pulse 1s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 0.35; }
  50% { opacity: 1; }
}

@media (max-width: 640px) {
  .message-list {
    padding-bottom: 178px;
  }

  .message-row,
  .from-user {
    width: calc(100% - 24px);
    grid-template-columns: 1fr;
  }

  .from-user .message-body {
    max-width: 86%;
  }
}
</style>