<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuth } from '../../composables/useAuth';

type AdminSection = 'users' | 'tasks' | 'assignments' | 'criteria' | 'archive';

const props = defineProps<{
  active: AdminSection;
  loading?: boolean;
  title?: string;
}>();

const router = useRouter();
const route = useRoute();
const auth = useAuth();

const navItems: Array<{
  key: AdminSection;
  title: string;
  description: string;
  path: string;
  query?: Record<string, string>;
}> = [
  {
    key: 'users',
    title: '用户管理',
    description: '账号、角色、状态',
    path: '/admin',
  },
  {
    key: 'tasks',
    title: '评审任务',
    description: '任务列表与筛选',
    path: '/admin/reviews',
    query: { tab: 'tasks' },
  },
  {
    key: 'assignments',
    title: '评审员分配',
    description: '负载与分配入口',
    path: '/admin/reviews',
    query: { tab: 'assignments' },
  },
  {
    key: 'criteria',
    title: '评审指标',
    description: '评分标准维护',
    path: '/admin/reviews',
    query: { tab: 'criteria' },
  },
  {
    key: 'archive',
    title: '共识/归档',
    description: '共识确认与留档',
    path: '/admin/reviews',
    query: { tab: 'archive' },
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
</script>

<template>
  <div class="admin-layout" v-loading="props.loading">
    <aside class="admin-sidebar" aria-label="管理后台导航">
      <div class="sidebar-brand">
        <span class="brand-mark">管</span>
        <div>
          <strong>管理后台</strong>
          <small>Paper RAG Server</small>
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
          <span>{{ item.title }}</span>
          <small>{{ item.description }}</small>
        </button>
      </nav>
    </aside>

    <div class="admin-main">
      <header class="admin-topbar">
        <div>
          <p>管理后台</p>
          <h1>{{ pageTitle }}</h1>
        </div>
        <div class="topbar-actions">
          <el-tag type="info" effect="plain">{{ currentUserName }} · 管理员</el-tag>
          <el-button @click="router.push('/review')">评审工作台</el-button>
          <el-button v-if="auth.hasRole('USER')" @click="router.push('/user')">用户端</el-button>
          <el-button @click="handleLogout">退出登录</el-button>
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
  grid-template-columns: 232px minmax(0, 1fr);
  background: #f3f4f6;
  color: #111827;
}

.admin-sidebar {
  position: sticky;
  top: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
  gap: 18px;
  border-right: 1px solid #d9dee8;
  background: #fff;
  padding: 20px 14px;
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 8px 18px;
  border-bottom: 1px solid #eef0f4;
}

.brand-mark {
  width: 34px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: #2563eb;
  color: #fff;
  font-weight: 800;
}

.sidebar-brand strong,
.sidebar-brand small {
  display: block;
}

.sidebar-brand strong {
  font-size: 18px;
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
  gap: 4px;
  border: 0;
  border-radius: 8px;
  padding: 11px 12px;
  background: transparent;
  color: #374151;
  cursor: pointer;
  text-align: left;
  transition: background 0.16s ease, color 0.16s ease;
}

.sidebar-menu-item:hover {
  background: #f3f6fb;
}

.sidebar-menu-item.active {
  background: #e8f0fe;
  color: #1d4ed8;
  font-weight: 700;
}

.sidebar-menu-item span {
  font-size: 14px;
}

.sidebar-menu-item small {
  color: #6b7280;
  font-size: 12px;
  font-weight: 400;
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
  justify-content: space-between;
  gap: 16px;
  min-height: 72px;
  border-bottom: 1px solid #d9dee8;
  background: #fff;
  padding: 14px 24px;
}

.admin-topbar p {
  margin: 0 0 3px;
  color: #6b7280;
  font-size: 12px;
}

.admin-topbar h1 {
  margin: 0;
  color: #111827;
  font-size: 22px;
  line-height: 1.2;
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
  padding: 20px 24px 32px;
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
    align-items: flex-start;
    flex-direction: column;
  }

  .topbar-actions {
    justify-content: flex-start;
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
