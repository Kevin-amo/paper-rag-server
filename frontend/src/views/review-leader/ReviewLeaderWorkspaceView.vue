<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import MainLayout from '../../layouts/MainLayout.vue';
import PageHeader from '../../components/common/PageHeader.vue';
import { getErrorMessage } from '../../api/http';
import {
  assignLeaderTask,
  confirmLeaderTaskConsensus,
  getLeaderTaskConsensus,
  listLeaderGroupMembers,
  listLeaderGroups,
  listLeaderGroupTasks,
  recalculateLeaderTaskConsensus,
  updateLeaderTaskConsensus,
  listLeaderTaskReports,
} from '../../api/reviewLeader';
import { useAuth } from '../../composables/useAuth';
import type {
  AdminReviewTaskSummary,
  ReviewConsensus,
  ReviewGroup,
  ReviewGroupMember,
  ReviewReport,
  ReviewScoreItem,
} from '../../types';

const router = useRouter();
const auth = useAuth();

const groups = ref<ReviewGroup[]>([]);
const members = ref<ReviewGroupMember[]>([]);
const tasks = ref<AdminReviewTaskSummary[]>([]);
const reports = ref<ReviewReport[]>([]);
const consensus = ref<ReviewConsensus | null>(null);
const selectedGroupId = ref<string>('');
const selectedTaskId = ref<string>('');
const loading = ref(false);
const scopeLoading = ref(false);
const detailLoading = ref(false);
const assigning = ref(false);
const consensusSaving = ref(false);
const consensusRecalculating = ref(false);
const consensusConfirming = ref(false);
const assignDialogVisible = ref(false);
const assignmentTask = ref<AdminReviewTaskSummary | null>(null);
const selectedReviewerIds = ref<string[]>([]);
const assignmentDueAt = ref<string | null>(null);
const consensusForm = reactive({
  finalScore: null as number | null,
  finalRecommendation: '',
});

const currentUserName = computed(() => auth.state.user?.displayName || auth.state.user?.username || '评审组长');
const selectedGroup = computed(() => groups.value.find((group) => group.id === selectedGroupId.value) ?? null);
const selectedTask = computed(() => tasks.value.find((task) => task.id === selectedTaskId.value) ?? null);
const reviewerMembers = computed(() => members.value.filter((member) => member.memberRole === 'REVIEWER' && member.status === 'ACTIVE'));
const unassignedCount = computed(() => tasks.value.filter((task) => isUnassignedTask(task)).length);
const submittedCount = computed(() => tasks.value.filter((task) => task.status === 'SUBMITTED').length);
const confirmedCount = computed(() => tasks.value.filter((task) => task.status === 'CONSENSUS_CONFIRMED').length);

const taskStatusLabels: Record<string, string> = {
  PENDING: '待分配',
  PENDING_ASSIGNMENT: '待分配',
  ASSIGNED: '已分配',
  REVIEWING: '评审中',
  IN_REVIEW: '评审中',
  SUBMITTED: '待最终评分',
  COMPLETED: '已完成',
  CONSENSUS_CONFIRMED: '最终已确认',
};

const assignmentStatusLabels: Record<string, string> = {
  ASSIGNED: '待评审',
  REVIEWING: '评审中',
  SUBMITTED: '已提交',
  RETURNED: '已退回',
  CANCELLED: '已取消',
};

const consensusStatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  IN_DISCUSSION: '讨论中',
  CONFIRMED: '已确认',
  ARCHIVED: '已归档',
};

watch(selectedGroupId, async (groupId) => {
  if (!groupId) {
    members.value = [];
    tasks.value = [];
    reports.value = [];
    consensus.value = null;
    selectedTaskId.value = '';
    return;
  }
  await loadGroupScope(groupId);
});

watch(consensus, (value) => {
  consensusForm.finalScore = value?.finalScore ?? null;
  consensusForm.finalRecommendation = value?.finalRecommendation ?? '';
});

function statusLabel(status: string | null | undefined) {
  if (!status) return '-';
  return taskStatusLabels[status] ?? assignmentStatusLabels[status] ?? consensusStatusLabels[status] ?? status;
}

function formatDate(value: string | null | undefined) {
  return value ? new Date(value).toLocaleString() : '-';
}

function reviewerDisplayName(member: ReviewGroupMember) {
  return member.displayName || member.username || member.userId;
}

function reportReviewerName(report: ReviewReport) {
  return report.reviewerDisplayName || report.reviewerUsername || report.reviewerUserId || '未知评审员';
}

function taskLeadName(task: AdminReviewTaskSummary) {
  return task.leadReviewerDisplayName || task.leadReviewerUsername || task.leadReviewerUserId || '-';
}

function isUnassignedTask(task: AdminReviewTaskSummary) {
  return task.assignmentCount === 0 || task.status === 'PENDING_ASSIGNMENT' || task.status === 'PENDING';
}

function scoreItems(report: ReviewReport) {
  return Array.isArray(report.scores) ? report.scores as ReviewScoreItem[] : [];
}

async function loadGroups() {
  loading.value = true;
  try {
    groups.value = await listLeaderGroups();
    if (!selectedGroupId.value && groups.value.length) {
      selectedGroupId.value = groups.value[0].id;
    }
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    loading.value = false;
  }
}

async function loadGroupScope(groupId = selectedGroupId.value) {
  if (!groupId) return;
  scopeLoading.value = true;
  try {
    const [nextMembers, nextTasks] = await Promise.all([
      listLeaderGroupMembers(groupId),
      listLeaderGroupTasks(groupId),
    ]);
    members.value = nextMembers;
    tasks.value = nextTasks;
    if (!tasks.value.some((task) => task.id === selectedTaskId.value)) {
      selectedTaskId.value = tasks.value[0]?.id ?? '';
    }
    if (selectedTaskId.value) {
      await loadTaskDetail(selectedTaskId.value);
    } else {
      reports.value = [];
      consensus.value = null;
    }
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    scopeLoading.value = false;
  }
}

async function loadTaskDetail(taskId = selectedTaskId.value) {
  if (!selectedGroupId.value || !taskId) return;
  detailLoading.value = true;
  try {
    selectedTaskId.value = taskId;
    const [nextReports, nextConsensus] = await Promise.all([
      listLeaderTaskReports(selectedGroupId.value, taskId),
      getLeaderTaskConsensus(selectedGroupId.value, taskId),
    ]);
    reports.value = nextReports;
    consensus.value = nextConsensus;
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    detailLoading.value = false;
  }
}

function openAssignDialog(task: AdminReviewTaskSummary) {
  assignmentTask.value = task;
  selectedReviewerIds.value = [];
  assignmentDueAt.value = task.dueAt;
  assignDialogVisible.value = true;
}

async function submitAssignment() {
  if (!selectedGroupId.value || !assignmentTask.value) return;
  if (!selectedReviewerIds.value.length) {
    ElMessage.warning('请选择本组普通评审员');
    return;
  }
  assigning.value = true;
  try {
    await assignLeaderTask(selectedGroupId.value, assignmentTask.value.id, {
      reviewerUserIds: selectedReviewerIds.value,
      dueAt: assignmentDueAt.value,
    });
    ElMessage.success('分配已完成');
    assignDialogVisible.value = false;
    await loadGroupScope();
    await loadTaskDetail(assignmentTask.value.id);
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    assigning.value = false;
  }
}

async function recalculateConsensus() {
  if (!selectedGroupId.value || !selectedTask.value) return;
  consensusRecalculating.value = true;
  try {
    consensus.value = await recalculateLeaderTaskConsensus(selectedGroupId.value, selectedTask.value.id);
    ElMessage.success('共识汇总已重新计算');
    await loadGroupScope();
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    consensusRecalculating.value = false;
  }
}

async function saveConsensus() {
  if (!selectedGroupId.value || !selectedTask.value) return;
  consensusSaving.value = true;
  try {
    consensus.value = await updateLeaderTaskConsensus(selectedGroupId.value, selectedTask.value.id, {
      finalScore: consensusForm.finalScore,
      finalRecommendation: consensusForm.finalRecommendation,
    });
    ElMessage.success('最终评分已保存');
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    consensusSaving.value = false;
  }
}

async function confirmConsensus() {
  if (!selectedGroupId.value || !selectedTask.value) return;
  try {
    await ElMessageBox.confirm('确认后任务将进入最终已确认状态，是否继续？', '确认最终评分', {
      type: 'warning',
      confirmButtonText: '确认',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  consensusConfirming.value = true;
  try {
    consensus.value = await confirmLeaderTaskConsensus(selectedGroupId.value, selectedTask.value.id);
    ElMessage.success('最终评分已确认');
    await loadGroupScope();
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    consensusConfirming.value = false;
  }
}

async function handleLogout() {
  await auth.logout();
  await router.replace('/login');
}

onMounted(async () => {
  if (!auth.state.user) {
    await auth.hydrateCurrentUser();
  }
  await loadGroups();
});
</script>

<template>
  <MainLayout class="leader-page">
    <PageHeader
      eyebrow="Review Leader Workspace"
      title="评审组长工作台"
      description="按评审小组处理任务分配、组内评分详情、最终评分与共识确认。"
    >
      <template #actions>
        <el-tag type="primary" size="large">{{ currentUserName }} · 组长端</el-tag>
        <el-button @click="router.push('/review')">评审工作台</el-button>
        <el-button v-if="auth.isAdmin.value" @click="router.push('/admin/reviews')">管理后台</el-button>
        <el-button @click="handleLogout">退出登录</el-button>
      </template>
    </PageHeader>

    <section class="summary-grid">
      <article class="summary-card app-card">
        <span>负责小组</span>
        <strong>{{ groups.length }}</strong>
      </article>
      <article class="summary-card app-card">
        <span>本组任务</span>
        <strong>{{ tasks.length }}</strong>
      </article>
      <article class="summary-card app-card">
        <span>待分配</span>
        <strong>{{ unassignedCount }}</strong>
      </article>
      <article class="summary-card app-card muted">
        <span>待最终评分</span>
        <strong>{{ submittedCount }}</strong>
      </article>
      <article class="summary-card app-card muted">
        <span>最终已确认</span>
        <strong>{{ confirmedCount }}</strong>
      </article>
    </section>

    <section class="leader-shell">
      <aside class="group-panel app-card" v-loading="loading || scopeLoading">
        <div class="panel-header">
          <div>
            <p>Groups</p>
            <h2>我的小组</h2>
          </div>
          <el-button @click="loadGroups">刷新</el-button>
        </div>

        <el-select v-model="selectedGroupId" class="full-width" placeholder="选择评审小组">
          <el-option
            v-for="group in groups"
            :key="group.id"
            :label="group.name"
            :value="group.id"
          />
        </el-select>

        <div v-if="selectedGroup" class="group-card">
          <strong>{{ selectedGroup.name }}</strong>
          <span>组长：{{ selectedGroup.leaderDisplayName || selectedGroup.leaderUsername || selectedGroup.leaderUserId }}</span>
          <span>成员：{{ selectedGroup.memberCount }} · 任务：{{ selectedGroup.taskCount }}</span>
        </div>

        <div class="member-list">
          <h3>组内普通评审员</h3>
          <el-empty v-if="!reviewerMembers.length" description="暂无普通评审员成员" />
          <div v-for="member in reviewerMembers" :key="member.id" class="member-item">
            <strong>{{ reviewerDisplayName(member) }}</strong>
            <span>{{ member.username || member.userId }}</span>
          </div>
        </div>
      </aside>

      <main class="task-panel app-card" v-loading="scopeLoading">
        <div class="panel-header">
          <div>
            <p>Tasks</p>
            <h2>本组评审任务</h2>
          </div>
          <el-button type="primary" @click="loadGroupScope()">刷新任务</el-button>
        </div>

        <el-table :data="tasks" class="task-table" highlight-current-row @row-click="(row: AdminReviewTaskSummary) => loadTaskDetail(row.id)">
          <el-table-column label="论文" min-width="240" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="task-title-cell">
                <strong>{{ row.title }}</strong>
                <span>{{ row.sourceId }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="130">
            <template #default="{ row }">
              <el-tag effect="plain">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="分配/提交" width="120">
            <template #default="{ row }">
              {{ row.submittedCount }}/{{ row.assignmentCount }}
            </template>
          </el-table-column>
          <el-table-column label="组长" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ taskLeadName(row) }}</template>
          </el-table-column>
          <el-table-column label="截止时间" width="180">
            <template #default="{ row }">{{ formatDate(row.dueAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="230" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click.stop="loadTaskDetail(row.id)">详情</el-button>
              <el-button size="small" type="primary" :disabled="!isUnassignedTask(row)" @click.stop="openAssignDialog(row)">
                分配
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!scopeLoading && !tasks.length" description="暂无本组任务" />
      </main>
    </section>

    <section class="detail-grid" v-if="selectedTask">
      <article class="app-card detail-card" v-loading="detailLoading">
        <div class="panel-header">
          <div>
            <p>Reports</p>
            <h2>组内评分详情</h2>
          </div>
          <el-tag>{{ reports.length }} 份报告</el-tag>
        </div>

        <el-empty v-if="!reports.length" description="暂无已生成的个人评分报告" />
        <el-collapse v-else accordion>
          <el-collapse-item v-for="report in reports" :key="report.id" :name="report.id">
            <template #title>
              <div class="report-title">
                <strong>{{ reportReviewerName(report) }}</strong>
                <span>{{ report.totalScore ?? '-' }} 分 · {{ report.finalRecommendation || '暂无最终建议' }}</span>
              </div>
            </template>
            <div class="report-meta">
              <el-tag size="small" effect="plain">{{ statusLabel(report.status) }}</el-tag>
              <span>更新时间：{{ formatDate(report.updatedAt) }}</span>
            </div>
            <el-table v-if="scoreItems(report).length" :data="scoreItems(report)" size="small" class="score-table">
              <el-table-column prop="name" label="指标" min-width="160" show-overflow-tooltip />
              <el-table-column prop="score" label="得分" width="90" />
              <el-table-column prop="maxScore" label="满分" width="90" />
              <el-table-column prop="reason" label="理由" min-width="220" show-overflow-tooltip />
            </el-table>
            <pre class="json-block">{{ JSON.stringify(report.comments || {}, null, 2) }}</pre>
          </el-collapse-item>
        </el-collapse>
      </article>

      <article class="app-card detail-card consensus-card" v-loading="detailLoading">
        <div class="panel-header">
          <div>
            <p>Consensus</p>
            <h2>最终评分 / 共识</h2>
          </div>
          <el-tag :type="consensus?.status === 'CONFIRMED' ? 'success' : 'warning'" effect="plain">
            {{ statusLabel(consensus?.status) }}
          </el-tag>
        </div>

        <div class="selected-task-card">
          <strong>{{ selectedTask.title }}</strong>
          <span>{{ selectedTask.sourceId }} · {{ statusLabel(selectedTask.status) }}</span>
          <span>提交进度：{{ selectedTask.submittedCount }}/{{ selectedTask.assignmentCount }}</span>
        </div>

        <div class="consensus-actions">
          <el-button type="primary" :loading="consensusRecalculating" :disabled="consensus?.status === 'CONFIRMED'" @click="recalculateConsensus">
            重新计算共识
          </el-button>
        </div>

        <el-form label-position="top" class="consensus-form">
          <el-form-item label="最终分数">
            <el-input-number v-model="consensusForm.finalScore" :min="0" :max="100" :precision="0" class="full-width" />
          </el-form-item>
          <el-form-item label="最终建议">
            <el-input v-model="consensusForm.finalRecommendation" type="textarea" :rows="5" placeholder="填写最终评审建议" />
          </el-form-item>
        </el-form>

        <div class="consensus-footer">
          <el-button :loading="consensusSaving" :disabled="!consensus || consensus.status === 'CONFIRMED'" @click="saveConsensus">
            保存最终评分
          </el-button>
          <el-button type="success" :loading="consensusConfirming" :disabled="!consensus || consensus.status === 'CONFIRMED'" @click="confirmConsensus">
            确认最终评分
          </el-button>
        </div>
      </article>
    </section>

    <el-dialog v-model="assignDialogVisible" title="分配本组评审任务" width="560px">
      <div v-if="assignmentTask" class="assign-task-card">
        <strong>{{ assignmentTask.title }}</strong>
        <span>{{ assignmentTask.sourceId }}</span>
      </div>
      <el-form label-position="top">
        <el-form-item label="普通评审员">
          <el-select v-model="selectedReviewerIds" multiple filterable class="full-width" placeholder="选择本组普通评审员">
            <el-option
              v-for="member in reviewerMembers"
              :key="member.userId"
              :label="reviewerDisplayName(member)"
              :value="member.userId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="截止时间">
          <el-date-picker
            v-model="assignmentDueAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ssZ"
            class="full-width"
            placeholder="可选"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="assigning" @click="submitAssignment">确认分配</el-button>
      </template>
    </el-dialog>
  </MainLayout>
</template>

<style scoped>
.leader-page {
  color: #101828;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
}

.summary-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 18px;
}

.summary-card span {
  color: #667085;
  font-size: 13px;
}

.summary-card strong {
  font-size: 30px;
}

.summary-card.muted {
  background: rgba(255, 255, 255, 0.78);
}

.leader-shell {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 18px;
}

.app-card {
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.86);
  box-shadow: 0 18px 45px rgba(15, 23, 42, 0.08);
}

.group-panel,
.task-panel,
.detail-card {
  padding: 20px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.panel-header p {
  margin: 0 0 4px;
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.panel-header h2 {
  margin: 0;
  font-size: 20px;
}

.full-width {
  width: 100%;
}

.group-card,
.assign-task-card,
.selected-task-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 14px;
  padding: 14px;
  border-radius: 16px;
  background: #f8fafc;
}

.group-card span,
.assign-task-card span,
.selected-task-card span,
.member-item span,
.task-title-cell span,
.report-title span,
.report-meta span {
  color: #667085;
  font-size: 13px;
}

.member-list {
  margin-top: 20px;
}

.member-list h3 {
  margin: 0 0 12px;
  font-size: 15px;
}

.member-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 0;
  border-bottom: 1px solid #eef2f7;
}

.task-title-cell,
.report-title {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.task-table {
  width: 100%;
}

.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(360px, 0.75fr);
  gap: 18px;
}

.report-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.score-table {
  margin-bottom: 12px;
}

.json-block {
  max-height: 220px;
  overflow: auto;
  margin: 0;
  padding: 12px;
  border-radius: 12px;
  background: #0f172a;
  color: #dbeafe;
  font-size: 12px;
  line-height: 1.6;
}

.consensus-card {
  display: flex;
  flex-direction: column;
}

.consensus-actions,
.consensus-form,
.consensus-footer {
  margin-top: 16px;
}

.consensus-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 1180px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .leader-shell,
  .detail-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>