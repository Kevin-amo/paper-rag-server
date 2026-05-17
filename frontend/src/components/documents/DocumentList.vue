<script lang="ts">
export default {
  name: 'DocumentList',
};
</script>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { Search, UploadFilled, Refresh } from '@element-plus/icons-vue';
import StatusTag from '../common/StatusTag.vue';
import ConfirmDeleteButton from '../common/ConfirmDeleteButton.vue';
import type { DocumentSummary } from '../../types';

const props = defineProps<{
  documents: DocumentSummary[];
  loading: boolean;
  keyword: string;
  page: number;
  size: number;
  total: number;
  deletingSourceId: string | null;
}>();

const emit = defineEmits<{
  search: [keyword: string];
  pageChange: [page: number];
  rowClick: [document: DocumentSummary];
  refresh: [];
  delete: [document: DocumentSummary];
  upload: [];
}>();

const localKeyword = ref(props.keyword);
const currentPage = computed(() => props.page + 1);

watch(
  () => props.keyword,
  (value) => {
    localKeyword.value = value;
  },
);

function displayTitle(document: DocumentSummary) {
  return document.title || document.fileName || '未命名论文';
}

function formatDate(value: string) {
  return value ? new Date(value).toLocaleString('zh-CN') : '-';
}

function formatFileSize(size: number | null) {
  if (size === null) return '-';
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / (1024 * 1024)).toFixed(2)} MB`;
}

function handleCurrentChange(value: number) {
  emit('pageChange', value - 1);
}
</script>

<template>
  <section class="document-list">
    <div class="library-toolbar">
      <el-input
        v-model="localKeyword"
        clearable
        :prefix-icon="Search"
        placeholder="搜索论文标题或文件名"
        @keyup.enter="emit('search', localKeyword)"
      />
      <el-button :icon="Refresh" @click="emit('refresh')">刷新</el-button>
      <el-button type="primary" :icon="UploadFilled" @click="emit('upload')">上传论文</el-button>
    </div>

    <div v-loading="props.loading" class="library-content">
      <el-empty v-if="!props.documents.length" description="还没有论文文档" :image-size="120">
        <el-button type="primary" :icon="UploadFilled" @click="emit('upload')">上传第一篇论文</el-button>
      </el-empty>

      <div v-else class="paper-card-list">
        <article v-for="document in props.documents" :key="document.sourceId" class="paper-card" @click="emit('rowClick', document)">
          <div class="paper-main">
            <div class="paper-title-row">
              <h3>{{ displayTitle(document) }}</h3>
              <StatusTag :status="document.status" />
            </div>
            <div class="paper-meta">
              <span>{{ document.fileName || '无文件名' }}</span>
              <span>{{ formatFileSize(document.fileSize) }}</span>
              <span>{{ document.publishYear || '年份未知' }}</span>
              <span>{{ formatDate(document.createdAt) }}</span>
            </div>
            <div class="paper-subtle">
              <span>分块 {{ document.chunkCount }}</span>
              <span v-if="document.origin">来源 {{ document.origin }}</span>
            </div>
          </div>

          <div class="paper-actions" @click.stop>
            <el-button text type="primary" @click="emit('rowClick', document)">查看详情</el-button>
            <ConfirmDeleteButton
              title="确认删除这篇论文吗？删除后将无法在问答中引用。"
              :loading="props.deletingSourceId === document.sourceId"
              @confirm="emit('delete', document)"
            />
          </div>
        </article>
      </div>
    </div>

    <div v-if="props.total > props.size" class="pagination-wrap">
      <el-pagination
        background
        layout="total, prev, pager, next"
        :total="props.total"
        :page-size="props.size"
        :current-page="currentPage"
        @current-change="handleCurrentChange"
      />
    </div>
  </section>
</template>

<style scoped>
.document-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 0;
}

.library-toolbar {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) auto auto;
  gap: 10px;
}

.library-toolbar .el-button,
.library-toolbar :deep(.el-input__wrapper) {
  border-radius: 13px;
}

.library-content {
  min-height: 360px;
}

.paper-card-list {
  display: grid;
  gap: 10px;
}

.paper-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border: 1px solid rgba(226, 232, 240, 0.92);
  border-radius: 18px;
  background: #fff;
  cursor: pointer;
  transition: 0.16s ease;
}

.paper-card:hover {
  border-color: rgba(37, 99, 235, 0.3);
  box-shadow: 0 16px 38px rgba(15, 23, 42, 0.08);
  transform: translateY(-1px);
}

.paper-main {
  min-width: 0;
}

.paper-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.paper-title-row h3 {
  overflow: hidden;
  margin: 0;
  color: #0f172a;
  font-size: 16px;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.paper-meta,
.paper-subtle {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  margin-top: 8px;
  color: #64748b;
  font-size: 13px;
}

.paper-subtle {
  color: #94a3b8;
  font-size: 12px;
}

.paper-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  padding-top: 6px;
}

@media (max-width: 760px) {
  .library-toolbar,
  .paper-card {
    grid-template-columns: 1fr;
  }

  .paper-actions {
    justify-content: flex-end;
  }
}
</style>