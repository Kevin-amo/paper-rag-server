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
    <div class="section-header">
      <h3>风险提示</h3>
      <p>政治表述、参考文献、结构与语言风险的规范化记录</p>
    </div>
    <div v-loading="riskLoading">
      <div v-if="riskRecords.length" class="risk-list">
        <article v-for="risk in riskRecords" :key="risk.id" class="risk-card">
          <div class="risk-card-header">
            <div class="risk-type">
              <span class="risk-level-dot" :class="risk.riskLevel"></span>
              <strong>{{ risk.riskType }}</strong>
            </div>
            <el-tag size="small" effect="plain">{{ riskStatusLabel(risk.status) }}</el-tag>
          </div>
          <p class="risk-evidence">{{ risk.evidence || '未给出证据' }}</p>
          <p class="risk-suggestion">{{ risk.suggestion || '建议人工复核' }}</p>
          <div class="risk-meta">
            <span v-if="risk.confidence != null">置信度 {{ risk.confidence }}</span>
            <span v-if="risk.detector">检测器 {{ risk.detector }}</span>
          </div>
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
        </article>
      </div>
      <el-empty v-else description="暂无风险提示" />
    </div>
  </section>
</template>

<style scoped>
.detail-section {
  margin-top: 20px;
}

.section-header {
  margin-bottom: 16px;
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

.risk-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.risk-card {
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  padding: 16px;
  background: var(--app-surface);
  transition: border-color 0.15s ease;
}

.risk-card:hover {
  border-color: var(--app-border-strong);
}

.risk-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.risk-type {
  display: flex;
  align-items: center;
  gap: 8px;
}

.risk-level-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.risk-level-dot.HIGH,
.risk-level-dot.CRITICAL {
  background: var(--app-danger);
}

.risk-level-dot.MEDIUM {
  background: var(--app-warning);
}

.risk-level-dot.LOW {
  background: var(--app-success);
}

.risk-type strong {
  color: var(--app-text);
  font-size: 14px;
  font-weight: 600;
}

.risk-evidence {
  margin: 10px 0 0;
  color: var(--app-text);
  font-size: 13px;
  line-height: 1.65;
}

.risk-suggestion {
  margin: 6px 0 0;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.risk-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
}

.risk-meta span {
  color: var(--app-text-subtle);
  font-size: 12px;
}

.risk-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--app-border);
}
</style>
