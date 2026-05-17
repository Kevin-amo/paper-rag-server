<script lang="ts">
export default {
  name: 'AvatarUploadDialog',
};
</script>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import type { UploadFile, UploadFiles, UploadUserFile } from 'element-plus';
import { UploadFilled } from '@element-plus/icons-vue';

const props = defineProps<{
  modelValue: boolean;
  loading?: boolean;
  currentAvatarUrl?: string | null;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
  submit: [file: File];
}>();

const fileList = ref<UploadUserFile[]>([]);
const selectedFile = ref<File | null>(null);
const previewUrl = ref('');
const displayAvatarUrl = computed(() => previewUrl.value || props.currentAvatarUrl || '');

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
});

watch(
  () => props.modelValue,
  (open) => {
    if (!open) {
      clearSelection();
    }
  },
);

function replacePreviewUrl(nextPreviewUrl: string) {
  revokePreviewUrl();
  previewUrl.value = nextPreviewUrl;
}

function validateAvatarFile(file: File) {
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
    ElMessage.warning('头像仅支持 JPG、PNG 或 WebP 图片');
    return false;
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('头像文件不能超过 5MB');
    return false;
  }
  return true;
}

function handleFileListChange(file: UploadFile, nextFileList: UploadFiles) {
  const rawFile = file.raw;
  if (!(rawFile instanceof File) || !validateAvatarFile(rawFile)) {
    clearSelection();
    return;
  }

  selectedFile.value = rawFile;
  fileList.value = nextFileList.slice(-1);
  replacePreviewUrl(URL.createObjectURL(rawFile));
}

function submitUpload() {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择头像图片');
    return;
  }
  emit('submit', selectedFile.value);
}

function clearSelection() {
  fileList.value = [];
  selectedFile.value = null;
  revokePreviewUrl();
}

function revokePreviewUrl() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value);
    previewUrl.value = '';
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="更换头像" width="min(460px, 92vw)" class="avatar-dialog" destroy-on-close>
    <div class="avatar-preview-wrap">
      <div class="avatar-preview">
        <img v-if="displayAvatarUrl" :src="displayAvatarUrl" alt="头像预览">
        <span v-else>头像</span>
      </div>
      <p>支持 JPG、PNG、WebP，最大 5MB。上传后会立即同步到当前账号。</p>
    </div>

    <el-upload
      :file-list="fileList"
      drag
      accept="image/jpeg,image/png,image/webp"
      :auto-upload="false"
      :limit="1"
      :show-file-list="false"
      class="avatar-upload"
      @change="handleFileListChange"
    >
      <el-icon class="upload-icon"><UploadFilled /></el-icon>
      <div class="el-upload__text">拖拽图片到这里，或 <em>点击选择</em></div>
      <template #tip>
        <div v-if="selectedFile" class="selected-file">已选择：{{ selectedFile.name }}</div>
      </template>
    </el-upload>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button :disabled="!selectedFile || props.loading" @click="clearSelection">重新选择</el-button>
      <el-button type="primary" :loading="props.loading" @click="submitUpload">上传头像</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.avatar-preview-wrap {
  display: grid;
  justify-items: center;
  gap: 12px;
  margin-bottom: 18px;
  padding: 18px;
  border: 1px solid rgba(37, 99, 235, 0.12);
  border-radius: 18px;
  background: #f8fbff;
  text-align: center;
}

.avatar-preview {
  display: grid;
  place-items: center;
  width: 96px;
  height: 96px;
  overflow: hidden;
  border-radius: 28px;
  color: var(--app-primary);
  background: var(--app-primary-soft);
  font-weight: 800;
}

.avatar-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-preview-wrap p {
  margin: 0;
  color: var(--app-text-muted);
  line-height: 1.6;
}

.upload-icon {
  color: var(--app-primary);
  font-size: 32px;
}

.avatar-upload :deep(.el-upload-dragger) {
  padding: 22px;
  border-radius: 18px;
  background: #fbfdff;
}

.selected-file {
  margin-top: 8px;
  color: var(--app-text-muted);
  font-size: 13px;
}
</style>