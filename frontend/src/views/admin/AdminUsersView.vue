<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import MainLayout from '../../layouts/MainLayout.vue';
import PageHeader from '../../components/common/PageHeader.vue';
import AdminUsersPanel from '../../components/AdminUsersPanel.vue';
import { getErrorMessage } from '../../api/http';
import { useAuth } from '../../composables/useAuth';
import { ElMessage } from 'element-plus';

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
  <MainLayout variant="dark" v-loading="loading">
    <PageHeader
      eyebrow="Admin Console"
      title="系统用户管理"
      description="集中管理账号、角色、状态与密码重置，保持知识库访问权限清晰可控。"
    >
      <template #actions>
        <el-tag type="danger" size="large">{{ currentUserName }} · 管理员</el-tag>
        <el-button @click="router.push('/user')">用户工作台</el-button>
        <el-button @click="handleLogout">退出登录</el-button>
      </template>
    </PageHeader>

    <section class="admin-entry app-card">
      <div>
        <p class="entry-kicker">User Operations</p>
        <h2>用户 CRUD 工作台</h2>
        <p>查看用户列表，按关键词或状态筛选；支持新建、编辑、禁用、删除用户，并可快速重置密码。</p>
      </div>
      <el-button type="primary" size="large" @click="panelVisible = true">打开用户管理</el-button>
    </section>

    <AdminUsersPanel v-model="panelVisible" />
  </MainLayout>
</template>

<style scoped>
.admin-entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 30px;
}

.entry-kicker {
  margin: 0 0 8px;
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

h2 {
  margin: 0;
  color: var(--app-text);
  font-size: 24px;
}

p:last-child {
  max-width: 720px;
  margin: 10px 0 0;
  color: var(--app-text-muted);
  line-height: 1.7;
}

@media (max-width: 760px) {
  .admin-entry {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>