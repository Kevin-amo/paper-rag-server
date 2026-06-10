<script setup lang="ts">
import { computed, ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import MainLayout from '../../layouts/MainLayout.vue';
import PageHeader from '../../components/common/PageHeader.vue';
import { getErrorMessage } from '../../api/http';
import { useAuth } from '../../composables/useAuth';
import { useReviews } from '../../composables/useReviews';
import { useReviewLeaderAccess } from '../../composables/useReviewLeaderAccess';
import type { PaperStructuredContent, ReviewRiskRecord, ReviewScoreItem } from '../../types';

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

const statusLabelMap: Record<string, string> = {
  PENDING: '待评审',
  REVIEWING: '评审中',
  COMPLETED: '已完成',
  PENDING_ASSIGNMENT: '待分配',
  ASSIGNED: '待评审',
  IN_REVIEW: '评审中',
  SUBMITTED: '已提交',
  RETURNED: '已退回',
  CANCELLED: '已取消',
  CONSENSUS_CONFIRMED: '最终已确认',
  NEEDS_REVIEW: '需复核',
};

const riskTypeMap: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
  LOW: 'success',
  MEDIUM: 'warning',
  HIGH: 'danger',
  CRITICAL: 'danger',
};

const riskStatusMap: Record<string, string> = {
  OPEN: '待处理',
  CONFIRMED: '已确认',
  IGNORED: '已忽略',
  RESOLVED: '已解决',
};

function statusLabel(status: string) {
  return statusLabelMap[status] ?? status;
}

function formatDate(value: string | null | undefined) {
  return value ? new Date(value).toLocaleString() : '-';
}

function textValue(value: unknown, fallback = '暂未识别') {
  if (Array.isArray(value)) {
    return value.length ? value.join('、') : fallback;
  }
  if (value === null || value === undefined) {
    return fallback;
  }
  const text = String(value).trim();
  return text || fallback;
}

function listValue(value: unknown) {
  return Array.isArray(value) ? value.map((item) => String(item)).filter(Boolean) : [];
}

function formatJson(value: unknown) {
  if (!value) {
    return '{}';
  }
  return JSON.stringify(value, null, 2);
}

function riskStatusLabel(status: ReviewRiskRecord['status']) {
  return riskStatusMap[status] ?? status;
}

function isRiskUpdating(riskId: string) {
  return reviews.riskStatusUpdatingIds.value.includes(riskId);
}

function isRiskActionDisabled(
  risk: ReviewRiskRecord,
  status: 'CONFIRMED' | 'IGNORED' | 'RESOLVED',
) {
  return isRiskUpdating(risk.id) || risk.status === status;
}

function handlePageChange(page: number) {
  reviews.loadTasks(page - 1);
}

function handleScoreInput(item: ReviewScoreItem, value: number | number[]) {
  reviews.updateScore(item.code, Array.isArray(value) ? value[0] : value);
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
      <aside class="task-panel app-card">
        <div class="panel-header">
          <div>
            <p>My Assignments</p>
            <h2>我的评审任务</h2>
          </div>
          <el-button :loading="reviews.loading.value" @click="reviews.loadTasks(0)">刷新</el-button>
        </div>

        <div class="task-toolbar">
          <el-input
            v-model="reviews.keyword.value"
            clearable
            placeholder="搜索标题 / 文档标识"
            @keyup.enter="reviews.loadTasks(0)"
          />
          <el-select v-model="reviews.statusFilter.value" clearable placeholder="状态">
            <el-option label="待评审" value="ASSIGNED" />
            <el-option label="评审中" value="REVIEWING" />
            <el-option label="已提交" value="SUBMITTED" />
            <el-option label="已退回" value="RETURNED" />
          </el-select>
          <el-button type="primary" @click="reviews.loadTasks(0)">筛选</el-button>
        </div>

        <div v-loading="reviews.loading.value" class="task-list">
          <button
            v-for="task in reviews.tasks.value"
            :key="task.id"
            class="task-item"
            :class="{ active: selectedTask?.id === task.id }"
            type="button"
            @click="reviews.selectTask(task.id)"
          >
            <span class="task-title">{{ task.title }}</span>
            <span class="task-meta">{{ task.sourceId }}</span>
            <span class="task-bottom">
              <el-tag size="small" effect="plain">{{ statusLabel(task.currentAssignment?.status ?? task.status) }}</el-tag>
              <span>{{ formatDate(task.updatedAt) }}</span>
            </span>
          </button>
          <el-empty v-if="!reviews.loading.value && !reviews.tasks.value.length" description="暂无评审任务" />
        </div>

        <el-pagination
          v-if="reviews.pagination.total > reviews.pagination.size"
          small
          background
          layout="prev, pager, next"
          :total="reviews.pagination.total"
          :page-size="reviews.pagination.size"
          :current-page="reviews.pagination.page + 1"
          @current-change="handlePageChange"
        />
      </aside>

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
            <section class="detail-section" v-loading="reviews.structuredParseLoading.value">
              <div class="section-title">
                <div>
                  <h3>结构化理解与内容解析</h3>
                  <span>独立于 AI 评审报告的论文结构化解析结果</span>
                </div>
                <div class="section-actions">
                  <el-tag :type="structuredParse?.status === 'FAILED' ? 'danger' : structuredParse ? 'success' : 'info'" effect="plain">
                    {{ structuredParse?.status || '未生成' }}
                  </el-tag>
                  <el-button size="small" :disabled="assignmentSubmitted" :loading="reviews.regeneratingStructuredParse.value" @click="reviews.rerunStructuredParse">
                    重新解析
                  </el-button>
                </div>
              </div>
              <div v-if="structuredParse?.errorMessage" class="parse-alert">
                {{ structuredParse.errorMessage }}
              </div>
              <div class="parse-tags">
                <el-tag v-if="structuredParse?.updatedAt" type="info" effect="plain">
                  更新时间：{{ formatDate(structuredParse.updatedAt) }}
                </el-tag>
                <el-tag v-for="field in missingFields" :key="`missing-${field}`" type="warning" effect="plain">
                  缺失：{{ field }}
                </el-tag>
                <el-tag v-for="field in lowConfidenceFields" :key="`low-${field}`" type="danger" effect="plain">
                  低置信：{{ field }}
                </el-tag>
              </div>
              <div class="section-grid">
                <article>
                  <span>标题</span>
                  <strong>{{ textValue(structuredContent.title, selectedTask.document?.title || '暂未识别') }}</strong>
                </article>
                <article>
                  <span>关键词</span>
                  <strong>{{ textValue(structuredContent.keywords || selectedTask.document?.keywords) }}</strong>
                </article>
                <article>
                  <span>研究对象</span>
                  <strong>{{ textValue(structuredContent.researchObject) }}</strong>
                </article>
                <article>
                  <span>研究问题</span>
                  <strong>{{ textValue(structuredContent.researchQuestion) }}</strong>
                </article>
                <article>
                  <span>方法路径</span>
                  <strong>{{ textValue(structuredContent.methodPath) }}</strong>
                </article>
                <article>
                  <span>创新点</span>
                  <strong>{{ textValue(structuredContent.innovationPoints) }}</strong>
                </article>
              </div>
              <div class="paper-sections">
                <el-collapse>
                  <el-collapse-item title="摘要" name="abstract">
                    <p>{{ textValue(structuredContent.abstract, selectedTask.document?.abstractText || '暂无摘要') }}</p>
                  </el-collapse-item>
                  <el-collapse-item title="引言" name="introduction">
                    <p>{{ textValue(structuredContent.introduction) }}</p>
                  </el-collapse-item>
                  <el-collapse-item title="文献综述 / 相关研究" name="literatureReview">
                    <p>{{ textValue(structuredContent.literatureReview) }}</p>
                  </el-collapse-item>
                  <el-collapse-item title="研究方法" name="methodology">
                    <p>{{ textValue(structuredContent.methodology) }}</p>
                  </el-collapse-item>
                  <el-collapse-item title="实验与结果" name="experimentResults">
                    <p>{{ textValue(structuredContent.experimentResults) }}</p>
                  </el-collapse-item>
                  <el-collapse-item title="讨论" name="discussion">
                    <p>{{ textValue(structuredContent.discussion) }}</p>
                  </el-collapse-item>
                  <el-collapse-item title="结论" name="conclusion">
                    <p>{{ textValue(structuredContent.conclusion) }}</p>
                  </el-collapse-item>
                  <el-collapse-item title="实验数据摘要" name="experimentDataSummary">
                    <p>{{ textValue(structuredContent.experimentDataSummary) }}</p>
                  </el-collapse-item>
                  <el-collapse-item title="主要结论" name="mainConclusions">
                    <p>{{ textValue(structuredContent.mainConclusions) }}</p>
                  </el-collapse-item>
                  <el-collapse-item title="参考文献" name="references">
                    <p>{{ textValue(structuredContent.references) }}</p>
                  </el-collapse-item>
                </el-collapse>
              </div>
            </section>

            </el-tab-pane>

            <el-tab-pane label="多维评分" name="scores">
            <section class="detail-section">
              <div class="section-title">
                <h3>维度化辅助评分</h3>
                <span>评委可在 AI 建议基础上手动调整总分和最终意见</span>
              </div>
              <div v-if="scoreItems.length" class="score-list">
                <article v-for="item in scoreItems" :key="item.code" class="score-card">
                  <div class="score-head">
                    <strong>{{ item.name }}</strong>
                    <span>{{ item.score }} / {{ item.maxScore }}</span>
                  </div>
                  <el-slider
                    :model-value="Number(item.score)"
                    :min="0"
                    :max="Number(item.maxScore || 100)"
                    :disabled="assignmentSubmitted"
                    @input="handleScoreInput(item, $event)"
                  />
                  <p>{{ item.reason }}</p>
                </article>
              </div>
              <el-empty v-else description="生成辅助评审后展示评分建议" />

              <div class="manual-form">
                <el-input-number v-model="reviews.reportForm.totalScore" :min="0" :max="100" controls-position="right" :disabled="assignmentSubmitted" />
                <el-input
                  v-model="reviews.reportForm.finalRecommendation"
                  type="textarea"
                  :rows="3"
                  :disabled="assignmentSubmitted"
                  placeholder="填写或调整最终评审意见"
                />
                <div class="manual-actions">
                  <el-button :disabled="assignmentSubmitted || !selectedReport" :loading="reviews.saving.value" @click="reviews.saveReport">
                    保存调整
                  </el-button>
                  <el-popconfirm title="提交后个人评审将只读，是否继续？" @confirm="reviews.submitCurrentAssignment">
                    <template #reference>
                      <el-button
                        type="success"
                        :disabled="assignmentSubmitted || reviews.submittingAssignment.value || !selectedTask?.report"
                        :loading="reviews.submittingAssignment.value"
                      >
                        提交评审
                      </el-button>
                    </template>
                  </el-popconfirm>
                </div>
              </div>
            </section>

            </el-tab-pane>

            <el-tab-pane label="风险预警" name="risks">
              <section class="detail-section">
                <div class="section-title">
                  <div>
                    <h3>风险提示</h3>
                    <span>政治表述、参考文献、结构与语言风险的规范化记录</span>
                  </div>
                </div>
                <div v-loading="reviews.riskLoading.value">
                  <div v-if="riskRecords.length" class="risk-list normalized-risk-list">
                    <article v-for="risk in riskRecords" :key="risk.id">
                      <el-tag :type="riskTypeMap[risk.riskLevel] || 'info'" effect="plain">{{ risk.riskLevel }}</el-tag>
                      <div>
                        <div class="risk-heading">
                          <strong>{{ risk.riskType }}</strong>
                          <el-tag size="small" effect="plain">{{ riskStatusLabel(risk.status) }}</el-tag>
                        </div>
                        <p>{{ risk.evidence || '未给出证据' }}</p>
                        <span>{{ risk.suggestion || '建议人工复核' }}</span>
                        <span v-if="risk.confidence != null">置信度：{{ risk.confidence }}</span>
                        <span v-if="risk.detector">检测器：{{ risk.detector }}</span>
                        <div class="risk-actions">
                          <el-button
                            size="small"
                            type="primary"
                            plain
                            :loading="isRiskUpdating(risk.id)"
                            :disabled="isRiskActionDisabled(risk, 'CONFIRMED')"
                            @click="reviews.setRiskStatus(risk.id, 'CONFIRMED')"
                          >
                            确认
                          </el-button>
                          <el-button
                            size="small"
                            plain
                            :loading="isRiskUpdating(risk.id)"
                            :disabled="isRiskActionDisabled(risk, 'IGNORED')"
                            @click="reviews.setRiskStatus(risk.id, 'IGNORED')"
                          >
                            忽略
                          </el-button>
                          <el-button
                            size="small"
                            type="success"
                            plain
                            :loading="isRiskUpdating(risk.id)"
                            :disabled="isRiskActionDisabled(risk, 'RESOLVED')"
                            @click="reviews.setRiskStatus(risk.id, 'RESOLVED')"
                          >
                            标记解决
                          </el-button>
                        </div>
                      </div>
                    </article>
                  </div>
                  <el-empty v-else description="暂无风险提示" />
                </div>
              </section>
            </el-tab-pane>

            <el-tab-pane label="评语意见" name="comments">
              <section class="detail-section">
                <div class="comments-card">
                  <div class="section-title compact">
                    <h3>个性化评语</h3>
                    <span>优点、不足与修改建议</span>
                  </div>
                  <p class="comment-summary">{{ textValue(comments.summary || comments.finalAdvice, selectedReport?.finalRecommendation || '暂无评语') }}</p>
                  <div class="comment-columns">
                    <div>
                      <strong>主要优点</strong>
                      <ul>
                        <li v-for="item in listValue(comments.strengths)" :key="item">{{ item }}</li>
                        <li v-if="!listValue(comments.strengths).length">暂无</li>
                      </ul>
                    </div>
                    <div>
                      <strong>问题与建议</strong>
                      <ul>
                        <li v-for="item in [...listValue(comments.weaknesses), ...listValue(comments.suggestions)]" :key="item">{{ item }}</li>
                        <li v-if="![...listValue(comments.weaknesses), ...listValue(comments.suggestions)].length">暂无</li>
                      </ul>
                    </div>
                  </div>
                </div>
              </section>
            </el-tab-pane>

            <el-tab-pane label="留档信息" name="audit">
              <section class="detail-section">
                <div class="section-title">
                  <h3>评审留档信息</h3>
                  <span>模型、提示词、指标版本与人工调整记录</span>
                </div>
                <div v-if="selectedReport" class="audit-grid">
                  <article>
                    <span>模型版本</span>
                    <strong>{{ selectedReport.modelVersion || '-' }}</strong>
                  </article>
                  <article>
                    <span>Prompt 版本</span>
                    <strong>{{ selectedReport.promptVersion || '-' }}</strong>
                  </article>
                  <article>
                    <span>指标版本</span>
                    <strong>{{ selectedReport.criterionVersion ?? '-' }}</strong>
                  </article>
                  <article>
                    <span>置信度</span>
                    <strong>{{ selectedReport.confidence ?? '-' }}</strong>
                  </article>
                </div>
                <pre v-if="selectedReport" class="manual-delta">{{ formatJson(selectedReport.manualDelta) }}</pre>
                <el-empty v-else description="生成辅助评审后展示留档信息" />
              </section>
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
  gap: 18px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.summary-card {
  padding: 18px;
}

.summary-card span,
.panel-header p,
.detail-hero p {
  margin: 0;
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 850;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.summary-card strong {
  display: block;
  margin-top: 10px;
  font-size: 30px;
  line-height: 1;
}

.summary-card.muted {
  background: rgba(255, 255, 255, 0.72);
}

.review-layout {
  display: grid;
  grid-template-columns: minmax(320px, 380px) minmax(0, 1fr);
  gap: 18px;
  min-height: 680px;
}

.task-panel,
.review-detail {
  padding: 20px;
}

.panel-header,
.detail-hero,
.section-title,
.score-head,
.manual-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.panel-header h2,
.detail-hero h2,
.section-title h3 {
  margin: 4px 0 0;
  color: var(--app-text);
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
  gap: 10px;
  min-height: 420px;
}

.task-item {
  width: 100%;
  border: 1px solid rgba(209, 213, 219, 0.72);
  border-radius: 18px;
  padding: 14px;
  background: rgba(255, 255, 255, 0.72);
  color: inherit;
  cursor: pointer;
  text-align: left;
  transition: all 0.18s ease;
}

.task-item.active,
.task-item:hover {
  border-color: rgba(0, 122, 255, 0.35);
  background: rgba(0, 122, 255, 0.08);
}

.task-title,
.task-meta,
.task-bottom {
  display: block;
}

.task-title {
  color: var(--app-text);
  font-weight: 850;
}

.task-meta,
.task-bottom span:last-child,
.detail-hero span,
.section-title span {
  color: var(--app-text-muted);
  font-size: 12px;
}

.task-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 12px;
}

.detail-hero {
  padding-bottom: 18px;
  border-bottom: 1px solid var(--app-border);
}

.hero-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.detail-section {
  margin-top: 22px;
}

.review-tabs {
  margin-top: 18px;
}

.section-title.compact {
  align-items: flex-start;
  flex-direction: column;
  gap: 2px;
}

.section-actions,
.parse-tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.parse-tags {
  margin-top: 12px;
}

.parse-alert {
  margin-top: 12px;
  border: 1px solid rgba(239, 68, 68, 0.2);
  border-radius: 14px;
  padding: 10px 12px;
  background: rgba(239, 68, 68, 0.08);
  color: #b91c1c;
  line-height: 1.6;
}

.section-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.section-grid article,
.score-card,
.comments-card {
  border: 1px solid rgba(209, 213, 219, 0.7);
  border-radius: 18px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.68);
}

.section-grid span {
  display: block;
  color: var(--app-text-muted);
  font-size: 12px;
}

.section-grid strong {
  display: block;
  margin-top: 8px;
  color: var(--app-text);
  line-height: 1.6;
}

.paper-sections {
  margin-top: 14px;
}

.paper-sections p,
.score-card p,
.comment-summary,
.risk-list p {
  color: var(--app-text-muted);
  line-height: 1.75;
}

.score-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.score-head strong {
  color: var(--app-text);
}

.score-head span {
  color: var(--app-primary);
  font-weight: 850;
}

.manual-form {
  display: grid;
  grid-template-columns: 140px minmax(0, 1fr) auto;
  gap: 12px;
  margin-top: 16px;
}

.comment-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.comment-columns strong {
  color: var(--app-text);
}

.comment-columns ul {
  margin: 8px 0 0;
  padding-left: 18px;
  color: var(--app-text-muted);
  line-height: 1.8;
}

.risk-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 12px;
}

.risk-list article {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 6px 10px;
  border-bottom: 1px solid rgba(209, 213, 219, 0.7);
  padding-bottom: 12px;
}

.risk-list strong,
.risk-list p,
.risk-list span {
  grid-column: 2;
}

.risk-list strong {
  color: var(--app-text);
}

.risk-list span {
  color: var(--app-text-muted);
  font-size: 12px;
}

.risk-list p,
.risk-list span,
.risk-heading {
  overflow-wrap: anywhere;
}

.normalized-risk-list article {
  grid-template-columns: auto minmax(0, 1fr);
}

.risk-heading,
.risk-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.risk-actions {
  margin-top: 10px;
}

.audit-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.audit-grid article {
  border: 1px solid rgba(209, 213, 219, 0.7);
  border-radius: 18px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.68);
}

.audit-grid span {
  display: block;
  color: var(--app-text-muted);
  font-size: 12px;
}

.audit-grid strong {
  display: block;
  margin-top: 8px;
  color: var(--app-text);
  line-height: 1.6;
}

.manual-delta {
  margin: 14px 0 0;
  border: 1px solid rgba(209, 213, 219, 0.7);
  border-radius: 18px;
  padding: 16px;
  background: rgba(15, 23, 42, 0.04);
  color: var(--app-text);
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

@media (max-width: 1180px) {
  .review-layout,
  .manual-form {
    grid-template-columns: 1fr;
  }

  .summary-grid,
  .section-grid,
  .score-list,
  .audit-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .manual-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 720px) {
  .summary-grid,
  .section-grid,
  .score-list,
  .comment-columns,
  .audit-grid {
    grid-template-columns: 1fr;
  }

  .detail-hero,
  .panel-header,
  .section-title {
    align-items: flex-start;
    flex-direction: column;
  }
}

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
.task-panel,
.review-detail {
  border: 1px solid #dde3ee;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 12px 30px rgba(16, 24, 40, 0.05);
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

.summary-card span,
.panel-header p,
.detail-hero p {
  color: #155eef;
  font-size: 12px;
  letter-spacing: 0.04em;
}

.panel-header h2,
.detail-hero h2,
.section-title h3 {
  color: #101828;
}

.task-meta,
.task-bottom span:last-child,
.detail-hero span,
.section-title span {
  color: #667085;
}

.summary-grid {
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

.summary-card strong {
  margin-top: 8px;
  color: #101828;
  font-size: 28px;
}

.review-layout {
  grid-template-columns: minmax(320px, 380px) minmax(0, 1fr);
  gap: 16px;
}

.task-panel,
.review-detail {
  padding: 18px;
}

.panel-header {
  padding-bottom: 14px;
  border-bottom: 1px solid #edf1f7;
}

.task-toolbar {
  gap: 10px;
}

.task-list {
  gap: 6px;
}

.task-item {
  position: relative;
  border: 1px solid transparent;
  border-radius: 9px;
  padding: 12px 12px 12px 14px;
  background: #fff;
  box-shadow: inset 0 -1px 0 #edf1f7;
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

.task-title {
  color: #101828;
  font-weight: 750;
}

.detail-hero {
  padding-bottom: 16px;
  border-bottom: 1px solid #edf1f7;
}

.hero-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.detail-section {
  margin-top: 18px;
}

.section-grid article,
.score-card,
.comments-card,
.audit-grid article {
  border: 1px solid #dde3ee;
  border-radius: 10px;
  background: #fff;
  box-shadow: none;
}

.section-grid span,
.audit-grid span {
  color: #667085;
}

.section-grid strong,
.audit-grid strong,
.score-head strong,
.comment-columns strong,
.risk-list strong {
  color: #101828;
}

.paper-sections p,
.score-card p,
.comment-summary,
.risk-list p {
  color: #475467;
  line-height: 1.7;
}

.score-head span {
  color: #155eef;
}

.manual-form {
  align-items: start;
}

.risk-list article {
  border: 1px solid #dde3ee;
  border-radius: 10px;
  padding: 14px;
  background: #fff;
}

.risk-list span {
  color: #667085;
}

.manual-delta {
  border-color: #dde3ee;
  border-radius: 10px;
  background: #f8fafc;
  color: #101828;
}

@media (max-width: 1180px) {
  .review-page {
    padding: 16px;
  }
}
</style>
