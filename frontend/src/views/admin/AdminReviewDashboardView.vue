<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import MainLayout from '../../layouts/MainLayout.vue';
import PageHeader from '../../components/common/PageHeader.vue';
import AdminReviewTaskTable from '../../components/admin/review/AdminReviewTaskTable.vue';
import ReviewAssignmentDrawer from '../../components/admin/review/ReviewAssignmentDrawer.vue';
import ReviewConsensusDrawer from '../../components/admin/review/ReviewConsensusDrawer.vue';
import ReviewCriteriaPanel from '../../components/admin/review/ReviewCriteriaPanel.vue';
import { useAdminReviews } from '../../composables/useAdminReviews';
import { useAuth } from '../../composables/useAuth';
import type { AdminReviewTaskSummary, AssignReviewersPayload, UpdateReviewConsensusPayload } from '../../types';

const router = useRouter();
const auth = useAuth();
const adminReviews = useAdminReviews();
const activeTab = ref('tasks');
const assignmentVisible = ref(false);
const consensusVisible = ref(false);

const submittedTotal = computed(() => adminReviews.tasks.value.reduce((sum, task) => sum + task.submittedCount, 0));
const assignmentTotal = computed(() => adminReviews.tasks.value.reduce((sum, task) => sum + task.assignmentCount, 0));
const currentUserName = computed(() => auth.state.user?.displayName || auth.state.user?.username || '管理员');

async function handleLogout() {
  await auth.logout();
  await router.replace('/login');
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
  <MainLayout variant="dark">
    <PageHeader
      eyebrow="Admin Console"
      title="评审管理看板"
      description="集中查看论文评审任务、评审人负载与评审标准，支持分配评审人与确认最终共识。"
    >
      <template #actions>
        <el-tag type="danger" size="large">{{ currentUserName }} · 管理员</el-tag>
        <el-button @click="router.push('/admin')">用户管理</el-button>
        <el-button @click="handleLogout">退出登录</el-button>
      </template>
    </PageHeader>

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
        <span>评审人</span>
        <strong>{{ adminReviews.reviewerLoads.value.length }}</strong>
      </div>
    </section>

    <section class="dashboard-card app-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="评审任务" name="tasks">
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

        <el-tab-pane label="评审人负载" name="loads">
          <div class="load-toolbar">
            <el-button type="primary" @click="adminReviews.loadReviewerLoads()">刷新负载</el-button>
          </div>
          <el-table :data="adminReviews.reviewerLoads.value" class="load-table">
            <el-table-column prop="reviewerUserId" label="Reviewer User ID" min-width="220" show-overflow-tooltip />
            <el-table-column prop="assignedCount" label="待评数量" width="140" />
            <el-table-column prop="reviewingCount" label="评审中" width="140" />
            <el-table-column prop="submittedCount" label="已提交" width="140" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="评审标准" name="criteria">
          <ReviewCriteriaPanel />
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
  </MainLayout>
</template>

<style scoped>
.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.summary-card {
  border: 1px solid rgba(37, 99, 235, 0.11);
  border-radius: 20px;
  padding: 18px;
  background: rgba(255, 255, 255, 0.76);
  box-shadow: 0 14px 30px rgba(15, 23, 42, 0.08);
}

.summary-card.muted {
  background: rgba(248, 250, 252, 0.86);
}

.summary-card span {
  display: block;
  color: var(--app-text-muted);
  font-size: 13px;
}

.summary-card strong {
  display: block;
  margin-top: 8px;
  color: var(--app-text);
  font-size: 28px;
  line-height: 1;
}

.dashboard-card {
  padding: 24px;
}

.toolbar {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) 160px auto auto;
  gap: 10px;
  margin-bottom: 16px;
}

.status-select {
  width: 160px;
}

.pagination-wrap,
.load-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.load-table {
  overflow: hidden;
  border: 1px solid var(--app-border);
  border-radius: 18px;
}

@media (max-width: 860px) {
  .summary-grid,
  .toolbar {
    grid-template-columns: 1fr;
  }

  .status-select {
    width: 100%;
  }
}
</style>
