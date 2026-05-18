<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import type { UploadUserFile } from 'element-plus';
import { UploadFilled } from '@element-plus/icons-vue';
import EmptyState from './common/EmptyState.vue';
import type { BatchDocumentIngestionResponse, BatchUploadDocumentPayload } from '../types';

type UploadStatus = 'pending' | 'uploading' | 'success' | 'failed';

interface EditableUploadItem {
  uid: number | string;
  file: File;
  sourceId: string;
  title: string;
  status: UploadStatus;
  errorMessage: string;
  chunkCount: number | null;
}

const props = defineProps<{
  loading: boolean;
  result: BatchDocumentIngestionResponse | null;
  errorMessage?: string;
}>();

const emit = defineEmits<{
  submit: [payload: BatchUploadDocumentPayload];
  remove: [sourceId: string];
}>();

const fileList = ref<UploadUserFile[]>([]);
const uploadItems = ref<EditableUploadItem[]>([]);
const selectedCount = computed(() => uploadItems.value.length);

watch(
  fileList,
  (nextFileList) => {
    const previousItems = new Map(uploadItems.value.map((item) => [String(item.uid), item]));

    uploadItems.value = nextFileList.flatMap((file) => {
      const rawFile = file.raw;
      if (!(rawFile instanceof File)) {
        return [];
      }

      const uid = file.uid ?? `${rawFile.name}-${rawFile.size}-${rawFile.lastModified}`;
      const previous = previousItems.get(String(uid));
      return [{
        uid,
        file: rawFile,
        sourceId: previous?.sourceId ?? '',
        title: previous?.title ?? '',
        status: previous?.status ?? 'pending',
        errorMessage: previous?.errorMessage ?? '',
        chunkCount: previous?.chunkCount ?? null,
      }];
    });
  },
  { deep: true },
);

watch(
  () => props.result,
  (result) => {
    if (!result) {
      return;
    }

    result.items.forEach((itemResult) => {
      const target = uploadItems.value[itemResult.index];
      if (!target) {
        return;
      }

      target.status = itemResult.accepted ? 'success' : 'failed';
      target.errorMessage = itemResult.errorMessage ?? '';
      target.chunkCount = null;
      if (itemResult.accepted && itemResult.sourceId) {
        target.sourceId = itemResult.sourceId || target.sourceId;
      }
    });
  },
);

watch(
  () => props.errorMessage,
  (errorMessage) => {
    if (!errorMessage) {
      return;
    }

    uploadItems.value.forEach((item) => {
      if (item.status !== 'uploading') {
        return;
      }
      item.status = 'failed';
      item.errorMessage = errorMessage;
    });
  },
);

function buildFallbackSourceId(file: File) {
  return `upload-${file.name}-${file.size}-${file.lastModified}`.replace(/[^\p{L}\p{N}._-]+/gu, '-').slice(0, 128);
}

function handleSubmit() {
  if (!uploadItems.value.length) {
    ElMessage.warning('请先选择至少一个文件');
    return;
  }

  uploadItems.value.forEach((item) => {
    item.sourceId = item.sourceId.trim() || buildFallbackSourceId(item.file);
    item.status = 'uploading';
    item.errorMessage = '';
    item.chunkCount = null;
  });

  emit('submit', {
    items: uploadItems.value.map((item) => ({
      file: item.file,
      sourceId: item.sourceId,
      title: item.title.trim() || undefined,
    })),
  });
}

function removeItem(uid: number | string) {
  const target = uploadItems.value.find((item) => String(item.uid) === String(uid));
  if (target?.sourceId) {
    emit('remove', target.sourceId);
  }
  fileList.value = fileList.value.filter((file) => String(file.uid) !== String(uid));
}

function statusTagType(status: UploadStatus) {
  switch (status) {
    case 'uploading':
      return 'warning';
    case 'success':
      return 'success';
    case 'failed':
      return 'danger';
    default:
      return 'info';
  }
}

function statusText(status: UploadStatus) {
  switch (status) {
    case 'uploading':
      return '提交中';
    case 'success':
      return '已入队';
    case 'failed':
      return '失败';
    default:
      return '待上传';
  }
}

function formatFileSize(size: number) {
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / (1024 * 1024)).toFixed(2)} MB`;
}
</script>

<template>
  <el-card shadow="never" class="panel-card">
    <template #header>
      <div class="panel-header">
        <div>
          <p class="section-kicker">Ingestion</p>
          <h2>批量上传论文</h2>
          <p>选择 PDF、图片或文本类论文资料，补充 sourceId 与标题后统一提交解析。</p>
        </div>
        <div class="header-actions">
          <el-tag type="info">已选 {{ selectedCount }} 个文件</el-tag>
          <el-button type="primary" :loading="props.loading" @click="handleSubmit">开始批量上传</el-button>
        </div>
      </div>
    </template>

    <div class="upload-form">
      <el-upload v-model:file-list="fileList" drag :auto-upload="false" :multiple="true">
        <el-icon class="upload-icon"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽文件到这里，或 <em>点击选择多个文件</em></div>
        <template #tip>
          <div class="el-upload__tip">不会自动上传；确认元数据后提交到异步处理队列，接口仍使用 multipart/form-data。</div>
        </template>
      </el-upload>

      <EmptyState
        v-if="!uploadItems.length"
        compact
        title="等待选择文件"
        description="选择文件后，可在下方逐条编辑标题和 sourceId。"
      />

      <div v-else class="upload-item-list">
        <div v-for="item in uploadItems" :key="item.uid" class="upload-item-card">
          <div class="upload-item-header">
            <div class="file-meta">
              <strong>{{ item.file.name }}</strong>
              <span>{{ formatFileSize(item.file.size) }}</span>
            </div>
            <div class="file-actions">
              <el-tag :type="statusTagType(item.status)">{{ statusText(item.status) }}</el-tag>
              <el-button text type="danger" :disabled="props.loading" @click="removeItem(item.uid)">移除</el-button>
            </div>
          </div>

          <el-row :gutter="16">
            <el-col :xs="24" :sm="12" :lg="10">
              <el-form-item label="sourceId（可选）">
                <el-input v-model="item.sourceId" :disabled="props.loading" placeholder="例如 transformer-survey-2024" clearable />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :lg="10">
              <el-form-item label="标题（可选）">
                <el-input v-model="item.title" :disabled="props.loading" placeholder="论文展示标题" clearable />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :lg="4">
              <el-form-item label="分块数">
                <span class="chunk-text">异步完成后更新</span>
              </el-form-item>
            </el-col>
          </el-row>

          <el-alert v-if="item.errorMessage" type="error" :closable="false" show-icon :title="item.errorMessage" />
        </div>
      </div>
    </div>
  </el-card>
</template>

<style scoped>
.panel-card {
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  box-shadow: var(--app-shadow);
}

.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.section-kicker {
  margin: 0 0 6px;
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.panel-header h2 {
  margin: 0;
  font-size: 20px;
}

.panel-header p:last-child {
  margin: 6px 0 0;
  color: var(--app-text-muted);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.upload-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.upload-icon {
  font-size: 34px;
  color: var(--app-primary);
}

.upload-item-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.upload-item-card {
  padding: 16px;
  border: 1px solid var(--app-border);
  border-radius: 16px;
  background: #fff;
}

.upload-item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.file-meta {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.file-meta strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-meta span {
  color: var(--app-text-muted);
  font-size: 12px;
}

.file-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chunk-text {
  color: #334155;
  line-height: 32px;
}

@media (max-width: 760px) {
  .panel-header,
  .header-actions,
  .upload-item-header {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>