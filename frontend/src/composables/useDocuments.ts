import { reactive, ref } from 'vue';
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

  async function loadDocuments(page = pagination.page) {
    documentsLoading.value = true;
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
      ElMessage.error(getErrorMessage(error));
    } finally {
      documentsLoading.value = false;
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
      const { successCount, failureCount } = lastBatchUploadResult.value;
      if (successCount > 0 && failureCount === 0) {
        ElMessage.success(`批量上传成功，共 ${successCount} 个文件`);
      } else if (successCount > 0) {
        ElMessage.warning(`上传完成：成功 ${successCount} 个，失败 ${failureCount} 个`);
      } else {
        ElMessage.error('批量上传失败，请检查文件格式或后端日志');
      }
      await loadDocuments(0);
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