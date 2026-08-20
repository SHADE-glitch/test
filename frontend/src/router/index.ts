import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: () => import('../pages/Home.vue'), meta: { requiresAuth: true } },
    { path: '/interview/:id', name: 'interview', component: () => import('../pages/Interview.vue'), meta: { requiresAuth: true } },
    { path: '/knowledge', name: 'knowledge', component: () => import('../pages/KnowledgeMap.vue'), meta: { requiresAuth: true } },
    { path: '/dashboard', name: 'dashboard', component: () => import('../pages/Dashboard.vue'), meta: { requiresAuth: true } },
    { path: '/login', name: 'login', component: () => import('../pages/Login.vue') },
    { path: '/register', name: 'register', component: () => import('../pages/Register.vue') },
  ],
})

router.beforeEach((to) => {
  const token = localStorage.getItem('aiview_access_token')
  if (to.meta.requiresAuth && !token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if ((to.name === 'login' || to.name === 'register') && token) {
    return { name: 'home' }
  }
})

export default router