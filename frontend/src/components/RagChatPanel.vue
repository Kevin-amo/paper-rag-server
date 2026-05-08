<script setup lang="ts">
import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import type { RagAnswer } from '../types';

const props = defineProps<{
  loading: boolean;
  answer: RagAnswer | null;
}>();

const emit = defineEmits<{
  submit: [payload: { question: string; topK?: number }];
}>();

const form = ref({
  question: '',
  topK: 3,
});

function handleSubmit() {
  if (!form.value.question.trim()) {
    ElMessage.warning('请输入问题');
    return;
  }

  emit('submit', {
    question: form.value.question.trim(),
    topK: form.value.topK || undefined,
  });
}
</script>

<template>
  <el-card shadow="never" class="rag-card">
    <template #header>
      <div class="rag-header">
        <div>
          <h2>RAG 问答</h2>
          <p>调用 `/rag/ask`，返回答案和引用片段。</p>
        </div>
        <el-button type="primary" :loading="props.loading" @click="handleSubmit">提问</el-button>
      </div>
    </template>

    <el-form label-position="top">
      <el-form-item label="问题">
        <el-input
          v-model="form.question"
          type="textarea"
          :rows="4"
          resize="none"
          placeholder="例如：这篇论文的核心观点是什么？"
        />
      </el-form-item>
      <el-form-item label="Top K">
        <el-input-number v-model="form.topK" :min="1" :max="10" />
      </el-form-item>
    </el-form>

    <div class="answer-block">
      <div class="block-title">答案</div>
      <el-skeleton :loading="props.loading" animated :rows="5">
        <el-empty v-if="!props.answer" description="尚未发起问答" />
        <div v-else class="answer-content">{{ props.answer.answer }}</div>
      </el-skeleton>
    </div>

    <div class="answer-block citations-block">
      <div class="block-title">引用</div>
      <el-empty v-if="!props.answer?.citations?.length" description="暂无引用" />
      <div v-else class="citation-list">
        <el-card v-for="citation in props.answer.citations" :key="citation.chunkId" shadow="hover" class="citation-card">
          <div class="citation-meta">
            <strong>{{ citation.title || citation.sourceId }}</strong>
            <el-tag size="small">score {{ citation.score.toFixed(4) }}</el-tag>
          </div>
          <p>{{ citation.excerpt }}</p>
          <small>{{ citation.sourceId }} · chunk #{{ citation.chunkIndex }}</small>
        </el-card>
      </div>
    </div>
  </el-card>
</template>

<style scoped>
.rag-card {
  border: none;
  border-radius: 20px;
}

.rag-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.rag-header h2 {
  margin: 0;
  font-size: 18px;
}

.rag-header p {
  margin: 6px 0 0;
  color: #6b7280;
}

.answer-block + .answer-block {
  margin-top: 20px;
}

.block-title {
  margin-bottom: 12px;
  font-weight: 600;
}

.answer-content {
  padding: 16px;
  border-radius: 16px;
  background: #f8fafc;
  color: #1f2937;
  white-space: pre-wrap;
  line-height: 1.7;
}

.citation-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.citation-card {
  border-radius: 18px;
}

.citation-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.citation-card p {
  margin: 12px 0 8px;
  color: #334155;
  white-space: pre-wrap;
}

.citation-card small {
  color: #64748b;
}
</style>