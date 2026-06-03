import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import { uploadDocumentsBatch } from '../api/documents';
import { getErrorMessage } from '../api/http';
import type { BatchUploadDocumentPayload, BatchDocumentIngestionResponse } from '../types';

export type UploadQueueItemStatus = 'pending' | 'uploading' | 'success' | 'failed';

export interface UploadQueueItem {
  id: string;
  fileName: string;
  fileSize: number;
  status: UploadQueueItemStatus;
  errorMessage?: string;
  progress?: number;
}

let idCounter = 0;

function generateId() {
  idCounter++;
  return `upload-${Date.now()}-${idCounter}`;
}

function buildFallbackSourceId(file: File) {
  return `paper-${file.name}-${file.size}-${file.lastModified}`
    .replace(/[^\p{L}\p{N}._-]+/gu, '-')
    .slice(0, 128);
}

export function useChatUpload(options: {
  onSuccess?: () => void;
} = {}) {
  const queue = ref<UploadQueueItem[]>([]);
  const isUploading = ref(false);

  function addFiles(files: File[]) {
    const newItems: UploadQueueItem[] = files.map((file) => ({
      id: generateId(),
      fileName: file.name,
      fileSize: file.size,
      status: 'pending',
    }));
    queue.value.push(...newItems);
    return newItems;
  }

  function removeItem(id: string) {
    queue.value = queue.value.filter((item) => item.id !== id);
  }

  function clearQueue() {
    queue.value = [];
  }

  function updateItem(id: string, patch: Partial<UploadQueueItem>) {
    const item = queue.value.find((i) => i.id === id);
    if (item) {
      Object.assign(item, patch);
    }
  }

  async function uploadFiles(files: File[]) {
    if (!files.length) return;

    const items = addFiles(files);
    isUploading.value = true;

    const payload: BatchUploadDocumentPayload = {
      items: files.map((file) => ({
        file,
        sourceId: buildFallbackSourceId(file),
        title: file.name.replace(/\.[^.]+$/, ''),
      })),
    };

    items.forEach((item) => {
      updateItem(item.id, { status: 'uploading', progress: 0 });
    });

    // Simulate progress animation
    const progressTimers = items.map((item) => {
      let progress = 10;
      return window.setInterval(() => {
        progress = Math.min(progress + Math.random() * 20, 85);
        updateItem(item.id, { progress: Math.round(progress) });
      }, 400);
    });

    try {
      const result: BatchDocumentIngestionResponse = await uploadDocumentsBatch(payload);

      progressTimers.forEach((timer) => window.clearInterval(timer));

      result.items.forEach((resItem, index) => {
        const queueItem = items[index];
        if (!queueItem) return;

        if (resItem.accepted) {
          updateItem(queueItem.id, { status: 'success', progress: 100 });
        } else {
          updateItem(queueItem.id, {
            status: 'failed',
            progress: 100,
            errorMessage: resItem.errorMessage || '上传失败',
          });
        }
      });

      const { acceptedCount, failureCount } = result;
      if (acceptedCount > 0 && failureCount === 0) {
        ElMessage.success(`上传完成：${acceptedCount} 个文件上传成功`);
      } else if (acceptedCount > 0) {
        ElMessage.warning(`上传完成：成功 ${acceptedCount} 个，失败 ${failureCount} 个`);
      } else {
        ElMessage.error('上传失败，请检查文件格式或网络连接');
      }

      options.onSuccess?.();
    } catch (error) {
      progressTimers.forEach((timer) => window.clearInterval(timer));

      const message = getErrorMessage(error);
      items.forEach((item) => {
        updateItem(item.id, {
          status: 'failed',
          progress: 100,
          errorMessage: message || '上传请求失败',
        });
      });

      ElMessage.error(`上传失败：${message}`);
    } finally {
      isUploading.value = false;
    }
  }

  return {
    queue,
    isUploading,
    addFiles,
    removeItem,
    clearQueue,
    uploadFiles,
  };
}
