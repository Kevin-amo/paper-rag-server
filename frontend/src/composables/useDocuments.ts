import { onUnmounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  deleteDocument,
  getDocumentChunks,
  getDocumentDetail,
  listDocuments,
  uploadDocumentsBatch,
} from '../api/documents';
import { getErrorMessage } from '../api/http';
import type {
  BatchDocumentIngestionResponse,
  BatchUploadDocumentPayload,
  DocumentChunk,
  DocumentDetail,
  DocumentSummary,
} from '../types';

export function useDocuments() {
  const uploadLoading = ref(false);
  const documentsLoading = ref(false);
  const detailLoading = ref(false);
  const chunkLoading = ref(false);
  const deletingSourceId = ref<string | null>(null);

  const keyword = ref('');
  const documents = ref<DocumentSummary[]>([]);
  const detail = ref<DocumentDetail | null>(null);
  const chunks = ref<DocumentChunk[]>([]);
  const detailVisible = ref(false);
  const selectedSourceId = ref<string | null>(null);
  const lastBatchUploadResult = ref<BatchDocumentIngestionResponse | null>(null);
  const uploadErrorMessage = ref('');

  const pagination = reactive({ page: 0, size: 10, total: 0 });
  const chunkPagination = reactive({ page: 0, size: 50, total: 0 });
  let uploadRefreshTimer: ReturnType<typeof window.setTimeout> | null = null;

  function stopUploadRefreshPolling() {
    if (uploadRefreshTimer !== null) {
      window.clearTimeout(uploadRefreshTimer);
      uploadRefreshTimer = null;
    }
  }

  function startUploadRefreshPolling(sourceIds: string[]) {
    stopUploadRefreshPolling();

    const pendingSourceIds = new Set(sourceIds.filter(Boolean));
    if (!pendingSourceIds.size) {
      return;
    }

    let remainingAttempts = 10;
    const poll = async () => {
      await loadDocuments(0, { silent: true, suppressError: true });

      const activeStatuses = new Set(['PENDING', 'PROCESSING', 'INDEXING']);
      const trackedDocuments = documents.value.filter((document) => pendingSourceIds.has(document.sourceId));
      const shouldContinue = trackedDocuments.length < pendingSourceIds.size || trackedDocuments.some((document) => (
        activeStatuses.has(String(document.status).toUpperCase())
      ));

      remainingAttempts -= 1;
      if (shouldContinue && remainingAttempts > 0) {
        uploadRefreshTimer = window.setTimeout(poll, 1500);
      } else {
        uploadRefreshTimer = null;
      }
    };

    uploadRefreshTimer = window.setTimeout(poll, 500);
  }

  async function loadDocuments(page = pagination.page, options: { silent?: boolean; suppressError?: boolean } = {}) {
    if (!options.silent) {
      documentsLoading.value = true;
    }
    try {
      const result = await listDocuments({
        keyword: keyword.value || undefined,
        page,
        size: pagination.size,
      });
      documents.value = result.items;
      pagination.page = result.page;
      pagination.size = result.size;
      pagination.total = result.total;
    } catch (error) {
      if (!options.suppressError) {
        ElMessage.error(getErrorMessage(error));
      }
    } finally {
      if (!options.silent) {
        documentsLoading.value = false;
      }
    }
  }

  async function loadDocumentDetail(sourceId: string) {
    detailLoading.value = true;
    try {
      detail.value = await getDocumentDetail(sourceId);
    } catch (error) {
      ElMessage.error(getErrorMessage(error));
    } finally {
      detailLoading.value = false;
    }
  }

  async function loadDocumentChunks(sourceId: string, page = chunkPagination.page, size = chunkPagination.size) {
    chunkLoading.value = true;
    try {
      const result = await getDocumentChunks(sourceId, { page, size });
      chunks.value = result.items;
      chunkPagination.page = result.page;
      chunkPagination.size = result.size;
      chunkPagination.total = result.total;
    } catch (error) {
      ElMessage.error(getErrorMessage(error));
    } finally {
      chunkLoading.value = false;
    }
  }

  async function uploadBatch(payload: BatchUploadDocumentPayload) {
    uploadLoading.value = true;
    uploadErrorMessage.value = '';
    lastBatchUploadResult.value = null;

    try {
      lastBatchUploadResult.value = await uploadDocumentsBatch(payload);
      const { acceptedCount, failureCount } = lastBatchUploadResult.value;
      if (acceptedCount > 0 && failureCount === 0) {
        ElMessage.success(`上传请求已提交，共 ${acceptedCount} 个文件进入处理队列`);
      } else if (acceptedCount > 0) {
        ElMessage.warning(`上传请求已提交：已入队 ${acceptedCount} 个，失败 ${failureCount} 个`);
      } else {
        ElMessage.error('上传请求提交失败，请检查文件格式或后端日志');
      }
      await loadDocuments(0);
      startUploadRefreshPolling(lastBatchUploadResult.value.items
        .filter((item) => item.accepted && item.sourceId)
        .map((item) => item.sourceId as string));
    } catch (error) {
      const message = getErrorMessage(error);
      uploadErrorMessage.value = `上传请求失败，后端可能仍在处理；请刷新文档列表确认状态。${message}`;
      ElMessage.error(uploadErrorMessage.value);
      await loadDocuments(0);
    } finally {
      uploadLoading.value = false;
    }
  }

  async function removeUploadedSource(sourceId: string) {
    try {
      await deleteDocument(sourceId);
      await loadDocuments(0);
    } catch (error) {
      ElMessage.error(getErrorMessage(error));
    }
  }

  async function removeDocument(document: DocumentSummary) {
    deletingSourceId.value = document.sourceId;
    try {
      await deleteDocument(document.sourceId);
      ElMessage.success('文档已删除');

      if (detail.value?.sourceId === document.sourceId) {
        closeDetail();
      }

      const nextPage = documents.value.length === 1 && pagination.page > 0 ? pagination.page - 1 : pagination.page;
      await loadDocuments(nextPage);
    } catch (error) {
      ElMessage.error(getErrorMessage(error));
    } finally {
      deletingSourceId.value = null;
    }
  }

  async function openDetail(document: DocumentSummary) {
    detailVisible.value = true;
    detail.value = null;
    selectedSourceId.value = document.sourceId;
    chunks.value = [];
    chunkPagination.page = 0;
    chunkPagination.total = 0;
    await Promise.all([loadDocumentDetail(document.sourceId), loadDocumentChunks(document.sourceId, 0)]);
  }

  function closeDetail() {
    detailVisible.value = false;
    detail.value = null;
    selectedSourceId.value = null;
    chunks.value = [];
    chunkPagination.page = 0;
    chunkPagination.total = 0;
  }

  function changeChunkPage(page: number) {
    if (selectedSourceId.value) {
      void loadDocumentChunks(selectedSourceId.value, page);
    }
  }

  function changeChunkSize(size: number) {
    if (selectedSourceId.value) {
      void loadDocumentChunks(selectedSourceId.value, 0, size);
    }
  }

  function search(nextKeyword: string) {
    keyword.value = nextKeyword.trim();
    void loadDocuments(0);
  }

  onUnmounted(stopUploadRefreshPolling);

  return {
    uploadLoading,
    documentsLoading,
    detailLoading,
    chunkLoading,
    deletingSourceId,
    keyword,
    documents,
    detail,
    chunks,
    detailVisible,
    selectedSourceId,
    lastBatchUploadResult,
    uploadErrorMessage,
    pagination,
    chunkPagination,
    loadDocuments,
    loadDocumentDetail,
    loadDocumentChunks,
    uploadBatch,
    removeUploadedSource,
    removeDocument,
    openDetail,
    closeDetail,
    changeChunkPage,
    changeChunkSize,
    search,
  };
}