<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { ApiError } from '../api/client'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function onSubmit() {
  error.value = ''
  loading.value = true
  try {
    await auth.login({ username: username.value, password: password.value })
    await router.push((route.query.redirect as string) || '/')
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '登录失败，请重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="mx-auto max-w-sm py-16">
    <form class="rounded-xl bg-white p-8 shadow-sm" @submit.prevent="onSubmit">
      <h1 class="mb-6 text-center text-xl font-bold">登录</h1>
      <input
        v-model="username"
        type="text"
        placeholder="用户名"
        class="mb-4 w-full rounded-lg border border-slate-200 px-3 py-2 outline-none focus:border-blue-400"
      />
      <input
        v-model="password"
        type="password"
        placeholder="密码"
        class="mb-4 w-full rounded-lg border border-slate-200 px-3 py-2 outline-none focus:border-blue-400"
      />
      <p v-if="error" class="mb-4 text-sm text-red-500">{{ error }}</p>
      <button
        type="submit"
        :disabled="loading"
        class="w-full rounded-lg bg-blue-600 py-2 text-white hover:bg-blue-700 disabled:opacity-50"
      >
        {{ loading ? '登录中...' : '登录' }}
      </button>
      <p class="mt-4 text-center text-sm text-slate-500">
        没有账号？<RouterLink to="/register" class="text-blue-600">注册</RouterLink>
      </p>
    </form>
  </div>
</template>