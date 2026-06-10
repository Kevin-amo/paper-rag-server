<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AdminShell from '../../components/admin/AdminShell.vue';
import AdminReviewTaskTable from '../../components/admin/review/AdminReviewTaskTable.vue';
import ReviewAssignmentDrawer from '../../components/admin/review/ReviewAssignmentDrawer.vue';
import ReviewBatchGroupPanel from '../../components/admin/review/ReviewBatchGroupPanel.vue';
import ReviewConsensusDrawer from '../../components/admin/review/ReviewConsensusDrawer.vue';
import ReviewCriteriaPanel from '../../components/admin/review/ReviewCriteriaPanel.vue';
import ReviewTaskDetailDrawer from '../../components/admin/review/ReviewTaskDetailDrawer.vue';
import { useAdminReviews } from '../../composables/useAdminReviews';
import type { AdminReviewTaskSummary, AssignReviewersPayload, UpdateReviewConsensusPayload } from '../../types';

const adminReviews = useAdminReviews();
const route = useRoute();
const router = useRouter();
const validTabs = ['config', 'tasks', 'criteria', 'archive'] as const;
type ReviewAdminTab = (typeof validTabs)[number];

const activeTab = ref<ReviewAdminTab>(normalizeTab(route.query.tab));
const detailVisible = ref(false);
const assignmentVisible = ref(false);
const consensusVisible = ref(false);

const submittedTotal = computed(() => adminReviews.tasks.value.reduce((sum, task) => sum + task.submittedCount, 0));
const assignmentTotal = computed(() => adminReviews.tasks.value.reduce((sum, task) => sum + task.assignmentCount, 0));
const activeSectionTitle = computed(() => {
  const titles: Record<ReviewAdminTab, string> = {
    config: '批次与小组',
    tasks: '全局进度',
    criteria: '评审指标',
    archive: '结果查看/兜底确认',
  };
  return titles[activeTab.value];
});

watch(
  () => route.query.tab,
  (tab) => {
    activeTab.value = normalizeTab(tab);
  },
);

watch(activeTab, async (tab) => {
  if (route.query.tab === tab) return;
  await router.replace({ path: '/admin/reviews', query: { ...route.query, tab } });
});

function normalizeTab(tab: unknown): ReviewAdminTab {
  return typeof tab === 'string' && validTabs.includes(tab as ReviewAdminTab) ? (tab as ReviewAdminTab) : 'config';
}

async function openTask(task: AdminReviewTaskSummary) {
  const detail = await adminReviews.openTask(task.id);
  detailVisible.value = Boolean(detail);
}

async function openAssignment(task: AdminReviewTaskSummary) {
  const [detail] = await Promise.all([adminReviews.openTask(task.id), adminReviews.loadReviewerLoads()]);
  assignmentVisible.value = Boolean(detail);
}

async function openConsensus(task: AdminReviewTaskSummary) {
  const detail = await adminReviews.openTask(task.id);
  consensusVisible.value = Boolean(detail);
}

async function saveAssignments(taskId: string, payload: AssignReviewersPayload) {
  await adminReviews.saveAssignments(taskId, payload);
  assignmentVisible.value = false;
}

async function saveConsensus(taskId: string, payload: UpdateReviewConsensusPayload) {
  await adminReviews.saveConsensus(taskId, payload);
}

async function confirmConsensus(taskId: string) {
  await adminReviews.confirm(taskId);
}

function handlePageSizeChange(nextSize: number) {
  adminReviews.size.value = nextSize;
  adminReviews.loadTasks(0);
}

function handlePageChange(nextPage: number) {
  adminReviews.loadTasks(nextPage - 1);
}

onMounted(async () => {
  await Promise.all([adminReviews.loadTasks(0), adminReviews.loadReviewerLoads()]);
});
</script>

<template>
  <AdminShell :active="activeTab" :title="activeSectionTitle">
    <section class="summary-grid">
      <div class="summary-card accent-blue">
        <span class="metric-icon">T</span>
        <div>
          <span>任务总数</span>
          <strong>{{ adminReviews.total.value }}</strong>
        </div>
      </div>
      <div class="summary-card accent-indigo">
        <span class="metric-icon">P</span>
        <div>
          <span>当前页任务</span>
          <strong>{{ adminReviews.tasks.value.length }}</strong>
        </div>
      </div>
      <div class="summary-card accent-green">
        <span class="metric-icon">S</span>
        <div>
          <span>提交进度</span>
          <strong>{{ submittedTotal }}/{{ assignmentTotal }}</strong>
        </div>
      </div>
      <div class="summary-card accent-amber">
        <span class="metric-icon">R</span>
        <div>
          <span>评审员</span>
          <strong>{{ adminReviews.reviewerLoads.value.length }}</strong>
        </div>
      </div>
    </section>

    <section class="dashboard-card app-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="批次与小组" name="config">
          <div class="section-note">
            <strong>批次与小组</strong>
            <span>配置评审批次、评审小组、组长和组内成员；普通评审任务分配后续交由组长处理。</span>
          </div>
          <ReviewBatchGroupPanel />
        </el-tab-pane>

        <el-tab-pane label="全局进度" name="tasks">
          <div class="section-note">
            <strong>全局进度</strong>
            <span>查看所有评审任务进度；普通分配主流程由组长处理，admin 仅保留异常兜底改派入口。</span>
          </div>
          <div class="toolbar">
            <el-input
              v-model="adminReviews.keyword.value"
              clearable
              placeholder="搜索标题 / 任务"
              @keyup.enter="adminReviews.loadTasks(0)"
            />
            <el-select v-model="adminReviews.status.value" clearable placeholder="任务状态" class="status-select">
              <el-option label="待分配" value="PENDING_ASSIGNMENT" />
              <el-option label="已分配" value="ASSIGNED" />
              <el-option label="评审中" value="IN_REVIEW" />
              <el-option label="已提交" value="SUBMITTED" />
              <el-option label="共识已确认" value="CONSENSUS_CONFIRMED" />
              <el-option label="旧状态：待处理" value="PENDING" />
              <el-option label="旧状态：评审中" value="REVIEWING" />
              <el-option label="旧状态：已完成" value="COMPLETED" />
            </el-select>
            <el-button @click="adminReviews.loadTasks(0)">搜索</el-button>
            <el-button type="primary" @click="adminReviews.loadTasks(adminReviews.page.value)">刷新</el-button>
          </div>

          <AdminReviewTaskTable
            :tasks="adminReviews.tasks.value"
            :loading="adminReviews.loading.value"
            @open="openTask"
            @assign="openAssignment"
            @consensus="openConsensus"
          />

          <div class="pagination-wrap">
            <el-pagination
              background
              layout="total, sizes, prev, pager, next"
              :total="adminReviews.total.value"
              :page-size="adminReviews.size.value"
              :current-page="adminReviews.page.value + 1"
              :page-sizes="[10, 20, 50]"
              @size-change="handlePageSizeChange"
              @current-change="handlePageChange"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane label="评审指标" name="criteria">
          <div class="section-note">
            <strong>评审指标</strong>
            <span>查看当前评审标准、评分维度和权重说明。</span>
          </div>
          <ReviewCriteriaPanel />
        </el-tab-pane>

        <el-tab-pane label="结果查看" name="archive">
          <div class="section-note">
            <strong>结果查看 / 兜底确认</strong>
            <span>最终评分主流程由组长处理；admin 在异常场景下从任务列表进入兜底确认。</span>
          </div>
          <div class="archive-helper">
            <p>结果与共识操作仍以任务为入口，避免重复维护两套列表。</p>
            <el-button type="primary" @click="activeTab = 'tasks'">查看评审任务</el-button>
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>

    <ReviewAssignmentDrawer
      v-model="assignmentVisible"
      :task="adminReviews.selectedTask.value"
      :reviewer-loads="adminReviews.reviewerLoads.value"
      @submit="saveAssignments"
    />
    <ReviewTaskDetailDrawer
      v-model="detailVisible"
      :task-detail="adminReviews.selectedTask.value"
      :loading="adminReviews.loading.value"
    />
    <ReviewConsensusDrawer
      v-model="consensusVisible"
      :task-detail="adminReviews.selectedTask.value"
      :loading="adminReviews.loading.value"
      @recalc="adminReviews.recalc"
      @save="saveConsensus"
      @confirm="confirmConsensus"
    />
  </AdminShell>
</template>

<style scoped>
.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.summary-card {
  display: flex;
  align-items: center;
  gap: 14px;
  border: 1px solid #dde3ee;
  border-radius: 10px;
  padding: 16px;
  background: #fff;
  box-shadow: 0 10px 26px rgba(16, 24, 40, 0.04);
}

.metric-icon {
  width: 42px;
  height: 42px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 850;
}

.summary-card span {
  display: block;
  color: #667085;
  font-size: 13px;
}

.summary-card strong {
  display: block;
  margin-top: 7px;
  color: #101828;
  font-size: 26px;
  line-height: 1;
}

.accent-blue .metric-icon {
  background: #eaf2ff;
  color: #155eef;
}

.accent-indigo .metric-icon {
  background: #eef2ff;
  color: #4f46e5;
}

.accent-green .metric-icon {
  background: #e8f8ef;
  color: #099250;
}

.accent-amber .metric-icon {
  background: #fff4df;
  color: #dc6803;
}

.dashboard-card {
  border: 1px solid #dde3ee;
  border-radius: 10px;
  padding: 18px;
  background: #fff;
  box-shadow: 0 12px 30px rgba(16, 24, 40, 0.05);
}

.section-note {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
  border-bottom: 1px solid #edf1f7;
  padding-bottom: 12px;
}

.section-note strong {
  color: #101828;
  font-size: 16px;
}

.section-note span {
  color: #667085;
  font-size: 13px;
}

.toolbar {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) 180px auto auto;
  gap: 10px;
  margin-bottom: 16px;
}

.status-select {
  width: 180px;
}

.toolbar :deep([class~="el-input__wrapper"]),
.toolbar :deep([class~="el-select__wrapper"]) {
  min-height: 38px;
  border-radius: 9px;
  box-shadow: 0 0 0 1px #d0d7e2 inset;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
}

.archive-helper {
  border: 1px dashed #c4cfdd;
  border-radius: 10px;
  padding: 22px;
  background: #f8fafc;
}

.archive-helper p {
  margin: 0 0 14px;
  color: #4b5563;
}

@media (max-width: 980px) {
  .summary-grid,
  .toolbar {
    grid-template-columns: 1fr;
  }

  .status-select {
    width: 100%;
  }

  .section-note {
    align-items: flex-start;
    flex-direction: column;
    gap: 6px;
  }
}
</style>
