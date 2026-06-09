<script setup lang="ts">
import { computed } from 'vue';
import type { AdminReviewTaskDetail, ReviewAssignment, ReviewReport } from '../../../types';

const props = defineProps<{
  modelValue: boolean;
  taskDetail: AdminReviewTaskDetail | null;
  loading?: boolean;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
});

function formatDate(value: string | null | undefined) {
  return value ? new Date(value).toLocaleString() : '-';
}

function assignmentReviewer(assignment: ReviewAssignment) {
  return assignment.reviewerDisplayName || assignment.reviewerUsername || assignment.reviewerUserId || '-';
}

function reportReviewer(report: ReviewReport) {
  return report.reviewerDisplayName || report.reviewerUsername || report.reviewerUserId || '-';
}
</script>

<template>
  <el-drawer v-model="visible" size="min(820px, 96vw)" destroy-on-close>
    <template #header>
      <div>
        <span class="eyebrow">Review Task</span>
        <h3>评审任务详情</h3>
      </div>
    </template>

    <el-empty v-if="!taskDetail" description="请选择评审任务" />
    <div v-else class="detail-body" v-loading="loading">
      <section class="task-hero">
        <div>
          <span>{{ taskDetail.task.sourceId }}</span>
          <h4>{{ taskDetail.task.title }}</h4>
        </div>
        <el-tag size="large" effect="plain">{{ taskDetail.task.status }}</el-tag>
      </section>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="任务 ID">{{ taskDetail.task.id }}</el-descriptions-item>
        <el-descriptions-item label="文档 ID">{{ taskDetail.task.documentId }}</el-descriptions-item>
        <el-descriptions-item label="提交人">{{ taskDetail.task.submitterUserId }}</el-descriptions-item>
        <el-descriptions-item label="负责人">{{ taskDetail.task.reviewerUserId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="分配时间">{{ formatDate(taskDetail.task.assignedAt) }}</el-descriptions-item>
        <el-descriptions-item label="截止时间">{{ formatDate(taskDetail.task.dueAt) }}</el-descriptions-item>
        <el-descriptions-item label="完成时间">{{ formatDate(taskDetail.task.completedAt) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatDate(taskDetail.task.updatedAt) }}</el-descriptions-item>
      </el-descriptions>

      <section class="detail-section">
        <h4>评审分配</h4>
        <el-table :data="taskDetail.assignments" empty-text="暂无分配记录">
          <el-table-column label="评审人" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ assignmentReviewer(row) }}</template>
          </el-table-column>
          <el-table-column prop="role" label="角色" width="110" />
          <el-table-column prop="status" label="状态" width="130" />
          <el-table-column label="截止时间" min-width="170">
            <template #default="{ row }">{{ formatDate(row.dueAt) }}</template>
          </el-table-column>
          <el-table-column label="提交时间" min-width="170">
            <template #default="{ row }">{{ formatDate(row.submittedAt) }}</template>
          </el-table-column>
        </el-table>
      </section>

      <section class="detail-section">
        <h4>已提交报告</h4>
        <el-table :data="taskDetail.submittedReports" empty-text="暂无提交报告">
          <el-table-column label="评审人" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ reportReviewer(row) }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="140" />
          <el-table-column prop="totalScore" label="总分" width="100" />
          <el-table-column prop="finalRecommendation" label="最终建议" min-width="180" show-overflow-tooltip />
          <el-table-column label="更新时间" min-width="170">
            <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
          </el-table-column>
        </el-table>
      </section>

      <section class="detail-section">
        <h4>共识状态</h4>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="状态">{{ taskDetail.consensus?.status || '-' }}</el-descriptions-item>
          <el-descriptions-item label="负责人">
            {{
              taskDetail.consensus?.leadReviewerDisplayName ||
              taskDetail.consensus?.leadReviewerUsername ||
              taskDetail.consensus?.leadReviewerUserId ||
              '-'
            }}
          </el-descriptions-item>
          <el-descriptions-item label="最终得分">{{ taskDetail.consensus?.finalScore ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="确认时间">{{ formatDate(taskDetail.consensus?.confirmedAt) }}</el-descriptions-item>
          <el-descriptions-item label="最终建议" :span="2">
            {{ taskDetail.consensus?.finalRecommendation || '-' }}
          </el-descriptions-item>
        </el-descriptions>
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

h3,
h4 {
  margin: 0;
}

.detail-body {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.task-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  border: 1px solid var(--app-border);
  border-radius: 10px;
  padding: 16px;
  background: #f8fafc;
}

.task-hero span {
  display: block;
  margin-bottom: 6px;
  color: #667085;
  font-size: 12px;
}

.task-hero h4 {
  color: #101828;
  font-size: 18px;
  line-height: 1.4;
}

.detail-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

@media (max-width: 720px) {
  .task-hero {
    flex-direction: column;
  }
}
</style>
