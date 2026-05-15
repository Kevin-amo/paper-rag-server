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
  ],
});

router.beforeEach((to) => {
  if (!to.meta.public && !authState.accessToken) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }
  if (to.path === '/login' && authState.accessToken) {
    return { path: '/' };
  }
  return true;
});

export default router;