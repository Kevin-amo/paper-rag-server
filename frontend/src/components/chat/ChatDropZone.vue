<script lang="ts">
export default {
  name: 'ChatDropZone',
};
</script>

<script setup lang="ts">
import { ref } from 'vue';
import { UploadFilled } from '@element-plus/icons-vue';

const emit = defineEmits<{
  dropFiles: [files: File[]];
}>();

const isDragging = ref(false);
let dragCounter = 0;

function handleDragEnter(event: DragEvent) {
  event.preventDefault();
  dragCounter++;
  if (event.dataTransfer?.types.includes('Files')) {
    isDragging.value = true;
  }
}

function handleDragOver(event: DragEvent) {
  event.preventDefault();
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'copy';
  }
}

function handleDragLeave(event: DragEvent) {
  event.preventDefault();
  dragCounter--;
  if (dragCounter <= 0) {
    isDragging.value = false;
    dragCounter = 0;
  }
}

function handleDrop(event: DragEvent) {
  event.preventDefault();
  isDragging.value = false;
  dragCounter = 0;

  const files = event.dataTransfer?.files;
  if (!files || files.length === 0) {
    return;
  }

  const fileList: File[] = [];
  for (let i = 0; i < files.length; i++) {
    fileList.push(files[i]);
  }

  if (fileList.length > 0) {
    emit('dropFiles', fileList);
  }
}
</script>

<template>
  <div
    class="chat-drop-zone"
    :class="{ 'is-dragging': isDragging }"
    @dragenter="handleDragEnter"
    @dragover="handleDragOver"
    @dragleave="handleDragLeave"
    @drop="handleDrop"
  >
    <div v-if="isDragging" class="drop-overlay">
      <div class="drop-hint">
        <el-icon class="drop-icon"><UploadFilled /></el-icon>
        <strong>释放以上传论文</strong>
        <span>支持批量上传 PDF、Word 文件</span>
      </div>
    </div>
    <slot />
  </div>
</template>

<style scoped>
.chat-drop-zone {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.drop-overlay {
  position: absolute;
  inset: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px dashed var(--app-primary);
  border-radius: 28px;
  background: rgba(0, 122, 255, 0.08);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
  animation: drop-in 0.2s ease both;
}

@keyframes drop-in {
  from {
    opacity: 0;
    transform: scale(0.98);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

.drop-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 32px 44px;
  border: 1px solid rgba(0, 122, 255, 0.18);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.86);
  box-shadow: 0 18px 44px rgba(0, 122, 255, 0.12);
}

.drop-icon {
  color: var(--app-primary);
  font-size: 40px;
}

.drop-hint strong {
  color: var(--app-text);
  font-size: 17px;
}

.drop-hint span {
  color: var(--app-text-muted);
  font-size: 13px;
}
</style>
