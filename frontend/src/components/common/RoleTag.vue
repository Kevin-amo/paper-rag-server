<script setup lang="ts">
import { computed } from 'vue';
import type { UserRole } from '../../types';

const props = defineProps<{
  role: UserRole | string;
}>();

const tagType = computed(() => {
  if (props.role === 'ADMIN') {
    return 'danger';
  }
  if (props.role === 'REVIEWER') {
    return 'warning';
  }
  return 'primary';
});
const label = computed(() => {
  if (props.role === 'ADMIN') {
    return '管理员';
  }
  if (props.role === 'REVIEWER') {
    return '评审员';
  }
  return '普通用户';
});
</script>

<template>
  <el-tag :type="tagType" effect="plain">{{ label }}</el-tag>
</template>