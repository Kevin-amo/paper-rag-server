<script setup lang="ts">
defineProps<{
  eyebrow?: string;
  title: string;
  description?: string;
}>();
</script>

<template>
  <header class="page-header">
    <div>
      <p v-if="eyebrow" class="page-eyebrow">{{ eyebrow }}</p>
      <h1>{{ title }}</h1>
      <p v-if="description" class="page-description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="page-actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  padding: 28px 32px;
  border-radius: var(--app-radius-lg);
  color: #fff;
  background: linear-gradient(135deg, var(--app-primary-dark) 0%, var(--app-primary) 48%, #60a5fa 100%);
  box-shadow: 0 20px 45px rgba(37, 99, 235, 0.2);
}

.page-eyebrow {
  margin: 0 0 8px;
  color: rgba(255, 255, 255, 0.78);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

h1 {
  margin: 0;
  font-size: clamp(26px, 3vw, 36px);
  line-height: 1.18;
}

.page-description {
  max-width: 760px;
  margin: 12px 0 0;
  color: rgba(255, 255, 255, 0.92);
  line-height: 1.7;
}

.page-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 860px) {
  .page-header {
    flex-direction: column;
    padding: 24px;
  }

  .page-actions {
    justify-content: flex-start;
  }
}
</style>