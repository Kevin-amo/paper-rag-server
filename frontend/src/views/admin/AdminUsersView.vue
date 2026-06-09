<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import AdminShell from '../../components/admin/AdminShell.vue';
import AdminUsersPanel from '../../components/admin/AdminUsersPanel.vue';
import { getErrorMessage } from '../../api/http';
import { useAuth } from '../../composables/useAuth';

const auth = useAuth();
const loading = ref(true);

const currentUserName = computed(() => auth.state.user?.displayName || auth.state.user?.username || '管理员');

onMounted(async () => {
  try {
    await auth.hydrateCurrentUser();
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <AdminShell active="users" title="用户管理" :loading="loading">
    <section class="admin-entry">
      <div>
        <h2>用户管理</h2>
        <p>当前管理员：{{ currentUserName }}。在这里维护用户账号、角色、启用状态和密码重置。</p>
      </div>
    </section>

    <AdminUsersPanel />
  </AdminShell>
</template>

<style scoped>
.admin-entry {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 18px 20px;
  background: #fff;
}

h2 {
  margin: 0;
  color: #111827;
  font-size: 20px;
}

p {
  max-width: 760px;
  margin: 8px 0 0;
  color: #4b5563;
  line-height: 1.7;
}
</style>
