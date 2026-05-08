<script setup lang="ts">
import { computed } from 'vue';
import type { DocumentChunk, DocumentDetail } from '../types';

const props = defineProps<{
  modelValue: boolean;
  detail: DocumentDetail | null;
  chunks: DocumentChunk[];
  loading: boolean;
  chunkLoading: boolean;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
});

function formatDate(value: string | null) {
  if (!value) {
    return '-';
  }

  return new Date(value).toLocaleString();
}

function formatValue(value: unknown) {
  if (value === null || value === undefined || value === '') {
    return '-';
  }

  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
    return String(value);
  }

  return JSON.stringify(value, null, 2);
}
</script>

<template>
  <el-drawer v-model="visible" title="文档详情" size="56%" destroy-on-close>
    <el-skeleton :loading="props.loading" animated :rows="10">
      <template v-if="props.detail">
        <el-tabs>
          <el-tab-pane label="基础信息">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="sourceId">{{ props.detail.sourceId }}</el-descriptions-item>
              <el-descriptions-item label="标题">{{ props.detail.title || '-' }}</el-descriptions-item>
              <el-descriptions-item label="来源">{{ props.detail.origin || '-' }}</el-descriptions-item>
              <el-descriptions-item label="文件名">{{ props.detail.fileName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="状态">{{ props.detail.status }}</el-descriptions-item>
              <el-descriptions-item label="Chunks">{{ props.detail.chunkCount }}</el-descriptions-item>
              <el-descriptions-item label="期刊">{{ props.detail.journal || '-' }}</el-descriptions-item>
              <el-descriptions-item label="年份">{{ props.detail.publishYear || '-' }}</el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ formatDate(props.detail.createdAt) }}</el-descriptions-item>
              <el-descriptions-item label="更新时间">{{ formatDate(props.detail.updatedAt) }}</el-descriptions-item>
            </el-descriptions>

            <div class="block">
              <h3>摘要</h3>
              <p>{{ props.detail.abstractText || '暂无摘要' }}</p>
            </div>

            <div class="block">
              <h3>扩展元数据</h3>
              <pre>{{ formatValue(props.detail.metadata) }}</pre>
            </div>
          </el-tab-pane>

          <el-tab-pane label="Chunks">
            <el-skeleton :loading="props.chunkLoading" animated :rows="6">
              <el-empty v-if="!props.chunks.length" description="暂无分片数据" />
              <div v-else class="chunk-list">
                <el-card v-for="chunk in props.chunks" :key="chunk.chunkId" shadow="hover" class="chunk-card">
                  <template #header>
                    <div class="chunk-header">
                      <strong>#{{ chunk.chunkIndex }}</strong>
                      <span>{{ chunk.sectionTitle || '未命名分段' }}</span>
                      <small>page: {{ chunk.pageNumber ?? '-' }}</small>
                    </div>
                  </template>
                  <p class="chunk-content">{{ chunk.content }}</p>
                </el-card>
              </div>
            </el-skeleton>
          </el-tab-pane>
        </el-tabs>
      </template>

      <el-empty v-else description="请选择一篇文档" />
    </el-skeleton>
  </el-drawer>
</template>

<style scoped>
.block {
  margin-top: 20px;
}

.block h3 {
  margin: 0 0 10px;
  font-size: 15px;
}

.block p,
.block pre {
  margin: 0;
  padding: 14px 16px;
  border-radius: 14px;
  background: #f8fafc;
  color: #334155;
  white-space: pre-wrap;
  word-break: break-word;
}

.chunk-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chunk-card {
  border-radius: 18px;
}

.chunk-header {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #475569;
}

.chunk-content {
  margin: 0;
  color: #1f2937;
  line-height: 1.7;
  white-space: pre-wrap;
}
</style>