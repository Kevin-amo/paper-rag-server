<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import AdminUsersPanel from '../../components/AdminUsersPanel.vue';
import { getErrorMessage } from '../../api/http';
import { useAuth } from '../../composables/useAuth';

const router = useRouter();
const auth = useAuth();
const panelVisible = ref(true);
const loading = ref(true);

const currentUserName = computed(() => auth.state.user?.displayName || auth.state.user?.username || '管理员');

async function handleLogout() {
  await auth.logout();
  await router.replace('/login');
}

onMounted(async () => {
  try {
    await auth.hydrateCurrentUser();
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
    await router.replace('/login');
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div class="admin-page" v-loading="loading">
    <header class="admin-hero">
      <div>
        <p class="eyebrow">Admin Console</p>
        <h1>用户管理</h1>
        <p>管理员界面仅保留用户 CRUD、角色、状态和密码重置能力。</p>
      </div>
      <div class="admin-actions">
        <el-tag type="danger" size="large">{{ currentUserName }} · 管理员</el-tag>
        <el-button @click="panelVisible = true">打开用户管理</el-button>
        <el-button @click="handleLogout">退出登录</el-button>
      </div>
    </header>

    <section class="admin-placeholder">
      <h2>用户 CRUD 工作台</h2>
      <p>点击“打开用户管理”进入完整用户列表，可创建、编辑、禁用、删除用户并重置密码。</p>
      <el-button type="primary" size="large" @click="panelVisible = true">管理用户</el-button>
    </section>

    <AdminUsersPanel v-model="panelVisible" />
  </div>
</template>

<style scoped>
.admin-page {
  min-height: 100vh;
  padding: 28px;
  background: #f8fafc;
}

.admin-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  padding: 28px 32px;
  border-radius: 24px;
  background: linear-gradient(135deg, #111827, #1f2937);
  color: #fff;
}

.eyebrow {
  margin: 0 0 8px;
  font-size: 13px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.75);
}

.admin-hero h1 {
  margin: 0;
  font-size: 32px;
}

.admin-hero p {
  margin: 12px 0 0;
  color: rgba(255, 255, 255, 0.88);
}

.admin-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.admin-placeholder {
  margin-top: 24px;
  padding: 32px;
  border: 1px solid #e2e8f0;
  border-radius: 24px;
  background: #fff;
}

.admin-placeholder h2 {
  margin: 0 0 10px;
}

.admin-placeholder p {
  margin: 0 0 18px;
  color: #64748b;
}
</style>