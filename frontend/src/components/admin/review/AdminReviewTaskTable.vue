<script setup lang="ts">
import { MoreFilled } from '@element-plus/icons-vue';
import type { AdminReviewTaskSummary } from '../../../types';

type TaskActionCommand = 'assign' | 'consensus';

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

function reviewerName(task: AdminReviewTaskSummary) {
  return task.leadReviewerDisplayName || task.leadReviewerUsername || task.leadReviewerUserId || '-';
}

function handleTaskAction(command: TaskActionCommand, task: AdminReviewTaskSummary) {
  if (command === 'assign') {
    emit('assign', task);
    return;
  }
  emit('consensus', task);
}
</script>

<template>
  <el-table :data="tasks" v-loading="loading" class="review-task-table">
    <el-table-column prop="title" label="标题" min-width="240" show-overflow-tooltip />
    <el-table-column label="状态" width="120">
      <template #default="{ row }">
        <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column label="进度" min-width="160">
      <template #default="{ row }">
        <div class="progress-cell">
          <el-progress :percentage="progress(row)" :stroke-width="8" />
          <span>{{ row.submittedCount }}/{{ row.assignmentCount }}</span>
        </div>
      </template>
    </el-table-column>
    <el-table-column label="负责人" min-width="140" show-overflow-tooltip>
      <template #default="{ row }">{{ reviewerName(row) }}</template>
    </el-table-column>
    <el-table-column label="截止时间" min-width="160">
      <template #default="{ row }">{{ formatDate(row.dueAt) }}</template>
    </el-table-column>
    <el-table-column label="共识" width="130">
      <template #default="{ row }">
        <el-tag :type="row.consensusStatus === 'CONFIRMED' ? 'success' : 'info'" size="small">
          {{ row.consensusStatus || '-' }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="操作" width="120" fixed="right" align="center">
      <template #default="{ row }">
        <div class="task-actions">
          <el-button text type="primary" size="small" @click="emit('open', row)">详情</el-button>
          <el-dropdown
            trigger="click"
            placement="bottom-end"
            popper-class="review-task-actions-menu"
            @command="(command: TaskActionCommand) => handleTaskAction(command, row)"
          >
            <button class="action-menu-trigger" type="button" aria-label="更多操作" title="更多操作">
              <el-icon><MoreFilled /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="assign">兜底处理</el-dropdown-item>
                <el-dropdown-item command="consensus">综评</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </template>
    </el-table-column>
  </el-table>
</template>

<style scoped>
.review-task-table {
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  overflow: hidden;
}

.progress-cell {
  display: grid;
  grid-template-columns: minmax(80px, 1fr) auto;
  align-items: center;
  gap: 10px;
}

.progress-cell span {
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 500;
}

.task-actions {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  width: 100%;
}

.action-menu-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 0;
  padding: 0;
  background: transparent;
  color: var(--app-text-subtle);
  cursor: pointer;
  border-radius: var(--app-radius-xs);
  transition: all 0.15s ease;
}

.action-menu-trigger:hover {
  background: var(--app-surface-soft);
  color: var(--app-primary);
}

.action-menu-trigger:focus-visible {
  outline: 2px solid var(--app-primary);
  outline-offset: 2px;
}

:global([class~="review-task-actions-menu"]) {
  min-width: 120px;
}
</style>
