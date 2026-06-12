<script setup lang="ts">
import { statusLabel } from '../../../constants/review';
import { formatDate } from '../../../utils/format';
import type { ReviewTask, ReviewAssignmentStatus } from '../../../types';

defineProps<{
  tasks: ReviewTask[];
  selectedTaskId: string | null;
  loading: boolean;
  keyword: string;
  statusFilter: ReviewAssignmentStatus | '';
  pagination: { page: number; size: number; total: number };
}>();

defineEmits<{
  'update:keyword': [value: string];
  'update:statusFilter': [value: ReviewAssignmentStatus | ''];
  search: [];
  select: [taskId: string];
  'page-change': [page: number];
}>();
</script>

<template>
  <aside class="task-panel">
    <div class="panel-header">
      <h2>评审任务</h2>
      <el-button size="small" :loading="loading" @click="$emit('search')">刷新</el-button>
    </div>

    <div class="task-toolbar">
      <el-input
        :model-value="keyword"
        clearable
        size="small"
        placeholder="搜索标题"
        @update:model-value="$emit('update:keyword', $event)"
        @keyup.enter="$emit('search')"
      />
      <el-select
        :model-value="statusFilter"
        clearable
        size="small"
        placeholder="状态"
        @update:model-value="$emit('update:statusFilter', $event)"
      >
        <el-option label="待评审" value="ASSIGNED" />
        <el-option label="评审中" value="REVIEWING" />
        <el-option label="已提交" value="SUBMITTED" />
        <el-option label="已退回" value="RETURNED" />
      </el-select>
      <el-button size="small" type="primary" @click="$emit('search')">筛选</el-button>
    </div>

    <div v-loading="loading" class="task-list">
      <button
        v-for="task in tasks"
        :key="task.id"
        class="task-item"
        :class="{ active: selectedTaskId === task.id }"
        type="button"
        @click="$emit('select', task.id)"
      >
        <div class="task-item-top">
          <span class="task-title">{{ task.title }}</span>
          <span class="task-status-badge" :class="task.currentAssignment?.status ?? task.status">
            {{ statusLabel(task.currentAssignment?.status ?? task.status) }}
          </span>
        </div>
        <div class="task-item-bottom">
          <span class="task-id">{{ task.sourceId }}</span>
          <span class="task-date">{{ formatDate(task.updatedAt) }}</span>
        </div>
      </button>
      <el-empty v-if="!loading && !tasks.length" description="暂无评审任务" />
    </div>

    <el-pagination
      v-if="pagination.total > pagination.size"
      small
      background
      layout="prev, pager, next"
      :total="pagination.total"
      :page-size="pagination.size"
      :current-page="pagination.page + 1"
      @current-change="(page: number) => $emit('page-change', page)"
    />
  </aside>
</template>

<style scoped>
.task-panel {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: var(--app-surface);
  padding: 16px;
  gap: 12px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--app-border);
}

.panel-header h2 {
  margin: 0;
  color: var(--app-text);
  font-size: 15px;
  font-weight: 700;
}

.task-toolbar {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-height: 400px;
  overflow-y: auto;
}

.task-item {
  width: 100%;
  border: 1px solid transparent;
  border-radius: var(--app-radius-sm);
  padding: 12px 14px;
  background: transparent;
  color: inherit;
  cursor: pointer;
  text-align: left;
  transition: all 0.15s ease;
}

.task-item:hover {
  background: var(--app-surface-soft);
  border-color: var(--app-border);
}

.task-item.active {
  background: var(--app-primary-soft);
  border-color: var(--app-primary);
}

.task-item-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.task-title {
  color: var(--app-text);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.task-status-badge {
  flex-shrink: 0;
  padding: 2px 8px;
  border-radius: var(--app-radius-xs);
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}

.task-status-badge.ASSIGNED {
  background: var(--app-primary-soft);
  color: var(--app-primary);
}

.task-status-badge.REVIEWING {
  background: var(--app-warning-soft);
  color: #b45309;
}

.task-status-badge.SUBMITTED {
  background: var(--app-success-soft);
  color: #047857;
}

.task-status-badge.RETURNED {
  background: var(--app-danger-soft);
  color: var(--app-danger);
}

.task-item-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 8px;
}

.task-id,
.task-date {
  color: var(--app-text-subtle);
  font-size: 12px;
}
</style>
