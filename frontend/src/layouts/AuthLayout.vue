<script setup lang="ts"></script>

<template>
  <main class="auth-layout">
    <slot />
  </main>
</template>

<style scoped>
.auth-layout {
  position: relative;
  isolation: isolate;
  min-height: 100vh;
  display: grid;
  place-items: center;
  overflow: hidden;
  padding: clamp(18px, 4vw, 40px);
  background:
    radial-gradient(circle at 18% 12%, rgba(0, 122, 255, 0.13), transparent 28rem),
    radial-gradient(circle at 82% 82%, rgba(175, 82, 222, 0.12), transparent 30rem),
    linear-gradient(135deg, #f5f5f7 0%, #fbfbfd 48%, #eef3ff 100%);
}

.auth-layout::before,
.auth-layout::after {
  position: absolute;
  z-index: -1;
  border-radius: 999px;
  content: '';
  filter: blur(2px);
}

.auth-layout::before {
  width: 420px;
  height: 420px;
  top: -170px;
  left: -110px;
  background: rgba(255, 255, 255, 0.68);
}

.auth-layout::after {
  width: 520px;
  height: 520px;
  right: -180px;
  bottom: -210px;
  background: rgba(255, 255, 255, 0.56);
}
</style>