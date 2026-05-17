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
  padding: clamp(18px, 4vw, 36px);
  background:
    radial-gradient(circle at top left, rgba(37, 99, 235, 0.18), transparent 34rem),
    radial-gradient(circle at bottom right, rgba(99, 102, 241, 0.14), transparent 28rem),
    #f8fafc;
}
</style>