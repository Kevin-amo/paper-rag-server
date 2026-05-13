<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { getDocumentAssetContentUrl, listDocumentAssets } from '../api/documents';
import type { DocumentAsset, DocumentChunk, DocumentDetail } from '../types';

const props = defineProps<{
  modelValue: boolean;
  detail: DocumentDetail | null;
  chunks: DocumentChunk[];
  loading: boolean;
  chunkLoading: boolean;
  chunkPage: number;
  chunkSize: number;
  chunkTotal: number;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
  'chunk-page-change': [page: number];
  'chunk-size-change': [size: number];
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
});

const assets = ref<DocumentAsset[]>([]);

watch(
  () => [props.detail?.sourceId, props.chunks.map((chunk) => chunk.chunkId).join('|')],
  async () => {
    const sourceId = props.detail?.sourceId;
    const assetIds = Array.from(new Set(props.chunks.reduce<string[]>((ids, chunk) => {
      ids.push(...chunkAssetIds(chunk));
      return ids;
    }, [])));
    if (!sourceId || !assetIds.length) {
      assets.value = [];
      return;
    }
    assets.value = await listDocumentAssets(sourceId, assetIds);
  },
  { immediate: true },
);

const assetMap = computed(() => new Map(assets.value.map((asset) => [asset.assetId, asset])));

function chunkAssetIds(chunk: DocumentChunk) {
  const assetIds = chunk.metadata?.assetIds;
  return Array.isArray(assetIds) ? assetIds.filter((value): value is string => typeof value === 'string') : [];
}

function imageAssetsForChunk(chunk: DocumentChunk) {
  return chunkAssetIds(chunk)
    .map((assetId) => assetMap.value.get(assetId))
    .filter((asset): asset is DocumentAsset => asset !== undefined && asset.contentType?.startsWith('image/') === true);
}

function assetContentUrl(asset: DocumentAsset) {
  return getDocumentAssetContentUrl(asset.sourceId, asset.assetId);
}

function previewUrls(chunk: DocumentChunk) {
  return imageAssetsForChunk(chunk).map(assetContentUrl);
}

function handleChunkCurrentChange(page: number) {
  emit('chunk-page-change', page - 1);
}

function handleChunkSizeChange(size: number) {
  emit('chunk-size-change', size);
}

function formatFileSize(size: number | null) {
  if (size === null) {
    return '-';
  }
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / (1024 * 1024)).toFixed(2)} MB`;
}

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
              <el-descriptions-item label="文件类型">{{ props.detail.fileType || '-' }}</el-descriptions-item>
              <el-descriptions-item label="状态">{{ props.detail.status }}</el-descriptions-item>
              <el-descriptions-item label="Chunks">{{ props.detail.chunkCount }}</el-descriptions-item>
              <el-descriptions-item label="期刊">{{ props.detail.journal || '-' }}</el-descriptions-item>
              <el-descriptions-item label="年份">{{ props.detail.publishYear || '-' }}</el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ formatDate(props.detail.createdAt) }}</el-descriptions-item>
              <el-descriptions-item label="更新时间">{{ formatDate(props.detail.updatedAt) }}</el-descriptions-item>
            </el-descriptions>

            <div class="block">
              <h3>解析信息</h3>
              <el-descriptions :column="2" border>
                <el-descriptions-item label="解析方式">{{ formatValue(props.detail.metadata?.extractionMode) }}</el-descriptions-item>
                <el-descriptions-item label="内容类型">{{ formatValue(props.detail.metadata?.contentType) }}</el-descriptions-item>
                <el-descriptions-item label="页数">{{ formatValue(props.detail.metadata?.renderedPageCount) }}</el-descriptions-item>
                <el-descriptions-item label="多模态截断">{{ props.detail.metadata?.multimodalTruncated ? '是' : '否' }}</el-descriptions-item>
              </el-descriptions>
            </div>

            <div class="block">
              <h3>正文提取结果</h3>
              <pre>{{ props.detail.contentText || '暂无正文' }}</pre>
            </div>

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
                  <div v-if="imageAssetsForChunk(chunk).length" class="chunk-assets">
                    <small>关联图片</small>
                    <div class="asset-preview-list">
                      <div v-for="asset in imageAssetsForChunk(chunk)" :key="asset.assetId" class="asset-preview-card">
                        <el-image
                          class="asset-preview-image"
                          fit="cover"
                          :src="assetContentUrl(asset)"
                          :preview-src-list="previewUrls(chunk)"
                          :initial-index="previewUrls(chunk).indexOf(assetContentUrl(asset))"
                          preview-teleported
                        />
                        <div class="asset-preview-meta">
                          <strong>{{ asset.fileName || asset.assetId }}</strong>
                          <span>{{ asset.contentType || 'unknown' }} · {{ formatFileSize(asset.fileSize) }}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                  <div v-if="chunk.metadata && Object.keys(chunk.metadata).length" class="chunk-extra">
                    <small>片段元数据</small>
                    <pre>{{ formatValue(chunk.metadata) }}</pre>
                  </div>
                </el-card>
              </div>
              <div v-if="props.chunkTotal > 0" class="chunk-pagination">
                <el-pagination
                  background
                  layout="total, sizes, prev, pager, next, jumper"
                  :current-page="props.chunkPage + 1"
                  :page-size="props.chunkSize"
                  :page-sizes="[20, 50, 100, 200]"
                  :total="props.chunkTotal"
                  :disabled="props.chunkLoading"
                  @current-change="handleChunkCurrentChange"
                  @size-change="handleChunkSizeChange"
                />
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
.block pre,
.chunk-extra pre {
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

.chunk-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
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

.chunk-extra {
  margin-top: 12px;
}

.chunk-extra small,
.chunk-assets small {
  display: inline-block;
  margin-bottom: 6px;
  color: #64748b;
}

.chunk-assets {
  margin-top: 12px;
}

.asset-preview-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.asset-preview-card {
  width: 168px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #fff;
}

.asset-preview-image {
  display: block;
  width: 168px;
  height: 116px;
  background: #f8fafc;
}

.asset-preview-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 10px;
  color: #64748b;
  font-size: 12px;
}

.asset-preview-meta strong {
  overflow: hidden;
  color: #334155;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>