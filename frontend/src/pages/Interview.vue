<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { getInterview, streamAnswer, type MessageVO } from '../api/interview'
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
const levelLabel = computed(() => {
  const map: Record<string, string> = { JUNIOR: '初级', INTERMEDIATE: '中级', SENIOR: '高级' }
  return session.value ? (map[session.value.level] ?? session.value.level) : ''
})

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
                : 'rounded-bl-sm border border-slate-200 bg-white'
            "
          >
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
    </div>
  </section>
</template>