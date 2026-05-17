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
    <el-collapse-transition>
      <div v-if="advancedVisible" class="advanced-panel">
        <div>
          <strong>高级设置</strong>
          <span>调整检索召回数量。默认配置适合大多数论文问答场景。</span>
        </div>
        <el-form-item label="引用召回数量 Top K">
          <el-input-number v-model="topK" :min="1" :max="10" />
        </el-form-item>
      </div>
    </el-collapse-transition>

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
        <el-button text :icon="Setting" @click="advancedVisible = !advancedVisible">
          高级设置
        </el-button>
        <el-button type="primary" size="large" :loading="props.loading" :disabled="!canSubmit" :icon="Promotion" @click="submitQuestion">
          发送
        </el-button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.chat-composer {
  padding: 14px 22px 20px;
  border-top: 1px solid rgba(226, 232, 240, 0.9);
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(16px);
}

.advanced-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
  padding: 12px 14px;
  border: 1px solid rgba(37, 99, 235, 0.12);
  border-radius: 16px;
  background: #f8fbff;
}

.advanced-panel strong,
.advanced-panel span {
  display: block;
}

.advanced-panel strong {
  color: #172554;
  font-size: 14px;
}

.advanced-panel span {
  margin-top: 4px;
  color: var(--app-text-muted);
  font-size: 12px;
}

.advanced-panel :deep(.el-form-item) {
  margin-bottom: 0;
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

@media (max-width: 640px) {
  .chat-composer {
    padding: 12px;
  }

  .advanced-panel,
  .composer-actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>