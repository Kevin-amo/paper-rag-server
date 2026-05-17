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
}

.main-layout.dark {
  background:
    radial-gradient(circle at 80% 0, rgba(59, 130, 246, 0.16), transparent 28rem),
    #f8fafc;
}
</style>