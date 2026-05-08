<script setup lang="ts">
import { ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import type { UploadUserFile } from 'element-plus';
import { UploadFilled } from '@element-plus/icons-vue';
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
}>();

const emit = defineEmits<{
  submit: [payload: BatchUploadDocumentPayload];
}>();

const fileList = ref<UploadUserFile[]>([]);
const uploadItems = ref<EditableUploadItem[]>([]);

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

      target.status = itemResult.success ? 'success' : 'failed';
      target.errorMessage = itemResult.errorMessage ?? '';
      target.chunkCount = itemResult.chunkCount ?? null;
      if (itemResult.success && itemResult.source) {
        target.sourceId = itemResult.source.sourceId || target.sourceId;
        target.title = itemResult.source.title || target.title;
      }
    });
  },
);

function handleSubmit() {
  if (!uploadItems.value.length) {
    ElMessage.warning('请先选择至少一个文件');
    return;
  }

  uploadItems.value.forEach((item) => {
    item.status = 'uploading';
    item.errorMessage = '';
    item.chunkCount = null;
  });

  emit('submit', {
    items: uploadItems.value.map((item) => ({
      file: item.file,
      sourceId: item.sourceId.trim() || undefined,
      title: item.title.trim() || undefined,
    })),
  });
}

function removeItem(uid: number | string) {
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
      return '上传中';
    case 'success':
      return '成功';
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
          <h2>文档上传</h2>
          <p>支持批量选择文件，并为每个文件单独填写 sourceId 和标题。</p>
        </div>
        <el-button type="primary" :loading="props.loading" @click="handleSubmit">开始批量上传</el-button>
      </div>
    </template>

    <div class="upload-form">
      <el-upload
        v-model:file-list="fileList"
        drag
        :auto-upload="false"
        :multiple="true"
      >
        <el-icon class="upload-icon"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽文件到这里，或 <em>点击选择多个文件</em></div>
        <template #tip>
          <div class="el-upload__tip">已选择的文件会出现在下方列表，可逐条编辑元数据后一次性提交。</div>
        </template>
      </el-upload>

      <el-empty v-if="!uploadItems.length" description="尚未选择文件" />

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
            <el-col :span="10">
              <el-form-item label="sourceId（可选）">
                <el-input v-model="item.sourceId" :disabled="props.loading" placeholder="例如 sample-paper" clearable />
              </el-form-item>
            </el-col>
            <el-col :span="10">
              <el-form-item label="标题（可选）">
                <el-input v-model="item.title" :disabled="props.loading" placeholder="前端展示标题" clearable />
              </el-form-item>
            </el-col>
            <el-col :span="4">
              <el-form-item label="Chunks">
                <span class="chunk-text">{{ item.chunkCount ?? '-' }}</span>
              </el-form-item>
            </el-col>
          </el-row>

          <el-alert
            v-if="item.errorMessage"
            type="error"
            :closable="false"
            show-icon
            :title="item.errorMessage"
          />
        </div>
      </div>
    </div>
  </el-card>
</template>

<style scoped>
.panel-card {
  border: none;
  border-radius: 20px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.panel-header h2 {
  margin: 0;
  font-size: 18px;
}

.panel-header p {
  margin: 6px 0 0;
  color: #6b7280;
}

.upload-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.upload-icon {
  font-size: 30px;
  color: #2563eb;
}

.upload-item-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.upload-item-card {
  padding: 16px;
  border: 1px solid #e5e7eb;
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
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.file-meta span {
  color: #6b7280;
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
</style>