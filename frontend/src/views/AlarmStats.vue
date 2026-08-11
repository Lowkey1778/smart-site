<template>
  <div>
    <!-- 统计卡片 -->
    <el-row :gutter="16" class="mb16">
      <el-col :span="4" v-for="item in statCards" :key="item.label">
        <el-card shadow="never" class="stat-card" :style="{ borderTopColor: item.color }">
          <div class="stat-value" :style="{ color: item.color }">{{ item.value }}</div>
          <div class="stat-label">{{ item.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span><el-icon><TrendCharts /></el-icon> 告警数量趋势（近 {{ statDays }} 天）</span>
              <el-radio-group v-model="statDays" size="small" @change="loadStats">
                <el-radio-button :value="7">近7天</el-radio-button>
                <el-radio-button :value="30">近30天</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div ref="trendRef" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>
            <span><el-icon><PieChart /></el-icon> 告警级别分布</span>
          </template>
          <div ref="levelRef" class="chart"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="mt16">
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>
            <span><el-icon><Odometer /></el-icon> 告警来源分布</span>
          </template>
          <div ref="sourceRef" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>
            <span><el-icon><Monitor /></el-icon> 设备类型告警分布</span>
          </template>
          <div ref="deviceRef" class="chart"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="mt16">
      <el-col :span="24">
        <el-card shadow="never">
          <template #header>
            <span><el-icon><DataAnalysis /></el-icon> 告警类型占比</span>
          </template>
          <div ref="typeRef" class="chart"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { getAlarmStats } from '../api/monitor'
import wsClient from '../api/ws'

const statDays = ref(7)
const stats = ref({ byStatus: [], byLevel: [], bySource: [], trend: [], byDeviceType: [], timeliness: {} })

const trendRef = ref()
const levelRef = ref()
const sourceRef = ref()
const deviceRef = ref()
const typeRef = ref()
const charts = { trend: null, level: null, source: null, device: null, type: null }

const sourceMap = { 1: '设备监测', 2: '环境监测', 3: 'AI识别' }
const levelMap = { 1: '预警', 2: '警报', 3: '控制' }

const statCards = computed(() => {
  const byStatus = stats.value.byStatus || []
  const get = key => {
    const item = byStatus.find(i => Number(i.status) === key)
    return item ? Number(item.cnt) : 0
  }
  const total = byStatus.reduce((sum, i) => sum + Number(i.cnt), 0)
  const t = stats.value.timeliness || {}
  return [
    { label: '告警总数', value: total, color: '#409EFF' },
    { label: '未处置', value: get(0), color: '#F56C6C' },
    { label: '处置中', value: get(1), color: '#E6A23C' },
    { label: '已处置', value: get(2), color: '#67C23A' },
    { label: '处置及时率', value: t.timelyRate !== undefined ? t.timelyRate + '%' : '-', color: '#9254DE' },
    { label: '平均处置时长', value: t.avgMinutes !== undefined ? t.avgMinutes + ' 分钟' : '-', color: '#13C2C2' }
  ]
})

const loadStats = async () => {
  stats.value = await getAlarmStats({ days: statDays.value })
  renderTrend(stats.value.trend || [])
  renderLevel(stats.value.byLevel || [])
  renderSource(stats.value.bySource || [])
  renderDevice(stats.value.byDeviceType || [])
  renderType(stats.value.byType || [])
}

const renderTrend = data => {
  if (!trendRef.value) return
  if (!charts.trend) charts.trend = echarts.init(trendRef.value)
  charts.trend.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: data.map(d => d.day) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      type: 'bar', data: data.map(d => Number(d.cnt)), barWidth: '55%',
      itemStyle: { color: '#409EFF', borderRadius: [4, 4, 0, 0] }
    }]
  })
}

const renderPie = (key, el, data, nameMap) => {
  if (!el.value) return
  if (!charts[key]) charts[key] = echarts.init(el.value)
  charts[key].setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie', radius: ['38%', '64%'], center: ['50%', '44%'],
      data: data.map(d => ({ name: nameMap[d.source] || nameMap[d.level] || d.typeName || d.name || '-', value: Number(d.cnt) })),
      label: { formatter: '{b}\n{d}%' }
    }]
  })
}

const renderLevel = data => renderPie('level', levelRef, data, levelMap)
const renderSource = data => renderPie('source', sourceRef, data, sourceMap)

// 告警类型占比（按内容分类）
const renderType = data => {
  if (!typeRef.value) return
  if (!charts.type) charts.type = echarts.init(typeRef.value)
  charts.type.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie', radius: ['32%', '58%'], center: ['50%', '44%'],
      roseType: 'radius',
      data: data.map(d => ({ name: d.typeName || '其他', value: Number(d.cnt) })),
      label: { formatter: '{b}\n{d}%' }
    }]
  })
}

const renderDevice = data => {
  if (!deviceRef.value) return
  if (!charts.device) charts.device = echarts.init(deviceRef.value)
  charts.device.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 90, right: 20, top: 20, bottom: 30 },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: { type: 'category', data: data.map(d => d.typeName).reverse() },
    series: [{
      type: 'bar', data: data.map(d => Number(d.cnt)).reverse(), barWidth: '55%',
      itemStyle: { color: '#67C23A', borderRadius: [0, 4, 4, 0] }
    }]
  })
}

const resize = () => {
  Object.values(charts).forEach(c => c && c.resize())
}

let unsub = null
onMounted(async () => {
  await loadStats()
  window.addEventListener('resize', resize)
  unsub = wsClient.subscribe(() => loadStats())
})
onUnmounted(() => {
  unsub && unsub()
  window.removeEventListener('resize', resize)
  Object.values(charts).forEach(c => c && c.dispose())
})
</script>

<style scoped>
.mb16 { margin-bottom: 16px; }
.mt16 { margin-top: 16px; }
.stat-card { text-align: center; border-top: 3px solid; }
.stat-value { font-size: 26px; font-weight: 700; }
.stat-label { color: #606266; margin-top: 4px; font-size: 13px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.chart { height: 300px; }
</style>
