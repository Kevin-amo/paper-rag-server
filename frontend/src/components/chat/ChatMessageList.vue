<script lang="ts">
export default {
  name: 'ChatMessageList',
};
</script>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import EmptyState from '../common/EmptyState.vue';
import CitationCards from './CitationCards.vue';
import LiteratureSearchResults from './LiteratureSearchResults.vue';
import { renderMarkdown } from '../../utils/markdown';
import type {
  AgentResultMetadata,
  ConversationMessage,
  LiteratureSearchMessageMetadata,
} from '../../types';

const props = defineProps<{
  messages: ConversationMessage[];
  loading?: boolean;
  currentUserAvatarUrl?: string | null;
}>();

const listRef = ref<HTMLElement | null>(null);
const AUTO_SCROLL_THRESHOLD_PX = 80;

const emit = defineEmits<{
  askExample: [question: string];
}>();

const scrollSignal = computed(() => {
  const last = props.messages[props.messages.length - 1];
  return `${props.messages.length}:${last?.id ?? ''}:${last?.content ?? ''}:${last?.streaming ?? false}`;
});

const emptyContent = computed(() => ({
  title: '开始使用论文超级助手',
  description: '你可以让它检索本地论文、搜索外部文献，并综合分析研究趋势、方法差异和后续方向。',
  examples: [
    '请总结我上传论文中的核心研究问题和主要贡献',
    '帮我找 Graph RAG 最新论文，并结合我的知识库总结趋势',
    '这些论文在方法设计上有哪些共同点和差异？',
  ],
}));

function assistantHtml(content: string, streaming?: boolean) {
  if (content && content.trim()) {
    return renderMarkdown(content);
  }
  return renderMarkdown(streaming ? '正在生成...' : '');
}

function isLiteratureSearchMetadata(metadata: ConversationMessage['metadata']): metadata is LiteratureSearchMessageMetadata {
  return !!metadata
    && typeof metadata === 'object'
    && 'type' in metadata
    && metadata.type === 'LITERATURE_SEARCH_RESULT'
    && Array.isArray((metadata as LiteratureSearchMessageMetadata).items);
}

function isAgentResultMetadata(metadata: ConversationMessage['metadata']): metadata is AgentResultMetadata {
  return !!metadata
    && typeof metadata === 'object'
    && 'type' in metadata
    && metadata.type === 'AGENT_RESULT'
    && Array.isArray((metadata as AgentResultMetadata).steps);
}

function literatureMetadata(message: ConversationMessage): LiteratureSearchMessageMetadata | null {
  if (isLiteratureSearchMetadata(message.metadata)) {
    return message.metadata;
  }
  return null;
}

function hasLiteratureMetadata(message: ConversationMessage) {
  return literatureMetadata(message) !== null;
}

function literatureItems(message: ConversationMessage) {
  return literatureMetadata(message)?.items ?? [];
}

function literatureQuery(message: ConversationMessage) {
  return literatureMetadata(message)?.query;
}

function isNearBottom(element: HTMLElement) {
  return element.scrollHeight - element.scrollTop - element.clientHeight <= AUTO_SCROLL_THRESHOLD_PX;
}

watch(
  scrollSignal,
  async () => {
    const shouldFollowOutput = listRef.value ? isNearBottom(listRef.value) : true;

    await nextTick();

    if (shouldFollowOutput) {
      listRef.value?.scrollTo({ top: listRef.value.scrollHeight, behavior: 'smooth' });
    }
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
      :title="emptyContent.title"
      :description="emptyContent.description"
    >
      <div class="example-prompts">
        <button
          v-for="example in emptyContent.examples"
          :key="example"
          type="button"
          @click="emit('askExample', example)"
        >
          {{ example }}
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
          <template v-else>
            <div v-if="isAgentResultMetadata(message.metadata) && message.metadata.steps.length" class="agent-steps">
              <details>
                <summary>分析过程</summary>
                <ol>
                  <li v-for="step in message.metadata.steps" :key="`${message.id}-${step.index}`">
                    <span>{{ step.thoughtSummary }}</span>
                    <em v-if="step.observationSummary">{{ step.observationSummary }}</em>
                  </li>
                </ol>
              </details>
            </div>
            <div
              class="message-content markdown-content"
              v-html="assistantHtml(message.content, message.streaming)"
            />
          </template>
          <span v-if="message.streaming" class="streaming-indicator">
            正在执行任务...
          </span>
          <CitationCards v-if="message.role === 'ASSISTANT' && message.citations?.length" :citations="message.citations || []" />
          <LiteratureSearchResults
            v-if="message.role === 'ASSISTANT' && hasLiteratureMetadata(message)"
            inline
            :items="literatureItems(message)"
            :last-query="literatureQuery(message)"
            :has-searched="true"
            :loading="false"
          />
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
  padding: clamp(24px, 4vw, 44px) 0 154px;
  background: transparent;
  scrollbar-color: rgba(0, 122, 255, 0.28) transparent;
}

.message-list.is-empty {
  align-items: center;
  justify-content: center;
  gap: 0;
  padding: 48px 0 64px;
}

.message-list.is-empty :deep(.empty-state) {
  width: min(820px, calc(100% - 48px));
  min-height: 230px;
  border-color: rgba(255, 255, 255, 0.76);
  background: rgba(255, 255, 255, 0.64);
  box-shadow:
    0 18px 44px rgba(15, 23, 42, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(18px) saturate(165%);
  -webkit-backdrop-filter: blur(18px) saturate(165%);
}

.message-list.is-empty :deep(.empty-state p) {
  max-width: 560px;
}

.message-list.is-empty :deep(.empty-icon) {
  background: rgba(0, 122, 255, 0.1);
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
  border: 1px solid rgba(255, 255, 255, 0.74);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.66);
  color: var(--app-text);
  cursor: pointer;
  font-size: 13px;
  font-weight: 750;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.06);
  transition: border-color 0.16s ease, box-shadow 0.16s ease, color 0.16s ease, transform 0.16s ease;
}

.example-prompts button:hover {
  border-color: rgba(0, 122, 255, 0.28);
  box-shadow: var(--app-shadow-glow);
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
  border-color: rgba(0, 122, 255, 0.22);
  background: linear-gradient(135deg, rgba(0, 122, 255, 0.14), rgba(90, 200, 250, 0.12));
  color: var(--app-text);
  box-shadow:
    0 12px 28px rgba(0, 122, 255, 0.12),
    inset 0 1px 0 rgba(255, 255, 255, 0.7);
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
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.66);
  backdrop-filter: blur(18px) saturate(160%);
  -webkit-backdrop-filter: blur(18px) saturate(160%);
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
  border-radius: 7px;
  background: rgba(242, 242, 247, 0.86);
  color: #3a3a3c;
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
  border-left: 3px solid rgba(0, 122, 255, 0.28);
  border-radius: 0 14px 14px 0;
  background: rgba(255, 255, 255, 0.56);
  color: #515154;
}

.markdown-content :deep(a) {
  color: var(--app-primary);
  font-weight: 700;
  text-decoration: none;
}

.markdown-content :deep(a:hover) {
  text-decoration: underline;
}

.agent-steps {
  margin: 12px 0 4px;
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.58);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.76);
}

.agent-steps details {
  padding: 10px 12px;
}

.agent-steps summary {
  cursor: pointer;
  color: #475569;
  font-size: 13px;
  font-weight: 900;
}

.agent-steps ol {
  display: grid;
  gap: 8px;
  margin: 10px 0 0;
  padding-left: 20px;
}

.agent-steps li {
  color: #475569;
  line-height: 1.6;
}

.agent-steps strong {
  display: inline-flex;
  margin-right: 8px;
  color: var(--app-primary);
  font-size: 12px;
}

.agent-steps span {
  margin-right: 8px;
}

.agent-steps em {
  display: block;
  margin-top: 3px;
  color: #64748b;
  font-style: normal;
  font-size: 12px;
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