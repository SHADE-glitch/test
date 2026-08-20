<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const username = ref('')
const password = ref('')

async function onSubmit() {
  await router.push('/')
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
        class="mb-4 w-full rounded-lg border border-slate-200 px-3 py-2"
      />
      <input
        v-model="password"
        type="password"
        placeholder="密码"
        class="mb-6 w-full rounded-lg border border-slate-200 px-3 py-2"
      />
      <button type="submit" class="w-full rounded-lg bg-blue-600 py-2 text-white hover:bg-blue-700">
        登录
      </button>
      <p class="mt-4 text-center text-sm text-slate-500">
        没有账号？<RouterLink to="/register" class="text-blue-600">注册</RouterLink>
      </p>
    </form>
  </div>
</template>