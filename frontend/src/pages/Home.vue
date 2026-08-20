<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { createInterview, listTopics, type TopicVO } from '../api/interview'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

const levels = [
  { value: 'JUNIOR', label: '初级' },
  { value: 'INTERMEDIATE', label: '中级' },
  { value: 'SENIOR', label: '高级' },
]

const { data: topics, isLoading } = useQuery({
  queryKey: ['topics'],
  queryFn: (): Promise<TopicVO[]> => listTopics(),
})

const selectedTopic = ref<string>('')
const selectedLevel = ref<string>('INTERMEDIATE')
const creating = ref(false)
const error = ref('')

const topicNames = computed(() => topics.value?.map((t) => t.topic) ?? [])

function pickTopic(name: string) {
  selectedTopic.value = selectedTopic.value === name ? '' : name
}

async function start() {
  if (!selectedTopic.value) {
    error.value = '请先选择一个面试主题'
    return
  }
  creating.value = true
  error.value = ''
  try {
    const session = await createInterview(selectedTopic.value, selectedLevel.value)
    router.push(`/interview/${session.id}`)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '创建面试失败，请稍后重试'
  } finally {
    creating.value = false
  }
}
</script>

<template>
  <section class="py-10">
    <div class="text-center">
      <h1 class="text-3xl font-bold">AI 面试陪练 & 智能反馈平台</h1>
      <p class="mt-3 text-slate-500">选择方向，AI 面试官连续追问，最后生成能力报告</p>
    </div>

    <div class="mx-auto mt-10 max-w-3xl rounded-xl bg-white p-6 shadow-sm">
      <div class="mb-2 flex items-center justify-between">
        <h2 class="font-semibold">选择面试主题</h2>
        <button class="text-sm text-slate-400 hover:text-slate-600" @click="auth.logout()">
          退出登录
        </button>
      </div>

      <div v-if="isLoading" class="py-8 text-center text-slate-400">加载主题中...</div>
      <div v-else class="grid grid-cols-2 gap-3 sm:grid-cols-5">
        <button
          v-for="t in topicNames"
          :key="t"
          class="rounded-lg border px-4 py-6 text-base font-medium transition hover:border-blue-400 hover:bg-blue-50"
          :class="selectedTopic === t ? 'border-blue-500 bg-blue-50 text-blue-600' : 'border-slate-200'"
          @click="pickTopic(t)"
        >
          {{ t }}
        </button>
      </div>

      <div class="mt-6 flex justify-center gap-3">
        <button
          v-for="l in levels"
          :key="l.value"
          class="rounded-full border px-5 py-2 text-sm transition hover:border-blue-400 hover:bg-blue-50"
          :class="selectedLevel === l.value ? 'border-blue-500 bg-blue-50 text-blue-600' : 'border-slate-200'"
          @click="selectedLevel = l.value"
        >
          {{ l.label }}
        </button>
      </div>

      <p v-if="error" class="mt-4 text-center text-sm text-red-500">{{ error }}</p>

      <div class="mt-8 text-center">
        <button
          class="rounded-lg bg-blue-600 px-8 py-3 text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
          :disabled="creating"
          @click="start"
        >
          {{ creating ? 'AI 面试官准备中...' : '开始面试' }}
        </button>
      </div>
    </div>
  </section>
</template>