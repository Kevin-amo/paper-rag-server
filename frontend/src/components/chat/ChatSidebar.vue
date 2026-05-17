<script lang="ts">
export default {
  name: 'ChatSidebar',
};
</script>

<script setup lang="ts">
import { computed } from 'vue';
import {
  ChatDotRound,
  Collection,
  Plus,
  SwitchButton,
  User,
  Setting,
} from '@element-plus/icons-vue';
import ConfirmDeleteButton from '../common/ConfirmDeleteButton.vue';
import type { Conversation } from '../../types';

const props = defineProps<{
  conversations: Conversation[];
  activeConversationId: string | null;
  conversationsLoading?: boolean;
  cleaningConversations?: boolean;
  currentUserName: string;
  isAdmin?: boolean;
}>();

const emit = defineEmits<{
  createConversation: [];
  selectConversation: [conversationId: string];
  deleteConversation: [conversationId: string];
  openDocuments: [];
  cleanEmptyConversations: [];
  goAdmin: [];
  logout: [];
}>();

const sortedConversations = computed(() => [...props.conversations].sort((a, b) => (
  new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
)));

function formatDate(value: string) {
  if (!value) {
    return '暂无时间';
  }

  const date = new Date(value);
  const now = new Date();
  const sameDay = date.toDateString() === now.toDateString();
  return date.toLocaleString('zh-CN', sameDay
    ? { hour: '2-digit', minute: '2-digit' }
    : { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
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
        <strong>Paper RAG</strong>
        <span>论文智能助手</span>
      </div>
    </div>

    <el-button class="new-chat-button" type="primary" size="large" :icon="Plus" @click="emit('createConversation')">
      新建问答
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
        <el-button text size="small" :loading="props.cleaningConversations" @click="emit('cleanEmptyConversations')">
          清理
        </el-button>
      </div>

      <div v-loading="props.conversationsLoading" class="conversation-list">
        <el-empty v-if="!sortedConversations.length" description="暂无会话" :image-size="72" />
        <div
          v-for="conversation in sortedConversations"
          v-else
          :key="conversation.id"
          class="conversation-item"
          :class="{ active: conversation.id === props.activeConversationId }"
        >
          <button type="button" class="conversation-select" @click="emit('selectConversation', conversation.id)">
            <span class="conversation-title">{{ conversationTitle(conversation) }}</span>
            <small>{{ formatDate(conversation.updatedAt) }}</small>
          </button>
          <ConfirmDeleteButton
            class="conversation-delete"
            title="确认删除这个问答会话吗？"
            confirm-text="删除"
            @confirm="emit('deleteConversation', conversation.id)"
          />
        </div>
      </div>
    </section>

    <footer class="user-footer">
      <div class="user-avatar">
        <el-icon><User /></el-icon>
      </div>
      <div class="user-meta">
        <strong>{{ props.currentUserName }}</strong>
        <span>个人知识库</span>
      </div>
      <el-button circle text :icon="SwitchButton" title="退出登录" @click="emit('logout')" />
    </footer>
  </aside>
</template>

<style scoped>
.chat-sidebar {
  width: 292px;
  min-width: 260px;
  height: 100vh;
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 22px 18px;
  border-right: 1px solid rgba(226, 232, 240, 0.86);
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(18px);
}

.brand-block {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 4px 10px;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 15px;
  color: #fff;
  background: linear-gradient(135deg, #2563eb, #4f46e5);
  font-weight: 900;
  box-shadow: 0 14px 30px rgba(37, 99, 235, 0.24);
}

.brand-block strong,
.user-meta strong {
  display: block;
  color: var(--app-text);
  line-height: 1.2;
}

.brand-block span,
.user-meta span {
  display: block;
  margin-top: 4px;
  color: var(--app-text-muted);
  font-size: 12px;
}

.new-chat-button {
  width: 100%;
  justify-content: center;
  border-radius: 14px;
  font-weight: 700;
}

.sidebar-nav {
  display: grid;
  gap: 8px;
}

.nav-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 13px;
  border: 1px solid transparent;
  border-radius: 14px;
  background: transparent;
  color: #475569;
  cursor: pointer;
  text-align: left;
}

.nav-item:hover,
.nav-item.active {
  border-color: rgba(37, 99, 235, 0.12);
  background: var(--app-primary-soft);
  color: var(--app-primary);
}

.conversation-section {
  min-height: 0;
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 10px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4px;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.conversation-list {
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  padding-right: 2px;
}

.conversation-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 30px;
  align-items: center;
  gap: 4px;
  padding: 10px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 15px;
  background: #fff;
  transition: 0.16s ease;
}

.conversation-item:hover,
.conversation-item.active {
  border-color: rgba(37, 99, 235, 0.34);
  background: #f8fbff;
  box-shadow: 0 12px 26px rgba(15, 23, 42, 0.06);
}

.conversation-select {
  min-width: 0;
  border: 0;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.conversation-title {
  display: block;
  overflow: hidden;
  color: #172554;
  font-size: 13px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-select small {
  display: block;
  margin-top: 5px;
  color: #94a3b8;
}

.conversation-delete {
  opacity: 0.6;
}

.user-footer {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 18px;
  background: #fff;
}

.user-avatar {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 14px;
  color: var(--app-primary);
  background: var(--app-primary-soft);
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
    max-height: 48vh;
    border-right: 0;
    border-bottom: 1px solid rgba(226, 232, 240, 0.86);
  }
}
</style>