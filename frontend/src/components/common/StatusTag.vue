<script setup lang="ts">
import { computed } from 'vue';
import type { DocumentStatus, UserStatus } from '../../types';

const props = defineProps<{
  status: DocumentStatus | UserStatus | string;
}>();

const tagType = computed(() => {
  switch (props.status?.toUpperCase()) {
    case 'ACTIVE':
    case 'INDEXED':
    case 'READY':
      return 'success';
    case 'FAILED':
    case 'DISABLED':
      return props.status?.toUpperCase() === 'FAILED' ? 'danger' : 'info';
    case 'PROCESSING':
    case 'PENDING':
      return 'warning';
    default:
      return 'info';
  }
});

const label = computed(() => {
  switch (props.status?.toUpperCase()) {
    case 'ACTIVE':
      return '启用';
    case 'DISABLED':
      return '禁用';
    case 'INDEXED':
    case 'READY':
      return '已就绪';
    case 'FAILED':
      return '失败';
    case 'PROCESSING':
      return '处理中';
    case 'PENDING':
    case 'QUEUED':
      return '排队中';
    default:
      return props.status || '-';
  }
});
</script>

<template>
  <el-tag :type="tagType" effect="light">{{ label }}</el-tag>
</template>