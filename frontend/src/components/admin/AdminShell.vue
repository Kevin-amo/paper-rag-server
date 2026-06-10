<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  Bell,
  DataAnalysis,
  Files,
  FolderChecked,
  Search,
  Setting,
  SwitchButton,
  User,
} from '@element-plus/icons-vue';
import { useAuth } from '../../composables/useAuth';
import { useReviewLeaderAccess } from '../../composables/useReviewLeaderAccess';

type AdminSection = 'users' | 'config' | 'tasks' | 'criteria' | 'archive';

const props = defineProps<{
  active: AdminSection;
  loading?: boolean;
  title?: string;
}>();

const router = useRouter();
const route = useRoute();
const auth = useAuth();
const { canAccessLeaderWorkspace, refreshLeaderWorkspaceAccess } = useReviewLeaderAccess();

const navItems: Array<{
  key: AdminSection;
  title: string;
  description: string;
  path: string;
  query?: Record<string, string>;
  icon: unknown;
}> = [
  {
    key: 'users',
    title: '用户管理',
    description: '账号、角色、状态',
    path: '/admin',
    icon: User,
  },
  {
    key: 'config',
    title: '批次与小组',
    description: '批次、组长、成员',
    path: '/admin/reviews',
    query: { tab: 'config' },
    icon: Setting,
  },
  {
    key: 'tasks',
    title: '全局进度',
    description: '任务、进度、兜底',
    path: '/admin/reviews',
    query: { tab: 'tasks' },
    icon: Files,
  },
  {
    key: 'criteria',
    title: '评审指标',
    description: '评分标准维护',
    path: '/admin/reviews',
    query: { tab: 'criteria' },
    icon: DataAnalysis,
  },
  {
    key: 'archive',
    title: '结果查看',
    description: '共识与归档',
    path: '/admin/reviews',
    query: { tab: 'archive' },
    icon: FolderChecked,
  },
];

const currentUserName = computed(() => auth.state.user?.displayName || auth.state.user?.username || '管理员');
const pageTitle = computed(() => props.title || navItems.find((item) => item.key === props.active)?.title || '管理后台');

function isCurrentItem(item: (typeof navItems)[number]) {
  if (route.path !== item.path) return false;
  if (!item.query?.tab) return props.active === item.key;
  return route.query.tab === item.query.tab || props.active === item.key;
}

async function navigate(item: (typeof navItems)[number]) {
  if (isCurrentItem(item)) return;
  await router.push({ path: item.path, query: item.query });
}

async function handleLogout() {
  await auth.logout();
  await router.replace('/login');
}

onMounted(() => {
  refreshLeaderWorkspaceAccess();
});
</script>

<template>
  <div class="admin-layout" v-loading="props.loading">
    <aside class="admin-sidebar" aria-label="管理后台导航">
      <div class="sidebar-brand">
        <span class="brand-mark">PR</span>
        <div>
          <strong>Paper Review</strong>
          <small>Research Operations</small>
        </div>
      </div>

      <nav class="sidebar-menu">
        <button
          v-for="item in navItems"
          :key="item.key"
          class="sidebar-menu-item"
          :class="{ active: props.active === item.key }"
          type="button"
          :aria-current="props.active === item.key ? 'page' : undefined"
          @click="navigate(item)"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
          <small>{{ item.description }}</small>
        </button>
      </nav>
    </aside>

    <div class="admin-main">
      <header class="admin-topbar">
        <div class="topbar-title">
          <p>Research Operations Console</p>
          <h1>{{ pageTitle }}</h1>
        </div>
        <div class="topbar-search">
          <el-input :prefix-icon="Search" placeholder="搜索论文、评审任务、用户..." />
        </div>
        <div class="topbar-actions">
          <el-button circle :icon="Bell" aria-label="通知" />
          <el-tag type="info" effect="plain">{{ currentUserName }} · 管理员</el-tag>
          <el-button v-if="canAccessLeaderWorkspace" @click="router.push('/review-leader')">组长工作台</el-button>
          <el-button @click="router.push('/review')">评审工作台</el-button>
          <el-button v-if="auth.hasRole('USER')" @click="router.push('/user')">用户端</el-button>
          <el-button :icon="SwitchButton" @click="handleLogout">退出登录</el-button>
        </div>
      </header>

      <main class="admin-content">
        <slot />
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-layout {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  max-width: 100%;
  background: #f6f8fb;
  color: #101828;
  overflow-x: hidden;
}

.admin-sidebar {
  position: sticky;
  top: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
  gap: 18px;
  border-right: 1px solid #dde3ee;
  background: linear-gradient(180deg, #ffffff 0%, #f9fbff 100%);
  padding: 18px 14px;
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 10px 18px;
  border-bottom: 1px solid #edf1f7;
}

.brand-mark {
  width: 38px;
  height: 38px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  color: #fff;
  font-size: 13px;
  font-weight: 850;
  letter-spacing: 0.02em;
  box-shadow: 0 12px 24px rgba(37, 99, 235, 0.22);
}

.sidebar-brand strong,
.sidebar-brand small {
  display: block;
}

.sidebar-brand strong {
  color: #0f172a;
  font-size: 17px;
  line-height: 1.2;
}

.sidebar-brand small {
  margin-top: 3px;
  color: #6b7280;
  font-size: 12px;
}

.sidebar-menu {
  display: grid;
  gap: 4px;
}

.sidebar-menu-item {
  width: 100%;
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr);
  gap: 3px 10px;
  align-items: center;
  border: 0;
  border-radius: 10px;
  padding: 11px 12px;
  background: transparent;
  color: #344054;
  cursor: pointer;
  text-align: left;
  transition: background 0.16s ease, color 0.16s ease;
}

.sidebar-menu-item:hover {
  background: #f1f6ff;
}

.sidebar-menu-item.active {
  background: #eaf2ff;
  color: #155eef;
  font-weight: 700;
}

.sidebar-menu-item .el-icon {
  grid-row: span 2;
  color: inherit;
  font-size: 18px;
}

.sidebar-menu-item span {
  font-size: 14px;
}

.sidebar-menu-item small {
  color: #667085;
  font-size: 12px;
  font-weight: 400;
}

.admin-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow-x: hidden;
}

.admin-topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: grid;
  grid-template-columns: minmax(180px, 280px) minmax(240px, 560px) auto;
  align-items: center;
  gap: 16px;
  min-height: 70px;
  border-bottom: 1px solid #dde3ee;
  background: rgba(255, 255, 255, 0.94);
  padding: 12px 24px;
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
}

.topbar-title {
  min-width: 0;
}

.admin-topbar p {
  margin: 0 0 3px;
  color: #667085;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.admin-topbar h1 {
  margin: 0;
  color: #101828;
  font-size: 20px;
  line-height: 1.2;
}

.topbar-search :deep([class~="el-input__wrapper"]) {
  min-height: 40px;
  border-radius: 10px;
  box-shadow: 0 0 0 1px #d0d7e2 inset;
}

.admin-layout :deep([class~="el-button"]) {
  border-radius: 8px;
  font-weight: 700;
}

.admin-layout :deep([class~="el-button--primary"]) {
  box-shadow: 0 10px 22px rgba(37, 99, 235, 0.18);
}

.admin-layout :deep([class~="el-tag"]) {
  border-radius: 7px;
  font-weight: 700;
}

.admin-layout :deep([class~="el-table__header-wrapper"] th) {
  background: #f8fafc;
  color: #475467;
  font-size: 12px;
  font-weight: 750;
}

.admin-layout :deep([class~="el-table__row"]:hover > td) {
  background: #f5f8ff;
}

.topbar-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.admin-content {
  display: grid;
  gap: 16px;
  min-width: 0;
  padding: 20px 24px 32px;
  overflow-x: auto;
}

.admin-content > :deep(*) {
  min-width: 0;
}

@media (max-width: 1120px) {
  .admin-topbar {
    grid-template-columns: 1fr;
    align-items: flex-start;
  }

  .topbar-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 860px) {
  .admin-layout {
    grid-template-columns: 1fr;
  }

  .admin-sidebar {
    position: static;
    height: auto;
    border-right: 0;
    border-bottom: 1px solid #d9dee8;
  }

  .sidebar-menu {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .admin-topbar {
    position: static;
  }
}

@media (max-width: 560px) {
  .sidebar-menu {
    grid-template-columns: 1fr;
  }

  .admin-content {
    padding: 14px;
  }
}
</style>
