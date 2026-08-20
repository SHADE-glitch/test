<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { getInterview, getInterviewResult, streamAnswer, type MessageVO } from '../api/interview'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const sessionId = computed(() => Number(route.params.id))

const { data: session, refetch, isLoading } = useQuery({
  queryKey: ['interview', sessionId],
  queryFn: () => getInterview(sessionId.value),
})

const answer = ref('')
const submitting = ref(false)
const error = ref('')
const streaming = ref('')
const messagesEnd = ref<HTMLElement | null>(null)

const messages = computed(() => session.value?.messages ?? [])
const isFinished = computed(() => session.value?.status === 'FINISHED')
const levelLabel = computed(() => {
  const map: Record<string, string> = { JUNIOR: '初级', INTERMEDIATE: '中级', SENIOR: '高级' }
  return session.value ? (map[session.value.level] ?? session.value.level) : ''
})

const { data: result } = useQuery({
  queryKey: ['interviewResult', sessionId],
  queryFn: () => getInterviewResult(sessionId.value),
  enabled: isFinished,
  refetchInterval: 5000,
})

const dimensionMeta: Record<string, { label: string; color: string }> = {
  knowledge: { label: '知识掌握', color: 'bg-blue-500' },
  expression: { label: '表达清晰', color: 'bg-emerald-500' },
  source: { label: '实践来源', color: 'bg-amber-500' },
  analysis: { label: '问题分析', color: 'bg-purple-500' },
}

watch(messages, async () => {
  await new Promise((r) => setTimeout(r, 0))
  messagesEnd.value?.scrollIntoView({ behavior: 'smooth' })
})

async function submit() {
  const content = answer.value.trim()
  if (!content || submitting.value) return
  submitting.value = true
  error.value = ''
  answer.value = ''
  streaming.value = ''
  try {
    await streamAnswer(sessionId.value, content, {
      onToken: (token) => {
        streaming.value += token
        messagesEnd.value?.scrollIntoView({ behavior: 'smooth' })
      },
      onDone: async () => {
        streaming.value = ''
        await refetch()
      },
    })
  } catch (e) {
    answer.value = content
    error.value = e instanceof Error ? e.message : '提交失败，请重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="mx-auto flex h-[calc(100vh-4rem)] max-w-4xl flex-col py-4">
    <div class="flex items-center justify-between rounded-t-xl bg-white px-5 py-3 shadow-sm">
      <div>
        <h2 class="font-semibold">
          {{ session?.topic ?? '...' }}
          <span class="ml-2 text-sm font-normal text-slate-400">{{ levelLabel }}</span>
        </h2>
        <p class="text-xs text-slate-400">已提问 {{ session?.questionCount ?? 0 }} 题</p>
      </div>
      <div class="flex items-center gap-2">
        <button class="text-sm text-slate-400 hover:text-slate-600" @click="router.push('/')">
          返回首页
        </button>
        <button class="text-sm text-slate-400 hover:text-slate-600" @click="auth.logout()">
          退出
        </button>
      </div>
    </div>

    <div class="flex-1 space-y-4 overflow-y-auto bg-slate-50 p-5">
      <div v-if="isLoading" class="py-10 text-center text-slate-400">加载面试中...</div>

      <template v-else>
        <div
          v-if="isFinished"
          class="rounded-2xl border border-emerald-200 bg-white p-5 shadow-sm"
        >
          <p class="mb-4 font-semibold text-emerald-700">能力报告</p>
          <div v-if="!result" class="py-6 text-center text-sm text-slate-400">
            正在生成能力报告，AI 评分中...
          </div>
          <template v-else>
            <div class="mb-5 flex items-center justify-center gap-6">
              <div class="text-center">
                <p class="text-3xl font-bold text-emerald-600">{{ result.totalScore }}</p>
                <p class="text-xs text-slate-400">总分 / 40</p>
              </div>
              <div class="flex-1 space-y-2">
                <div v-for="(meta, key) in dimensionMeta" :key="key">
                  <div class="mb-1 flex justify-between text-xs">
                    <span>{{ meta.label }}</span>
                    <span class="text-slate-500">{{ result.dimensions[key]?.score ?? 0 }} / 10</span>
                  </div>
                  <div class="h-2 overflow-hidden rounded-full bg-slate-100">
                    <div
                      class="h-full rounded-full transition-all duration-500"
                      :class="meta.color"
                      :style="{ width: `${(result.dimensions[key]?.score ?? 0) * 10}%` }"
                    />
                  </div>
                  <p class="mt-0.5 text-xs text-slate-400">{{ result.dimensions[key]?.comment }}</p>
                </div>
              </div>
            </div>
            <div class="grid gap-4 sm:grid-cols-2">
              <div class="rounded-xl bg-red-50 p-4">
                <p class="mb-2 text-sm font-semibold text-red-600">薄弱点</p>
                <ul class="list-inside list-disc space-y-1 text-sm text-red-700">
                  <li v-for="(w, i) in result.weakPoints" :key="i">{{ w }}</li>
                </ul>
              </div>
              <div class="rounded-xl bg-blue-50 p-4">
                <p class="mb-2 text-sm font-semibold text-blue-600">提升建议</p>
                <ul class="list-inside list-disc space-y-1 text-sm text-blue-700">
                  <li v-for="(s, i) in result.suggestions" :key="i">{{ s }}</li>
                </ul>
              </div>
            </div>
          </template>
        </div>

        <div
          v-for="m in messages"
          :key="m.id"
          class="flex"
          :class="m.role === 'USER' ? 'justify-end' : 'justify-start'"
        >
          <div
            class="max-w-[75%] whitespace-pre-wrap rounded-2xl px-4 py-3 text-sm leading-relaxed"
            :class="
              m.role === 'USER'
                ? 'rounded-br-sm bg-blue-600 text-white'
                : m.kind === 'feedback'
                  ? 'rounded-bl-sm border border-emerald-200 bg-emerald-50'
                  : 'rounded-bl-sm border border-slate-200 bg-white'
            "
          >
            <p v-if="m.kind === 'feedback'" class="mb-1 text-xs font-semibold text-emerald-600">
              面试总结
            </p>
            {{ m.content }}
          </div>
        </div>

        <div v-if="submitting && !streaming" class="flex justify-start">
          <div class="rounded-2xl rounded-bl-sm border border-slate-200 bg-white px-4 py-3 text-sm text-slate-400">
            AI 思考中...
          </div>
        </div>
        <div v-if="streaming" class="flex justify-start">
          <div class="max-w-[75%] whitespace-pre-wrap rounded-2xl rounded-bl-sm border border-slate-200 bg-white px-4 py-3 text-sm leading-relaxed">
            {{ streaming }}<span class="ml-0.5 inline-block h-4 w-1.5 animate-pulse bg-blue-500 align-middle" />
          </div>
        </div>
        <div ref="messagesEnd" />
      </template>
    </div>

    <div class="rounded-b-xl border-t bg-white p-3">
      <div
        v-if="isFinished"
        class="flex items-center justify-between rounded-lg bg-emerald-50 px-4 py-3"
      >
        <p class="text-sm font-medium text-emerald-700">面试已结束，AI 已给出整体反馈</p>
        <button
          class="rounded-lg bg-emerald-600 px-4 py-1.5 text-sm text-white transition hover:bg-emerald-700"
          @click="router.push('/')"
        >
          返回首页
        </button>
      </div>
      <template v-else>
        <p v-if="error" class="mb-2 text-center text-sm text-red-500">{{ error }}</p>
        <form class="flex gap-2" @submit.prevent="submit">
          <input
            v-model="answer"
            type="text"
            placeholder="输入你的回答，回车提交"
            class="flex-1 rounded-lg border border-slate-200 px-4 py-2.5 text-sm outline-none focus:border-blue-400"
            :disabled="submitting"
          />
          <button
            type="submit"
            class="rounded-lg bg-blue-600 px-6 py-2.5 text-sm text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
            :disabled="submitting || !answer.trim()"
          >
            {{ submitting ? '等待中' : '发送' }}
          </button>
        </form>
      </template>
    </div>
  </section>
</template>