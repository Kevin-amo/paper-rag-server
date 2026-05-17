<script setup lang="ts">
defineProps<{
  title: string;
  description?: string;
  compact?: boolean;
}>();
</script>

<template>
  <div class="empty-state" :class="{ compact }">
    <div class="empty-icon">⌁</div>
    <strong>{{ title }}</strong>
    <p v-if="description">{{ description }}</p>
    <slot />
  </div>
</template>

<style scoped>
.empty-state {
  display: grid;
  place-items: center;
  gap: 8px;
  min-height: 220px;
  padding: 28px;
  border: 1px dashed var(--app-border);
  border-radius: 18px;
  background: var(--app-surface-soft);
  text-align: center;
}

.empty-state.compact {
  min-height: 120px;
  padding: 18px;
}

.empty-icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 999px;
  color: var(--app-primary);
  background: var(--app-primary-soft);
  font-size: 26px;
  font-weight: 700;
}

strong {
  color: var(--app-text);
  font-size: 16px;
}

p {
  max-width: 460px;
  margin: 0;
  color: var(--app-text-muted);
  line-height: 1.7;
}
</style>