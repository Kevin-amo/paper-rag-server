<script lang="ts">
export default {
  name: 'DocumentDetailDrawer',
};
</script>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import StatusTag from '../common/StatusTag.vue';
import { getDocumentAssetContentUrl, listDocumentAssets } from '../../api/documents';
import type { DocumentAsset, DocumentChunk, DocumentDetail } from '../../types';

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
  chunkPageChange: [page: number];
  chunkSizeChange: [size: number];
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
});

const assets = ref<DocumentAsset[]>([]);
const assetMap = computed(() => new Map(assets.value.map((asset) => [asset.assetId, asset])));

watch(
  () => [props.detail?.sourceId, props.chunks.map((chunk) => chunk.chunkId).join('|')],
  async () => {
    const sourceId = props.detail?.sourceId;
    const assetIds = Array.from(new Set(props.chunks.flatMap(chunkAssetIds)));
    if (!sourceId || !assetIds.length) {
      assets.value = [];
      return;
    }

    assets.value = await listDocumentAssets(sourceId, assetIds);
  },
  { immediate: true },
);

function formatDate(value: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN') : '-';
}

function formatFileSize(size: number | null) {
  if (size === null) return '-';
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / (1024 * 1024)).toFixed(2)} MB`;
}

function formatList(value: unknown) {
  if (!value) return '-';
  if (Array.isArray(value)) {
    return value.map((item) => (typeof item === 'string' ? item : JSON.stringify(item))).join('、') || '-';
  }
  if (typeof value === 'string') return value;
  return JSON.stringify(value);
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
  emit('chunkPageChange', page - 1);
}

function handleChunkSizeChange(size: number) {
  emit('chunkSizeChange', size);
}
</script>

<template>
  <el-drawer v-model="visible" title="文档详情" size="min(760px, 94vw)" destroy-on-close class="document-detail-drawer">
    <el-skeleton :loading="props.loading" animated :rows="9">
      <template v-if="props.detail">
        <section class="detail-hero">
          <div>
            <p>Paper Detail</p>
            <h2>{{ props.detail.title || props.detail.fileName || '未命名论文' }}</h2>
            <span>{{ props.detail.sourceId }}</span>
          </div>
          <StatusTag :status="props.detail.status" />
        </section>

        <section class="detail-section">
          <h3>基础信息</h3>
          <dl class="info-grid">
            <div>
              <dt>作者</dt>
              <dd>{{ formatList(props.detail.authors) }}</dd>
            </div>
            <div>
              <dt>DOI</dt>
              <dd>{{ props.detail.doi || '-' }}</dd>
            </div>
            <div>
              <dt>期刊</dt>
              <dd>{{ props.detail.journal || '-' }}</dd>
            </div>
            <div>
              <dt>年份</dt>
              <dd>{{ props.detail.publishYear || '-' }}</dd>
            </div>
            <div>
              <dt>关键词</dt>
              <dd>{{ formatList(props.detail.keywords) }}</dd>
            </div>
            <div>
              <dt>来源</dt>
              <dd>{{ props.detail.origin || '-' }}</dd>
            </div>
          </dl>
        </section>

        <section class="detail-section">
          <h3>解析信息</h3>
          <dl class="info-grid compact">
            <div>
              <dt>文件名</dt>
              <dd>{{ props.detail.fileName || '-' }}</dd>
            </div>
            <div>
              <dt>文件类型</dt>
              <dd>{{ props.detail.fileType || '-' }}</dd>
            </div>
            <div>
              <dt>文件大小</dt>
              <dd>{{ formatFileSize(props.detail.fileSize) }}</dd>
            </div>
            <div>
              <dt>分块数量</dt>
              <dd>{{ props.detail.chunkCount }}</dd>
            </div>
            <div>
              <dt>上传时间</dt>
              <dd>{{ formatDate(props.detail.createdAt) }}</dd>
            </div>
            <div>
              <dt>更新时间</dt>
              <dd>{{ formatDate(props.detail.updatedAt) }}</dd>
            </div>
          </dl>

          <el-alert
            v-if="props.detail.errorMessage"
            class="status-alert"
            type="error"
            show-icon
            :closable="false"
            :title="props.detail.errorMessage"
          />
        </section>

        <section class="detail-section reading-section">
          <h3>摘要</h3>
          <p>{{ props.detail.abstractText || '暂无摘要' }}</p>
        </section>

        <section v-if="props.detail.contentText" class="detail-section reading-section">
          <h3>正文预览</h3>
          <p class="content-preview">{{ props.detail.contentText }}</p>
        </section>

        <section class="detail-section">
          <div class="section-title-row">
            <h3>检索片段</h3>
            <span>{{ props.chunkTotal }} 条</span>
          </div>
          <el-skeleton :loading="props.chunkLoading" animated :rows="6">
            <el-empty v-if="!props.chunks.length" description="暂无分片数据" />
            <div v-else class="chunk-list">
              <article v-for="chunk in props.chunks" :key="chunk.chunkId" class="chunk-card">
                <div class="chunk-header">
                  <strong>片段 {{ chunk.chunkIndex + 1 }}</strong>
                  <span>{{ chunk.sectionTitle || '未命名分段' }}</span>
                  <small>page: {{ chunk.pageNumber ?? '-' }}</small>
                </div>
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

                <details v-if="chunk.metadata && Object.keys(chunk.metadata).length" class="chunk-extra">
                  <summary>片段元数据</summary>
                  <pre>{{ formatValue(chunk.metadata) }}</pre>
                </details>
              </article>
            </div>
            <div v-if="props.chunkTotal > 0" class="chunk-pagination">
              <el-pagination
                background
                layout="total, sizes, prev, pager, next"
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
        </section>

        <section class="detail-section reading-section">
          <h3>元数据</h3>
          <pre>{{ formatValue({ ownerUserId: props.detail.ownerUserId, origin: props.detail.origin, metadata: props.detail.metadata, deletedAt: props.detail.deletedAt }) }}</pre>
        </section>
      </template>

      <el-empty v-else description="请选择一篇论文" />
    </el-skeleton>
  </el-drawer>
</template>

<style scoped>
.detail-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 18px;
  padding: 18px;
  border: 1px solid var(--app-border);
  border-radius: 22px;
  background: #f7f8fa;
}

.detail-hero > div {
  min-width: 0;
  flex: 1 1 auto;
}

.detail-hero p {
  margin: 0 0 7px;
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.detail-hero h2 {
  margin: 0;
  color: var(--app-text);
  font-size: 21px;
  line-height: 1.45;
  word-break: break-word;
  overflow-wrap: break-word;
}

.detail-hero span {
  display: block;
  margin-top: 8px;
  color: var(--app-text-muted);
  font-size: 12px;
  word-break: break-all;
}

.detail-hero [class~="el-tag"] {
  flex-shrink: 0;
  align-self: center;
}

.detail-section {
  margin-top: 18px;
  padding: 16px;
  border: 1px solid var(--app-border);
  border-radius: 20px;
  background: #ffffff;
}

.detail-section h3,
.section-title-row h3 {
  margin: 0 0 12px;
  color: var(--app-text);
  font-size: 15px;
}

.section-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-title-row span {
  color: var(--app-text-muted);
  font-size: 12px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}

.info-grid div {
  min-width: 0;
  padding: 12px;
  border: 1px solid #edf0f4;
  border-radius: 14px;
  background: #f9fafb;
}

dt {
  margin-bottom: 6px;
  color: #9ca3af;
  font-size: 12px;
  font-weight: 700;
}

dd {
  margin: 0;
  color: #374151;
  line-height: 1.7;
  word-break: break-word;
}

.status-alert {
  margin-top: 12px;
  border-radius: 13px;
}

.reading-section p,
.reading-section pre,
.content-preview,
.chunk-extra pre {
  max-height: 320px;
  overflow: auto;
  margin: 0;
  padding: 14px 16px;
  border: 1px solid #edf0f4;
  border-radius: 15px;
  background: #f7f8fa;
  color: #374151;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.chunk-list {
  display: grid;
  gap: 12px;
}

.chunk-card {
  padding: 14px;
  border: 1px solid #edf0f4;
  border-radius: 18px;
  background: #f9fafb;
}

.chunk-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
  color: var(--app-text-muted);
}

.chunk-header strong {
  color: var(--app-text);
}

.chunk-header small {
  padding: 2px 8px;
  border-radius: 999px;
  background: #ffffff;
  color: #9ca3af;
}

.chunk-content {
  margin: 10px 0 0;
  color: #374151;
  line-height: 1.78;
  white-space: pre-wrap;
}

.chunk-assets,
.chunk-extra {
  margin-top: 12px;
}

.chunk-extra summary,
.chunk-assets small {
  display: inline-block;
  margin-bottom: 8px;
  color: var(--app-text-muted);
  cursor: pointer;
  font-size: 12px;
  font-weight: 700;
}

.asset-preview-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.asset-preview-card {
  width: 166px;
  overflow: hidden;
  border: 1px solid var(--app-border);
  border-radius: 14px;
  background: #fff;
}

.asset-preview-image {
  display: block;
  width: 166px;
  height: 112px;
  background: #f3f4f6;
}

.asset-preview-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 10px;
  color: var(--app-text-muted);
  font-size: 12px;
}

.asset-preview-meta strong {
  overflow: hidden;
  color: #374151;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chunk-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

@media (max-width: 640px) {
  .info-grid {
    grid-template-columns: 1fr;
  }

  .detail-section {
    padding: 14px;
  }
}
</style>
