<script setup lang="ts">
import { ref } from 'vue';
import ChatMessageList from './ChatMessageList.vue';
import ChatComposer from './ChatComposer.vue';
import LiteratureSearchResults from './LiteratureSearchResults.vue';
import type { ChatMode, ConversationMessage, LiteratureSearchResult } from '../../types';

const props = defineProps<{
  loading: boolean;
  mode: ChatMode;
  messages: ConversationMessage[];
  activeConversation?: unknown;
  messagesLoading?: boolean;
  documentTotal: number;
  currentUserAvatarUrl?: string | null;
  literatureLoading?: boolean;
  literatureItems: LiteratureSearchResult[];
  literatureErrorMessage?: string;
  lastLiteratureQuery?: string;
  hasSearchedLiterature?: boolean;
}>();

const emit = defineEmits<{
  submit: [payload: { mode: ChatMode; question: string; topK?: number }];
  'update:mode': [mode: ChatMode];
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
      v-if="props.mode === 'rag'"
      :messages="props.messages"
      :loading="props.messagesLoading"
      :current-user-avatar-url="props.currentUserAvatarUrl"
      @ask-example="handleExample"
    />
    <LiteratureSearchResults
      v-else
      :items="props.literatureItems"
      :loading="props.literatureLoading"
      :error-message="props.literatureErrorMessage"
      :last-query="props.lastLiteratureQuery"
      :has-searched="props.hasSearchedLiterature"
    />
    <ChatComposer
      ref="composerRef"
      :mode="props.mode"
      :loading="props.loading"
      @update:mode="emit('update:mode', $event)"
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