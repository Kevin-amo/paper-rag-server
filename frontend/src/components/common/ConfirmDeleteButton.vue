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
    @confirm="emit('confirm')"
  >
    <template #reference>
      <el-button text type="danger" :loading="loading" :disabled="disabled" @click.stop>
        <slot>删除</slot>
      </el-button>
    </template>
  </el-popconfirm>
</template>