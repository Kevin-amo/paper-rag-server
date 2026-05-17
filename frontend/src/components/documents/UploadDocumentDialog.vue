<script lang="ts">
export default {
  name: 'UploadDocumentDialog',
};
</script>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import type { UploadUserFile } from 'element-plus';
import { UploadFilled } from '@element-plus/icons-vue';
import type { BatchDocumentIngestionResponse, BatchUploadDocumentPayload } from '../../types';

type UploadStatus = 'pending' | 'uploading' | 'success' | 'failed';

interface EditableUploadItem {
  uid: number | string;
  file: File;
  title: string;
  status: UploadStatus;
  errorMessage: string;
  chunkCount: number | null;
}

const props = defineProps<{
  modelValue: boolean;
  loading: boolean;
  result: BatchDocumentIngestionResponse | null;
  errorMessage?: string;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
  submit: [payload: BatchUploadDocumentPayload];
}>();

const fileList = ref<UploadUserFile[]>([]);
const uploadItems = ref<EditableUploadItem[]>([]);

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
});

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
        title: previous?.title ?? rawFile.name.replace(/\.[^.]+$/, ''),
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
      if (itemResult.success && itemResult.source?.title) {
        target.title = itemResult.source.title;
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
      if (item.status === 'uploading') {
        item.status = 'failed';
        item.errorMessage = errorMessage;
      }
    });
  },
);

function buildFallbackSourceId(file: File) {
  return `paper-${file.name}-${file.size}-${file.lastModified}`.replace(/[^\p{L}\p{N}._-]+/gu, '-').slice(0, 128);
}

function submitUpload() {
  if (!uploadItems.value.length) {
    ElMessage.warning('请先选择论文文件');
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
      sourceId: buildFallbackSourceId(item.file),
      title: item.title.trim() || undefined,
    })),
  });
}

function removeItem(uid: number | string) {
  fileList.value = fileList.value.filter((file) => String(file.uid) !== String(uid));
}

function clearFinished() {
  fileList.value = [];
  uploadItems.value = [];
}

function statusType(status: UploadStatus) {
  if (status === 'success') return 'success';
  if (status === 'failed') return 'danger';
  if (status === 'uploading') return 'warning';
  return 'info';
}

function statusText(status: UploadStatus) {
  if (status === 'success') return '上传完成';
  if (status === 'failed') return '上传失败';
  if (status === 'uploading') return '处理中';
  return '待上传';
}

function formatFileSize(size: number) {
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / (1024 * 1024)).toFixed(2)} MB`;
}
</script>

<template>
  <el-dialog v-model="visible" title="上传论文" width="min(720px, 92vw)" class="upload-dialog" destroy-on-close>
    <div class="upload-intro">
      <strong>把论文加入个人知识库</strong>
      <span>支持批量选择或拖拽上传。上传后系统会解析并建立索引，完成后即可在问答中引用。</span>
    </div>

    <el-upload
      v-model:file-list="fileList"
      drag
      :auto-upload="false"
      :multiple="true"
      :show-file-list="false"
      class="compact-upload"
    >
      <el-icon class="upload-icon"><UploadFilled /></el-icon>
      <div class="el-upload__text">拖拽论文文件到这里，或 <em>点击选择</em></div>
      <template #tip>
        <div class="el-upload__tip">建议上传 PDF、文本或图片型论文资料。标题可在下方编辑。</div>
      </template>
    </el-upload>

    <div v-if="uploadItems.length" class="upload-list">
      <article v-for="item in uploadItems" :key="item.uid" class="upload-item">
        <div class="file-info">
          <strong>{{ item.file.name }}</strong>
          <span>{{ formatFileSize(item.file.size) }}</span>
        </div>
        <el-input v-model="item.title" :disabled="props.loading" placeholder="论文标题" />
        <el-tag :type="statusType(item.status)" effect="light">{{ statusText(item.status) }}</el-tag>
        <el-button text type="danger" :disabled="props.loading" @click="removeItem(item.uid)">移除</el-button>
        <el-alert v-if="item.errorMessage" class="upload-error" type="error" :closable="false" :title="item.errorMessage" />
      </article>
    </div>

    <el-alert
      v-if="props.result"
      class="upload-result"
      type="success"
      show-icon
      :closable="false"
      :title="`上传完成：成功 ${props.result.successCount} 个，失败 ${props.result.failureCount} 个`"
    />

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button :disabled="!uploadItems.length || props.loading" @click="clearFinished">清空列表</el-button>
      <el-button type="primary" :loading="props.loading" @click="submitUpload">开始上传</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.upload-intro {
  display: grid;
  gap: 6px;
  margin-bottom: 16px;
  padding: 14px 16px;
  border: 1px solid rgba(37, 99, 235, 0.12);
  border-radius: 16px;
  background: #f8fbff;
}

.upload-intro strong {
  color: #172554;
  font-size: 16px;
}

.upload-intro span {
  color: var(--app-text-muted);
  line-height: 1.65;
}

.upload-icon {
  color: var(--app-primary);
  font-size: 34px;
}

.compact-upload :deep(.el-upload-dragger) {
  padding: 22px;
  border-radius: 18px;
  background: #fbfdff;
}

.upload-list {
  display: grid;
  gap: 10px;
  margin-top: 16px;
}

.upload-item {
  display: grid;
  grid-template-columns: minmax(170px, 1fr) minmax(180px, 1fr) auto auto;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--app-border);
  border-radius: 16px;
  background: #fff;
}

.file-info {
  min-width: 0;
}

.file-info strong,
.file-info span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-info span {
  margin-top: 4px;
  color: var(--app-text-muted);
  font-size: 12px;
}

.upload-error,
.upload-result {
  grid-column: 1 / -1;
  margin-top: 4px;
  border-radius: 12px;
}

@media (max-width: 720px) {
  .upload-item {
    grid-template-columns: 1fr;
    align-items: stretch;
  }
}
</style>