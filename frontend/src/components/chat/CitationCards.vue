<script lang="ts">
export default {
  name: 'CitationCards',
};
</script>

<script setup lang="ts">
import { computed } from 'vue';
import { Document, TrendCharts } from '@element-plus/icons-vue';
import type { AnswerCitation } from '../../types';

const props = defineProps<{
  citations: AnswerCitation[];
}>();

const visibleCitations = computed(() => props.citations.filter((citation) => citation.excerpt || citation.title || citation.sourceId));

const maxRankScore = computed(() => Math.max(
  0,
  ...visibleCitations.value.map((citation) => Number.isFinite(citation.rankScore) ? citation.rankScore : 0),
));

function scorePercent(score: number) {
  if (!Number.isFinite(score) || maxRankScore.value <= 0) {
    return '—';
  }

  const normalized = Math.round((score / maxRankScore.value) * 100);
  return `${Math.max(0, Math.min(normalized, 100))}%`;
}
</script>

<template>
  <div v-if="visibleCitations.length" class="citation-cards">
    <div class="citation-heading">
      <div class="citation-title">
        <el-icon><Document /></el-icon>
        <span>引用依据</span>
      </div>
      <span class="citation-count">{{ visibleCitations.length }} 条</span>
    </div>

    <article v-for="citation in visibleCitations" :key="`${citation.sourceId}-${citation.chunkIndex}`" class="citation-card">
      <div class="citation-topline">
        <strong>{{ citation.title || citation.sourceId || '未命名论文' }}</strong>
        <span class="score-pill">
          <el-icon><TrendCharts /></el-icon>
          排序分 {{ scorePercent(citation.rankScore) }}
        </span>
      </div>
      <p :title="citation.excerpt || '暂无片段摘要'">{{ citation.excerpt || '暂无片段摘要' }}</p>
      <div class="citation-meta">
        <span>{{ citation.sourceId || '未知来源' }}</span>
        <span>片段 {{ citation.chunkIndex + 1 }}</span>
      </div>
    </article>
  </div>
</template>

<style scoped>
.citation-cards {
  display: grid;
  gap: 10px;
  margin-top: 18px;
  padding: 12px;
  border: 1px solid var(--app-border);
  border-radius: 18px;
  background: #f7f8fa;
}

.citation-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  color: var(--app-text);
  font-size: 13px;
  font-weight: 800;
}

.citation-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.citation-count {
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 700;
}

.citation-card {
  padding: 12px;
  border: 1px solid #edf0f4;
  border-radius: 14px;
  background: #ffffff;
}

.citation-topline {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.citation-topline strong {
  min-width: 0;
  color: var(--app-text);
  font-size: 13px;
  line-height: 1.5;
}

.score-pill {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  padding: 0 8px;
  border-radius: 999px;
  background: var(--app-primary-soft);
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 800;
}

.citation-card p {
  display: -webkit-box;
  overflow: hidden;
  margin: 9px 0 8px;
  color: #4b5563;
  font-size: 13px;
  line-height: 1.7;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.citation-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  color: #9ca3af;
  font-size: 12px;
}
</style>