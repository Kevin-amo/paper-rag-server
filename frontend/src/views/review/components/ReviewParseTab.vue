<script setup lang="ts">
import { formatDate, textValue } from '../../../utils/format';
import type { PaperStructuredContent, PaperStructuredParse, ReviewTask } from '../../../types';

defineProps<{
  structuredParse: PaperStructuredParse | null;
  structuredContent: Partial<PaperStructuredContent>;
  selectedTask: ReviewTask | null;
  missingFields: string[];
  lowConfidenceFields: string[];
  assignmentSubmitted: boolean;
  structuredParseLoading: boolean;
  regeneratingStructuredParse: boolean;
}>();

defineEmits<{
  rerunStructuredParse: [];
}>();
</script>

<template>
  <section class="detail-section" v-loading="structuredParseLoading">
    <div class="section-header">
      <div class="section-header-left">
        <h3>结构化理解与内容解析</h3>
        <p>独立于 AI 评审报告的论文结构化解析结果</p>
      </div>
      <div class="section-actions">
        <el-tag :type="structuredParse?.status === 'FAILED' ? 'danger' : structuredParse ? 'success' : 'info'" size="small" effect="plain">
          {{ structuredParse?.status || '未生成' }}
        </el-tag>
        <el-button size="small" :disabled="assignmentSubmitted" :loading="regeneratingStructuredParse" @click="$emit('rerunStructuredParse')">
          生成解析
        </el-button>
      </div>
    </div>
    <div v-if="structuredParse?.errorMessage" class="parse-alert">
      {{ structuredParse.errorMessage }}
    </div>
    <div class="parse-tags">
      <el-tag v-if="structuredParse?.updatedAt" type="info" size="small" effect="plain">
        更新时间：{{ formatDate(structuredParse.updatedAt) }}
      </el-tag>
      <el-tag v-for="field in missingFields" :key="`missing-${field}`" type="warning" size="small" effect="plain">
        缺失：{{ field }}
      </el-tag>
      <el-tag v-for="field in lowConfidenceFields" :key="`low-${field}`" type="danger" size="small" effect="plain">
        低置信：{{ field }}
      </el-tag>
    </div>
    <div class="section-grid">
      <article>
        <span>标题</span>
        <strong>{{ textValue(structuredContent.title, selectedTask?.document?.title || '暂未识别') }}</strong>
      </article>
      <article>
        <span>关键词</span>
        <strong>{{ textValue(structuredContent.keywords || selectedTask?.document?.keywords) }}</strong>
      </article>
      <article>
        <span>研究对象</span>
        <strong>{{ textValue(structuredContent.researchObject) }}</strong>
      </article>
      <article>
        <span>研究问题</span>
        <strong>{{ textValue(structuredContent.researchQuestion) }}</strong>
      </article>
      <article>
        <span>方法路径</span>
        <strong>{{ textValue(structuredContent.methodPath) }}</strong>
      </article>
      <article>
        <span>创新点</span>
        <strong>{{ textValue(structuredContent.innovationPoints) }}</strong>
      </article>
    </div>
    <div class="paper-sections">
      <el-collapse>
        <el-collapse-item title="摘要" name="abstract">
          <p>{{ textValue(structuredContent.abstract, selectedTask?.document?.abstractText || '暂无摘要') }}</p>
        </el-collapse-item>
        <el-collapse-item title="引言" name="introduction">
          <p>{{ textValue(structuredContent.introduction) }}</p>
        </el-collapse-item>
        <el-collapse-item title="文献综述 / 相关研究" name="literatureReview">
          <p>{{ textValue(structuredContent.literatureReview) }}</p>
        </el-collapse-item>
        <el-collapse-item title="研究方法" name="methodology">
          <p>{{ textValue(structuredContent.methodology) }}</p>
        </el-collapse-item>
        <el-collapse-item title="实验与结果" name="experimentResults">
          <p>{{ textValue(structuredContent.experimentResults) }}</p>
        </el-collapse-item>
        <el-collapse-item title="讨论" name="discussion">
          <p>{{ textValue(structuredContent.discussion) }}</p>
        </el-collapse-item>
        <el-collapse-item title="结论" name="conclusion">
          <p>{{ textValue(structuredContent.conclusion) }}</p>
        </el-collapse-item>
        <el-collapse-item title="实验数据摘要" name="experimentDataSummary">
          <p>{{ textValue(structuredContent.experimentDataSummary) }}</p>
        </el-collapse-item>
        <el-collapse-item title="主要结论" name="mainConclusions">
          <p>{{ textValue(structuredContent.mainConclusions) }}</p>
        </el-collapse-item>
        <el-collapse-item title="参考文献" name="references">
          <p>{{ textValue(structuredContent.references) }}</p>
        </el-collapse-item>
      </el-collapse>
    </div>
  </section>
</template>

<style scoped>
.detail-section {
  margin-top: 20px;
}

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
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

.section-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.parse-tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}

.parse-alert {
  margin-top: 12px;
  border: 1px solid var(--app-danger-soft);
  border-radius: var(--app-radius-sm);
  padding: 10px 12px;
  background: var(--app-danger-soft);
  color: var(--app-danger);
  font-size: 13px;
  line-height: 1.6;
}

.section-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.section-grid article {
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  padding: 12px;
  background: var(--app-surface);
}

.section-grid span {
  display: block;
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 500;
}

.section-grid strong {
  display: block;
  margin-top: 4px;
  color: var(--app-text);
  font-size: 13px;
  line-height: 1.5;
}

.paper-sections {
  margin-top: 14px;
}

.paper-sections p {
  color: var(--app-text-muted);
  line-height: 1.7;
}

@media (max-width: 1180px) {
  .section-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .section-grid {
    grid-template-columns: 1fr;
  }

  .section-header {
    flex-direction: column;
    gap: 8px;
  }
}
</style>
