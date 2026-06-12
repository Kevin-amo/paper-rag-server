<script setup lang="ts"></script>

<template>
  <main class="auth-layout">
    <slot />
  </main>
</template>

<style scoped>
.auth-layout {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: clamp(18px, 4vw, 40px);
  background: var(--app-bg);
}
</style>
