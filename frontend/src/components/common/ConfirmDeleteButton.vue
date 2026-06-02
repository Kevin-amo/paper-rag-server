<script setup lang="ts">
withDefaults(
  defineProps<{
    title?: string;
    confirmText?: string;
    cancelText?: string;
    loading?: boolean;
    disabled?: boolean;
  }>(),
  {
    title: '确认删除吗？此操作不可恢复。',
    confirmText: '删除',
    cancelText: '取消',
    loading: false,
    disabled: false,
  },
);

const emit = defineEmits<{
  confirm: [];
}>();
</script>

<template>
  <el-popconfirm
    :title="title"
    :confirm-button-text="confirmText"
    :cancel-button-text="cancelText"
    confirm-button-type="danger"
    popper-class="confirm-delete-popper"
    @confirm="emit('confirm')"
  >
    <template #reference>
      <el-button class="confirm-delete-trigger" text type="danger" :loading="loading" :disabled="disabled" @click.stop>
        <slot>删除</slot>
      </el-button>
    </template>
  </el-popconfirm>
</template>

<style scoped>
.confirm-delete-trigger {
  --el-button-text-color: var(--app-danger);
  --el-button-hover-text-color: #d70015;
  --el-button-hover-bg-color: rgba(255, 59, 48, 0.1);
  --el-button-hover-border-color: transparent;
  --el-button-active-text-color: #b00020;
  transform-origin: center;
  transition: color 0.16s ease, background-color 0.16s ease, transform 0.16s ease;
}

.confirm-delete-trigger:hover {
  transform: scale(1.06);
}

:global(.confirm-delete-popper .el-button--danger) {
  --el-button-text-color: #ffffff;
  --el-button-bg-color: var(--app-danger);
  --el-button-border-color: var(--app-danger);
  --el-button-hover-text-color: #ffffff;
  --el-button-hover-bg-color: #ff6259;
  --el-button-hover-border-color: #ff6259;
  --el-button-active-text-color: #ffffff;
  --el-button-active-bg-color: #d70015;
  --el-button-active-border-color: #d70015;
  transform-origin: center;
  transition: background-color 0.16s ease, border-color 0.16s ease, transform 0.16s ease;
}

:global(.confirm-delete-popper .el-button--danger:hover) {
  transform: scale(1.06);
}
</style>