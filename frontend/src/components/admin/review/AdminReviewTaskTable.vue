<script setup lang="ts">
import type { AdminReviewTaskSummary } from '../../../types';

defineProps<{
  tasks: AdminReviewTaskSummary[];
  loading: boolean;
}>();

const emit = defineEmits<{
  open: [task: AdminReviewTaskSummary];
  assign: [task: AdminReviewTaskSummary];
  consensus: [task: AdminReviewTaskSummary];
}>();

function formatDate(value: string | null) {
  return value ? new Date(value).toLocaleString() : '-';
}

function progress(task: AdminReviewTaskSummary) {
  if (!task.assignmentCount) {
    return 0;
  }
  return Math.round((task.submittedCount / task.assignmentCount) * 100);
}

function statusType(status: string) {
  if (status === 'COMPLETED') return 'success';
  if (status === 'FAILED') return 'danger';
  if (status === 'REVIEWING') return 'warning';
  return 'info';
}
</script>

<template>
  <el-table :data="tasks" v-loading="loading" class="review-task-table">
    <el-table-column prop="title" label="标题" min-width="260" show-overflow-tooltip />
    <el-table-column label="状态" width="130">
      <template #default="{ row }">
        <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column label="进度" min-width="180">
      <template #default="{ row }">
        <div class="progress-cell">
          <el-progress :percentage="progress(row)" :stroke-width="10" />
          <span>{{ row.submittedCount }}/{{ row.assignmentCount }}</span>
        </div>
      </template>
    </el-table-column>
    <el-table-column label="负责人" min-width="180" show-overflow-tooltip>
      <template #default="{ row }">{{ row.leadReviewerUserId || '-' }}</template>
    </el-table-column>
    <el-table-column label="截止时间" min-width="180">
      <template #default="{ row }">{{ formatDate(row.dueAt) }}</template>
    </el-table-column>
    <el-table-column label="共识状态" width="150">
      <template #default="{ row }">
        <el-tag :type="row.consensusStatus === 'CONFIRMED' ? 'success' : 'info'">
          {{ row.consensusStatus || '-' }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="操作" width="250" fixed="right">
      <template #default="{ row }">
        <el-button text type="primary" @click="emit('open', row)">详情</el-button>
        <el-button text type="primary" @click="emit('assign', row)">分配</el-button>
        <el-button text type="primary" @click="emit('consensus', row)">共识</el-button>
      </template>
    </el-table-column>
  </el-table>
</template>

<style scoped>
.review-task-table {
  overflow: hidden;
  border: 1px solid var(--app-border);
  border-radius: 18px;
}

.progress-cell {
  display: grid;
  grid-template-columns: minmax(90px, 1fr) auto;
  align-items: center;
  gap: 10px;
}

.progress-cell span {
  color: var(--app-text-muted);
  font-size: 12px;
}
</style>
