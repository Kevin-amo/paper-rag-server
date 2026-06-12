<script setup lang="ts">
import { computed, ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import MainLayout from '../../layouts/MainLayout.vue';
import PageHeader from '../../components/common/PageHeader.vue';
import ReviewTaskList from './components/ReviewTaskList.vue';
import ReviewParseTab from './components/ReviewParseTab.vue';
import ReviewScoresTab from './components/ReviewScoresTab.vue';
import ReviewRisksTab from './components/ReviewRisksTab.vue';
import ReviewCommentsTab from './components/ReviewCommentsTab.vue';
import ReviewAuditTab from './components/ReviewAuditTab.vue';
import { statusLabel } from '../../constants/review';
import { formatDate } from '../../utils/format';
import { getErrorMessage } from '../../api/http';
import { useAuth } from '../../composables/useAuth';
import { useReviews } from '../../composables/useReviews';
import { useReviewLeaderAccess } from '../../composables/useReviewLeaderAccess';
import type { PaperStructuredContent, ReviewScoreItem } from '../../types';

const router = useRouter();
const auth = useAuth();
const reviews = useReviews();
const { canAccessLeaderWorkspace, refreshLeaderWorkspaceAccess } = useReviewLeaderAccess();
const activeReviewTab = ref('parse');
const reviewFileInputRef = ref<HTMLInputElement | null>(null);

const currentUserName = computed(() => auth.state.user?.displayName || auth.state.user?.username || '评审员');
const selectedTask = computed(() => reviews.selectedTask.value);
const selectedReport = computed(() => reviews.selectedReport.value);
const assignmentSubmitted = computed(() => selectedTask.value?.currentAssignment?.status === 'SUBMITTED');
const structuredParse = computed(() => reviews.structuredParse.value);
const structuredContent = computed(() => {
  const merged = structuredParse.value?.mergedResult;
  if (merged && typeof merged === 'object') {
    return merged as PaperStructuredContent;
  }
  return selectedReport.value?.paperSections as Partial<PaperStructuredContent> ?? {};
});
const missingFields = computed(() => structuredParse.value?.missingFields ?? []);
const lowConfidenceFields = computed(() => structuredParse.value?.lowConfidenceFields ?? []);
const scoreItems = computed(() => (Array.isArray(selectedReport.value?.scores) ? selectedReport.value?.scores as ReviewScoreItem[] : []));
const riskRecords = computed(() => reviews.riskRecords.value);
const comments = computed(() => selectedReport.value?.comments && typeof selectedReport.value.comments === 'object'
  ? selectedReport.value.comments as Record<string, unknown>
  : {});

function handlePageChange(page: number) {
  reviews.loadTasks(page - 1);
}

function handleScoreInput(code: string, value: number) {
  reviews.updateScore(code, value);
}

function handleSelectReviewPaper() {
  reviewFileInputRef.value?.click();
}

async function handleReviewPaperChange(event: Event) {
  const target = event.target as HTMLInputElement;
  const file = target.files?.[0];
  if (!file) {
    return;
  }
  await reviews.uploadPaper(file);
  target.value = '';
}

async function handleLogout() {
  await auth.logout();
  await router.replace('/login');
}

onMounted(async () => {
  try {
    if (!auth.state.user) {
      await auth.hydrateCurrentUser();
    }
    await Promise.all([
      reviews.loadCriteria(),
      reviews.loadTasks(0),
      refreshLeaderWorkspaceAccess(),
    ]);
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
    await router.replace('/login');
  }
});
</script>

<template>
  <MainLayout class="review-page">
    <PageHeader
      eyebrow="Review Workspace"
      title="论文辅助评审工作台"
      description="面向教师/评委的论文初筛、结构化解析、多维评分、风险提示与评审留档入口。"
    >
      <template #actions>
        <el-tag type="primary" size="large">{{ currentUserName }}</el-tag>
        <el-button type="primary" :loading="reviews.uploading.value" @click="handleSelectReviewPaper">上传待评审论文</el-button>
        <el-button v-if="canAccessLeaderWorkspace" @click="router.push('/review-leader')">组长工作台</el-button>
        <el-button v-if="auth.hasRole('USER')" @click="router.push('/user')">用户端</el-button>
        <el-button v-if="auth.isAdmin.value" @click="router.push('/admin')">管理后台</el-button>
        <el-button @click="handleLogout">退出登录</el-button>
      </template>
    </PageHeader>

    <section class="stats-bar">
      <div class="stat-item">
        <span class="stat-dot pending"></span>
        <span class="stat-label">待评审</span>
        <strong>{{ reviews.pendingCount.value }}</strong>
      </div>
      <div class="stat-divider"></div>
      <div class="stat-item">
        <span class="stat-dot reviewing"></span>
        <span class="stat-label">评审中</span>
        <strong>{{ reviews.reviewingCount.value }}</strong>
      </div>
      <div class="stat-divider"></div>
      <div class="stat-item">
        <span class="stat-dot completed"></span>
        <span class="stat-label">已完成</span>
        <strong>{{ reviews.completedCount.value }}</strong>
      </div>
      <div class="stat-divider"></div>
      <div class="stat-item">
        <span class="stat-dot criteria"></span>
        <span class="stat-label">评审指标</span>
        <strong>{{ reviews.criteria.value.length }}</strong>
      </div>
    </section>

    <section class="review-layout">
      <ReviewTaskList
        :tasks="reviews.tasks.value"
        :selected-task-id="selectedTask?.id ?? null"
        :loading="reviews.loading.value"
        :keyword="reviews.keyword.value"
        :status-filter="reviews.statusFilter.value"
        :pagination="reviews.pagination"
        @select="reviews.selectTask"
        @search="reviews.loadTasks(0)"
        @page-change="handlePageChange"
      />

      <main class="review-detail" v-loading="reviews.detailLoading.value">
        <template v-if="selectedTask">
          <div class="detail-header">
            <div class="detail-title">
              <h2>{{ selectedTask.title }}</h2>
              <div class="detail-meta">
                <span>{{ selectedTask.sourceId }}</span>
                <span class="meta-dot"></span>
                <span>{{ formatDate(selectedTask.createdAt) }}</span>
              </div>
            </div>
            <div class="detail-actions">
              <el-tag v-if="selectedTask.currentAssignment" size="default" effect="plain">
                {{ statusLabel(selectedTask.currentAssignment.status) }}
              </el-tag>
              <el-button type="primary" :disabled="assignmentSubmitted" :loading="reviews.generating.value" @click="reviews.runAiReview">
                {{ selectedReport ? '重新生成辅助评审' : '生成辅助评审' }}
              </el-button>
            </div>
          </div>

          <el-tabs v-model="activeReviewTab" class="review-tabs">
            <el-tab-pane label="结构化解析" name="parse">
              <ReviewParseTab
                :structured-parse="structuredParse"
                :structured-content="structuredContent"
                :selected-task="selectedTask"
                :missing-fields="missingFields"
                :low-confidence-fields="lowConfidenceFields"
                :assignment-submitted="assignmentSubmitted"
                :structured-parse-loading="reviews.structuredParseLoading.value"
                :regenerating-structured-parse="reviews.regeneratingStructuredParse.value"
                @rerun-structured-parse="reviews.rerunStructuredParse"
              />
            </el-tab-pane>

            <el-tab-pane label="多维评分" name="scores">
              <ReviewScoresTab
                :score-items="scoreItems"
                :selected-report="selectedReport"
                :assignment-submitted="assignmentSubmitted"
                :saving="reviews.saving.value"
                :submitting-assignment="reviews.submittingAssignment.value"
                :report-form="reviews.reportForm"
                @update-score="handleScoreInput"
                @save-report="reviews.saveReport"
                @submit-assignment="reviews.submitCurrentAssignment"
              />
            </el-tab-pane>

            <el-tab-pane label="风险预警" name="risks">
              <ReviewRisksTab
                :risk-records="riskRecords"
                :risk-loading="reviews.riskLoading.value"
                :risk-status-updating-ids="reviews.riskStatusUpdatingIds.value"
                @set-risk-status="reviews.setRiskStatus"
              />
            </el-tab-pane>

            <el-tab-pane label="评语意见" name="comments">
              <ReviewCommentsTab
                :comments="comments"
                :selected-report="selectedReport"
              />
            </el-tab-pane>

            <el-tab-pane label="留档信息" name="audit">
              <ReviewAuditTab :selected-report="selectedReport" />
            </el-tab-pane>
          </el-tabs>
        </template>

        <el-empty v-else description="请选择一篇论文进行评审" />
      </main>
    </section>
    <input
      ref="reviewFileInputRef"
      type="file"
      accept=".pdf,.doc,.docx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
      style="display: none"
      @change="handleReviewPaperChange"
    />
  </MainLayout>
</template>

<style scoped>
.review-page {
  gap: 20px;
  padding: 24px;
  background: var(--app-bg);
}

.review-page :deep([class~="page-header"]) {
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  padding: 24px 28px;
  background: var(--app-surface);
  box-shadow: var(--app-shadow-xs);
}

.review-page :deep([class~="page-eyebrow"]) {
  margin-bottom: 6px;
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.06em;
}

.review-page :deep([class~="page-header"] h1) {
  color: var(--app-text);
  font-size: clamp(22px, 2.2vw, 28px);
  letter-spacing: -0.02em;
}

.review-page :deep([class~="page-description"]) {
  margin-top: 8px;
  color: var(--app-text-muted);
  line-height: 1.6;
}

.review-page :deep([class~="el-button"]) {
  border-radius: var(--app-radius-sm);
}

.review-page :deep([class~="el-tabs__item"]) {
  color: var(--app-text-muted);
  font-weight: 600;
}

.review-page :deep([class~="el-tabs__item"][class~="is-active"]) {
  color: var(--app-primary);
}

.review-page :deep([class~="el-tabs__active-bar"]) {
  background: var(--app-primary);
}

.review-page .app-card,
.review-detail {
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: var(--app-surface);
}

.stats-bar {
  display: flex;
  align-items: center;
  gap: 0;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  padding: 14px 24px;
  background: var(--app-surface);
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 16px;
}

.stat-item:first-child {
  padding-left: 0;
}

.stat-item:last-child {
  padding-right: 0;
}

.stat-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.stat-dot.pending {
  background: var(--app-primary);
}

.stat-dot.reviewing {
  background: var(--app-warning);
}

.stat-dot.completed {
  background: var(--app-success);
}

.stat-dot.criteria {
  background: var(--app-accent);
}

.stat-label {
  color: var(--app-text-muted);
  font-size: 13px;
  font-weight: 500;
}

.stat-item strong {
  color: var(--app-text);
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.stat-divider {
  width: 1px;
  height: 24px;
  background: var(--app-border);
  flex-shrink: 0;
}

.review-layout {
  display: grid;
  grid-template-columns: minmax(300px, 360px) minmax(0, 1fr);
  gap: 20px;
  min-height: 640px;
}

.review-detail {
  padding: 24px;
}

.detail-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--app-border);
}

.detail-title h2 {
  margin: 0;
  color: var(--app-text);
  font-size: 20px;
  font-weight: 700;
  line-height: 1.3;
}

.detail-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  color: var(--app-text-subtle);
  font-size: 13px;
}

.meta-dot {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--app-text-subtle);
}

.detail-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  flex-shrink: 0;
}

.review-tabs {
  margin-top: 20px;
}

@media (max-width: 1180px) {
  .review-page {
    padding: 16px;
  }

  .review-layout {
    grid-template-columns: 1fr;
  }

  .stats-bar {
    flex-wrap: wrap;
    gap: 8px;
  }

  .stat-divider {
    display: none;
  }

  .stat-item {
    padding: 4px 12px 4px 0;
  }
}

@media (max-width: 720px) {
  .detail-header {
    flex-direction: column;
  }
}
</style>
