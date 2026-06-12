<script setup lang="ts">
import { formatJson } from '../../../utils/format';
import type { ReviewReport } from '../../../types';

defineProps<{
  selectedReport: ReviewReport | null;
}>();
</script>

<template>
  <section class="detail-section">
    <div class="section-header">
      <h3>评审留档信息</h3>
      <p>模型、提示词、指标版本与人工调整记录</p>
    </div>
    <div v-if="selectedReport" class="audit-grid">
      <article>
        <span>模型版本</span>
        <strong>{{ selectedReport.modelVersion || '-' }}</strong>
      </article>
      <article>
        <span>Prompt 版本</span>
        <strong>{{ selectedReport.promptVersion || '-' }}</strong>
      </article>
      <article>
        <span>指标版本</span>
        <strong>{{ selectedReport.criterionVersion ?? '-' }}</strong>
      </article>
      <article>
        <span>置信度</span>
        <strong>{{ selectedReport.confidence ?? '-' }}</strong>
      </article>
    </div>
    <pre v-if="selectedReport" class="manual-delta">{{ formatJson(selectedReport.manualDelta) }}</pre>
    <el-empty v-else description="生成辅助评审后展示留档信息" />
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

.audit-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.audit-grid article {
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  padding: 14px;
  background: var(--app-surface);
}

.audit-grid span {
  display: block;
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 500;
}

.audit-grid strong {
  display: block;
  margin-top: 6px;
  color: var(--app-text);
  font-size: 14px;
  line-height: 1.5;
}

.manual-delta {
  margin: 14px 0 0;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  padding: 14px;
  background: var(--app-surface-soft);
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

@media (max-width: 1180px) {
  .audit-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .audit-grid {
    grid-template-columns: 1fr;
  }
}
</style>
