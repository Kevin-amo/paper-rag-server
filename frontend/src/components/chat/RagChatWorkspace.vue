<script setup lang="ts">
import { ref } from 'vue';
import ChatMessageList from './ChatMessageList.vue';
import ChatComposer from './ChatComposer.vue';
import type { ConversationMessage } from '../../types';

const props = defineProps<{
  loading: boolean;
  messages: ConversationMessage[];
  activeConversation?: unknown;
  messagesLoading?: boolean;
  documentTotal: number;
  currentUserAvatarUrl?: string | null;
}>();

const emit = defineEmits<{
  submit: [payload: { question: string; topK?: number }];
  openDocuments: [];
  openUpload: [];
}>();

const composerRef = ref<{ fillQuestion: (question: string) => void } | null>(null);

function handleExample(question: string) {
  composerRef.value?.fillQuestion(question);
}
</script>

<template>
  <main class="rag-workspace">
    <ChatMessageList
      :messages="props.messages"
      :loading="props.messagesLoading"
      :current-user-avatar-url="props.currentUserAvatarUrl"
      @ask-example="handleExample"
    />
    <ChatComposer
      ref="composerRef"
      :loading="props.loading"
      @submit="emit('submit', $event)"
      @open-documents="emit('openDocuments')"
      @open-upload="emit('openUpload')"
    />
  </main>
</template>

<style scoped>
.rag-workspace {
  position: relative;
  min-width: 0;
  height: 100vh;
  display: flex;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
  background: #ffffff;
}

@media (max-width: 900px) {
  .rag-workspace {
    height: auto;
    min-height: 100vh;
  }
}
</style>