<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { useRouter } from 'vue-router';
import ChatSidebar from '../components/chat/ChatSidebar.vue';
import RagChatWorkspace from '../components/chat/RagChatWorkspace.vue';
import DocumentLibraryDrawer from '../components/documents/DocumentLibraryDrawer.vue';
import UploadDocumentDialog from '../components/documents/UploadDocumentDialog.vue';
import DocumentDetailDrawer from '../components/documents/DocumentDetailDrawer.vue';
import AccountManagementDialog from '../components/user/AccountManagementDialog.vue';
import { getErrorMessage } from '../api/http';
import { useAuth } from '../composables/useAuth';
import { clearAuthSession } from '../composables/authState';
import { useDocuments } from '../composables/useDocuments';
import { useConversations } from '../composables/useConversations';
import { useAgentChat } from '../composables/useAgentChat';
import type { ConversationMessage } from '../types';

const router = useRouter();
const auth = useAuth();
const documentsState = useDocuments();
const conversationsState = useConversations();
const documentLibraryVisible = ref(false);
const uploadVisible = ref(false);
const accountManagementVisible = ref(false);
const avatarUploading = ref(false);
const displayNameChanging = ref(false);
const passwordChanging = ref(false);
const emailCodeSending = ref(false);
const emailChanging = ref(false);
const avatarRefreshVersion = ref(0);

const currentUserName = computed(() => auth.state.user?.displayName || auth.state.user?.username || '当前用户');
const currentUserAvatarUrl = computed(() => buildAvatarDisplayUrl(auth.state.user?.avatarUrl ?? null));
const activeConversation = computed(() => (
  conversationsState.conversations.value.find((item) => item.id === conversationsState.activeConversationId.value) ?? null
));

const agentState = useAgentChat({
  activeConversationId: conversationsState.activeConversationId,
  activeConversation,
  conversationMessages: conversationsState.conversationMessages,
  conversations: conversationsState.conversations,
  loadConversations: () => conversationsState.loadConversations(),
  loadMessages: conversationsState.loadMessages,
});

const visibleMessages = computed(() => conversationsState.conversationMessages.value
  .filter((message) => isRenderableMessage(message))
  .sort((a, b) => a.messageOrder - b.messageOrder));

function buildAvatarDisplayUrl(avatarUrl: string | null) {
  if (!avatarUrl || !avatarRefreshVersion.value) {
    return avatarUrl;
  }

  const separator = avatarUrl.includes('?') ? '&' : '?';
  return `${avatarUrl}${separator}t=${avatarRefreshVersion.value}`;
}

function isLiteratureMessageMetadata(metadata: ConversationMessage['metadata']) {
  if (!metadata || typeof metadata !== 'object' || !('type' in metadata)) {
    return false;
  }
  if (metadata.type === 'LITERATURE_SEARCH_RESULT') {
    return Array.isArray((metadata as { items?: unknown[] }).items);
  }
  if (metadata.type === 'AGENT_RESULT' && 'literature' in metadata) {
    const literature = (metadata as { literature?: { items?: unknown[] } }).literature;
    return Array.isArray(literature?.items);
  }
  return false;
}

function isRenderableMessage(message: ConversationMessage) {
  const content = message.content.trim();
  return message.streaming
    || (!!content && content !== '-')
    || (message.citations?.length ?? 0) > 0
    || (message.role === 'ASSISTANT' && isLiteratureMessageMetadata(message.metadata));
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
    ElMessage.success('头像已更新');
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    avatarUploading.value = false;
  }
}

async function handleChangeDisplayName(displayName: string) {
  displayNameChanging.value = true;
  try {
    await auth.changeDisplayName(displayName);
    ElMessage.success('昵称已更新');
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    displayNameChanging.value = false;
  }
}

async function handleChangePassword(payload: { currentPassword: string; newPassword: string }) {
  passwordChanging.value = true;
  try {
    await auth.changePassword(payload.currentPassword, payload.newPassword);
    clearAuthSession();
    await router.replace('/login');
    ElMessage.success('密码已更新，请重新登录');
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    passwordChanging.value = false;
  }
}

async function handleRequestEmailCode(email: string) {
  emailCodeSending.value = true;
  try {
    await auth.requestChangeEmailCode(email);
    ElMessage.success('验证码已发送');
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    emailCodeSending.value = false;
  }
}

async function handleChangeEmail(payload: { email: string; emailCode: string }) {
  emailChanging.value = true;
  try {
    await auth.changeEmail(payload.email, payload.emailCode);
    ElMessage.success('邮箱已换绑');
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    emailChanging.value = false;
  }
}

async function openDocumentDetail(document: Parameters<typeof documentsState.openDetail>[0]) {
  await documentsState.openDetail(document);
}

async function handleChatSubmit(payload: { question: string; topK?: number }) {
  await agentState.ask({ question: payload.question, topK: payload.topK });
}

async function handleSelectConversation(conversationId: string) {
  await conversationsState.selectConversation(conversationId);
}

function handleCreateConversation() {
  conversationsState.createNewConversation();
}

onMounted(async () => {
  try {
    await auth.hydrateCurrentUser();
  } catch {
    await router.replace('/login');
    return;
  }
  await Promise.all([documentsState.loadDocuments(0), conversationsState.loadConversations()]);
});
</script>

<template>
  <div class="user-app-shell">
    <ChatSidebar
      :conversations="conversationsState.conversations.value"
      :active-conversation-id="conversationsState.activeConversationId.value"
      :conversations-loading="conversationsState.conversationsLoading.value"
      :current-user-name="currentUserName"
      :current-user-avatar-url="currentUserAvatarUrl"
      :is-admin="auth.isAdmin.value"
      @create-conversation="handleCreateConversation"
      @select-conversation="handleSelectConversation"
      @delete-conversation="conversationsState.removeConversation"
      @rename-conversation="conversationsState.renameConversation"
      @open-documents="documentLibraryVisible = true"
      @go-admin="router.push('/admin')"
      @open-account-management="accountManagementVisible = true"
      @logout="handleLogout"
    />

    <RagChatWorkspace
      :loading="agentState.agentLoading.value"
      :messages="visibleMessages"
      :active-conversation="activeConversation"
      :messages-loading="conversationsState.messagesLoading.value"
      :document-total="documentsState.pagination.total"
      :current-user-avatar-url="currentUserAvatarUrl"
      @submit="handleChatSubmit"
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
      :deleting-all-documents="documentsState.deletingAllDocuments.value"
      @search="documentsState.search"
      @page-change="documentsState.loadDocuments"
      @row-click="openDocumentDetail"
      @refresh="documentsState.loadDocuments(0)"
      @delete="documentsState.removeDocument"
      @delete-all="documentsState.removeAllDocuments"
      @upload="uploadVisible = true"
    />

    <UploadDocumentDialog
      v-model="uploadVisible"
      :loading="documentsState.uploadLoading.value"
      :result="documentsState.lastBatchUploadResult.value"
      :error-message="documentsState.uploadErrorMessage.value"
      @submit="handleUpload"
    />

    <AccountManagementDialog
      v-model="accountManagementVisible"
      :user="auth.state.user"
      :avatar-url="currentUserAvatarUrl"
      :avatar-loading="avatarUploading"
      :display-name-loading="displayNameChanging"
      :password-loading="passwordChanging"
      :email-code-loading="emailCodeSending"
      :email-loading="emailChanging"
      @upload-avatar="handleAvatarUpload"
      @change-display-name="handleChangeDisplayName"
      @change-password="handleChangePassword"
      @request-email-code="handleRequestEmailCode"
      @change-email="handleChangeEmail"
    />

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
  </div>
</template>

<style scoped>
.user-app-shell {
  position: relative;
  isolation: isolate;
  height: 100vh;
  display: flex;
  gap: 18px;
  overflow: hidden;
  padding: 18px;
  background:
    radial-gradient(circle at 12% 8%, rgba(0, 122, 255, 0.13), transparent 28rem),
    radial-gradient(circle at 88% 92%, rgba(175, 82, 222, 0.11), transparent 30rem),
    linear-gradient(135deg, #f5f5f7 0%, #fbfbfd 46%, #eef3ff 100%);
}

.user-app-shell::before,
.user-app-shell::after {
  position: absolute;
  z-index: -1;
  border-radius: 999px;
  content: '';
  pointer-events: none;
}

.user-app-shell::before {
  width: 420px;
  height: 420px;
  top: -180px;
  left: -130px;
  background: rgba(255, 255, 255, 0.62);
}

.user-app-shell::after {
  width: 520px;
  height: 520px;
  right: -210px;
  bottom: -230px;
  background: rgba(255, 255, 255, 0.5);
}

@media (max-width: 900px) {
  .user-app-shell {
    height: auto;
    min-height: 100vh;
    flex-direction: column;
    overflow: visible;
    padding: 12px;
  }
}
</style>