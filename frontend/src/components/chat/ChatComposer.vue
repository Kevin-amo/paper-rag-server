<script lang="ts">
export default {
  name: 'ChatComposer',
};
</script>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { Promotion, Setting } from '@element-plus/icons-vue';

const props = defineProps<{
  loading?: boolean;
}>();

const emit = defineEmits<{
  submit: [payload: { question: string; topK?: number }];
}>();

const question = ref('');
const topK = ref(3);
const advancedVisible = ref(false);
const topKOptions = Array.from({ length: 10 }, (_, index) => index + 1);

const canSubmit = computed(() => question.value.trim().length > 0 && !props.loading);

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
        placeholder="向论文知识库提问，按 Enter 发送，Shift + Enter 换行"
        @keydown="handleKeydown"
      />
      <div class="composer-actions">
        <el-button text :icon="Setting" @click="advancedVisible = true">
          高级设置
        </el-button>
        <el-button type="primary" size="large" :loading="props.loading" :disabled="!canSubmit" :icon="Promotion" @click="submitQuestion">
          发送
        </el-button>
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
  padding: 14px 22px 20px;
  border-top: 1px solid rgba(226, 232, 240, 0.9);
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(16px);
}

.composer-box {
  display: grid;
  gap: 12px;
  padding: 13px;
  border: 1px solid rgba(37, 99, 235, 0.16);
  border-radius: 22px;
  background: #fff;
  box-shadow: 0 16px 38px rgba(15, 23, 42, 0.08);
}

.composer-box :deep(.el-textarea__inner) {
  border: 0;
  box-shadow: none;
  color: #0f172a;
  font-size: 15px;
  line-height: 1.7;
}

.composer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.composer-actions .el-button--primary {
  min-width: 104px;
  border-radius: 14px;
  font-weight: 800;
}

.advanced-setting-card {
  display: grid;
  gap: 18px;
  padding: 16px;
  border: 1px solid rgba(37, 99, 235, 0.12);
  border-radius: 18px;
  background: linear-gradient(180deg, #f8fbff 0%, #ffffff 100%);
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
  color: #172554;
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
  border: 1px solid rgba(148, 163, 184, 0.32);
  border-radius: 12px;
  background: #fff;
  color: #334155;
  cursor: pointer;
  font: inherit;
  font-weight: 700;
  transition: all 0.18s ease;
}

.topk-option:hover {
  border-color: rgba(37, 99, 235, 0.42);
  color: #1d4ed8;
  transform: translateY(-1px);
}

.topk-option.active {
  border-color: #2563eb;
  background: #eff6ff;
  color: #1d4ed8;
  box-shadow: inset 0 0 0 1px rgba(37, 99, 235, 0.32);
}

:global(.advanced-dialog .el-dialog) {
  border-radius: 22px;
}

:global(.advanced-dialog .el-dialog__header) {
  padding-bottom: 10px;
}

:global(.advanced-dialog .el-dialog__title) {
  color: #0f172a;
  font-weight: 800;
}

@media (max-width: 640px) {
  .chat-composer {
    padding: 12px;
  }

  .composer-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .topk-options {
    grid-template-columns: repeat(5, minmax(42px, 1fr));
  }

  :global(.advanced-dialog) {
    width: calc(100% - 28px) !important;
  }
}
</style>