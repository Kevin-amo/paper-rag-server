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

function scorePercent(score: number) {
  if (!Number.isFinite(score)) {
    return '—';
  }

  const normalized = score > 1 ? Math.min(score, 100) : Math.round(score * 100);
  return `${normalized}%`;
}
</script>

<template>
  <div v-if="visibleCitations.length" class="citation-cards">
    <div class="citation-heading">
      <el-icon><Document /></el-icon>
      <span>引用依据</span>
    </div>

    <article v-for="citation in visibleCitations" :key="`${citation.sourceId}-${citation.chunkIndex}`" class="citation-card">
      <div class="citation-topline">
        <strong>{{ citation.title || citation.sourceId || '未命名论文' }}</strong>
        <el-tag size="small" effect="light" type="success">
          <el-icon><TrendCharts /></el-icon>
          相关度 {{ scorePercent(citation.rankScore) }}
        </el-tag>
      </div>
      <p>{{ citation.excerpt || '暂无片段摘要' }}</p>
      <small>{{ citation.sourceId || '未知来源' }} · 片段 {{ citation.chunkIndex + 1 }}</small>
    </article>
  </div>
</template>

<style scoped>
.citation-cards {
  display: grid;
  gap: 9px;
  margin-top: 14px;
}

.citation-heading {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #475569;
  font-size: 13px;
  font-weight: 800;
}

.citation-card {
  padding: 12px;
  border: 1px solid rgba(37, 99, 235, 0.12);
  border-radius: 14px;
  background: linear-gradient(180deg, #fff, #f8fbff);
}

.citation-topline {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.citation-topline strong {
  min-width: 0;
  color: #172554;
  font-size: 13px;
  line-height: 1.5;
}

.citation-card p {
  display: -webkit-box;
  overflow: hidden;
  margin: 8px 0 6px;
  color: #475569;
  font-size: 13px;
  line-height: 1.65;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.citation-card small {
  color: #94a3b8;
}

.el-tag :deep(.el-icon) {
  margin-right: 3px;
}
</style>