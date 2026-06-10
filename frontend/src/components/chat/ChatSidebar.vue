<script lang="ts">
export default {
  name: 'ChatSidebar',
};
</script>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue';
import {
  ChatDotRound,
  Collection,
  Delete,
  EditPen,
  MoreFilled,
  Plus,
  SwitchButton,
  User,
  Setting,
} from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import type { Conversation } from '../../types';

const PINNED_CONVERSATIONS_STORAGE_KEY = 'paper-mind:pinned-conversations';

type ConversationMenuCommand = 'pin' | 'unpin' | 'rename' | 'delete';

const props = defineProps<{
  conversations: Conversation[];
  activeConversationId: string | null;
  conversationsLoading?: boolean;
  currentUserName: string;
  currentUserAvatarUrl?: string | null;
  isAdmin?: boolean;
}>();

const emit = defineEmits<{
  createConversation: [];
  selectConversation: [conversationId: string];
  deleteConversation: [conversationId: string];
  renameConversation: [conversationId: string, title: string];
  openDocuments: [];
  goAdmin: [];
  openAccountManagement: [];
  logout: [];
}>();

const pinnedConversationIds = ref<string[]>(loadPinnedConversationIds());
const deleteDialogVisible = ref(false);
const logoutDialogVisible = ref(false);
const operatingConversation = ref<Conversation | null>(null);
const editingConversationId = ref<string | null>(null);
const renameTitle = ref('');
const renameInputRef = ref<HTMLInputElement | null>(null);

const sortedConversations = computed(() => [...props.conversations].sort((a, b) => {
  const aPinned = isPinned(a.id);
  const bPinned = isPinned(b.id);

  if (aPinned !== bPinned) {
    return aPinned ? -1 : 1;
  }

  return new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime();
}));

function loadPinnedConversationIds() {
  try {
    const rawValue = localStorage.getItem(PINNED_CONVERSATIONS_STORAGE_KEY);
    if (!rawValue) {
      return [];
    }

    const parsedValue = JSON.parse(rawValue);
    return Array.isArray(parsedValue)
      ? parsedValue.filter((item): item is string => typeof item === 'string')
      : [];
  } catch {
    return [];
  }
}

function savePinnedConversationIds() {
  try {
    localStorage.setItem(PINNED_CONVERSATIONS_STORAGE_KEY, JSON.stringify(pinnedConversationIds.value));
  } catch {
    // ignore storage failures
  }
}

function isPinned(conversationId: string) {
  return pinnedConversationIds.value.includes(conversationId);
}

function togglePinned(conversationId: string) {
  if (isPinned(conversationId)) {
    pinnedConversationIds.value = pinnedConversationIds.value.filter((id) => id !== conversationId);
  } else {
    pinnedConversationIds.value = [conversationId, ...pinnedConversationIds.value.filter((id) => id !== conversationId)];
  }
  savePinnedConversationIds();
}

function handleDropdownCommand(conversation: Conversation, command: string | number | object) {
  if (command === 'pin' || command === 'unpin' || command === 'rename' || command === 'delete') {
    handleMenuCommand(conversation, command);
  }
}

function handleMenuCommand(conversation: Conversation, command: ConversationMenuCommand) {
  if (command === 'pin' || command === 'unpin') {
    togglePinned(conversation.id);
    return;
  }

  if (command === 'rename') {
    startInlineRename(conversation);
    return;
  }

  cancelInlineRename();
  operatingConversation.value = conversation;
  deleteDialogVisible.value = true;
}

async function startInlineRename(conversation: Conversation) {
  operatingConversation.value = conversation;
  editingConversationId.value = conversation.id;
  renameTitle.value = conversationTitle(conversation);
  await nextTick();
  renameInputRef.value?.focus();
  renameInputRef.value?.select();
}

function confirmRename() {
  if (!operatingConversation.value) {
    return;
  }

  const normalizedTitle = renameTitle.value.trim();
  if (!normalizedTitle) {
    ElMessage.warning('会话名称不能为空');
    return;
  }

  if (normalizedTitle !== conversationTitle(operatingConversation.value)) {
    emit('renameConversation', operatingConversation.value.id, normalizedTitle);
  }

  editingConversationId.value = null;
  operatingConversation.value = null;
  renameTitle.value = '';
}

function cancelInlineRename() {
  editingConversationId.value = null;
  operatingConversation.value = null;
  renameTitle.value = '';
}

function confirmDelete() {
  if (!operatingConversation.value) {
    return;
  }

  emit('deleteConversation', operatingConversation.value.id);
  deleteDialogVisible.value = false;
  operatingConversation.value = null;
}

function closeOperationDialog() {
  deleteDialogVisible.value = false;
  operatingConversation.value = null;
}

function confirmLogout() {
  logoutDialogVisible.value = false;
  emit('logout');
}

function conversationTitle(conversation: Conversation) {
  return conversation.title?.trim() || '新的论文问答';
}
</script>

<template>
  <aside class="chat-sidebar">
    <div class="brand-block">
      <div class="brand-mark">P</div>
      <div>
        <strong>PaperMind</strong>
        <span>论文智能助手</span>
      </div>
    </div>

    <el-button class="new-chat-button" type="primary" size="large" :icon="Plus" @click="emit('createConversation')">
      新聊天
    </el-button>

    <nav class="sidebar-nav">
      <button class="nav-item active" type="button">
        <el-icon><ChatDotRound /></el-icon>
        <span>问答会话</span>
      </button>
      <button class="nav-item" type="button" @click="emit('openDocuments')">
        <el-icon><Collection /></el-icon>
        <span>文档库</span>
      </button>
      <button v-if="props.isAdmin" class="nav-item" type="button" @click="emit('goAdmin')">
        <el-icon><Setting /></el-icon>
        <span>管理后台</span>
      </button>
    </nav>

    <section class="conversation-section">
      <div class="section-title">
        <span>最近会话</span>
      </div>

      <div v-loading="props.conversationsLoading" class="conversation-list">
        <el-empty v-if="!sortedConversations.length" description="暂无会话" :image-size="72" />
        <div
          v-for="conversation in sortedConversations"
          v-else
          :key="conversation.id"
          class="conversation-item"
          :class="{ active: conversation.id === props.activeConversationId, pinned: isPinned(conversation.id) }"
        >
          <template v-if="editingConversationId === conversation.id">
            <input
              ref="renameInputRef"
              v-model="renameTitle"
              class="conversation-rename-input"
              maxlength="60"
              @click.stop
              @keydown.enter.prevent="confirmRename"
              @keydown.esc.prevent="cancelInlineRename"
              @blur="cancelInlineRename"
            >
          </template>
          <button v-else type="button" class="conversation-select" @click="emit('selectConversation', conversation.id)">
            <span class="conversation-title-row">
              <span class="conversation-title">{{ conversationTitle(conversation) }}</span>
              <span v-if="isPinned(conversation.id)" class="conversation-pin-badge" aria-label="已置顶" title="已置顶">📌</span>
            </span>
          </button>

          <el-dropdown
            trigger="click"
            popper-class="conversation-action-popper"
            @command="handleDropdownCommand(conversation, $event)"
          >
            <el-button
              text
              circle
              class="conversation-menu-button"
              :aria-label="isPinned(conversation.id) ? '会话菜单：已置顶' : '会话菜单：未置顶'"
              @click.stop
            >
              <el-icon><MoreFilled /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu class="conversation-action-menu">
                <el-dropdown-item :command="isPinned(conversation.id) ? 'unpin' : 'pin'" class="conversation-action-item">
                  <span class="action-icon">📌</span>
                  <span>{{ isPinned(conversation.id) ? '取消置顶' : '置顶会话' }}</span>
                </el-dropdown-item>
                <el-dropdown-item command="rename" class="conversation-action-item">
                  <el-icon><EditPen /></el-icon>
                  <span>重命名</span>
                </el-dropdown-item>
                <el-dropdown-item command="delete" class="conversation-action-item danger">
                  <el-icon><Delete /></el-icon>
                  <span>删除会话</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </section>

    <footer class="user-footer">
      <button class="user-avatar" type="button" title="账号管理" @click="emit('openAccountManagement')">
        <img v-if="props.currentUserAvatarUrl" :src="props.currentUserAvatarUrl" alt="用户头像">
        <el-icon v-else><User /></el-icon>
      </button>
      <div class="user-meta">
        <strong>{{ props.currentUserName }}</strong>
      </div>
      <el-button circle text :icon="SwitchButton" title="退出登录" @click="logoutDialogVisible = true" />
    </footer>

    <el-dialog
      v-model="logoutDialogVisible"
      title="退出登录"
      width="400px"
      class="conversation-dialog logout-dialog"
      append-to-body
      align-center
    >
      <div class="conversation-dialog-body logout-dialog-body">
        <strong>确认退出当前账号吗？</strong>
        <span class="dialog-caption">退出后需要重新登录，才能继续使用论文助手。</span>
      </div>
      <template #footer>
        <el-button @click="logoutDialogVisible = false">取消</el-button>
        <el-button class="logout-confirm-button" type="danger" @click="confirmLogout">确认退出</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="deleteDialogVisible"
      title="删除会话"
      width="400px"
      class="conversation-dialog danger-dialog"
      append-to-body
      align-center
      @closed="closeOperationDialog"
    >
      <div class="conversation-dialog-body">
        <strong>确认删除这个问答会话吗？</strong>
        <span class="dialog-caption">“{{ operatingConversation ? conversationTitle(operatingConversation) : '' }}” 删除后不可恢复。</span>
      </div>
      <template #footer>
        <el-button @click="deleteDialogVisible = false">取消</el-button>
        <el-button class="delete-confirm-button" type="danger" @click="confirmDelete">删除</el-button>
      </template>
    </el-dialog>
  </aside>
</template>

<style scoped>
.chat-sidebar {
  width: 306px;
  min-width: 306px;
  height: calc(100vh - 36px);
  display: flex;
  flex-direction: column;
  gap: 15px;
  overflow: hidden;
  padding: 18px 14px;
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 30px;
  background: rgba(255, 255, 255, 0.58);
  box-shadow:
    0 22px 56px rgba(15, 23, 42, 0.12),
    inset 0 1px 0 rgba(255, 255, 255, 0.84);
  backdrop-filter: blur(24px) saturate(175%);
  -webkit-backdrop-filter: blur(24px) saturate(175%);
}

.brand-block {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 2px 8px 8px;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 13px;
  color: #fff;
  background: linear-gradient(135deg, #007aff, #5ac8fa);
  font-size: 15px;
  font-weight: 900;
  box-shadow: 0 10px 22px rgba(0, 122, 255, 0.24);
}

.brand-block strong,
.user-meta strong {
  display: block;
  color: var(--app-text);
  line-height: 1.2;
}

.brand-block strong {
  font-size: 15px;
}

.brand-block span,
.user-meta span {
  display: block;
  margin-top: 3px;
  color: var(--app-text-muted);
  font-size: 12px;
}

.new-chat-button {
  width: 100%;
  height: 46px;
  justify-content: center;
  border: 1px solid rgba(255, 255, 255, 0.76);
  border-radius: 18px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.92), rgba(242, 247, 255, 0.84));
  color: var(--app-text);
  font-weight: 850;
  box-shadow:
    0 12px 24px rgba(15, 23, 42, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.9);
}

.new-chat-button:hover,
.new-chat-button:focus {
  border-color: rgba(0, 122, 255, 0.28);
  background: #ffffff;
  color: var(--app-primary);
  box-shadow: var(--app-shadow-glow);
}

.sidebar-nav {
  display: grid;
  gap: 4px;
}

.nav-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid transparent;
  border-radius: 15px;
  background: transparent;
  color: var(--app-text-muted);
  cursor: pointer;
  text-align: left;
  transition: background 0.16s ease, border-color 0.16s ease, color 0.16s ease, box-shadow 0.16s ease;
}

.nav-item:hover,
.nav-item.active {
  border-color: rgba(255, 255, 255, 0.7);
  background: rgba(255, 255, 255, 0.68);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.78);
  color: var(--app-text);
}

.conversation-section {
  min-height: 0;
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 8px 0;
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 700;
}

.conversation-list {
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 3px;
  padding-right: 2px;
}

.conversation-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 2px;
  min-height: 40px;
  padding: 4px 5px 4px 10px;
  border: 1px solid transparent;
  border-radius: 15px;
  background: transparent;
  transition: background 0.16s ease, border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.conversation-item:hover {
  border-color: rgba(255, 255, 255, 0.68);
  background: rgba(255, 255, 255, 0.56);
}

.conversation-item.active {
  border-color: rgba(0, 122, 255, 0.24);
  background: rgba(0, 122, 255, 0.1);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.78);
}

.conversation-select {
  min-width: 0;
  min-height: 30px;
  border: 0;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
  padding: 0;
}

.conversation-rename-input {
  width: 100%;
  min-width: 0;
  height: 30px;
  border: 1px solid #c7d2fe;
  border-radius: 9px;
  background: #ffffff;
  color: #1f2937;
  outline: none;
  padding: 0 8px;
  font-size: 13px;
  font-weight: 600;
  box-shadow: 0 0 0 3px rgba(91, 124, 250, 0.12);
}

.conversation-title-row {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
}

.conversation-title {
  display: block;
  overflow: hidden;
  color: #1f2937;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-pin-badge {
  flex: none;
  display: inline-grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border-radius: 999px;
  background: rgba(91, 124, 250, 0.12);
  font-size: 11px;
  line-height: 1;
}

.conversation-menu-button {
  flex: none;
  width: 28px;
  height: 28px;
  color: #9ca3af;
  opacity: 0;
}

.conversation-item:hover .conversation-menu-button,
.conversation-item.active .conversation-menu-button {
  opacity: 1;
}

.conversation-menu-button:hover {
  color: var(--app-text-muted);
  background: rgba(17, 24, 39, 0.04);
}

:global([class~="conversation-action-popper"]) {
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.72) !important;
  border-radius: 18px !important;
  background: rgba(255, 255, 255, 0.88) !important;
  box-shadow: 0 18px 44px rgba(15, 23, 42, 0.12) !important;
  backdrop-filter: blur(22px) saturate(170%);
  -webkit-backdrop-filter: blur(22px) saturate(170%);
}

:global([class~="conversation-action-popper"] [class~="el-popper__arrow"]) {
  display: none;
}

:global([class~="conversation-action-menu"]) {
  min-width: 156px;
  padding: 6px;
}

:global([class~="conversation-action-menu"] [class~="el-dropdown-menu__item"]) {
  display: flex;
  align-items: center;
  gap: 9px;
  height: 36px;
  padding: 0 10px;
  border-radius: 10px;
  color: #374151;
  font-size: 13px;
  font-weight: 700;
}

:global([class~="conversation-action-menu"] [class~="el-dropdown-menu__item"]:not([class~="is-disabled"]):focus),
:global([class~="conversation-action-menu"] [class~="el-dropdown-menu__item"]:not([class~="is-disabled"]):hover) {
  background: #f7f8fa;
  color: #111827;
}

:global([class~="conversation-action-menu"] [class~="el-dropdown-menu__item"][class~="danger"]) {
  color: #dc2626;
}

:global([class~="conversation-action-menu"] [class~="el-dropdown-menu__item"][class~="danger"]:not([class~="is-disabled"]):focus),
:global([class~="conversation-action-menu"] [class~="el-dropdown-menu__item"][class~="danger"]:not([class~="is-disabled"]):hover) {
  background: #fef2f2;
  color: #dc2626;
}

.action-icon {
  width: 1em;
  text-align: center;
  line-height: 1;
}

.conversation-dialog-body {
  display: grid;
  gap: 12px;
}

.conversation-dialog-body strong {
  color: var(--app-text);
  font-size: 15px;
}

.dialog-caption {
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.6;
}

:global([class~="conversation-dialog"] [class~="el-dialog"]) {
  border-radius: 22px;
}

:global([class~="conversation-dialog"] [class~="el-button"]) {
  border-radius: 12px;
}

:global([class~="danger-dialog"] [class~="delete-confirm-button"]),
:global([class~="logout-dialog"] [class~="logout-confirm-button"]) {
  transition: transform 0.16s ease, box-shadow 0.16s ease;
}

:global([class~="danger-dialog"] [class~="delete-confirm-button"]:hover),
:global([class~="danger-dialog"] [class~="delete-confirm-button"]:focus),
:global([class~="logout-dialog"] [class~="logout-confirm-button"]:hover),
:global([class~="logout-dialog"] [class~="logout-confirm-button"]:focus) {
  background: var(--el-color-danger);
  border-color: var(--el-color-danger);
  color: #ffffff;
  transform: scale(1.02);
  box-shadow: 0 8px 18px rgba(220, 38, 38, 0.22);
}

.user-footer {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  margin-top: auto;
  padding: 10px;
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.68);
  box-shadow:
    0 10px 24px rgba(15, 23, 42, 0.07),
    inset 0 1px 0 rgba(255, 255, 255, 0.82);
}

.user-avatar {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  overflow: hidden;
  border: 0;
  border-radius: 999px;
  color: var(--app-primary);
  background: rgba(0, 122, 255, 0.1);
  cursor: pointer;
  padding: 0;
}

.user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-avatar:hover {
  box-shadow: var(--app-focus-ring);
}

.user-meta {
  min-width: 0;
}

.user-meta strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 900px) {
  .chat-sidebar {
    width: 100%;
    min-width: 0;
    height: auto;
    max-height: 46vh;
    border-radius: 26px;
  }

  .sidebar-nav {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .chat-sidebar {
    padding: 12px;
    border-radius: 24px;
  }

  .sidebar-nav {
    grid-template-columns: 1fr;
  }
}
</style>
