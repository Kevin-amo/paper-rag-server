<script lang="ts">
export default {
  name: 'ChatUploadQueue',
};
</script>

<script setup lang="ts">
import { computed } from 'vue';
import { CircleCheckFilled, CircleCloseFilled, Loading, Document } from '@element-plus/icons-vue';

export interface UploadQueueItem {
  id: string;
  fileName: string;
  fileSize: number;
  status: 'pending' | 'uploading' | 'success' | 'failed';
  errorMessage?: string;
  progress?: number;
}

const props = defineProps<{
  items: UploadQueueItem[];
}>();

const emit = defineEmits<{
  remove: [id: string];
  clear: [];
}>();

const hasItems = computed(() => props.items.length > 0);
const pendingCount = computed(() => props.items.filter((i) => i.status === 'pending' || i.status === 'uploading').length);
const successCount = computed(() => props.items.filter((i) => i.status === 'success').length);
const failedCount = computed(() => props.items.filter((i) => i.status === 'failed').length);

function formatSize(size: number) {
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / (1024 * 1024)).toFixed(2)} MB`;
}

function statusLabel(status: UploadQueueItem['status']) {
  switch (status) {
    case 'pending': return '等待上传';
    case 'uploading': return '正在上传';
    case 'success': return '上传成功';
    case 'failed': return '上传失败';
  }
}
</script>

<template>
  <Transition name="queue">
    <div v-if="hasItems" class="upload-queue">
      <div class="queue-header">
        <div class="queue-title">
          <el-icon><Document /></el-icon>
          <span>上传队列</span>
          <small v-if="pendingCount > 0">{{ pendingCount }} 个进行中</small>
          <small v-else-if="successCount > 0 && failedCount === 0" class="all-success">全部完成</small>
          <small v-else-if="failedCount > 0" class="has-failed">{{ failedCount }} 个失败</small>
        </div>
        <el-button text size="small" @click="emit('clear')">清空</el-button>
      </div>
      <div class="queue-list">
        <div
          v-for="item in props.items"
          :key="item.id"
          class="queue-item"
          :class="`status-${item.status}`"
        >
          <div class="queue-item-info">
            <span class="queue-item-name">{{ item.fileName }}</span>
            <span class="queue-item-size">{{ formatSize(item.fileSize) }}</span>
          </div>
          <div class="queue-item-meta">
            <span class="queue-item-status">
              <el-icon v-if="item.status === 'uploading'" class="is-loading"><Loading /></el-icon>
              <el-icon v-else-if="item.status === 'success'" class="status-icon success"><CircleCheckFilled /></el-icon>
              <el-icon v-else-if="item.status === 'failed'" class="status-icon failed"><CircleCloseFilled /></el-icon>
              {{ statusLabel(item.status) }}
            </span>
            <el-button
              v-if="item.status === 'failed' || item.status === 'success'"
              text
              type="danger"
              size="small"
              @click="emit('remove', item.id)"
            >
              移除
            </el-button>
          </div>
          <div v-if="item.status === 'uploading' && item.progress !== undefined" class="queue-progress">
            <div class="queue-progress-bar" :style="{ width: `${item.progress}%` }" />
          </div>
          <div v-if="item.errorMessage" class="queue-error">{{ item.errorMessage }}</div>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.upload-queue {
  position: absolute;
  right: 16px;
  bottom: 120px;
  z-index: 20;
  width: min(380px, calc(100% - 32px));
  max-height: 320px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.76);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 18px 44px rgba(15, 23, 42, 0.1);
  backdrop-filter: blur(22px) saturate(170%);
  -webkit-backdrop-filter: blur(22px) saturate(170%);
}

.queue-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 14px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.04);
}

.queue-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--app-text);
  font-size: 13px;
  font-weight: 750;
}

.queue-title small {
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(0, 122, 255, 0.08);
  color: var(--app-primary);
  font-size: 11px;
  font-weight: 700;
}

.queue-title small.all-success {
  background: rgba(103, 194, 58, 0.1);
  color: #67c23a;
}

.queue-title small.has-failed {
  background: rgba(245, 108, 108, 0.1);
  color: #f56c6c;
}

.queue-list {
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px 10px;
}

.queue-item {
  display: grid;
  gap: 6px;
  padding: 8px 10px;
  border-radius: 12px;
  background: rgba(247, 248, 252, 0.72);
  transition: background 0.16s ease;
}

.queue-item-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
}

.queue-item-name {
  overflow: hidden;
  min-width: 0;
  color: var(--app-text);
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.queue-item-size {
  flex: none;
  color: var(--app-text-muted);
  font-size: 11px;
}

.queue-item-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.queue-item-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--app-text-muted);
  font-size: 12px;
}

.status-icon.success {
  color: #67c23a;
}

.status-icon.failed {
  color: #f56c6c;
}

.is-loading {
  animation: rotating 1s linear infinite;
}

@keyframes rotating {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.queue-progress {
  height: 4px;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.06);
}

.queue-progress-bar {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--app-primary), #5ac8fa);
  transition: width 0.3s ease;
}

.queue-error {
  color: #f56c6c;
  font-size: 11px;
  line-height: 1.45;
}

[class~="queue-enter-active"],
[class~="queue-leave-active"] {
  transition: all 0.25s ease;
}

[class~="queue-enter-from"],
[class~="queue-leave-to"] {
  opacity: 0;
  transform: translateY(10px) scale(0.98);
}
</style>
