<script setup lang="ts">
import { ref } from 'vue';
import ChatMessageList from './ChatMessageList.vue';
import ChatComposer from './ChatComposer.vue';
import ChatDropZone from './ChatDropZone.vue';
import ChatUploadQueue from './ChatUploadQueue.vue';
import type { ConversationMessage } from '../../types';
import type { UploadQueueItem } from './ChatUploadQueue.vue';

const props = defineProps<{
  loading: boolean;
  messages: ConversationMessage[];
  activeConversation?: unknown;
  messagesLoading?: boolean;
  documentTotal: number;
  currentUserAvatarUrl?: string | null;
  uploadQueue: UploadQueueItem[];
}>();

const emit = defineEmits<{
  submit: [payload: { question: string; topK?: number }];
  openDocuments: [];
  dropFiles: [files: File[]];
  selectFiles: [];
  removeQueueItem: [id: string];
  clearQueue: [];
}>();

const composerRef = ref<{ fillQuestion: (question: string) => void } | null>(null);

function handleExample(question: string) {
  composerRef.value?.fillQuestion(question);
}
</script>

<template>
  <main class="rag-workspace">
    <ChatDropZone @drop-files="emit('dropFiles', $event)">
      <ChatMessageList
        :messages="props.messages"
        :loading="props.messagesLoading"
        :current-user-avatar-url="props.currentUserAvatarUrl"
        @ask-example="handleExample"
      />
      <ChatUploadQueue
        :items="props.uploadQueue"
        @remove="emit('removeQueueItem', $event)"
        @clear="emit('clearQueue')"
      />
      <ChatComposer
        ref="composerRef"
        :loading="props.loading"
        @submit="emit('submit', $event)"
        @open-documents="emit('openDocuments')"
        @select-files="emit('selectFiles')"
      />
    </ChatDropZone>
  </main>
</template>

<style scoped>
.rag-workspace {
  position: relative;
  min-width: 0;
  height: calc(100vh - 36px);
  display: flex;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 32px;
  background: rgba(255, 255, 255, 0.62);
  box-shadow:
    0 28px 70px rgba(15, 23, 42, 0.12),
    inset 0 1px 0 rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(26px) saturate(175%);
  -webkit-backdrop-filter: blur(26px) saturate(175%);
}

.rag-workspace::before {
  position: absolute;
  inset: 0;
  z-index: 0;
  background:
    radial-gradient(circle at 8% 0, rgba(0, 122, 255, 0.07), transparent 24rem),
    linear-gradient(180deg, rgba(255, 255, 255, 0.72), rgba(255, 255, 255, 0.28));
  content: '';
  pointer-events: none;
}

.rag-workspace > * {
  position: relative;
  z-index: 1;
}

@media (max-width: 900px) {
  .rag-workspace {
    height: auto;
    min-height: 70vh;
    border-radius: 28px;
  }
}
</style>
