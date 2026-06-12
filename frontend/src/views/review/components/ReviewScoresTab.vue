<script setup lang="ts">
import type { ReviewReport, ReviewReportStatus, ReviewScoreItem } from '../../../types';

defineProps<{
  scoreItems: ReviewScoreItem[];
  selectedReport: ReviewReport | null;
  assignmentSubmitted: boolean;
  saving: boolean;
  submittingAssignment: boolean;
  reportForm: { totalScore: number; finalRecommendation: string; status: ReviewReportStatus };
}>();

defineEmits<{
  'update-score': [code: string, score: number];
  'save-report': [];
  'submit-assignment': [];
}>();
</script>

<template>
  <section class="detail-section">
    <div class="section-header">
      <h3>维度化辅助评分</h3>
      <p>评委可在 AI 建议基础上手动调整总分和最终意见</p>
    </div>
    <div v-if="scoreItems.length" class="score-grid">
      <article v-for="item in scoreItems" :key="item.code" class="score-card">
        <div class="score-card-header">
          <strong class="score-name">{{ item.name }}</strong>
          <span class="score-value">{{ item.score }}<span class="score-max">/{{ item.maxScore }}</span></span>
        </div>
        <el-slider
          :model-value="Number(item.score)"
          :min="0"
          :max="Number(item.maxScore || 100)"
          :disabled="assignmentSubmitted"
          @input="$emit('update-score', item.code, Array.isArray($event) ? $event[0] : $event)"
        />
        <p class="score-reason">{{ item.reason }}</p>
      </article>
    </div>
    <el-empty v-else description="生成辅助评审后展示评分建议" />

    <div class="manual-section">
      <div class="manual-header">
        <h4>手动调整</h4>
      </div>
      <div class="manual-form">
        <div class="manual-score">
          <label>总分</label>
          <el-input-number v-model="reportForm.totalScore" :min="0" :max="100" controls-position="right" :disabled="assignmentSubmitted" />
        </div>
        <div class="manual-opinion">
          <label>最终评审意见</label>
          <el-input
            v-model="reportForm.finalRecommendation"
            type="textarea"
            :rows="3"
            :disabled="assignmentSubmitted"
            placeholder="填写或调整最终评审意见"
          />
        </div>
      </div>
      <div class="manual-actions">
        <el-button :disabled="assignmentSubmitted || !selectedReport" :loading="saving" @click="$emit('save-report')">
          保存调整
        </el-button>
        <el-popconfirm title="提交后个人评审将只读，是否继续？" @confirm="$emit('submit-assignment')">
          <template #reference>
            <el-button
              type="success"
              :disabled="assignmentSubmitted || submittingAssignment || !selectedReport"
              :loading="submittingAssignment"
            >
              提交评审
            </el-button>
          </template>
        </el-popconfirm>
      </div>
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

.score-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.score-card {
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  padding: 16px;
  background: var(--app-surface);
  transition: border-color 0.15s ease;
}

.score-card:hover {
  border-color: var(--app-border-strong);
}

.score-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.score-name {
  color: var(--app-text);
  font-size: 14px;
  font-weight: 600;
}

.score-value {
  color: var(--app-primary);
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.score-max {
  color: var(--app-text-subtle);
  font-size: 14px;
  font-weight: 500;
}

.score-reason {
  margin: 8px 0 0;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.manual-section {
  margin-top: 20px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  padding: 16px;
  background: var(--app-surface);
}

.manual-header {
  margin-bottom: 14px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--app-border);
}

.manual-header h4 {
  margin: 0;
  color: var(--app-text);
  font-size: 14px;
  font-weight: 700;
}

.manual-form {
  display: grid;
  grid-template-columns: 140px minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

.manual-score label,
.manual-opinion label {
  display: block;
  margin-bottom: 6px;
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.manual-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--app-border);
}

@media (max-width: 1180px) {
  .manual-form {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .score-grid {
    grid-template-columns: 1fr;
  }

  .section-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }
}
</style>
