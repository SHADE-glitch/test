import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: () => import('../pages/Home.vue') },
    { path: '/login', name: 'login', component: () => import('../pages/Login.vue') },
    { path: '/register', name: 'register', component: () => import('../pages/Register.vue') },
  ],
})

export default router