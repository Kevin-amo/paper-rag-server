<script lang="ts">
export default {
  name: 'ChatComposer',
};
</script>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { Promotion, Setting, Plus } from '@element-plus/icons-vue';

const props = defineProps<{
  loading?: boolean;
}>();

const emit = defineEmits<{
  submit: [payload: { question: string; topK?: number }];
  selectFiles: [];
}>();

const question = ref('');
const topK = ref(3);
const advancedVisible = ref(false);
const topKOptions = Array.from({ length: 10 }, (_, index) => index + 1);

const canSubmit = computed(() => question.value.trim().length > 0 && !props.loading);
const placeholder = computed(() => '告诉我你的研究目标，按 Enter 发送，Shift + Enter 换行');

function submitQuestion() {
  const content = question.value.trim();
  if (!content || props.loading) {
    return;
  }

  emit('submit', {
    question: content,
    topK: topK.value || undefined,
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
          <el-button
            class="composer-pill upload-pill"
            text
            :icon="Plus"
            title="上传论文"
            @click="emit('selectFiles')"
          >
            上传
          </el-button>
          <el-button class="composer-pill" text :icon="Setting" @click="advancedVisible = true">
            高级设置 · Top K {{ topK }}
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
  background: linear-gradient(180deg, rgba(255, 255, 255, 0), rgba(247, 248, 252, 0.72) 42%, rgba(247, 248, 252, 0.9));
}

.composer-box {
  display: grid;
  gap: 10px;
  width: min(960px, calc(100% - 48px));
  margin: 0 auto;
  padding: 14px 14px 12px;
  border: 1px solid rgba(255, 255, 255, 0.78);
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.78);
  box-shadow:
    0 18px 44px rgba(15, 23, 42, 0.12),
    inset 0 1px 0 rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(24px) saturate(170%);
  -webkit-backdrop-filter: blur(24px) saturate(170%);
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

.composer-box :deep(.el-textarea__inner:focus) {
  box-shadow: none;
}

.composer-box :deep(.el-textarea__inner::placeholder) {
  color: var(--app-text-subtle);
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
  border: 1px solid rgba(255, 255, 255, 0.74);
  border-radius: 999px;
  background: rgba(242, 242, 247, 0.74);
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 750;
}

.composer-pill:hover {
  border-color: rgba(0, 122, 255, 0.26);
  background: rgba(255, 255, 255, 0.88);
  color: var(--app-primary);
}

.send-button {
  width: 40px;
  height: 40px;
  border: 0;
  background: linear-gradient(135deg, var(--app-primary), #5ac8fa);
  box-shadow: 0 12px 24px rgba(0, 122, 255, 0.24);
}

.send-button:hover,
.send-button:focus {
  background: linear-gradient(135deg, #0a84ff, #64d2ff);
  box-shadow: 0 14px 28px rgba(0, 122, 255, 0.28);
}

.send-button.is-disabled,
.send-button.is-disabled:hover {
  background: rgba(174, 174, 178, 0.82);
  color: #ffffff;
  box-shadow: none;
}

.advanced-setting-card {
  display: grid;
  gap: 18px;
  padding: 16px;
  border: 1px solid rgba(255, 255, 255, 0.74);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.62);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.78);
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
  border: 1px solid rgba(209, 209, 214, 0.72);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  color: var(--app-text);
  cursor: pointer;
  font: inherit;
  font-weight: 750;
  transition: all 0.16s ease;
}

.topk-option:hover {
  border-color: rgba(0, 122, 255, 0.28);
  color: var(--app-primary);
}

.topk-option.active {
  border-color: rgba(0, 122, 255, 0.28);
  background: rgba(0, 122, 255, 0.1);
  color: var(--app-primary);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.72);
}

:global(.advanced-dialog .el-dialog) {
  border-radius: 26px;
}

:global(.advanced-dialog .el-dialog__title) {
  color: var(--app-text);
  font-weight: 850;
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