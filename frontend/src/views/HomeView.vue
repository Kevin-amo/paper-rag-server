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
  await router.replace(auth.isAdmin.value ? '/admin' : '/user');
});
</script>

<template>
  <div class="redirect-page" v-loading="true" />
</template>

<style scoped>
.redirect-page {
  min-height: 100vh;
  background: #f8fafc;
}
</style>