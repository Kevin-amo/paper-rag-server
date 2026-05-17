<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { useRouter } from 'vue-router';
import ChatSidebar from '../components/chat/ChatSidebar.vue';
import RagChatWorkspace from '../components/chat/RagChatWorkspace.vue';
import DocumentLibraryDrawer from '../components/documents/DocumentLibraryDrawer.vue';
import UploadDocumentDialog from '../components/documents/UploadDocumentDialog.vue';
import DocumentDetailDrawer from '../components/documents/DocumentDetailDrawer.vue';
import AvatarUploadDialog from '../components/user/AvatarUploadDialog.vue';
import { getErrorMessage } from '../api/http';
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
const avatarUploadVisible = ref(false);
const avatarUploading = ref(false);
const avatarRefreshVersion = ref(0);

const ragState = useRagChat({
  activeConversationId: conversationsState.activeConversationId,
  conversationMessages: conversationsState.conversationMessages,
  conversations: conversationsState.conversations,
  loadConversations: () => conversationsState.loadConversations(),
  loadMessages: conversationsState.loadMessages,
});

const currentUserName = computed(() => auth.state.user?.displayName || auth.state.user?.username || '当前用户');
const currentUserAvatarUrl = computed(() => buildAvatarDisplayUrl(auth.state.user?.avatarUrl ?? null));
const activeConversation = computed(() => (
  conversationsState.conversations.value.find((item) => item.id === conversationsState.activeConversationId.value) ?? null
));
const visibleMessages = computed(() => conversationsState.conversationMessages.value
  .filter((message) => {
    const content = message.content.trim();
    return message.streaming || (content && content !== '-') || message.citations?.length;
  })
  .sort((a, b) => a.messageOrder - b.messageOrder));

function buildAvatarDisplayUrl(avatarUrl: string | null) {
  if (!avatarUrl || !avatarRefreshVersion.value) {
    return avatarUrl;
  }

  const separator = avatarUrl.includes('?') ? '&' : '?';
  return `${avatarUrl}${separator}t=${avatarRefreshVersion.value}`;
}

async function handleLogout() {
  await auth.logout();
  await router.replace('/login');
}

async function handleUpload(payload: Parameters<typeof documentsState.uploadBatch>[0]) {
  await documentsState.uploadBatch(payload);
  documentLibraryVisible.value = true;
}

async function handleAvatarUpload(file: File) {
  avatarUploading.value = true;
  try {
    await auth.uploadAvatar(file);
    avatarRefreshVersion.value = Date.now();
    avatarUploadVisible.value = false;
    ElMessage.success('头像已更新');
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    avatarUploading.value = false;
  }
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
      :current-user-avatar-url="currentUserAvatarUrl"
      :is-admin="auth.isAdmin.value"
      @create-conversation="conversationsState.createNewConversation"
      @select-conversation="conversationsState.selectConversation"
      @delete-conversation="conversationsState.removeConversation"
      @open-documents="documentLibraryVisible = true"
      @clean-empty-conversations="conversationsState.cleanEmptyConversations"
      @go-admin="router.push('/admin')"
      @open-avatar-upload="avatarUploadVisible = true"
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

    <AvatarUploadDialog
      v-model="avatarUploadVisible"
      :loading="avatarUploading"
      :current-avatar-url="currentUserAvatarUrl"
      @submit="handleAvatarUpload"
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