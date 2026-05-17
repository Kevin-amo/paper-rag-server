<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import ChatSidebar from '../components/chat/ChatSidebar.vue';
import RagChatWorkspace from '../components/chat/RagChatWorkspace.vue';
import DocumentLibraryDrawer from '../components/documents/DocumentLibraryDrawer.vue';
import UploadDocumentDialog from '../components/documents/UploadDocumentDialog.vue';
import DocumentDetailDrawer from '../components/documents/DocumentDetailDrawer.vue';
import { useAuth } from '../composables/useAuth';
import { useDocuments } from '../composables/useDocuments';
import { useConversations } from '../composables/useConversations';
import { useRagChat } from '../composables/useRagChat';

const router = useRouter();
const auth = useAuth();
const documentsState = useDocuments();
const conversationsState = useConversations();
const documentLibraryVisible = ref(false);
const uploadVisible = ref(false);

const ragState = useRagChat({
  activeConversationId: conversationsState.activeConversationId,
  conversationMessages: conversationsState.conversationMessages,
  conversations: conversationsState.conversations,
  loadConversations: () => conversationsState.loadConversations(),
  loadMessages: conversationsState.loadMessages,
});

const currentUserName = computed(() => auth.state.user?.displayName || auth.state.user?.username || '当前用户');
const activeConversation = computed(() => (
  conversationsState.conversations.value.find((item) => item.id === conversationsState.activeConversationId.value) ?? null
));
const visibleMessages = computed(() => conversationsState.conversationMessages.value
  .filter((message) => {
    const content = message.content.trim();
    return message.streaming || (content && content !== '-') || message.citations?.length;
  })
  .sort((a, b) => a.messageOrder - b.messageOrder));

async function handleLogout() {
  await auth.logout();
  await router.replace('/login');
}

async function handleUpload(payload: Parameters<typeof documentsState.uploadBatch>[0]) {
  await documentsState.uploadBatch(payload);
  documentLibraryVisible.value = true;
}

async function openDocumentDetail(document: Parameters<typeof documentsState.openDetail>[0]) {
  await documentsState.openDetail(document);
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
  <div class="user-app-shell">
    <ChatSidebar
      :conversations="conversationsState.conversations.value"
      :active-conversation-id="conversationsState.activeConversationId.value"
      :conversations-loading="conversationsState.conversationsLoading.value"
      :cleaning-conversations="conversationsState.cleaningConversations.value"
      :current-user-name="currentUserName"
      :is-admin="auth.isAdmin.value"
      @create-conversation="conversationsState.createNewConversation"
      @select-conversation="conversationsState.selectConversation"
      @delete-conversation="conversationsState.removeConversation"
      @open-documents="documentLibraryVisible = true"
      @clean-empty-conversations="conversationsState.cleanEmptyConversations"
      @go-admin="router.push('/admin')"
      @logout="handleLogout"
    />

    <RagChatWorkspace
      :loading="ragState.ragLoading.value"
      :messages="visibleMessages"
      :active-conversation="activeConversation"
      :messages-loading="conversationsState.messagesLoading.value"
      :document-total="documentsState.pagination.total"
      @submit="ragState.ask"
      @open-documents="documentLibraryVisible = true"
      @open-upload="uploadVisible = true"
    />

    <DocumentLibraryDrawer
      v-model="documentLibraryVisible"
      :documents="documentsState.documents.value"
      :loading="documentsState.documentsLoading.value"
      :keyword="documentsState.keyword.value"
      :page="documentsState.pagination.page"
      :size="documentsState.pagination.size"
      :total="documentsState.pagination.total"
      :deleting-source-id="documentsState.deletingSourceId.value"
      @search="documentsState.search"
      @page-change="documentsState.loadDocuments"
      @row-click="openDocumentDetail"
      @refresh="documentsState.loadDocuments(0)"
      @delete="documentsState.removeDocument"
      @upload="uploadVisible = true"
    />

    <UploadDocumentDialog
      v-model="uploadVisible"
      :loading="documentsState.uploadLoading.value"
      :result="documentsState.lastBatchUploadResult.value"
      :error-message="documentsState.uploadErrorMessage.value"
      @submit="handleUpload"
    />

    <DocumentDetailDrawer
      v-model="documentsState.detailVisible.value"
      :detail="documentsState.detail.value"
      :loading="documentsState.detailLoading.value"
    />
  </div>
</template>

<style scoped>
.user-app-shell {
  min-height: 100vh;
  display: flex;
  overflow: hidden;
  background:
    radial-gradient(circle at 70% 0, rgba(37, 99, 235, 0.1), transparent 30rem),
    #f8fafc;
}

@media (max-width: 900px) {
  .user-app-shell {
    flex-direction: column;
    overflow: visible;
  }
}
</style>