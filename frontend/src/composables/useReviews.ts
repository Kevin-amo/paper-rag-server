import { computed, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  generateReviewReport,
  getReviewTask,
  listReviewRisks,
  listReviewCriteria,
  listReviewTasks,
  uploadReviewPaper,
  updateReviewReport,
  updateReviewRisk,
} from '../api/reviews';
import {
  getPaperStructuredParse,
  regeneratePaperStructuredParse,
} from '../api/documents';
import { getErrorMessage } from '../api/http';
import type {
  PaperStructuredParse,
  ReviewCriterion,
  ReviewReport,
  ReviewRiskRecord,
  ReviewReportStatus,
  ReviewScoreItem,
  ReviewTask,
  ReviewTaskStatus,
  UpdateReviewReportPayload,
  UploadReviewPaperPayload,
} from '../types';

export function useReviews() {
  const loading = ref(false);
  const detailLoading = ref(false);
  const generating = ref(false);
  const saving = ref(false);
  const uploading = ref(false);
  const structuredParseLoading = ref(false);
  const regeneratingStructuredParse = ref(false);
  const riskRecords = ref<ReviewRiskRecord[]>([]);
  const riskLoading = ref(false);
  const riskStatusUpdatingIds = ref<string[]>([]);
  const tasks = ref<ReviewTask[]>([]);
  const criteria = ref<ReviewCriterion[]>([]);
  const selectedTask = ref<ReviewTask | null>(null);
  const structuredParse = ref<PaperStructuredParse | null>(null);
  const keyword = ref('');
  const statusFilter = ref<ReviewTaskStatus | ''>('');
  const pagination = reactive({ page: 0, size: 20, total: 0 });
  const reportForm = reactive({
    totalScore: 0,
    finalRecommendation: '',
    status: 'ADJUSTED' as ReviewReportStatus,
  });

  const pendingCount = computed(() => tasks.value.filter((item) => item.status === 'PENDING').length);
  const reviewingCount = computed(() => tasks.value.filter((item) => item.status === 'REVIEWING').length);
  const completedCount = computed(() => tasks.value.filter((item) => item.status === 'COMPLETED').length);
  const selectedReport = computed(() => selectedTask.value?.report ?? null);
  let selectTaskRequestId = 0;
  let structuredParseRequestId = 0;
  let riskRequestId = 0;

  async function loadTasks(page = pagination.page) {
    loading.value = true;
    try {
      const result = await listReviewTasks({
        page,
        size: pagination.size,
        keyword: keyword.value.trim() || undefined,
        status: statusFilter.value || undefined,
      });
      tasks.value = result.items;
      pagination.page = result.page;
      pagination.size = result.size;
      pagination.total = result.total;
      if (!selectedTask.value && result.items.length) {
        await selectTask(result.items[0].id);
      } else if (selectedTask.value) {
        const exists = result.items.some((item) => item.id === selectedTask.value?.id);
        if (!exists && result.items.length) {
          await selectTask(result.items[0].id);
        } else if (!exists) {
          clearReviewSelection();
        }
      } else if (!result.items.length) {
        clearReviewSelection();
      }
    } catch (error) {
      ElMessage.error(getErrorMessage(error));
    } finally {
      loading.value = false;
    }
  }

  async function loadCriteria() {
    try {
      criteria.value = await listReviewCriteria(false);
    } catch (error) {
      ElMessage.error(getErrorMessage(error));
    }
  }

  async function selectTask(taskId: string) {
    const requestId = ++selectTaskRequestId;
    detailLoading.value = true;
    structuredParse.value = null;
    riskRecords.value = [];
    riskStatusUpdatingIds.value = [];
    structuredParseRequestId++;
    riskRequestId++;
    structuredParseLoading.value = false;
    riskLoading.value = false;
    try {
      const task = await getReviewTask(taskId);
      if (requestId !== selectTaskRequestId) {
        return;
      }
      selectedTask.value = task;
      syncReportForm(task.report);
      await loadStructuredParse(task.sourceId, false);
      if (requestId !== selectTaskRequestId || selectedTask.value?.id !== taskId) {
        return;
      }
      await loadRisks(task.report?.id ?? null);
    } catch (error) {
      if (requestId === selectTaskRequestId) {
        structuredParse.value = null;
        riskRecords.value = [];
        riskStatusUpdatingIds.value = [];
        ElMessage.error(getErrorMessage(error));
      }
    } finally {
      if (requestId === selectTaskRequestId) {
        detailLoading.value = false;
      }
    }
  }

  async function loadStructuredParse(sourceId: string, showError = true) {
    const requestId = ++structuredParseRequestId;
    structuredParseLoading.value = true;
    try {
      const result = await getPaperStructuredParse(sourceId);
      if (requestId === structuredParseRequestId && selectedTask.value?.sourceId === sourceId) {
        structuredParse.value = result;
      }
    } catch (error) {
      if (requestId === structuredParseRequestId && selectedTask.value?.sourceId === sourceId) {
        structuredParse.value = null;
      }
      if (showError && requestId === structuredParseRequestId) {
        ElMessage.error(getErrorMessage(error));
      }
    } finally {
      if (requestId === structuredParseRequestId) {
        structuredParseLoading.value = false;
      }
    }
  }

  async function rerunStructuredParse() {
    if (!selectedTask.value) {
      return;
    }
    const sourceId = selectedTask.value.sourceId;
    regeneratingStructuredParse.value = true;
    try {
      const result = await regeneratePaperStructuredParse(sourceId);
      if (selectedTask.value?.sourceId === sourceId) {
        structuredParse.value = result;
        ElMessage.success('结构化解析已重新生成');
      }
    } catch (error) {
      ElMessage.error(getErrorMessage(error));
    } finally {
      regeneratingStructuredParse.value = false;
    }
  }

  async function runAiReview() {
    if (!selectedTask.value) {
      return;
    }
    const taskId = selectedTask.value.id;
    generating.value = true;
    try {
      const report = await generateReviewReport(taskId);
      if (selectedTask.value?.id === taskId) {
        selectedTask.value = { ...selectedTask.value, status: 'REVIEWING', report };
        syncReportForm(report);
        await loadRisks(report.id);
        ElMessage.success('辅助评审报告已生成');
      }
      await loadTasks();
    } catch (error) {
      ElMessage.error(getErrorMessage(error));
    } finally {
      generating.value = false;
    }
  }

  async function saveReport(status: ReviewReportStatus = reportForm.status) {
    const report = selectedReport.value;
    if (!report || !selectedTask.value) {
      ElMessage.warning('请先生成辅助评审报告');
      return;
    }
    saving.value = true;
    try {
      const payload: UpdateReviewReportPayload = {
        paperSections: report.paperSections,
        scores: report.scores,
        comments: report.comments,
        risks: report.risks,
        totalScore: reportForm.totalScore,
        finalRecommendation: reportForm.finalRecommendation.trim() || null,
        status,
      };
      const nextReport = await updateReviewReport(report.id, payload);
      selectedTask.value = {
        ...selectedTask.value,
        status: status === 'CONFIRMED' || status === 'COMPLETED' ? 'COMPLETED' : 'REVIEWING',
        report: nextReport,
      };
      syncReportForm(nextReport);
      await loadTasks();
      ElMessage.success(status === 'CONFIRMED' || status === 'COMPLETED' ? '评审结果已确认' : '评审调整已保存');
    } catch (error) {
      ElMessage.error(getErrorMessage(error));
    } finally {
      saving.value = false;
    }
  }

  async function uploadPaper(payload: UploadReviewPaperPayload) {
    uploading.value = true;
    try {
      const result = await uploadReviewPaper(payload);
      ElMessage.success('评审论文已提交入库，索引完成后会自动进入任务池');
      await loadTasks(0);
      return result;
    } catch (error) {
      ElMessage.error(getErrorMessage(error));
      throw error;
    } finally {
      uploading.value = false;
    }
  }

  async function loadRisks(reportId: string | null) {
    const requestId = ++riskRequestId;
    riskRecords.value = [];
    riskStatusUpdatingIds.value = [];
    if (!reportId) {
      riskLoading.value = false;
      return;
    }
    riskLoading.value = true;
    try {
      const records = await listReviewRisks(reportId);
      if (requestId === riskRequestId && selectedReport.value?.id === reportId) {
        riskRecords.value = records;
      }
    } catch (error) {
      if (requestId === riskRequestId) {
        ElMessage.error(getErrorMessage(error));
      }
    } finally {
      if (requestId === riskRequestId) {
        riskLoading.value = false;
      }
    }
  }

  async function setRiskStatus(
    riskId: string,
    status: 'CONFIRMED' | 'IGNORED' | 'RESOLVED' | 'OPEN',
    reviewerNote?: string,
  ) {
    if (riskStatusUpdatingIds.value.includes(riskId)) {
      return;
    }
    const reportId = selectedReport.value?.id ?? null;
    if (!reportId || !riskRecords.value.some((item) => item.id === riskId && item.reportId === reportId)) {
      return;
    }
    riskStatusUpdatingIds.value = [...riskStatusUpdatingIds.value, riskId];
    try {
      const updated = await updateReviewRisk(riskId, { status, reviewerNote: reviewerNote ?? null });
      const stillCurrent = selectedReport.value?.id === reportId
        && updated.reportId === reportId
        && riskRecords.value.some((item) => item.id === riskId && item.reportId === reportId);
      if (stillCurrent) {
        riskRecords.value = riskRecords.value.map((item) => (
          item.id === riskId && item.reportId === reportId ? updated : item
        ));
        ElMessage.success('风险状态已更新');
      }
    } catch (error) {
      ElMessage.error(getErrorMessage(error));
    } finally {
      riskStatusUpdatingIds.value = riskStatusUpdatingIds.value.filter((id) => id !== riskId);
    }
  }

  function clearReviewSelection() {
    selectTaskRequestId++;
    structuredParseRequestId++;
    riskRequestId++;
    selectedTask.value = null;
    structuredParse.value = null;
    riskRecords.value = [];
    riskStatusUpdatingIds.value = [];
    detailLoading.value = false;
    structuredParseLoading.value = false;
    riskLoading.value = false;
    syncReportForm(null);
  }

  function updateScore(code: string, score: number) {
    const report = selectedReport.value;
    if (!report || !Array.isArray(report.scores)) {
      return;
    }
    const nextScores = report.scores.map((item) => {
      const scoreItem = item as ReviewScoreItem;
      return scoreItem.code === code ? { ...scoreItem, score } : scoreItem;
    });
    report.scores = nextScores;
    reportForm.totalScore = calculateAverageScore(nextScores);
  }

  function syncReportForm(report: ReviewReport | null) {
    reportForm.totalScore = report?.totalScore ?? calculateAverageScore(report?.scores);
    reportForm.finalRecommendation = report?.finalRecommendation ?? '';
    reportForm.status = report?.status === 'CONFIRMED' || report?.status === 'COMPLETED' ? report.status : 'ADJUSTED';
  }

  function calculateAverageScore(scores: unknown) {
    if (!Array.isArray(scores) || scores.length === 0) {
      return 0;
    }
    const values = scores
      .map((item) => Number((item as ReviewScoreItem).score))
      .filter((value) => Number.isFinite(value));
    if (!values.length) {
      return 0;
    }
    return Math.round(values.reduce((sum, value) => sum + value, 0) / values.length);
  }

  return {
    loading,
    detailLoading,
    generating,
    saving,
    uploading,
    structuredParseLoading,
    regeneratingStructuredParse,
    riskRecords,
    riskLoading,
    riskStatusUpdatingIds,
    tasks,
    criteria,
    selectedTask,
    structuredParse,
    selectedReport,
    keyword,
    statusFilter,
    pagination,
    reportForm,
    pendingCount,
    reviewingCount,
    completedCount,
    loadTasks,
    loadCriteria,
    selectTask,
    runAiReview,
    saveReport,
    uploadPaper,
    loadStructuredParse,
    loadRisks,
    setRiskStatus,
    rerunStructuredParse,
    updateScore,
  };
}
