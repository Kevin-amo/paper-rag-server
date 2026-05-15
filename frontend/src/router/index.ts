import { createRouter, createWebHistory } from 'vue-router';
import { authState } from '../composables/authState';

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
  if (!to.meta.public && !authState.accessToken) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }
  if (to.path === '/login' && authState.accessToken) {
    return { path: '/' };
  }

  const requiredRoles = to.meta.roles as string[] | undefined;
  if (requiredRoles?.length && !requiredRoles.some((role) => authState.user?.roles.includes(role))) {
    return { path: '/' };
  }

  return true;
});

export default router;