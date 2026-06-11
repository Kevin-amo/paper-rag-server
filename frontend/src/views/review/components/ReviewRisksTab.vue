<script setup lang="ts">
import { riskTypeMap, riskStatusLabel } from '../../../constants/review';
import type { ReviewRiskRecord } from '../../../types';

defineProps<{
  riskRecords: ReviewRiskRecord[];
  riskLoading: boolean;
  riskStatusUpdatingIds: string[];
}>();

defineEmits<{
  'set-risk-status': [riskId: string, status: 'CONFIRMED' | 'IGNORED' | 'RESOLVED'];
}>();

function isRiskUpdating(riskId: string, updatingIds: string[]) {
  return updatingIds.includes(riskId);
}

function isRiskActionDisabled(
  risk: ReviewRiskRecord,
  status: 'CONFIRMED' | 'IGNORED' | 'RESOLVED',
  updatingIds: string[],
) {
  return isRiskUpdating(risk.id, updatingIds) || risk.status === status;
}
</script>

<template>
  <section class="detail-section">
    <div class="section-title">
      <div>
        <h3>风险提示</h3>
        <span>政治表述、参考文献、结构与语言风险的规范化记录</span>
      </div>
    </div>
    <div v-loading="riskLoading">
      <div v-if="riskRecords.length" class="risk-list normalized-risk-list">
        <article v-for="risk in riskRecords" :key="risk.id">
          <el-tag :type="riskTypeMap[risk.riskLevel] || 'info'" effect="plain">{{ risk.riskLevel }}</el-tag>
          <div>
            <div class="risk-heading">
              <strong>{{ risk.riskType }}</strong>
              <el-tag size="small" effect="plain">{{ riskStatusLabel(risk.status) }}</el-tag>
            </div>
            <p>{{ risk.evidence || '未给出证据' }}</p>
            <span>{{ risk.suggestion || '建议人工复核' }}</span>
            <span v-if="risk.confidence != null">置信度：{{ risk.confidence }}</span>
            <span v-if="risk.detector">检测器：{{ risk.detector }}</span>
            <div class="risk-actions">
              <el-button
                size="small"
                type="primary"
                plain
                :loading="isRiskUpdating(risk.id, riskStatusUpdatingIds)"
                :disabled="isRiskActionDisabled(risk, 'CONFIRMED', riskStatusUpdatingIds)"
                @click="$emit('set-risk-status', risk.id, 'CONFIRMED')"
              >
                确认
              </el-button>
              <el-button
                size="small"
                plain
                :loading="isRiskUpdating(risk.id, riskStatusUpdatingIds)"
                :disabled="isRiskActionDisabled(risk, 'IGNORED', riskStatusUpdatingIds)"
                @click="$emit('set-risk-status', risk.id, 'IGNORED')"
              >
                忽略
              </el-button>
              <el-button
                size="small"
                type="success"
                plain
                :loading="isRiskUpdating(risk.id, riskStatusUpdatingIds)"
                :disabled="isRiskActionDisabled(risk, 'RESOLVED', riskStatusUpdatingIds)"
                @click="$emit('set-risk-status', risk.id, 'RESOLVED')"
              >
                标记解决
              </el-button>
            </div>
          </div>
        </article>
      </div>
      <el-empty v-else description="暂无风险提示" />
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

.section-title h3 {
  margin: 4px 0 0;
  color: #101828;
}

.section-title span {
  color: #667085;
  font-size: 12px;
}

.risk-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 12px;
}

.risk-list article {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 6px 10px;
  border: 1px solid #dde3ee;
  border-radius: 10px;
  padding: 14px;
  background: #fff;
}

.risk-list strong,
.risk-list p,
.risk-list span {
  grid-column: 2;
}

.risk-list strong {
  color: #101828;
}

.risk-list p {
  color: #475467;
  line-height: 1.7;
}

.risk-list span {
  color: #667085;
  font-size: 12px;
}

.risk-list p,
.risk-list span,
.risk-heading {
  overflow-wrap: anywhere;
}

.risk-heading,
.risk-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.risk-actions {
  margin-top: 10px;
}

@media (max-width: 720px) {
  .section-title {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
