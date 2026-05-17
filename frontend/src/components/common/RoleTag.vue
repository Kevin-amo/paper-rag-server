<script setup lang="ts">
import { computed } from 'vue';
import type { UserRole } from '../../types';

const props = defineProps<{
  role: UserRole | string;
}>();

const tagType = computed(() => (props.role === 'ADMIN' ? 'danger' : 'primary'));
const label = computed(() => (props.role === 'ADMIN' ? '管理员' : '普通用户'));
</script>

<template>
  <el-tag :type="tagType" effect="plain">{{ label }}</el-tag>
</template>