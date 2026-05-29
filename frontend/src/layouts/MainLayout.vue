<script setup lang="ts">
defineProps<{
  variant?: 'primary' | 'dark';
}>();
</script>

<template>
  <main class="main-layout" :class="variant">
    <slot />
  </main>
</template>

<style scoped>
.main-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: clamp(16px, 3vw, 28px);
  background:
    radial-gradient(circle at 14% 10%, rgba(0, 122, 255, 0.12), transparent 28rem),
    radial-gradient(circle at 86% 78%, rgba(175, 82, 222, 0.1), transparent 30rem),
    linear-gradient(135deg, #f5f5f7 0%, #fbfbfd 48%, #eef3ff 100%);
}

.main-layout.dark {
  background:
    radial-gradient(circle at 14% 10%, rgba(0, 122, 255, 0.12), transparent 28rem),
    radial-gradient(circle at 86% 78%, rgba(175, 82, 222, 0.1), transparent 30rem),
    linear-gradient(135deg, #f5f5f7 0%, #fbfbfd 48%, #eef3ff 100%);
}
</style>