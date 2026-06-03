<script lang="ts">
export default {
  name: 'DocumentList',
};
</script>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { Search, Refresh } from '@element-plus/icons-vue';
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
  deletingAllDocuments: boolean;
}>();

const emit = defineEmits<{
  search: [keyword: string];
  pageChange: [page: number];
  rowClick: [document: DocumentSummary];
  refresh: [];
  delete: [document: DocumentSummary];
  deleteAll: [];
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
      <ConfirmDeleteButton
        title="确认清空全部论文吗？删除后将无法在问答中引用。"
        confirm-text="清空"
        :loading="props.deletingAllDocuments"
        :disabled="!props.documents.length || props.loading"
        @confirm="emit('deleteAll')"
      >
        清空
      </ConfirmDeleteButton>
    </div>

    <div v-loading="props.loading" class="library-content">
      <el-empty v-if="!props.documents.length" description="还没有论文文档" :image-size="120">
        <span class="empty-hint">请在聊天界面通过拖拽或点击"+"按钮上传论文</span>
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
  padding: 10px;
  border: 1px solid rgba(255, 255, 255, 0.76);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.58);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.78);
}

.empty-hint {
  display: block;
  margin-top: 8px;
  color: var(--app-text-muted);
  font-size: 13px;
}

.library-toolbar .el-button,
.library-toolbar :deep(.el-input__wrapper) {
  height: 38px;
  border-radius: 999px;
  box-shadow: none;
}

.library-toolbar :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.82);
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
  padding: 15px;
  border: 1px solid rgba(255, 255, 255, 0.74);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.68);
  cursor: pointer;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.78);
  transition: border-color 0.16s ease, background 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.paper-card:hover {
  border-color: rgba(0, 122, 255, 0.28);
  background: rgba(255, 255, 255, 0.86);
  box-shadow: 0 14px 28px rgba(15, 23, 42, 0.08);
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
  color: var(--app-text);
  font-size: 15px;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.paper-meta,
.paper-subtle {
  display: flex;
  flex-wrap: wrap;
  gap: 7px 12px;
  margin-top: 8px;
  color: var(--app-text-muted);
  font-size: 13px;
}

.paper-subtle {
  color: var(--app-text-subtle);
  font-size: 12px;
}

.paper-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.paper-actions .el-button {
  border-radius: 999px;
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