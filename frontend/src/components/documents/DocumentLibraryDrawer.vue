<script lang="ts">
export default {
  name: 'DocumentLibraryDrawer',
};
</script>

<script setup lang="ts">
import { computed } from 'vue';
import DocumentList from './DocumentList.vue';
import type { DocumentSummary } from '../../types';

const props = defineProps<{
  modelValue: boolean;
  documents: DocumentSummary[];
  loading: boolean;
  keyword: string;
  page: number;
  size: number;
  total: number;
  deletingSourceId: string | null;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
  search: [keyword: string];
  pageChange: [page: number];
  rowClick: [document: DocumentSummary];
  refresh: [];
  delete: [document: DocumentSummary];
  upload: [];
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
});
</script>

<template>
  <el-drawer v-model="visible" size="min(760px, 94vw)" destroy-on-close class="document-library-drawer">
    <template #header>
      <div class="drawer-heading">
        <p>Document Library</p>
        <h2>论文文档库</h2>
        <span>管理已上传论文，查看处理状态，并打开详情。</span>
      </div>
    </template>

    <DocumentList
      :documents="props.documents"
      :loading="props.loading"
      :keyword="props.keyword"
      :page="props.page"
      :size="props.size"
      :total="props.total"
      :deleting-source-id="props.deletingSourceId"
      @search="emit('search', $event)"
      @page-change="emit('pageChange', $event)"
      @row-click="emit('rowClick', $event)"
      @refresh="emit('refresh')"
      @delete="emit('delete', $event)"
      @upload="emit('upload')"
    />
  </el-drawer>
</template>

<style scoped>
.drawer-heading {
  display: grid;
  gap: 4px;
}

.drawer-heading p {
  margin: 0;
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 850;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.drawer-heading h2 {
  margin: 0;
  color: var(--app-text);
  font-size: 23px;
  letter-spacing: -0.025em;
}

.drawer-heading span {
  display: block;
  margin-top: 2px;
  color: var(--app-text-muted);
  line-height: 1.6;
}
</style>