<script lang="ts">
export default {
  name: 'RagChatWorkspace',
};
</script>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { Collection, UploadFilled } from '@element-plus/icons-vue';
import ChatMessageList from './ChatMessageList.vue';
import ChatComposer from './ChatComposer.vue';
import type { Conversation, ConversationMessage } from '../../types';

const props = defineProps<{
  loading: boolean;
  messages: ConversationMessage[];
  activeConversation: Conversation | null;
  messagesLoading?: boolean;
  documentTotal: number;
}>();

const emit = defineEmits<{
  submit: [payload: { question: string; topK?: number }];
  openDocuments: [];
  openUpload: [];
}>();

const composerRef = ref<InstanceType<typeof ChatComposer> | null>(null);

const subtitle = computed(() => {
  if (!props.activeConversation) {
    return '新问答将在发送第一条问题后自动保存';
  }

  return `最近更新：${new Date(props.activeConversation.updatedAt).toLocaleString('zh-CN')}`;
});

function handleExample(question: string) {
  composerRef.value?.fillQuestion(question);
}
</script>

<template>
  <main class="rag-workspace">
    <header class="workspace-header">
      <div>
        <p class="workspace-kicker">论文智能问答</p>
        <h1>{{ props.activeConversation?.title || '和你的论文知识库对话' }}</h1>
        <span>{{ subtitle }}</span>
      </div>

      <div class="header-actions">
        <el-button :icon="Collection" @click="emit('openDocuments')">
          文档库 {{ props.documentTotal }}
        </el-button>
        <el-button type="primary" :icon="UploadFilled" @click="emit('openUpload')">
          上传论文
        </el-button>
      </div>
    </header>

    <ChatMessageList :messages="props.messages" :loading="props.messagesLoading" @ask-example="handleExample" />
    <ChatComposer ref="composerRef" :loading="props.loading" @submit="emit('submit', $event)" />
  </main>
</template>

<style scoped>
.rag-workspace {
  min-width: 0;
  height: 100vh;
  display: flex;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
  background: #f8fafc;
}

.workspace-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 22px clamp(18px, 3vw, 30px);
  border-bottom: 1px solid rgba(226, 232, 240, 0.9);
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(16px);
}

.workspace-kicker {
  margin: 0 0 6px;
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0.12em;
}

.workspace-header h1 {
  max-width: 780px;
  overflow: hidden;
  margin: 0;
  color: #0f172a;
  font-size: clamp(22px, 3vw, 30px);
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-header span {
  display: block;
  margin-top: 7px;
  color: var(--app-text-muted);
  font-size: 13px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  white-space: nowrap;
}

.header-actions .el-button {
  border-radius: 13px;
  font-weight: 700;
}

@media (max-width: 900px) {
  .rag-workspace {
    height: auto;
    min-height: 100vh;
  }

  .workspace-header {
    align-items: stretch;
    flex-direction: column;
  }

  .header-actions {
    white-space: normal;
  }
}

@media (max-width: 520px) {
  .header-actions {
    flex-direction: column;
  }

  .header-actions .el-button {
    width: 100%;
  }
}
</style>