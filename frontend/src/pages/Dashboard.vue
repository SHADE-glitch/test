<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import * as echarts from 'echarts'
import { getDashboard, type DashboardVO } from '../api/dashboard'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const radarEl = ref<HTMLDivElement | null>(null)
const trendEl = ref<HTMLDivElement | null>(null)
let radarChart: echarts.ECharts | null = null
let trendChart: echarts.ECharts | null = null

const { data, isLoading } = useQuery({
  queryKey: ['dashboard'],
  queryFn: getDashboard,
})

function renderRadar(d: DashboardVO) {
  if (!radarEl.value) return
  if (!radarChart) radarChart = echarts.init(radarEl.value)
  radarChart.setOption({
    tooltip: {},
    radar: {
      indicator: d.dimensions.map((x) => ({ name: x.name, max: 10 })),
      radius: '65%',
      splitArea: { areaStyle: { color: ['#f8fafc', '#f1f5f9'] } },
    },
    series: [
      {
        type: 'radar',
        data: [
          {
            value: d.dimensions.map((x) => x.value),
            name: '平均分',
            areaStyle: { color: 'rgba(59,130,246,0.25)' },
            lineStyle: { color: '#3b82f6', width: 2 },
            itemStyle: { color: '#3b82f6' },
          },
        ],
      },
    ],
  })
}

function renderTrend(d: DashboardVO) {
  if (!trendEl.value) return
  if (!trendChart) trendChart = echarts.init(trendEl.value)
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: d.trend.map((t) => t.date) },
    yAxis: { type: 'value', min: 0, max: 40 },
    series: [
      {
        type: 'line',
        smooth: true,
        data: d.trend.map((t) => t.score),
        lineStyle: { color: '#10b981', width: 2.5 },
        itemStyle: { color: '#10b981' },
        areaStyle: { color: 'rgba(16,185,129,0.15)' },
      },
    ],
  })
}

function renderAll(d: DashboardVO) {
  renderRadar(d)
  renderTrend(d)
}

watch(
  data,
  (v) => {
    if (v) renderAll(v)
  },
  { flush: 'post' },
)

onMounted(() => {
  window.addEventListener('resize', onResize)
  if (data.value) renderAll(data.value)
})

function onResize() {
  radarChart?.resize()
  trendChart?.resize()
}

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  radarChart?.dispose()
  trendChart?.dispose()
  radarChart = null
  trendChart = null
})
</script>

<template>
  <section class="py-6">
    <div class="mb-4 flex items-center justify-between">
      <div>
        <h1 class="text-xl font-bold">数据分析</h1>
        <p class="mt-1 text-sm text-slate-500">聚合所有已完成面试，查看能力雷达、成绩趋势与薄弱环节</p>
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

    <div v-if="isLoading" class="flex h-[60vh] items-center justify-center rounded-xl bg-white text-slate-400">
      加载数据中...
    </div>

    <template v-else-if="data">
      <div class="grid grid-cols-4 gap-4">
        <div class="rounded-xl bg-white p-5 shadow-sm">
          <p class="text-sm text-slate-400">已完成面试</p>
          <p class="mt-2 text-3xl font-bold text-slate-800">{{ data.stats.totalInterviews }}</p>
        </div>
        <div class="rounded-xl bg-white p-5 shadow-sm">
          <p class="text-sm text-slate-400">平均总分</p>
          <p class="mt-2 text-3xl font-bold text-blue-600">{{ data.stats.avgScore }}<span class="text-base text-slate-400"> / 40</span></p>
        </div>
        <div class="rounded-xl bg-white p-5 shadow-sm">
          <p class="text-sm text-slate-400">最高分</p>
          <p class="mt-2 text-3xl font-bold text-emerald-600">{{ data.stats.maxScore }}</p>
        </div>
        <div class="rounded-xl bg-white p-5 shadow-sm">
          <p class="text-sm text-slate-400">累计回答题目</p>
          <p class="mt-2 text-3xl font-bold text-amber-600">{{ data.stats.totalQuestions }}</p>
        </div>
      </div>

      <div class="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-2">
        <div class="rounded-xl bg-white p-5 shadow-sm">
          <h2 class="mb-2 font-semibold">能力雷达</h2>
          <div ref="radarEl" class="h-72" />
        </div>
        <div class="rounded-xl bg-white p-5 shadow-sm">
          <h2 class="mb-2 font-semibold">成绩趋势</h2>
          <div ref="trendEl" class="h-72" />
        </div>
      </div>

      <div class="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-2">
        <div class="rounded-xl bg-white p-5 shadow-sm">
          <h2 class="mb-3 font-semibold">薄弱点</h2>
          <ul v-if="data.weakPoints.length" class="space-y-2 text-sm">
            <li v-for="w in data.weakPoints" :key="w" class="flex items-start gap-2">
              <span class="mt-1 h-2 w-2 shrink-0 rounded-full bg-red-500" />
              <span class="text-slate-700">{{ w }}</span>
            </li>
          </ul>
          <p v-else class="text-sm text-slate-400">暂无数据</p>
        </div>
        <div class="rounded-xl bg-white p-5 shadow-sm">
          <h2 class="mb-3 font-semibold">提升建议</h2>
          <ul v-if="data.suggestions.length" class="space-y-2 text-sm">
            <li v-for="s in data.suggestions" :key="s" class="flex items-start gap-2">
              <span class="mt-1 h-2 w-2 shrink-0 rounded-full bg-blue-500" />
              <span class="text-slate-700">{{ s }}</span>
            </li>
          </ul>
          <p v-else class="text-sm text-slate-400">暂无数据</p>
        </div>
      </div>
    </template>

    <div v-else class="flex h-[60vh] items-center justify-center rounded-xl bg-white text-slate-400">
      暂无数据，先完成一场面试即可生成分析报告
    </div>
  </section>
</template>