<script setup lang="ts">
import { onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useAuth } from '../composables/useAuth';

const router = useRouter();
const auth = useAuth();

onMounted(async () => {
  if (!auth.state.user) {
    try {
      await auth.hydrateCurrentUser();
    } catch {
      await router.replace('/login');
      return;
    }
  }
  if (auth.isAdmin.value) {
    await router.replace('/admin');
    return;
  }
  if (auth.isReviewer.value) {
    await router.replace('/review');
    return;
  }
  await router.replace('/user');
});
</script>

<template>
  <div class="redirect-page" v-loading="true">
    <div class="redirect-card">
      <strong>正在进入工作台</strong>
      <span>系统将根据你的角色自动跳转。</span>
    </div>
  </div>
</template>

<style scoped>
.redirect-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background: var(--app-bg);
}

.redirect-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 24px 28px;
  border: 1px solid var(--app-border);
  border-radius: 20px;
  background: #fff;
  box-shadow: var(--app-shadow);
  text-align: center;
}

.redirect-card span {
  color: var(--app-text-muted);
}
</style>