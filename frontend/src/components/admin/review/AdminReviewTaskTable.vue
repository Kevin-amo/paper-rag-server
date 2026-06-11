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
      <template #default="{ row }">{{ reviewerName(row) }}</template>
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
    <el-table-column label="操作" width="130" fixed="right" align="center">
      <template #default="{ row }">
        <div class="task-actions">
          <el-button text type="primary" @click="emit('open', row)">详情</el-button>
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
  overflow: hidden;
  border: 1px solid #dde3ee;
  border-radius: 10px;
}

.review-task-table :deep([class~="el-table__header-wrapper"] th) {
  background: #f8fafc;
  color: #475467;
  font-size: 12px;
  font-weight: 750;
}

.review-task-table :deep([class~="el-table__row"]:hover > td) {
  background: #f5f8ff;
}

.progress-cell {
  display: grid;
  grid-template-columns: minmax(90px, 1fr) auto;
  align-items: center;
  gap: 10px;
}

.progress-cell span {
  color: #667085;
  font-size: 12px;
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
  width: 32px;
  height: 32px;
  border: 0;
  padding: 0;
  background: transparent;
  color: var(--app-primary);
  cursor: pointer;
  font: inherit;
  line-height: 1;
  transition: color 0.16s ease;
}

.action-menu-trigger:hover,
.action-menu-trigger:focus {
  background: transparent;
  color: var(--app-primary-dark);
  outline: none;
  box-shadow: none;
}

.action-menu-trigger:focus-visible {
  outline: 2px solid rgba(0, 122, 255, 0.3);
  outline-offset: 2px;
}

:global([class~="review-task-actions-menu"]) {
  min-width: 120px;
}
</style>
