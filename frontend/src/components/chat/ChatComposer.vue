<script lang="ts">
export default {
  name: 'ChatComposer',
};
</script>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { Promotion, Setting, UploadFilled, Collection } from '@element-plus/icons-vue';
import type { ChatMode } from '../../types';

const props = defineProps<{
  loading?: boolean;
  mode: ChatMode;
}>();

const emit = defineEmits<{
  submit: [payload: { mode: ChatMode; question: string; topK?: number }];
  'update:mode': [mode: ChatMode];
  openDocuments: [];
  openUpload: [];
}>();

const question = ref('');
const topK = ref(3);
const advancedVisible = ref(false);
const topKOptions = Array.from({ length: 10 }, (_, index) => index + 1);

const canSubmit = computed(() => question.value.trim().length > 0 && !props.loading);
const placeholder = computed(() => (
  props.mode === 'literature'
    ? '搜索论文，例如 Graph RAG，按 Enter 发送，Shift + Enter 换行'
    : '向论文知识库提问，按 Enter 发送，Shift + Enter 换行'
));
const modeOptions: Array<{ label: string; value: ChatMode }> = [
  { label: '论文分析', value: 'rag' },
  { label: '文献搜索', value: 'literature' },
];

function submitQuestion() {
  const content = question.value.trim();
  if (!content || props.loading) {
    return;
  }

  emit('submit', {
    mode: props.mode,
    question: content,
    topK: props.mode === 'rag' ? topK.value || undefined : undefined,
  });
  question.value = '';
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter' || event.shiftKey) {
    return;
  }

  event.preventDefault();
  submitQuestion();
}

function fillQuestion(value: string) {
  question.value = value;
  submitQuestion();
}

defineExpose({ fillQuestion });
</script>

<template>
  <section class="chat-composer">
    <div class="composer-box">
      <div class="mode-switch" role="radiogroup" aria-label="聊天模式">
        <button
          v-for="option in modeOptions"
          :key="option.value"
          type="button"
          :class="{ active: props.mode === option.value }"
          :aria-checked="props.mode === option.value"
          role="radio"
          @click="emit('update:mode', option.value)"
        >
          {{ option.label }}
        </button>
      </div>
      <el-input
        v-model="question"
        type="textarea"
        resize="none"
        :autosize="{ minRows: 2, maxRows: 7 }"
        :placeholder="placeholder"
        @keydown="handleKeydown"
      />
      <div class="composer-actions">
        <div class="composer-left-actions">
          <el-button v-if="props.mode === 'rag'" class="composer-pill" text :icon="Setting" @click="advancedVisible = true">
            高级设置 · Top K {{ topK }}
          </el-button>
          <el-button class="composer-pill hide-on-small" text :icon="Collection" @click="emit('openDocuments')">
            文档库
          </el-button>
          <el-button class="composer-pill hide-on-small" text :icon="UploadFilled" @click="emit('openUpload')">
            上传
          </el-button>
        </div>
        <el-button
          class="send-button"
          type="primary"
          circle
          :loading="props.loading"
          :disabled="!canSubmit"
          :icon="Promotion"
          aria-label="发送"
          @click="submitQuestion"
        />
      </div>
    </div>

    <el-dialog v-model="advancedVisible" title="高级设置" width="420px" class="advanced-dialog" append-to-body align-center>
      <div class="advanced-setting-card">
        <div class="advanced-setting-header">
          <div>
            <strong>引用召回数量 Top K</strong>
            <span>控制回答时参考的论文片段数量，默认 3 条。</span>
          </div>
        </div>
        <div class="topk-options" role="radiogroup" aria-label="引用召回数量 Top K">
          <button
            v-for="option in topKOptions"
            :key="option"
            type="button"
            class="topk-option"
            :class="{ active: topK === option }"
            :aria-checked="topK === option"
            role="radio"
            @click="topK = option"
          >
            {{ option }}
          </button>
        </div>
      </div>
      <template #footer>
        <el-button @click="advancedVisible = false">完成</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.chat-composer {
  position: sticky;
  bottom: 0;
  z-index: 5;
  padding: 0 0 20px;
  background: #ffffff;
}

.composer-box {
  display: grid;
  gap: 10px;
  width: min(960px, calc(100% - 48px));
  margin: 0 auto;
  padding: 14px 14px 12px;
  border: 1px solid var(--app-border);
  border-radius: 28px;
  background: #ffffff;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.06);
}

.mode-switch {
  display: inline-flex;
  width: fit-content;
  padding: 3px;
  border: 1px solid #dbeafe;
  border-radius: 999px;
  background: #f8fbff;
}

.mode-switch button {
  height: 30px;
  padding: 0 14px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  font-weight: 900;
  transition: all 0.16s ease;
}

.mode-switch button.active {
  background: var(--app-primary);
  color: #ffffff;
  box-shadow: 0 6px 14px rgba(91, 124, 250, 0.2);
}

.composer-box :deep(.el-textarea__inner) {
  min-height: 56px !important;
  border: 0;
  background: transparent;
  box-shadow: none;
  color: var(--app-text);
  font-size: 15px;
  line-height: 1.7;
  padding: 2px 4px;
}

.composer-box :deep(.el-textarea__inner::placeholder) {
  color: #9ca3af;
}

.composer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.composer-left-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.composer-pill {
  height: 30px;
  padding: 0 10px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  background: #f9fafb;
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 700;
}

.composer-pill:hover {
  border-color: #d9e2ff;
  background: #f3f6ff;
  color: var(--app-primary);
}

.send-button {
  width: 40px;
  height: 40px;
  border: 0;
  background: var(--app-primary);
  box-shadow: 0 8px 18px rgba(91, 124, 250, 0.22);
}

.send-button.is-disabled,
.send-button.is-disabled:hover {
  background: #d1d5db;
  color: #ffffff;
  box-shadow: none;
}

.advanced-setting-card {
  display: grid;
  gap: 18px;
  padding: 16px;
  border: 1px solid var(--app-border);
  border-radius: 20px;
  background: #ffffff;
}

.advanced-setting-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.advanced-setting-header strong,
.advanced-setting-header span {
  display: block;
}

.advanced-setting-header strong {
  color: var(--app-text);
  font-size: 15px;
}

.advanced-setting-header span {
  margin-top: 5px;
  color: var(--app-text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.topk-options {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.topk-option {
  height: 38px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  background: #fff;
  color: #374151;
  cursor: pointer;
  font: inherit;
  font-weight: 700;
  transition: all 0.16s ease;
}

.topk-option:hover {
  border-color: #d9e2ff;
  color: var(--app-primary);
}

.topk-option.active {
  border-color: #d9e2ff;
  background: var(--app-primary-soft);
  color: var(--app-primary);
}

:global(.advanced-dialog .el-dialog) {
  border-radius: 24px;
}

:global(.advanced-dialog .el-dialog__title) {
  color: var(--app-text);
  font-weight: 800;
}

@media (max-width: 640px) {
  .chat-composer {
    padding-bottom: 12px;
  }

  .composer-box {
    width: calc(100% - 24px);
    border-radius: 24px;
  }
 
  .composer-actions {
    align-items: flex-end;
  }

  .hide-on-small {
    display: none;
  }

  .topk-options {
    grid-template-columns: repeat(5, minmax(42px, 1fr));
  }

  :global(.advanced-dialog) {
    width: calc(100% - 28px) !important;
  }
}
</style>