<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const username = ref('')
const nickname = ref('')
const password = ref('')

async function onSubmit() {
  await router.push('/login')
}
</script>

<template>
  <div class="mx-auto max-w-sm py-16">
    <form class="rounded-xl bg-white p-8 shadow-sm" @submit.prevent="onSubmit">
      <h1 class="mb-6 text-center text-xl font-bold">注册</h1>
      <input
        v-model="username"
        type="text"
        placeholder="用户名"
        class="mb-4 w-full rounded-lg border border-slate-200 px-3 py-2"
      />
      <input
        v-model="nickname"
        type="text"
        placeholder="昵称"
        class="mb-4 w-full rounded-lg border border-slate-200 px-3 py-2"
      />
      <input
        v-model="password"
        type="password"
        placeholder="密码"
        class="mb-6 w-full rounded-lg border border-slate-200 px-3 py-2"
      />
      <button type="submit" class="w-full rounded-lg bg-blue-600 py-2 text-white hover:bg-blue-700">
        注册
      </button>
      <p class="mt-4 text-center text-sm text-slate-500">
        已有账号？<RouterLink to="/login" class="text-blue-600">登录</RouterLink>
      </p>
    </form>
  </div>
</template>