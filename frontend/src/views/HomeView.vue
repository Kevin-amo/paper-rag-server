<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import UploadPanel from '../components/UploadPanel.vue';
import DocumentTable from '../components/DocumentTable.vue';
import DocumentDetailDrawer from '../components/DocumentDetailDrawer.vue';
import RagChatPanel from '../components/RagChatPanel.vue';
import AdminUsersPanel from '../components/AdminUsersPanel.vue';
import { deleteDocument, getDocumentChunks, getDocumentDetail, listDocuments, uploadDocumentsBatch } from '../api/documents';
import { apiPrefix, getErrorMessage } from '../api/http';
import { askQuestion } from '../api/rag';
import { useAuth } from '../composables/useAuth';
import type {
  BatchDocumentIngestionResponse,
  BatchUploadDocumentPayload,
  DocumentChunk,
  DocumentDetail,
  DocumentSummary,
  RagAnswer,
} from '../types';

const router = useRouter();
const auth = useAuth();
const uploadLoading = ref(false);
const documentsLoading = ref(false);
const detailLoading = ref(false);
const chunkLoading = ref(false);
const ragLoading = ref(false);
const deletingSourceId = ref<string | null>(null);
const adminUsersVisible = ref(false);

const keyword = ref('');
const documents = ref<DocumentSummary[]>([]);
const detail = ref<DocumentDetail | null>(null);
const chunks = ref<DocumentChunk[]>([]);
const ragAnswer = ref<RagAnswer | null>(null);
const detailVisible = ref(false);
const lastBatchUploadResult = ref<BatchDocumentIngestionResponse | null>(null);
const uploadErrorMessage = ref('');

const pagination = reactive({
  page: 0,
  size: 10,
  total: 0,
});

const chunkPagination = reactive({
  page: 0,
  size: 50,
  total: 0,
});
const selectedSourceId = ref<string | null>(null);

const requestPrefixText = computed(() => (apiPrefix ? apiPrefix : '直连控制器路径'));
const isAdmin = computed(() => auth.isAdmin.value);
const currentUserName = computed(() => auth.state.user?.displayName || auth.state.user?.username || '当前用户');

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

async function handleUpload(payload: BatchUploadDocumentPayload) {
  uploadLoading.value = true;
  uploadErrorMessage.value = '';
  lastBatchUploadResult.value = null;

  try {
    lastBatchUploadResult.value = await uploadDocumentsBatch(payload);
    if (lastBatchUploadResult.value.successCount > 0 && lastBatchUploadResult.value.failureCount === 0) {
      ElMessage.success(`批量上传成功，共 ${lastBatchUploadResult.value.successCount} 个文件`);
    } else if (lastBatchUploadResult.value.successCount > 0) {
      ElMessage.warning(`上传完成：成功 ${lastBatchUploadResult.value.successCount} 个，失败 ${lastBatchUploadResult.value.failureCount} 个`);
    } else {
      ElMessage.error('批量上传失败');
    }
    await loadDocuments(0);
  } catch (error) {
    const errorMessage = getErrorMessage(error);
    uploadErrorMessage.value = `上传请求失败，后端可能仍在处理；请刷新文档列表确认状态。${errorMessage}`;
    ElMessage.error(uploadErrorMessage.value);
    await loadDocuments(0);
  } finally {
    uploadLoading.value = false;
  }
}

async function handleUploadRemove(sourceId: string) {
  if (!isAdmin.value) {
    return;
  }
  try {
    await deleteDocument(sourceId);
    await loadDocuments(0);
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  }
}

async function handleDelete(document: DocumentSummary) {
  if (!isAdmin.value) {
    ElMessage.warning('只有管理员可以删除文档');
    return;
  }
  deletingSourceId.value = document.sourceId;

  try {
    await deleteDocument(document.sourceId);
    ElMessage.success('文档已移除');

    if (detail.value?.sourceId === document.sourceId) {
      detailVisible.value = false;
      detail.value = null;
      selectedSourceId.value = null;
      chunks.value = [];
      chunkPagination.page = 0;
      chunkPagination.total = 0;
    }

    const nextPage = documents.value.length === 1 && pagination.page > 0 ? pagination.page - 1 : pagination.page;
    await loadDocuments(nextPage);
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    deletingSourceId.value = null;
  }
}

async function handleOpenDetail(document: DocumentSummary) {
  detailVisible.value = true;
  detail.value = null;
  selectedSourceId.value = document.sourceId;
  chunks.value = [];
  chunkPagination.page = 0;
  chunkPagination.total = 0;

  await Promise.all([loadDocumentDetail(document.sourceId), loadDocumentChunks(document.sourceId, 0)]);
}

function handleChunkPageChange(page: number) {
  if (!selectedSourceId.value) {
    return;
  }
  void loadDocumentChunks(selectedSourceId.value, page);
}

function handleChunkSizeChange(size: number) {
  if (!selectedSourceId.value) {
    return;
  }
  void loadDocumentChunks(selectedSourceId.value, 0, size);
}

async function handleAsk(payload: { question: string; topK?: number }) {
  ragLoading.value = true;

  try {
    ragAnswer.value = await askQuestion(payload);
    ElMessage.success('问答完成');
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    ragLoading.value = false;
  }
}

function handleSearch(nextKeyword: string) {
  keyword.value = nextKeyword.trim();
  void loadDocuments(0);
}

async function handleLogout() {
  await auth.logout();
  await router.replace('/login');
}

onMounted(async () => {
  try {
    await auth.hydrateCurrentUser();
  } catch {
    await router.replace('/login');
    return;
  }
  await loadDocuments(0);
});
</script>

<template>
  <div class="page-shell">
    <header class="hero-card">
      <div>
        <p class="eyebrow">Vue 3 + Element Plus</p>
        <h1>Paper RAG 前后端联调 Demo</h1>
        <p class="hero-desc">
          这个页面直接对接现有 Spring Boot 接口，覆盖上传、列表、详情分片和问答四条链路。
        </p>
      </div>
      <div class="hero-meta">
        <el-tag type="info" size="large">请求前缀：{{ requestPrefixText }}</el-tag>
        <el-tag type="success" size="large">{{ currentUserName }} · {{ isAdmin ? '管理员' : '普通用户' }}</el-tag>
        <div class="hero-actions">
          <el-button v-if="isAdmin" @click="adminUsersVisible = true">用户管理</el-button>
          <el-button @click="handleLogout">退出登录</el-button>
        </div>
      </div>
    </header>

    <UploadPanel
      v-if="isAdmin"
      :loading="uploadLoading"
      :result="lastBatchUploadResult"
      :error-message="uploadErrorMessage"
      @submit="handleUpload"
      @remove="handleUploadRemove"
    />

    <el-alert
      v-if="lastBatchUploadResult"
      class="result-alert"
      type="success"
      show-icon
      :closable="false"
      :title="`最近一次批量上传：成功 ${lastBatchUploadResult.successCount} 个，失败 ${lastBatchUploadResult.failureCount} 个`"
      :description="lastBatchUploadResult.items.filter((item) => item.success).map((item) => item.source?.sourceId || item.fileName).join('、') || '暂无成功项'"
    />

    <section class="grid-layout">
      <div class="grid-left">
        <DocumentTable
          :documents="documents"
          :loading="documentsLoading"
          :keyword="keyword"
          :page="pagination.page"
          :size="pagination.size"
          :total="pagination.total"
          :deleting-source-id="deletingSourceId"
          :can-delete="isAdmin"
          @search="handleSearch"
          @page-change="loadDocuments"
          @row-click="handleOpenDetail"
          @refresh="loadDocuments(0)"
          @delete="handleDelete"
        />
      </div>

      <div class="grid-right">
        <RagChatPanel :loading="ragLoading" :answer="ragAnswer" @submit="handleAsk" />
      </div>
    </section>

    <DocumentDetailDrawer
      v-model="detailVisible"
      :detail="detail"
      :chunks="chunks"
      :loading="detailLoading"
      :chunk-loading="chunkLoading"
      :chunk-page="chunkPagination.page"
      :chunk-size="chunkPagination.size"
      :chunk-total="chunkPagination.total"
      @chunk-page-change="handleChunkPageChange"
      @chunk-size-change="handleChunkSizeChange"
    />

    <AdminUsersPanel v-model="adminUsersVisible" />
  </div>
</template>

<style scoped>
.page-shell {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 28px;
}

.hero-card {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  padding: 28px 32px;
  border-radius: 24px;
  background: linear-gradient(135deg, #1d4ed8 0%, #2563eb 45%, #60a5fa 100%);
  color: #fff;
  box-shadow: 0 20px 45px rgba(37, 99, 235, 0.22);
}

.eyebrow {
  margin: 0 0 8px;
  font-size: 13px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.8);
}

.hero-card h1 {
  margin: 0;
  font-size: 32px;
}

.hero-desc {
  max-width: 720px;
  margin: 12px 0 0;
  color: rgba(255, 255, 255, 0.92);
}

.hero-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
}

.hero-actions {
  display: flex;
  gap: 10px;
}

.result-alert {
  border-radius: 18px;
}

.grid-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(380px, 0.9fr);
  gap: 20px;
  align-items: start;
}

.grid-left,
.grid-right {
  min-width: 0;
}

@media (max-width: 1400px) {
  .grid-layout {
    grid-template-columns: 1fr;
  }

  .hero-card {
    flex-direction: column;
  }

  .hero-meta {
    align-items: flex-start;
  }
}
</style>