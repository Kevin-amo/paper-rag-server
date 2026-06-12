<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  Bell,
  DataAnalysis,
  Files,
  Setting,
  SwitchButton,
  User,
} from '@element-plus/icons-vue';
import { useAuth } from '../../composables/useAuth';
import { useReviewLeaderAccess } from '../../composables/useReviewLeaderAccess';

type AdminSection = 'users' | 'config' | 'tasks' | 'criteria';

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
        <div class="brand-logo">
          <svg width="28" height="28" viewBox="0 0 28 28" fill="none">
            <rect width="28" height="28" rx="7" fill="#2563EB"/>
            <path d="M8 9h12M8 14h8M8 19h10" stroke="#fff" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </div>
        <div class="brand-text">
          <strong>PaperMind</strong>
          <span>管理控制台</span>
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
          <div class="menu-item-text">
            <span>{{ item.title }}</span>
            <small>{{ item.description }}</small>
          </div>
        </button>
      </nav>

      <div class="sidebar-footer">
        <div class="sidebar-user">
          <div class="user-avatar">{{ currentUserName.charAt(0) }}</div>
          <div class="user-info">
            <span class="user-name">{{ currentUserName }}</span>
            <span class="user-role">管理员</span>
          </div>
        </div>
      </div>
    </aside>

    <div class="admin-main">
      <header class="admin-topbar">
        <div class="topbar-left">
          <h1>{{ pageTitle }}</h1>
        </div>
        <div class="topbar-right">
          <el-button circle :icon="Bell" aria-label="通知" />
          <el-divider direction="vertical" />
          <el-button v-if="canAccessLeaderWorkspace" size="small" @click="router.push('/review-leader')">组长工作台</el-button>
          <el-button size="small" @click="router.push('/review')">评审工作台</el-button>
          <el-button v-if="auth.hasRole('USER')" size="small" @click="router.push('/user')">用户端</el-button>
          <el-divider direction="vertical" />
          <el-button :icon="SwitchButton" text @click="handleLogout">退出</el-button>
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
  grid-template-columns: 240px minmax(0, 1fr);
  background: var(--app-bg);
  color: var(--app-text);
}

.admin-sidebar {
  position: sticky;
  top: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--app-surface);
  border-right: 1px solid var(--app-border);
  overflow-y: auto;
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 16px;
  border-bottom: 1px solid var(--app-border);
}

.sidebar-brand:hover .brand-logo svg {
  transform: scale(1.05);
}

.brand-logo svg {
  transition: transform 0.2s ease;
}

.brand-logo {
  flex-shrink: 0;
}

.brand-text strong {
  display: block;
  color: var(--app-text);
  font-size: 15px;
  font-weight: 700;
  line-height: 1.2;
}

.brand-text span {
  display: block;
  color: var(--app-text-subtle);
  font-size: 11px;
  font-weight: 500;
}

.sidebar-menu {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 12px 8px;
}

.sidebar-menu-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  border: 0;
  border-radius: var(--app-radius-sm);
  padding: 10px 12px;
  background: transparent;
  color: var(--app-text-muted);
  cursor: pointer;
  text-align: left;
  transition: all 0.15s ease;
}

.sidebar-menu-item:hover {
  background: var(--app-surface-soft);
  color: var(--app-text);
}

.sidebar-menu-item.active {
  background: var(--app-primary-soft);
  color: var(--app-primary);
}

.sidebar-menu-item .el-icon {
  flex-shrink: 0;
  font-size: 18px;
}

.menu-item-text span {
  display: block;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.2;
}

.menu-item-text small {
  display: block;
  color: var(--app-text-subtle);
  font-size: 11px;
  font-weight: 400;
  margin-top: 2px;
}

.sidebar-menu-item.active .menu-item-text small {
  color: var(--app-primary);
  opacity: 0.7;
}

.sidebar-footer {
  padding: 12px 8px;
  border-top: 1px solid var(--app-border);
}

.sidebar-user {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: var(--app-radius-sm);
}

.user-avatar {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--app-radius-sm);
  background: var(--app-primary-soft);
  color: var(--app-primary);
  font-size: 13px;
  font-weight: 700;
  flex-shrink: 0;
}

.user-info {
  min-width: 0;
}

.user-name {
  display: block;
  color: var(--app-text);
  font-size: 13px;
  font-weight: 600;
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-role {
  display: block;
  color: var(--app-text-subtle);
  font-size: 11px;
}

.admin-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.admin-topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 16px;
  min-height: 56px;
  border-bottom: 1px solid var(--app-border);
  background: var(--app-surface);
  padding: 0 24px;
}

.topbar-left h1 {
  margin: 0;
  color: var(--app-text);
  font-size: 18px;
  font-weight: 700;
  white-space: nowrap;
}

.topbar-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.topbar-right :deep(.el-divider--vertical) {
  height: 20px;
  margin: 0 4px;
}

.admin-content {
  display: grid;
  gap: 20px;
  min-width: 0;
  padding: 24px;
  overflow-x: auto;
}

.admin-content > :deep(*) {
  min-width: 0;
}

@media (max-width: 1120px) {
  .admin-topbar {
    flex-wrap: wrap;
    padding: 12px 24px;
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
    border-bottom: 1px solid var(--app-border);
  }

  .sidebar-menu {
    flex-direction: row;
    overflow-x: auto;
    padding: 8px;
  }

  .sidebar-footer {
    display: none;
  }

  .admin-topbar {
    position: static;
  }
}

@media (max-width: 560px) {
  .sidebar-menu {
    flex-direction: column;
  }

  .admin-content {
    padding: 16px;
  }

  .topbar-right {
    flex-wrap: wrap;
  }
}
</style>
