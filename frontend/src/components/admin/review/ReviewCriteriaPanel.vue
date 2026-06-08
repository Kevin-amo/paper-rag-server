<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { listReviewCriteria } from '../../../api/reviews';
import { getErrorMessage } from '../../../api/http';
import type { ReviewCriterion } from '../../../types';

const loading = ref(false);
const criteria = ref<ReviewCriterion[]>([]);

async function loadCriteria() {
  loading.value = true;
  try {
    criteria.value = await listReviewCriteria(true);
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    loading.value = false;
  }
}

onMounted(loadCriteria);
</script>

<template>
  <el-table :data="criteria" v-loading="loading" class="criteria-table">
    <el-table-column prop="code" label="Code" width="140" />
    <el-table-column prop="name" label="名称" min-width="180" show-overflow-tooltip />
    <el-table-column prop="weight" label="权重" width="100" />
    <el-table-column label="启用" width="100">
      <template #default="{ row }">
        <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="description" label="描述" min-width="260" show-overflow-tooltip>
      <template #default="{ row }">{{ row.description || '-' }}</template>
    </el-table-column>
  </el-table>
</template>

<style scoped>
.criteria-table {
  overflow: hidden;
  border: 1px solid var(--app-border);
  border-radius: 18px;
}
</style>
