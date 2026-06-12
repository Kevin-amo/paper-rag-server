<script setup lang="ts">
defineProps<{
  title: string;
  description?: string;
  compact?: boolean;
}>();
</script>

<template>
  <div class="empty-state" :class="{ compact }">
    <div class="empty-icon">
      <svg width="32" height="32" viewBox="0 0 32 32" fill="none">
        <rect x="6" y="4" width="20" height="24" rx="3" stroke="currentColor" stroke-width="2"/>
        <path d="M11 11h10M11 15h7M11 19h8" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
      </svg>
    </div>
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
  min-height: 200px;
  padding: 32px;
  border: 1px dashed var(--app-border);
  border-radius: var(--app-radius-md);
  text-align: center;
}

.empty-state.compact {
  min-height: 100px;
  padding: 16px;
}

.empty-icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: var(--app-radius-sm);
  color: var(--app-text-subtle);
  background: var(--app-surface-soft);
}

strong {
  color: var(--app-text);
  font-size: 15px;
  font-weight: 600;
}

p {
  max-width: 360px;
  margin: 0;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.6;
}
</style>
