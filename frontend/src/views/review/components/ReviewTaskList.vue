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
  <aside class="task-panel app-card">
    <div class="panel-header">
      <div>
        <p>My Assignments</p>
        <h2>我的评审任务</h2>
      </div>
      <el-button :loading="loading" @click="$emit('search')">刷新</el-button>
    </div>

    <div class="task-toolbar">
      <el-input
        :model-value="keyword"
        clearable
        placeholder="搜索标题 / 文档标识"
        @update:model-value="$emit('update:keyword', $event)"
        @keyup.enter="$emit('search')"
      />
      <el-select
        :model-value="statusFilter"
        clearable
        placeholder="状态"
        @update:model-value="$emit('update:statusFilter', $event)"
      >
        <el-option label="待评审" value="ASSIGNED" />
        <el-option label="评审中" value="REVIEWING" />
        <el-option label="已提交" value="SUBMITTED" />
        <el-option label="已退回" value="RETURNED" />
      </el-select>
      <el-button type="primary" @click="$emit('search')">筛选</el-button>
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
        <span class="task-title">{{ task.title }}</span>
        <span class="task-meta">{{ task.sourceId }}</span>
        <span class="task-bottom">
          <el-tag size="small" effect="plain">{{ statusLabel(task.currentAssignment?.status ?? task.status) }}</el-tag>
          <span>{{ formatDate(task.updatedAt) }}</span>
        </span>
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
  padding: 18px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding-bottom: 14px;
  border-bottom: 1px solid #edf1f7;
}

.panel-header p {
  margin: 0;
  color: #155eef;
  font-size: 12px;
  font-weight: 850;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.panel-header h2 {
  margin: 4px 0 0;
  color: #101828;
}

.task-toolbar {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
  margin: 16px 0;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 420px;
}

.task-item {
  position: relative;
  width: 100%;
  border: 1px solid transparent;
  border-radius: 9px;
  padding: 12px 12px 12px 14px;
  background: #fff;
  box-shadow: inset 0 -1px 0 #edf1f7;
  color: inherit;
  cursor: pointer;
  text-align: left;
  transition: all 0.18s ease;
}

.task-item:hover {
  border-color: #c7d7fe;
  background: #f5f8ff;
}

.task-item.active {
  border-color: #b2ccff;
  background: #f0f6ff;
}

.task-item.active::before {
  position: absolute;
  left: 0;
  top: 10px;
  bottom: 10px;
  width: 3px;
  border-radius: 0 999px 999px 0;
  background: #155eef;
  content: '';
}

.task-title,
.task-meta,
.task-bottom {
  display: block;
}

.task-title {
  color: #101828;
  font-weight: 750;
}

.task-meta {
  color: #667085;
  font-size: 12px;
}

.task-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 12px;
}

.task-bottom span:last-child {
  color: #667085;
  font-size: 12px;
}
</style>
