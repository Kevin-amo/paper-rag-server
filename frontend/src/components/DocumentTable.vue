<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import StatusTag from './common/StatusTag.vue';
import EmptyState from './common/EmptyState.vue';
import ConfirmDeleteButton from './common/ConfirmDeleteButton.vue';
import type { DocumentSummary } from '../types';

const props = defineProps<{
  documents: DocumentSummary[];
  loading: boolean;
  keyword: string;
  page: number;
  size: number;
  total: number;
  deletingSourceId: string | null;
  canDelete?: boolean;
}>();

const emit = defineEmits<{
  search: [keyword: string];
  pageChange: [page: number];
  rowClick: [document: DocumentSummary];
  refresh: [];
  delete: [document: DocumentSummary];
}>();

const localKeyword = ref(props.keyword);
const currentPage = computed(() => props.page + 1);

watch(
  () => props.keyword,
  (value) => {
    localKeyword.value = value;
  },
);

function fileTypeTagType(fileType: string) {
  if (!fileType) {
    return 'info';
  }
  if (fileType.startsWith('image/')) {
    return 'warning';
  }
  if (fileType.includes('pdf')) {
    return 'success';
  }
  return 'info';
}

function formatDate(value: string) {
  return value ? new Date(value).toLocaleString() : '-';
}

function handleCurrentChange(value: number) {
  emit('pageChange', value - 1);
}
</script>

<template>
  <el-card shadow="never" class="table-card">
    <template #header>
      <div class="table-header">
        <div>
          <p class="section-kicker">Document Library</p>
          <h2>文档管理</h2>
          <p>检索已上传论文，查看解析详情、分块内容与关联图片。</p>
        </div>

        <div class="toolbar">
          <el-input
            v-model="localKeyword"
            clearable
            placeholder="按标题 / sourceId 搜索"
            @keyup.enter="emit('search', localKeyword)"
          />
          <el-button @click="emit('refresh')">刷新</el-button>
          <el-button type="primary" @click="emit('search', localKeyword)">搜索</el-button>
        </div>
      </div>
    </template>

    <EmptyState
      v-if="!props.loading && !props.documents.length"
      title="暂无文档"
      description="上传论文后会在这里展示解析状态、分块数量与更新时间。"
    />

    <template v-else>
      <el-table :data="props.documents" stripe height="520" :loading="props.loading" @row-click="emit('rowClick', $event)">
        <el-table-column prop="title" label="标题" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="title-cell">
              <strong>{{ row.title || row.fileName || row.sourceId }}</strong>
              <span>{{ row.sourceId }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="fileType" label="类型" width="140">
          <template #default="{ row }">
            <el-tag :type="fileTypeTagType(row.fileType)" size="small">{{ row.fileType || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <StatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="分块" width="86" />
        <el-table-column prop="publishYear" label="年份" width="86">
          <template #default="{ row }">{{ row.publishYear || '-' }}</template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="170">
          <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column v-if="props.canDelete" label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <ConfirmDeleteButton
              title="确认删除这篇文档吗？"
              :loading="props.deletingSourceId === row.sourceId"
              @confirm="emit('delete', row)"
            />
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :total="props.total"
          :page-size="props.size"
          :current-page="currentPage"
          @current-change="handleCurrentChange"
        />
      </div>
    </template>
  </el-card>
</template>

<style scoped>
.table-card {
  height: 100%;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  box-shadow: var(--app-shadow);
}

.table-header {
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

.table-header h2 {
  margin: 0;
  font-size: 20px;
}

.table-header p:last-child {
  margin: 6px 0 0;
  color: var(--app-text-muted);
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: min(430px, 100%);
}

.title-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.title-cell span {
  color: var(--app-text-muted);
  font-size: 12px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

:deep(.el-table__row) {
  cursor: pointer;
}

@media (max-width: 760px) {
  .table-header,
  .toolbar {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>