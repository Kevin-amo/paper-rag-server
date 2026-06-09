<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AdminShell from '../../components/admin/AdminShell.vue';
import AdminReviewTaskTable from '../../components/admin/review/AdminReviewTaskTable.vue';
import ReviewAssignmentDrawer from '../../components/admin/review/ReviewAssignmentDrawer.vue';
import ReviewConsensusDrawer from '../../components/admin/review/ReviewConsensusDrawer.vue';
import ReviewCriteriaPanel from '../../components/admin/review/ReviewCriteriaPanel.vue';
import { useAdminReviews } from '../../composables/useAdminReviews';
import type { AdminReviewTaskSummary, AssignReviewersPayload, UpdateReviewConsensusPayload } from '../../types';

const adminReviews = useAdminReviews();
const route = useRoute();
const router = useRouter();
const validTabs = ['tasks', 'assignments', 'criteria', 'archive'] as const;
type ReviewAdminTab = (typeof validTabs)[number];

const activeTab = ref<ReviewAdminTab>(normalizeTab(route.query.tab));
const assignmentVisible = ref(false);
const consensusVisible = ref(false);

const submittedTotal = computed(() => adminReviews.tasks.value.reduce((sum, task) => sum + task.submittedCount, 0));
const assignmentTotal = computed(() => adminReviews.tasks.value.reduce((sum, task) => sum + task.assignmentCount, 0));
const activeSectionTitle = computed(() => {
  const titles: Record<ReviewAdminTab, string> = {
    tasks: '评审任务',
    assignments: '评审员分配',
    criteria: '评审指标',
    archive: '共识/归档',
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
  return typeof tab === 'string' && validTabs.includes(tab as ReviewAdminTab) ? (tab as ReviewAdminTab) : 'tasks';
}

async function openTask(task: AdminReviewTaskSummary) {
  await adminReviews.openTask(task.id);
}

async function openAssignment(task: AdminReviewTaskSummary) {
  await Promise.all([adminReviews.openTask(task.id), adminReviews.loadReviewerLoads()]);
  assignmentVisible.value = true;
}

async function openConsensus(task: AdminReviewTaskSummary) {
  await adminReviews.openTask(task.id);
  consensusVisible.value = true;
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

onMounted(async () => {
  await Promise.all([adminReviews.loadTasks(0), adminReviews.loadReviewerLoads()]);
});
</script>

<template>
  <AdminShell :active="activeTab" :title="activeSectionTitle">
    <section class="summary-grid">
      <div class="summary-card">
        <span>任务总数</span>
        <strong>{{ adminReviews.total.value }}</strong>
      </div>
      <div class="summary-card">
        <span>当前页任务</span>
        <strong>{{ adminReviews.tasks.value.length }}</strong>
      </div>
      <div class="summary-card">
        <span>提交进度</span>
        <strong>{{ submittedTotal }}/{{ assignmentTotal }}</strong>
      </div>
      <div class="summary-card muted">
        <span>评审员</span>
        <strong>{{ adminReviews.reviewerLoads.value.length }}</strong>
      </div>
    </section>

    <section class="dashboard-card app-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="评审任务" name="tasks">
          <div class="section-note">
            <strong>评审任务</strong>
            <span>查看论文评审任务，按标题或状态筛选，并进入分配与共识操作。</span>
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
              @size-change="(nextSize: number) => { adminReviews.size.value = nextSize; adminReviews.loadTasks(0); }"
              @current-change="(nextPage: number) => adminReviews.loadTasks(nextPage - 1)"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane label="评审员分配" name="assignments">
          <div class="section-note">
            <strong>评审员分配</strong>
            <span>先查看评审员负载；需要分配时回到任务表点击“分配”。</span>
          </div>
          <div class="load-toolbar">
            <el-button @click="activeTab = 'tasks'">返回任务列表</el-button>
            <el-button type="primary" @click="adminReviews.loadReviewerLoads()">刷新负载</el-button>
          </div>
          <el-table :data="adminReviews.reviewerLoads.value" class="load-table">
            <el-table-column prop="reviewerUserId" label="Reviewer User ID" min-width="220" show-overflow-tooltip />
            <el-table-column prop="assignedCount" label="待评数量" width="140" />
            <el-table-column prop="reviewingCount" label="评审中" width="140" />
            <el-table-column prop="submittedCount" label="已提交" width="140" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="评审指标" name="criteria">
          <div class="section-note">
            <strong>评审指标</strong>
            <span>查看当前评审标准、评分维度和权重说明。</span>
          </div>
          <ReviewCriteriaPanel />
        </el-tab-pane>

        <el-tab-pane label="共识/归档" name="archive">
          <div class="section-note">
            <strong>共识/归档</strong>
            <span>从任务列表打开共识抽屉，完成共识确认；已确认任务作为归档记录保留。</span>
          </div>
          <div class="archive-helper">
            <p>当前共识与归档操作仍以任务为入口，避免重复维护两套列表。</p>
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
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 16px;
  background: #fff;
}

.summary-card.muted {
  background: #f9fafb;
}

.summary-card span {
  display: block;
  color: #6b7280;
  font-size: 13px;
}

.summary-card strong {
  display: block;
  margin-top: 8px;
  color: #111827;
  font-size: 26px;
  line-height: 1;
}

.dashboard-card {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 18px;
  background: #fff;
  box-shadow: none;
}

.section-note {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
  border-bottom: 1px solid #eef0f4;
  padding-bottom: 12px;
}

.section-note strong {
  color: #111827;
  font-size: 16px;
}

.section-note span {
  color: #6b7280;
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

.pagination-wrap,
.load-toolbar {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
}

.load-toolbar {
  margin: 0 0 16px;
}

.load-table {
  overflow: hidden;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.archive-helper {
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  padding: 22px;
  background: #f9fafb;
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
