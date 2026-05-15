<script setup lang="ts">
import { computed, ref, watch } from 'vue';
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

watch(
  () => props.keyword,
  (value) => {
    localKeyword.value = value;
  },
);

const currentPage = computed(() => props.page + 1);

function statusTagType(status: string) {
  switch (status?.toUpperCase()) {
    case 'INDEXED':
    case 'READY':
      return 'success';
    case 'FAILED':
      return 'danger';
    case 'PROCESSING':
    case 'PENDING':
      return 'warning';
    default:
      return 'info';
  }
}

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
  if (!value) {
    return '-';
  }

  return new Date(value).toLocaleString();
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
          <h2>文档列表</h2>
          <p>点击任意一行可查看文档详情与 chunks。</p>
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

    <el-table :data="props.documents" stripe border height="520" :loading="props.loading" @row-click="emit('rowClick', $event)">
      <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip>
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
      <el-table-column prop="status" label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="chunkCount" label="Chunks" width="90" />
      <el-table-column prop="publishYear" label="年份" width="90" />
      <el-table-column prop="updatedAt" label="更新时间" min-width="170">
        <template #default="{ row }">
          {{ formatDate(row.updatedAt) }}
        </template>
      </el-table-column>
      <el-table-column v-if="props.canDelete" label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-popconfirm
            title="确认移除这篇文档吗？"
            confirm-button-text="确认"
            cancel-button-text="取消"
            @confirm="emit('delete', row)"
          >
            <template #reference>
              <el-button
                text
                type="danger"
                :loading="props.deletingSourceId === row.sourceId"
                @click.stop
              >
                删除
              </el-button>
            </template>
          </el-popconfirm>
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
  </el-card>
</template>

<style scoped>
.table-card {
  height: 100%;
  border: none;
  border-radius: 20px;
}

.table-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.table-header h2 {
  margin: 0;
  font-size: 18px;
}

.table-header p {
  margin: 6px 0 0;
  color: #6b7280;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 420px;
}

.title-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.title-cell span {
  color: #6b7280;
  font-size: 12px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>