<script setup lang="ts">
import { computed, ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import type { UploadUserFile } from 'element-plus';
import MainLayout from '../../layouts/MainLayout.vue';
import PageHeader from '../../components/common/PageHeader.vue';
import { getErrorMessage } from '../../api/http';
import { useAuth } from '../../composables/useAuth';
import { useReviews } from '../../composables/useReviews';
import type { PaperStructuredContent, ReviewRiskRecord, ReviewScoreItem } from '../../types';

const router = useRouter();
const auth = useAuth();
const reviews = useReviews();
const uploadDialogVisible = ref(false);
const uploadFileList = ref<UploadUserFile[]>([]);
const uploadTitle = ref('');
const activeReviewTab = ref('parse');

const currentUserName = computed(() => auth.state.user?.displayName || auth.state.user?.username || '评审员');
const selectedTask = computed(() => reviews.selectedTask.value);
const selectedReport = computed(() => reviews.selectedReport.value);
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

function buildReviewSourceId(file: File) {
  return `review-${file.name}-${file.size}-${file.lastModified}`.replace(/[^\p{L}\p{N}._-]+/gu, '-').slice(0, 128);
}

function handlePageChange(page: number) {
  reviews.loadTasks(page - 1);
}

function handleScoreInput(item: ReviewScoreItem, value: number | number[]) {
  reviews.updateScore(item.code, Array.isArray(value) ? value[0] : value);
}

async function submitReviewUpload() {
  const rawFile = uploadFileList.value[0]?.raw;
  if (!(rawFile instanceof File)) {
    ElMessage.warning('请先选择待评审论文');
    return;
  }
  await reviews.uploadPaper({
    file: rawFile,
    sourceId: buildReviewSourceId(rawFile),
    title: uploadTitle.value.trim() || rawFile.name.replace(/\.[^.]+$/, ''),
  });
  uploadDialogVisible.value = false;
  uploadFileList.value = [];
  uploadTitle.value = '';
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
    await Promise.all([reviews.loadCriteria(), reviews.loadTasks(0)]);
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
        <el-button type="primary" :loading="reviews.uploading.value" @click="uploadDialogVisible = true">上传评审论文</el-button>
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

    <section class="review-upload-card app-card">
      <div>
        <p>Review Upload</p>
        <h2>上传待评审论文</h2>
        <span>文件会进入独立的评审入库流程，不会出现在普通用户知识库和默认问答检索中。</span>
      </div>
      <el-button type="primary" :loading="reviews.uploading.value" @click="uploadDialogVisible = true">选择论文</el-button>
    </section>

    <section class="review-layout">
      <aside class="task-panel app-card">
        <div class="panel-header">
          <div>
            <p>Review Queue</p>
            <h2>论文任务池</h2>
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
            <el-option label="待评审" value="PENDING" />
            <el-option label="评审中" value="REVIEWING" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="需复核" value="NEEDS_REVIEW" />
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
              <el-tag size="small" effect="plain">{{ statusLabel(task.status) }}</el-tag>
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
              <el-tag size="large" effect="plain">{{ statusLabel(selectedTask.status) }}</el-tag>
              <el-button type="primary" :loading="reviews.generating.value" @click="reviews.runAiReview">
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
                  <el-button size="small" :loading="reviews.regeneratingStructuredParse.value" @click="reviews.rerunStructuredParse">
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
                    @input="handleScoreInput(item, $event)"
                  />
                  <p>{{ item.reason }}</p>
                </article>
              </div>
              <el-empty v-else description="生成辅助评审后展示评分建议" />

              <div class="manual-form">
                <el-input-number v-model="reviews.reportForm.totalScore" :min="0" :max="100" controls-position="right" />
                <el-input
                  v-model="reviews.reportForm.finalRecommendation"
                  type="textarea"
                  :rows="3"
                  placeholder="填写或调整最终评审意见"
                />
                <div class="manual-actions">
                  <el-button :disabled="!selectedReport" :loading="reviews.saving.value" @click="reviews.saveReport('ADJUSTED')">
                    保存调整
                  </el-button>
                  <el-button type="primary" :disabled="!selectedReport" :loading="reviews.saving.value" @click="reviews.saveReport('CONFIRMED')">
                    确认评审结果
                  </el-button>
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

    <el-dialog v-model="uploadDialogVisible" title="上传评审论文" width="min(560px, 92vw)" destroy-on-close>
      <div class="review-upload-form">
        <el-upload
          v-model:file-list="uploadFileList"
          drag
          :auto-upload="false"
          :limit="1"
          :show-file-list="true"
        >
          <div class="review-upload-drop">
            <strong>拖拽论文文件到这里</strong>
            <span>或点击选择文件；索引完成后自动生成评审任务。</span>
          </div>
        </el-upload>
        <el-input v-model="uploadTitle" clearable placeholder="论文标题，可留空使用文件名" />
      </div>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="reviews.uploading.value" @click="submitReviewUpload">提交入库</el-button>
      </template>
    </el-dialog>
  </MainLayout>
</template>

<style scoped>
.review-page {
  gap: 18px;
}

.review-upload-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
}

.review-upload-card p {
  margin: 0;
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 850;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.review-upload-card h2 {
  margin: 4px 0 6px;
  color: var(--app-text);
}

.review-upload-card span {
  color: var(--app-text-muted);
  line-height: 1.6;
}

.review-upload-form {
  display: grid;
  gap: 14px;
}

.review-upload-drop {
  display: grid;
  gap: 8px;
  color: var(--app-text-muted);
}

.review-upload-drop strong {
  color: var(--app-text);
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
.comments-card,
.risk-card {
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

.comments-risk-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 0.8fr);
  gap: 14px;
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
  .review-upload-card,
  .comments-risk-grid,
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
  .review-upload-card,
  .section-title {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
