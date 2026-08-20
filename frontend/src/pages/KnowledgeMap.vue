<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import * as echarts from 'echarts'
import { getKnowledgeMap, type KnowledgeMapVO } from '../api/knowledge'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const chartEl = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

const { data, isLoading } = useQuery({
  queryKey: ['knowledgeMap'],
  queryFn: getKnowledgeMap,
})

function masteryColor(mastery: number) {
  if (mastery >= 70) return '#10b981'
  if (mastery >= 40) return '#f59e0b'
  if (mastery > 0) return '#ef4444'
  return '#cbd5e1'
}

function render(map: KnowledgeMapVO) {
  if (!chartEl.value) return
  if (!chart) chart = echarts.init(chartEl.value)

  const nodes = map.nodes.map((n) => ({
    id: n.id,
    name: n.name,
    value: n.mastery,
    category: n.category,
    symbolSize: n.category === 'topic' ? 52 : 22 + n.mastery * 0.18,
    itemStyle: { color: masteryColor(n.mastery) },
    label: { show: true, fontSize: n.category === 'topic' ? 13 : 10, color: '#334155' },
  }))

  try {
    chart.setOption({
      tooltip: {
        formatter: (params: { data: { name: string; category: string; value: number } }) =>
          `${params.data.name}<br/>${params.data.category === 'topic' ? '主题' : '知识点'}<br/>掌握度: ${params.data.value}%`,
      },
      series: [
        {
          type: 'graph',
          layout: 'force',
          data: nodes,
          links: map.links.map((l) => ({ source: l.source, target: l.target })),
          roam: true,
          draggable: true,
          force: { repulsion: 220, edgeLength: 90, gravity: 0.08 },
          emphasis: { focus: 'adjacency' },
          lineStyle: { color: '#94a3b8', width: 1.2 },
        },
      ],
    })
  } catch (e) {
    console.error('knowledge chart render failed', e)
  }
  ;(window as unknown as { __chart?: echarts.ECharts }).__chart = chart
}

watch(
  data,
  (v) => {
    if (v) render(v)
  },
  { flush: 'post' },
)

onMounted(() => {
  window.addEventListener('resize', onResize)
  if (data.value) render(data.value)
})

function onResize() {
  chart?.resize()
}

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <section class="py-6">
    <div class="mb-4 flex items-center justify-between">
      <div>
        <h1 class="text-xl font-bold">知识图谱</h1>
        <p class="mt-1 text-sm text-slate-500">基于已完成面试的评分聚合出各知识点掌握度，拖拽节点可自由查看</p>
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

    <div
      v-if="isLoading"
      class="flex h-[70vh] items-center justify-center rounded-xl bg-white text-slate-400"
    >
      加载知识图谱中...
    </div>

    <div
      v-else-if="data && data.nodes.length"
      ref="chartEl"
      class="h-[70vh] rounded-xl bg-white shadow-sm"
    />

    <div v-else class="flex h-[70vh] items-center justify-center rounded-xl bg-white text-slate-400">
      暂无数据，先完成一场面试即可生成知识图谱
    </div>

    <div class="mt-3 flex justify-center gap-6 text-xs text-slate-500">
      <span class="flex items-center gap-1">
        <i class="inline-block h-3 w-3 rounded-full" style="background: #10b981" /> 掌握良好 (≥70%)
      </span>
      <span class="flex items-center gap-1">
        <i class="inline-block h-3 w-3 rounded-full" style="background: #f59e0b" /> 有待提高 (40-70%)
      </span>
      <span class="flex items-center gap-1">
        <i class="inline-block h-3 w-3 rounded-full" style="background: #ef4444" /> 薄弱 (&lt;40%)
      </span>
      <span class="flex items-center gap-1">
        <i class="inline-block h-3 w-3 rounded-full" style="background: #cbd5e1" /> 未学习
      </span>
    </div>
  </section>
</template>