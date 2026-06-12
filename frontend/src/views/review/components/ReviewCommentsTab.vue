<script setup lang="ts">
import { textValue, listValue } from '../../../utils/format';
import type { ReviewReport } from '../../../types';

defineProps<{
  comments: Record<string, unknown>;
  selectedReport: ReviewReport | null;
}>();
</script>

<template>
  <section class="detail-section">
    <div class="section-header">
      <h3>个性化评语</h3>
      <p>优点、不足与修改建议</p>
    </div>
    <div class="comments-card">
      <p class="comment-summary">{{ textValue(comments.summary || comments.finalAdvice, selectedReport?.finalRecommendation || '暂无评语') }}</p>
      <div class="comment-columns">
        <div>
          <strong>主要优点</strong>
          <ul>
            <li v-for="item in listValue(comments.strengths)" :key="item">{{ item }}</li>
            <li v-if="!listValue(comments.strengths).length">暂无</li>
          </ul>
        </div>
        <div>
          <strong>问题与建议</strong>
          <ul>
            <li v-for="item in [...listValue(comments.weaknesses), ...listValue(comments.suggestions)]" :key="item">{{ item }}</li>
            <li v-if="![...listValue(comments.weaknesses), ...listValue(comments.suggestions)].length">暂无</li>
          </ul>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.detail-section {
  margin-top: 20px;
}

.section-header {
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--app-border);
}

.section-header h3 {
  margin: 0;
  color: var(--app-text);
  font-size: 16px;
  font-weight: 700;
}

.section-header p {
  margin: 4px 0 0;
  color: var(--app-text-muted);
  font-size: 13px;
}

.comments-card {
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  padding: 16px;
  background: var(--app-surface);
}

.comment-summary {
  color: var(--app-text-muted);
  line-height: 1.7;
}

.comment-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--app-border);
}

.comment-columns strong {
  color: var(--app-text);
  font-size: 13px;
  font-weight: 600;
}

.comment-columns ul {
  margin: 8px 0 0;
  padding-left: 16px;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.8;
}

@media (max-width: 720px) {
  .comment-columns {
    grid-template-columns: 1fr;
  }
}
</style>
