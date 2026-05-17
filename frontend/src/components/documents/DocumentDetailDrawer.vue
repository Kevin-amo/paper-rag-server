<script lang="ts">
export default {
  name: 'DocumentDetailDrawer',
};
</script>

<script setup lang="ts">
import { computed } from 'vue';
import StatusTag from '../common/StatusTag.vue';
import type { DocumentDetail } from '../../types';

const props = defineProps<{
  modelValue: boolean;
  detail: DocumentDetail | null;
  loading: boolean;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
});

const isDev = computed(() => import.meta.env.DEV);

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

function formatJson(value: unknown) {
  if (!value) return '-';
  return JSON.stringify(value, null, 2);
}
</script>

<template>
  <el-drawer v-model="visible" title="论文详情" size="min(680px, 94vw)" destroy-on-close>
    <el-skeleton :loading="props.loading" animated :rows="9">
      <template v-if="props.detail">
        <section class="detail-hero">
          <div>
            <p>Paper Detail</p>
            <h2>{{ props.detail.title || props.detail.fileName || '未命名论文' }}</h2>
          </div>
          <StatusTag :status="props.detail.status" />
        </section>

        <section class="detail-section">
          <h3>论文信息</h3>
          <dl class="info-grid">
            <div>
              <dt>作者</dt>
              <dd>{{ formatList(props.detail.authors) }}</dd>
            </div>
            <div>
              <dt>摘要</dt>
              <dd class="wide">{{ props.detail.abstractText || '暂无摘要' }}</dd>
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
          </dl>
        </section>

        <section class="detail-section">
          <h3>文件与处理状态</h3>
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

        <section v-if="props.detail.contentText" class="detail-section">
          <h3>正文预览</h3>
          <p class="content-preview">{{ props.detail.contentText }}</p>
        </section>

        <el-collapse v-if="isDev" class="debug-collapse">
          <el-collapse-item title="技术信息（仅开发环境）" name="debug">
            <pre>{{ formatJson({ sourceId: props.detail.sourceId, ownerUserId: props.detail.ownerUserId, origin: props.detail.origin, metadata: props.detail.metadata, deletedAt: props.detail.deletedAt }) }}</pre>
          </el-collapse-item>
        </el-collapse>
      </template>

      <el-empty v-else description="请选择一篇论文" />
    </el-skeleton>
  </el-drawer>
</template>

<style scoped>
.detail-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 18px;
  padding: 18px;
  border: 1px solid rgba(37, 99, 235, 0.12);
  border-radius: 20px;
  background: linear-gradient(135deg, #eff6ff, #fff);
}

.detail-hero p {
  margin: 0 0 7px;
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.detail-hero h2 {
  margin: 0;
  color: #0f172a;
  font-size: 21px;
  line-height: 1.45;
}

.detail-section {
  margin-top: 18px;
}

.detail-section h3 {
  margin: 0 0 12px;
  color: #172554;
  font-size: 16px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 0;
}

.info-grid div {
  min-width: 0;
  padding: 13px 14px;
  border: 1px solid rgba(226, 232, 240, 0.92);
  border-radius: 15px;
  background: #fff;
}

.info-grid div:has(.wide) {
  grid-column: 1 / -1;
}

dt {
  margin-bottom: 6px;
  color: #94a3b8;
  font-size: 12px;
  font-weight: 700;
}

dd {
  margin: 0;
  color: #334155;
  line-height: 1.7;
  word-break: break-word;
}

.status-alert {
  margin-top: 12px;
  border-radius: 13px;
}

.content-preview,
.debug-collapse pre {
  max-height: 260px;
  overflow: auto;
  margin: 0;
  padding: 14px 16px;
  border: 1px solid var(--app-border);
  border-radius: 15px;
  background: #f8fafc;
  color: #334155;
  line-height: 1.75;
  white-space: pre-wrap;
}

.debug-collapse {
  margin-top: 18px;
}

@media (max-width: 640px) {
  .info-grid {
    grid-template-columns: 1fr;
  }
}
</style>