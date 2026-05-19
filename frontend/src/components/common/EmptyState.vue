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
  padding: 32px;
  border: 1px solid rgba(37, 99, 235, 0.14);
  border-radius: 26px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(248, 251, 255, 0.92));
  box-shadow: 0 18px 44px rgba(15, 23, 42, 0.07);
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