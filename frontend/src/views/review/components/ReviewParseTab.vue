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
    <div class="section-title">
      <div>
        <h3>结构化理解与内容解析</h3>
        <span>独立于 AI 评审报告的论文结构化解析结果</span>
      </div>
      <div class="section-actions">
        <el-tag :type="structuredParse?.status === 'FAILED' ? 'danger' : structuredParse ? 'success' : 'info'" effect="plain">
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
      <el-tag v-if="structuredParse?.updatedAt" type="info" effect="plain">
        更新时间：{{ formatDate(structuredParse.updatedAt) }}
      </el-tag>
      <el-tag v-for="field in missingFields" :key="`missing-${field}`" type="warning" effect="plain">
        缺失：{{ field }}
      </el-tag>
      <el-tag v-for="field in lowConfidenceFields" :key="`low-${field}`" type="danger" effect="plain">
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

.section-actions,
.parse-tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.parse-tags {
  margin-top: 12px;
}

.parse-alert {
  margin-top: 12px;
  border: 1px solid rgba(239, 68, 68, 0.2);
  border-radius: 14px;
  padding: 10px 12px;
  background: rgba(239, 68, 68, 0.08);
  color: #b91c1c;
  line-height: 1.6;
}

.section-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.section-grid article {
  border: 1px solid #dde3ee;
  border-radius: 10px;
  padding: 16px;
  background: #fff;
  box-shadow: none;
}

.section-grid span {
  display: block;
  color: #667085;
  font-size: 12px;
}

.section-grid strong {
  display: block;
  margin-top: 8px;
  color: #101828;
  line-height: 1.6;
}

.paper-sections {
  margin-top: 14px;
}

.paper-sections p {
  color: #475467;
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

  .section-title {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
