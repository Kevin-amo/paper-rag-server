<script setup lang="ts">
import { computed, reactive, watch } from 'vue';
import type { AdminReviewTaskDetail, UpdateReviewConsensusPayload } from '../../../types';

const props = defineProps<{
  modelValue: boolean;
  taskDetail: AdminReviewTaskDetail | null;
  loading?: boolean;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
  recalc: [taskId: string];
  save: [taskId: string, payload: UpdateReviewConsensusPayload];
  confirm: [taskId: string];
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
});

const form = reactive({
  finalScore: null as number | null,
  finalRecommendation: '',
});

const consensus = computed(() => props.taskDetail?.consensus ?? null);
const canConfirm = computed(() => Boolean(props.taskDetail?.task.id && consensus.value && consensus.value.status !== 'CONFIRMED'));

watch(
  consensus,
  (value) => {
    form.finalScore = value?.finalScore ?? null;
    form.finalRecommendation = value?.finalRecommendation ?? '';
  },
  { immediate: true },
);

function prettyJson(value: unknown) {
  if (value === null || value === undefined) {
    return '-';
  }
  return JSON.stringify(value, null, 2);
}

function taskId() {
  return props.taskDetail?.task.id;
}

function leadReviewerName() {
  return (
    consensus.value?.leadReviewerDisplayName ||
    consensus.value?.leadReviewerUsername ||
    consensus.value?.leadReviewerUserId ||
    '-'
  );
}

function confirmedByName() {
  return (
    consensus.value?.confirmedByDisplayName ||
    consensus.value?.confirmedByUsername ||
    consensus.value?.confirmedByUserId ||
    '-'
  );
}

function handleSave() {
  const id = taskId();
  if (!id) return;
  emit('save', id, {
    finalScore: form.finalScore,
    finalRecommendation: form.finalRecommendation || null,
  });
}
</script>

<template>
  <el-drawer v-model="visible" size="min(760px, 96vw)" destroy-on-close>
    <template #header>
      <div>
        <span class="eyebrow">Review Consensus</span>
        <h3>评审共识</h3>
      </div>
    </template>

    <el-empty v-if="!taskDetail" description="请选择评审任务" />
    <div v-else class="consensus-body" v-loading="loading">
      <el-alert :title="taskDetail.task.title" type="info" :closable="false" />

      <div class="action-row">
        <el-button type="primary" plain @click="emit('recalc', taskDetail.task.id)">重新计算</el-button>
        <el-button type="primary" @click="handleSave">保存最终意见</el-button>
        <el-popconfirm title="确认后将锁定当前评审共识，是否继续？" @confirm="emit('confirm', taskDetail.task.id)">
          <template #reference>
            <el-button type="success" :disabled="!canConfirm">确认共识</el-button>
          </template>
        </el-popconfirm>
      </div>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="共识状态">{{ consensus?.status || '-' }}</el-descriptions-item>
        <el-descriptions-item label="负责人">{{ leadReviewerName() }}</el-descriptions-item>
        <el-descriptions-item label="确认人">{{ confirmedByName() }}</el-descriptions-item>
        <el-descriptions-item label="确认时间">{{ consensus?.confirmedAt || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-form label-position="top" class="final-form">
        <el-form-item label="最终得分">
          <el-input-number v-model="form.finalScore" :min="0" :max="100" :precision="2" controls-position="right" />
        </el-form-item>
        <el-form-item label="最终建议">
          <el-input v-model="form.finalRecommendation" type="textarea" :rows="4" placeholder="请输入最终录用/修改/拒稿建议" />
        </el-form-item>
      </el-form>

      <section class="json-section">
        <h4>Score Summary</h4>
        <pre>{{ prettyJson(consensus?.scoreSummary) }}</pre>
      </section>
      <section class="json-section">
        <h4>Disagreement Items</h4>
        <pre>{{ prettyJson(consensus?.disagreementItems) }}</pre>
      </section>
    </div>
  </el-drawer>
</template>

<style scoped>
.eyebrow {
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

h3 {
  margin: 4px 0 0;
}

.consensus-body,
.final-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.json-section h4 {
  margin: 0 0 8px;
}

pre {
  max-height: 260px;
  overflow: auto;
  margin: 0;
  padding: 14px;
  border: 1px solid var(--app-border);
  border-radius: 14px;
  background: #0f172a;
  color: #dbeafe;
  font-size: 12px;
  line-height: 1.5;
}
</style>
