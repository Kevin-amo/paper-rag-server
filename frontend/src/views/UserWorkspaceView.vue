<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import MainLayout from '../layouts/MainLayout.vue';
import PageHeader from '../components/common/PageHeader.vue';
import UploadPanel from '../components/UploadPanel.vue';
import DocumentTable from '../components/DocumentTable.vue';
import DocumentDetailDrawer from '../components/DocumentDetailDrawer.vue';
import RagChatPanel from '../components/RagChatPanel.vue';
import { apiPrefix } from '../api/http';
import { useAuth } from '../composables/useAuth';
import { useDocuments } from '../composables/useDocuments';
import { useConversations } from '../composables/useConversations';
import { useRagChat } from '../composables/useRagChat';

const router = useRouter();
const auth = useAuth();
const documentsState = useDocuments();
const conversationsState = useConversations();
const ragState = useRagChat({
  activeConversationId: conversationsState.activeConversationId,
  conversationMessages: conversationsState.conversationMessages,
  loadConversations: () => conversationsState.loadConversations(),
  loadMessages: conversationsState.loadMessages,
});

const requestPrefixText = computed(() => (apiPrefix ? apiPrefix : 'Vite 代理直连'));
const currentUserName = computed(() => auth.state.user?.displayName || auth.state.user?.username || '当前用户');
const successUploadDescription = computed(() => {
  const result = documentsState.lastBatchUploadResult.value;
  if (!result) {
    return '';
  }
  return result.items.filter((item) => item.success).map((item) => item.source?.sourceId || item.fileName).join('、') || '暂无成功项';
});

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
  await Promise.all([documentsState.loadDocuments(0), conversationsState.loadConversations(true)]);
});
</script>

<template>
  <MainLayout>
    <PageHeader
      eyebrow="User Workspace"
      title="论文知识库与 RAG 问答"
      description="上传、解析并管理个人论文文档，在持久化会话中基于私有知识库完成检索增强问答。"
    >
      <template #actions>
        <el-tag type="info" size="large">接口前缀：{{ requestPrefixText }}</el-tag>
        <el-tag type="success" size="large">{{ currentUserName }} · 普通用户</el-tag>
        <el-button v-if="auth.isAdmin.value" @click="router.push('/admin')">管理后台</el-button>
        <el-button @click="handleLogout">退出登录</el-button>
      </template>
    </PageHeader>

    <section class="workspace-summary">
      <div class="metric-card">
        <span>文档总数</span>
        <strong>{{ documentsState.pagination.total }}</strong>
      </div>
      <div class="metric-card">
        <span>当前页文档</span>
        <strong>{{ documentsState.documents.value.length }}</strong>
      </div>
      <div class="metric-card">
        <span>会话数量</span>
        <strong>{{ conversationsState.conversations.value.length }}</strong>
      </div>
      <div class="metric-card soft">
        <span>当前用户</span>
        <strong>{{ currentUserName }}</strong>
      </div>
    </section>

    <UploadPanel
      :loading="documentsState.uploadLoading.value"
      :result="documentsState.lastBatchUploadResult.value"
      :error-message="documentsState.uploadErrorMessage.value"
      @submit="documentsState.uploadBatch"
      @remove="documentsState.removeUploadedSource"
    />

    <el-alert
      v-if="documentsState.lastBatchUploadResult.value"
      class="result-alert"
      type="success"
      show-icon
      :closable="false"
      :title="`最近一次批量上传：成功 ${documentsState.lastBatchUploadResult.value.successCount} 个，失败 ${documentsState.lastBatchUploadResult.value.failureCount} 个`"
      :description="successUploadDescription"
    />

    <section class="grid-layout">
      <DocumentTable
        :documents="documentsState.documents.value"
        :loading="documentsState.documentsLoading.value"
        :keyword="documentsState.keyword.value"
        :page="documentsState.pagination.page"
        :size="documentsState.pagination.size"
        :total="documentsState.pagination.total"
        :deleting-source-id="documentsState.deletingSourceId.value"
        :can-delete="true"
        @search="documentsState.search"
        @page-change="documentsState.loadDocuments"
        @row-click="documentsState.openDetail"
        @refresh="documentsState.loadDocuments(0)"
        @delete="documentsState.removeDocument"
      />

      <RagChatPanel
        :loading="ragState.ragLoading.value"
        :conversations="conversationsState.conversations.value"
        :messages="conversationsState.conversationMessages.value"
        :active-conversation-id="conversationsState.activeConversationId.value"
        :conversations-loading="conversationsState.conversationsLoading.value"
        :messages-loading="conversationsState.messagesLoading.value"
        :cleaning-conversations="conversationsState.cleaningConversations.value"
        @submit="ragState.ask"
        @create-conversation="conversationsState.createNewConversation"
        @clean-empty-conversations="conversationsState.cleanEmptyConversations"
        @select-conversation="conversationsState.selectConversation"
        @delete-conversation="conversationsState.removeConversation"
      />
    </section>

    <DocumentDetailDrawer
      v-model="documentsState.detailVisible.value"
      :detail="documentsState.detail.value"
      :chunks="documentsState.chunks.value"
      :loading="documentsState.detailLoading.value"
      :chunk-loading="documentsState.chunkLoading.value"
      :chunk-page="documentsState.chunkPagination.page"
      :chunk-size="documentsState.chunkPagination.size"
      :chunk-total="documentsState.chunkPagination.total"
      @chunk-page-change="documentsState.changeChunkPage"
      @chunk-size-change="documentsState.changeChunkSize"
    />
  </MainLayout>
</template>

<style scoped>
.workspace-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.metric-card {
  padding: 18px;
  border: 1px solid rgba(37, 99, 235, 0.12);
  border-radius: 20px;
  background: linear-gradient(135deg, #eff6ff, #fff);
}

.metric-card.soft {
  background: linear-gradient(135deg, #f8fafc, #fff);
}

.metric-card span {
  display: block;
  color: var(--app-text-muted);
  font-size: 13px;
}

.metric-card strong {
  display: block;
  overflow: hidden;
  margin-top: 8px;
  color: var(--app-text);
  font-size: clamp(22px, 3vw, 30px);
  line-height: 1.1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-alert {
  border-radius: 18px;
}

.grid-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(420px, 0.95fr);
  gap: 20px;
  align-items: start;
}

@media (max-width: 1180px) {
  .grid-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 780px) {
  .workspace-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 520px) {
  .workspace-summary {
    grid-template-columns: 1fr;
  }
}
</style>