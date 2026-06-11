<script setup lang="ts">
import { formatJson } from '../../../utils/format';
import type { ReviewReport } from '../../../types';

defineProps<{
  selectedReport: ReviewReport | null;
}>();
</script>

<template>
  <section class="detail-section">
    <div class="section-title">
      <h3>评审留档信息</h3>
      <span>模型、提示词、指标版本与人工调整记录</span>
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
  margin-top: 22px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.section-title h3 {
  margin: 4px 0 0;
  color: #101828;
}

.section-title span {
  color: #667085;
  font-size: 12px;
}

.audit-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.audit-grid article {
  border: 1px solid #dde3ee;
  border-radius: 10px;
  padding: 16px;
  background: #fff;
  box-shadow: none;
}

.audit-grid span {
  display: block;
  color: #667085;
  font-size: 12px;
}

.audit-grid strong {
  display: block;
  margin-top: 8px;
  color: #101828;
  line-height: 1.6;
}

.manual-delta {
  margin: 14px 0 0;
  border: 1px solid #dde3ee;
  border-radius: 10px;
  padding: 16px;
  background: #f8fafc;
  color: #101828;
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

  .section-title {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
