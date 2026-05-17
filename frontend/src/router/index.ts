import { createRouter, createWebHistory } from 'vue-router';
import { authState } from '../composables/authState';
import type { UserRole } from '../types';

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean;
    roles?: UserRole[];
  }
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      name: 'home',
      component: () => import('../views/HomeView.vue'),
    },
    {
      path: '/user',
      name: 'user-workspace',
      component: () => import('../views/UserWorkspaceView.vue'),
      meta: { roles: ['USER'] },
    },
    {
      path: '/admin',
      name: 'admin-users',
      component: () => import('../views/admin/AdminUsersView.vue'),
      meta: { roles: ['ADMIN'] },
    },
  ],
});

router.beforeEach((to) => {
  const isLoginPage = to.path === '/login';

  if (!to.meta.public && !authState.accessToken) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }

  if (isLoginPage && authState.accessToken) {
    return { path: '/' };
  }

  const requiredRoles = to.meta.roles;
  if (requiredRoles?.length && !requiredRoles.some((role) => authState.user?.roles.includes(role))) {
    return { path: '/' };
  }

  return true;
});

export default router;