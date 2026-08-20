<script setup lang="ts">
import { useAuthStore } from './stores/auth'
import { useRouter } from 'vue-router'

const auth = useAuthStore()
const router = useRouter()

function onLogout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <div class="flex min-h-full flex-col">
    <header class="border-b border-slate-200 bg-white">
      <div class="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
        <RouterLink to="/" class="text-lg font-bold">🧠 AI 面试陪练</RouterLink>
        <nav class="flex items-center gap-4 text-sm">
          <template v-if="auth.isAuthed">
            <span class="text-slate-600">👤 {{ auth.user?.nickname || auth.user?.username }}</span>
            <button class="text-slate-400 hover:text-slate-700" @click="onLogout">退出</button>
          </template>
          <template v-else>
            <RouterLink to="/login" class="text-slate-600 hover:text-slate-900">登录</RouterLink>
            <RouterLink to="/register" class="text-slate-600 hover:text-slate-900">注册</RouterLink>
          </template>
        </nav>
      </div>
    </header>
    <main class="mx-auto w-full max-w-6xl flex-1 px-4 py-6">
      <RouterView />
    </main>
  </div>
</template>