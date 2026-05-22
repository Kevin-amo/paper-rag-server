<script setup lang="ts">
import EmptyState from '../common/EmptyState.vue';
import type { LiteratureSearchResult } from '../../types';

const props = defineProps<{
  items: LiteratureSearchResult[];
  loading?: boolean;
  errorMessage?: string;
  lastQuery?: string;
  hasSearched?: boolean;
}>();

function sourceLabel(source: string | null | undefined) {
        if (source === 'openalex') {
    return 'OpenAlex';
  }
  return source || '来源未知';
}

function metaText(item: LiteratureSearchResult) {
  return [
    item.authors.length ? item.authors.join(', ') : '作者未知',
    item.year ?? '年份未知',
    item.primaryCategory || item.categories[0] || '分类未知',
  ].join(' · ');
}
</script>

<template>
  <section
    v-loading="props.loading"
    class="literature-results"
    :class="{ 'is-empty': !props.items.length }"
  >
    <EmptyState
      v-if="!props.hasSearched && !props.loading"
      title="搜索外部论文"
      description="输入关键词即可检索论文元数据。"
    />

    <div v-else-if="props.errorMessage" class="literature-state error-state">
      <strong>文献搜索失败</strong>
      <span>{{ props.errorMessage }}</span>
    </div>

    <div v-else-if="props.hasSearched && !props.loading && !props.items.length" class="literature-state">
      <strong>未找到相关论文</strong>
      <span v-if="props.lastQuery">关键词：{{ props.lastQuery }}</span>
    </div>

    <div v-else class="literature-panel">
      <div class="literature-header">
        <div>
          <span class="mode-badge">文献搜索结果</span>
          <h2>{{ props.lastQuery || 'Papers' }}</h2>
        </div>
        <span class="result-count">{{ props.items.length }} 篇</span>
      </div>

      <article v-for="(item, index) in props.items" :key="`${item.externalId || item.title || index}`" class="paper-card">
        <div class="paper-index">{{ index + 1 }}</div>
        <div class="paper-body">
          <h3>{{ item.title || '未命名论文' }}</h3>
          <p class="paper-meta">{{ metaText(item) }}</p>
          <p v-if="item.abstractText" class="paper-abstract">{{ item.abstractText }}</p>
          <div class="paper-tags">
            <span>{{ sourceLabel(item.source) }}</span>
            <span v-if="item.externalId">{{ item.externalId }}</span>
            <span v-for="category in item.categories" :key="category">{{ category }}</span>
          </div>
          <div class="paper-links">
            <a v-if="item.url" :href="item.url" target="_blank" rel="noopener noreferrer">Abs</a>
            <a v-if="item.pdfUrl" :href="item.pdfUrl" target="_blank" rel="noopener noreferrer">PDF</a>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.literature-results {
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex: 1;
  flex-direction: column;
  padding: clamp(24px, 4vw, 44px) 0 150px;
  background: linear-gradient(180deg, #fbfcff 0%, #ffffff 46%);
}

.literature-results.is-empty {
  align-items: center;
  justify-content: center;
  padding: 48px 0 64px;
}

.literature-results.is-empty :deep(.empty-state) {
  width: min(820px, calc(100% - 48px));
  min-height: 220px;
  border-color: #dbeafe;
  background: #ffffff;
  box-shadow: none;
}

.literature-panel {
  width: min(960px, calc(100% - 48px));
  margin: 0 auto;
  display: grid;
  gap: 16px;
}

.literature-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
  border: 1px solid #dbeafe;
  border-radius: 24px;
  background: #f8fbff;
}

.mode-badge {
  display: inline-flex;
  padding: 5px 10px;
  border-radius: 999px;
  background: #e0f2fe;
  color: #0369a1;
  font-size: 12px;
  font-weight: 800;
}

.literature-header h2 {
  margin: 10px 0 0;
  color: var(--app-text);
  font-size: 20px;
}

.result-count {
  color: var(--app-text-muted);
  font-size: 13px;
  font-weight: 800;
  white-space: nowrap;
}

.paper-card {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: 14px;
  padding: 18px;
  border: 1px solid #e0ecff;
  border-radius: 24px;
  background: #ffffff;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.04);
}

.paper-index {
  width: 34px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: #eef6ff;
  color: #2563eb;
  font-weight: 900;
}

.paper-body {
  min-width: 0;
  display: grid;
  gap: 10px;
}

.paper-body h3 {
  margin: 0;
  color: var(--app-text);
  font-size: 17px;
  line-height: 1.5;
}

.paper-meta {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.paper-abstract {
  margin: 0;
  color: #374151;
  line-height: 1.75;
  white-space: pre-wrap;
}

.paper-tags,
.paper-links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.paper-tags span {
  padding: 5px 9px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
  font-size: 12px;
  font-weight: 800;
}

.paper-links a {
  color: #2563eb;
  font-size: 13px;
  font-weight: 900;
  text-decoration: none;
}

.paper-links a:hover {
  text-decoration: underline;
}

.literature-state {
  width: min(760px, calc(100% - 48px));
  margin: auto;
  padding: 24px;
  border: 1px solid #e5e7eb;
  border-radius: 24px;
  background: #ffffff;
  color: var(--app-text-muted);
  text-align: center;
}

.literature-state strong,
.literature-state span {
  display: block;
}

.literature-state strong {
  margin-bottom: 8px;
  color: var(--app-text);
  font-size: 16px;
}

.error-state {
  border-color: #fecaca;
  background: #fff7f7;
  color: #b91c1c;
}

@media (max-width: 640px) {
  .literature-results {
    padding-bottom: 178px;
  }

  .literature-panel,
  .literature-state {
    width: calc(100% - 24px);
  }

  .paper-card {
    grid-template-columns: 1fr;
  }
}
</style>