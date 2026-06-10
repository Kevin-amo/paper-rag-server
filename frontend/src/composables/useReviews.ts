import { computed, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  generateReviewReport,
  getReviewTaskStructuredParse,
  getReviewTask,
  listReviewRisks,
  listReviewCriteria,
  listReviewTasks,
  regenerateReviewTaskStructuredParse,
  submitReviewAssignment,
  updateReviewReport,
  updateReviewRisk,
  uploadReviewPaper,
  getReviewUploadJob,
} from '../api/reviews';
import { getErrorMessage } from '../api/http';
import type {
  PaperStructuredParse,
  ReviewCriterion,
  ReviewReport,
  ReviewRiskRecord,
  ReviewReportStatus,
  ReviewScoreItem,
  ReviewTask,
  ReviewAssignmentStatus,
  UpdateReviewReportPayload,
} from '../types';

export function useReviews() {
  const loading = ref(false);
  const detailLoading = ref(false);
  const generating = ref(false);
  const uploading = ref(false);
  const saving = ref(false);
  const submittingAssignment = ref(false);
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
  const statusFilter = ref<ReviewAssignmentStatus | ''>('');
  const pagination = reactive({ page: 0, size: 20, total: 0 });
  const reportForm = reactive({
    totalScore: 0,
    finalRecommendation: '',
    status: 'ADJUSTED' as ReviewReportStatus,
  });

  const pendingCount = computed(() => tasks.value.filter((item) => (
    item.currentAssignment?.status === 'ASSIGNED' || (!item.currentAssignment && item.status === 'PENDING_ASSIGNMENT')
  )).length);
  const reviewingCount = computed(() => tasks.value.filter((item) => item.currentAssignment?.status === 'REVIEWING').length);
  const completedCount = computed(() => tasks.value.filter((item) => item.currentAssignment?.status === 'SUBMITTED').length);
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

  async function uploadPaper(file: File) {
    if (uploading.value) {
      return;
    }
    uploading.value = true;
    try {
      const result = await uploadReviewPaper({
        file,
        title: file.name.replace(/\.[^.]+$/, ''),
      });
      await waitForUploadIndexed(result.jobId);
      await loadTasks(0);
      ElMessage.success('待评审论文已入库');
    } catch (error) {
      ElMessage.error(getErrorMessage(error));
    } finally {
      uploading.value = false;
    }
  }

  async function waitForUploadIndexed(jobId: string) {
    const maxAttempts = 20;
    for (let attempt = 0; attempt < maxAttempts; attempt++) {
      const job = await getReviewUploadJob(jobId);
      if (job.status === 'INDEXED') {
        return;
      }
      if (job.status === 'FAILED') {
        throw new Error(job.errorMessage || '论文入库失败');
      }
      await new Promise((resolve) => { setTimeout(resolve, 1500); });
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
      await loadStructuredParse(task.id, false);
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

  async function loadStructuredParse(taskId: string, showError = true) {
    const requestId = ++structuredParseRequestId;
    structuredParseLoading.value = true;
    try {
      const result = await getReviewTaskStructuredParse(taskId);
      if (requestId === structuredParseRequestId && selectedTask.value?.id === taskId) {
        structuredParse.value = result;
      }
    } catch (error) {
      if (requestId === structuredParseRequestId && selectedTask.value?.id === taskId) {
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
    const taskId = selectedTask.value.id;
    regeneratingStructuredParse.value = true;
    try {
      const result = await regenerateReviewTaskStructuredParse(taskId);
      if (selectedTask.value?.id === taskId) {
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
        const currentAssignment = selectedTask.value.currentAssignment
          ? { ...selectedTask.value.currentAssignment, status: 'REVIEWING' as const }
          : null;
        selectedTask.value = { ...selectedTask.value, status: 'IN_REVIEW', currentAssignment, report };
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

  async function saveReport() {
    const report = selectedReport.value;
    const task = selectedTask.value;
    if (!report || !task) {
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
        status: 'ADJUSTED',
      };
      const nextReport = await updateReviewReport(report.id, payload);
      const currentAssignment = task.currentAssignment
        ? { ...task.currentAssignment, status: 'REVIEWING' as const }
        : null;
      selectedTask.value = {
        ...task,
        status: currentAssignment ? 'IN_REVIEW' : 'REVIEWING',
        currentAssignment,
        report: nextReport,
      };
      syncReportForm(nextReport);
      await loadTasks();
      ElMessage.success('评审调整已保存');
    } catch (error) {
      ElMessage.error(getErrorMessage(error));
    } finally {
      saving.value = false;
    }
  }

  async function submitCurrentAssignment() {
    if (submittingAssignment.value) {
      return;
    }

    const taskId = selectedTask.value?.id;
    const assignmentId = selectedTask.value?.currentAssignment?.id;
    if (!taskId || !assignmentId) {
      ElMessage.warning('当前没有可提交的评审任务');
      return;
    }

    submittingAssignment.value = true;
    try {
      await submitReviewAssignment(assignmentId);
      await loadTasks();
      if (selectedTask.value?.id === taskId) {
        await selectTask(taskId);
      }
      ElMessage.success('评审已提交');
    } catch (error) {
      ElMessage.error(getErrorMessage(error));
    } finally {
      submittingAssignment.value = false;
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
    uploading,
    saving,
    submittingAssignment,
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
    uploadPaper,
    selectTask,
    runAiReview,
    saveReport,
    submitCurrentAssignment,
    loadStructuredParse,
    loadRisks,
    setRiskStatus,
    rerunStructuredParse,
    updateScore,
  };
}


