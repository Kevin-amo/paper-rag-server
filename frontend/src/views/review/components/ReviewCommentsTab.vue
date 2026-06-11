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
    <div class="comments-card">
      <div class="section-title compact">
        <h3>个性化评语</h3>
        <span>优点、不足与修改建议</span>
      </div>
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
  margin-top: 22px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.section-title.compact {
  align-items: flex-start;
  flex-direction: column;
  gap: 2px;
}

.section-title h3 {
  margin: 4px 0 0;
  color: #101828;
}

.section-title span {
  color: #667085;
  font-size: 12px;
}

.comments-card {
  border: 1px solid #dde3ee;
  border-radius: 10px;
  padding: 16px;
  background: #fff;
  box-shadow: none;
}

.comment-summary {
  color: #475467;
  line-height: 1.7;
}

.comment-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.comment-columns strong {
  color: #101828;
}

.comment-columns ul {
  margin: 8px 0 0;
  padding-left: 18px;
  color: #667085;
  line-height: 1.8;
}

@media (max-width: 720px) {
  .comment-columns {
    grid-template-columns: 1fr;
  }

  .section-title {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
