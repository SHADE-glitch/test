<script setup lang="ts">
const topics = ['Java', 'Redis', 'MySQL', 'JVM', '并发']
const levels = ['初级', '中级', '高级']
</script>

<template>
  <section class="py-10 text-center">
    <h1 class="text-3xl font-bold">AI 面试陪练 & 智能反馈平台</h1>
    <p class="mt-3 text-slate-500">选择方向，AI 面试官连续追问，最后生成能力报告</p>

    <div class="mt-10 rounded-xl bg-white p-6 shadow-sm">
      <h2 class="mb-4 text-left font-semibold">选择面试主题</h2>
      <div class="grid grid-cols-2 gap-3 sm:grid-cols-5">
        <button
          v-for="t in topics"
          :key="t"
          class="rounded-lg border border-slate-200 px-4 py-6 text-base font-medium hover:border-blue-400 hover:bg-blue-50"
        >
          {{ t }}
        </button>
      </div>
      <div class="mt-6 flex justify-center gap-3">
        <button
          v-for="l in levels"
          :key="l"
          class="rounded-full border border-slate-200 px-5 py-2 text-sm hover:border-blue-400 hover:bg-blue-50"
        >
          {{ l }}
        </button>
      </div>
      <button class="mt-8 rounded-lg bg-blue-600 px-8 py-3 text-white hover:bg-blue-700">
        开始面试
      </button>
    </div>
  </section>
</template>