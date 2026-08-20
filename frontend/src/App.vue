<script setup lang="ts">
</script>

<template>
  <div class="flex min-h-full flex-col">
    <header class="border-b border-slate-200 bg-white">
      <div class="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
        <span class="text-lg font-bold">🧠 AI 面试陪练</span>
        <nav class="flex gap-4 text-sm">
          <RouterLink to="/" class="text-slate-600 hover:text-slate-900">首页</RouterLink>
          <RouterLink to="/login" class="text-slate-600 hover:text-slate-900">登录</RouterLink>
        </nav>
      </div>
    </header>
    <main class="mx-auto w-full max-w-6xl flex-1 px-4 py-6">
      <RouterView />
    </main>
  </div>
</template>