<script setup lang="ts">
import { computed, reactive, watch } from 'vue';
import { ElMessage } from 'element-plus';
import type { AdminReviewTaskDetail, AssignReviewersPayload, ReviewerLoad } from '../../../types';

const props = defineProps<{
  modelValue: boolean;
  task: AdminReviewTaskDetail | null;
  reviewerLoads: ReviewerLoad[];
}>();

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
  submit: [taskId: string, payload: AssignReviewersPayload];
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
});

const form = reactive({
  reviewerUserIds: [] as string[],
  leadReviewerUserId: '',
  dueAt: '' as string | null,
});

const assignmentInvalid = computed(
  () =>
    !form.reviewerUserIds.length ||
    !form.leadReviewerUserId ||
    !form.reviewerUserIds.includes(form.leadReviewerUserId),
);
const hasExistingAssignments = computed(() => Boolean(props.task?.assignments.length));

watch(
  () => props.task,
  (detail) => {
    form.reviewerUserIds = detail?.assignments.map((assignment) => assignment.reviewerUserId) ?? [];
    form.leadReviewerUserId = detail?.consensus?.leadReviewerUserId || detail?.task.reviewerUserId || form.reviewerUserIds[0] || '';
    form.dueAt = detail?.task.dueAt ?? null;
  },
  { immediate: true },
);

watch(
  () => form.reviewerUserIds.slice(),
  (ids) => {
    if (form.leadReviewerUserId && !ids.includes(form.leadReviewerUserId)) {
      form.leadReviewerUserId = '';
    }
  },
);

function reviewerLabel(load: ReviewerLoad) {
  const name = load.displayName || load.username || load.reviewerUserId;
  return `${name}（待评 ${load.assignedCount} / 评审中 ${load.reviewingCount}）`;
}

function handleSubmit() {
  if (!props.task) {
    return;
  }

  if (!form.reviewerUserIds.length || !form.leadReviewerUserId) {
    ElMessage.warning('请选择评审人并指定组长');
    return;
  }

  if (!form.reviewerUserIds.includes(form.leadReviewerUserId)) {
    ElMessage.warning('组长必须在评审人列表中');
    return;
  }

  emit('submit', props.task.task.id, {
    reviewerUserIds: form.reviewerUserIds,
    leadReviewerUserId: form.leadReviewerUserId,
    dueAt: form.dueAt || null,
  });
}
</script>

<template>
  <el-drawer v-model="visible" size="min(560px, 94vw)" destroy-on-close>
    <template #header>
      <div>
        <span class="eyebrow">Admin Override</span>
        <h3>异常兜底处理</h3>
      </div>
    </template>

    <el-empty v-if="!task" description="请选择评审任务" />
    <el-form v-else label-position="top" class="assignment-form">
      <el-alert :title="task.task.title" type="info" :closable="false" />
      <el-alert
        v-if="hasExistingAssignments"
        title="该任务已存在有效分配；当前版本仅保留未分配任务的 admin 兜底分配，已分配任务的取消/改派将在异常处理阶段接入。"
        type="warning"
        :closable="false"
      />
      <el-form-item label="评审人" required>
        <el-select v-model="form.reviewerUserIds" multiple filterable collapse-tags collapse-tags-tooltip class="full-width" :disabled="hasExistingAssignments">
          <el-option
            v-for="load in reviewerLoads"
            :key="load.reviewerUserId"
            :label="reviewerLabel(load)"
            :value="load.reviewerUserId"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="负责人" required>
        <el-select v-model="form.leadReviewerUserId" filterable class="full-width" :disabled="hasExistingAssignments">
          <el-option
            v-for="load in reviewerLoads.filter((item) => form.reviewerUserIds.includes(item.reviewerUserId))"
            :key="load.reviewerUserId"
            :label="load.displayName || load.username || load.reviewerUserId"
            :value="load.reviewerUserId"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="截止时间">
        <el-date-picker v-model="form.dueAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" class="full-width" :disabled="hasExistingAssignments" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="!task || hasExistingAssignments || assignmentInvalid" @click="handleSubmit">保存兜底分配</el-button>
    </template>
  </el-drawer>
</template>

<style scoped>
.eyebrow {
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

h3 {
  margin: 4px 0 0;
}

.assignment-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.full-width {
  width: 100%;
}
</style>
