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
    <div class="section-title">
      <h3>维度化辅助评分</h3>
      <span>评委可在 AI 建议基础上手动调整总分和最终意见</span>
    </div>
    <div v-if="scoreItems.length" class="score-list">
      <article v-for="item in scoreItems" :key="item.code" class="score-card">
        <div class="score-head">
          <strong>{{ item.name }}</strong>
          <span>{{ item.score }} / {{ item.maxScore }}</span>
        </div>
        <el-slider
          :model-value="Number(item.score)"
          :min="0"
          :max="Number(item.maxScore || 100)"
          :disabled="assignmentSubmitted"
          @input="$emit('update-score', item.code, Array.isArray($event) ? $event[0] : $event)"
        />
        <p>{{ item.reason }}</p>
      </article>
    </div>
    <el-empty v-else description="生成辅助评审后展示评分建议" />

    <div class="manual-form">
      <el-input-number v-model="reportForm.totalScore" :min="0" :max="100" controls-position="right" :disabled="assignmentSubmitted" />
      <el-input
        v-model="reportForm.finalRecommendation"
        type="textarea"
        :rows="3"
        :disabled="assignmentSubmitted"
        placeholder="填写或调整最终评审意见"
      />
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

.score-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.score-card {
  border: 1px solid #dde3ee;
  border-radius: 10px;
  padding: 16px;
  background: #fff;
  box-shadow: none;
}

.score-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.score-head strong {
  color: #101828;
}

.score-head span {
  color: #155eef;
  font-weight: 850;
}

.score-card p {
  color: #475467;
  line-height: 1.7;
}

.manual-form {
  display: grid;
  grid-template-columns: 140px minmax(0, 1fr) auto;
  gap: 12px;
  margin-top: 16px;
  align-items: start;
}

.manual-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

@media (max-width: 1180px) {
  .score-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .manual-form {
    grid-template-columns: 1fr;
  }

  .manual-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 720px) {
  .score-list {
    grid-template-columns: 1fr;
  }

  .section-title {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
