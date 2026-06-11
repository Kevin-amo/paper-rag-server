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
      eyebrow="AI Review Workspace"
      title="论文辅助评审工作台"
      description="面向教师/评委的论文初筛、结构化解析、多维评分、风险提示与评审留档入口。"
    >
      <template #actions>
        <el-tag type="primary" size="large">{{ currentUserName }} · 评审端</el-tag>
        <el-button type="primary" :loading="reviews.uploading.value" @click="handleSelectReviewPaper">上传待评审论文</el-button>
        <el-button v-if="canAccessLeaderWorkspace" @click="router.push('/review-leader')">组长工作台</el-button>
        <el-button v-if="auth.hasRole('USER')" @click="router.push('/user')">用户端</el-button>
        <el-button v-if="auth.isAdmin.value" @click="router.push('/admin')">管理后台</el-button>
        <el-button @click="handleLogout">退出登录</el-button>
      </template>
    </PageHeader>

    <section class="summary-grid">
      <article class="summary-card app-card">
        <span>待评审</span>
        <strong>{{ reviews.pendingCount.value }}</strong>
      </article>
      <article class="summary-card app-card">
        <span>评审中</span>
        <strong>{{ reviews.reviewingCount.value }}</strong>
      </article>
      <article class="summary-card app-card">
        <span>已完成</span>
        <strong>{{ reviews.completedCount.value }}</strong>
      </article>
      <article class="summary-card app-card muted">
        <span>评审指标</span>
        <strong>{{ reviews.criteria.value.length }}</strong>
      </article>
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

      <main class="review-detail app-card" v-loading="reviews.detailLoading.value">
        <template v-if="selectedTask">
          <div class="detail-hero">
            <div>
              <p>Paper Review</p>
              <h2>{{ selectedTask.title }}</h2>
              <span>{{ selectedTask.sourceId }} · {{ formatDate(selectedTask.createdAt) }}</span>
            </div>
            <div class="hero-actions">
              <el-tag v-if="selectedTask.currentAssignment" size="large" effect="plain">
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
  gap: 16px;
  padding: 20px 24px 32px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.72), rgba(255, 255, 255, 0) 240px),
    #f6f8fb;
}

.review-page :deep([class~="page-header"]) {
  border: 1px solid #dde3ee;
  border-radius: 10px;
  padding: 20px 22px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
  box-shadow: 0 12px 30px rgba(16, 24, 40, 0.05);
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

.review-page :deep([class~="page-eyebrow"]) {
  margin-bottom: 6px;
  color: #155eef;
  font-size: 12px;
  letter-spacing: 0.04em;
}

.review-page :deep([class~="page-header"] h1) {
  color: #101828;
  font-size: clamp(22px, 2.2vw, 30px);
  letter-spacing: 0;
}

.review-page :deep([class~="page-description"]) {
  margin-top: 8px;
  color: #475467;
  line-height: 1.6;
}

.review-page :deep([class~="el-button"]) {
  border-radius: 8px;
  font-weight: 700;
}

.review-page :deep([class~="el-button--primary"]) {
  box-shadow: 0 10px 22px rgba(37, 99, 235, 0.18);
}

.review-page :deep([class~="el-input__wrapper"]),
.review-page :deep([class~="el-select__wrapper"]),
.review-page :deep([class~="el-textarea__inner"]),
.review-page :deep([class~="el-input-number"] [class~="el-input__wrapper"]) {
  border-radius: 9px;
  box-shadow: 0 0 0 1px #d0d7e2 inset;
}

.review-page :deep([class~="el-tabs__item"]) {
  color: #475467;
  font-weight: 700;
}

.review-page :deep([class~="el-tabs__item"][class~="is-active"]) {
  color: #155eef;
}

.review-page :deep([class~="el-tabs__active-bar"]) {
  background: #155eef;
}

.review-page :deep([class~="el-collapse"]) {
  border-color: #edf1f7;
}

.review-page :deep([class~="el-collapse-item__header"]) {
  color: #101828;
  font-weight: 750;
}

.review-page .app-card,
.review-detail {
  border: 1px solid #dde3ee;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 12px 30px rgba(16, 24, 40, 0.05);
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.summary-card {
  position: relative;
  min-height: 96px;
  display: grid;
  align-content: center;
  padding: 16px 18px 16px 72px;
  border-color: #dde3ee;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 10px 26px rgba(16, 24, 40, 0.04);
}

.summary-card::before {
  position: absolute;
  left: 18px;
  top: 50%;
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  transform: translateY(-50%);
  font-size: 13px;
  font-weight: 850;
}

.summary-card:nth-child(1)::before {
  background: #eaf2ff;
  color: #155eef;
  content: 'Q';
}

.summary-card:nth-child(2)::before {
  background: #fff4df;
  color: #dc6803;
  content: 'R';
}

.summary-card:nth-child(3)::before {
  background: #e8f8ef;
  color: #099250;
  content: 'C';
}

.summary-card:nth-child(4)::before {
  background: #eef2ff;
  color: #4f46e5;
  content: 'M';
}

.summary-card span {
  color: #155eef;
  font-size: 12px;
  font-weight: 850;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.summary-card strong {
  display: block;
  margin-top: 8px;
  color: #101828;
  font-size: 28px;
}

.review-layout {
  display: grid;
  grid-template-columns: minmax(320px, 380px) minmax(0, 1fr);
  gap: 16px;
  min-height: 680px;
}

.review-detail {
  padding: 18px;
}

.detail-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding-bottom: 16px;
  border-bottom: 1px solid #edf1f7;
}

.detail-hero p {
  margin: 0;
  color: #155eef;
  font-size: 12px;
  font-weight: 850;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.detail-hero h2 {
  margin: 4px 0 0;
  color: #101828;
}

.detail-hero span {
  color: #667085;
  font-size: 12px;
}

.hero-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.review-tabs {
  margin-top: 18px;
}

@media (max-width: 1180px) {
  .review-page {
    padding: 16px;
  }

  .review-layout {
    grid-template-columns: 1fr;
  }

  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }

  .detail-hero {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
